package com.zachduda.puuids;

import com.zachduda.puuids.api.OnNewFile;
import com.zachduda.puuids.api.TimerSaved;
import com.zachduda.puuids.storage.FileStore;
import com.zachduda.puuids.storage.MySQLStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import space.arim.morepaperlib.scheduling.ScheduledTask;

import java.io.File;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Timer {
    // How often the queue is drained, in server ticks (Advanced.Save-Rate-Ticks).
    static long processrate = 10;
    // Most queued plugin writes handled in a single run (Advanced.Max-Processes-Per-Queue).
    static int sizelimit = 25;

    private static final Main plugin = Main.getPlugin(Main.class);
    private static final AtomicBoolean busy = new AtomicBoolean(false);
    private static final AtomicInteger taskid = new AtomicInteger(1);

    // puuids' own player file updates (username / ip / last-on / play time).
    private static final ConcurrentLinkedQueue<PlayerUpdate> updateSystem = new ConcurrentLinkedQueue<>();

    //                                                UUID    PLUGIN   PATH    DATA     ID
    private static final ConcurrentLinkedQueue<Quartet<String, String, String, Object, Integer>> rawdata = new ConcurrentLinkedQueue<>();
    // ConcurrentLinkedQueue#size() walks the whole queue, so the depth is tracked separately.
    private static final AtomicInteger rawsize = new AtomicInteger();

    private static final ConcurrentHashMap<UUID, Long> sessions = new ConcurrentHashMap<>();

    // Batches whose file couldn't be read or saved, waiting for another go. Keyed by UUID.
    private static final ConcurrentHashMap<String, Batch> retries = new ConcurrentHashMap<>();
    private static final long RETRY_WINDOW_MS = 60_000L;

    private static volatile ScheduledTask timer;
    private static volatile long scheduledrate = -1;

    static synchronized void startTimer() {
        final long rate = Math.max(1L, processrate) * 50L; // ticks -> ms

        if (timer != null) {
            if (rate == scheduledrate) {
                return;
            }
            timer.cancel();
        }

        scheduledrate = rate;
        timer = plugin.mpl.scheduling().asyncScheduler().runAtFixedRate(
                Timer::process, Duration.ofMillis(rate), Duration.ofMillis(rate));
    }

    /*
      Gathers the next run's work, grouped per player file so each file is loaded and saved once
      however many values changed in it.

      filelimit caps how many files plugin writes may touch in one run. It used to cap individual
      values instead, which threw the batching away: one setLocation is six values, so the
      default of 25 could mean as few as four files per run.
     */
    private static Map<String, Batch> collect(int filelimit) {
        final Map<String, Batch> work = new LinkedHashMap<>();

        // Batches that failed last time go first, so anything queued since lands on top of them.
        for (Iterator<Map.Entry<String, Batch>> it = retries.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<String, Batch> failed = it.next();
            work.put(failed.getKey(), failed.getValue());
            it.remove();
        }

        PlayerUpdate update;
        while ((update = updateSystem.poll()) != null) {
            work.computeIfAbsent(update.uuid, k -> new Batch()).updates.add(update);
        }

        final boolean unlimited = filelimit == Integer.MAX_VALUE;
        // Stops one player with an enormous backlog from holding a run open indefinitely.
        final long oplimit = unlimited ? Long.MAX_VALUE : filelimit * 200L;
        final Set<String> setfiles = new HashSet<>();

        long taken = 0;
        while (taken < oplimit) {
            final Quartet<String, String, String, Object, Integer> next = rawdata.peek();
            if (next == null) {
                break;
            }
            if (!unlimited && setfiles.size() >= filelimit && !setfiles.contains(next.getUUID())) {
                break; // A new file, and this run is full. Order per file is untouched.
            }

            final Quartet<String, String, String, Object, Integer> data = rawdata.poll();
            if (data == null) {
                break;
            }
            rawsize.decrementAndGet();
            setfiles.add(data.getUUID());
            work.computeIfAbsent(data.getUUID(), k -> new Batch()).sets.add(data);
            taken++;
        }

        return work;
    }

    private static void process() {
        if (plugin.isSavingPaused() || (updateSystem.isEmpty() && rawdata.isEmpty() && retries.isEmpty())) {
            return;
        }

        if (!busy.compareAndSet(false, true)) {
            return; // A previous run is still going; it will pick these up.
        }

        final long start = System.currentTimeMillis();
        try {
            plugin.setQRequests = rawsize.get();

            final Map<String, Batch> work = collect(sizelimit);

            if (!rawdata.isEmpty()) {
                plugin.debug("Q reached its limit of " + sizelimit + " files... sending other updates to next run.");
            }

            for (Map.Entry<String, Batch> entry : work.entrySet()) {
                final String uuid = entry.getKey();
                final Batch batch = entry.getValue();
                try {
                    if (!writeBatch(uuid, batch, true)) {
                        retryLater(uuid, batch);
                    }
                } catch (Throwable err) {
                    // One bad value must not take every other player's batch down with it.
                    plugin.getLogger().warning("Unexpected error while saving puuids data for " + uuid + ": " + err);
                    if (plugin.debug) {
                        err.printStackTrace();
                    }
                }
            }
        } catch (Throwable err) {
            // Never let a bad file take the whole pipeline down with it.
            plugin.getLogger().warning("Unexpected error while saving player data: " + err);
            if (plugin.debug) {
                err.printStackTrace();
            }
        } finally {
            busy.set(false);
        }

        plugin.qTimesMS = System.currentTimeMillis() - start;

        if (plugin.qTimesMS > 650) {
            plugin.getLogger().warning("Saving player data took " + plugin.qTimesMS + "ms. Try reducing your max task limit!");
        }
    }

    /*
      A file that couldn't be read or saved (disk full, a passing I/O error) keeps its changes
      and tries again on the following runs, instead of losing them on the first failure.
     */
    private static void retryLater(String uuid, Batch batch) {
        final long now = System.currentTimeMillis();
        final int changes = batch.updates.size() + batch.sets.size();

        if (batch.failedsince == 0L) {
            batch.failedsince = now;
            plugin.getLogger().warning("Unable to save puuids file " + uuid + ".yml (" + batch.error
                    + "). Its " + changes + " queued changes will be retried for up to "
                    + (RETRY_WINDOW_MS / 1000L) + "s.");
        } else if (now - batch.failedsince > RETRY_WINDOW_MS) {
            plugin.getLogger().severe("Giving up on puuids file " + uuid + ".yml after "
                    + (RETRY_WINDOW_MS / 1000L) + "s (" + batch.error + "). " + changes + " changes were lost.");
            return;
        }

        retries.put(uuid, batch);
    }

    /** @return false if the file couldn't be read or saved; {@code batch.error} then says why. */
    private static boolean writeBatch(String uuid, Batch batch, boolean events) {
        final long startset = System.currentTimeMillis();
        final File f = plugin.playerFile(uuid);
        final boolean isnewfile;
        final FileConfiguration setcache;
        final boolean storeips = plugin.storeIPs();

        synchronized (FileStore.LOCK) {
            try {
                // Never YamlConfiguration.loadConfiguration here: it answers an unreadable file
                // with an empty one, and saving that would wipe the player's whole file.
                setcache = FileStore.loadForUpdate(f, plugin.getLogger());
            } catch (Exception err) {
                batch.error = "unreadable: " + err;
                return false;
            }
            isnewfile = !f.exists();

            if (!setcache.contains("UUID")) {
                setcache.set("UUID", uuid);
            }

            long credited = 0;
            for (PlayerUpdate update : batch.updates) {
                setcache.set("UUID", update.uuid);
                setcache.set("Username", update.name);
                if (!storeips) {
                    setcache.set("IP", null);
                } else if (update.ip != null) {
                    setcache.set("IP", update.ip);
                }
                setcache.set("Last-On", update.timestamp);

                if (update.playedSeconds > 0) {
                    final long current = setcache.getLong("Time-Played");
                    setcache.set("Time-Played", current + update.playedSeconds);
                    credited += update.playedSeconds;
                }
            }

            for (Quartet<String, String, String, Object, Integer> data : batch.sets) {
                final String plname = data.getPlugin();
                final String path = data.getPath();

                if (path.equals("PUUIDS_SET_AS_ALL_NULL")) {
                    setcache.set("Plugins." + plname, null);
                } else {
                    setcache.set("Plugins." + plname + "." + path, data.getData());
                }
            }

            try {
                FileStore.save(setcache, f);
            } catch (Exception err) {
                batch.error = err.toString();
                return false;
            }

            if (plugin.debug) {
                if (credited > 0) {
                    plugin.debug("Credited " + setcache.getString("Username") + " with " + credited
                            + "s of play time (total " + setcache.getLong("Time-Played") + "s).");
                }
                for (Quartet<String, String, String, Object, Integer> data : batch.sets) {
                    plugin.debug("(" + data.getId() + ") " + data.getPlugin() + " set " + data.getData()
                            + " for " + uuid + " under: " + data.getPath());
                }
            }
        }

        mirror(uuid, batch, setcache, isnewfile);

        if (!batch.updates.isEmpty()) {
            plugin.setTimes.incrementAndGet();
            plugin.indexName(batch.updates.getLast().name, uuid);
        }

        if (!batch.sets.isEmpty()) {
            plugin.setTimeMS = (plugin.setTimeMS + System.currentTimeMillis() - startset) / 2;
            plugin.setTimes.addAndGet(batch.sets.size());
        }

        if (!events) {
            return true;
        }

        if (isnewfile) {
            for (PlayerUpdate update : batch.updates) {
                if (update.player != null) {
                    Bukkit.getPluginManager().callEvent(new OnNewFile(update.player));
                    break;
                }
            }
        }

        for (Quartet<String, String, String, Object, Integer> data : batch.sets) {
            Bukkit.getServer().getPluginManager().callEvent(new TimerSaved(data.getPlugin(), uuid, data.getId()));
        }
        return true;
    }

    private static void mirror(String uuid, Batch batch, FileConfiguration setcache, boolean isnewfile) {
        final MySQLStorage storage = plugin.getStorage();
        if (storage == null || !storage.isConnected()) {
            return;
        }

        try {
            if (!batch.updates.isEmpty() || isnewfile) {
                storage.mirrorPlayer(uuid, setcache.getString("Username"), setcache.getString("IP"),
                        setcache.getLong("Last-On"), setcache.getLong("Time-Played"));
            }

            for (Quartet<String, String, String, Object, Integer> data : batch.sets) {
                final String plname = data.getPlugin();
                final String path = data.getPath();

                if (path.equals("PUUIDS_SET_AS_ALL_NULL")) {
                    storage.mirrorClear(plname, uuid);
                } else {
                    storage.mirrorSet(plname, uuid, path, setcache.get("Plugins." + plname + "." + path));
                }
            }
        } catch (Exception err) {
            // Mirroring is never allowed to break the file pipeline.
            plugin.getLogger().warning("Unable to queue a MySQL update for " + uuid + ": " + err);
            if (plugin.debug) {
                err.printStackTrace();
            }
        }
    }

    static void startSession(UUID uuid) {
        sessions.put(uuid, System.currentTimeMillis());
    }

    static long liveSessionSeconds(UUID uuid) {
        final Long anchor = sessions.get(uuid);
        if (anchor == null) {
            return 0;
        }
        return Math.max(0L, (System.currentTimeMillis() - anchor) / 1000L);
    }

    static void queuePlayerUpdate(Player p, boolean quit) {
        if (p == null) {
            return;
        }

        final long now = System.currentTimeMillis();
        final UUID id = p.getUniqueId();
        final long[] credited = {0L};

        sessions.compute(id, (key, anchor) -> {
            if (anchor == null) {
                // Not accruing yet (server just started, or we never saw them join).
                return quit ? null : now;
            }
            final long secs = Math.max(0L, (now - anchor) / 1000L);
            credited[0] = secs;
            // Advance by whole seconds only, so the sub-second remainder isn't dropped
            // every single checkpoint.
            return quit ? null : anchor + (secs * 1000L);
        });

        final String ip = plugin.storeIPs() ? readIP(p) : null;
        updateSystem.add(new PlayerUpdate(p, id.toString(), p.getName(), ip, now, credited[0], quit));
    }

    private static String readIP(Player p) {
        try {
            final InetSocketAddress address = p.getAddress();
            if (address == null || address.getAddress() == null) {
                return null;
            }
            return address.getAddress().getHostAddress();
        } catch (Exception err) {
            return null;
        }
    }

    static int getQSize() {
        return rawsize.get();
    }

    static int queueSet(String pl, String uuid, String location, Object value) {
        // The UUID becomes a file name, so anything that could step outside the Data folder
        // (or isn't a plain name at all) is refused here rather than written somewhere odd.
        if (!Main.validKey(uuid) || location == null) {
            return 0;
        }

        final int thisid = taskid.getAndIncrement();
        // Copied so the caller changing their list afterwards can't race the writer thread.
        final Object stored = value instanceof List<?> list ? new ArrayList<>(list) : value;
        rawdata.add(new Quartet<>(uuid, pl.toUpperCase(Locale.ROOT), location, stored, thisid));
        rawsize.incrementAndGet();
        return thisid;
    }

    static void stopTimer() {
        final ScheduledTask running = timer;
        if (running != null) {
            running.cancel();
            timer = null;
            scheduledrate = -1;
        }

        // Wait briefly for an in-flight run so the same file isn't written from two threads.
        for (int i = 0; i < 100 && busy.get(); i++) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        final int leftover = rawsize.get();
        if (leftover > 0) {
            plugin.getLogger().info("Saving " + leftover + " leftover tasks...");
        }

        for (Map.Entry<String, Batch> entry : collect(Integer.MAX_VALUE).entrySet()) {
            final Batch batch = entry.getValue();
            try {
                // No events: the plugin manager is on its way down with us.
                if (!writeBatch(entry.getKey(), batch, false)) {
                    plugin.getLogger().severe("Unable to save leftover puuids data for " + entry.getKey() + " ("
                            + batch.error + "). " + (batch.updates.size() + batch.sets.size()) + " changes were lost.");
                }
            } catch (Throwable err) {
                plugin.getLogger().warning("Unable to save leftover puuids data for " + entry.getKey() + ": " + err);
            }
        }

        sessions.clear();
    }

    private static final class Batch {
        private final List<PlayerUpdate> updates = new ArrayList<>(1);
        private final List<Quartet<String, String, String, Object, Integer>> sets = new ArrayList<>(1);
        // When this batch first failed to save (0 if it never has), and why.
        private long failedsince;
        private String error;
    }

    private static final class PlayerUpdate {
        private final Player player;
        private final String uuid;
        private final String name;
        private final String ip;
        private final long timestamp;
        private final long playedSeconds;
        private final boolean quit;

        private PlayerUpdate(Player player, String uuid, String name, String ip,
                             long timestamp, long playedSeconds, boolean quit) {
            this.player = player;
            this.uuid = uuid;
            this.name = name;
            this.ip = ip;
            this.timestamp = timestamp;
            this.playedSeconds = playedSeconds;
            this.quit = quit;
        }
    }
}

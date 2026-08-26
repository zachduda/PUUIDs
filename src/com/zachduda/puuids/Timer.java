package com.zachduda.puuids;

import com.zachduda.puuids.api.OnNewFile;
import com.zachduda.puuids.api.TimerSaved;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import space.arim.morepaperlib.scheduling.ScheduledTask;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /*
      Play time is only ever credited for the span between two checkpoints of a session that
      we watched begin. Last-On can't be used for this: it is also refreshed for players who
      are merely seen, so reading it back as "when they joined" credited offline time as play
      time. Anything not in this map simply isn't accruing.
     */
    private static final ConcurrentHashMap<UUID, Long> sessions = new ConcurrentHashMap<>();

    private static volatile ScheduledTask timer;
    private static volatile long scheduledrate = -1;

    /**
     * Starts (or reschedules, after a config reload) the queue timer.
     * Called once the configuration has been read so Save-Rate-Ticks is actually honoured.
     */
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

    /**
     * Drains both queues and writes each affected player file exactly once.
     * Every path out of here has to clear {@code busy}, or saving stops for good.
     */
    private static void process() {
        if (plugin.asyncrunning || (updateSystem.isEmpty() && rawdata.isEmpty())) {
            return;
        }

        if (!busy.compareAndSet(false, true)) {
            return; // A previous run is still going; it will pick these up.
        }

        final long start = System.currentTimeMillis();
        try {
            final Map<String, Batch> work = new LinkedHashMap<>();

            PlayerUpdate update;
            while ((update = updateSystem.poll()) != null) {
                work.computeIfAbsent(update.uuid, k -> new Batch()).updates.add(update);
            }

            int queued = rawsize.get();
            plugin.setQRequests = queued;

            int processed = 0;
            while (processed < sizelimit) {
                Quartet<String, String, String, Object, Integer> data = rawdata.poll();
                if (data == null) {
                    break;
                }
                rawsize.decrementAndGet();
                work.computeIfAbsent(data.getUUID(), k -> new Batch()).sets.add(data);
                processed++;
            }

            if (processed == sizelimit && !rawdata.isEmpty()) {
                plugin.debug("Q reached size limit of " + sizelimit + "... sending other updates to next run.");
            }

            for (Map.Entry<String, Batch> entry : work.entrySet()) {
                writeBatch(entry.getKey(), entry.getValue(), true);
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

    /**
     * Applies every queued change for one player to one file, with a single load and a single save.
     *
     * @param events whether the OnNewFile / TimerSaved events should be fired (skipped on shutdown).
     */
    private static void writeBatch(String uuid, Batch batch, boolean events) {
        final long startset = System.currentTimeMillis();
        final File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        final File f = new File(cache, File.separator + uuid + ".yml");
        final boolean isnewfile = !f.exists();

        final FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        for (PlayerUpdate update : batch.updates) {
            setcache.set("UUID", update.uuid);
            setcache.set("Username", update.name);
            if (update.ip != null) {
                // A player who has already dropped has no address; keep the last known one.
                setcache.set("IP", update.ip);
            }
            setcache.set("Last-On", update.timestamp);

            if (update.playedSeconds > 0) {
                final long current = setcache.getLong("Time-Played");
                setcache.set("Time-Played", current + update.playedSeconds);
                plugin.debug("Credited " + update.name + " with " + update.playedSeconds + "s of play time (total "
                        + (current + update.playedSeconds) + "s)" + (update.quit ? " on quit." : "."));
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

            plugin.debug("(" + data.getId() + ") " + plname + " set " + data.getData() + " for " + uuid + " under: " + path);
        }

        if (!save(setcache, f)) {
            return;
        }

        if (!batch.updates.isEmpty()) {
            plugin.setTimes.incrementAndGet();
            plugin.indexName(batch.updates.get(batch.updates.size() - 1).name, uuid);
        }

        if (!batch.sets.isEmpty()) {
            plugin.setTimeMS = (plugin.setTimeMS + System.currentTimeMillis() - startset) / 2;
            plugin.setTimes.addAndGet(batch.sets.size());
        }

        if (!events) {
            return;
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
    }

    /**
     * Writes to a sibling temp file and moves it into place, so a crash mid-write can't
     * leave a player with a half-written (and therefore unreadable) data file.
     */
    private static boolean save(FileConfiguration config, File target) {
        final File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            config.save(temp);
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicUnsupported) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception err) {
            plugin.getLogger().warning("Unable to save puuids file " + target.getName() + ": " + err);
            if (plugin.debug) {
                err.printStackTrace();
            }
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }
    }

    /**
     * Opens a play time session. Called the moment a player joins, before any of the
     * asynchronous join handling, so a session is never missed.
     */
    static void startSession(UUID uuid) {
        sessions.put(uuid, System.currentTimeMillis());
    }

    /**
     * Seconds of the current session that haven't been written to file yet.
     * Returns 0 for anyone who isn't currently accruing.
     */
    static long liveSessionSeconds(UUID uuid) {
        final Long anchor = sessions.get(uuid);
        if (anchor == null) {
            return 0;
        }
        return Math.max(0L, (System.currentTimeMillis() - anchor) / 1000L);
    }

    /**
     * Snapshots a player and queues their file update.
     * <p>
     * Everything is read here, on the thread that still has a valid Player, rather than later
     * on the writer thread where a quitting player's address (and name) may already be gone.
     */
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

        updateSystem.add(new PlayerUpdate(p, id.toString(), p.getName(), readIP(p), now, credited[0], quit));
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
        if (uuid == null) {
            return 0;
        }

        final int thisid = taskid.getAndIncrement();
        rawdata.add(new Quartet<>(uuid, pl.toUpperCase(), location, value, thisid));
        rawsize.incrementAndGet();
        return thisid;
    }

    /**
     * Cancels the timer and flushes whatever is still queued. Runs on the shutdown thread.
     */
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
        if (leftover == 0 && updateSystem.isEmpty()) {
            return;
        }

        if (leftover > 0) {
            plugin.getLogger().info("Saving " + leftover + " leftover tasks...");
        }

        final Map<String, Batch> work = new LinkedHashMap<>();

        PlayerUpdate update;
        while ((update = updateSystem.poll()) != null) {
            work.computeIfAbsent(update.uuid, k -> new Batch()).updates.add(update);
        }

        Quartet<String, String, String, Object, Integer> data;
        while ((data = rawdata.poll()) != null) {
            rawsize.decrementAndGet();
            work.computeIfAbsent(data.getUUID(), k -> new Batch()).sets.add(data);
        }

        for (Map.Entry<String, Batch> entry : work.entrySet()) {
            try {
                // No events: the plugin manager is on its way down with us.
                writeBatch(entry.getKey(), entry.getValue(), false);
            } catch (Exception err) {
                plugin.getLogger().warning("Unable to save leftover puuids data for " + entry.getKey() + ": " + err);
            }
        }

        sessions.clear();
    }

    /** Everything queued for a single player file. */
    private static final class Batch {
        private final List<PlayerUpdate> updates = new ArrayList<>(1);
        private final List<Quartet<String, String, String, Object, Integer>> sets = new ArrayList<>(1);
    }

    /** An immutable snapshot of a player, taken while they were still valid to read. */
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

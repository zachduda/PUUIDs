package com.zachduda.puuids;

import com.zachduda.puuids.api.OnNewFile;
import com.zachduda.puuids.api.TimerSaved;
import com.zachduda.puuids.storage.FileStore;
import com.zachduda.puuids.storage.MySQLStorage;
import com.zachduda.puuids.storage.ValueCodec;
import com.zachduda.puuids.storage.Values;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import space.arim.morepaperlib.scheduling.ScheduledTask;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class Timer {
    // Ticks between queue runs.
    static volatile long processrate = 10;
    // Player files per batch. A run writes batches until its time budget is used.
    static volatile int sizelimit = 25;

    private static final Main plugin = Main.getPlugin(Main.class);
    // Held while files are written, so shutdown can wait for a run in progress.
    private static final ReentrantLock writing = new ReentrantLock();
    private static final AtomicInteger taskid = new AtomicInteger(1);

    private static final ConcurrentLinkedQueue<PlayerUpdate> updateSystem = new ConcurrentLinkedQueue<>();

    // UUID, plugin, path, data, id
    private static final ConcurrentLinkedQueue<Quartet<String, String, String, Object, Integer>> rawdata = new ConcurrentLinkedQueue<>();
    // size() is O(n), so the depth is tracked separately.
    private static final AtomicInteger rawsize = new AtomicInteger();

    // Changes puuids makes to a file itself, such as the one-time play time sync.
    private static final ConcurrentLinkedQueue<FileFix> fixes = new ConcurrentLinkedQueue<>();

    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);
    // Session anchors in System.nanoTime(), so a change to the system clock can't skew play time.
    private static final ConcurrentHashMap<UUID, Long> sessions = new ConcurrentHashMap<>();
    // When each online player is next checkpointed, in System.nanoTime().
    private static final ConcurrentHashMap<UUID, Long> checkpoints = new ConcurrentHashMap<>();
    private static volatile long checkpointnanos = TimeUnit.SECONDS.toNanos(300);

    // Batches that couldn't be saved, by UUID. Only touched while holding the writing lock.
    private static final Map<String, Batch> retries = new ConcurrentHashMap<>();
    private static final long FIRST_BACKOFF_NANOS = TimeUnit.MILLISECONDS.toNanos(500);
    private static final long MAX_BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long WARN_EVERY_NANOS = TimeUnit.MINUTES.toNanos(5);
    private static final long SLOW_BATCH_NANOS = TimeUnit.MILLISECONDS.toNanos(650);
    private static final long SHUTDOWN_WAIT_SECONDS = 30;

    private static final AtomicLong lastslowwarning = new AtomicLong(System.nanoTime() - WARN_EVERY_NANOS);
    // Plugin and path of writes already reported as rejected, so a loop can't flood the console.
    private static final Set<String> rejected = ConcurrentHashMap.newKeySet();

    private static volatile boolean stopping = false;
    private static volatile ScheduledTask timer;
    private static volatile long scheduledrate = -1;

    static synchronized void startTimer() {
        stopping = false;
        final long rate = Math.max(1L, processrate) * 50L;

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
     * Groups the next batch of work per player file, so each file is loaded and saved once. Player
     * updates, plugin writes and puuids' own fixes all count toward {@code filelimit}.
     *
     * @param everything true at shutdown: no limit, and retries are due whatever their backoff.
     */
    private static Map<String, Batch> collect(int filelimit, long now, boolean everything) {
        final Map<String, Batch> work = new LinkedHashMap<>();

        // Retries go first so newer changes land on top.
        for (Iterator<Map.Entry<String, Batch>> it = retries.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<String, Batch> failed = it.next();
            if (everything || failed.getValue().nextattempt - now <= 0) {
                work.put(failed.getKey(), failed.getValue());
                it.remove();
            }
        }

        final boolean unlimited = everything || filelimit == Integer.MAX_VALUE;

        PlayerUpdate peeked;
        while ((peeked = updateSystem.peek()) != null && fits(work, peeked.uuid, filelimit, unlimited)) {
            final PlayerUpdate update = updateSystem.poll();
            if (update == null) {
                break;
            }
            target(work, update.uuid).addUpdate(update);
        }

        // Keeps one player's backlog from holding a batch open.
        final long oplimit = unlimited ? Long.MAX_VALUE : filelimit * 200L;
        long taken = 0;
        Quartet<String, String, String, Object, Integer> next;
        while (taken < oplimit && (next = rawdata.peek()) != null && fits(work, next.getUUID(), filelimit, unlimited)) {
            final Quartet<String, String, String, Object, Integer> data = rawdata.poll();
            if (data == null) {
                break;
            }
            rawsize.decrementAndGet();
            target(work, data.getUUID()).addSet(data);
            taken++;
        }

        FileFix fix;
        while ((fix = fixes.peek()) != null && fits(work, fix.uuid, filelimit, unlimited)) {
            final FileFix polled = fixes.poll();
            if (polled == null) {
                break;
            }
            target(work, polled.uuid).fixes.add(polled.change);
        }

        return work;
    }

    private static boolean fits(Map<String, Batch> work, String uuid, int limit, boolean unlimited) {
        return unlimited || work.containsKey(uuid) || retries.containsKey(uuid) || work.size() < limit;
    }

    // Changes for a file whose earlier changes are waiting on a retry join them, so nothing is saved out of order.
    private static Batch target(Map<String, Batch> work, String uuid) {
        final Batch waiting = retries.get(uuid);
        return waiting != null ? waiting : work.computeIfAbsent(uuid, k -> new Batch());
    }

    private static boolean hasWork(long now) {
        if (!updateSystem.isEmpty() || !rawdata.isEmpty() || !fixes.isEmpty()) {
            return true;
        }
        for (Batch failed : retries.values()) {
            if (failed.nextattempt - now <= 0) {
                return true;
            }
        }
        return false;
    }

    private static void process() {
        if (stopping || plugin.isSavingPaused() || !hasWork(System.nanoTime())) {
            return;
        }

        if (!writing.tryLock()) {
            return; // A previous run is still going; it will pick these up.
        }

        final long start = System.nanoTime();
        try {
            plugin.setQRequests = rawsize.get();
            // Half of each interval: a backlog drains quickly without keeping a thread busy for good.
            final long budget = Math.max(TimeUnit.MILLISECONDS.toNanos(25),
                    TimeUnit.MILLISECONDS.toNanos(Math.max(1L, processrate) * 50L) / 2);

            do {
                final long batchstart = System.nanoTime();
                final Map<String, Batch> work = collect(sizelimit, batchstart, false);
                if (work.isEmpty()) {
                    break;
                }
                for (Map.Entry<String, Batch> entry : work.entrySet()) {
                    save(entry.getKey(), entry.getValue(), true);
                }
                warnIfSlow(work.size(), System.nanoTime() - batchstart);
            } while (!stopping && !plugin.isSavingPaused()
                    && System.nanoTime() - start < budget && hasWork(System.nanoTime()));

            if (!rawdata.isEmpty()) {
                plugin.debug("Save run used its time budget... sending " + rawsize.get() + " other updates to the next run.");
            }
        } catch (Throwable err) {
            // Never let a bad file take the whole pipeline down with it.
            plugin.getLogger().warning("Unexpected error while saving player data: " + err);
            if (plugin.debug) {
                err.printStackTrace();
            }
        } finally {
            writing.unlock();
        }

        plugin.qTimesMS = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static void warnIfSlow(int files, long took) {
        if (took < SLOW_BATCH_NANOS) {
            return;
        }
        final long now = System.nanoTime();
        final long last = lastslowwarning.get();
        if (now - last >= WARN_EVERY_NANOS && lastslowwarning.compareAndSet(last, now)) {
            plugin.getLogger().warning("Saving " + files + " player files took " + TimeUnit.NANOSECONDS.toMillis(took)
                    + "ms. The disk may be slow; lowering Advanced.Max-Processes-Per-Queue spreads the work out.");
        }
    }

    private static void save(String uuid, Batch batch, boolean events) {
        final long timed = plugin.debugStart();
        try {
            final boolean saved = writeBatch(uuid, batch, events);
            plugin.debugTook("SET save of " + batch.changes() + " queued changes to " + uuid + ".yml", timed);
            if (saved) {
                if (batch.failing) {
                    plugin.getLogger().info("Saved puuids file " + uuid + ".yml after " + batch.attempts
                            + " failed attempts. Nothing was lost.");
                }
            } else {
                retryLater(uuid, batch, System.nanoTime());
            }
        } catch (Throwable err) {
            // One bad value must not take every other player's batch down with it.
            plugin.getLogger().severe("Unexpected error while saving puuids data for " + uuid + " (" + err + "). "
                    + batch.changes() + " queued changes were lost.");
            if (plugin.debug) {
                err.printStackTrace();
            }
        }
    }

    /**
     * Keeps a failed file's changes for later runs instead of losing them. Read and write errors
     * (a full disk, a locked file) are retried for as long as the server runs, with a growing gap
     * between attempts; anything else is given up on.
     */
    private static void retryLater(String uuid, Batch batch, long now) {
        if (batch.retrynow) {
            // The values that broke the save were dropped; try the rest straight away.
            batch.retrynow = false;
            batch.nextattempt = now;
            retries.put(uuid, batch);
            return;
        }

        if (!batch.retryable) {
            plugin.getLogger().severe("Unable to save puuids file " + uuid + ".yml (" + batch.error + "). "
                    + batch.changes() + " queued changes were lost.");
            return;
        }

        batch.attempts++;
        if (!batch.failing) {
            batch.failing = true;
            batch.failedsince = now;
            batch.lastwarned = now;
            plugin.getLogger().warning("Unable to save puuids file " + uuid + ".yml (" + batch.error + "). Its "
                    + batch.changes() + " queued changes are kept in memory and retried until it works.");
        } else if (now - batch.lastwarned >= WARN_EVERY_NANOS) {
            batch.lastwarned = now;
            plugin.getLogger().warning("Still unable to save puuids file " + uuid + ".yml after "
                    + TimeUnit.NANOSECONDS.toSeconds(now - batch.failedsince) + "s (" + batch.error + "). "
                    + batch.changes() + " changes are waiting.");
        }

        final long backoff = Math.min(MAX_BACKOFF_NANOS, FIRST_BACKOFF_NANOS << Math.min(batch.attempts - 1, 6));
        batch.nextattempt = now + backoff;
        retries.put(uuid, batch);
    }

    /** @return false if the file couldn't be read or saved; the batch then says why and whether to retry. */
    private static boolean writeBatch(String uuid, Batch batch, boolean events) {
        final long startset = System.currentTimeMillis();
        final File f = plugin.playerFile(uuid);
        final boolean isnewfile;
        final FileConfiguration setcache;
        final boolean storeips = plugin.storeIPs();
        final PlayerUpdate update = batch.update;

        synchronized (FileStore.LOCK) {
            try {
                // Not loadConfiguration: it returns an empty config for an unreadable file, and saving that would wipe it.
                setcache = FileStore.loadForUpdate(f, plugin.getLogger());
            } catch (IOException | RuntimeException err) {
                batch.fail("unreadable: " + err, true);
                return false;
            }
            isnewfile = !f.exists();

            if (isnewfile && update == null && batch.sets.isEmpty()) {
                // Only puuids' own fixes, for a file that has gone since. Nothing to do.
                return true;
            }

            if (!setcache.contains("UUID")) {
                setcache.set("UUID", uuid);
            }

            for (Consumer<FileConfiguration> fix : batch.fixes) {
                try {
                    fix.accept(setcache);
                } catch (RuntimeException err) {
                    plugin.getLogger().warning("Unable to update " + uuid + ".yml: " + err);
                }
            }

            if (update != null) {
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
                }
            }

            for (Write write : batch.sets.values()) {
                final String plname = write.data.getPlugin();
                final String path = write.data.getPath();

                if (path.equals("PUUIDS_SET_AS_ALL_NULL")) {
                    setcache.set("Plugins." + plname, null);
                } else if (write.data.getData() instanceof Map<?, ?> map) {
                    // As a section, the way the file reads it back, so a later write inside it merges the same
                    // way whether or not the file was saved in between.
                    setcache.createSection("Plugins." + plname + "." + path, map);
                } else {
                    setcache.set("Plugins." + plname + "." + path, write.data.getData());
                }
            }

            try {
                FileStore.save(setcache, f);
            } catch (IOException err) {
                batch.fail(err.toString(), true);
                return false;
            } catch (RuntimeException err) {
                // A value that can't be written as YAML. Drop it and keep everything else.
                final int dropped = dropUnwritable(uuid, batch);
                batch.fail(err.toString(), dropped > 0);
                batch.retrynow = dropped > 0;
                return false;
            }

            if (plugin.debug) {
                if (update != null && update.playedSeconds > 0) {
                    plugin.debug("Credited " + setcache.getString("Username") + " with " + update.playedSeconds
                            + "s of play time (total " + setcache.getLong("Time-Played") + "s).");
                }
                for (Write write : batch.sets.values()) {
                    plugin.debug("(" + write.data.getId() + ") " + write.data.getPlugin() + " set " + write.data.getData()
                            + " for " + uuid + " under: " + write.data.getPath());
                }
            }
        }

        mirror(uuid, batch, setcache, isnewfile);

        if (update != null) {
            plugin.setTimes.incrementAndGet();
            plugin.indexName(update.name, uuid);
        }

        if (!batch.sets.isEmpty()) {
            plugin.setTimeMS = (plugin.setTimeMS + System.currentTimeMillis() - startset) / 2;
            plugin.setTimes.addAndGet(batch.sets.size());
        }

        if (!events) {
            return true;
        }

        if (isnewfile && update != null && update.player != null) {
            Bukkit.getPluginManager().callEvent(new OnNewFile(update.player));
        }

        for (Write write : batch.sets.values()) {
            for (int id : write.ids) {
                Bukkit.getServer().getPluginManager().callEvent(new TimerSaved(write.data.getPlugin(), uuid, id));
            }
        }
        return true;
    }

    // Finds and removes the values that can't be saved, naming each one.
    private static int dropUnwritable(String uuid, Batch batch) {
        int dropped = 0;
        for (Iterator<Write> it = batch.sets.values().iterator(); it.hasNext(); ) {
            final Write write = it.next();
            try {
                ValueCodec.encode(write.data.getData());
            } catch (RuntimeException unwritable) {
                it.remove();
                dropped++;
                plugin.getLogger().warning("Dropped " + write.data.getPlugin() + "'s value at '" + write.data.getPath()
                        + "' for " + uuid + ": it can't be saved (" + unwritable + ").");
            }
        }
        return dropped;
    }

    private static void mirror(String uuid, Batch batch, FileConfiguration setcache, boolean isnewfile) {
        final MySQLStorage storage = plugin.getStorage();
        if (storage == null || !storage.isConnected()) {
            return;
        }

        try {
            if (batch.update != null || isnewfile || !batch.fixes.isEmpty()) {
                storage.mirrorPlayer(uuid, setcache.getString("Username"), setcache.getString("IP"),
                        setcache.getLong("Last-On"), setcache.getLong("Time-Played"));
            }

            for (Write write : batch.sets.values()) {
                final String plname = write.data.getPlugin();
                final String path = write.data.getPath();

                if (path.equals("PUUIDS_SET_AS_ALL_NULL")) {
                    storage.mirrorClear(plname, uuid);
                } else {
                    storage.mirrorSet(plname, uuid, path, at -> setcache.get("Plugins." + plname + "." + at));
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

    // Sessions and checkpoints -------------------------------------------------------------

    static void setCheckpointSeconds(int seconds) {
        checkpointnanos = TimeUnit.SECONDS.toNanos(Math.max(1, seconds));
    }

    /**
     * Starts counting play time for a player who just joined.
     *
     * @param spread true for players already online when puuids starts, so their first checkpoints are
     *               spread over the interval instead of all falling due together.
     */
    static void startSession(UUID uuid, boolean spread) {
        final long now = System.nanoTime();
        final long interval = checkpointnanos;
        sessions.put(uuid, now);
        checkpoints.put(uuid, now + (spread ? 1L + ThreadLocalRandom.current().nextLong(interval) : interval));
    }

    /** Whether this online player's checkpoint is due. Each is due one interval after their last save. */
    static boolean checkpointDue(UUID uuid, long now) {
        final Long due = checkpoints.get(uuid);
        return due != null && now - due >= 0;
    }

    static long liveSessionSeconds(UUID uuid) {
        final Long anchor = sessions.get(uuid);
        if (anchor == null) {
            return 0;
        }
        return Math.max(0L, (System.nanoTime() - anchor) / NANOS_PER_SECOND);
    }

    static void queuePlayerUpdate(Player p, boolean quit) {
        if (p == null) {
            return;
        }

        final UUID id = p.getUniqueId();
        final long now = System.nanoTime();
        final long[] credited = {0L};
        final boolean[] live = {false};

        sessions.compute(id, (key, anchor) -> {
            if (anchor == null) {
                // Only a join starts a session, so a late checkpoint can't bring one back for a player who left.
                return null;
            }
            live[0] = true;
            final long secs = Math.max(0L, (now - anchor) / NANOS_PER_SECOND);
            credited[0] = secs;
            // Whole seconds only, so the remainder carries over.
            return quit ? null : anchor + (secs * NANOS_PER_SECOND);
        });

        if (quit) {
            checkpoints.remove(id);
        } else {
            if (!live[0]) {
                // They have left (a checkpoint or join task that ran just after the quit); the quit saved everything.
                return;
            }
            checkpoints.computeIfPresent(id, (key, due) -> now + checkpointnanos);
        }

        final String ip = plugin.storeIPs() ? readIP(p) : null;
        updateSystem.add(new PlayerUpdate(p, id.toString(), p.getName(), ip, System.currentTimeMillis(), credited[0], quit));
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

    // Queueing --------------------------------------------------------------------------

    static int getQSize() {
        return rawsize.get();
    }

    static int queueSet(String pl, String uuid, String location, Object value) {
        // The UUID becomes a file name, so reject anything that could escape the Data folder.
        if (!Main.validKey(uuid) || location == null) {
            return 0;
        }

        final String plname = pl.toUpperCase(Locale.ROOT);
        final Object stored;
        try {
            // Copied so later changes by the caller can't race the writer, and checked so the file stays readable.
            stored = Values.normalize(value);
        } catch (IllegalArgumentException unsafe) {
            final String what = plname + "." + location;
            if (rejected.size() < 500 && rejected.add(what)) {
                plugin.getLogger().warning("Refused " + plname + "'s write to '" + location + "' for " + uuid + ": "
                        + unsafe.getMessage() + ". Nothing was saved. (Shown once per path.)");
            } else {
                plugin.debug("Refused another write from " + plname + " to '" + location + "': " + unsafe.getMessage());
            }
            return 0;
        }

        final int thisid = taskid.getAndIncrement();
        rawdata.add(new Quartet<>(uuid, plname, location, stored, thisid));
        rawsize.incrementAndGet();
        return thisid;
    }

    /**
     * Queues a change puuids makes to a player file itself. It is applied to the file as it is when
     * written, and skipped if the file no longer exists.
     */
    static void queueFix(String uuid, Consumer<FileConfiguration> change) {
        if (Main.validKey(uuid) && change != null) {
            fixes.add(new FileFix(uuid, change));
        }
    }

    static void stopTimer() {
        stopping = true;
        final ScheduledTask running = timer;
        if (running != null) {
            running.cancel();
            timer = null;
            scheduledrate = -1;
        }

        // Wait for an in-flight run, which stops after its current batch, so changes land in order.
        boolean locked = false;
        try {
            locked = writing.tryLock(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (!locked) {
            plugin.getLogger().warning("A save run was still going after " + SHUTDOWN_WAIT_SECONDS
                    + "s; saving the rest of the queue alongside it.");
        }

        try {
            final int leftover = rawsize.get() + updateSystem.size() + fixes.size() + retries.size();
            if (leftover > 0) {
                plugin.getLogger().info("Saving " + leftover + " leftover tasks...");
            }

            for (Map.Entry<String, Batch> entry : collect(Integer.MAX_VALUE, System.nanoTime(), true).entrySet()) {
                final Batch batch = entry.getValue();
                try {
                    // No events during shutdown.
                    boolean saved = writeBatch(entry.getKey(), batch, false);
                    if (!saved && batch.retrynow) {
                        // The values that broke it were dropped; one more try for the rest.
                        batch.retrynow = false;
                        saved = writeBatch(entry.getKey(), batch, false);
                    }
                    if (!saved) {
                        plugin.getLogger().severe("Unable to save leftover puuids data for " + entry.getKey() + " ("
                                + batch.error + "). " + batch.changes() + " changes were lost.");
                    }
                } catch (Throwable err) {
                    plugin.getLogger().warning("Unable to save leftover puuids data for " + entry.getKey() + ": " + err);
                }
            }
        } finally {
            if (locked) {
                writing.unlock();
            }
        }

        sessions.clear();
        checkpoints.clear();
    }

    // Data ------------------------------------------------------------------------------

    private static final class Batch {
        // All of this file's player updates, merged into one.
        private PlayerUpdate update;
        // Plugin writes in the order they were made, by task id.
        private final LinkedHashMap<Integer, Write> sets = new LinkedHashMap<>();
        // Plugin and path -> the ids in sets still writing there.
        private final Map<String, List<Integer>> bykey = new HashMap<>();
        private final List<Consumer<FileConfiguration>> fixes = new ArrayList<>(0);

        private boolean failing;
        private int attempts;
        private long failedsince;
        private long lastwarned;
        private long nextattempt;
        private String error;
        private boolean retryable;
        private boolean retrynow;

        private void addUpdate(PlayerUpdate next) {
            update = update == null ? next : update.then(next);
        }

        /*
          Keeps a batch from growing with every write to the same path, without changing the file it saves.

          A value at a path replaces everything written at or below that path before it, and creates any
          missing parent sections itself, so earlier writes to the same path can go. A removal is kept
          after them instead: Bukkit only creates parent sections when setting a value, so dropping an
          earlier value could change which (empty) sections the file ends up with.
         */
        private void addSet(Quartet<String, String, String, Object, Integer> data) {
            final String key = data.getPlugin() + '\0' + data.getPath();

            if (data.getData() != null) {
                final List<Integer> earlier = bykey.remove(key);
                final List<Write> replaced = new ArrayList<>(earlier == null ? 0 : earlier.size());
                if (earlier != null) {
                    for (int id : earlier) {
                        final Write gone = sets.remove(id);
                        if (gone != null) {
                            replaced.add(gone);
                        }
                    }
                }
                sets.put(data.getId(), new Write(key, data, replaced));
                final List<Integer> now = new ArrayList<>(1);
                now.add(data.getId());
                bykey.put(key, now);
                return;
            }

            final Map.Entry<Integer, Write> last = sets.lastEntry();
            if (last != null && last.getValue().key.equals(key) && last.getValue().data.getData() == null) {
                // The same removal again, with nothing in between: one is enough.
                sets.put(last.getKey(), new Write(key, data, List.of(last.getValue())));
                return;
            }
            sets.put(data.getId(), new Write(key, data, List.of()));
            bykey.computeIfAbsent(key, k -> new ArrayList<>(1)).add(data.getId());
        }

        private void fail(String why, boolean canretry) {
            error = why;
            retryable = canretry;
        }

        private int changes() {
            return (update == null ? 0 : 1) + sets.size() + fixes.size();
        }
    }

    /** A plugin write, with the ids of earlier writes it made unnecessary. */
    private static final class Write {
        private final String key;
        private final Quartet<String, String, String, Object, Integer> data;
        // TimerSaved fires for every one of these once the value is on disk.
        private final int[] ids;

        private Write(String key, Quartet<String, String, String, Object, Integer> data, List<Write> replaced) {
            this.key = key;
            this.data = data;
            int count = 1;
            for (Write earlier : replaced) {
                count += earlier.ids.length;
            }
            this.ids = new int[count];
            int at = 0;
            for (Write earlier : replaced) {
                System.arraycopy(earlier.ids, 0, this.ids, at, earlier.ids.length);
                at += earlier.ids.length;
            }
            this.ids[at] = data.getId();
        }
    }

    private record FileFix(String uuid, Consumer<FileConfiguration> change) {
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

        /** This update followed by {@code next}: the latest details, with the play time of both. */
        private PlayerUpdate then(PlayerUpdate next) {
            return new PlayerUpdate(player != null ? player : next.player, uuid, next.name,
                    next.ip != null ? next.ip : ip, next.timestamp, playedSeconds + next.playedSeconds, next.quit);
        }
    }
}

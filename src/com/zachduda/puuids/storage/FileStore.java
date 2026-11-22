package com.zachduda.puuids.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.logging.Logger;

public final class FileStore {

    public static final String TEMP_SUFFIX = ".tmp";

    /**
     * Held for every load-modify-save of a player file. The save queue, a MySQL join sync and the
     * reset commands all run on different threads, and without this two of them could load the
     * same file and the slower one would silently undo the other's changes.
     */
    public static final Object LOCK = new Object();

    /** Told about every player file this class writes or deletes, so indexes can follow along. */
    public interface Listener {
        void saved(File target, FileConfiguration config);

        void deleted(File target);
    }

    /*
      Parsed player files, shared by every thread that reads them. What's cached must never be
      modified: read() and peek() are for reading only.

      - A save puts the file it just wrote straight back in (if anyone was reading it), instead
        of dropping it and making the next reader parse the whole file again.
      - Files of online players are pinned: never evicted, however full the cache is.
      - An entry is checked against the file's size and modified time at most once per
        Advanced.Cache-Revalidate-Seconds, so a hit costs no disk access at all. Changes made
        through this class are seen at once; a hand edit within that many seconds.
      - Plain concurrent map rather than a locked LinkedHashMap, so readers on different threads
        (Folia's region threads in particular) don't queue on one lock, and two threads missing
        the same file parse it once between them.

      Once a save has returned, no read may hand out anything older. Every entry carries a
      number from SEQ: a save takes one after its file is in place, a reader takes one just
      before it opens the file. A higher number therefore never holds older content, and an
      entry is only ever replaced by a higher one. Entries that leave the cache without a
      tombstone (evicted, or the cache cleared), and saves to files nobody has cached, raise the
      floor for their key to their number, so a slow reader that opened the file before then
      can't put an older copy in afterwards. Floors are shared by stripes of keys, which only
      ever costs an extra parse.
     */
    private static volatile int cachesize = 500;
    private static volatile long revalidatenanos = TimeUnit.SECONDS.toNanos(5);
    private static volatile Listener listener;

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Load> LOADING = new ConcurrentHashMap<>();
    private static final Set<String> PINNED = ConcurrentHashMap.newKeySet();
    private static final AtomicLong SEQ = new AtomicLong();
    private static final int STRIPES = 64;
    private static final AtomicLongArray FLOORS = new AtomicLongArray(STRIPES);
    private static final AtomicBoolean EVICTING = new AtomicBoolean(false);

    /** A parse in progress, which other readers of the same file may wait for instead of parsing it too. */
    private static final class Load {
        final CompletableFuture<YamlConfiguration> result = new CompletableFuture<>();
        // SEQ just before the file was opened; MAX_VALUE until then.
        volatile long opened = Long.MAX_VALUE;
    }

    // How stale a last-access time may get before a hit bothers to update it.
    private static final long TOUCH_NANOS = TimeUnit.MILLISECONDS.toNanos(500);

    /** A parsed file, or a tombstone (config == null) left by a change, so nothing older lands on top. */
    private static final class Entry {
        final YamlConfiguration config;
        final long modified;
        final long length;
        final long seq;
        volatile long validated;
        volatile long accessed;

        Entry(YamlConfiguration config, long modified, long length, long seq, long now) {
            this.config = config;
            this.modified = modified;
            this.length = length;
            this.seq = seq;
            this.validated = now;
            this.accessed = now;
        }

        static Entry tombstone(long seq) {
            // Oldest possible access time, so eviction always drops these first.
            return new Entry(null, 0L, 0L, seq, Long.MIN_VALUE);
        }

        boolean matches(long modified, long length) {
            return config != null && this.modified == modified && this.length == length;
        }

        void touch(long now) {
            if (now - accessed > TOUCH_NANOS) {
                accessed = now;
            }
        }
    }

    private FileStore() {
    }

    private static String key(File target) {
        return target.getAbsolutePath();
    }

    // Settings ----------------------------------------------------------------------------

    /**
     * How many parsed files are kept (Advanced.Cache-Size), not counting online players' files,
     * which are always kept. 0 switches the cache off entirely.
     */
    public static void setCacheSize(int size) {
        cachesize = Math.max(0, size);
        if (cachesize == 0) {
            dropAll();
        } else {
            evictIfFull();
        }
    }

    /**
     * How long a cached file is trusted before its size and modified time are checked again
     * (Advanced.Cache-Revalidate-Seconds). 0 checks on every read.
     */
    public static void setRevalidateSeconds(int seconds) {
        revalidatenanos = TimeUnit.SECONDS.toNanos(Math.max(0, seconds));
    }

    public static void setListener(Listener updated) {
        listener = updated;
    }

    public static void clearCache() {
        dropAll();
    }

    // Pinning -----------------------------------------------------------------------------

    /** Keeps this file cached however full the cache gets, until {@link #unpin}. */
    public static void pin(File target) {
        PINNED.add(key(target));
    }

    /**
     * Lets this file age out like any other. It counts as just used, so a player who rejoins
     * shortly after leaving still finds it in memory.
     */
    public static void unpin(File target) {
        final String key = key(target);
        if (PINNED.remove(key)) {
            final Entry entry = CACHE.get(key);
            if (entry != null) {
                entry.accessed = System.nanoTime();
            }
            evictIfFull();
        }
    }

    public static void unpinAll() {
        PINNED.clear();
        evictIfFull();
    }

    // Reading -----------------------------------------------------------------------------

    /**
     * A player file for reading. The result may be shared with other threads, so it must not be
     * modified, and neither may any list, section or item taken out of it.
     */
    public static FileConfiguration read(File target) {
        if (cachesize == 0) {
            return YamlConfiguration.loadConfiguration(target);
        }

        final String key = key(target);
        final Entry cached = CACHE.get(key);
        final long now = System.nanoTime();
        final long window = revalidatenanos;

        if (cached != null && cached.config != null && window > 0 && now - cached.validated < window) {
            cached.touch(now);
            return cached.config;
        }

        final long checked = SEQ.incrementAndGet();
        final long modified = target.lastModified();
        final long length = target.length();

        if (modified == 0L && length == 0L) {
            // Doesn't exist (or is empty), which reads as an empty file either way.
            if (cached != null) {
                CACHE.computeIfPresent(key, (k, current) -> current.seq < checked ? Entry.tombstone(checked) : current);
            }
            return new YamlConfiguration();
        }

        if (cached != null && cached.matches(modified, length)) {
            cached.validated = now;
            cached.touch(now);
            return cached.config;
        }

        final Load mine = new Load();
        final Load theirs = LOADING.putIfAbsent(key, mine);
        if (theirs != null) {
            if (canShare(key, theirs)) {
                // Another thread is already parsing this file; use its copy rather than parse it twice.
                return theirs.result.join();
            }
            return load(key, target, modified, length, now, null);
        }

        try {
            final YamlConfiguration loaded = load(key, target, modified, length, now, mine);
            mine.result.complete(loaded);
            return loaded;
        } catch (RuntimeException | Error err) {
            mine.result.completeExceptionally(err);
            throw err;
        } finally {
            LOADING.remove(key, mine);
        }
    }

    private static YamlConfiguration load(String key, File target, long modified, long length, long now, Load progress) {
        final long opened = SEQ.incrementAndGet();
        if (progress != null) {
            progress.opened = opened;
        }
        final YamlConfiguration loaded = YamlConfiguration.loadConfiguration(target);
        offer(key, new Entry(loaded, modified, length, opened, now));
        return loaded;
    }

    /*
      Whether waiting for another thread's parse is as good as parsing now: true unless a save
      finished (or the entry left the cache) after that thread opened the file, in which case
      its copy may be older than what this read has to return.
     */
    private static boolean canShare(String key, Load theirs) {
        final long opened = theirs.opened;
        if (opened == Long.MAX_VALUE) {
            return true; // Hasn't opened the file yet, so it will see everything saved so far.
        }
        if (opened <= floor(key)) {
            return false;
        }
        final Entry current = CACHE.get(key);
        return current == null || current.seq < opened;
    }

    /** Stores an entry unless something newer is already there, or has left the cache since. */
    private static void offer(String key, Entry entry) {
        CACHE.compute(key, (k, current) -> {
            if (current != null && current.seq > entry.seq) {
                return current;
            }
            if (entry.seq <= floor(k)) {
                // Too old to keep, but newer than what's there, so that has to go too.
                return null;
            }
            return entry;
        });
        evictIfFull();
    }

    private static int stripe(String key) {
        final int h = key.hashCode();
        return (h ^ (h >>> 16)) & (STRIPES - 1);
    }

    private static long floor(String key) {
        return FLOORS.get(stripe(key));
    }

    /** Drops an entry without leaving a tombstone, raising its floor first to cover for it. */
    private static void drop(String key, Entry entry) {
        FLOORS.accumulateAndGet(stripe(key), entry.seq, Math::max);
        CACHE.remove(key, entry);
    }

    private static void dropAll() {
        final long seq = SEQ.incrementAndGet();
        for (int i = 0; i < STRIPES; i++) {
            FLOORS.accumulateAndGet(i, seq, Math::max);
        }
        CACHE.clear();
    }

    /**
     * Like {@link #read}, but a file that isn't cached is parsed without being added. For scans
     * over every player file, which would otherwise push every file anyone is actually using
     * out of the cache. The same rule applies: the result must not be modified.
     */
    public static FileConfiguration peek(File target) {
        if (cachesize > 0) {
            final Entry cached = CACHE.get(key(target));
            if (cached != null && cached.config != null) {
                final long window = revalidatenanos;
                if ((window > 0 && System.nanoTime() - cached.validated < window)
                        || cached.matches(target.lastModified(), target.length())) {
                    return cached.config;
                }
            }
        }
        return YamlConfiguration.loadConfiguration(target);
    }

    /**
     * Loads a player file that is about to be modified and saved again. A missing file gives an
     * empty configuration, exactly like {@code YamlConfiguration.loadConfiguration}.
     * <p>
     * Unlike that method, a file that exists but can't be used is never quietly treated as
     * empty - saving on top of that would wipe every plugin's data for the player. A file that
     * can't be parsed is moved into the Corrupt folder first so it can be recovered by hand,
     * and one that can't be read at all (a passing I/O error) throws so the caller leaves it be.
     * <p>
     * Always a fresh copy from disk, never the cached one, since the caller is about to change it.
     */
    public static YamlConfiguration loadForUpdate(File target, Logger log) throws IOException {
        final YamlConfiguration config = new YamlConfiguration();
        if (!target.exists()) {
            return config;
        }

        try {
            config.load(target);
            return config;
        } catch (InvalidConfigurationException invalid) {
            final File folder = new File(target.getParentFile().getParentFile(), "Corrupt");
            if (!folder.exists() && !folder.mkdirs() && !folder.isDirectory()) {
                throw new IOException("Unable to create the folder for corrupt files: " + folder);
            }
            final File moved = new File(folder, target.getName() + "." + System.currentTimeMillis());
            Files.move(target.toPath(), moved.toPath(), StandardCopyOption.REPLACE_EXISTING);
            invalidate(target);
            log.warning(target.getName() + " could not be parsed and was moved to " + moved.getPath()
                    + " (" + invalid.getMessage() + "). A new file has been started for that player.");
            return new YamlConfiguration();
        }
    }

    // Writing -----------------------------------------------------------------------------

    public static void save(FileConfiguration config, File target) throws IOException {
        final File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create the data folder: " + parent);
        }

        final String data = config.saveToString();
        final File temp = new File(parent, target.getName() + TEMP_SUFFIX);
        try {
            // What FileConfiguration#save does, minus the parent-folder handling done above.
            Files.writeString(temp.toPath(), data, StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | UnsupportedOperationException atomicUnsupported) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException err) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw err;
        }

        cacheSaved(target, data);

        final Listener notify = listener;
        if (notify != null) {
            notify.saved(target, config);
        }
    }

    /**
     * Deletes a player file, making sure nothing keeps reading the old copy from memory.
     *
     * @return whether the file is gone.
     */
    public static boolean delete(File target) {
        final boolean deleted = target.delete() || !target.exists();
        invalidate(target);
        if (deleted) {
            final Listener notify = listener;
            if (notify != null) {
                notify.deleted(target);
            }
        }
        return deleted;
    }

    /** Forgets the cached copy of a file changed by something other than {@link #save}. */
    public static void invalidate(File target) {
        final long seq = SEQ.incrementAndGet();
        if (wanted(key(target), seq)) {
            offer(key(target), Entry.tombstone(seq));
        }
    }

    /*
      Whether anyone is reading this file: cached, pinned, or being parsed. If not, the floor is
      raised instead - a reader could have opened the old file just before it changed - which
      saves leaving a tombstone behind for every file written. Inside compute(), so no reader's
      offer() for the key can land between the look and the floor going up.
     */
    private static boolean wanted(String key, long seq) {
        final boolean[] wanted = {false};
        CACHE.compute(key, (k, current) -> {
            if (current != null || PINNED.contains(k) || LOADING.containsKey(k)) {
                wanted[0] = true;
            } else {
                FLOORS.accumulateAndGet(stripe(k), seq, Math::max);
            }
            return current;
        });
        return wanted[0];
    }

    /*
      Puts the file that was just written back in the cache, parsed from exactly the text that
      went to disk: a reader sees the same types it would get from the file (an Integer where
      a Long was set, say), and nothing the calling plugin still holds - a list it passed in and
      keeps changing - is shared with readers. Only for files someone is reading; parsing every
      file written during a bulk job just to cache it would be wasted work.
     */
    private static void cacheSaved(File target, String data) {
        final String key = key(target);
        final long seq = SEQ.incrementAndGet();
        if (cachesize == 0) {
            return;
        }
        if (!wanted(key, seq)) {
            return;
        }

        Entry replacement;
        try {
            final YamlConfiguration saved = new YamlConfiguration();
            saved.loadFromString(data);
            replacement = new Entry(saved, target.lastModified(), target.length(), seq, System.nanoTime());
        } catch (InvalidConfigurationException | RuntimeException unparsable) {
            // Readers will load it from disk and see the same problem there.
            replacement = Entry.tombstone(seq);
        }

        offer(key, replacement);
    }

    // Eviction ----------------------------------------------------------------------------

    /*
      Approximate LRU. Nothing happens until the cache is a little over its size, then one thread
      drops tombstones and the least recently used unpinned files in one pass - so the cost is
      a sort every few dozen inserts rather than a lock on every read.
     */
    private static void evictIfFull() {
        final int limit = cachesize;
        if (limit == 0) {
            return;
        }
        final int slack = Math.max(8, limit / 16);
        if (CACHE.size() <= limit + PINNED.size() + slack) {
            return;
        }
        if (!EVICTING.compareAndSet(false, true)) {
            return;
        }

        try {
            record Candidate(String key, Entry entry, long accessed) {
            }

            final List<Candidate> candidates = new ArrayList<>(CACHE.size());
            for (Map.Entry<String, Entry> cached : CACHE.entrySet()) {
                if (!PINNED.contains(cached.getKey())) {
                    final Entry entry = cached.getValue();
                    candidates.add(new Candidate(cached.getKey(), entry,
                            entry.config == null ? Long.MIN_VALUE : entry.accessed));
                }
            }

            final int excess = candidates.size() - limit;
            if (excess <= 0) {
                return;
            }

            // Snapshotted above: sorting on the live, still-changing times could break the sort.
            candidates.sort(Comparator.comparingLong(Candidate::accessed));
            for (int i = 0; i < excess; i++) {
                final Candidate oldest = candidates.get(i);
                drop(oldest.key(), oldest.entry());
            }
        } finally {
            EVICTING.set(false);
        }
    }
}

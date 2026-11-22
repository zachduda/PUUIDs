package com.zachduda.puuids.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

    /** Held for every load-modify-save of a player file, since several threads write them. */
    public static final Object LOCK = new Object();

    /** Told about every player file written or deleted, so indexes can follow. */
    public interface Listener {
        void saved(File target, FileConfiguration config);

        void deleted(File target);
    }

    /*
      Parsed player files shared by all threads; cached values must never be modified.

      - Saves put the written file back in the cache if anyone was reading it.
      - Online players' files are pinned and never evicted.
      - Size and modified time are re-checked at most once per Cache-Revalidate-Seconds, so
        hand edits can take that long to show up. Changes through this class are seen at once.
      - A concurrent map, so readers on different threads (e.g. Folia regions) don't share a
        lock and concurrent misses on one file parse it once.

      Ordering: once a save returns, no read may return older content. Entries carry a number
      from SEQ: a save takes one after its file is in place, a reader just before opening it,
      and an entry is only replaced by a higher number. Entries leaving the cache without a
      tombstone, and saves to uncached files, raise a per-key floor so a slow reader can't
      store an older copy afterwards. Floors are shared by key stripes, which at worst costs
      an extra parse.
     */
    private static volatile int cachesize = 500;
    private static volatile long revalidatenanos = TimeUnit.SECONDS.toNanos(5);
    private static volatile Listener listener;
    private static volatile Logger log = Logger.getLogger("PUUIDs");

    // Files already reported as unreadable, by key, with the modified time that was reported.
    private static final Map<String, Long> WARNED = new ConcurrentHashMap<>();
    // The last copy kept in the Corrupt folder for each file, so a retried save doesn't keep adding more.
    private static final Map<String, CorruptCopy> CORRUPT_COPIES = new ConcurrentHashMap<>();
    private static final int MAX_REMEMBERED = 1000;

    private record CorruptCopy(int hash, int length, File file) {
    }

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
            // Evicted first.
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

    /** Parsed files kept, not counting pinned ones. 0 switches the cache off. */
    public static void setCacheSize(int size) {
        cachesize = Math.max(0, size);
        if (cachesize == 0) {
            dropAll();
        } else {
            evictIfFull();
        }
    }

    /** How long a cached file is trusted before being re-checked. 0 checks on every read. */
    public static void setRevalidateSeconds(int seconds) {
        revalidatenanos = TimeUnit.SECONDS.toNanos(Math.max(0, seconds));
    }

    public static void setListener(Listener updated) {
        listener = updated;
    }

    public static void setLogger(Logger updated) {
        if (updated != null) {
            log = updated;
        }
    }

    public static void clearCache() {
        dropAll();
    }

    /** Keeps this file cached however full the cache gets, until {@link #unpin}. */
    public static void pin(File target) {
        PINNED.add(key(target));
    }

    /** Lets the file age out like any other, counted as just used. */
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

    /** A player file for reading. Shared, so neither it nor anything taken from it may be modified. */
    public static FileConfiguration read(File target) {
        if (cachesize == 0) {
            return parseFile(target);
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
                // Reuse the other thread's parse.
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
        final YamlConfiguration loaded = parseFile(target);
        offer(key, new Entry(loaded, modified, length, opened, now));
        return loaded;
    }

    // False if a save finished after the other thread opened the file, so its copy may be stale.
    private static boolean canShare(String key, Load theirs) {
        final long opened = theirs.opened;
        if (opened == Long.MAX_VALUE) {
            return true;
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
                // Too old to keep, but newer than the current entry, which must go.
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

    /** Drops an entry without a tombstone, raising its floor instead. */
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

    /** Like {@link #read}, but uncached files aren't added, so scans don't evict files in use. */
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
        return parseFile(target);
    }

    /** A fresh parse from disk that is never cached, so callers may keep or change what they take from it. */
    public static FileConfiguration readFresh(File target) {
        return parseFile(target);
    }

    /**
     * Whether the file is there, answered from the cache when it saw the file recently. Like reads,
     * that can miss a hand deletion for up to Cache-Revalidate-Seconds.
     */
    public static boolean exists(File target) {
        if (cachesize > 0) {
            final Entry cached = CACHE.get(key(target));
            final long window = revalidatenanos;
            if (cached != null && cached.config != null && window > 0 && System.nanoTime() - cached.validated < window) {
                return true;
            }
        }
        return target.exists();
    }

    /**
     * A player file's top-level values as plain data, without building any Bukkit objects and without
     * the cache. For scans that only need the core fields; it is much cheaper than a full parse.
     *
     * @return null if the file isn't YAML at all; empty if it is empty or gone.
     * @throws IOException if the file is there but can't be read.
     */
    public static Map<String, Object> readRaw(File target) throws IOException {
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(target.toPath());
        } catch (NoSuchFileException gone) {
            return new LinkedHashMap<>();
        }
        return YamlSalvage.raw(new String(bytes, StandardCharsets.UTF_8));
    }

    // Never throws. A file Bukkit refuses gives whatever could be recovered from it.
    private static YamlConfiguration parseFile(File target) {
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(target.toPath());
        } catch (NoSuchFileException missing) {
            return new YamlConfiguration();
        } catch (IOException | RuntimeException unreadable) {
            warnOnce(target, "could not be read (" + unreadable + "), so it reads as empty for now.");
            return new YamlConfiguration();
        }
        return parseText(target, new String(bytes, StandardCharsets.UTF_8));
    }

    private static YamlConfiguration parseText(File target, String text) {
        final YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(text);
            return config;
        } catch (InvalidConfigurationException | RuntimeException invalid) {
            final YamlConfiguration salvaged = YamlSalvage.salvage(text);
            warnOnce(target, "can't be read by Bukkit (" + reason(invalid) + "). "
                    + (salvaged == null ? "Nothing in it could be recovered, so it reads as empty."
                    : "puuids is using everything it could recover from it.")
                    + " Its next save keeps a copy in the Corrupt folder and rewrites it in a form Bukkit can read.");
            return salvaged == null ? new YamlConfiguration() : salvaged;
        }
    }

    private static void warnOnce(File target, String problem) {
        final long modified = target.lastModified();
        if (WARNED.size() > MAX_REMEMBERED) {
            WARNED.clear();
        }
        final Long earlier = WARNED.put(key(target), modified);
        if (earlier == null || earlier != modified) {
            log.warning(target.getName() + " " + problem);
        }
    }

    private static String reason(Throwable err) {
        final String message = err.getMessage() == null ? err.toString() : err.getMessage();
        final String line = message.lines().filter(l -> !l.isBlank()).findFirst().orElse(message).trim();
        return line.length() > 200 ? line.substring(0, 200) + "..." : line;
    }

    /**
     * Loads a player file that is about to be modified and saved. A missing file gives an empty
     * configuration.
     * <p>
     * Unlike {@code YamlConfiguration.loadConfiguration}, an unusable file is never treated as
     * empty, since saving over it would wipe the player's data. A file Bukkit can't parse is copied to
     * the Corrupt folder and whatever can be recovered from it is returned, so the save that follows
     * rewrites it in a readable form. A file that can't be read at all throws, so the caller leaves it be.
     * <p>
     * Always a fresh copy from disk, never the cached one.
     */
    public static YamlConfiguration loadForUpdate(File target, Logger log) throws IOException {
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(target.toPath());
        } catch (NoSuchFileException missing) {
            return new YamlConfiguration();
        }

        final String text = new String(bytes, StandardCharsets.UTF_8);
        final YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(text);
            return config;
        } catch (InvalidConfigurationException | RuntimeException invalid) {
            // Throws if the copy can't be made, which leaves the file alone for a later retry.
            final File copy = keepCorruptCopy(target, bytes);
            final YamlConfiguration salvaged = YamlSalvage.salvage(text);
            log.warning(target.getName() + " couldn't be read by Bukkit (" + reason(invalid) + "). "
                    + (salvaged == null
                    ? "Nothing in it could be recovered, so a new file has been started for that player."
                    : "Everything that could be recovered was kept, and it is being rewritten in a form Bukkit can read.")
                    + " The original was copied to " + copy.getPath() + ".");
            return salvaged == null ? new YamlConfiguration() : salvaged;
        }
    }

    private static File keepCorruptCopy(File target, byte[] bytes) throws IOException {
        final String key = key(target);
        final int hash = Arrays.hashCode(bytes);
        final CorruptCopy earlier = CORRUPT_COPIES.get(key);
        if (earlier != null && earlier.hash() == hash && earlier.length() == bytes.length && earlier.file().exists()) {
            return earlier.file();
        }

        final File folder = new File(target.getAbsoluteFile().getParentFile().getParentFile(), "Corrupt");
        if (!folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory()) {
            throw new IOException("Unable to create the folder for corrupt files: " + folder);
        }

        final String base = target.getName() + "." + System.currentTimeMillis();
        for (int attempt = 0; ; attempt++) {
            final File copy = new File(folder, attempt == 0 ? base : base + "-" + attempt);
            try {
                Files.write(copy.toPath(), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException taken) {
                if (attempt < 100) {
                    continue;
                }
                throw taken;
            }
            if (CORRUPT_COPIES.size() > MAX_REMEMBERED) {
                CORRUPT_COPIES.clear();
            }
            CORRUPT_COPIES.put(key, new CorruptCopy(hash, bytes.length, copy));
            return copy;
        }
    }

    public static void save(FileConfiguration config, File target) throws IOException {
        final File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create the data folder: " + parent);
        }

        final String data = config.saveToString();
        final File temp = new File(parent, target.getName() + TEMP_SUFFIX);
        try {
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
     * Deletes a player file and its cached copy.
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

    // Whether anyone is reading this file (cached, pinned or loading). If not, the floor is raised
    // instead of leaving a tombstone. Inside compute() so no offer() lands between check and raise.
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

    // Caches the written file, parsed from the text that went to disk so readers get the same types
    // as a file read and share nothing with the calling plugin. Only for files someone is reading.
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
            // Lenient, so a value Bukkit can't read back is reported here and the rest stays readable.
            final YamlConfiguration saved = parseText(target, data);
            replacement = new Entry(saved, target.lastModified(), target.length(), seq, System.nanoTime());
        } catch (RuntimeException unparsable) {
            replacement = Entry.tombstone(seq);
        }

        offer(key, replacement);
    }

    // Approximate LRU: once slightly over size, one thread drops tombstones and the oldest unpinned files in a pass.
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

            // Snapshotted so concurrent updates can't break the sort.
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

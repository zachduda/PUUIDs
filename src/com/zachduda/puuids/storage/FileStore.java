package com.zachduda.puuids.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class FileStore {

    public static final String TEMP_SUFFIX = ".tmp";

    /**
     * Held for every load-modify-save of a player file. The save queue, a MySQL join sync and the
     * reset commands all run on different threads, and without this two of them could load the
     * same file and the slower one would silently undo the other's changes.
     */
    public static final Object LOCK = new Object();

    /*
      Recently read player files, parsed. Every getter in the API used to open and parse the
      player's whole .yml on the calling thread - usually the main one - so a plugin reading
      five values for a scoreboard parsed the same file five times a tick.

      An entry is only trusted while the file's size and modified time still match, so an edit
      made by hand (or by anything else) is picked up, and every save through this class drops
      the entry outright. What's cached is shared between threads and must never be modified:
      read() is for reading only.
     */
    private static volatile int cachesize = 500;
    private static final Map<String, Cached> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                    return size() > cachesize;
                }
            });

    private record Cached(YamlConfiguration config, long modified, long length) {
    }

    private static String key(File target) {
        return target.getAbsolutePath();
    }

    /** How many parsed files are kept (Advanced.Cache-Size). 0 switches the cache off. */
    public static void setCacheSize(int size) {
        cachesize = Math.max(0, size);
        if (cachesize == 0) {
            CACHE.clear();
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * A player file for reading. The result may be shared with other threads, so it must not be
     * modified, and neither may any list, section or item taken out of it.
     */
    public static FileConfiguration read(File target) {
        final String key = key(target);
        final long modified = target.lastModified();
        final long length = target.length();

        if (modified == 0L && length == 0L) {
            // Doesn't exist (or is empty), which reads as an empty file either way.
            CACHE.remove(key);
            return new YamlConfiguration();
        }

        final Cached cached = CACHE.get(key);
        if (cached != null && cached.modified == modified && cached.length == length) {
            return cached.config;
        }

        final YamlConfiguration loaded = YamlConfiguration.loadConfiguration(target);
        if (cachesize > 0) {
            CACHE.put(key, new Cached(loaded, modified, length));
        }
        return loaded;
    }

    private FileStore() {
    }

    /**
     * Loads a player file that is about to be modified and saved again. A missing file gives an
     * empty configuration, exactly like {@code YamlConfiguration.loadConfiguration}.
     * <p>
     * Unlike that method, a file that exists but can't be used is never quietly treated as
     * empty - saving on top of that would wipe every plugin's data for the player. A file that
     * can't be parsed is moved into the Corrupt folder first so it can be recovered by hand,
     * and one that can't be read at all (a passing I/O error) throws so the caller leaves it be.
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
            CACHE.remove(key(target));
            log.warning(target.getName() + " could not be parsed and was moved to " + moved.getPath()
                    + " (" + invalid.getMessage() + "). A new file has been started for that player.");
            return new YamlConfiguration();
        }
    }

    public static void save(FileConfiguration config, File target) throws IOException {
        final File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create the data folder: " + parent);
        }

        final File temp = new File(parent, target.getName() + TEMP_SUFFIX);
        try {
            config.save(temp);
            CACHE.remove(key(target));
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | UnsupportedOperationException atomicUnsupported) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            // Again now the new file is in place, in case a reader cached the old one meanwhile.
            CACHE.remove(key(target));
        } catch (IOException | RuntimeException err) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw err;
        }
    }
}

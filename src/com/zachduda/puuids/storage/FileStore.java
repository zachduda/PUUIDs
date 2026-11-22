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

    /** Held for every load-modify-save of a player file, since several threads write them. */
    public static final Object LOCK = new Object();

    // Parsed player files. An entry is trusted only while the file's size and modified time
    // still match, and saves drop it. Shared between threads, so never modify cached values.
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

    /** How many parsed files are kept. 0 switches the cache off. */
    public static void setCacheSize(int size) {
        cachesize = Math.max(0, size);
        if (cachesize == 0) {
            CACHE.clear();
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** A player file for reading. Shared, so neither it nor anything taken from it may be modified. */
    public static FileConfiguration read(File target) {
        final String key = key(target);
        final long modified = target.lastModified();
        final long length = target.length();

        if (modified == 0L && length == 0L) {
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
     * Loads a player file that is about to be modified and saved. A missing file gives an empty
     * configuration.
     * <p>
     * Unlike {@code YamlConfiguration.loadConfiguration}, an unusable file is never treated as
     * empty, since saving over it would wipe the player's data. An unparseable file is moved to
     * the Corrupt folder, and an unreadable one throws so the caller leaves it be.
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
            // A reader may have cached the old file meanwhile.
            CACHE.remove(key(target));
        } catch (IOException | RuntimeException err) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw err;
        }
    }
}

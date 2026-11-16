package com.zachduda.puuids.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public final class FileStore {

    public static final String TEMP_SUFFIX = ".tmp";

    /**
     * Held for every load-modify-save of a player file. The save queue, a MySQL join sync and the
     * reset commands all run on different threads, and without this two of them could load the
     * same file and the slower one would silently undo the other's changes.
     */
    public static final Object LOCK = new Object();

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
    }
}

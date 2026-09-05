package com.zachduda.puuids.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Turns a single config value into text for a database column and back again.
 * <p>
 * Values are stored as a one-key YAML document rather than as a plain string so the type
 * survives the round trip: an int comes back an int, a list comes back a list, and anything
 * Bukkit knows how to serialize - ItemStacks in particular - comes back as itself. That is the
 * same representation the .yml files already use, so nothing is lost by mirroring through it.
 */
public final class ValueCodec {

    private static final String KEY = "v";

    private ValueCodec() {
    }

    public static String encode(Object value) {
        final YamlConfiguration holder = new YamlConfiguration();
        holder.set(KEY, value);
        return holder.saveToString();
    }

    /**
     * @return the decoded value, or null if the column held something this server can't read
     * (a value written by a plugin that is no longer installed, for instance).
     */
    public static Object decode(String encoded) throws InvalidConfigurationException {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        final YamlConfiguration holder = new YamlConfiguration();
        holder.loadFromString(encoded);
        return holder.get(KEY);
    }
}

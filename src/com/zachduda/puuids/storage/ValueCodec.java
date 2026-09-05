package com.zachduda.puuids.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ValueCodec {

    private static final String KEY = "v";

    private ValueCodec() {
    }

    public static String encode(Object value) {
        final YamlConfiguration holder = new YamlConfiguration();
        holder.set(KEY, value);
        return holder.saveToString();
    }

    public static Object decode(String encoded) throws InvalidConfigurationException {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        final YamlConfiguration holder = new YamlConfiguration();
        holder.loadFromString(encoded);
        return holder.get(KEY);
    }
}

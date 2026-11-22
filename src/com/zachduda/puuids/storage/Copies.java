package com.zachduda.puuids.storage;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Copies values read from a cached player file, so callers can change what they get back without
 * changing the copy every other reader shares.
 */
public final class Copies {

    /** Returned for a value this class can't copy; the caller then reads it from disk instead. */
    public static final Object UNCOPYABLE = new Object();

    private static final int MAX_DEPTH = 64;

    private Copies() {
    }

    /** A copy of {@code value}, or {@link #UNCOPYABLE}. */
    public static Object copy(Object value) {
        return copy(value, 0);
    }

    private static Object copy(Object value, int depth) {
        if (depth > MAX_DEPTH) {
            return UNCOPYABLE;
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof UUID || value instanceof Enum<?>) {
            // Immutable.
            return value;
        }
        if (value instanceof Date date) {
            return new Date(date.getTime());
        }
        if (value instanceof byte[] bytes) {
            return bytes.clone();
        }
        if (value instanceof ItemStack item) {
            return item.clone();
        }
        if (value instanceof Location location) {
            return location.clone();
        }
        if (value instanceof ConfigurationSection section) {
            final ConfigurationSection copied = section(section);
            return copied == null ? UNCOPYABLE : copied;
        }
        if (value instanceof List<?> list) {
            final List<Object> out = new ArrayList<>(list.size());
            for (Object element : list) {
                final Object copied = copy(element, depth + 1);
                if (copied == UNCOPYABLE) {
                    return UNCOPYABLE;
                }
                out.add(copied);
            }
            return out;
        }
        if (value instanceof Set<?> set) {
            final Set<Object> out = new LinkedHashSet<>();
            for (Object element : set) {
                final Object copied = copy(element, depth + 1);
                if (copied == UNCOPYABLE) {
                    return UNCOPYABLE;
                }
                out.add(copied);
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            final Map<Object, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                final Object copied = copy(entry.getValue(), depth + 1);
                if (copied == UNCOPYABLE) {
                    return UNCOPYABLE;
                }
                out.put(entry.getKey(), copied);
            }
            return out;
        }
        // Some other ConfigurationSerializable, with no general way to copy it.
        return UNCOPYABLE;
    }

    /**
     * Copies a section into a configuration of its own, at the same path, so its name and path read the
     * same as the original's.
     */
    private static ConfigurationSection section(ConfigurationSection source) {
        final YamlConfiguration root = new YamlConfiguration();
        final String path = source.getCurrentPath();
        final ConfigurationSection target = path == null || path.isEmpty() ? root : root.createSection(path);
        return fill(source, target, 0) ? target : null;
    }

    private static boolean fill(ConfigurationSection source, ConfigurationSection target, int depth) {
        if (depth > MAX_DEPTH) {
            return false;
        }
        for (String key : source.getKeys(false)) {
            final Object value = source.get(key);
            if (value instanceof ConfigurationSection child) {
                if (!fill(child, target.createSection(key), depth + 1)) {
                    return false;
                }
                continue;
            }
            final Object copied = copy(value, depth + 1);
            if (copied == UNCOPYABLE) {
                return false;
            }
            target.set(key, copied);
        }
        return true;
    }
}

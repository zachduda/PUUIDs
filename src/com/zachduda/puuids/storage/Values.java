package com.zachduda.puuids.storage;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Checks values before they are queued for a player file.
 * <p>
 * Bukkit happily writes a {@code UUID}, an enum or a plain Java object, but as a Java-tagged value it
 * then refuses to read back, which makes the whole file unreadable. Values are therefore converted
 * where there is an obvious String form, copied so the caller can't change them before they are
 * saved, and rejected otherwise.
 */
public final class Values {

    // Also catches a list or map that contains itself.
    private static final int MAX_DEPTH = 64;

    private Values() {
    }

    /**
     * A copy of {@code value} that is safe to save and shares nothing mutable with the caller.
     *
     * @throws IllegalArgumentException naming the first part of the value that can't be saved.
     */
    public static Object normalize(Object value) {
        return normalize(value, 0);
    }

    private static Object normalize(Object value, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("it is nested more than " + MAX_DEPTH
                    + " levels deep, or contains itself");
        }

        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Character) {
            return value;
        }
        if (value instanceof Number number) {
            return number(number);
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        if (value instanceof Date date) {
            return new Date(date.getTime());
        }
        if (value instanceof ItemStack item) {
            return item.clone();
        }
        if (value instanceof Location location) {
            return location.clone();
        }
        if (value instanceof ConfigurationSerializable) {
            return value;
        }
        if (value instanceof ConfigurationSection section) {
            return map(section.getValues(false), depth);
        }
        if (value instanceof Map<?, ?> map) {
            return map(map, depth);
        }
        if (value instanceof Set<?> set) {
            final Set<Object> out = new LinkedHashSet<>();
            for (Object element : set) {
                out.add(normalize(element, depth + 1));
            }
            return out;
        }
        if (value instanceof Collection<?> collection) {
            final List<Object> out = new ArrayList<>(collection.size());
            for (Object element : collection) {
                out.add(normalize(element, depth + 1));
            }
            return out;
        }
        if (value instanceof byte[] bytes) {
            return bytes.clone();
        }
        if (value.getClass().isArray()) {
            // Written as a list either way, so it reads back the same.
            final int length = Array.getLength(value);
            final List<Object> out = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                out.add(normalize(Array.get(value, i), depth + 1));
            }
            return out;
        }

        throw new IllegalArgumentException(value.getClass().getName() + " can't be saved. Use a String, number, "
                + "boolean, list, map or ConfigurationSerializable (such as an ItemStack) instead");
    }

    private static Object number(Number number) {
        if (number instanceof Integer || number instanceof Long || number instanceof Double
                || number instanceof Float || number instanceof Short || number instanceof Byte
                || number instanceof BigInteger || number instanceof BigDecimal) {
            return number;
        }
        // AtomicInteger, LongAdder and friends: saved as their current value.
        final long whole = number.longValue();
        return number.doubleValue() == whole ? (Object) whole : (Object) number.doubleValue();
    }

    private static Map<Object, Object> map(Map<?, ?> map, int depth) {
        final Map<Object, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.put(key(entry.getKey()), normalize(entry.getValue(), depth + 1));
        }
        return out;
    }

    private static Object key(Object key) {
        if (key == null || key instanceof String || key instanceof Number || key instanceof Boolean) {
            return key;
        }
        if (key instanceof UUID || key instanceof CharSequence || key instanceof Character) {
            return key.toString();
        }
        if (key instanceof Enum<?> constant) {
            return constant.name();
        }
        throw new IllegalArgumentException("a map key of type " + key.getClass().getName()
                + " can't be saved. Use String keys instead");
    }
}

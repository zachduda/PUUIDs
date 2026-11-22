package com.zachduda.puuids.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads player files that Bukkit refuses, keeping every value it can.
 * <p>
 * Bukkit fails a whole file over one value it can't construct: a Java-tagged value such as
 * {@code !!java.util.UUID}, or a {@code ConfigurationSerializable} whose plugin isn't loaded. Here,
 * unknown tags are read as the plain text, list or map underneath, and serialized objects are only
 * rebuilt where Bukkit knows their class; the rest stay as maps, so saving writes them back as they were.
 */
final class YamlSalvage {

    private YamlSalvage() {
    }

    /**
     * The file's top-level mapping as plain Java data, without constructing any Bukkit objects.
     *
     * @return null if the text isn't a YAML mapping at all; empty for an empty file.
     */
    static Map<String, Object> raw(String text) {
        final Object root;
        try {
            root = new Yaml(new Lenient()).load(text);
        } catch (RuntimeException unparseable) {
            return null;
        }
        if (root == null) {
            return new LinkedHashMap<>();
        }
        if (!(root instanceof Map<?, ?> map)) {
            return null;
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }

    /** What can be recovered of a file as a configuration, or null if nothing can. */
    static YamlConfiguration salvage(String text) {
        final Map<String, Object> root = raw(text);
        if (root == null) {
            return null;
        }
        final YamlConfiguration config = new YamlConfiguration();
        fill(config, root);
        return config;
    }

    // As Bukkit's own loader does: maps become sections, except serialized objects.
    private static void fill(ConfigurationSection section, Map<?, ?> values) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            final String key = String.valueOf(entry.getKey());
            final Object value = entry.getValue();
            if (value instanceof Map<?, ?> map && !map.containsKey(ConfigurationSerialization.SERIALIZED_TYPE_KEY)) {
                fill(section.createSection(key), map);
            } else {
                section.set(key, revive(value));
            }
        }
    }

    private static Object revive(Object value) {
        if (value instanceof Map<?, ?> map) {
            final Map<String, Object> typed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                typed.put(String.valueOf(entry.getKey()), revive(entry.getValue()));
            }
            if (typed.containsKey(ConfigurationSerialization.SERIALIZED_TYPE_KEY)) {
                try {
                    final ConfigurationSerializable object = ConfigurationSerialization.deserializeObject(typed);
                    if (object != null) {
                        return object;
                    }
                } catch (RuntimeException unknownclass) {
                    // Kept as the map, which saves back unchanged.
                }
            }
            return typed;
        }
        if (value instanceof List<?> list) {
            final List<Object> out = new ArrayList<>(list.size());
            for (Object element : list) {
                out.add(revive(element));
            }
            return out;
        }
        return value;
    }

    /** SnakeYAML's safe constructor, reading unknown tags as plain data instead of failing. */
    private static final class Lenient extends SafeConstructor {

        Lenient() {
            super(options());
            // Only plain data is ever built here, so allowing every tag can't instantiate anything.
            this.yamlConstructors.put(null, new AbstractConstruct() {
                @Override
                public Object construct(Node node) {
                    return switch (node.getNodeId()) {
                        case scalar -> constructScalar((ScalarNode) node);
                        case sequence -> constructSequence((SequenceNode) node);
                        case mapping -> constructMapping((MappingNode) node);
                        default -> null;
                    };
                }
            });
        }

        // The limits Bukkit's YamlConfiguration uses, so anything Bukkit could read, this can.
        private static LoaderOptions options() {
            final LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(Integer.MAX_VALUE);
            options.setCodePointLimit(Integer.MAX_VALUE);
            options.setNestingDepthLimit(100);
            options.setTagInspector(tag -> true);
            return options;
        }
    }
}

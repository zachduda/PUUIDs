package com.zachduda.puuids.storage;

import java.util.Locale;

/**
 * Builds the table names, and the DDL for them.
 * <p>
 * Every plugin that stores data gets its own table, so a server owner can look at
 * {@code puuids_data_myplugin} and see exactly that plugin's rows - and drop the table when the
 * plugin is gone without touching anybody else's data. Plugin names come from another author's
 * plugin.yml, so they are reduced to plain identifier characters here: the result is
 * interpolated into a statement, and nothing but this method is allowed to produce a table name.
 */
final class Tables {

    /** MySQL's identifier limit; the sanitized plugin name has to fit inside it with the prefix. */
    private static final int MAX_IDENTIFIER = 64;

    private Tables() {
    }

    static String players(String prefix) {
        return prefix + "players";
    }

    /** Maps the plugin name as it appears in the .yml files to the table holding its rows. */
    static String registry(String prefix) {
        return prefix + "plugins";
    }

    static String data(String prefix, String plugin) {
        final String base = prefix + "data_";
        return base + sanitize(plugin, MAX_IDENTIFIER - base.length());
    }

    private static String sanitize(String plugin, int room) {
        String cleaned = plugin == null ? "" : plugin.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");

        if (cleaned.isEmpty()) {
            cleaned = "unknown";
        }

        if (cleaned.length() > room) {
            /*
              Two plugins whose names only differ past the cut-off would otherwise share a table,
              so the tail is replaced by a hash of the full name rather than simply dropped.
             */
            final String hash = Integer.toHexString(cleaned.hashCode());
            final int keep = Math.max(1, Math.min(cleaned.length(), room - hash.length() - 1));
            cleaned = cleaned.substring(0, keep) + "_" + hash;
        }

        return cleaned;
    }

    static String createPlayers(String table) {
        return "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "`uuid` CHAR(36) NOT NULL,"
                + "`username` VARCHAR(16) DEFAULT NULL,"
                + "`ip` VARCHAR(45) DEFAULT NULL,"
                + "`last_on` BIGINT NOT NULL DEFAULT 0,"
                + "`time_played` BIGINT NOT NULL DEFAULT 0,"
                + "`updated` BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (`uuid`),"
                + "KEY `idx_username` (`username`)"
                + ") ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4";
    }

    static String createRegistry(String table) {
        return "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "`plugin` VARCHAR(64) NOT NULL,"
                + "`table_name` VARCHAR(64) NOT NULL,"
                + "`updated` BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (`plugin`)"
                + ") ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4";
    }

    static String createData(String table) {
        return "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "`uuid` CHAR(36) NOT NULL,"
                + "`path` VARCHAR(" + Op.MAX_PATH + ") NOT NULL,"
                + "`value` MEDIUMTEXT,"
                + "`updated` BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (`uuid`,`path`)"
                + ") ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4";
    }
}

package com.zachduda.puuids.storage;

import java.util.Locale;

final class Tables {

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
            final String hash = Integer.toHexString(cleaned.hashCode());
            final int keep = Math.clamp(room - hash.length() - 1, 1, cleaned.length());
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

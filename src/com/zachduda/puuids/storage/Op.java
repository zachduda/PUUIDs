package com.zachduda.puuids.storage;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * One queued change, waiting to be sent to MySQL.
 * <p>
 * A flush coalesces the queue before sending it (see {@code MySQLStorage#reduce}) and then
 * groups what is left by {@link #batchKey()}, so a busy queue costs a couple of round trips
 * rather than one per change.
 */
abstract class Op {

    /**
     * Index-friendly cap on a data path. Deeper than anything a plugin sensibly stores, and
     * short enough that (uuid, path) stays inside InnoDB's index limit on utf8mb4.
     */
    static final int MAX_PATH = 191;

    final String table;
    /** The plugin name as written in the .yml files, or null for puuids' own player table. */
    final String plugin;
    final String uuid;

    private Op(String table, String plugin, String uuid) {
        this.table = table;
        this.plugin = plugin;
        this.uuid = uuid;
    }

    /** Operations sharing this key, and sitting next to each other, are batched together. */
    abstract String batchKey();

    abstract String sql();

    abstract void bind(PreparedStatement ps) throws SQLException;

    /**
     * Escapes a path for use as a LIKE prefix. Data paths routinely contain underscores, which
     * LIKE would otherwise treat as "any character" and delete a sibling's rows with.
     */
    private static String childPattern(String path) {
        final StringBuilder pattern = new StringBuilder(path.length() + 4);
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            if (c == '!' || c == '%' || c == '_') {
                pattern.append('!');
            }
            pattern.append(c);
        }
        return pattern.append(".%").toString();
    }

    /** puuids' own record of a player: name, address, last seen and total play time. */
    static final class Player extends Op {
        private final String username;
        private final String ip;
        private final long laston;
        private final long timeplayed;
        private final long updated;

        Player(String table, String uuid, String username, String ip, long laston, long timeplayed) {
            super(table, null, uuid);
            this.username = username;
            this.ip = ip;
            this.laston = laston;
            this.timeplayed = timeplayed;
            this.updated = System.currentTimeMillis();
        }

        String batchKey() {
            return "player";
        }

        String sql() {
            return "INSERT INTO `" + table + "` (`uuid`,`username`,`ip`,`last_on`,`time_played`,`updated`)"
                    + " VALUES (?,?,?,?,?,?)"
                    + " ON DUPLICATE KEY UPDATE `username`=VALUES(`username`),`ip`=VALUES(`ip`),"
                    + "`last_on`=VALUES(`last_on`),`time_played`=VALUES(`time_played`),`updated`=VALUES(`updated`)";
        }

        void bind(PreparedStatement ps) throws SQLException {
            ps.setString(1, uuid);
            if (username == null) {
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(2, username);
            }
            if (ip == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, ip);
            }
            ps.setLong(4, laston);
            ps.setLong(5, timeplayed);
            ps.setLong(6, updated);
        }
    }

    /** A single value belonging to one plugin. */
    static final class Set extends Op {
        final String path;
        private final String value;
        private final long updated;

        Set(String table, String plugin, String uuid, String path, String value) {
            super(table, plugin, uuid);
            this.path = path;
            this.value = value;
            this.updated = System.currentTimeMillis();
        }

        String batchKey() {
            return "set:" + table;
        }

        String sql() {
            return "INSERT INTO `" + table + "` (`uuid`,`path`,`value`,`updated`) VALUES (?,?,?,?)"
                    + " ON DUPLICATE KEY UPDATE `value`=VALUES(`value`),`updated`=VALUES(`updated`)";
        }

        void bind(PreparedStatement ps) throws SQLException {
            ps.setString(1, uuid);
            ps.setString(2, path);
            ps.setString(3, value);
            ps.setLong(4, updated);
        }
    }

    /**
     * Clears out what used to live under a path.
     * <p>
     * With {@code includeself} it is the mirror of setting null on file - the value and
     * everything nested below it go. Without, only the nested values go: that is what runs
     * before a value is re-written, so a path that used to hold a whole section can't leave
     * orphaned children behind when it becomes a single value.
     */
    static final class Remove extends Op {
        final String path;
        final boolean includeself;

        Remove(String table, String plugin, String uuid, String path, boolean includeself) {
            super(table, plugin, uuid);
            this.path = path;
            this.includeself = includeself;
        }

        String batchKey() {
            return (includeself ? "removetree:" : "removechildren:") + table;
        }

        String sql() {
            if (includeself) {
                return "DELETE FROM `" + table + "` WHERE `uuid`=? AND (`path`=? OR `path` LIKE ? ESCAPE '!')";
            }
            return "DELETE FROM `" + table + "` WHERE `uuid`=? AND `path` LIKE ? ESCAPE '!'";
        }

        void bind(PreparedStatement ps) throws SQLException {
            ps.setString(1, uuid);
            if (includeself) {
                ps.setString(2, path);
                ps.setString(3, childPattern(path));
            } else {
                ps.setString(2, childPattern(path));
            }
        }

        /** Whether executing this would also delete the row at {@code other}. */
        boolean covers(String other) {
            return (includeself && other.equals(path)) || other.startsWith(path + ".");
        }
    }

    /** Drops everything one plugin has stored for one player. */
    static final class Clear extends Op {

        Clear(String table, String plugin, String uuid) {
            super(table, plugin, uuid);
        }

        String batchKey() {
            return "clear:" + table;
        }

        String sql() {
            return "DELETE FROM `" + table + "` WHERE `uuid`=?";
        }

        void bind(PreparedStatement ps) throws SQLException {
            ps.setString(1, uuid);
        }
    }
}

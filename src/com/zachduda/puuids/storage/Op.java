package com.zachduda.puuids.storage;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

abstract class Op {

    static final int MAX_PATH = 191;

    final String table;
    final String plugin;
    final String uuid;

    private Op(String table, String plugin, String uuid) {
        this.table = table;
        this.plugin = plugin;
        this.uuid = uuid;
    }

    abstract String batchKey();

    abstract String sql();

    abstract void bind(PreparedStatement ps) throws SQLException;

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
                ps.setString(2, username.length() > Tables.MAX_USERNAME
                        ? username.substring(0, Tables.MAX_USERNAME) : username);
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

    static final class Remove extends Op {
        final String path;
        // The row at the path itself.
        final boolean self;
        // Every row under the path.
        final boolean children;

        /** Removes everything under the path, and the path itself if {@code includeself}. */
        Remove(String table, String plugin, String uuid, String path, boolean includeself) {
            this(table, plugin, uuid, path, includeself, true);
        }

        Remove(String table, String plugin, String uuid, String path, boolean self, boolean children) {
            super(table, plugin, uuid);
            this.path = path;
            this.self = self;
            this.children = children;
        }

        /** Only the row at the path, for a parent that has just become a section. */
        static Remove exact(String table, String plugin, String uuid, String path) {
            return new Remove(table, plugin, uuid, path, true, false);
        }

        String batchKey() {
            return (self ? (children ? "removetree:" : "removeexact:") : "removechildren:") + table;
        }

        String sql() {
            if (self && children) {
                return "DELETE FROM `" + table + "` WHERE `uuid`=? AND (`path`=? OR `path` LIKE ? ESCAPE '!')";
            }
            if (self) {
                return "DELETE FROM `" + table + "` WHERE `uuid`=? AND `path`=?";
            }
            return "DELETE FROM `" + table + "` WHERE `uuid`=? AND `path` LIKE ? ESCAPE '!'";
        }

        void bind(PreparedStatement ps) throws SQLException {
            ps.setString(1, uuid);
            if (self && children) {
                ps.setString(2, path);
                ps.setString(3, childPattern(path));
            } else if (self) {
                ps.setString(2, path);
            } else {
                ps.setString(2, childPattern(path));
            }
        }

        /** Both removals at once. Removals don't depend on each other's order, so this is the same. */
        Remove and(Remove other) {
            if ((self || !other.self) && (children || !other.children)) {
                return this;
            }
            return new Remove(table, plugin, uuid, path, self || other.self, children || other.children);
        }

        /** Whether executing this would also delete the row at {@code other}. */
        boolean covers(String other) {
            return (self && other.equals(path)) || (children && other.startsWith(path + "."));
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

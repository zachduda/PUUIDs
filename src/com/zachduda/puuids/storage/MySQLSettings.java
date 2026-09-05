package com.zachduda.puuids.storage;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.Objects;

public final class MySQLSettings {

    public final boolean enabled;
    public final String host;
    public final int port;
    public final String database;
    public final String username;
    public final String password;
    public final String prefix;
    public final boolean usessl;
    public final String extraproperties;
    public final int poolsize;
    public final int connecttimeout;
    public final long flushms;
    public final int maxqueued;
    public final boolean importonstartup;
    public final boolean exportonstartup;
    public final boolean synconjoin;

    private MySQLSettings(FileConfiguration cfg) {
        enabled = cfg.getBoolean("MySQL.Enabled", false);
        host = text(cfg.getString("MySQL.Host", "localhost"), "localhost");
        port = clamp(cfg.getInt("MySQL.Port", 3306), 1, 65535, 3306);
        database = text(cfg.getString("MySQL.Database", "minecraft"), "minecraft");
        username = text(cfg.getString("MySQL.Username", "root"), "root");
        // An empty password is legitimate, so this one only defends against a null.
        cfg.getString("MySQL.Password", "");
        password = cfg.getString("MySQL.Password", "");
        prefix = sanitizePrefix(cfg.getString("MySQL.Table-Prefix", "puuids_"));
        usessl = cfg.getBoolean("MySQL.Use-SSL", false);
        cfg.getString("MySQL.Extra-Properties", "");
        extraproperties = cfg.getString("MySQL.Extra-Properties", "");
        poolsize = clamp(cfg.getInt("MySQL.Pool-Size", 3), 1, 16, 3);
        connecttimeout = clamp(cfg.getInt("MySQL.Connection-Timeout-Seconds", 10), 1, 120, 10);
        // Below ~100ms the writer thread spends more time waking up than working.
        flushms = clamp(cfg.getInt("MySQL.Flush-Rate-Ms", 1000), 100, 600000, 1000);
        maxqueued = clamp(cfg.getInt("MySQL.Max-Queued-Writes", 100000), 1000, 5000000, 100000);
        importonstartup = cfg.getBoolean("MySQL.Import-On-Startup", false);
        exportonstartup = cfg.getBoolean("MySQL.Export-On-Startup", false);
        synconjoin = cfg.getBoolean("MySQL.Sync-On-Join", false);
    }

    public static MySQLSettings from(FileConfiguration cfg) {
        return new MySQLSettings(cfg);
    }

    private static String text(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    private static String sanitizePrefix(String raw) {
        if (raw == null) {
            return "puuids_";
        }
        final String cleaned = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (cleaned.isEmpty()) {
            return "puuids_";
        }
        // Capped so there is always room for a plugin name inside MySQL's 64 character limit.
        return cleaned.length() > 24 ? cleaned.substring(0, 24) : cleaned;
    }

    public boolean sameConnection(MySQLSettings other) {
        return other != null
                && enabled == other.enabled
                && port == other.port
                && usessl == other.usessl
                && poolsize == other.poolsize
                && connecttimeout == other.connecttimeout
                && Objects.equals(host, other.host)
                && Objects.equals(database, other.database)
                && Objects.equals(username, other.username)
                && Objects.equals(password, other.password)
                && Objects.equals(prefix, other.prefix)
                && Objects.equals(extraproperties, other.extraproperties);
    }

    public boolean sameAs(MySQLSettings other) {
        return sameConnection(other)
                && flushms == other.flushms
                && maxqueued == other.maxqueued
                && importonstartup == other.importonstartup
                && exportonstartup == other.exportonstartup
                && synconjoin == other.synconjoin;
    }

    public String describe() {
        return host + ":" + port + "/" + database;
    }
}

package com.zachduda.puuids.storage;

import com.zachduda.puuids.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class MySQLStorage {

    private static final String[] MYSQL_DRIVERS = {"com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver"};
    private static final String MARIADB_DRIVER = "org.mariadb.jdbc.Driver";

    /** Players handled per round trip when importing, so a big database can't eat the heap. */
    private static final int IMPORT_PAGE = 250;
    /** Files read per transaction when exporting. */
    private static final int EXPORT_BATCH = 200;
    /** How long a repeated failure stays quiet in the console. */
    private static final long LOG_MUTE_MS = 60_000L;

    private final Main plugin;
    private final MySQLSettings settings;
    private final String playerstable;
    private final String registrytable;

    private final LinkedBlockingDeque<Op> queue = new LinkedBlockingDeque<>();
    private final Set<String> ensured = ConcurrentHashMap.newKeySet();
    private final Set<String> longpathwarned = ConcurrentHashMap.newKeySet();

    private final AtomicLong written = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean longtask = new AtomicBoolean();

    private volatile ConnectionPool pool;
    private volatile ScheduledExecutorService worker;
    private volatile boolean connected;
    private volatile String lasterror;
    private volatile long quietuntil;
    private volatile long retryafter;

    public MySQLStorage(Main plugin, MySQLSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.playerstable = Tables.players(settings.prefix);
        this.registrytable = Tables.registry(settings.prefix);
    }

    // Lifecycle ---------------------------------------------------------------------------

    public boolean start() {
        if (!running.compareAndSet(false, true)) {
            return connected;
        }

        final String driver = resolveDriver();
        if (driver == null) {
            plugin.getLogger().severe("MySQL is enabled in your config.yml, but this server has no MySQL JDBC driver.");
            plugin.getLogger().severe("Add a MySQL (or MariaDB) connector to your server, or set MySQL.Enabled to false.");
            running.set(false);
            return false;
        }

        plugin.debug("Using JDBC driver " + driver);
        pool = new ConnectionPool(url(driver), properties(), settings.poolsize, settings.connecttimeout);

        try {
            withConnection(connection -> {
                try (Statement st = connection.createStatement()) {
                    st.executeUpdate(Tables.createPlayers(playerstable));
                    st.executeUpdate(Tables.createRegistry(registrytable));
                }
                return null;
            });
        } catch (SQLException err) {
            plugin.getLogger().severe("Unable to connect to MySQL at " + settings.describe() + ": " + err.getMessage());
            plugin.getLogger().severe("puuids will keep saving to file only. Fix the connection and run /puuids mysql reconnect.");
            lasterror = err.getMessage();
            pool.close();
            pool = null;
            running.set(false);
            return false;
        }

        connected = true;
        lasterror = null;

        final ThreadFactory factory = task -> {
            final Thread thread = new Thread(task, "PUUIDs-MySQL");
            thread.setDaemon(true);
            return thread;
        };
        final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(factory);
        service.scheduleWithFixedDelay(this::flushQuietly, settings.flushms, settings.flushms, TimeUnit.MILLISECONDS);
        worker = service;

        plugin.getLogger().info("Connected to MySQL at " + settings.describe() + ". Player data will be mirrored there.");
        return true;
    }

    public void shutdown() {
        final ScheduledExecutorService service = worker;
        worker = null;

        if (service != null) {
            service.shutdown();
            try {
                if (!service.awaitTermination(5, TimeUnit.SECONDS)) {
                    service.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                service.shutdownNow();
            }
        }

        final int leftover = queue.size();
        if (leftover > 0 && connected) {
            plugin.getLogger().info("Sending " + leftover + " leftover changes to MySQL...");
        }

        retryafter = 0; // A backoff from earlier must not skip the last flush.
        for (int attempt = 0; attempt < 200 && !queue.isEmpty(); attempt++) {
            final int before = queue.size();
            flushQuietly();
            if (queue.size() >= before) {
                // Nothing happening, something went wrong.
                break;
            }
        }

        final ConnectionPool open = pool;
        pool = null;
        if (open != null) {
            open.close();
        }

        connected = false;
        running.set(false);
        queue.clear();
        ensured.clear();
    }

    public MySQLSettings settings() {
        return settings;
    }

    public boolean isConnected() {
        return connected;
    }

    public int queued() {
        return queue.size();
    }

    public List<String> status() {
        final List<String> lines = new ArrayList<>();
        lines.add("&8&l> &fServer: &e" + settings.describe());
        lines.add("&8&l> &fConnected: " + (connected ? "&a&lYES" : "&c&lNO"));
        lines.add("&8&l> &fQueued Changes: &e" + queue.size());
        lines.add("&8&l> &fChanges Written: &e" + written.get());
        lines.add("&8&l> &fPlugin Tables: &e" + ensured.size());
        if (dropped.get() > 0) {
            lines.add("&8&l> &fDropped Changes: &c" + dropped.get());
        }
        if (lasterror != null) {
            lines.add("&8&l> &6&lLAST ERROR: &f" + lasterror);
        }
        return lines;
    }

    // Mirroring ---------------------------------------------------------------------------

    public void mirrorPlayer(String uuid, String username, String ip, long laston, long timeplayed) {
        if (uuid == null) {
            return;
        }
        enqueue(new Op.Player(playerstable, uuid, username, ip, laston, timeplayed));
    }

    public void mirrorSet(String pluginname, String uuid, String path, Object current) {
        if (uuid == null || pluginname == null || path == null) {
            return;
        }

        if (tooLong(pluginname, path, "mirroring")) {
            return;
        }

        final String table = Tables.data(settings.prefix, pluginname);

        if (current == null) {
            // Setting null on file drops the value and everything under it; so does this.
            enqueue(new Op.Remove(table, pluginname, uuid, path, true));
            return;
        }

        enqueue(new Op.Remove(table, pluginname, uuid, path, false));

        if (current instanceof ConfigurationSection section) {
            for (String key : section.getKeys(true)) {
                final Object leaf = section.get(key);
                if (leaf == null || leaf instanceof ConfigurationSection) {
                    continue;
                }
                queueValue(table, pluginname, uuid, path + "." + key, leaf);
            }
            return;
        }

        queueValue(table, pluginname, uuid, path, current);
    }

    public void mirrorClear(String pluginname, String uuid) {
        if (uuid == null || pluginname == null) {
            return;
        }
        enqueue(new Op.Clear(Tables.data(settings.prefix, pluginname), pluginname, uuid));
    }

    private void queueValue(String table, String pluginname, String uuid, String path, Object value) {
        if (tooLong(pluginname, path, "mirroring")) {
            return;
        }
        enqueue(new Op.Set(table, pluginname, uuid, path, ValueCodec.encode(value)));
    }

    private boolean tooLong(String pluginname, String path, String what) {
        if (path.length() <= Op.MAX_PATH) {
            return false;
        }
        if (longpathwarned.size() < 500 && longpathwarned.add(pluginname + "." + path)) {
            plugin.getLogger().warning("Skipping " + pluginname + "'s path '" + path + "' when " + what
                    + " to MySQL: paths over " + Op.MAX_PATH + " characters can't be indexed.");
        }
        return true;
    }

    private void enqueue(Op op) {
        if (!connected) {
            return;
        }

        queue.add(op);

        while (queue.size() > settings.maxqueued) {
            if (queue.poll() == null) {
                break;
            }
            final long total = dropped.incrementAndGet();
            if (total == 1 || quiet()) {
                plugin.getLogger().warning("MySQL is too far behind; dropping queued changes ("
                        + total + " so far). Run /puuids mysql export once it is healthy again.");
            }
        }
    }

    // Writing -----------------------------------------------------------------------------

    private void flushQuietly() {
        try {
            flush();
        } catch (Throwable err) {
            fail("Unexpected error while writing to MySQL", err);
        }
    }

    private void flush() {
        if (!connected || queue.isEmpty() || pool == null) {
            return;
        }

        if (System.currentTimeMillis() < retryafter) {
            return; // Backing off after a failure.
        }

        final List<Op> batch = new ArrayList<>(Math.min(queue.size(), 5000));
        Op op;
        while (batch.size() < 5000 && (op = queue.poll()) != null) {
            batch.add(op);
        }

        if (batch.isEmpty()) {
            return;
        }

        final List<Op> ready = reduce(batch);

        try {
            withConnection(connection -> {
                ensureTables(connection, ready);
                apply(connection, ready);
                return null;
            });
            written.addAndGet(ready.size());
            retryafter = 0;
            lasterror = null;
        } catch (SQLException err) {
            for (int i = ready.size() - 1; i >= 0; i--) {
                queue.addFirst(ready.get(i));
            }
            retryafter = System.currentTimeMillis() + 15_000L;
            fail("Unable to write " + ready.size() + " changes to MySQL", err);
        }
    }

    private void ensureTables(Connection connection, List<Op> batch) throws SQLException {
        final Map<String, String> missing = new LinkedHashMap<>();
        for (Op op : batch) {
            if (op.plugin != null && !ensured.contains(op.table)) {
                missing.put(op.table, op.plugin);
            }
        }

        if (missing.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : missing.entrySet()) {
            try (Statement st = connection.createStatement()) {
                st.executeUpdate(Tables.createData(entry.getKey()));
            }
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO `" + registrytable
                    + "` (`plugin`,`table_name`,`updated`) VALUES (?,?,?)"
                    + " ON DUPLICATE KEY UPDATE `table_name`=VALUES(`table_name`),`updated`=VALUES(`updated`)")) {
                ps.setString(1, entry.getValue());
                ps.setString(2, entry.getKey());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
            ensured.add(entry.getKey());
            plugin.debug("MySQL table " + entry.getKey() + " is ready for " + entry.getValue() + ".");
        }
    }

    private List<Op> reduce(List<Op> ops) {
        final Map<String, Op.Player> players = new LinkedHashMap<>();
        final Map<String, List<Pending>> bytable = new LinkedHashMap<>();
        final Map<String, Pending> pending = new LinkedHashMap<>();

        for (Op op : ops) {
            if (op instanceof Op.Player) {
                players.put(op.uuid, (Op.Player) op);
                continue;
            }

            final Pending forplayer = pending.computeIfAbsent(op.table + ' ' + op.uuid, key -> {
                final Pending created = new Pending();
                bytable.computeIfAbsent(op.table, k -> new ArrayList<>()).add(created);
                return created;
            });
            forplayer.add(op);
        }

        final List<Op> out = new ArrayList<>(ops.size());
        out.addAll(players.values());

        for (List<Pending> table : bytable.values()) {
            for (Pending entry : table) {
                if (entry.clear != null) {
                    out.add(entry.clear);
                }
            }
            for (Pending entry : table) {
                for (Op.Remove remove : entry.removes.values()) {
                    if (remove.includeself) {
                        out.add(remove);
                    }
                }
            }
            for (Pending entry : table) {
                for (Op.Remove remove : entry.removes.values()) {
                    if (!remove.includeself) {
                        out.add(remove);
                    }
                }
            }
            for (Pending entry : table) {
                out.addAll(entry.sets.values());
            }
        }

        return out;
    }

    private static final class Pending {
        private Op.Clear clear;
        private final Map<String, Op.Remove> removes = new LinkedHashMap<>();
        private final Map<String, Op.Set> sets = new LinkedHashMap<>();

        private void add(Op op) {
            if (op instanceof Op.Clear) {
                // Wipes the slate: nothing queued before it can still matter.
                clear = (Op.Clear) op;
                removes.clear();
                sets.clear();
                return;
            }

            if (op instanceof Op.Remove remove) {
                sets.keySet().removeIf(remove::covers);

                if (clear != null) {
                    return; // The clear already takes these rows out.
                }

                removes.keySet().removeIf(path -> path.startsWith(remove.path + "."));
                removes.put(remove.path, remove);
                return;
            }

            final Op.Set set = (Op.Set) op;
            sets.put(set.path, set);
        }
    }

    private void apply(Connection connection, List<Op> batch) throws SQLException {
        final boolean autocommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int index = 0;
            while (index < batch.size()) {
                final Op first = batch.get(index);
                final String key = first.batchKey();

                try (PreparedStatement ps = connection.prepareStatement(first.sql())) {
                    while (index < batch.size() && batch.get(index).batchKey().equals(key)) {
                        batch.get(index).bind(ps);
                        ps.addBatch();
                        index++;
                    }
                    ps.executeBatch();
                }
            }
            connection.commit();
        } catch (SQLException | RuntimeException err) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // The connection is discarded by the caller either way.
            }
            throw err;
        } finally {
            try {
                connection.setAutoCommit(autocommit);
            } catch (SQLException ignored) {
                // Same: this connection is on its way out if we got here through an error.
            }
        }
    }

    // Export ------------------------------------------------------------------------------

    public void exportAll(Consumer<String> progress, Runnable then) {
        final boolean queued = submit(() -> {
            if (!longtask.compareAndSet(false, true)) {
                progress.accept("&c&lBusy. &fAnother MySQL import or export is already running.");
                finish(then);
                return;
            }

            final long start = System.currentTimeMillis();
            try {
                final File[] files = dataFiles();
                if (files.length == 0) {
                    progress.accept("&6&lNothing To Do. &fThere are no data files to export.");
                    return;
                }

                progress.accept("&7&oExporting " + files.length + " players to MySQL...");

                int exported = 0;
                final List<Op> batch = new ArrayList<>();

                for (File file : files) {
                    final String uuid = file.getName().substring(0, file.getName().length() - 4);
                    final FileConfiguration data = YamlConfiguration.loadConfiguration(file);
                    collect(uuid, data, batch);
                    exported++;

                    if (exported % EXPORT_BATCH == 0) {
                        write(batch);
                        batch.clear();
                        progress.accept("&7&oExported " + exported + " / " + files.length + " players...");
                    }
                }

                write(batch);
                progress.accept("&a&lDone. &fExported &7&l" + exported + "&f players to MySQL in &7&l"
                        + (System.currentTimeMillis() - start) + "ms");
            } catch (SQLException err) {
                fail("Export to MySQL failed", err);
                progress.accept("&c&lFailed. &fMySQL rejected the export: " + err.getMessage());
            } finally {
                longtask.set(false);
                finish(then);
            }
        });

        if (!queued) {
            // Nothing will ever run, so whoever is waiting on us has to be released here.
            finish(then);
        }
    }

    public void clearPluginData(List<String> uuids, Consumer<String> progress) {
        if (uuids == null || uuids.isEmpty()) {
            return;
        }

        final List<String> targets = new ArrayList<>(uuids);
        targets.removeIf(java.util.Objects::isNull);

        submit(() -> {
            try {
                flush();

                final Map<String, String> tables = pluginTables();
                for (Map.Entry<String, String> entry : tables.entrySet()) {
                    final String table = entry.getValue();
                    for (int from = 0; from < targets.size(); from += IMPORT_PAGE) {
                        final List<String> page = targets.subList(from, Math.min(targets.size(), from + IMPORT_PAGE));
                        final List<Op> deletes = new ArrayList<>(page.size());
                        for (String uuid : page) {
                            deletes.add(new Op.Clear(table, entry.getKey(), uuid));
                        }
                        write(deletes);
                    }
                }
                progress.accept("&a&lDone. &fCleared the same data out of MySQL.");
            } catch (SQLException err) {
                fail("Unable to clear plugin data from MySQL", err);
                progress.accept("&c&lHeads Up. &fThe files were reset, but MySQL still holds the old data: "
                        + err.getMessage());
            }
        });
    }

    private void collect(String uuid, FileConfiguration data, List<Op> batch) {
        batch.add(new Op.Player(playerstable, uuid,
                data.getString("Username"), data.getString("IP"),
                data.getLong("Last-On"), data.getLong("Time-Played")));

        final ConfigurationSection plugins = data.getConfigurationSection("Plugins");
        if (plugins == null) {
            return;
        }

        for (String pluginname : plugins.getKeys(false)) {
            final ConfigurationSection section = plugins.getConfigurationSection(pluginname);
            if (section == null) {
                continue;
            }

            final String table = Tables.data(settings.prefix, pluginname);
            for (String path : section.getKeys(true)) {
                final Object value = section.get(path);
                if (value == null || value instanceof ConfigurationSection) {
                    continue;
                }
                if (tooLong(pluginname, path, "exporting")) {
                    continue;
                }
                batch.add(new Op.Set(table, pluginname, uuid, path, ValueCodec.encode(value)));
            }
        }
    }

    private void write(List<Op> batch) throws SQLException {
        if (batch.isEmpty()) {
            return;
        }
        final List<Op> ready = reduce(batch);
        withConnection(connection -> {
            ensureTables(connection, ready);
            apply(connection, ready);
            return null;
        });
        written.addAndGet(ready.size());
    }

    // Import ------------------------------------------------------------------------------

    public void importAll(Consumer<String> progress, Runnable then) {
        final boolean queued = submit(() -> {
            if (!longtask.compareAndSet(false, true)) {
                progress.accept("&c&lBusy. &fAnother MySQL import or export is already running.");
                finish(then);
                return;
            }

            final long start = System.currentTimeMillis();
            final boolean waspaused = plugin.isSavingPaused();

            // Hold the file writer while the folder is rewritten underneath it. Queued changes
            // simply wait, and land on the imported files once we are done.
            plugin.setSavingPaused(true);
            try {
                final Map<String, String> tables = pluginTables();
                int imported = 0;
                String cursor = "";

                while (true) {
                    final List<PlayerRow> page = playerPage(cursor);
                    if (page.isEmpty()) {
                        break;
                    }

                    final Map<String, Map<String, Map<String, String>>> values = valuesFor(tables, page);

                    for (PlayerRow row : page) {
                        applyToFile(row, values.get(row.uuid), true);
                        imported++;
                    }

                    cursor = page.getLast().uuid;
                    progress.accept("&7&oImported " + imported + " players...");
                }

                progress.accept("&a&lDone. &fImported &7&l" + imported + "&f players from MySQL in &7&l"
                        + (System.currentTimeMillis() - start) + "ms");
            } catch (SQLException err) {
                fail("Import from MySQL failed", err);
                progress.accept("&c&lFailed. &fMySQL rejected the import: " + err.getMessage());
            } finally {
                plugin.setSavingPaused(waspaused);
                longtask.set(false);
                finish(then);
            }
        });

        if (!queued) {
            finish(then);
        }
    }

    public void pullPlayer(String uuid) {
        submit(() -> {
            try {
                final Map<String, String> tables = pluginTables();
                final PlayerRow row = playerRow(uuid);
                if (row == null) {
                    plugin.debug("No MySQL record for " + uuid + " yet.");
                    return;
                }
                final Map<String, Map<String, Map<String, String>>> values =
                        valuesFor(tables, Collections.singletonList(row));
                applyToFile(row, values.get(uuid), false);
                plugin.debug("Refreshed " + uuid + "'s file from MySQL.");
            } catch (SQLException err) {
                fail("Unable to read " + uuid + " from MySQL", err);
            }
        });
    }

    private Map<String, String> pluginTables() throws SQLException {
        return withConnection(connection -> {
            final Map<String, String> tables = new LinkedHashMap<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT `plugin`,`table_name` FROM `" + registrytable + "`");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.put(rs.getString(1), rs.getString(2));
                }
            }
            return tables;
        });
    }

    private List<PlayerRow> playerPage(String after) throws SQLException {
        return withConnection(connection -> {
            final List<PlayerRow> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT `uuid`,`username`,`ip`,`last_on`,`time_played` FROM `" + playerstable + "`"
                            + " WHERE `uuid` > ? ORDER BY `uuid` ASC LIMIT " + IMPORT_PAGE)) {
                ps.setString(1, after);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new PlayerRow(rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.getLong(4), rs.getLong(5)));
                    }
                }
            }
            return rows;
        });
    }

    private PlayerRow playerRow(String uuid) throws SQLException {
        return withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT `uuid`,`username`,`ip`,`last_on`,`time_played` FROM `" + playerstable + "` WHERE `uuid`=?")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new PlayerRow(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getLong(4), rs.getLong(5));
                }
            }
        });
    }

    private Map<String, Map<String, Map<String, String>>> valuesFor(Map<String, String> tables, List<PlayerRow> page)
            throws SQLException {
        final Map<String, Map<String, Map<String, String>>> out = new LinkedHashMap<>();
        if (tables.isEmpty() || page.isEmpty()) {
            return out;
        }

        final StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < page.size(); i++) {
            placeholders.append(i == 0 ? "?" : ",?");
        }

        for (Map.Entry<String, String> entry : tables.entrySet()) {
            final String pluginname = entry.getKey();
            final String table = entry.getValue();

            try {
                withConnection(connection -> {
                    // Ordered by path so a parent is always applied before its children.
                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT `uuid`,`path`,`value` FROM `" + table + "` WHERE `uuid` IN (" + placeholders
                                    + ") ORDER BY `uuid`,`path`")) {
                        for (int i = 0; i < page.size(); i++) {
                            ps.setString(i + 1, page.get(i).uuid);
                        }
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                out.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>())
                                        .computeIfAbsent(pluginname, k -> new LinkedHashMap<>())
                                        .put(rs.getString(2), rs.getString(3));
                            }
                        }
                    }
                    return null;
                });
            } catch (SQLException err) {
                // A registered plugin whose table was dropped by hand shouldn't stop the import.
                plugin.getLogger().warning("Skipping MySQL table " + table + " for " + pluginname + ": " + err.getMessage());
            }
        }

        return out;
    }

    private void applyToFile(PlayerRow row, Map<String, Map<String, String>> values, boolean overwritecore) {
        final File folder = new File(plugin.getDataFolder(), File.separator + "Data");
        final File file = new File(folder, File.separator + row.uuid + ".yml");
        final FileConfiguration data = YamlConfiguration.loadConfiguration(file);

        final boolean newer = overwritecore || row.laston >= data.getLong("Last-On");

        data.set("UUID", row.uuid);
        if (newer) {
            if (row.username != null) {
                data.set("Username", row.username);
            }
            if (row.ip != null) {
                data.set("IP", row.ip);
            }
            data.set("Last-On", row.laston);
        }
        data.set("Time-Played", overwritecore ? row.timeplayed : Math.max(row.timeplayed, data.getLong("Time-Played")));

        if (values != null) {
            for (Map.Entry<String, Map<String, String>> byplugin : values.entrySet()) {
                for (Map.Entry<String, String> value : byplugin.getValue().entrySet()) {
                    try {
                        data.set("Plugins." + byplugin.getKey() + "." + value.getKey(),
                                ValueCodec.decode(value.getValue()));
                    } catch (Exception err) {
                        plugin.getLogger().warning("Unreadable MySQL value for " + row.uuid + " at "
                                + byplugin.getKey() + "." + value.getKey() + ": " + err.getMessage());
                    }
                }
            }
        }

        try {
            FileStore.save(data, file);
            plugin.indexName(row.username, row.uuid);
        } catch (Exception err) {
            plugin.getLogger().warning("Unable to write " + file.getName() + " during a MySQL import: " + err.getMessage());
        }
    }

    private File[] dataFiles() {
        final File folder = new File(plugin.getDataFolder(), File.separator + "Data");
        final File[] files = folder.isDirectory()
                ? folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"))
                : null;
        return files == null ? new File[0] : files;
    }

    // Plumbing ----------------------------------------------------------------------------

    private boolean submit(Runnable task) {
        final ScheduledExecutorService service = worker;
        if (service == null) {
            plugin.getLogger().warning("MySQL isn't running; that request was ignored.");
            return false;
        }
        try {
            service.execute(task);
            return true;
        } catch (RejectedExecutionException shuttingdown) {
            plugin.debug("MySQL task rejected, the plugin is shutting down.");
            return false;
        }
    }

    private void finish(Runnable then) {
        if (then == null) {
            return;
        }
        try {
            then.run();
        } catch (Throwable err) {
            plugin.getLogger().warning("Error after a MySQL task finished: " + err);
        }
    }

    private interface SqlTask<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> T withConnection(SqlTask<T> task) throws SQLException {
        final ConnectionPool open = pool;
        if (open == null) {
            throw new SQLException("The MySQL connection is not open.");
        }

        final Connection connection = open.borrow();
        boolean healthy = false;
        try {
            final T result = task.run(connection);
            healthy = true;
            return result;
        } finally {
            if (healthy) {
                open.release(connection);
            } else {
                open.discard(connection);
            }
        }
    }

    private void fail(String what, Throwable err) {
        lasterror = err.getMessage();
        if (quiet()) {
            plugin.getLogger().warning(what + ": " + err.getMessage());
            if (plugin.isDebug()) {
                err.printStackTrace();
            }
        }
    }

    private boolean quiet() {
        final long now = System.currentTimeMillis();
        if (now < quietuntil) {
            return false;
        }
        quietuntil = now + LOG_MUTE_MS;
        return true;
    }

    private String resolveDriver() {
        for (String candidate : MYSQL_DRIVERS) {
            if (load(candidate)) {
                return candidate;
            }
        }
        return load(MARIADB_DRIVER) ? MARIADB_DRIVER : null;
    }

    private boolean load(String driver) {
        try {
            Class.forName(driver);
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    private String url(String driver) {
        // MariaDB's own connector dropped the jdbc:mysql: scheme in 3.x.
        final String scheme = MARIADB_DRIVER.equals(driver) ? "jdbc:mariadb://" : "jdbc:mysql://";
        return scheme + settings.host + ":" + settings.port + "/" + settings.database;
    }

    private Properties properties() {
        final Properties props = new Properties();
        props.setProperty("user", settings.username);
        props.setProperty("password", settings.password);
        props.setProperty("useSSL", Boolean.toString(settings.usessl));
        props.setProperty("useUnicode", "true");
        props.setProperty("characterEncoding", "utf8");
        // Turns each JDBC batch into a single multi-row statement instead of one per row.
        props.setProperty("rewriteBatchedStatements", "true");
        props.setProperty("connectTimeout", Long.toString(settings.connecttimeout * 1000L));
        props.setProperty("socketTimeout", Long.toString(Math.max(30, settings.connecttimeout * 3L) * 1000L));

        for (String pair : settings.extraproperties.split("&")) {
            final int split = pair.indexOf('=');
            if (split > 0) {
                props.setProperty(pair.substring(0, split).trim(), pair.substring(split + 1).trim());
            }
        }

        return props;
    }

    /** One row of the players table. */
    @SuppressWarnings("ClassCanBeRecord")
    private static final class PlayerRow {
        private final String uuid;
        private final String username;
        private final String ip;
        private final long laston;
        private final long timeplayed;

        private PlayerRow(String uuid, String username, String ip, long laston, long timeplayed) {
            this.uuid = uuid;
            this.username = username;
            this.ip = ip;
            this.laston = laston;
            this.timeplayed = timeplayed;
        }
    }
}

package com.zachduda.puuids;

import com.zachduda.puuids.api.PUUIDS.*;
import com.zachduda.puuids.api.*;
import com.zachduda.puuids.api.VersionManager.VersionTest;
import com.zachduda.puuids.storage.FileStore;
import com.zachduda.puuids.storage.MySQLSettings;
import com.zachduda.puuids.storage.MySQLStorage;
import com.google.common.io.Files;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import space.arim.morepaperlib.MorePaperLib;
import space.arim.morepaperlib.scheduling.ScheduledTask;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends JavaPlugin implements Listener {

    static volatile boolean hasess = false;
    // Read from whatever thread a plugin calls the API on, so it can't be a plain HashMap.
    private static final Map<Plugin, APIVersion> plugins = new ConcurrentHashMap<>();

    // A UUID (or any other key a plugin files data under) becomes a file name.
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private static volatile boolean allowconnections = false;
    private static volatile boolean allow_unsafe_reloads = false;
    public final AtomicInteger getTimes = new AtomicInteger();
    public PUUIDS api;
    public MorePaperLib mpl = new MorePaperLib(this);
    volatile boolean debug = false;
    /*
      Who is holding the file writer. This was one boolean shared by the start-up scan, the reset
      commands, a MySQL import and /puuids togglesave, so whichever finished first switched
      saving back on underneath the others. Each now takes and returns its own hold.
     */
    private final AtomicInteger pauseholders = new AtomicInteger();
    private volatile boolean frozen = false; // /puuids togglesave
    private volatile boolean storeips = true;
    // UUIDs whose puuids file the start-up scan removed, waiting for Essentials to be enabled.
    private final Queue<String> essentialscleanup = new ConcurrentLinkedQueue<>();
    volatile long setTimeMS = 0; // how long in ms for file saving
    final AtomicInteger setTimes = new AtomicInteger();
    volatile long qTimesMS = 0;
    volatile int setQRequests = 0;
    private final String version = Bukkit.getBukkitVersion().replace("-SNAPSHOT", "");
    private volatile boolean sounds;
    private volatile boolean status;
    private volatile String statusreason = "0";
    private volatile boolean updatecheck = true;
    private final boolean isFullySupported = getMCSupportedStatus();
    private ScheduledTask taskresettimer;
    private ScheduledTask playerupdatetimer;
    private volatile int playerupdateseconds = 300;

    // Lower-cased username -> UUID, so name lookups don't have to parse every file on disk.
    private final Map<String, String> nameindex = new ConcurrentHashMap<>();
    // Set once the start-up scan has read every file. From then on the index knows every name
    // on disk, so a miss no longer has to fall back to parsing the whole Data folder.
    private volatile boolean nameindexready = false;

    /** The UUID and username inside a player file, as of when puuids last read or saved it. */
    public record KnownFile(String uuid, String name) {
    }

    // File name (without .yml) -> what's in it. Filled by the start-up scan and kept current by
    // every save, so listing every player's name doesn't mean parsing every file again.
    private final Map<String, KnownFile> knownfiles = new ConcurrentHashMap<>();

    // Optional MySQL mirror of the Data folder. Null whenever it is off or couldn't connect.
    private volatile MySQLStorage storage;

    private Metrics metrics;

    private int mcMajorVersion;
    private int mcMinorVersion;
    private int mcPatchVersion;

    private static final Pattern MC_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private Matcher parseMCVersion(String this_ver) {
        Matcher version = MC_VERSION.matcher(this_ver);
        if (!version.find()) {
            return null;
        }
        mcMajorVersion = Integer.parseInt(version.group(1));
        mcMinorVersion = Integer.parseInt(version.group(2));
        mcPatchVersion = version.group(3) == null ? 0 : Integer.parseInt(version.group(3));
        return version;
    }

    public String getMCVersion(String separator) {
        String this_ver = Bukkit.getBukkitVersion()
                .toUpperCase()
                .replaceAll("-.+$", "");

        if (separator == null) {
            separator = ".";
        }

        Matcher version = parseMCVersion(this_ver);

        if (version == null) {
            getLogger().warning(ChatColor.RED + "Unable to read Minecraft Version: " + this_ver);
            return this_ver;
        }

        debug("Parsed Version -> M:" + mcMajorVersion + " MN: " + mcMinorVersion + " P:" + mcPatchVersion);
        return (version.group(1) + separator + version.group(2));
    }

    public String getMCVersion() {
        return getMCVersion(".");
    }

    public boolean getMCSupportedStatus() {
        // This runs as a field initialiser, before anything else has parsed the version. It
        // used to read the still-zero fields, so it answered false on every server: sounds
        // were always off and the start-up play time check never ran.
        if (mcMajorVersion == 0) {
            parseMCVersion(Bukkit.getBukkitVersion());
        }
        if(mcMajorVersion == 1) {
            if(mcMinorVersion >= 13) {
                return true;
            }
        }
        return mcMajorVersion >= 26;
    }

    public void onEnable() {
        try {
            // The queues are plain java.util.concurrent now, so Multimap is no longer required -
            // checking for it would have disabled the plugin over a class it never touches.
            Class.forName("com.google.common.io.Files");
        } catch (ClassNotFoundException e) {
            getLogger().severe("Missing Google's Util Common IO. This is normally shipped with the server, but is missing here. puuids will now disable...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        status = true;
        statusreason = "0";
        // Held until startupScan() returns it.
        pauseholders.set(0);
        frozen = false;
        pauseSaving();

        // Read the configuration up front: the cleanup scan below depends on it, and reading it
        // from another thread while onEnable was still copying defaults meant File-Cleanup and
        // Debug were both silently off for the whole scan.
        getConfig().options().copyDefaults(true);
        saveConfig();
        updateConfig(true);

        // Before anything can save or delete a player file, so the index never misses one.
        FileStore.setListener(new FileStore.Listener() {
            @Override
            public void saved(File target, FileConfiguration config) {
                indexFile(target, config, true);
            }

            @Override
            public void deleted(File target) {
                knownfiles.remove(fileKey(target));
            }
        });

        /*
          When the database is the master copy - a fresh server joining a network, or one being
          restored - the folder has to be rebuilt before anything reads or cleans it, so the scan
          waits for the import rather than racing it.
         */
        final MySQLStorage atstartup = storage;
        if (atstartup != null && atstartup.settings().importonstartup) {
            getLogger().info("Importing player data from MySQL before start-up checks...");
            atstartup.importAll(line -> Msgs.sendPrefix(Bukkit.getConsoleSender(), line),
                    () -> mpl.scheduling().asyncScheduler().run(this::startupScan));
        } else {
            mpl.scheduling().asyncScheduler().run(this::startupScan);
        }

        plugins.put(this, APIVersion.V4);
        allowconnections = true;

        Bukkit.getServer().getPluginManager().registerEvents(this, this);

        // Idempotent; updateConfig() above already started it at the configured rate. The timer
        // used to be a static final field built during class-load with the hardcoded defaults,
        // so Advanced.Save-Rate-Ticks never had any effect at all.
        Timer.startTimer();

        mpl.scheduling().globalRegionalScheduler().run(() -> {

            if (debug) {
                int total = plugins.size() - 1;
                if (total == 1) {
                    debug("Hooked with " + total + " plugin.");
                } else if (total == 0) {
                    debug("There aren't any plugins hooked with PUUIDS yet.");
                } else {
                    debug("Hooked with " + total + " plugins.");
                }
            }
            if(!getConfig().getBoolean("Advanced.Allow-Post-Startup-Connections")) {
                allowconnections = false;
                debug("Plugin registration window is now locked.");
            }

            // Plugman -- Prevent Reloading
            if (getServer().getPluginManager().isPluginEnabled("PlugMan")) {
                debug("Detected PlugMan...");
                Plugin plugMan = Bukkit.getPluginManager().getPlugin("PlugMan");
                try {
                    List<String> ignoredPlugins = (List<String>) Objects.requireNonNull(plugMan).getClass().getMethod("getIgnoredPlugins").invoke(plugMan);
                    if (!ignoredPlugins.contains("PUUIDs")) {
                        ignoredPlugins.add("PUUIDs");
                        debug("Injecting exception into Plugman...");
                    }
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
                    if(debug) {
                        debug("[Do Not Report] There was an issue when trying to communicate to PlugMan: ");
                        ignored.printStackTrace();
                    }
                }
            }
            // --- End of Plugman Prevention
        });

        if (updatecheck) {
            new Updater(this, mpl).checkForUpdate();
        }

        // Players shouldn't EVER be online when we are starting....
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (!players.isEmpty()) {
            status = false;
            statusreason = " was improperly reloaded. This may damage your player's data files! Please restart your server.";
            Msgs.sendPrefix(Bukkit.getConsoleSender(), "&4&l<!> &c&l&nReloading puuids without a proper restart can severely damage PUUID's player data. PLEASE RESTART YOUR SERVER!");
            for (Player online : players) {
                if (online.isOp() || online.hasPermission("puuids.admin")) {
                    Msgs.sendPrefix(online, "&c&lWARNING: &fPUUIDs has been improperly reloaded. This will cause data loss and possible damage to other plugins.");
                    if (sounds) {
                        online.playSound(online.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2.0F, 2.0F);
                    }
                }
            }
        }

        if (getConfig().getBoolean("Settings.Metrics", true)) {
            metrics = new Metrics(this, 18290);
        } else {
            debug("Metrics have been disabled in the config.yml. Guess we won't support all this hard work today!");
        }

        // Anyone already online (an improper reload) still needs a play time session opened,
        // or nothing would accrue for them until they reconnected.
        for (Player online : players) {
            Timer.startSession(online.getUniqueId());
            keepLoaded(online.getUniqueId());
        }

        taskresettimer = startStatResetTimer();
        playerupdatetimer = startPlayerUpdateTimer();

        mpl.scheduling().asyncScheduler().run(() -> {
            ConnectionOpen coe = new ConnectionOpen();
            Bukkit.getPluginManager().callEvent(coe);
        });
    }

    /**
     * Walks the Data folder once at start-up: drops files that are corrupt or long abandoned,
     * fills the username index, and reconciles play time with the server's own statistic.
     * <p>
     * Runs off the main thread, and always ends by returning its hold on the file writer.
     */
    private void startupScan() {
        try {
            final boolean useclean = getConfig().getBoolean("Settings.File-Cleanup.Enabled", true);
            final boolean cleaness = getConfig().getBoolean("Settings.File-Cleanup.Clean-Essentials", true);
            // Without the default a missing (or mistyped) key read as 0 days, which deletes the
            // entire Data folder on the next start-up.
            final int maxDays = Math.max(1, getConfig().getInt("Settings.File-Cleanup.Max-Days", 365));

            // Turkish and Azeri upper-case "i" to a dotted capital, so plugin names used to be
            // filed under a different key there than on every other server.
            final String language = Locale.getDefault().getLanguage();
            final boolean turkic = language.equals("tr") || language.equals("az");

            final File folder = new File(this.getDataFolder(), File.separator + "Data");
            final File[] cachefiles = folder.exists() ? folder.listFiles() : null;
            if (cachefiles == null) {
                nameindexready = true; // Nothing on disk, so nothing the index could be missing.
                return;
            }

            ArrayList<String> unknownfiles = new ArrayList<>();

            for (File cachefile : cachefiles) {
                String path = cachefile.getPath();

                final File f = new File(path);
                if (!Files.getFileExtension(path).equalsIgnoreCase("yml")) {
                    if (f.getName().toLowerCase().contains("ds_store")) {
                        if (!f.delete()) {
                            debug("Error deleting file:" + f.toPath());
                        }
                        debug("Found macOS .ds_store file in folder. Deleting!");
                    } else if (f.getName().endsWith(FileStore.TEMP_SUFFIX)) {
                        // Left behind by a save that was interrupted; the real file is intact.
                        if (!f.delete()) {
                            debug("Error deleting file:" + f.toPath());
                        }
                        debug("Cleaned up a leftover temporary file: " + f.getName());
                    } else {
                        unknownfiles.add(f.getName());
                    }
                } else {

                    try {
                        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

                        if (!setcache.contains("Last-On") || (!setcache.contains("Username")) || (!setcache.contains("UUID"))) {
                            if(!FileStore.delete(f)) {
                                debug("Error deleting file:" + f.toPath());
                            }
                            debug("Deleted file: " + f.getName() + "... It was invalid!");
                        } else {

                            long daysAgo = Math
                                    .abs(((setcache.getLong("Last-On")) / 86400000) - (System.currentTimeMillis() / 86400000));

                            String playername = setcache.getString("Username");

                            if (daysAgo >= maxDays && useclean) {
                                if(!FileStore.delete(f)) {
                                    debug("Error deleting file:" + f.toPath());
                                }
                                final String expired = setcache.getString("UUID");
                                if (cleaness && expired != null) {
                                    // Essentials isn't enabled this early; see cleanEssentials().
                                    essentialscleanup.add(expired);
                                }
                                if (debug) {
                                    getLogger().info("[Debug] Deleted " + playername + "'s data file because it's " + daysAgo +
                                            " days old. (Max: " + maxDays + " Days)");
                                }
                            } else {
                                /*
                                  The following EnumUtil code call for native Spigot player's ontime contains code from EssentialsX.
                                  https://github.com/EssentialsX/Essentials/blob/3af931740b20507837276f87f9456221653ac43d/Essentials/src/main/java/com/earth2me/essentials/commands/Commandplaytime.java
                                 */
                                final String uuid = setcache.getString("UUID");
                                indexFile(f, setcache, false);
                                long playtime = 0;
                                if(isFullySupported) {
                                    try {
                                        // The statistic counts ticks; Time-Played is in seconds.
                                        // This used to multiply by 50 (giving milliseconds), which
                                        // overwrote every file with a play time 1000x too large.
                                        final long ticks = getServer().getOfflinePlayer(UUID.fromString(Objects.requireNonNull(uuid)))
                                                .getStatistic(EnumUtil.getStatistic("PLAY_ONE_MINUTE", "PLAY_ONE_TICK"));
                                        playtime = ticks / 20L;
                                    } catch (Exception e) {
                                        if(debug) {
                                            debug("Unable to use getStatistic for player playtime:");
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                debug("Spigot playtime for " + playername + " is " + playtime/60 + " minutes");
                                boolean dirty = turkic && migratePluginKeys(setcache);
                                final long puuids_playtime = setcache.getLong("Time-Played");
                                debug("PUUIDS playtime for " + playername + " is " + puuids_playtime/60 + " minutes");
                                if(playtime > puuids_playtime) {
                                    debug("Using native MC playtime for puuids data file for " + playername);
                                    setcache.set("Time-Played", playtime);
                                    dirty = true;
                                }
                                if (dirty) {
                                    FileStore.save(setcache, f);
                                }
                                if (debug) {
                                    if (useclean) {
                                        getLogger().info("[Debug] Keeping " + playername + "'s data file. (" + daysAgo + "/" + maxDays +
                                                ")");
                                    } else {
                                        getLogger().info("[Debug] Found " + playername + "'s data file. (" + daysAgo + " days)");
                                    }
                                }
                            } // end of not too old check.
                        } // end of contains variables check.
                    } catch (Exception err) {
                        status = false;
                        statusreason = "Error when trying to save a player file during the clean-up start cycle.";
                        if (debug) {
                            debug("Error when trying to work with player file: " + f.getName() + ", see below:");
                            err.printStackTrace();
                        }
                    } // end of catch err
                } // end of if not global check
            } // end of For loop

            if (!unknownfiles.isEmpty()) {
                getLogger().warning("Found " + unknownfiles.size() + " unknown files in your Data folder:");
                for (String file : unknownfiles) {
                    debug("   - " + file);
                }
                getLogger().warning("Make sure that the files above weren't misplaced or corrupted.");
                status = false;
                statusreason = "Unknown file was found in your puuids Data folder, please remove the following files: " + unknownfiles;
            }

            unknownfiles.clear();
            nameindexready = true;
        } finally {
            // Saving stays frozen until this returns, however it returns.
            resumeSaving();
        }

        scheduleEssentialsCleanup();

        final MySQLStorage started = storage;
        if (started != null && started.settings().exportonstartup) {
            getLogger().info("Exporting the Data folder to MySQL...");
            started.exportAll(line -> Msgs.sendPrefix(Bukkit.getConsoleSender(), line), null);
        }
    }

    /** Moves plugin sections filed under a dotted capital I to the name every other locale uses. */
    private boolean migratePluginKeys(FileConfiguration data) {
        final ConfigurationSection section = data.getConfigurationSection("Plugins");
        if (section == null) {
            return false;
        }

        boolean changed = false;
        for (String key : new ArrayList<>(section.getKeys(false))) {
            if (key.indexOf('\u0130') < 0) {
                continue;
            }
            final String fixed = key.replace('\u0130', 'I');
            if (!section.contains(fixed)) {
                section.set(fixed, section.get(key));
                section.set(key, null);
                changed = true;
            }
        }
        return changed;
    }

    /*
      File-Cleanup.Clean-Essentials never did anything: puuids loads at STARTUP, before
      Essentials is enabled, so the hook was always off by the time the scan looked. The scan now
      only notes who it removed, and this runs on the first tick - when every plugin is up.
     */
    private void scheduleEssentialsCleanup() {
        if (essentialscleanup.isEmpty()) {
            return;
        }

        mpl.scheduling().globalRegionalScheduler().run(() -> {
            if (!getServer().getPluginManager().isPluginEnabled("Essentials")) {
                essentialscleanup.clear();
                return;
            }
            hasess = true;
            mpl.scheduling().asyncScheduler().run(() -> {
                try {
                    final int cleaned = EssentialsHook.reset(essentialscleanup, this);
                    if (cleaned > 0) {
                        getLogger().info("Removed the Essentials data of " + cleaned + " players whose puuids file had expired.");
                    }
                } catch (Throwable err) {
                    essentialscleanup.clear();
                    getLogger().warning("Unable to clean up Essentials data: " + err);
                }
            });
        });
    }

    /*
      Checkpoints every online player's file so play time survives a crash. A normal quit is
      always exact; this interval only bounds how much of a session is lost if the server dies
      without a shutdown. It used to be hardcoded to 12 seconds despite the comment claiming
      ten minutes, which meant a write per online player every 12 seconds.
     */
    private ScheduledTask startPlayerUpdateTimer() {
        final Duration interval = Duration.ofSeconds(playerupdateseconds);
        return mpl.scheduling().asyncScheduler().runAtFixedRate(() -> {
            debug("Updating any online players data files...");
            for (Player p : Bukkit.getOnlinePlayers()) {
                updateFile(p, false);
            }
            UpdatedPlayerStats ups = new UpdatedPlayerStats();
            Bukkit.getPluginManager().callEvent(ups);
        }, interval, interval);
    }

    // Reset Stats every 12 hours
    private ScheduledTask startStatResetTimer() {
        // Was 864000ms (14 minutes), not the 12 hours the comment promised.
        final Duration interval = Duration.ofHours(12);
        return mpl.scheduling().asyncScheduler().runAtFixedRate(() -> {
            debug("Resetting the puuids debug statistics, it's been over 12 hours...");
            setTimeMS = 0;
            setTimes.set(0);
            getTimes.set(0);
            qTimesMS = 0;
            setQRequests = 0;
        }, interval, interval);
    }
    // End of 12 hour Stat Reset timer

    public void debug(String input) {
        if (debug) {
            getLogger().info("[Debug] " + input);
        }
    }

    public boolean isDebug() {
        return debug;
    }

    /** The MySQL mirror, or null when it is switched off or couldn't connect. */
    public MySQLStorage getStorage() {
        return storage;
    }

    /**
     * Whether the file writer is currently held. Queued changes accumulate while it is, which is
     * how the start-up scan, the reset commands and a MySQL import all keep the folder to
     * themselves.
     */
    public boolean isSavingPaused() {
        return frozen || pauseholders.get() > 0;
    }

    /** Takes a hold on the file writer. Every call must be matched by one {@link #resumeSaving()}. */
    public void pauseSaving() {
        pauseholders.incrementAndGet();
    }

    public void resumeSaving() {
        pauseholders.updateAndGet(held -> Math.max(0, held - 1));
    }

    /** @deprecated One caller's "false" used to undo everyone else's pause. Use {@link #pauseSaving()}. */
    @Deprecated
    public void setSavingPaused(boolean paused) {
        frozen = paused;
    }

    public boolean storeIPs() {
        return storeips;
    }

    /** Whether a UUID handed to the API is safe to use as a file name. */
    public static boolean validKey(String uuid) {
        return uuid != null && VALID_KEY.matcher(uuid).matches();
    }

    /** The key a plugin's values are filed under: its name in upper case, whatever the server's locale. */
    public static String pluginKey(Plugin pl) {
        return pl.getDescription().getName().toUpperCase(Locale.ROOT);
    }

    public File playerFile(String uuid) {
        return new File(new File(getDataFolder(), "Data"), uuid + ".yml");
    }

    /**
     * A player's file for reading, parsed at most once while it stays unchanged on disk. The
     * result is shared: it must not be modified.
     */
    public FileConfiguration readFile(String uuid) {
        if (!validKey(uuid)) {
            return new YamlConfiguration();
        }
        return FileStore.read(playerFile(uuid));
    }

    /**
     * Reads one value for handing to another plugin. Lists and items are copied, and anything
     * else that could be modified is loaded fresh, so nothing a caller does reaches the cache.
     */
    public Object readDetached(String uuid, String path) {
        final Object value = readFile(uuid).get(path);
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character) {
            return value;
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return YamlConfiguration.loadConfiguration(playerFile(uuid)).get(path);
    }

    public boolean isRegistered(Plugin pl) {
        return pl != null && plugins.containsKey(pl);
    }

    public APIVersion getAPIVersion(Plugin pl) {
        return pl == null ? null : plugins.get(pl);
    }

    /**
     * Starts, stops or rebuilds the MySQL mirror so it matches the config. Safe to call on a
     * reload: an unchanged MySQL section leaves the existing connection alone.
     */
    private synchronized void applyStorageConfig() {
        final MySQLSettings updated = MySQLSettings.from(getConfig());
        final MySQLStorage current = storage;

        if (current != null && current.settings().sameAs(updated)) {
            return;
        }

        if (current != null) {
            storage = null;
            current.shutdown();
        }

        if (!updated.enabled) {
            return;
        }

        final MySQLStorage created = new MySQLStorage(this, updated);
        if (created.start()) {
            storage = created;
        }
    }

    public void onDisable() {
        final long start = System.currentTimeMillis();

        ConnectionClose cce = new ConnectionClose();
        Bukkit.getPluginManager().callEvent(cce);

        allowconnections = false;

        // Stop the repeating tasks before flushing, so nothing queues more work behind us.
        // These used to be cancelled by passing a ScheduledTask's hashCode to the Bukkit
        // scheduler as if it were a task id, which cancelled nothing of ours (and could well
        // have cancelled another plugin's task that happened to hold that id).
        if (taskresettimer != null) {
            taskresettimer.cancel();
            taskresettimer = null;
        }
        if (playerupdatetimer != null) {
            playerupdatetimer.cancel();
            playerupdatetimer = null;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            Cooldowns.clearAll(p.getUniqueId());
            updateFile(p, true);
        }

        Timer.stopTimer();

        // After the file writer, so every last change it made is mirrored before we disconnect.
        final MySQLStorage closing = storage;
        storage = null;
        if (closing != null) {
            closing.shutdown();
        }

        mpl.scheduling().cancelGlobalTasks();

        plugins.clear();
        nameindex.clear();
        knownfiles.clear();
        FileStore.setListener(null);
        FileStore.unpinAll();
        FileStore.clearCache();

        setTimeMS = 0;
        setTimes.set(0);
        getTimes.set(0);
        qTimesMS = 0;
        setQRequests = 0;

        if(metrics != null) {
            metrics.shutdown();
        }
        getLogger().info("Successfully disabled in " + (System.currentTimeMillis() - start) + "ms");
    }

    private synchronized void reconnectStorage() {
        final MySQLStorage current = storage;
        if (current != null) {
            storage = null;
            current.shutdown();
        }
        applyStorageConfig();
    }

    private void updateConfig(boolean startup) {
        int conf_ver = 0;
        if(getConfig().contains("Version")) {
            conf_ver = getConfig().getInt("Version");
        }
        debug("Configuration version is: " + conf_ver);

        // The id is read back from Advanced.UUID, but used to be written to a top-level "UUID"
        // key - so getServerId() always answered "0" and a fresh id was generated every start-up.
        if(Objects.equals(getConfig().getString("Advanced.UUID"), "0") || getConfig().getString("Advanced.UUID") == null) {
            debug("Generating new server UUID for saving...");
            getConfig().set("Advanced.UUID", UUID.randomUUID().toString());
        }

        if(conf_ver < 2) {
            getConfig().set("Version", 2);
        }

        saveConfig();
        reloadConfig();

        // End of setting -----

        debug = getConfig().getBoolean("Settings.Debug", false);
        updatecheck = getConfig().getBoolean("Settings.Update-Checking", true);
        Msgs.prefix = getConfig().getString("Settings.Prefix", "&8[&e&lPUUIDs&8]");

        if (getConfig().getLong("Advanced.Save-Rate-Ticks", 10) > 0) {
            Timer.processrate = getConfig().getLong("Advanced.Save-Rate-Ticks", 10);
        } else {
            Timer.processrate = 10;
            getLogger().warning("Save Rate was set to 0 ticks in the config, this will cause damage. Defaulting to 10 ticks.");
        }

        if (getConfig().getInt("Advanced.Max-Processes-Per-Queue", 25) > 0) {
            Timer.sizelimit = getConfig().getInt("Advanced.Max-Processes-Per-Queue", 25);
        } else {
            // This used to reset processrate instead of sizelimit, so a queue size of 0 both
            // kept the broken limit and quietly changed the save rate.
            Timer.sizelimit = 25;
            getLogger().warning("Max-Processes-Per-Queue was set to 0 in the config, this will prevent data from being set. Defaulting to 25.");
        }

        playerupdateseconds = Math.max(5, getConfig().getInt("Advanced.Player-Update-Seconds", 300));

        // Picks up a changed Save-Rate-Ticks on /puuids reload. No-op before the timer exists.
        Timer.startTimer();

        if (playerupdatetimer != null) {
            // Only on reload; during onEnable the timer hasn't been created yet.
            playerupdatetimer.cancel();
            playerupdatetimer = startPlayerUpdateTimer();
        }

        if (isFullySupported) {
            sounds = true;
        } else {
            sounds = false;
            debug("Sounds have been disabled, this is an older version of Minecraft.");
        }

        // Assigned either way: turning the option back off and reloading used to leave unsafe
        // reloads permitted until the next restart.
        allow_unsafe_reloads = getConfig().getBoolean("Advanced.Allow-Unsafe-Reloads", false);
        if(allow_unsafe_reloads) {
            getLogger().warning("Unsafe reloading (via /rl, /reload) is permitted per config.yml. This is VERY dangerous! You will get no support for corrupted files.");
        }

        storeips = getConfig().getBoolean("Settings.Store-IPs", true);
        FileStore.setCacheSize(getConfig().getInt("Advanced.Cache-Size", 500));
        FileStore.setRevalidateSeconds(getConfig().getInt("Advanced.Cache-Revalidate-Seconds", 5));

        if (startup) {
            // Start-up waits on the database once: an import has to finish before the scan.
            applyStorageConfig();
        } else {
            // A reload must not: connecting can take the whole Connection-Timeout, and closing
            // the old connection flushes its queue first. Both used to freeze the main thread.
            mpl.scheduling().asyncScheduler().run(this::applyStorageConfig);
        }
    }


    /** A copy of the connected plugins. Use {@link #isRegistered(Plugin)} to check for one. */
    public HashMap<Plugin, APIVersion> getPlugins() {
        return new HashMap<>(plugins);
    }

    public boolean connect(Plugin pl, APIVersion vers) {
        if (pl == null) {
            debug("A plugin tried to register with puuids as 'null', this is not allowed.");
            return false;
        }

        String plname = pl.getName();

        VersionTest vt = VersionManager.checks(plname, vers);
        if (vt == VersionTest.FAIL || vers == null) {
            // Plugin will NOT work with this version.
            getLogger().severe("Plugin " + plname + " is unable to use puuids, for they are using an outdated version.");
            return false;
        }

        if (vt == VersionTest.LEGACY) {
            // Plugin may not be 100% compatiable.
            getLogger().warning(plname + " needs to update their plugin to work better with puuids");
        } else {
            debug(plname + " has been compiled with the latest puuids version.");
            // Passed Version test for FULL support.
        }


        if (!allowconnections) {
            if (!plugins.containsKey(pl)) {
                getLogger().warning("Plugin '" + plname + "' tried to register with puuids after the connection window.");
            } else {
                debug(plname + " was reloaded improperly and send another hook request. Ignoring.");
            }
            return false;
        }

        if (!plugins.containsKey(pl)) {
            plugins.put(pl, vers);
            debug("Plugin " + plname + " has been registered.");

            mpl.scheduling().asyncScheduler().run(() -> {
                PluginRegistered plr = new PluginRegistered(plname);
                Bukkit.getPluginManager().callEvent(plr);
            });
            return true;
        }

        status = false;
        statusreason = "Plugin " + plname + " tried to overwrite another plugin with the exact same name. Please contact " + pl.getDescription().getAuthors() + ". This is not puuids fault.";
        getLogger().warning(statusreason);
        return false;
    }

    public String getServerId() {
        return getConfig().getString("Advanced.UUID");
    }

    public int set(Plugin pl, String uuid, String loc, Object obj) {
        final String plname = pluginKey(pl);
        if (!isRegistered(pl)) {
            debug("Not allowing " + plname + " to access data. They didn't connect properly.");
            return 0;
        }

        return Timer.queueSet(plname, uuid, loc, obj);
    }

    public int set(Plugin pl, String uuid, String loc, List<?> obj) {
        final String plname = pluginKey(pl);
        if (!isRegistered(pl)) {
            debug("Not allowing " + plname + " to access data. They didn't connect properly.");
            return 0;
        }

        return Timer.queueSet(plname, uuid, loc, obj);
    }


    // ONLY for setting Null info
    public int set(Plugin pl, String uuid, Object should_be_null) {
        final String plname = pluginKey(pl);
        if (!isRegistered(pl)) {
            debug("Not allowing " + plname + " to access data. They didn't connect properly.");
            return 0;
        }

        if (should_be_null != null) {
            return 0;
        }

        return Timer.queueSet(plname, uuid, "PUUIDS_SET_AS_ALL_NULL", null);
    }

    /** Remembers a username so the next lookup for it doesn't have to scan the data folder. */
    public void indexName(String name, String uuid) {
        if (name == null || uuid == null) {
            return;
        }
        nameindex.put(name.toLowerCase(Locale.ROOT), uuid);
    }

    private static String fileKey(File f) {
        final String name = f.getName();
        return name.length() > 4 ? name.substring(0, name.length() - 4) : name;
    }

    /**
     * Records what a player file says about its player, for the name index and the listings.
     *
     * @param saved true when this is what was just written. Otherwise it was read, possibly just
     *              before a save on another thread, so it never replaces what that save recorded.
     */
    private KnownFile indexFile(File f, FileConfiguration config, boolean saved) {
        final KnownFile read = new KnownFile(config.getString("UUID"), config.getString("Username"));
        final KnownFile kept;
        if (saved) {
            knownfiles.put(fileKey(f), read);
            kept = read;
        } else {
            final KnownFile earlier = knownfiles.putIfAbsent(fileKey(f), read);
            kept = earlier == null ? read : earlier;
        }
        indexName(kept.name(), kept.uuid());
        return kept;
    }

    /**
     * The UUID and username inside a player file, answered from memory once puuids has seen the
     * file (at start-up, or when it last saved it), and only parsed from disk otherwise.
     */
    public KnownFile knownFile(File f) {
        final KnownFile known = knownfiles.get(fileKey(f));
        if (known != null) {
            return known;
        }
        final FileConfiguration config = FileStore.peek(f);
        if (!config.contains("UUID")) {
            // Not a valid player file (or already gone), so nothing worth remembering.
            return new KnownFile(config.getString("UUID"), config.getString("Username"));
        }
        return indexFile(f, config, false);
    }

    /**
     * Keeps an online player's file in memory until they quit, loading it now (off the main
     * thread) so their first read doesn't have to.
     */
    private void keepLoaded(UUID uuid) {
        final String key = uuid.toString();
        FileStore.pin(playerFile(key));
        mpl.scheduling().asyncScheduler().run(() -> readFile(key));
    }

    /*
      This used to load and parse every file in the data folder on the calling thread, which is
      the main thread for /puuids ontime <player> and for the PUUIDS.getUUID(name) API. On a
      server with thousands of player files that's a visible freeze on every single call.
      The index answers the common case outright; a miss still falls back to the full scan.
     */
    public String nametoUUID(String inputsearch) {
        if (inputsearch == null) {
            return "0";
        }

        final String key = inputsearch.toLowerCase(Locale.ROOT);
        final File folder = new File(this.getDataFolder(), File.separator + "Data");

        final String cached = nameindex.get(key);
        if (cached != null) {
            // Confirm against the one file it points at, in case they've since changed name.
            if (inputsearch.equalsIgnoreCase(readFile(cached).getString("Username"))) {
                return cached;
            }
            nameindex.remove(key, cached);
        }

        if (nameindexready) {
            return "0";
        }

        final File[] files = folder.exists() ? folder.listFiles() : null;
        if (files == null) {
            return "0";
        }

        for (File AllData : files) {
            File f = new File(AllData.getPath());
            if (!f.getName().endsWith(".yml")) {
                continue;
            }

            // Indexes the file as a side effect, so the next lookup for this name is a map hit.
            final KnownFile known = knownFile(f);
            String playername = known.name();
            String UUID = known.uuid();

            if (inputsearch.equalsIgnoreCase(playername)) {
                return UUID;
            }
        }

        return "0";
    }

    public boolean hasPlayedUUID(String uuid) {
        return validKey(uuid) && playerFile(uuid).exists();
    }

    public boolean hasPlayedName(String name) {
        return !Objects.equals(nametoUUID(name), "0");
    }

    public long getLastOn(String uuid) {
        return readFile(uuid).getLong("Last-On");
    }

    /**
     * Total play time in seconds, including the part of a live session that hasn't been
     * written to file yet. Without that, asking an online player how long they'd played
     * reported whatever was last checkpointed rather than the truth.
     */
    public long getPlayTime(String uuid) {
        if (uuid == null) {
            return 0;
        }

        long played = readFile(uuid).getLong("Time-Played");

        try {
            played += Timer.liveSessionSeconds(UUID.fromString(uuid));
        } catch (IllegalArgumentException notAUuid) {
            // Nothing live to add for a malformed UUID.
        }

        return played;
    }

    public String getPlayerIP(String uuid) {
        if (!storeips) {
            return "0";
        }
        final String ip = readFile(uuid).getString("IP");
        return ip == null ? "0" : ip;
    }

    public boolean allowConnections() {
        return allowconnections;
    }

    public String UUIDtoname(String inputsearch) {
        if (!hasPlayedUUID(inputsearch)) {
            return "0";
        }
        return readFile(inputsearch).getString("Username");
    }


    /** listFiles() answers null if the folder vanished; a reset has to finish (and unpause) regardless. */
    private static File[] listData(File folder) {
        final File[] files = folder.listFiles();
        return files == null ? new File[0] : files;
    }

    private void updateFile(Player p, boolean quit) {
        Timer.queuePlayerUpdate(p, quit);
    }

    /*
        Mmm yes unexpected halt in delicate data timer make server go brrrrrr
        The bozo prevent-i-nator-inator 9000
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreventBozos(PlayerCommandPreprocessEvent e) {
        if (allow_unsafe_reloads || !isReloadCommand(e.getMessage())) {
            return;
        }
        final Player p = e.getPlayer();
        if (!p.isOp() && !p.hasPermission("bukkit.command.reload")) {
            return; // The server turns them away itself; they don't need to hear about puuids.
        }
        Msgs.sendPrefix(p, "&fThis will &c&lcorrupt&f your servers running memory, including PUUIDs'.");
        bass(p);
        e.setCancelled(true);
    }

    // Most reloads are typed into the console (or sent over RCON), which nothing used to cover.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreventConsoleBozos(ServerCommandEvent e) {
        if (allow_unsafe_reloads || !isReloadCommand(e.getCommand())) {
            return;
        }
        Msgs.sendPrefix(e.getSender(), "&fReloading will &c&lcorrupt&f your servers running memory, including PUUIDs'. "
                + "Restart instead, or set &7Advanced.Allow-Unsafe-Reloads&f to true.");
        e.setCancelled(true);
    }

    /*
      /restart is no longer treated as a reload: it stops the server and starts it again, which
      disables puuids cleanly. "/bukkit:reload" used to slip through, as only the word "bukkit"
      was stripped and ":reload" was left behind.
     */
    private static boolean isReloadCommand(String raw) {
        if (raw == null) {
            return false;
        }
        String cmd = raw.trim().split(" ")[0].toLowerCase(Locale.ROOT);
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        if (cmd.startsWith("bukkit:")) {
            cmd = cmd.substring("bukkit:".length());
        }
        return cmd.equals("reload") || cmd.equals("rl");
    }

    /*
      Already off the main thread, and before the join: a good moment to parse the file that
      the player's first scoreboard or chat format is about to read. Only for logins that will
      go ahead; MONITOR runs after every other plugin's say on that.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            readFile(e.getUniqueId().toString());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        final Player joining = e.getPlayer();

        // Open the play time session here and unconditionally: it has to happen even when the
        // file refresh below is skipped by the cooldown, or a quick reconnect accrues nothing.
        Timer.startSession(joining.getUniqueId());

        // Their file stays in memory while they're online (usually already loaded at pre-login).
        keepLoaded(joining.getUniqueId());

        // On a network the player may have been on another server since we last saw them, so
        // their file is refreshed from the shared database before anything reads it.
        final MySQLStorage db = storage;
        if (db != null && db.isConnected() && db.settings().synconjoin) {
            db.pullPlayer(joining.getUniqueId().toString());
        }

        mpl.scheduling().asyncScheduler().run(() -> {
            UUID uuid = joining.getUniqueId();
            if (Cooldowns.recentlyJoined(uuid)) {
                debug(joining.getName() + "'s file won't be refreshed, it was updated less than 60s ago. [Join]");
                return;
            }

            Cooldowns.justJoined(uuid);
            updateFile(joining, false);

            if (updatecheck) {
                if (joining.hasPermission("puuids.admin") || joining.isOp()) {
                    if (Updater.outdated) {
                        try {
                            Msgs.sendPrefix(joining, "&c&lOutdated Plugin! &7Running v" + getDescription().getVersion() +
                                    " while the latest is &f&l" + Updater.posted_version);
                            pop(joining);
                        } catch (Exception err) {
                            sounds = false;
                            debug("Error on update notif. on join: ");
                            err.printStackTrace();
                        }
                    }
                }
            }

            if (joining.getUniqueId().toString().equals("6191ff85-e092-4e9a-94bd-63df409c2079")) {
                Msgs.send(joining, "&7This server is running &fPUUIDs &6v" + getDescription().getVersion() +
                        " &7for " + getMCVersion());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        /*
          The quit write is never skipped any more. It used to be suppressed whenever the player
          had joined within the cooldown window, which threw away the whole session: the quit
          write is the only thing that closes the session and banks the time.
         */
        updateFile(p, true);
        Cooldowns.justJoined(uuid);
        Cooldowns.clearConfirm(uuid);
        Cooldowns.forgetOnTime(uuid);

        // Free to age out of the cache now, but counted as just used, so a quick rejoin (and the
        // quit write above, which updates the cached copy) still finds it in memory.
        FileStore.unpin(playerFile(uuid.toString()));
    }

    private String randomString() {
        int leftLimit = 97; // A
        int rightLimit = 122; // to Z
        int length = 5;
        Random random = new Random();
        StringBuilder buffer = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomLimitedInt = leftLimit + (int)
                    (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }

    private void bass(CommandSender sender) {
        try {
            if (!sounds) {
                return;
            }

            if (!(sender instanceof Player p)) {
                return;
            }

            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 2.0F, 1.3F);

        } catch (Exception err) {
            sounds = false;
        }
    }

    private void pop(CommandSender sender) {
        try {
            if (!sounds) {
                return;
            }

            if (!(sender instanceof Player p)) {
                return;
            }

            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 2.0F, 2.0F);

        } catch (Exception err) {
            sounds = false;
        }
    }

    private void thinking(CommandSender sender) {
        try {
            if (!sounds) {
                return;
            }

            if (!(sender instanceof Player p)) {
                return;
            }

            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_FRAME_PLACE, 2.0F, 2.0F);
        } catch (Exception err) {
            sounds = false;
        }
    }

    private void noPermission(CommandSender sender) {
        Msgs.sendPrefix(sender, getConfig().getString("Messages.No-Permission"));
        bass(sender);
    }

    public boolean onCommand(@NonNull CommandSender sender, Command cmd, @NonNull String cmdLabel, String[] args) {
        if (cmd.getName().equalsIgnoreCase("puuids")) {
            if (!sender.hasPermission("puuids.admin") && !sender.isOp()) {
                noPermission(sender);
                return true;
            }

            if (args.length == 0) {
                Msgs.send(sender, "");
                Msgs.send(sender, "#4ec483&lPUUIDs");
                Msgs.send(sender, "&8&l> &f&o/puuids help &7&ofor commands & help.");
                Msgs.send(sender, "");
                pop(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("help")) {
                Msgs.send(sender, "");
                Msgs.send(sender, "#4ec483&lPUUIDs");
                Msgs.send(sender, "&8&l> &f&l/puuids version &7Get the current version of your puuids system.");
                Msgs.send(sender, "&8&l> &f&l/puuids ontime (player) &7See how long you or someone else been playing.");
                Msgs.send(sender, "&8&l> &f&l/puuids reload &7Reload your config.yml.");
                Msgs.send(sender, "&8&l> &f&l/puuids info &7Shows you how fast/slow your system is running.");
                if (debug) {
                    Msgs.send(sender, "&8&l> &f&l/puuids debug &7Shows detailed system information.");
                    Msgs.send(sender, "&8&l> &f&l/puuids reset all &7Resets everything except UUIDs/IPs/Names");
                    Msgs.send(sender, "&8&l> &f&l/puuids reset ontime &7Set everyone's total play-time back to 0.");
                }
                Msgs.send(sender, "&8&l> &f&l/puuids plugins &7Shows connected plugins.");
                Msgs.send(sender, "&8&l> &f&l/puuids mysql &7Check on (or re-sync) the MySQL mirror.");
                Msgs.send(sender, "");
                pop(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("mysql")) {
                return mysqlCommand(sender, args);
            }

            if (args[0].equalsIgnoreCase("plugins")) {
                if (plugins.isEmpty()) {
                    pop(sender);
                    Msgs.sendPrefix(sender, "&fThere are &7&lNo Connected Plugins&f currently.");
                    return true;
                }
                final int size = plugins.size() - 1;
                if(size < 1) {
                    Msgs.sendPrefix(sender, "&fThere are no plugins currently connected to PUUIDs.");
                } else {
                    Msgs.sendPrefix(sender, "&fThere are &6&l" + size + " &fplugins connected:");
                    for (HashMap.Entry<Plugin, APIVersion> entry : plugins.entrySet()) {
                        final String plname = entry.getKey().getDescription().getName();
                        if (!plname.equalsIgnoreCase("puuids")) {
                            Msgs.send(sender, "&r     &8&l> &e&l" + plname);
                        }
                    }
                }
                pop(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("debug")) {
                if (!debug) {
                    bass(sender);
                    Msgs.send(sender, "&7");
                    Msgs.send(sender, "#4ec483&lPUUIDs");
                    Msgs.send(sender, "&8&l> &c&lCommand Disabled. &fTo enable, please turn on debug mode.");
                    Msgs.send(sender, "&7");
                    return true;
                }
                mpl.scheduling().asyncScheduler().run(() -> {
                    double jversion = Double.parseDouble(System.getProperty("java.specification.version"));
                    StringBuilder sb = getStringBuilder();

                    String size = Integer.toString((plugins.size() - 1));

                    Msgs.send(sender, "&f");
                    if (size.equals("0")) {
                        Msgs.send(sender, "&7There are no plugins connected.");
                    } else {
                        Msgs.send(sender, "&6" + size + " &fConnected Plugins: &e" + sb);
                    }
                    Msgs.send(sender, "Java: &e" + jversion);
                    Msgs.send(sender, "&fConfig Process Rate: &e" + Timer.processrate);
                    Msgs.send(sender, "&fConfig Q Max Size: &e" + Timer.sizelimit);
                    Msgs.send(sender, "&fSingle Set Time: &e" + setTimeMS + "ms");
                    Msgs.send(sender, "&fQ Process Time: &e" + qTimesMS + "ms");
                    try {
                        Class.forName("com.destroystokyo.paper.PaperConfig");
                        Msgs.send(sender, "&fPaper Version: &e" + version);
                    } catch (Exception NotPaper) {
                        try {
                            Class.forName("org.spigotmc.SpigotConfig");
                            Msgs.send(sender, "&fSpigot Version: &e" + version);
                        } catch (Exception Other) {
                            Msgs.send(sender, "&fBukkit Version: &e" + version);
                        }
                    }
                    Msgs.send(sender, "&fTotal Sets: &e" + setTimes.get());
                    Msgs.send(sender, "&fTotal Gets: &e" + getTimes.get());

                    try {
                        Runtime r = Runtime.getRuntime();
                        long memUsed = (r.totalMemory() - r.freeMemory()) / 1048576;
                        long maxMem = (r.maxMemory() / 1048576);
                        Msgs.send(sender, "&fRAM: &e" + memUsed + "mb &8/ &e" + maxMem + "mb");
                    } catch (Exception err) {
                        Msgs.send(sender, "&fRAM: &7Readings Not Available");
                    }

                    try {
                        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                        ObjectName name = ObjectName.getInstance("java.lang:type=OperatingSystem");
                        AttributeList list = mbs.getAttributes(name, new String[]{
                                "ProcessCpuLoad"
                        });
                        Attribute att = (Attribute) list.getFirst();
                        Double value = (Double) att.getValue();
                        // ProcessCpuLoad is 0.0 - 1.0 (or negative when unavailable).
                        if (value < 0) {
                            throw new IllegalStateException("CPU load unavailable");
                        }
                        long cpu = Math.round(value * 100);
                        Msgs.send(sender, "&fCPU: &e" + cpu + "%");
                    } catch (Exception err) {
                        Msgs.send(sender, "&fCPU: &7Readings Not Available");
                    }
                    if (!Objects.equals(statusreason, "0")) {
                        Msgs.send(sender, "&fLatest Issue: &e" + statusreason);
                    }
                    pop(sender);
                });
                return true;
            }

            if (args[0].equalsIgnoreCase("togglesave")) {
                if (!debug) {
                    bass(sender);
                    Msgs.send(sender, "&7");
                    Msgs.send(sender, "#4ec483&lPUUIDs");
                    Msgs.send(sender, "&8&l> &c&lCommand Disabled. &fTo enable, please turn on debug mode.");
                    Msgs.send(sender, "&7");
                    return true;
                }

                if (!(sender instanceof Player p)) {
                    Msgs.sendPrefix(sender, "&6&lFOR SECURITY REASONS: &fOnly a player with permission & op may run this command.");
                    return true;
                }

                if (!p.hasPermission("puuids.admin") || !p.isOp()) {
                    bass(p);
                    Msgs.sendPrefix(p, "&6&lFor Safety: &fYou must have the &7puuids.admin&f permission & be OP to do this.");
                    return true;
                }

                String pendingkey = Cooldowns.confirmKey(p.getUniqueId());

                if (pendingkey == null) {
                    String key = randomString();
                    thinking(p);
                    Msgs.sendPrefix(p, "&c&lARE YOU SURE? &fThis may corrupt data. Do &7&l/puuids togglesave " + key + "&f in 10s to confirm.");
                    Cooldowns.confirm(p.getUniqueId(), key);
                    return true;
                } else {
                    // Has reset all confirmation key active v v v
                    if (args.length == 1) {
                        Msgs.sendPrefix(p, "&c&lARE YOU SURE? &fType &7&l/puuids togglesave " + pendingkey + "&f to confirm.");
                        thinking(p);
                        return true;
                    } else {
                        if (!args[1].equalsIgnoreCase(pendingkey)) {
                            bass(p);
                            Msgs.sendPrefix(p, "&6&lToggle Saving Canceled. &fThat was an invalid confirmation key.");
                            Cooldowns.clearConfirm(p.getUniqueId());
                            return true;
                        }
                    }
                }

                Msgs.send(p, "&7");
                Msgs.send(p, "#4ec483&lPUUIDs");
                if (frozen) {
                    Msgs.send(p, "&8&l> &a&lUnfrozen. &fNow processing saving requests...");
                    frozen = false;
                } else {
                    frozen = true;
                    Msgs.send(p, "&8&l> &c&lFrozen. &fRequests will be Q'ed but not saved.");
                }
                Msgs.send(p, "&7");
                pop(p);
                Cooldowns.clearConfirm(p.getUniqueId());
                return true;
            }

            if (args[0].equalsIgnoreCase("reset")) {
                if (!debug) {
                    bass(sender);
                    Msgs.send(sender, "&7");
                    Msgs.send(sender, "#4ec483&lPUUIDs");
                    Msgs.send(sender, "&8&l> &c&lCommand Disabled. &fTo enable, please turn on debug mode.");
                    Msgs.send(sender, "&7");
                    return true;
                }

                if (!Cooldowns.canRunLargeTask()) {
                    bass(sender);
                    Msgs.sendPrefix(sender, "&6&lPlease Wait. &fRunning large tasks this quickly can have a negative impact on your server's performance.");
                    return true;
                }

                if (args.length == 1) {
                    bass(sender);
                    Msgs.sendPrefix(sender, "&c&lOops. &fYou must provide what to reset: &7/puuids reset &f(all/ontime)");
                    return true;
                }

                if (!args[1].equalsIgnoreCase("ontime") && !args[1].equalsIgnoreCase("all")) {
                    bass(sender);
                    Msgs.sendPrefix(sender, "&c&lOops. &fYou can't reset &7&l" + args[1] + "&f only: &7&lall&f or &7&lontime");
                    return true;
                }

                File folder = new File(this.getDataFolder(), File.separator + "Data");

                if (!folder.exists()) {
                    bass(sender);
                    Msgs.sendPrefix(sender, "&c&lData Folder Missing. &fThere is no data to remove here.");
                    return true;
                }

                if (args[1].equalsIgnoreCase("ontime")) {
                    Cooldowns.startLargeTask();
                    Msgs.send(sender, "&7");
                    Msgs.send(sender, "#4ec483&lPUUIDs");
                    Msgs.send(sender, "&8&l> &7&oPlease wait... this may take a long time.");
                    Msgs.send(sender, "&7");
                    thinking(sender);

                    pauseSaving();

                    mpl.scheduling().asyncScheduler().run(() -> {
                        int total = 0;
                        final long start = System.currentTimeMillis();
                        final MySQLStorage db = storage;
                        for (File AllData : listData(folder)) {
                            File f = new File(AllData.getPath());
                            if (!f.getName().endsWith(".yml")) {
                                continue;
                            }

                            synchronized (FileStore.LOCK) {
                            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                            debug("Reset " + setcache.getString("Username") + "'s Time-Played. (" + setcache.getLong("Time-Played") + "secs) " + " (" + f.getName() + ")");
                            setcache.set("Time-Played", 0);

                            try {
                                FileStore.save(setcache, f);
                                if (db != null && db.isConnected()) {
                                    // Or the database would hand the old play time straight back
                                    // on the next sync.
                                    db.mirrorPlayer(setcache.getString("UUID"), setcache.getString("Username"),
                                            setcache.getString("IP"), setcache.getLong("Last-On"), 0);
                                }
                            } catch (Exception err) {
                                debug("Unable to reset " + f.getName() + ": " + err);
                            }
                            }
                            total++;
                        }

                        final String finished = Long.toString(System.currentTimeMillis() - start);

                        getLogger().info("Reset " + total + " players Time-Played stats on file. (Done in " + finished + "ms)");
                        resumeSaving();

                        Msgs.send(sender, "");
                        Msgs.send(sender, "#4ec483&lPUUIDs");
                        Msgs.send(sender, "&8&l> &a&lDone. &fReset everyone's Time-Played back to zero in &7&l" + finished + "ms");
                        Msgs.send(sender, "");
                        pop(sender);

                        Cooldowns.endLargeTask();
                    });
                    return true;
                }

                if (args[1].equalsIgnoreCase("all")) {
                    if (!(sender instanceof Player p)) {
                        Msgs.sendPrefix(sender, "&6&lFOR SECURITY REASONS: &fOnly a player with permission & op may run this command.");
                        return true;
                    }

                    if (!p.hasPermission("puuids.admin") || !p.isOp()) {
                        bass(p);
                        Msgs.sendPrefix(p, "&6&lFor Safety: &fYou must have the &7puuids.admin&f permission & be OP to do this.");
                        return true;
                    }

                    String pendingkey = Cooldowns.confirmKey(p.getUniqueId());

                    if (pendingkey == null) {
                        String key = randomString();
                        thinking(p);
                        Msgs.sendPrefix(p, "&c&lARE YOU SURE? &fThis will erase ALL player data from your PUUID's data folder. Do &7&l/puuids reset all " + key + "&f in 10s to confirm.");
                        Cooldowns.confirm(p.getUniqueId(), key);
                        return true;
                    } else {
                        // Has reset all confirmation key active v v v
                        if (args.length == 2) {
                            Msgs.sendPrefix(p, "&c&lARE YOU SURE? &fType &7&l/puuids reset all " + pendingkey + "&f to confirm.");
                            thinking(p);
                            return true;
                        } else {
                            if (!args[2].equalsIgnoreCase(pendingkey)) {
                                bass(p);
                                Msgs.sendPrefix(p, "&6&lReset Canceled. &fThat was an invalid reset key.");
                                Cooldowns.clearConfirm(p.getUniqueId());
                                return true;
                            }
                        }
                    }

                    pauseSaving();

                    Cooldowns.startLargeTask();
                    Msgs.send(sender, "&7");
                    Msgs.send(sender, "#4ec483&lPUUIDs");
                    Msgs.send(sender, "&8&l> &7&oPlease wait... this may take a long time.");
                    Msgs.send(sender, "&7");
                    thinking(sender);

                    mpl.scheduling().asyncScheduler().run(() -> {
                        int total = 0;
                        final long start = System.currentTimeMillis();
                        final List<String> wiped = new ArrayList<>();
                        for (File AllData : listData(folder)) {
                            File f = new File(AllData.getPath());
                            if (!f.getName().endsWith(".yml")) {
                                continue;
                            }

                            synchronized (FileStore.LOCK) {
                            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                            setcache.set("Plugins", null);

                            debug("Reset" + setcache.getString("Username") + "'s file back to basics. (" + f.getName() + ")");

                            try {
                                FileStore.save(setcache, f);
                                wiped.add(setcache.getString("UUID"));
                            } catch (Exception err) {
                                debug("Unable to reset " + f.getName() + ": " + err);
                            }
                            }
                            total++;
                        }

                        final MySQLStorage db = storage;
                        if (db != null && db.isConnected()) {
                            // The same rows have to go from MySQL, or a later import or join
                            // sync would put everything that was just erased back again.
                            db.clearPluginData(wiped, line -> Msgs.sendPrefix(sender, line));
                        }

                        final String finished = Long.toString(System.currentTimeMillis() - start);

                        getLogger().info("Reset " + total + " players files back to basics. (Done in " + finished + "ms)");
                        resumeSaving();

                        Msgs.send(sender, "");
                        Msgs.send(sender, "#4ec483&lPUUIDs");
                        Msgs.send(sender, "&8&l> &a&lDone. &fCleared player files back to basics in &7&l" + finished + "ms");
                        Msgs.send(sender, "");
                        pop(sender);

                        Cooldowns.endLargeTask();
                    });
                    return true;
                }

                Msgs.send(sender, "");
                Msgs.send(sender, "#4ec483&lPUUIDs");
                Msgs.send(sender, "&8&l> &c&lError. &fSomething went wrong here.");
                Msgs.send(sender, "");
                bass(sender);
                status = false;
                statusreason = "Unknown issue when trying to clear player file data via cmd.";
                return true;
            }


            if (args[0].equalsIgnoreCase("version")) {
                Msgs.send(sender, "");
                Msgs.send(sender, "#4ec483&lPUUIDs");
                Msgs.send(sender, "&8&l> &7You are currently running &f&lv" + getDescription().getVersion());
                Msgs.send(sender, "");
                pop(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                final long start = System.currentTimeMillis();
                reloadConfig();
                updateConfig(false);
                Msgs.send(sender, "");
                Msgs.send(sender, "#4ec483&lPUUIDs");
                Msgs.send(sender, "&8&l> &fConfiguration has been reloaded in &6" + (System.currentTimeMillis() - start) + "ms");
                Msgs.send(sender, "");
                pop(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("ontime")) {
                // Need to optimize, TOO many sender instanceof Player stuffs.

                if (sender instanceof Player p) {
                    if (Cooldowns.onTimeCooling(p.getUniqueId())) {
                        Msgs.sendPrefix(sender, "&c&lSlow Down. &fPlease wait before checking that again.");
                        bass(p);
                        return true;
                    }
                }

                if (args.length == 1) {
                    if (!(sender instanceof Player p)) {
                        Msgs.sendPrefix(sender, "&c&lOops. &fYou must specify a player: &7&l/puuids ontime (player)");
                        return true;
                    }
                    Msgs.sendPrefix(sender, "&6So far, you've played for &f&l" + PUUIDS.getFormatedPlayTime(p.getUniqueId().toString()));
                    pop(p);
                    Cooldowns.onTime(p.getUniqueId());
                    return true;
                }

                if (sender instanceof Player p) {
                    Cooldowns.onTime(p.getUniqueId());
                }

                String uuid = nametoUUID(args[1]);
                if (Objects.equals(uuid, "0")) {
                    Msgs.sendPrefix(sender, "&c&lHmm. &fThat player has never played before.");
                    bass(sender);
                    return true;
                }

                Msgs.sendPrefix(sender, "&6" + UUIDtoname(uuid) + " has played for &f&l" + PUUIDS.getFormatedPlayTime(uuid));
                pop(sender);
                return true;
            }

            if (args[0].equalsIgnoreCase("info")) {
                Msgs.send(sender, "");
                Msgs.send(sender, "#4ec483&lPUUIDs");
                final int active = plugins.size() - 1;
                if (active > 0) {
                    Msgs.send(sender, "&8&l> &fHooked Plugins: &e&l" + active);
                } else {
                    Msgs.send(sender, "&8&l> &fHooked Plugins: &7&l0");
                }
                if (setTimeMS == 0 && setTimes.get() == 0) {
                    Msgs.send(sender, "&8&l> &fSet Information: &7&l--ms");
                } else {
                    Msgs.send(sender, "&8&l> &fSet Information: &e&l" + setTimeMS + "ms");
                    if (setTimeMS > 10) {
                        statusreason = "A single update is taking over " + setTimeMS + "ms to save.";
                    }
                }

                if (qTimesMS != 0) {
                    Msgs.send(sender, "&8&l> &fQ Process: &e&l" + qTimesMS + "ms");
                    if (qTimesMS > 650) {
                        statusreason = "Q's are taking over " + qTimesMS + "ms to process.";
                    }
                }

                Msgs.send(sender, "&8&l> &fSet Requests: &e&l" + setTimes.get());
                Msgs.send(sender, "&8&l> &fGet Requests: &e&l" + getTimes.get());

                if (status) {
                    if (setTimeMS <= 10 && qTimesMS <= 650) {
                        Msgs.send(sender, "&8&l> &fDatabase Health: &a&lGREAT");
                        statusreason = "0";
                    } else {
                        Msgs.send(sender, "&8&l> &fDatabase Health: &e&lFAIR");
                    }
                } else {
                    Msgs.send(sender, "&8&l> &fDatabase Health: &6&lPOOR");
                }

                if (Objects.equals(statusreason, "0")) {
                    if (!status) {
                        Msgs.send(sender, "   &8&l> &7Couldn't identify a cause for poor health.");
                    }
                } else {
                    // Status Reason is a string.
                    Msgs.send(sender, "   &8&l> &6&lREASON: &f" + statusreason);
                }
                if (Timer.getQSize() != 0) {
                    Msgs.send(sender, "&8&l> &fQueued Data: &e&l" + Timer.getQSize());
                }
                Msgs.send(sender, "&8&l> &fRequests Per Q: &e&l" + setQRequests);
                Msgs.send(sender, "&8&l> &fDebug Mode: " + (debug ? "&e&lON" : "&7&lOFF"));
                Msgs.send(sender, "");
                pop(sender);
                return true;
            }

            bass(sender);
            Msgs.send(sender, "");
            Msgs.send(sender, "#4ec483&lPUUIDs");
            Msgs.send(sender, "&8&l> &c&lCommand Not Found. &fWe couldn't find that command.");
            Msgs.send(sender, "");
            return true;
        }

        return true;
    }

    /*
      Every connected plugin's name, comma separated. The old version appended the last plugin
      twice and always left a trailing separator dangling on the end of the list.
     */
    private StringBuilder getStringBuilder() {
        StringBuilder sb = new StringBuilder();
        for(HashMap.Entry<Plugin, APIVersion> entry : plugins.entrySet()) {
            final String plname = entry.getKey().getDescription().getName();
            if (!plname.equalsIgnoreCase("puuids")) {
                if (!sb.isEmpty()) {
                    sb.append("&f, &e");
                }
                sb.append(plname);
            }
        }
        return sb;
    }

    /**
     * {@code /puuids mysql [export|import confirm|reconnect]}.
     * <p>
     * With no argument it reports on the mirror. The rest are the two directions of a manual
     * re-sync plus a way to retry a connection that was down when the server started.
     */
    private boolean mysqlCommand(CommandSender sender, String[] args) {
        final MySQLStorage db = storage;

        if (args.length == 1) {
            Msgs.send(sender, "");
            Msgs.send(sender, "#4ec483&lPUUIDs &8&l| &fMySQL");
            if (db == null) {
                if (getConfig().getBoolean("MySQL.Enabled", false)) {
                    Msgs.send(sender, "&8&l> &c&lNot Connected. &fCheck the console, then &7/puuids mysql reconnect");
                } else {
                    Msgs.send(sender, "&8&l> &7&lDisabled. &fSet &fMySQL.Enabled &fto true in your config.yml.");
                }
            } else {
                for (String line : db.status()) {
                    Msgs.send(sender, line);
                }
                Msgs.send(sender, "&8&l> &7&o/puuids mysql export &8- &7push the Data folder to MySQL");
                Msgs.send(sender, "&8&l> &7&o/puuids mysql import confirm &8- &7overwrite the Data folder from MySQL");
            }
            Msgs.send(sender, "");
            pop(sender);
            return true;
        }

        if (args[1].equalsIgnoreCase("reconnect")) {
            Msgs.sendPrefix(sender, "&7&oReconnecting to MySQL...");
            thinking(sender);
            // Off the main thread: this waits on the database for up to Connection-Timeout.
            mpl.scheduling().asyncScheduler().run(() -> {
                reconnectStorage();
                if (storage == null) {
                    bass(sender);
                    Msgs.sendPrefix(sender, "&c&lStill Down. &fSee the console for what MySQL said.");
                } else {
                    pop(sender);
                    Msgs.sendPrefix(sender, "&a&lConnected. &fPlayer data is being mirrored again.");
                }
            });
            return true;
        }

        if (db == null || !db.isConnected()) {
            bass(sender);
            Msgs.sendPrefix(sender, "&c&lNot Connected. &fMySQL has to be on and reachable for that.");
            return true;
        }

        if (args[1].equalsIgnoreCase("export")) {
            Msgs.sendPrefix(sender, "&7&oExporting to MySQL, this may take a while...");
            thinking(sender);
            db.exportAll(line -> Msgs.sendPrefix(sender, line), null);
            return true;
        }

        if (args[1].equalsIgnoreCase("import")) {
            // This overwrites files with whatever the database holds, so it is never one word.
            if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                bass(sender);
                Msgs.sendPrefix(sender, "&c&lARE YOU SURE? &fEvery value in MySQL will overwrite the one in your Data folder.");
                Msgs.sendPrefix(sender, "&fRun &7&l/puuids mysql import confirm&f if that is what you want.");
                return true;
            }

            Msgs.sendPrefix(sender, "&7&oImporting from MySQL, saving is paused until it finishes...");
            thinking(sender);
            db.importAll(line -> Msgs.sendPrefix(sender, line), null);
            return true;
        }

        bass(sender);
        Msgs.sendPrefix(sender, "&c&lOops. &fTry &7/puuids mysql &f(export/import/reconnect)");
        return true;
    }
}
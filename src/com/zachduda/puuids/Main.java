package com.zachduda.puuids;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.google.common.io.Files;
import com.zachduda.puuids.api.*;
import com.zachduda.puuids.api.PUUIDS.APIVersion;
import com.zachduda.puuids.api.VersionManager.VersionTest;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.*;

public class Main extends JavaPlugin implements Listener {

    static boolean hasess = false;
    private static HashMap<Plugin, APIVersion> plugins = new HashMap<>();

    private static boolean allowconnections = false;
    public int getTimes = 0;
    public PUUIDS api;
    boolean debug = false;
    boolean asyncrunning = false;
    long setTimeMS = 0; // how long in ms for file saving
    int setTimes = 0;
    long qTimesMS = 0;
    int setQRequests = 0;
    private final String version = Bukkit.getBukkitVersion().replace("-SNAPSHOT", "");
    private boolean sounds;
    private boolean status;
    private String statusreason = "0";
    private boolean updatecheck = true;
    private final boolean isFullySupported = version.contains("1.19") || version.contains("1.18") || version.contains("1.17") || version.contains("1.16") || version.contains("1.15") || version.contains("1.14") || version.contains("1.13");
    private int taskresetid = 0;
    private int playerupdateid = 0;

    private Metrics metrics;

    public void onEnable() {
        double jversion = Double.parseDouble(System.getProperty("java.specification.version"));
        if (jversion < 1.8) {
            getLogger().severe("Unsupported Java Version: " + jversion);
            getLogger().warning("PUUIDs works best in Java 8 (or higher). JDK releases are NOT supported.");
        }

        try {
            Class.forName("com.google.common.collect.Multimap");
            Class.forName("com.google.common.collect.ArrayListMultimap");
        } catch (ClassNotFoundException e) {
            getLogger().severe("Missing Google's Util Common Multimap. This is normally found in Java 8, but is missing with this version of Java. PUUIDs will now disable...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (this.getServer().getPluginManager().isPluginEnabled("Essentials")
                && this.getServer().getPluginManager().getPlugin("Essentials") != null) {
            hasess = true;
            getLogger().info("Hooking into Essentials...");
        }

        status = true;
        statusreason = "0";
        asyncrunning = true;

        final boolean useclean = getConfig().getBoolean("Settings.File-Cleanup.Enabled");
        final boolean cleaness = getConfig().getBoolean("Settings.File-Cleanup.Clean-Essentials");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            final Essentials ess = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
            final File folder = new File(this.getDataFolder(), File.separator + "Data");
            if (!folder.exists()) {
                return;
            }

            ArrayList<String> unknownfiles = new ArrayList<>();

            int maxDays = getConfig().getInt("Settings.File-Cleanup.Max-Days");

            for (File cachefile : folder.listFiles()) {
                String path = cachefile.getPath();

                final File f = new File(path);
                if (!Files.getFileExtension(path).equalsIgnoreCase("yml")) {
                    if(f.getName().toLowerCase().contains("ds_store")) {
                        f.delete();
                        debug("Found macOS .ds_store file in folder. Deleting!");
                    } else {
                        unknownfiles.add(f.getName());
                    }
                } else {

                    try {
                        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

                        if (!setcache.contains("Last-On") || (!setcache.contains("Username")) || (!setcache.contains("UUID"))) {
                            f.delete();
                            debug("Deleted file: " + f.getName() + "... It was invalid!");
                        } else {

                            long daysAgo = Math
                                    .abs(((setcache.getLong("Last-On")) / 86400000) - (System.currentTimeMillis() / 86400000));

                            String playername = setcache.getString("Username");

                            if (daysAgo >= maxDays && useclean) {
                                f.delete();
                                if (hasess && cleaness) {
                                    User user = ess.getUser(playername);
                                    if (!user.getBase().isBanned()) {
                                        // Cleanup EssentialsX data too unless they are banned, in which case their data file should be left alone as to not unban them.
                                        user.reset();
                                    }
                                }
                                if (debug) {
                                    getLogger().info("[Debug] Deleted " + playername + "'s data file because it's " + daysAgo +
                                            "s old. (Max: " + maxDays + " Days)");
                                }
                            } else {
                                /**
                                 * The following EnumUtil code call for native Spigot player's ontime contains code from EssentialsX.
                                 * https://github.com/EssentialsX/Essentials/blob/3af931740b20507837276f87f9456221653ac43d/Essentials/src/main/java/com/earth2me/essentials/commands/Commandplaytime.java
                                 */
                                final String uuid = setcache.getString("UUID");
                                final long playtime = ((getServer().getOfflinePlayer(UUID.fromString(uuid)).getStatistic(EnumUtil.getStatistic("PLAY_ONE_MINUTE", "PLAY_ONE_TICK"))) * 50L);
                                debug("Spigot playtime for " + playername + " is " + playtime/1000 + " seconds");
                                final long puuids_playtime = getPlayTime(uuid);
                                debug("PUUIDS playtime for " + playername + " is " + puuids_playtime/1000 + " seconds");
                                if(playtime > puuids_playtime) {
                                    debug("Using native MC playtime for PUUIDs data file for " + playername);
                                    setcache.set("Time-Played", playtime);
                                    setcache.save(f);
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

            if (unknownfiles.size() >= 1) {
                getLogger().warning("Found " + unknownfiles.size() + " unknown files in your Data folder:");
                for (String file : unknownfiles) {
                    debug("   - " + file);
                }
                getLogger().warning("Make sure that the files above weren't missplaced or corrupted.");
                status = false;
                statusreason = "Unknown file was found in your PUUIDs Data folder, please remove the following files: " + unknownfiles.toString();
            }

            asyncrunning = false;
            unknownfiles.clear();
        }); // End of Async;

        plugins.put(this, APIVersion.V4);
        allowconnections = true;

        Bukkit.getServer().getPluginManager().registerEvents(this, this);

        getConfig().options().copyDefaults(true);
        saveConfig();

        updateConfig();

        api = new PUUIDS();

        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            public void run() {

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
                }
            }
        });

        if (updatecheck) {
            new Updater(this).checkForUpdate();
        }

        // Players shouldn't EVER be online when we are starting....
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (players.size() > 0) {
            status = false;
            statusreason = "PUUIDs was improperly reloaded. This may damage your player's data files! Please restart your server.";
            Msgs.sendPrefix(Bukkit.getConsoleSender(), "&4&l<!> &c&l&nReloading PUUIDs without a proper restart can severely damage PUUID's player data. PLEASE RESTART YOUR SERVER!");
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
            metrics = new Metrics(this, 5740);
        } else {
            debug("Metrics have been disabled in the config.yml. Guess we won't support all this hard work today!");
        }

        taskresetid = startStatResetTimer();
        playerupdateid = startPlayerUpdateTimer();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            ConnectionOpen coe = new ConnectionOpen();
            Bukkit.getPluginManager().callEvent(coe);
        });
    }


    // Update player file, mostly for accurate playtime's every 10 minutes!
    private int startPlayerUpdateTimer() {
        final BukkitTask playerupdatetime = Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            public void run() {
                asyncrunning = true;
                debug("Updating any online players data files...");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateFile(p, false);
                }
                asyncrunning = false;
                UpdatedPlayerStats ups = new UpdatedPlayerStats();
                Bukkit.getPluginManager().callEvent(ups);
            }
        }, 12000L, 12000L);
        return playerupdatetime.getTaskId();
    }

    // Reset Stats every 12 hours
    private int startStatResetTimer() {
        final BukkitTask resetstatstimer = Bukkit.getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            public void run() {
                debug("Resetting the PUUIDs debug statistics, it's been over 12 hours...");
                setTimeMS = 0;
                setTimes = 0;
                getTimes = 0;
                qTimesMS = 0;
                setQRequests = 0;
            }
        }, 864000L, 864000L);
        return resetstatstimer.getTaskId();
    }
    // End of 12 hour Stat Reset timer

    public void debug(String input) {
        if (debug) {
            getLogger().info("[Debug] " + input);
        }
    }

    public void onDisable() {
        final long start = System.currentTimeMillis();

        ConnectionClose cce = new ConnectionClose();
        Bukkit.getPluginManager().callEvent(cce);

        allowconnections = false;

        for (Player p : Bukkit.getOnlinePlayers()) {
            updateFile(p, true);
        }

        Timer.stopTimer();
        getServer().getScheduler().cancelTask(taskresetid);
        getServer().getScheduler().cancelTask(playerupdateid);

        plugins.clear();

        setTimeMS = 0;
        setTimes = 0;
        getTimes = 0;
        qTimesMS = 0;
        setQRequests = 0;

        if(metrics != null) {
            metrics.shutdown();
        }

        getLogger().info("Successfully disabled in " + Long.toString(System.currentTimeMillis() - start) + "ms");
    }

    private void updateConfig() {
        debug = getConfig().getBoolean("Settings.Debug", false);
        updatecheck = getConfig().getBoolean("Settings.Update-Checking", true);
        Msgs.prefix = getConfig().getString("Settings.Prefix", "&8[&e&lPUUIDs&8]");

        if (getConfig().getLong("Advanced.Save-Rate-Ticks", 10) != 0) {
            Timer.processrate = getConfig().getLong("Advanced.Save-Rate-Ticks", 10);
        } else {
            Timer.processrate = 10;
            getLogger().warning("Save Rate was set to 0 ticks in the config, this will cause damage. Defaulting to 10 ticks.");
        }

        if (getConfig().getLong("Advanced.Max-Processes-Per-Queue", 25) != 0) {
            Timer.sizelimit = getConfig().getInt("Advanced.Max-Processes-Per-Queue", 25);
        } else {
            Timer.processrate = 25;
            getLogger().warning("Max-Processes-Per-Queue was set to 0 in the config, this will prevent data from being set. Defaulting to 25.");
        }

        if (isFullySupported) {
            sounds = true;
        } else {
            sounds = false;
            debug("Sounds have been disabled, this is an older version of Minecraft.");
        }
    }


    public HashMap<Plugin, APIVersion> getPlugins() {
        return plugins;
    }

    public boolean connect(Plugin pl, APIVersion vers) {
        if (pl == null) {
            debug("A plugin tried to register with PUUIDs as 'null', this is not allowed.");
            return false;
        }

        String plname = pl.getName();

        VersionTest vt = VersionManager.checks(plname, vers);
        if (vt == VersionTest.FAIL || vers == null) {
            // Plugin will NOT work with this version.
            getLogger().severe("Plugin " + plname + " is unable to use PUUIDs, for they are using an outdated version.");
            return false;
        }

        if (vt == VersionTest.LEGACY) {
            // Plugin may not be 100% compatiable.
            getLogger().warning(plname + " needs to update their plugin to work better with PUUIDs");
        } else {
            debug(plname + " has been compiled with the latest PUUIDs version.");
            // Passed Version test for FULL support.
        }


        if (!allowconnections) {
            if (!plugins.containsKey(pl)) {
                getLogger().warning("Plugin '" + plname + "' tried to register with PUUIDs after the connection window.");
            } else {
                debug(plname + " was reloaded improperly and send another hook request. Ignoring.");
            }
            return false;
        }

        if (!plugins.containsKey(pl)) {
            plugins.put(pl, vers);
            debug("Plugin " + plname + " has been registered.");

            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                PluginRegistered plr = new PluginRegistered(plname);
                Bukkit.getPluginManager().callEvent(plr);
            });
            return true;
        }

        status = false;
        statusreason = "Plugin " + plname + " tried to overwrite another plugin with the exact same name. Please contact " + pl.getDescription().getAuthors().toString() + ". This is not PUUIDs fault.";
        getLogger().warning(statusreason);
        return false;
    }

    public boolean isConnected(Plugin pl) {
        if (plugins.containsKey(pl.getDescription().getName())) {
            return true;
        } else {
            return false;
        }
    }

    public int set(Plugin pl, String uuid, String loc, Object obj) {
        final String plname = pl.getDescription().getName().toUpperCase();
        if (!getPlugins().containsKey(pl)) {
            debug("Not allowing " + plname + " to access data. They didn't connect properly.");
            return 0;
        }

        return Timer.queueSet(plname, uuid, loc, obj);
    }

    public int set(Plugin pl, String uuid, String loc, List<?> obj) {
        final String plname = pl.getDescription().getName().toUpperCase();
        if (!getPlugins().containsKey(pl)) {
            debug("Not allowing " + plname + " to access data. They didn't connect properly.");
            return 0;
        }

        return Timer.queueSet(plname, uuid, loc, obj);
    }


    // ONLY for setting Null info
    public int set(Plugin pl, String uuid, Object should_be_null) {
        final String plname = pl.getDescription().getName().toUpperCase();
        if (!getPlugins().containsKey(pl)) {
            debug("Not allowing " + plname + " to access data. They didn't connect properly.");
            return 0;
        }

        if (should_be_null != null) {
            return 0;
        }

        return Timer.queueSet(plname, uuid, "PUUIDS_SET_AS_ALL_NULL", null);
    }

    public String nametoUUID(String inputsearch) {
        File folder = new File(this.getDataFolder(), File.separator + "Data");

        for (File AllData : folder.listFiles()) {
            File f = new File(AllData.getPath());

            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

            String playername = setcache.getString("Username");
            String UUID = setcache.getString("UUID");

            if (inputsearch.equalsIgnoreCase(playername)) {
                return UUID;
            }
        }

        return "0";
    }

    public boolean hasPlayedUUID(String uuid) {
        File cache = new File(this.getDataFolder(), File.separator + "Data");

        if (!cache.exists()) {
            return false;
        }

        File f = new File(cache, File.separator + "" + uuid + ".yml");

        if (f.exists()) {
            return true;
        }

        return false;
    }

    public boolean hasPlayedName(String name) {
        if (nametoUUID(name) == "0") {
            return false;
        } else {
            return true;
        }
    }

    public long getLastOn(String uuid) {
        File cache = new File(this.getDataFolder(), File.separator + "Data");

        if (!cache.exists()) {
            return 0;
        }

        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        if (!f.exists()) {
            return 0;
        }

        if (!setcache.contains("Last-On")) {
            return 0;
        }

        return setcache.getLong("Last-On");
    }

    public long getPlayTime(String uuid) {
        File cache = new File(this.getDataFolder(), File.separator + "Data");

        if (!cache.exists()) {
            return 0;
        }

        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        if (!f.exists()) {
            return 0;
        }

        if (!setcache.contains("Time-Played")) {
            return 0;
        }

        return setcache.getLong("Time-Played");
    }

    public String getPlayerIP(String uuid) {
        File cache = new File(this.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        if (!f.exists()) {
            return "0";
        }

        if (!setcache.contains("IP") || setcache.getString("IP") == null) {
            return "0";
        }

        return setcache.getString("IP");
    }

    public boolean allowConnections() {
        return allowconnections;
    }

    public String UUIDtoname(String inputsearch) {
        File folder = new File(this.getDataFolder(), File.separator + "Data");
        File f = new File(folder, File.separator + "" + inputsearch + ".yml");

        if (!f.exists()) {
            return "0";
        }

        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
        return setcache.getString("Username");
    }


    private void updateFile(Player p, boolean quit) {
        Timer.updateSystem.put(p, quit);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            Player p = e.getPlayer();
            UUID uuid = p.getUniqueId();
            if (Cooldowns.joined.contains(uuid)) {
                debug(p.getName() + "'s file won't be refreshed, it was updated less than 60s ago. [Join]");
                return;
            }

            Cooldowns.justJoined(uuid);
            updateFile(p, false);

            if (updatecheck) {
                if (p.hasPermission("puuids.admin") || p.isOp()) {
                    if (Updater.outdated) {
                        try {
                            Msgs.sendPrefix(p, "&c&lOutdated Plugin! &7Running v" + getDescription().getVersion() +
                                    " while the latest is &f&l" + Updater.postedver);
                            pop(p);
                        } catch (Exception err) {
                            sounds = false;
                            debug("Error on update notif. on join: ");
                            err.printStackTrace();
                        }
                    }
                }
            }

            if (p.getUniqueId().toString().equals("6191ff85-e092-4e9a-94bd-63df409c2079")) {
                Msgs.send(p, "&7This server is running &fPUUIDs &6v" + getDescription().getVersion() +
                        " &7for " + Bukkit.getBukkitVersion().replace("-SNAPSHOT", ""));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (!Cooldowns.joined.contains(uuid)) {
            updateFile(p, true);
            Cooldowns.justJoined(uuid);
        } else {
            debug(p.getName() + "'s file won't be refreshed, it was updated less than 60s ago. [Quit]");
        }

        Cooldowns.confirmall.remove(p);
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

            if (!(sender instanceof Player)) {
                return;
            }

            Player p = (Player) sender;
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

            if (!(sender instanceof Player)) {
                return;
            }

            Player p = (Player) sender;
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

            if (!(sender instanceof Player)) {
                return;
            }

            Player p = (Player) sender;
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_FRAME_PLACE, 2.0F, 2.0F);
        } catch (Exception err) {
            sounds = false;
        }
    }

    private void noPermission(CommandSender sender) {
        Msgs.sendPrefix(sender, getConfig().getString("Messages.No-Permission"));
        bass(sender);
    }

    public boolean onCommand(CommandSender sender, Command cmd, String cmdLabel, String[] args) {
        if (cmd.getName().equalsIgnoreCase("puuids")) {
            if (!sender.hasPermission("puuids.admin") && !sender.isOp()) {
                noPermission(sender);
                return true;
            }

            if (args.length == 0) {
                Msgs.send(sender, "");
                Msgs.send(sender, "&e&lPUUIDs");
                Msgs.send(sender, "&8&l> &f&o/puuids help &7&ofor commands & help.");
                Msgs.send(sender, "");
                pop(sender);
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("help")) {
                Msgs.send(sender, "");
                Msgs.send(sender, "&e&lPUUIDs");
                Msgs.send(sender, "&8&l> &f&l/puuids version &7Get the current version of your PUUIDs system.");
                Msgs.send(sender, "&8&l> &f&l/puuids ontime (player) &7See how long you or someone else been playing.");
                Msgs.send(sender, "&8&l> &f&l/puuids reload &7Reload your config.yml.");
                Msgs.send(sender, "&8&l> &f&l/puuids info &7Shows you how fast/slow your system is running.");
                if (debug) {
                    Msgs.send(sender, "&8&l> &f&l/puuids debug &7Shows detailed system information.");
                    Msgs.send(sender, "&8&l> &f&l/puuids reset all &7Resets everything except UUIDs/IPs/Names");
                    Msgs.send(sender, "&8&l> &f&l/puuids reset ontime &7Set everyone's total play-time back to 0.");
                }
                Msgs.send(sender, "&8&l> &f&l/puuids plugins &7Shows connected plugins.");
                Msgs.send(sender, "");
                pop(sender);
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("plugins")) {
                if (plugins.size() == 0) {
                    pop(sender);
                    Msgs.sendPrefix(sender, "&fThere are &7&lNo Connected Plugins&f currently.");
                    return true;
                }
                final int size = plugins.size() - 1;
                Msgs.sendPrefix(sender, "&fThere are &6&l" + size + " &fplugins connected:");
                for(HashMap.Entry<Plugin, APIVersion> entry : plugins.entrySet()) {
                    final String plname = entry.getKey().getDescription().getName();
                    if (!plname.equalsIgnoreCase("puuids")) {
                        Msgs.send(sender, "&r     &8&l> &e&l" + plname);
                    }
                }
                pop(sender);
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("debug")) {
                if (!debug) {
                    bass(sender);
                    Msgs.send(sender, "&7");
                    Msgs.send(sender, "&e&lPUUIDs");
                    Msgs.send(sender, "&8&l> &c&lCommand Disabled. &fTo enable, please turn on debug mode.");
                    Msgs.send(sender, "&7");
                    return true;
                }
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    double jversion = Double.parseDouble(System.getProperty("java.specification.version"));
                    StringBuilder sb = new StringBuilder();
                    int plsb = 0;
                    for(HashMap.Entry<Plugin, APIVersion> entry : plugins.entrySet()) {
                        final String plname = entry.getKey().getDescription().getName();
                        if (!plname.equalsIgnoreCase("puuids")) {
                            if (plsb == getPlugins().size() - 1) {
                                sb.append(plname);
                            }

                            sb.append(plname + "&f, &e");
                            plsb++;
                        }
                    }

                    String size = Integer.toString((getPlugins().size() - 1));

                    Msgs.send(sender, "&f");
                    if (size.equals("0")) {
                        Msgs.send(sender, "&7There are no plugins connected.");
                    } else {
                        Msgs.send(sender, "&6" + size + " &fConnected Plugins: &e" + sb.toString());
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
                    Msgs.send(sender, "&fTotal Sets: &e" + setTimes);
                    Msgs.send(sender, "&fTotal Gets: &e" + getTimes);

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
                        Attribute att = (Attribute) list.get(0);
                        Double value = (Double) att.getValue();
                        long cpu = Math.round(((int) (value * 1000) / 10.0) * 39);
                        Msgs.send(sender, "&fCPU: &e" + cpu + "%");
                    } catch (Exception err) {
                        Msgs.send(sender, "&fCPU: &7Readings Not Available");
                    }
                    if (statusreason != "0") {
                        Msgs.send(sender, "&fLatest Issue: &e" + statusreason);
                    }
                    pop(sender);
                });
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("togglesave")) {
                if (!debug) {
                    bass(sender);
                    Msgs.send(sender, "&7");
                    Msgs.send(sender, "&e&lPUUIDs");
                    Msgs.send(sender, "&8&l> &c&lCommand Disabled. &fTo enable, please turn on debug mode.");
                    Msgs.send(sender, "&7");
                    return true;
                }

                if (!(sender instanceof Player)) {
                    Msgs.sendPrefix(sender, "&6&lFOR SECURITY REASONS: &fOnly a player with permission & op may run this command.");
                    return true;
                }

                Player p = (Player) sender;

                if (!p.hasPermission("puuids.admin") || !p.isOp()) {
                    bass(p);
                    Msgs.sendPrefix(p, "&6&lFor Saftey: &fYou must have the &7puuids.admin&f permission & be OP to do this.");
                    return true;
                }

                if (!Cooldowns.confirmall.containsKey(p)) {
                    String key = randomString();
                    thinking(p);
                    Msgs.sendPrefix(p, "&c&lARE YOU SURE? &fThis may corrupt data. Do &7&l/puuids togglesave " + key + "&f in 10s to confirm.");
                    Cooldowns.confirm(p, key);
                    return true;
                } else {
                    // Has reset all confirmation key active v v v
                    if (args.length == 1) {
                        Msgs.sendPrefix(p, "&c&lARE YOU SURE? &fType &7&l/puuids togglesave " + Cooldowns.confirmall.get(p) + "&f to confirm.");
                        thinking(p);
                        return true;
                    } else if (args.length >= 2) {
                        if (!args[1].equalsIgnoreCase(Cooldowns.confirmall.get(p))) {
                            bass(p);
                            Msgs.sendPrefix(p, "&6&lToggle Saving Canceled. &fThat was an invalid confirmation key.");
                            Cooldowns.confirmall.remove(p);
                            return true;
                        }
                    }
                }

                Msgs.send(p, "&7");
                Msgs.send(p, "&e&lPUUIDs");
                if (asyncrunning) {
                    Msgs.send(p, "&8&l> &a&lUnfrozen. &fNow processing saving requests...");
                    asyncrunning = false;
                } else {
                    asyncrunning = true;
                    Msgs.send(p, "&8&l> &c&lFrozen. &fRequests will be Q'ed but not saved.");
                }
                Msgs.send(p, "&7");
                pop(p);
                Cooldowns.confirmall.remove(p);
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
                if (!debug) {
                    bass(sender);
                    Msgs.send(sender, "&7");
                    Msgs.send(sender, "&e&lPUUIDs");
                    Msgs.send(sender, "&8&l> &c&lCommand Disabled. &fTo enable, please turn on debug mode.");
                    Msgs.send(sender, "&7");
                    return true;
                }

                if (!Cooldowns.canRunLargeTask) {
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
                    Msgs.send(sender, "&e&lPUUIDs");
                    Msgs.send(sender, "&8&l> &7&oPlease wait... this may take a long time.");
                    Msgs.send(sender, "&7");
                    thinking(sender);

                    asyncrunning = true;

                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        int total = 0;
                        final long start = System.currentTimeMillis();
                        for (File AllData : folder.listFiles()) {
                            File f = new File(AllData.getPath());

                            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                            debug("Reset " + setcache.getString("Username") + "'s Time-Played. (" + setcache.getLong("Time-Played") + "secs) " + " (" + f.getName() + ")");
                            setcache.set("Time-Played", 0);

                            try {
                                setcache.save(f);
                            } catch (Exception err) {
                            }
                            total++;
                        }

                        final String finished = Long.toString(System.currentTimeMillis() - start);

                        getLogger().info("Reset " + total + " players Time-Played stats on file. (Done in " + finished + "ms)");
                        asyncrunning = false;

                        Msgs.send(sender, "");
                        Msgs.send(sender, "&e&lPUUIDs");
                        Msgs.send(sender, "&8&l> &a&lDone. &fReset everyone's Time-Played back to zero in &7&l" + finished + "ms");
                        Msgs.send(sender, "");
                        pop(sender);

                        Cooldowns.endLargeTask();
                    });
                    return true;
                }

                if (args[1].equalsIgnoreCase("all")) {
                    if (!(sender instanceof Player)) {
                        Msgs.sendPrefix(sender, "&6&lFOR SECURITY REASONS: &fOnly a player with permission & op may run this command.");
                        return true;
                    }

                    Player p = (Player) sender;

                    if (!p.hasPermission("puuids.admin") || !p.isOp()) {
                        bass(p);
                        Msgs.sendPrefix(p, "&6&lFor Safety: &fYou must have the &7puuids.admin&f permission & be OP to do this.");
                        return true;
                    }

                    if (!Cooldowns.confirmall.containsKey(p)) {
                        String key = randomString();
                        thinking(p);
                        Msgs.sendPrefix(p, "&c&lARE YOU SURE? &fThis will erase ALL player data from your PUUID's data folder. Do &7&l/puuids reset all " + key + "&f in 10s to confirm.");
                        Cooldowns.confirm(p, key);
                        return true;
                    } else {
                        // Has reset all confirmation key active v v v
                        if (args.length == 2) {
                            Msgs.sendPrefix(p, "&c&lARE YOU SURE? &fType &7&l/puuids reset all " + Cooldowns.confirmall.get(p) + "&f to confirm.");
                            thinking(p);
                            return true;
                        } else if (args.length >= 3) {
                            if (!args[2].equalsIgnoreCase(Cooldowns.confirmall.get(p))) {
                                bass(p);
                                Msgs.sendPrefix(p, "&6&lReset Canceled. &fThat was an invalid reset key.");
                                Cooldowns.confirmall.remove(p);
                                return true;
                            }
                        }
                    }

                    asyncrunning = true;

                    Cooldowns.startLargeTask();
                    Msgs.send(sender, "&7");
                    Msgs.send(sender, "&e&lPUUIDs");
                    Msgs.send(sender, "&8&l> &7&oPlease wait... this may take a long time.");
                    Msgs.send(sender, "&7");
                    thinking(sender);

                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        int total = 0;
                        final long start = System.currentTimeMillis();
                        for (File AllData : folder.listFiles()) {
                            File f = new File(AllData.getPath());

                            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                            setcache.set("Plugins", null);

                            debug("Reset" + setcache.getString("Username") + "'s file back to basics. (" + f.getName() + ")");

                            try {
                                setcache.save(f);
                            } catch (Exception err) {
                            }
                            total++;
                        }

                        final String finished = Long.toString(System.currentTimeMillis() - start);

                        getLogger().info("Reset " + total + " players files back to basics. (Done in " + finished + "ms)");
                        asyncrunning = false;

                        Msgs.send(sender, "");
                        Msgs.send(sender, "&e&lPUUIDs");
                        Msgs.send(sender, "&8&l> &a&lDone. &fCleared player files back to basics in &7&l" + finished + "ms");
                        Msgs.send(sender, "");
                        pop(sender);

                        Cooldowns.endLargeTask();
                    });
                    return true;
                }

                Msgs.send(sender, "");
                Msgs.send(sender, "&e&lPUUIDs");
                Msgs.send(sender, "&8&l> &c&lError. &fSomething went wrong here.");
                Msgs.send(sender, "");
                bass(sender);
                status = false;
                statusreason = "Unknown issue when trying to clear player file data via cmd.";
                return true;
            }


            if (args.length >= 1 && args[0].equalsIgnoreCase("version")) {
                Msgs.send(sender, "");
                Msgs.send(sender, "&e&lPUUIDs");
                Msgs.send(sender, "&8&l> &7You are currently running &f&lv" + getDescription().getVersion());
                Msgs.send(sender, "");
                pop(sender);
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                final long start = System.currentTimeMillis();
                reloadConfig();
                updateConfig();
                Msgs.send(sender, "");
                Msgs.send(sender, "&e&lPUUIDs");
                Msgs.send(sender, "&8&l> &fConfiguration has been reloaded in &6" + Long.toString(System.currentTimeMillis() - start) + "ms");
                Msgs.send(sender, "");
                pop(sender);
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("ontime")) {
                // Need to optimize, TOO many sender instanceof Player stuffs.

                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    if (Cooldowns.ontime.contains(p.getUniqueId())) {
                        Msgs.sendPrefix(sender, "&c&lSlow Down. &fPlease wait before checking that again.");
                        bass(p);
                        return true;
                    }
                }

                if (args.length == 1) {
                    if (!(sender instanceof Player)) {
                        Msgs.sendPrefix(sender, "&c&lOops. &fYou must specify a player: &7&l/puuids ontime (player)");
                        return true;
                    }
                    Player p = (Player) sender;
                    Msgs.sendPrefix(sender, "&6So far, you've played for &f&l" + PUUIDS.getFormatedPlayTime(p.getUniqueId().toString()));
                    pop(p);
                    Cooldowns.onTime(p.getUniqueId());
                    return true;
                }

                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    Cooldowns.onTime(p.getUniqueId());
                }

                String uuid = nametoUUID(args[1]);
                if (uuid == "0") {
                    Msgs.sendPrefix(sender, "&c&lHmm. &fThat player has never played before.");
                    bass(sender);
                    return true;
                }

                Msgs.sendPrefix(sender, "&6" + UUIDtoname(uuid) + " has played for &f&l" + PUUIDS.getFormatedPlayTime(uuid));
                pop(sender);
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
                Msgs.send(sender, "");
                Msgs.send(sender, "&e&lPUUIDs");
                final int active = getPlugins().size() - 1;
                if (active > 0) {
                    Msgs.send(sender, "&8&l> &fHooked Plugins: &e&l" + active);
                } else {
                    Msgs.send(sender, "&8&l> &fHooked Plugins: &7&l0");
                }
                if (setTimeMS == 0 && setTimes == 0) {
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

                Msgs.send(sender, "&8&l> &fSet Requests: &e&l" + setTimes);
                Msgs.send(sender, "&8&l> &fGet Requests: &e&l" + getTimes);

                if (status) {
                    if (setTimeMS < 10 || qTimesMS < 650) {
                        Msgs.send(sender, "&8&l> &fDatabase Health: &a&lGREAT");
                        statusreason = "0";
                    } else {
                        Msgs.send(sender, "&8&l> &fDatabase Health: &e&lFAIR");
                    }
                } else {
                    Msgs.send(sender, "&8&l> &fDatabase Health: &6&lPOOR");
                }

                if (statusreason == "0") {
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

            if (args.length >= 1) {
                bass(sender);
                Msgs.send(sender, "");
                Msgs.send(sender, "&e&lPUUIDs");
                Msgs.send(sender, "&8&l> &c&lCommand Not Found. &fWe couldn't find that command.");
                Msgs.send(sender, "");
                return true;
            }
        }

        return true;
    }
}
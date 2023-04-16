package com.zachduda.puuids.api;

import com.google.common.io.Files;
import com.zachduda.puuids.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PUUIDS {
    private static final Main plugin = Main.getPlugin(Main.class);

    /**
     * Authorizes with puuids. While not required for some functions, it is required for getting / setting plugin data.
     *
     * @param pl Your plugin, most of the time, you can just put "this"
     * @return Returns true or false if the connection was successful.
     */
    public static boolean connect(Plugin pl, APIVersion vers) {
        if (plugin.connect(pl, vers)) {
            return true;
        }

        return false;
    }

    /**
     * Returns a players UUID from their Name as a String.
     *
     * @param name      The player's username you want the UUID of.
     * @param usemojang (Deprecated will be removed in future update!)
     * @return The UUID of a player as a String
     */
    @Deprecated
    public static String getUUID(String name, boolean usemojang) {
        wasGet();
        return plugin.nametoUUID(name);
    }

    public static String getUUID(String name) {
        wasGet();
        return plugin.nametoUUID(name);
    }

    /**
     * Returns a players Name from their UUID as a String.
     *
     * @param uuid The UUID of the player you want the name of.
     * @return The username of a player as a string.
     */
    public static String getName(String uuid) {
        wasGet();
        return plugin.UUIDtoname(uuid);
    }

    /**
     * Checks the player UUID from cache and if it returns "0", we return false.
     *
     * @param uuid The UUID of the player.
     * @return If a player has a data file. (Usually it's yes)
     */
    public static boolean hasFile(String uuid) {
        wasGet();
        return plugin.hasPlayedUUID(uuid);
    }

    public static long getLastOn(String uuid) {
        wasGet();
        return plugin.getLastOn(uuid);
    }

    public static String getIP(String uuid) {
        wasGet();
        return plugin.getPlayerIP(uuid);
    }

    public static long getPlayTime(String uuid) {
        wasGet();
        return plugin.getPlayTime(uuid);
    }

    public static String getFormatedPlayTime(String uuid) {
        long time = getPlayTime(uuid);
        // v3 now saves in miliseconds, convert to sec for now
        String ans = secsToFormatTime(time/1000);
        wasGet();
        return ans;
    }

    public static String secsToFormatTime(long secs) {
        if (secs == 0) {
            return "Nothing Yet!";
        } else if (secs < 60) {
            if (secs == 1) {
                return secs + " second";
            } else {
                return secs + " seconds";
            }
        } else if (secs < 3600) {
            final long min = secs / 60;
            if (min == 1) {
                return min + " minute";
            } else {
                return min + " minutes";
            }
        } else if (secs < 86400) {
            final long hours = secs / 3600;
            if (hours == 1) {
                return hours + " hour";
            } else {
                return hours + " hours";
            }
        } else {
            final long days = secs / 86400;
            if (days == 1) {
                return days + " day";
            } else {
                return days + " days";
            }
        }
    }

    public static String getString(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getString("Plugins." + plname.toUpperCase() + "." + location);
    }

    // GETTING & SETTING PLUGIN DATA ------------------------------------------

    // Strings -------------

    // Booleans --------------
    public static boolean getBoolean(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location);
    }

    // End of Strings ------------

    // Config Keys


    // End of Config Keys

    public static ArrayList<String> getAllWithBoolean(Plugin pl, String location, boolean quickmode) {
        String plname = pl.getName();
        File cache = new File(plugin.getDataFolder(), File.separator + "Data");

        ArrayList<String> allplayers = new ArrayList<String>();

        for (String playeruuid : getAllPlayerUUIDs(pl, quickmode)) {
            File f = new File(cache, File.separator + "" + playeruuid + ".yml");
            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

            if (setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location)) {
                allplayers.add(setcache.getString("Username"));
            }
        }

        wasGet();
        return allplayers;
    }

    public static ArrayList<String> getAllWithoutBoolean(Plugin pl, String location, boolean quickmode) {
        String plname = pl.getName();
        File cache = new File(plugin.getDataFolder(), File.separator + "Data");

        ArrayList<String> allplayers = new ArrayList<String>();

        for (String playeruuid : getAllPlayerUUIDs(pl, quickmode)) {
            File f = new File(cache, File.separator + "" + playeruuid + ".yml");
            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

            if (!setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location)) {
                allplayers.add(setcache.getString("Username"));
            }
        }

        wasGet();
        return allplayers;
    }

    public static int getInt(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getInt("Plugins." + plname.toUpperCase() + "." + location);
    }

    // End of Booleans -------

    // Start of Int --------------

    // Start of Double --------------
    public static double getDouble(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location);
    }
    // End of Int -------

    public static long getLong(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getLong("Plugins." + plname.toUpperCase() + "." + location);
    }

    // End of Double -------

    // Start of Long --------------

    public static List<?> getNCList(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getList("Plugins." + plname.toUpperCase() + "." + location);
    }
    // End of Long -------


    // Start of List --------------

    public static List<Integer> getIntList(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getIntegerList("Plugins." + plname.toUpperCase() + "." + location);
    }

    public static ItemStack getItemStack(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getItemStack("Plugins." + plname.toUpperCase() + "." + location);
    }

    public static List<String> getStringList(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getStringList("Plugins." + plname.toUpperCase() + "." + location);
    }

    public static ArrayList<String> getAllPlayerNames(Plugin pl) {
        String plname = pl.getName();
        if (!plugin.getPlugins().containsKey(pl)) {
            return null;
        }

        // Gets the player names of all we have record of.

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");

        ArrayList<String> allplayers = new ArrayList<String>();

        for (File cachefile : cache.listFiles()) {
            String path = cachefile.getPath();

            if (!Files.getFileExtension(path).equalsIgnoreCase("yml")) {
                // NOT A VALID FILE
            } else {

                File f = new File(path);
                FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                allplayers.add(setcache.getString("Username"));
            }
        }
        wasGet();
        return allplayers;
    }

    public static ArrayList<String> getAllPlayerUUIDs(Plugin pl, boolean quickmode) {
        if (!plugin.getPlugins().containsKey(pl)) {
            return null;
        }

        // Gets a list of all the uuids we have a record of.

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");

        ArrayList<String> allplayers = new ArrayList<String>();

        for (File cachefile : cache.listFiles()) {
            String path = cachefile.getPath();

            if (!Files.getFileExtension(path).equalsIgnoreCase("yml")) {
                // NOT A VALID FILE
            } else {

                File f = new File(path);
                if (quickmode) {
                    allplayers.add(f.getName().replace(".yml", ""));
                } else {
                    FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                    allplayers.add(setcache.getString("UUID"));
                }
            }
        }
        wasGet();
        return allplayers;
    }

    public static int setLocation(Plugin pl, String uuid, String location, Location input) {
        if (pl == null || uuid == null || location == null) {
            return 0;
        }

        plugin.set(pl, uuid, location + ".X", input.getX());
        plugin.set(pl, uuid, location + ".Y", input.getY());
        plugin.set(pl, uuid, location + ".Z", input.getZ());
        plugin.set(pl, uuid, location + ".Pitch", input.getPitch());
        if(plugin.getPlugins().get(pl) == APIVersion.V4) {
            plugin.set(pl, uuid, location + ".World", input.getWorld().getName());
        }
        int taskid = plugin.set(pl, uuid, location + ".Yaw", input.getYaw());

        return taskid;
    }
    // End of List -------


    // Start of Location ----

    @Deprecated // in v4, the manual input String for worlds are no longer required.
    public static Location getLocation(Plugin pl, String uuid, String location, String world) {
        if (pl == null || uuid == null || location == null) {
            return null;
        }

        String plname = pl.getName();

        if (!plugin.getPlugins().containsKey(pl)) {
            plugin.debug("Not allowing " + plname + " to access data. They didn't connect properly.");
            return null;
        }

        if(plugin.getPlugins().get(pl) == APIVersion.V4) {
            plugin.getLogger().severe(plname + " should use getLocation() without world string!");
        }

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        double prevx = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".X");
        double prevy = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".Y") + 0.3D;
        double prevz = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".Z");
        float prevpitch = setcache.getInt("Plugins." + plname.toUpperCase() + "." + location + ".Pitch");
        float prevyaw = setcache.getInt("Plugins." + plname.toUpperCase() + "." + location + ".Yaw");
        Location finalloc = new Location(plugin.getServer().getWorld(world), prevx, prevy, prevz, prevyaw, prevpitch);
        wasGet();
        return finalloc;
    }

    public static Location getLocation(Plugin pl, String uuid, String location) {
        if (pl == null || uuid == null || location == null) {
            return null;
        }

        String plname = pl.getName();

        if (!plugin.getPlugins().containsKey(pl)) {
            plugin.debug("Not allowing " + plname + " to access data. They didn't connect properly.");
            return null;
        }

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        double prevx = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".X");
        double prevy = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".Y") + 0.3D;
        double prevz = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".Z");
        float prevpitch = setcache.getInt("Plugins." + plname.toUpperCase() + "." + location + ".Pitch");
        float prevyaw = setcache.getInt("Plugins." + plname.toUpperCase() + "." + location + ".Yaw");
        String world = setcache.getString("Plugins." + plname.toUpperCase() + "." + location + ".World");
        Location finalloc = new Location(plugin.getServer().getWorld(world), prevx, prevy, prevz, prevyaw, prevpitch);
        wasGet();
        return finalloc;
    }

    // Contains
    public static boolean contains(Plugin pl, String uuid, String location) {
        if (pl == null || uuid == null || location == null) {
            return false;
        }

        String plname = pl.getName();

        if (!plugin.getPlugins().containsKey(pl)) {
            plugin.debug("Not allowing " + pl.getName() + " to access data. They didn't connect properly.");
            return false;
        }

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        if (setcache.contains("Plugins." + plname.toUpperCase() + "." + location)) {
            return true;
        }

        wasGet();
        return false;
    }

    // End of Location

    // GET (v1.4 and +)
    public static Object get(Plugin pl, String uuid, String location) {
        if (pl == null || uuid == null || location == null) {
            return false;
        }

        String plname = pl.getName();

        if (!plugin.getPlugins().containsKey(pl)) {
            plugin.debug("Not allowing " + pl.getName() + " to access data. They didn't connect properly.");
            return false;
        }

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + "" + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.get("Plugins." + plname.toUpperCase() + "." + location);
    }
    // End of Contains.

    /**
     * Sets a value as null
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @return The UUID of a player as a String
     */
    public static int setNull(Plugin pl, String uuid, String location) {
        if (pl == null || uuid == null || location == null) {
            return 0;
        }

        return plugin.set(pl, uuid, location, null);
    }
    // END GET

    // NULL SET v1.4.3+

    /**
     * Removes ALL set values for your plugin.
     *
     * @param pl   Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid The UUID of the player as a String.
     * @return The UUID of a player as a String
     */
    public static int setNull(Plugin pl, String uuid) {
        if (pl == null || uuid == null) {
            return 0;
        }

        return plugin.set(pl, uuid, null);
    }

    /**
     * Add a string to an existing list.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @param add      The string you want to add and save to that player file.
     * @return A boolean of weather or not the operation was successful.
     */
    public static int addToStringList(Plugin pl, String uuid, String location, String add) {
        if (pl == null || uuid == null || location == null) {
            return 0;
        }

        List<String> input = getStringList(pl, uuid, location);
        input.add(add);
        return plugin.set(pl, uuid, location, input);
    }
    // End of NULL set

    // v1.4.5 and + add to list

    /**
     * Add a int to an existing int list.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @param add      The int you want to add and save to that player file.
     * @return A boolean of weather or not the operation was successful.
     */
    public static int addToIntList(Plugin pl, String uuid, String location, int add) {
        if (pl == null || uuid == null || location == null) {
            return 0;
        }
        List<Integer> input = getIntList(pl, uuid, location);
        input.add(add);
        return plugin.set(pl, uuid, location, input);
    }
    // End of List Add

    // v1.4.5 and + add to int list

    /**
     * Remove a string to an existing list.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @param add      The string you want to remove and save to that player file.
     * @return A boolean of weather or not the operation was successful.
     */
    public static int removeFromStringList(Plugin pl, String uuid, String location, String add) {
        if (pl == null || uuid == null || location == null) {
            return 0;
        }
        List<String> input = getStringList(pl, uuid, location);
        input.remove(add);
        return plugin.set(pl, uuid, location, input);
    }
    // End of List Add


    // v1.4.5 and + remove to list

    /**
     * Remove an int to an existing int list.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @param add      The int you want to remove and save to that player file.
     * @return A boolean of weather or not the operation was successful.
     */
    public static int removeFromIntList(Plugin pl, String uuid, String location, int add) {
        if (pl == null || uuid == null || location == null) {
            return 0;
        }

        List<Integer> input = getIntList(pl, uuid, location);
        input.add(add);
        return plugin.set(pl, uuid, location, input);
    }
    // End of List Add

    // v1.4.5 and + remove to int list

    /**
     * Set plugin data for your plugin in a player's data file.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @param input    Accepts a boolean/int/long or other value to set.
     * @return The UUID of a player as a String
     */
    public static int set(Plugin pl, String uuid, String location, Object input) {
        if (pl == null || uuid == null || location == null) {
            return 0;
        }

        return plugin.set(pl, uuid, location, input);
    }
    // End of List Remove


    // SET (v1.3 and +)

    /**
     * Sets default values for already existing files without said value. Can only be executed while connections are being accepted.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param location Where do you want the value to be set?
     * @param input    What to set the default value as?
     */
    public static boolean addToAllWithout(Plugin pl, String location, Object input) {
        if (pl == null || location == null || location == null || input == null) {
            return false;
        }

        // DOES NOT USE ASYNC TIMER. RUNS SYNC

        String plname = pl.getName();

        if (!plugin.allowConnections()) {
            plugin.debug(plname + " tried to set addToAllWithout method AFTER startup, this isn't allowed.");
            return false;
        }

        if (!plugin.getPlugins().containsKey(pl)) {
            plugin.debug("Not allowing " + pl.getName() + " to access data. They didn't connect properly.");
            return false;
        }

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");

        int total = 0;

        for (String playeruuid : getAllPlayerUUIDs(pl, false)) {
            try {
                File f = new File(cache, File.separator + "" + playeruuid + ".yml");
                FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!setcache.contains("Plugins." + plname.toUpperCase() + "." + location) || setcache.get("Plugins." + plname.toUpperCase() + "." + location) == null) {
                        setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
                        try {
                            setcache.save(f);
                        } catch (Exception err) {
                        }
                    }
                });
                total++;
            } catch (Exception err) {
                return false;
            }
        }

        if (total != 0) {
            plugin.debug(plname + " updated " + total + " files with their missing values.");
        }
        total = 0;
        return true;
    }
    // End of SET


    // Update Existing

    private static void wasGet() {
        plugin.getTimes++;
    }

    // End of Updates


    // General

    public static enum APIVersion {
        @Deprecated V1,
        @Deprecated V2,
        @Deprecated V3,
        V4
    }
    // End of General
}

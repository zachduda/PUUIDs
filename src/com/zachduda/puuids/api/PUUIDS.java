package com.zachduda.puuids.api;

import com.google.common.io.Files;
import com.zachduda.puuids.Main;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The PUUIDs API. Every method is static.
 * <p>
 * Call {@link #connect(Plugin, APIVersion)} once from your plugin's {@code onEnable()}, then use the
 * {@code set} methods to save values and the {@code get} methods to read them. Players are identified
 * by their UUID as a String ({@code player.getUniqueId().toString()}), and each plugin's values live in
 * their own section of the player's file, under the plugin's name in upper case.
 * <p>
 * Writes are queued and saved off the main thread shortly afterwards, while reads load the player's
 * file from disk on the calling thread. A read straight after a write can therefore still return the
 * old value.
 *
 * @see <a href="https://github.com/zachduda/PUUIDs/tree/master/docs">PUUIDs documentation</a>
 */
@SuppressWarnings("unused")
public class PUUIDS {
    private static final Main plugin = Main.getPlugin(Main.class);

    /**
     * Registers your plugin with puuids. Required before saving any data, and before {@link #get},
     * {@link #contains}, {@link #getLocation(Plugin, String, String)} and the {@code getAll} methods.
     * <p>
     * Call this once, from {@code onEnable()}. Connections are only accepted while the server is starting
     * up, unless the server owner turns on {@code Advanced.Allow-Post-Startup-Connections}.
     *
     * @param pl   Your plugin, most of the time, you can just put "this"
     * @param vers The API version your plugin was written for. Use {@link APIVersion#V4}.
     * @return true if your plugin is now connected; false if startup has finished, the version is
     *         outdated, or this plugin had already connected.
     */
    public static boolean connect(Plugin pl, APIVersion vers) {
        return plugin.connect(pl, vers);
    }

    /**
     * Returns a players UUID from their Name as a String. The name is matched ignoring case, and only
     * players with a puuids file on this server can be found: nothing is looked up from Mojang.
     * <p>
     * Names puuids has already seen are answered from memory. Any other name makes puuids read every
     * player file first, so avoid looking up unknown names on the main thread of a large server.
     *
     * @param name      The player's username you want the UUID of.
     * @return The UUID of a player as a String, or "0" if no player with that name has a file.
     */
    public static String getUUID(String name) {
        wasGet();
        return plugin.nametoUUID(name);
    }

    /**
     * Returns a players Name from their UUID as a String, as it was when they were last seen.
     *
     * @param uuid The UUID of the player you want the name of.
     * @return The username of a player as a string, or "0" if they have no file.
     */
    public static String getName(String uuid) {
        wasGet();
        return plugin.UUIDtoname(uuid);
    }

    /**
     * Checks whether a player has a puuids data file. Anyone who has joined the server has one, unless
     * it has since been removed by the inactivity clean-up.
     *
     * @param uuid The UUID of the player.
     * @return true if the player has a data file.
     */
    public static boolean hasFile(String uuid) {
        wasGet();
        return plugin.hasPlayedUUID(uuid);
    }

    /**
     * When puuids last saved this player's details. That happens as they join, as they quit, and at a
     * regular checkpoint while they are online, so for an offline player it is roughly when they left.
     *
     * @param uuid The UUID of the player.
     * @return A timestamp in milliseconds since the epoch, or 0 if unknown.
     */
    public static long getLastOn(String uuid) {
        wasGet();
        return plugin.getLastOn(uuid);
    }

    /**
     * @param uuid The UUID of the player.
     * @return The IP address the player last joined from, or "0" if unknown.
     */
    public static String getIP(String uuid) {
        wasGet();
        return plugin.getPlayerIP(uuid);
    }

    /**
     * Total time played on this server, including the current session of an online player.
     *
     * @param uuid The UUID of the player.
     * @return Play time in seconds, or 0 if unknown.
     */
    public static long getPlayTime(String uuid) {
        wasGet();
        return plugin.getPlayTime(uuid);
    }

    /**
     * Play time as a readable string, such as "3 hours, 12 minutes".
     * Stored in seconds.
     *
     * @param uuid The UUID of the player.
     * @return The formatted play time. See {@link #secsToFormatTime(long)}.
     */
    public static String getFormatedPlayTime(String uuid) {
        return secsToFormatTime(getPlayTime(uuid));
    }

    /**
     * Formats a number of seconds, keeping the two largest useful units.
     * For example, 3725 becomes "1 hour, 2 minutes" and 90000 becomes "1 day, 1 hour".
     *
     * @param secs A duration in seconds.
     * @return The formatted duration, or "Nothing Yet!" for zero or less.
     */
    public static String secsToFormatTime(long secs) {
        if (secs <= 0) {
            return "Nothing Yet!";
        }

        if (secs < 60) {
            return plural(secs, "second");
        }

        if (secs < 3600) {
            return join(plural(secs / 60, "minute"), plural(secs % 60, "second"));
        }

        if (secs < 86400) {
            return join(plural(secs / 3600, "hour"), plural((secs % 3600) / 60, "minute"));
        }

        return join(plural(secs / 86400, "day"), plural((secs % 86400) / 3600, "hour"));
    }

    private static String plural(long amount, String unit) {
        if (amount == 0) {
            return null;
        }
        return amount + " " + unit + (amount == 1 ? "" : "s");
    }

    private static String join(String largest, String remainder) {
        if (remainder == null) {
            return largest;
        }
        return largest + ", " + remainder;
    }

    /**
     * Reads a String your plugin saved for this player. Like every getter, this loads the player's file
     * from disk, so a value that is still waiting in the save queue isn't visible yet.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path the value was saved under.
     * @return The value, or null if nothing is saved there.
     */
    public static String getString(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getString("Plugins." + plname.toUpperCase() + "." + location);
    }

    // GETTING & SETTING PLUGIN DATA ------------------------------------------

    // Strings -------------

    // Booleans --------------

    /**
     * Reads a boolean your plugin saved for this player.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path the value was saved under.
     * @return The value, or false if nothing is saved there.
     */
    public static boolean getBoolean(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location);
    }

    // End of Strings ------------

    // Config Keys


    // End of Config Keys

    /**
     * Finds every player whose boolean at {@code location} is true. This reads every player file, so call
     * it off the main thread. Requires {@link #connect}.
     *
     * @param pl        Your plugin (usually "this").
     * @param location  The path of the boolean.
     * @param quickmode true to take UUIDs from the file names instead of reading them from each file.
     * @return The usernames (not UUIDs) of the matching players. Empty if your plugin isn't connected.
     */
    public static ArrayList<String> getAllWithBoolean(Plugin pl, String location, boolean quickmode) {
        String plname = pl.getName();
        File cache = new File(plugin.getDataFolder(), File.separator + "Data");

        ArrayList<String> allplayers = new ArrayList<>();

        for (String playeruuid : getAllPlayerUUIDs(pl, quickmode)) {
            File f = new File(cache, File.separator + playeruuid + ".yml");
            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

            if (setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location)) {
                allplayers.add(setcache.getString("Username"));
            }
        }

        wasGet();
        return allplayers;
    }

    /**
     * Finds every player whose boolean at {@code location} is false or missing. This reads every player
     * file, so call it off the main thread. Requires {@link #connect}.
     *
     * @param pl        Your plugin (usually "this").
     * @param location  The path of the boolean.
     * @param quickmode true to take UUIDs from the file names instead of reading them from each file.
     * @return The usernames (not UUIDs) of the matching players. Empty if your plugin isn't connected.
     */
    public static ArrayList<String> getAllWithoutBoolean(Plugin pl, String location, boolean quickmode) {
        String plname = pl.getName();
        File cache = new File(plugin.getDataFolder(), File.separator + "Data");

        ArrayList<String> allplayers = new ArrayList<>();

        for (String playeruuid : getAllPlayerUUIDs(pl, quickmode)) {
            File f = new File(cache, File.separator + playeruuid + ".yml");
            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

            if (!setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location)) {
                allplayers.add(setcache.getString("Username"));
            }
        }

        wasGet();
        return allplayers;
    }

    /**
     * Reads an int your plugin saved for this player.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path the value was saved under.
     * @return The value, or 0 if nothing is saved there.
     */
    public static int getInt(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getInt("Plugins." + plname.toUpperCase() + "." + location);
    }

    // End of Booleans -------

    // Start of Int --------------

    // Start of Double --------------

    /**
     * Reads a double your plugin saved for this player.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path the value was saved under.
     * @return The value, or 0.0 if nothing is saved there.
     */
    public static double getDouble(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location);
    }
    // End of Int -------

    /**
     * Reads a long your plugin saved for this player.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path the value was saved under.
     * @return The value, or 0 if nothing is saved there.
     */
    public static long getLong(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getLong("Plugins." + plname.toUpperCase() + "." + location);
    }

    // End of Double -------

    // Start of Long --------------

    /**
     * Reads a list your plugin saved for this player, exactly as stored and without casting its elements.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path the list was saved under.
     * @return The list, or null if nothing is saved there.
     */
    public static List<?> getNCList(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getList("Plugins." + plname.toUpperCase() + "." + location);
    }
    // End of Long -------


    // Start of List --------------

    /**
     * Reads a list of ints your plugin saved for this player.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path the list was saved under.
     * @return A new, modifiable list. Empty if nothing is saved there.
     */
    public static List<Integer> getIntList(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getIntegerList("Plugins." + plname.toUpperCase() + "." + location);
    }

    /**
     * Reads an ItemStack your plugin saved for this player.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path the item was saved under.
     * @return The item, or null if nothing is saved there.
     */
    public static ItemStack getItemStack(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getItemStack("Plugins." + plname.toUpperCase() + "." + location);
    }

    /**
     * Reads a list of Strings your plugin saved for this player.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path the list was saved under.
     * @return A new, modifiable list. Empty if nothing is saved there.
     */
    public static List<String> getStringList(Plugin pl, String uuid, String location) {
        String plname = pl.getName();

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.getStringList("Plugins." + plname.toUpperCase() + "." + location);
    }

    /**
     * Lists the username of every player with a puuids file. This reads every player file, so call it off
     * the main thread. Requires {@link #connect}.
     *
     * @param pl Your plugin (usually "this").
     * @return The usernames. Empty if your plugin isn't connected.
     */
    public static ArrayList<String> getAllPlayerNames(Plugin pl) {
        ArrayList<String> allplayers = new ArrayList<>();

        if (!plugin.getPlugins().containsKey(pl)) {
            return allplayers;
        }

        // Gets the player names of all we have record of.

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");

        for (File cachefile : listData(cache)) {
            String path = cachefile.getPath();

            if (Files.getFileExtension(path).equalsIgnoreCase("yml")) {
                File f = new File(path);
                FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                allplayers.add(setcache.getString("Username"));
            }
        }
        wasGet();
        return allplayers;
    }

    /**
     * Lists the UUID of every player with a puuids file. Requires {@link #connect}.
     *
     * @param pl        Your plugin (usually "this").
     * @param quickmode true to take UUIDs from the file names, which is faster than reading every file.
     * @return The UUIDs as Strings. Empty if your plugin isn't connected.
     */
    public static ArrayList<String> getAllPlayerUUIDs(Plugin pl, boolean quickmode) {
        ArrayList<String> allplayers = new ArrayList<>();

        // Returns an empty list rather than null: callers were passing this straight into a
        // for-each wrapped in requireNonNull, so an unregistered plugin threw instead of no-oping.
        if (!plugin.getPlugins().containsKey(pl)) {
            return allplayers;
        }

        // Gets a list of all the uuids we have a record of.

        File cache = new File(plugin.getDataFolder(), File.separator + "Data");

        for (File cachefile : listData(cache)) {
            String path = cachefile.getPath();

            if (Files.getFileExtension(path).equalsIgnoreCase("yml")) {
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

    /** listFiles() returns null for a folder that doesn't exist yet, which is not an error here. */
    private static File[] listData(File cache) {
        final File[] files = cache.isDirectory() ? cache.listFiles() : null;
        return files == null ? new File[0] : files;
    }

    /**
     * Saves a location as X, Y, Z, Pitch, World and Yaw under {@code location}.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @param input    The location to save. It must have a world.
     * @return The task id of the last of those writes, or 0 if the call was rejected.
     */
    public static int setLocation(Plugin pl, String uuid, String location, Location input) {
        if (pl == null || uuid == null || location == null) {
            return 0;
        }

        plugin.set(pl, uuid, location + ".X", input.getX());
        plugin.set(pl, uuid, location + ".Y", input.getY());
        plugin.set(pl, uuid, location + ".Z", input.getZ());
        plugin.set(pl, uuid, location + ".Pitch", input.getPitch());
        if(plugin.getPlugins().get(pl) == APIVersion.V4) {
            plugin.set(pl, uuid, location + ".World", Objects.requireNonNull(input.getWorld()).getName());
        }

        return plugin.set(pl, uuid, location + ".Yaw", input.getYaw());
    }
    // End of List -------


    // Start of Location ----

    /**
     * Reads a location, ignoring the saved world and using {@code world} instead.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path the location was saved under.
     * @param world    The name of the world to place the location in.
     * @return The location, or null if your plugin isn't connected.
     * @deprecated Since API V4, locations are saved with their world. Use
     *             {@link #getLocation(Plugin, String, String)}.
     */
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
        File f = new File(cache, File.separator + uuid + ".yml");
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

    /**
     * Reads a location saved with {@link #setLocation}. Y is raised by 0.3 so a player teleported there
     * doesn't clip into the floor, and pitch and yaw come back as whole degrees. Requires {@link #connect}.
     * <p>
     * Check {@link #contains} first: if no location is saved at that path, this throws a
     * NullPointerException instead of returning null.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path the location was saved under.
     * @return The location, whose world is null if that world isn't loaded. Null if your plugin isn't
     *         connected.
     */
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
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        double prevx = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".X");
        double prevy = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".Y") + 0.3D;
        double prevz = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".Z");
        float prevpitch = setcache.getInt("Plugins." + plname.toUpperCase() + "." + location + ".Pitch");
        float prevyaw = setcache.getInt("Plugins." + plname.toUpperCase() + "." + location + ".Yaw");
        String world = setcache.getString("Plugins." + plname.toUpperCase() + "." + location + ".World");
        Location finalloc = new Location(plugin.getServer().getWorld(Objects.requireNonNull(world)), prevx, prevy, prevz, prevyaw, prevpitch);
        wasGet();
        return finalloc;
    }

    // Contains

    /**
     * Checks whether your plugin has a value, or a section, saved at {@code location} for this player.
     * Requires {@link #connect}.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path to check.
     * @return true if something is saved there; false otherwise, or if your plugin isn't connected.
     */
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
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        if (setcache.contains("Plugins." + plname.toUpperCase() + "." + location)) {
            return true;
        }

        wasGet();
        return false;
    }

    // End of Location

    // GET (v1.4 and +)

    /**
     * Reads the raw value at {@code location} exactly as loaded from the file: a String, Integer,
     * Boolean, List, ItemStack, ConfigurationSection and so on. Requires {@link #connect}.
     *
     * @param pl       Your plugin (usually "this").
     * @param uuid     The UUID of the player as a String.
     * @param location The path to read.
     * @return The value, or null if nothing is saved there. Returns Boolean.FALSE, not null, if an
     *         argument is null or your plugin isn't connected.
     */
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
        File f = new File(cache, File.separator + uuid + ".yml");
        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

        wasGet();
        return setcache.get("Plugins." + plname.toUpperCase() + "." + location);
    }
    // End of Contains.

    /**
     * Sets a value as null, removing it (and anything nested under it) from the player's file.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @return The task id of the queued write, or 0 if it was rejected.
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
     * Removes ALL set values for your plugin from this player's file.
     *
     * @param pl   Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid The UUID of the player as a String.
     * @return The task id of the queued write, or 0 if it was rejected.
     */
    public static int setNull(Plugin pl, String uuid) {
        if (pl == null || uuid == null) {
            return 0;
        }

        return plugin.set(pl, uuid, null);
    }

    /**
     * Add a string to an existing list.
     * <p>
     * The list is read from disk and the whole updated list is queued, so a second call for the same list
     * before the first has been saved overwrites it. To make several changes, build the list yourself and
     * call {@link #set} once.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @param add      The string you want to add and save to that player file.
     * @return The task id of the queued write, or 0 if it was rejected.
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
     * <p>
     * The list is read from disk and the whole updated list is queued, so a second call for the same list
     * before the first has been saved overwrites it.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @param add      The int you want to add and save to that player file.
     * @return The task id of the queued write, or 0 if it was rejected.
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
     * Remove a string from an existing list. Only the first matching entry is removed.
     * <p>
     * The list is read from disk and the whole updated list is queued, so a second call for the same list
     * before the first has been saved overwrites it.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @param add      The string you want to remove and save to that player file.
     * @return The task id of the queued write, or 0 if it was rejected.
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
     * Remove an int from an existing int list. Only the first matching entry is removed.
     * <p>
     * The list is read from disk and the whole updated list is queued, so a second call for the same list
     * before the first has been saved overwrites it.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @param add      The int you want to remove and save to that player file.
     * @return The task id of the queued write, or 0 if it was rejected.
     */
    public static int removeFromIntList(Plugin pl, String uuid, String location, int add) {
        if (pl == null || uuid == null || location == null) {
            return 0;
        }

        List<Integer> input = getIntList(pl, uuid, location);
        // Was input.add(add): this method appended instead of removing.
        input.remove(Integer.valueOf(add));
        return plugin.set(pl, uuid, location, input);
    }
    // End of List Add

    // v1.4.5 and + remove to int list

    /**
     * Set plugin data for your plugin in a player's data file.
     * <p>
     * The write is queued and saved off the main thread shortly afterwards, so this is cheap to call from
     * any thread. Until it is saved, the getters still return the old value. A {@link TimerSaved} event
     * carrying the returned task id is fired once it is on disk.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param uuid     The UUID of the player as a String.
     * @param location Where under the player file to save as? (Similar to Configuration save paths)
     * @param input    Accepts a boolean/int/long or other value to set: anything a YamlConfiguration can
     *                 save, including lists and ItemStacks. Null removes the value.
     * @return The task id of the queued write, or 0 if it was rejected (for example, because your plugin
     *         isn't connected).
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
     * <p>
     * This reads every player file on the calling thread, then queues one write per player that needs the
     * default.
     *
     * @param pl       Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
     * @param location Where do you want the value to be set?
     * @param input    What to set the default value as?
     * @return true once the defaults have been queued (even if no file needed one); false if called after
     *         startup, if your plugin isn't connected, or if an argument is null.
     */
    public static boolean addToAllWithout(Plugin pl, String location, Object input) {
        if (pl == null || location == null || input == null) {
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
                File f = new File(cache, File.separator + playeruuid + ".yml");
                FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                final String path = "Plugins." + plname.toUpperCase() + "." + location;
                if (!setcache.contains(path) || setcache.get(path) == null) {
                    plugin.set(pl, playeruuid, location, input);
                    total++;
                }
            } catch (Exception err) {
                return false;
            }
        }

        if (total != 0) {
            plugin.debug(plname + " updated " + total + " files with their missing values.");
        }
        return true;
    }
    // End of SET


    /**
     *  Returns the UUID that was randomly generated on the first run of PUUIDs for this server
     *  (stored as {@code Advanced.UUID} in its config.yml). It stays the same across restarts.
     *
     * @return This server's ID.
     */
    public static String getServerId() {
        return plugin.getServerId();
    }

    // Internal counter
    private static void wasGet() {
        plugin.getTimes.incrementAndGet();
    }



    // General

    /**
     * API versions for {@link #connect(Plugin, APIVersion)}. Always pass the newest one: this release
     * only accepts {@link #V4}.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    public enum APIVersion {
        @Deprecated V1,
        @Deprecated V2,
        @Deprecated V3,
        V4
    }
    // End of General
}

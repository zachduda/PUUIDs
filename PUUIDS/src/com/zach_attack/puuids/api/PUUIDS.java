package com.zach_attack.puuids.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.google.common.io.Files;
import com.zach_attack.puuids.Main;

public class PUUIDS {
	private static Main plugin = Main.getPlugin(Main.class);
	
   /**
    * Authorizes with PUUIDs. While not required for some functions, it is required for getting / setting plugin data. 
    * 
	* @param pl Your plugin, most of the time, you can just put "this"
	* @return Returns true or false if the connection was successful.
	*/
	public static boolean connect(Plugin pl) {
		if(plugin.connect(pl)) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Returns a players UUID from their Name as a String.
	 * 
	 * @param name The player's username you want the UUID of.
	 * @param usemojang If we're having trouble, should PUUIDs ask Mojang for help when getting UUIDs. (Resource Intensive)
	 * @return The UUID of a player as a String
	 */
	public static String getUUID(String name, boolean usemojang) {
		wasGet();
		return plugin.nametoUUID(name, usemojang);
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
	 * Runs getName() w/o Mojang, and if it returns "0", we return false.
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
		String ans = secsToFormatTime(time);
		wasGet();
		return ans;
	}
	
	public static String secsToFormatTime(long secs) {
		if(secs == 0) {
			return "Nothing Yet!";
		} else if(secs < 60) {
			if(secs == 1) { 
				return secs + " second";
			} else {
				return secs + " seconds";
			}
		} else if(secs < 3600) {
			final long min = secs/60;
			if(min == 1) {
				return min + " minute";
			} else {
				return min + " minutes";
			}
		} else if(secs < 86400) {
			final long hours = secs/3600;
			if(hours == 1) {
				return hours + " hour";
			} else {
				return hours + " hours";
			}
		} else {
			final long days = secs/86400;
			if(days == 1) {
				return days + " day";
			} else {
				return days + " days";	
			}
		}
	}
	
	// GETTING & SETTING PLUGIN DATA ------------------------------------------
	
	// Strings -------------
	
	public static String getString(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		wasGet();
		return setcache.getString("Plugins." + plname.toUpperCase() + "." + location);
	}
	
	// End of Strings ------------
	
	// Booleans --------------	
	public static boolean getBoolean(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		wasGet();
		return setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location);
	}
	
	public static ArrayList<String> getAllWithBoolean(Plugin pl, String location, boolean quickmode) {
		String plname = pl.getName();
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		
		ArrayList<String> allplayers = new ArrayList<String>();
		
		for (String playeruuid : getAllPlayerUUIDs(plugin, quickmode)) {
			File f = new File(cache, File.separator + "" + playeruuid + ".yml");
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
			
			if(setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location)) {
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
		
		for (String playeruuid : getAllPlayerUUIDs(plugin, quickmode)) {
			File f = new File(cache, File.separator + "" + playeruuid + ".yml");
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
			
			if(!setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location)) {
				allplayers.add(setcache.getString("Username"));
			}
		}
		
		wasGet();
		return allplayers;
	}
	
	// End of Booleans -------
	
	// Start of Int --------------
	
	public static int getInt(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		wasGet();
		return setcache.getInt("Plugins." + plname.toUpperCase() + "." + location);
	}
	// End of Int -------
	
	// Start of Double --------------	
	public static double getDouble(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		wasGet();
		return setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location);
	}
	
	// End of Double -------
	
	// Start of Long --------------
	
	public static long getLong(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		wasGet();
		return setcache.getLong("Plugins." + plname.toUpperCase() + "." + location);
	}
	// End of Long -------
	
	
	// Start of List --------------
	
	public static List<?> getNCList(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		wasGet();
		return setcache.getList("Plugins." + plname.toUpperCase() + "." + location);
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
		if(!plugin.getPlugins().contains(plname)) {
			return null;
		}		
		
		// Gets the player names of all we have record of.
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		
		ArrayList<String> allplayers = new ArrayList<String>();
		
		for (File cachefile : cache.listFiles()) {
			String path = cachefile.getPath();
			
			if(!Files.getFileExtension(path).equalsIgnoreCase("yml")) {
				// NOT A VALID FILE
			} else {
				
			File f = new File(path);
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
			allplayers.add(setcache.getString("Username"));
		}}
		wasGet();
		return allplayers;
	}
	
	
	public static ArrayList<String> getAllPlayerUUIDs(Plugin pl, boolean quickmode) {
		String plname = pl.getName();
		if(!plugin.getPlugins().contains(plname)) {
			return null;
		}		
		
		// Gets a list of all the uuids we have a record of.
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		
		ArrayList<String> allplayers = new ArrayList<String>();
		
		for (File cachefile : cache.listFiles()) {
			String path = cachefile.getPath();
			
			if(!Files.getFileExtension(path).equalsIgnoreCase("yml")) {
				// NOT A VALID FILE
			} else {
				
			File f = new File(path);
			if(quickmode) {
				allplayers.add(f.getName().replace(".yml", ""));
			} else {
				FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
				allplayers.add(setcache.getString("UUID"));
			}}
		}
		wasGet();
		return allplayers;
	}
	// End of List -------
	
	
	// Start of Location ----
	
	public static void setLocation(Plugin pl, String uuid, String location, Location input) {
        if(!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Cannot set data ASYNC when using PUUIDs set method. Stopping to prevent corruption!");
        }
        
		if(pl == null || uuid == null || location == null) {
			return;
		}
		
		String plname = pl.getName();
		
		if(!plugin.getPlugins().contains(plname)) {
			plugin.debug("Not allowing " + pl.getName() + " to save data. They didn't connect properly.");
			return;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" +  uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location + ".X", input.getX());
		setcache.set("Plugins." + plname.toUpperCase() + "." + location + ".Y", input.getY());
		setcache.set("Plugins." + plname.toUpperCase() + "." + location + ".Z", input.getZ());
		setcache.set("Plugins." + plname.toUpperCase() + "." + location + ".Pitch", input.getPitch());
		setcache.set("Plugins." + plname.toUpperCase() + "." + location + ".Yaw", input.getYaw());
		
		if(plugin.asyncrunning) {
			plugin.debug("Blocking " + plname + " from setting info, PUUIDs is running a large task async.");
		} else {
			try {
				setcache.save(f);
			} catch (IOException e) {}
		}
		wasSet();
	}
	
	public static Location getLocation(Plugin pl, String uuid, String location, String world) {
		if(pl == null || uuid == null || location == null) {
			return null;
		}
		
		String plname = pl.getName();
		
		if(!plugin.getPlugins().contains(plname)) {
			plugin.debug("Not allowing " + pl.getName() + " to access data. They didn't connect properly.");
			return null;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" +  uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		double prevx = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".X");
		double prevy = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".Y")+0.3D;
		double prevz = setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location + ".Z");
		float prevpitch = setcache.getInt("Plugins." + plname.toUpperCase() + "." + location + ".Pitch");
		float prevyaw = setcache.getInt("Plugins." + plname.toUpperCase() + "." + location + ".Yaw");
		
 	  Location finalloc = new Location(plugin.getServer().getWorld(world), prevx, prevy, prevz, prevyaw, prevpitch);
		wasGet();
		return finalloc;
	}
	
	// End of Location
	
	// Contains
	public static boolean contains(Plugin pl, String uuid, String location) {
		if(pl == null || uuid == null || location == null) {
			return false;
		}
		
		String plname = pl.getName();
		
		if(!plugin.getPlugins().contains(plname)) {
			plugin.debug("Not allowing " + pl.getName() + " to access data. They didn't connect properly.");
			return false;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		if(setcache.contains("Plugins." + plname.toUpperCase() + "." + location)) {
			return true;
		}
		
		wasGet();
		return false;
	}
	// End of Contains.
	
	
	// GET (v1.4 and +)
	public static Object get(Plugin pl, String uuid, String location) {	
		if(pl == null || uuid == null || location == null) {
			return false;
		}
		
		String plname = pl.getName();
		
		if(!plugin.getPlugins().contains(plname)) {
			plugin.debug("Not allowing " + pl.getName() + " to access data. They didn't connect properly.");
			return false;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		wasGet();
		return setcache.get("Plugins." + plname.toUpperCase() + "." + location);
	}
	// END GET
	
	
	// SET (v1.3 and +)
	/**
	 * Set plugin data for your plugin in a player's data file.
	 * 
	 * @param pl Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
	 * @param uuid The UUID of the player as a String.
	 * @param location Where under the player file to save as? (Similar to Configuration save paths)
	 * @param input Accepts a boolean/int/long or other value to set.
	 * @return The UUID of a player as a String
	 */
	public static boolean set(Plugin pl, String uuid, String location, Object input) {
		if(pl == null || uuid == null || location == null) {
			return false;
		}
		
        if(!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Cannot set data ASYNC when using PUUIDs set method. Stopping to prevent corruption!");
        }
		
		String plname = pl.getName();
		
		if(!plugin.getPlugins().contains(plname)) {
			plugin.debug("Not allowing " + pl.getName() + " to access data. They didn't connect properly.");
			return false;
		}
		
		long start = System.currentTimeMillis();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" +  uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
		
		if(plugin.asyncrunning) {
			plugin.debug("Blocking " + plname + " from setting info, PUUIDs is running a large task async.");
		} else {
			try {
				setcache.save(f);
			} catch (IOException e) {}
		}
		
		plugin.setTimeMS = System.currentTimeMillis()-start;
		wasSet();
		return true;
	}
	// End of SET
	
	
	// Update Existing
	/**
	 * Sets default values for already existing files without said value. Can only be executed while connections are being accepted.
	 * 
	 * @param pl Your plugin (usually "this"). Must be authenticated via PUUIDS.connect(this);
	 * @param location Where do you want the value to be set?
	 * @param input What to set the default value as?
	 */
	public static void addToAllWithout(Plugin pl, String location, Object input) {
		if(pl == null || location == null || location == null || input == null) {
			return;
		}
		
        if(!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Cannot set data ASYNC when using PUUIDs addToAllWithout method. Stopping to prevent corruption!");
        }
		
		String plname = pl.getName();
		
        
        if(!plugin.allowConnections()) {
        	plugin.debug(plname + " tried to set addToAllWithout method AFTER startup, this isn't allowed.");
        	return;
        }
		
		if(!plugin.getPlugins().contains(plname)) {
			plugin.debug("Not allowing " + pl.getName() + " to access data. They didn't connect properly.");
			return;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		
		int total = 0;
		
		for (String playeruuid : getAllPlayerUUIDs(plugin, false)) {
			try {
			File f = new File(cache, File.separator + "" + playeruuid + ".yml");
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
			
			if(!setcache.contains("Plugins." + plname.toUpperCase() + "." + location) || setcache.get("Plugins." + plname.toUpperCase() + "." + location) == null) {
				setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
				setcache.save(f);
				total++;
			}
			} catch (Exception err) {}
		}
		
		if(total != 0) {
		plugin.debug(plname + " updated " + total + " files with their missing values.");
		}
		total = 0;
	}
	
	// End of Updates
	
	
	// General
	private static void wasSet() {
		plugin.setTimes++;
	}
	
	private static void wasGet() {
		plugin.getTimes++;
	}
	// End of General
}

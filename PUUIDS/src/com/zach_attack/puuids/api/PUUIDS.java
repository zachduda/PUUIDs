package com.zach_attack.puuids.api;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.google.common.io.Files;
import com.zach_attack.puuids.Main;

public class PUUIDS {
	private static Main plugin = Main.getPlugin(Main.class);
	
	public static boolean connect(Plugin pl) {
		if(plugin.connect(pl)) {
			return true;
		} else {
			return false;
		}
	}
	
	public static String getUUID(String name, boolean usemojang) {
		return plugin.nametoUUID(name, usemojang);
	}
	
	public static String getName(String uuid) {
		return plugin.UUIDtoname(uuid);
	}
	
	public static boolean hasFile(String uuid) {
		return plugin.hasPlayedUUID(uuid);
	}
	
	public static long getLastOn(String uuid) {
		return plugin.getLastOn(uuid);
	}
	
	public static String getIP(String uuid) {
		return plugin.getPlayerIP(uuid);
	}
	
	public static long getPlayTime(String uuid) {
		return plugin.getPlayTime(uuid);
	}
	
	public static String getFormatedPlayTime(String uuid) {
		long time = getPlayTime(uuid);
		String ans = secsToFormatTime(time);
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
			final long hours = secs/86400;
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
		
		return setcache.getString("Plugins." + plname.toUpperCase() + "." + location);
	}
	
	// End of Strings ------------
	
	// Booleans --------------	
	public static boolean getBoolean(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
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
		
		return allplayers;
	}
	
	// End of Booleans -------
	
	// Start of Int --------------
	
	public static int getInt(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getInt("Plugins." + plname.toUpperCase() + "." + location);
	}
	// End of Int -------
	
	// Start of Double --------------	
	public static double getDouble(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location);
	}
	
	// End of Double -------
	
	// Start of Long --------------
	
	public static long getLong(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getLong("Plugins." + plname.toUpperCase() + "." + location);
	}
	// End of Long -------
	
	
	// Start of List --------------
	
	public static List<?> getNCList(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getList("Plugins." + plname.toUpperCase() + "." + location);
	}
	
	public static ItemStack getItemStack(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getItemStack("Plugins." + plname.toUpperCase() + "." + location);
	}
	
	public static List<String> getStringList(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getStringList("Plugins." + plname.toUpperCase() + "." + location);
	}
	
	public static ArrayList<String> getAllPlayerNames(Plugin pl) {
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
		
		return allplayers;
	}
	
	
	public static ArrayList<String> getAllPlayerUUIDs(Plugin pl, boolean quickmode) {
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
		
		return allplayers;
	}
	// End of List -------
	
	// Contains
	public static boolean contains(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		if(setcache.contains("Plugins." + plname.toUpperCase() + "." + location)) {
			return true;
		}
		
		return false;
	}
	// End of Contains.
	
	
	// GET (v1.4 and +)
	public static Object get(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.get("Plugins." + plname.toUpperCase() + "." + location);
	}
	// END GET
	
	
	// SET (v1.3 and +)
	public static void set(Plugin pl, String uuid, String location, Object input) {
        if(!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Cannot set data ASYNC when using PUUIDs set method. Stopping to prevent corruption!");
        }
        
		long start = System.currentTimeMillis();
		String plname = pl.getName();
		
		if(!plugin.getPlugins().contains(plname)) {
			return;
		}		
		
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
		
		plugin.setTime = System.currentTimeMillis()-start;
	}
	// End of SET
}

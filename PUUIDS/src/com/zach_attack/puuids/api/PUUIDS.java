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

import com.zach_attack.puuids.Main;

public class PUUIDS {
	private static Main plugin = Main.getPlugin(Main.class);
	
	public static ArrayList<String> getPlugins() {
		return plugin.getPlugins();
	}
	
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
	
	public static void updateName(Plugin pl, String uuid, String name) {
		String plname = pl.getDescription().getName();
		if(getPlugins().contains(plname)) {
			plugin.updateName(uuid, name);
			plugin.getLogger().info("Updating Name & UUID values for " + name + " from " + plname + "...");
		} else {
			plugin.getLogger().warning("Unauthorized attempt to change user data (updateName) by plugin: " + plname + "");
		}
	}
	
	public static long getLastOn(String uuid) {
		// REQUIRES a UUID
		return plugin.getLastOn(uuid);
	}
	
	public static String getIP(String uuid) {
		// REQUIRES a UUID
		return plugin.getPlayerIP(uuid);
	}
	
	
	// GETTING & SETTING PLUGIN DATA ------------------------------------------
	
	// Strings -------------
	@Deprecated
	public static void setString(Plugin pl, String uuid, String location, String input) {
		String plname = pl.getName();
		
		if(!getPlugins().contains(plname)) {
			return;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
		try {
			setcache.save(f);
		} catch (IOException e) {}
	}
	
	public static String getString(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getString("Plugins." + plname.toUpperCase() + "." + location);
	}
	
	// End of Strings ------------
	
	// Booleans --------------
	@Deprecated
	public static void setBoolean(Plugin pl, String uuid, String location, boolean input) {
		String plname = pl.getName();
		
		if(!getPlugins().contains(plname)) {
			return;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
		try {
			setcache.save(f);
		} catch (IOException e) {}
	}
	
	public static boolean getBoolean(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location);
	}
	
	public static ArrayList<String> getAllWithBoolean(Plugin pl, String location) {
		String plname = pl.getName();
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		
		ArrayList<String> allplayers = new ArrayList<String>();
		
		for (String playeruuid : getAllPlayerUUIDs(plugin)) {
			File f = new File(cache, File.separator + "" + playeruuid + ".yml");
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
			
			if(setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location) == true) {
				allplayers.add((String) setcache.get("Username"));
			}
		}
		
		return allplayers;
	}
	
	public static ArrayList<String> getAllWithoutBoolean(Plugin pl, String location) {
		String plname = pl.getName();
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		
		ArrayList<String> allplayers = new ArrayList<String>();
		
		for (String playeruuid : getAllPlayerUUIDs(plugin)) {
			File f = new File(cache, File.separator + "" + playeruuid + ".yml");
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
			
			if(setcache.getBoolean("Plugins." + plname.toUpperCase() + "." + location) == false) {
				allplayers.add((String) setcache.get("Username"));
			}
		}
		
		return allplayers;
	}
	
	// End of Booleans -------
	
	// Start of Int --------------
	@Deprecated
	public static void setInt(Plugin pl, String uuid, String location, int input) {
		String plname = pl.getName();
		
		if(!getPlugins().contains(plname)) {
			return;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
		try {
			setcache.save(f);
		} catch (IOException e) {}
	}
	
	public static int getInt(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getInt("Plugins." + plname.toUpperCase() + "." + location);
	}
	// End of Int -------
	
	// Start of Double --------------
	@Deprecated
	public static void setDouble(Plugin pl, String uuid, String location, double input) {
		String plname = pl.getName();
		
		if(!getPlugins().contains(plname)) {
			return;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
		try {
			setcache.save(f);
		} catch (IOException e) {}
	}
	
	public static double getDouble(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getDouble("Plugins." + plname.toUpperCase() + "." + location);
	}
	// End of Double -------
	
	// Start of Long --------------
	@Deprecated
	public static void setLong(Plugin pl, String uuid, String location, long input) {
		String plname = pl.getName();
		
		if(!getPlugins().contains(plname)) {
			return;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
		try {
			setcache.save(f);
		} catch (IOException e) {}
	}
	
	public static long getLong(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getLong("Plugins." + plname.toUpperCase() + "." + location);
	}
	// End of Long -------
	
	// Start of List --------------
	@Deprecated
	public static void setStringList(Plugin pl, String uuid, String location, List<String> input) {
		String plname = pl.getName();
		
		if(!getPlugins().contains(plname)) {
			return;
		}		
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
		try {
			setcache.save(f);
		} catch (IOException e) {}
	}
	
	@Deprecated
	public static void setNCList(Plugin pl, String uuid, String location, List<?> input) {
		String plname = pl.getName();
		
		if(!getPlugins().contains(plname)) {
			return;
		}		
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
		try {
			setcache.save(f);
		} catch (IOException e) {}
	}
	
	public static List<?> getNCList(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		return setcache.getList("Plugins." + plname.toUpperCase() + "." + location);
	}
	
	@Deprecated
	public static void setItemStack(Plugin pl, String uuid, String location, ItemStack[] input) {
		String plname = pl.getName();
		
		if(!getPlugins().contains(plname)) {
			return;
		}		
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" +  uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
		try {
			setcache.save(f);
		} catch (IOException e) {}
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
		
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
		for (File cachefile : cache.listFiles()) {
			File f = new File(cachefile.getPath());
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
			allplayers.add((String) setcache.get("Username"));
		}});
		
		return allplayers;
	}
	
	
	public static ArrayList<String> getAllPlayerUUIDs(Plugin pl) {
		// Gets a list of all the uuids we have a record of.
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		
		ArrayList<String> allplayers = new ArrayList<String>();
		
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
		for (File cachefile : cache.listFiles()) {
			File f = new File(cachefile.getPath());
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
			allplayers.add((String) setcache.get("UUID"));
		}});
		
		return allplayers;
	}
	// Start of List -------

	// Null
	@Deprecated
	public static void setNull(Plugin pl, String uuid, String location) {
		String plname = pl.getName();
		
		if(!getPlugins().contains(plname)) {
			return;
		}		
		
		if(uuid == null || uuid == "0") {
			return;
		}
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location, null);
		try {
			setcache.save(f);
		} catch (IOException e) {}
	}
	// End of Null
	
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
	
	// v1.3 new SET
	public static void set(Plugin pl, String uuid, String location, Object input) {
		String plname = pl.getName();
		
		if(!getPlugins().contains(plname)) {
			return;
		}		
		
		File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" +  uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		setcache.set("Plugins." + plname.toUpperCase() + "." + location, input);
		try {
			setcache.save(f);
		} catch (IOException e) {}
	}
	// End of new v1.3 SET
}

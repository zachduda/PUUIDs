package com.zach_attack.puuids;

import java.io.File;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.zach_attack.puuids.Updater;
import com.zach_attack.puuids.api.OnNewFile;
import com.zach_attack.puuids.api.PUUIDS;

public class Main extends JavaPlugin implements Listener {
	
    private static ArrayList<String> plugins = new ArrayList<String>();
	private static boolean allowconnections = false;
    
	boolean debug = false;
	
	boolean updatecheck = true;
	
	public PUUIDS api;
	
	public void onEnable() {
		plugins.add(getDescription().getName());
		allowconnections = true;
		
		Bukkit.getServer().getPluginManager().registerEvents(this, this);
		
		getConfig().options().copyDefaults(true);
		saveConfig();
		
		updateConfig();
		
		api = new PUUIDS();
		
		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable()
		  {
		    public void run()
		     {
		    	
				if(debug) {
		    	int total = plugins.size()-1;
		    	if(total == 1) {
		    		debug("Hooked with " + total + " plugin.");
		    	} else if(total == 0) {
		    		debug("There aren't any plugins hooked with PUUIDS yet.");	
		    	} else {
		    		debug("Hooked with " + total + " plugins.");
		    	}}
				
		        allowconnections = false;
		     }
		  });
		
		if(getConfig().getBoolean("Settings.Update-Check")) {
			new Updater(this).checkForUpdate();
		}
	}
	
	private void debug(String input) {
		getLogger().info("[Debug] " + input);
	}
	
	public void onDisable() {
		plugins.clear();
		allowconnections = false;
	}
	
	private void updateConfig() {
		debug = getConfig().getBoolean("Settings.Debug");
		updatecheck = getConfig().getBoolean("Settings.Update-Check");
	}

	public ArrayList<String> getPlugins() {
		return plugins;
	}
	
	public boolean connect(Plugin pl) {
		String plname = pl.getDescription().getName();
		
		if(!allowconnections) { 
			getLogger().warning("Plugin '" + plname + "' tried to register with PUUID after the connection window.");
			return false;
		}
		
		if(!plugins.contains(plname)) {
			plugins.add(plname);
			debug("Plugin " + plname  + " has been registered.");
			return true;
		}
		
		getLogger().warning("Plugin '" + plname + "' tried to overwrite another plugin registered with PUUID. Contact " + pl.getDescription().getAuthors().toString() + ".");
	   return false;
	}
	
	public boolean isConnected(Plugin pl) {
		if(plugins.contains(pl.getDescription().getName())) {
			return true;
		} else {
			return false;
		}
	}
	
	public String nametoUUID(String inputsearch, boolean usemojang) {
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
		
		if(usemojang) {
			String mojanguuid = Mojang.getUUIDfromName(inputsearch);
		if(mojanguuid != "0") {
			return mojanguuid;
		}}
		
		return "0";
	}
	
	public boolean hasPlayedUUID(String uuid) {
		File cache = new File(this.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		
		if(f.exists()) {
			return true;
		}
		
		return false;
	}
	
	public boolean hasPlayedName(String name) {
		if(nametoUUID(name, false) == "0") {
			return false;
		} else {
			return true;
		}
	}
	
	public long getLastOn(String uuid) {
		File cache = new File(this.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		if(!f.exists()) {
			return 0;
		}
		
		if(!setcache.contains("Last-On")) {
			return 0;
		}
		
		return setcache.getLong("Last-On");
	}
	
	public String getPlayerIP(String uuid) {
		File cache = new File(this.getDataFolder(), File.separator + "Data");
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		if(!f.exists()) {
			return "0";
		}
		
		if(!setcache.contains("IP")) {
			return "0";
		}
		
		return setcache.getString("IP");
	}
	
	public void updateName(String uuid, String name) {
			File cache = new File(this.getDataFolder(), File.separator + "Data");
			File f = new File(cache, File.separator + "" + uuid + ".yml");
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

			if (!f.exists()) {
				try {
					setcache.save(f);
				} catch (Exception err) {
				}
			}
		
		    setcache.set("UUID", uuid);
		    setcache.set("Username", name);
		    setcache.set("Last-On", System.currentTimeMillis());
		    
			try {
				setcache.save(f);
			} catch (Exception err) {}
	}
	
	public String UUIDtoname(String inputsearch) {
		File folder = new File(this.getDataFolder(), File.separator + "Data");

		for (File AllData : folder.listFiles()) {
			File f = new File(AllData.getPath());
			
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

			String playername = setcache.getString("Username");
			String UUID = setcache.getString("UUID");

			if (inputsearch.toString().equalsIgnoreCase(UUID)) {
				return playername;
			}}
		return "0";
	}

	
	private void updateFile(Player p) {
			File cache = new File(this.getDataFolder(), File.separator + "Data");
			File f = new File(cache, File.separator + "" + p.getUniqueId().toString() + ".yml");
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

			if (!f.exists()) {
				try {
					if(debug) {
						debug(p.getName() + " is new, creating a file for them.");
					}
					setcache.save(f);
				} catch (Exception err) {}
				
				Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable()
				  {
				    public void run()
				     {
						OnNewFile fse = new OnNewFile(p);
						Bukkit.getPluginManager().callEvent(fse);
				     }
				  });
			}
			
			Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			String UUID = p.getUniqueId().toString();
			String IPAdd = p.getAddress().getAddress().toString().replace(p.getAddress().getHostString() + "/", "").replace("/", "");
			
		    setcache.set("UUID", UUID);
			setcache.set("IP", IPAdd);
			setcache.set("Username", p.getName().toString());			
			setcache.set("Last-On", System.currentTimeMillis());
			
			try {
				setcache.save(f);
			} catch (Exception err) {}});
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		
		updateFile(p);
		
		if(updatecheck) {
		if(p.hasPermission("puuids.admin") || p.isOp()) {
		if (Updater.isOutdated()) {
			p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c&lOutdated Plugin! &7Running v" + getDescription().getVersion()
			+ " while the latest is &f&l" + Updater.getOutdatedVersion()));
		}}}
		
		if (p.getUniqueId().toString().equals("6191ff85-e092-4e9a-94bd-63df409c2079")) {
			p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7This server is running &fPUUIDs &6v" + getDescription().getVersion()
			+ " &7for " + Bukkit.getBukkitVersion().replace("-SNAPSHOT", "")));
		}
	}
	
}

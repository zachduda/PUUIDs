package com.zach_attack.puuids;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

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

import com.google.common.io.Files;
import com.zach_attack.puuids.Updater;
import com.zach_attack.puuids.api.OnNewFile;
import com.zach_attack.puuids.api.PUUIDS;

public class Main extends JavaPlugin implements Listener {
	
	private String version = Bukkit.getBukkitVersion();
	
    private static ArrayList<String> plugins = new ArrayList<String>();
    private static ArrayList<Player> queuedJoinUpdates = new ArrayList<Player>();
    
	private static boolean allowconnections = false;
    private boolean sounds;
    private boolean status;
    private String statusreason = "0";
	
	protected boolean updatecheck = true;
	protected String prefix = "&8[&e&lPUUIDs&8]";
	
	public boolean debug = false;
	public boolean asyncrunning = false;
	public long setTime = 0;
	
	public PUUIDS api;
	
	public void onEnable() {
		status = true;
		statusreason = "0";
		asyncrunning = true;
		boolean useclean = getConfig().getBoolean("Settings.File-Cleanup.Enabled");
			
		
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
		File folder = new File(this.getDataFolder(), File.separator + "Data");
		if (!folder.exists()) {
			return;
		}
		
		ArrayList<String> unknownfiles = new ArrayList<String>();
		
		int maxDays = getConfig().getInt("Settings.File-Cleanup.Max-Days");

		for (File cachefile : folder.listFiles()) {
			String path = cachefile.getPath();
			
			File f = new File(path);
					
			if(!Files.getFileExtension(path).equalsIgnoreCase("yml")) {
				unknownfiles.add(f.getName());
			} else {
						
			try {
				FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
					
				if(!setcache.contains("Last-On") || (!setcache.contains("Username")) || (!setcache.contains("UUID"))) {
					f.delete();
					debug("Deleted file: " + f.getName() + "... It was invalid!");
				} else {
					
					long daysAgo = Math
							.abs(((setcache.getLong("Last-On")) / 86400000) - (System.currentTimeMillis() / 86400000));

					String playername = setcache.getString("Username");
			
					if (daysAgo >= maxDays && useclean) {
						f.delete();
						if (debug) {
							getLogger().info("[Debug] Deleted " + playername + "'s data file because it's " + daysAgo
									+ "s old. (Max: " + maxDays + " Days)");
						}} else {
							
						
						if (debug) {
							if(useclean) {
								getLogger().info("[Debug] Keeping " + playername + "'s data file. (" + daysAgo + "/" + maxDays
									+ ")");
							} else {
								getLogger().info("[Debug] Found " + playername + "'s data file. (" + daysAgo + " days)");	
							}
						}
					} // end of not too old check.
					} // end of contains variables check.
					} catch(Exception err) {
						status = false;
						statusreason = "Error when trying to save a player file during the clean-up start cycle.";
						if(debug) {
							debug("Error when trying to work with player file: " + f.getName() + ", see below:");
							err.printStackTrace();
						}
					} // end of catch err
				} // end of if not global check
			} // end of For loop
		
			if(unknownfiles.size() >= 1) {
				getLogger().warning("Found " + unknownfiles.size() + " unknown files in your Data folder:");
				for(String file : unknownfiles) {
					debug("   - " + file);
				}
				getLogger().warning("Make sure that the files above weren't missplaced or corrupted.");
				status = false;
				statusreason = "Unknown file was found in your PUUIDs Data folder, please remove the following files: " + unknownfiles.toString();
			}
	
			asyncrunning = false;
			unknownfiles.clear();
		}); // End of Async;
		
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
		
		if(updatecheck) {
			new Updater(this).checkForUpdate();
		}
		
		// Players shouldn't EVER be online when we are starting....
		Collection<? extends Player> players = Bukkit.getOnlinePlayers();
		if(players.size() > 0) {
			status = false;
		    statusreason = "PUUIDs was improperly reloaded. This may damage your player's data files! Please restart your server.";
			Msgs.sendPrefix(Bukkit.getConsoleSender(), "&4&l<!> &c&l&nReloading PUUIDs without a proper restart can severely damage PUUID's player data. PLEASE RESTART YOUR SERVER!");
			for(Player online : players) {
				if(online.isOp() || online.hasPermission("puuids.admin")) {
					Msgs.sendPrefix(online, "&c&lWARNING: &fPUUIDs has been improperly reloaded. This will cause data loss and possible damage to other plugins.");
					if(sounds) {
						online.playSound(online.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2.0F, 2.0F);
					}
			}
		}}
		
		if(getConfig().getBoolean("Settings.Metrics", true)) {
			MetricsLite metrics = new MetricsLite(this);
			if(!metrics.isEnabled()) {
				debug("Metrics have been disabled in the bStats folder. Guess we won't support all this hard work today!");
			}
		} else {
			debug("Metrics have been disabled in the config.yml. Guess we won't support all this hard work today!");
		}
	}
	
	private void runQueuedJoinUpdates() {
		int total = 0;
		
		for(Player p : queuedJoinUpdates) {
			if(p.isOnline()) {
				updateFile(p, false, false);
			}
			queuedJoinUpdates.remove(p);
			total++;
		}
		
		if(debug) {
			debug("Reupdated " + total + " player files that were skipped.");
		}
		
		total = 0;
	}
	
	public void debug(String input) {
		if(debug) {
			getLogger().info("[Debug] " + input);
		}
	}
	
	private boolean isSupported() {
		
		if(version.contains("1.14")) {
			return true;
		}
	
		if(version.contains("1.13")) {
			return true;
		}
		
		return false;
	}
	
	public void onDisable() {
		final long start = System.currentTimeMillis();
		
		try {
			int onlinenum = Bukkit.getOnlinePlayers().size();
			if(onlinenum >= 1) {
				for(Player online : Bukkit.getOnlinePlayers()) {
						updateFile(online, true, true);
					}
				debug("Shutting down... Updating " + onlinenum + " players files.");
			}
		} catch (Exception err) {
			debug("Couldn't save player data at the last minute, oh well...");
		}
		
		plugins.clear();
		
		Cooldowns.joined.clear();
		Cooldowns.ontime.clear();
		queuedJoinUpdates.clear();
		
		setTime = 0;
		allowconnections = false;
		
		getLogger().info("Successfully disabled in " + Long.toString(System.currentTimeMillis()-start) + "ms");
	}
	
	private void updateConfig() {
		debug = getConfig().getBoolean("Settings.Debug", false);
		updatecheck = getConfig().getBoolean("Settings.Update-Checking", true);
		prefix = getConfig().getString("Settings.Prefix", "&8[&e&lPUUIDs&8]");
		
		if(isSupported()) {
			sounds = true;
		} else {
			sounds = false;
			debug("Sounds have been disabled, this is an older verison of Minecraft.");
		}
	}

	public ArrayList<String> getPlugins() {
		return plugins;
	}
	
	public boolean connect(Plugin pl) {
		String plname = pl.getDescription().getName();
		
		if(!allowconnections) {
			if(!plugins.contains(plname)) {
			getLogger().warning("Plugin '" + plname + "' tried to register with PUUID after the connection window.");
			} else {
				debug(plname + " was reloaded improperly and send another hook request. Ignoring.");
			}
			return false;
		}
		
		if(!plugins.contains(plname)) {
			plugins.add(plname);
			debug("Plugin " + plname  + " has been registered.");
			return true;
		}
		
		status = false;
	    statusreason = "Plugin " + plname + " tried to overwrite another plugin with the exact same name. Please contact " + pl.getDescription().getAuthors().toString() + ". This is not PUUIDs fault.";
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
		
		if(!cache.exists()) {
			return false;
		}
		
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
		
		if(!cache.exists()) {
			return 0;
		}
		
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
	
	public long getPlayTime(String uuid) {
		File cache = new File(this.getDataFolder(), File.separator + "Data");
		
		if(!cache.exists()) {
			return 0;
		}
		
		File f = new File(cache, File.separator + "" + uuid + ".yml");
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		
		if(!f.exists()) {
			return 0;
		}
		
		if(!setcache.contains("Time-Played")) {
			return 0;
		}
		
		return setcache.getLong("Time-Played");
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
	
	public String UUIDtoname(String inputsearch) {
		File folder = new File(this.getDataFolder(), File.separator + "Data");
		File f = new File(folder, File.separator + "" + inputsearch + ".yml");
		
		if(!f.exists()) {
			return "0";
		}
		
		FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
		return setcache.getString("Username");
	}

	
	private void updateFile(Player p, boolean quit, boolean force) {
		if(asyncrunning) {
			debug("Delaying join update of " + p.getName() + ", as a large async task is currently running.");
			return;
		}
		
		
		if(force) {
			File cache = new File(this.getDataFolder(), File.separator + "Data");
			
			if(!cache.exists()) {
				return;
			}
			
			File f = new File(cache, File.separator + "" + p.getUniqueId().toString() + ".yml");
			
			if(!f.exists()) {
				return;
			}
			
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
						
			final long left = System.currentTimeMillis();
			String IPAdd = p.getAddress().getAddress().toString().replace(p.getAddress().getHostString() + "/", "").replace("/", "");
			
			setcache.set("Username", p.getName());
			setcache.set("IP", IPAdd);
			setcache.set("Last-On", left);
			
			if(quit) {
			final long joined = setcache.getLong("Last-On");
			final long current = setcache.getLong("Time-Played");
			setcache.set("Time-Played", ((current)+((left-joined)/1000)));
			}
			
			try {
				setcache.save(f);
			} catch (Exception err) {}
			return;
		}
		
		asyncrunning = true;
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			boolean isnew = false;
			
			File cache = new File(this.getDataFolder(), File.separator + "Data");
			File f = new File(cache, File.separator + "" + p.getUniqueId().toString() + ".yml");
			FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
			
			String name = p.getName();

			if (!f.exists()) {
				try {
					debug(name + " is new, creating a file for them.");
					setcache.save(f);
				} catch (Exception err) {}
				isnew = true;
			}
			
			String UUID = p.getUniqueId().toString();
			
			String IPAdd = p.getAddress().getAddress().toString().replace(p.getAddress().getHostString() + "/", "").replace("/", "");
			
			final long left = System.currentTimeMillis();
			
			if(quit) {
			final long joined = setcache.getLong("Last-On");
			final long current = setcache.getLong("Time-Played");
			setcache.set("Time-Played", ((current)+((left-joined)/1000)));
			}
			
		    setcache.set("UUID", UUID);
			setcache.set("IP", IPAdd);
			setcache.set("Username", name.toString());			
			setcache.set("Last-On", left);
			
			try {
				setcache.save(f);
			} catch (Exception err) {}
			
			asyncrunning = false;
			
			if(!force) {
			if(isnew) {
				Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
					public void run() {
						OnNewFile fse = new OnNewFile(p);
						Bukkit.getPluginManager().callEvent(fse);
					}
				}, 20L);
			}}
		});
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		
		updateFile(p, false, false);
		Cooldowns.justJoined(p.getUniqueId());
		
		if(updatecheck) {
		if(p.hasPermission("puuids.admin") || p.isOp()) {
		if (Updater.outdated) {
			Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable()
			  {
			    public void run()
			     {
			    	try {
			    		Msgs.sendPrefix(p, "&c&lOutdated Plugin! &7Running v" + getDescription().getVersion()
							+ " while the latest is &f&l" + Updater.outdatedversion);
			    		pop(p);
			    	} catch (Exception err) {
			    		sounds = false;
			    		debug("Error on update notif. on join: "); err.printStackTrace();
			    	}
			}}, 50L);
		}}}
		
		if (p.getUniqueId().toString().equals("6191ff85-e092-4e9a-94bd-63df409c2079")) {
			Msgs.send(p, "&7This server is running &fPUUIDs &6v" + getDescription().getVersion()
			+ " &7for " + Bukkit.getBukkitVersion().replace("-SNAPSHOT", ""));
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		Player p = e.getPlayer();
		UUID uuid = p.getUniqueId();
		
		if(!Cooldowns.joined.contains(uuid)) {
			updateFile(p, true, false);
			Cooldowns.justJoined(uuid);
		} else {
			debug(p.getName() + "'s file won't be refreshed, it was updated less than 60s ago. [Quit]");
		}
		
		Cooldowns.confirmall.remove(p);
	}
	
	private void bass(CommandSender sender) {
		if(!(sender instanceof Player)) {
			return;
		}
		
		Player p = (Player)sender;
		
		if(sounds) {
		p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 2.0F, 1.3F);
	}}
	
	private void pop(CommandSender sender) {
		if(!(sender instanceof Player)) {
			return;
		}
		
		Player p = (Player)sender;
		
		if(sounds) {
		p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 2.0F, 2.0F);
	}}
	
	private void thinking(CommandSender sender) {
		if(!(sender instanceof Player)) {
			return;
		}
		
		Player p = (Player)sender;
		
		if(sounds) {
		p.playSound(p.getLocation(), Sound.ENTITY_ITEM_FRAME_PLACE, 2.0F, 2.0F);
	}}
	
	private void noPermission(CommandSender sender) {
		Msgs.sendPrefix(sender, getConfig().getString("Messages.No-Permission"));
		bass(sender);
	}

	public boolean onCommand(CommandSender sender, Command cmd, String cmdLabel, String[] args) {
		if(cmd.getName().equalsIgnoreCase("puuids")) {
			if(!sender.hasPermission("puuids.admin") && !sender.isOp()) {
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
				Msgs.send(sender, "&8&l> &f&l/puuids debug &7Shows you how fast/slow your system is running.");
				Msgs.send(sender, "&8&l> &f&l/puuids reset all &7Resets everything except UUIDs/IPs/Names");
				Msgs.send(sender, "&8&l> &f&l/puuids reset ontime &7Set everyone's total play-time back to 0.");
				Msgs.send(sender, "");
				pop(sender);
				return true;
			}
			
			if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
				if(!Cooldowns.canRunLargeTask) {
					bass(sender);
					Msgs.sendPrefix(sender, "&6&lPlease Wait. &fRunning large tasks this quickly can have a negative impact on your server's preformance.");
					return true;
				}
				
				if(args.length == 1) {
					bass(sender);
					Msgs.sendPrefix(sender, "&c&lOops. &fYou must provide what to reset: &7/puuids reset &f(all/ontime)");
					return true;
				}
				
				if(!args[1].equalsIgnoreCase("ontime") && !args[1].equalsIgnoreCase("all")) {
					bass(sender);
					Msgs.sendPrefix(sender, "&c&lOops. &fYou can't reset &7&l" + args[1] + "&f only: &7&lall&f or &7&lontime");
					return true;
				}
				
				File folder = new File(this.getDataFolder(), File.separator + "Data");
				
				if(!folder.exists()) {
					bass(sender);
					Msgs.sendPrefix(sender, "&c&lData Folder Missing. &fThere is no data to remove here.");
					return true;
				}
				
				if(args[1].equalsIgnoreCase("ontime")) {
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
						setcache.set("Time-Played", 0);
						debug("Reset" + setcache.getString("Username") + "'s Time-Played. (" + f.getName() + ")");
						
						try {
							setcache.save(f);
						} catch(Exception err) {}
						total++;
					}
					
					final String finished = Long.toString(System.currentTimeMillis()-start);
					
					getLogger().info("Reset " + total + " players Time-Played stats on file. (Done in " + finished + "ms)");
					asyncrunning = false;
					
						Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
							public void run() {
								debug("Finished Task. Now updating any needed join updates we missed...");
								runQueuedJoinUpdates();
							}
						}, 20L);
						
						Msgs.send(sender, "");
						Msgs.send(sender, "&e&lPUUIDs");
						Msgs.send(sender, "&8&l> &a&lDone. &fReset everyone's Time-Played back to zero in &7&l" + finished + "ms");
						Msgs.send(sender, "");
						pop(sender);
						
						Cooldowns.endLargeTask();
					});
					return true;
				}
				
				if(args[1].equalsIgnoreCase("all")) {
					if(!(sender instanceof Player)) {
						Msgs.sendPrefix(sender, "&6&lFOR SECURITY REASONS: &fOnly a player with permission & op may run this command.");
						return true;
					}
					
					Player p = (Player)sender;
					
					if(!p.hasPermission("puuids.admin") || !p.isOp()) {
						bass(p);
						Msgs.sendPrefix(p, "&6&lFor Saftey: &fYou must have the &7puuids.admin&f permission & be OP to do this.");
						return true;
					}
					
					if(!Cooldowns.confirmall.containsKey(p)) {
						thinking(p);
						Msgs.sendPrefix(p, "&c&lARE YOU SURE? &fThis will erase ALL player plugin data from your PUUID's data folder. Do &7&l/puuids reset all &fagain to confirm.");
						Cooldowns.confirm(p);
						return true;
					}
					
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
						setcache.set("Plugins", null);
						
						debug("Reset" + setcache.getString("Username") + "'s file back to basics. (" + f.getName() + ")");
						
						try {
							setcache.save(f);
						} catch(Exception err) {}
						total++;
					}
					
					final String finished = Long.toString(System.currentTimeMillis()-start);
					
					getLogger().info("Reset " + total + " players files back to basics. (Done in " + finished + "ms)");
					asyncrunning = false;
					
						Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
							public void run() {
								debug("Finished Task. Now updating any needed join updates we missed...");
								runQueuedJoinUpdates();
							}
						}, 20L);
						
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
				Msgs.send(sender, "&8&l> &c&lError. &fSomething wen't wrong here.");
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
				reloadConfig();
				updateConfig();
				Msgs.send(sender, "");
				Msgs.send(sender, "&e&lPUUIDs");
				Msgs.send(sender, "&8&l> &fConfiguration has been reloaded.");
				Msgs.send(sender, "");
				pop(sender);
				return true;
			}
			
			if(args.length >= 1 && args[0].equalsIgnoreCase("ontime")) {
				// Need to optimize, TOO many sender instanceof Player stuffs.
				
				if(sender instanceof Player) {
					Player p = (Player)sender;
					if(Cooldowns.ontime.contains(p.getUniqueId())) {
						Msgs.sendPrefix(sender, "&c&lSlow Down. &fPlease wait before checking that again.");
						bass(p);
						return true;
					}
				}
				
				if(args.length == 1) {
				if (!(sender instanceof Player)) {
					Msgs.sendPrefix(sender, "&c&lOops. &fYou must specify a player: &7&l/puuids ontime (player)");
					return true;
				}
					Player p = (Player)sender;
					Msgs.sendPrefix(sender, "&6So far, you've played for &f&l" + PUUIDS.getFormatedPlayTime(p.getUniqueId().toString()));
					pop(p);
					Cooldowns.onTime(p.getUniqueId());
					return true;
				}
				
				if(sender instanceof Player) {
					Player p = (Player)sender;
					Cooldowns.onTime(p.getUniqueId());
				}
				
				String uuid = nametoUUID(args[1], false);
				if(uuid == "0") {
					Msgs.sendPrefix(sender, "&c&lHmm. &fThat player has never played before.");
					bass(sender);
					return true;
				}
				
				Msgs.sendPrefix(sender, "&6" + UUIDtoname(uuid) + " has played for &f&l" + PUUIDS.getFormatedPlayTime(uuid));
				pop(sender);
				return true;
			}
			
			if (args.length >= 1 && args[0].equalsIgnoreCase("debug")) {
				reloadConfig();
				Msgs.send(sender, "");
				Msgs.send(sender, "&e&lPUUIDs");
				final int active = getPlugins().size()-1;
				if(active > 0) {
					Msgs.send(sender, "&8&l> &fHooked Plugins: &e&l" + active);
				} else {
					Msgs.send(sender, "&8&l> &fHooked Plugins: &7&l0");	
				}
				if(setTime == 0) {
					Msgs.send(sender, "&8&l> &fSet Information: &7&l--ms");
				} else {
					Msgs.send(sender, "&8&l> &fSet Information: &e&l" + setTime + "ms");
				}
				if(status) {
					if(setTime < 100) {
						Msgs.send(sender, "&8&l> &fDatabase Health: &a&lGREAT");
					} else {
						Msgs.send(sender, "&8&l> &fDatabase Health: &e&lFAIR &7(Slow Set Time)");
					}
				} else {
					Msgs.send(sender, "&8&l> &fDatabase Health: &6&lPOOR");
					if(statusreason == "0") {
						Msgs.send(sender, "  &8&l> &7Couldn't identify a cause for poor health.");
					} else {
						Msgs.send(sender, "  &8&l> &c&lREASON: &7" + statusreason);
					}
				}
				Msgs.send(sender, "");
				pop(sender);
				return true;
			}
		}
		
		return true;
	}
	
}

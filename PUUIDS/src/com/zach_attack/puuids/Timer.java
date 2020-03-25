package com.zach_attack.puuids;

import java.io.File;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.zach_attack.puuids.api.OnNewFile;

public class Timer {
    private static Main plugin = Main.getPlugin(Main.class);

    static long processrate = 10;
    static int sizelimit = 25;
    private static boolean busy = false;

    //                          UUID   PLUGIN   PATH     DATA
    private static ArrayList<Quartet<String, String, String, Object>> rawdata = new ArrayList<>();
    
    //               Player,  Quit?
    static Multimap<Player, Boolean> updateSystem = ArrayListMultimap.create();

    static int getQSize() {
    	return rawdata.size();
    }
    
    static void queueSet(String pl, String uuid, String location, Object value) {
        Bukkit.getScheduler().runTask(plugin, () -> { // Ensures running SYNC to place.
        	Quartet<String, String, String, Object> quart = new Quartet<String, String, String, Object>(uuid, pl.toUpperCase(), location, value);
            rawdata.add(quart);
        });
    }

    final static BukkitTask timer = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
            	final int size = getQSize();
            	final int ssize = updateSystem.size(); 
            	if((ssize == 0 && size == 0) || busy || plugin.asyncrunning) {
            		return;
            	}
            	
            	busy = true;
            	
    	        final File cache = new File(plugin.getDataFolder(), File.separator + "Data");
            	
            	// internal puuids updates
            	if (ssize != 0) {
            	    for (int i = 0; i < ssize; i++) {
            	        final Player p = updateSystem.keys().iterator().next();
            	        final boolean quit = updateSystem.get(p).iterator().next();

            	        final String uuid = p.getUniqueId().toString();
            	        final String name = p.getName();

            	        File f = new File(cache, File.separator + "" + uuid + ".yml");
            	        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
            	        
                        if(!f.exists()) {
                        	try {
                        		setcache.save(f);
                            	Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    // Also call this API event in SYNC after 2s so the file has time to save.
                            		OnNewFile fse = new OnNewFile(p);
                                    Bukkit.getPluginManager().callEvent(fse);
                                }, 40L);
                        	} catch (Exception err) {}
                    	}

            	        final long lefttime = System.currentTimeMillis();

            	        String IPAdd = p.getAddress().getAddress().toString().replace(p.getAddress().getHostString() + "/", "").replace("/", "");

            	        setcache.set("UUID", uuid);
            	        setcache.set("IP", IPAdd);
            	        setcache.set("Username", name);
            	        setcache.set("Last-On", lefttime);

            	        if (quit) {
            	        	final long joined = setcache.getLong("Last-On", 0);
            	            final long current = setcache.getLong("Time-Played", 0);
            	            final long sub = (lefttime-joined);
            	            final long secs = sub/1000;
            	            final long result = (current+secs);
            	            setcache.set("Time-Played", result);
            	            plugin.debug("Joined: " + joined + "   Current: " + current + "  Sub: " + sub + "  Secs: " + secs + "  Result: " + result);
            	        }

            	        try {
            	            setcache.save(f);
            	        } catch (Exception err) {
            	            plugin.debug("Error. Was unable to save " + name + "'s file for PUUIDs System update: ");
            	            err.printStackTrace();
            	        }
            	        
            	        plugin.debug("Updated " + name + "'s player data.");
            	        plugin.setTimes++;
            	        updateSystem.remove(p, quit);
            	    }
            	}
            	// end of system updates --------------
            	
            	
                final long start = System.currentTimeMillis();
                int processed = 0; // Proccessed non-puuids requests
                plugin.setQRequests = getQSize();
            	
                for (int i = 0; i < size; i++) {
                	final long startset = System.currentTimeMillis();
                	if(processed > sizelimit) {
                		plugin.debug("Q reached size limit of " + sizelimit + "... sending other updates to next run.");
                		break;
                	}
                	Quartet<String, String, String, Object> data = rawdata.get(0);
                    final String uuid = data.getUUID();
                    final String plname = data.getPlugin();
                    final String path = data.getPath();
                    final Object value = data.getData();

                    File f = new File(cache, File.separator + "" + uuid + ".yml");
                    FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
                    
                    if(!f.exists()) {
                    	try {
                    		setcache.save(f);
                    	} catch (Exception err) {}
                	}
                    
                    if(path.equals("PUUIDS_SET_AS_ALL_NULL")) {
                    	setcache.set("Plugins." + plname, null);
                    } else {
                    	setcache.set("Plugins." + plname + "." + path, value);
                    }
                    
                    plugin.debug(plname + " set " + value + " for " + uuid + " under: " + path);

                    try {
                    	setcache.save(f);
                    } catch (Exception err) {
                    	busy = false;
                        if (plugin.debug) {
                            plugin.getLogger().warning("Unable to save PUUIDs file for user " + plugin.UUIDtoname(uuid) + " in plugin: " + plname);
                            err.printStackTrace();
                        }
                    }
                    
                    plugin.setTimeMS = System.currentTimeMillis() - startset;
                    plugin.setTimes++;
                    processed++;
                    rawdata.remove(data);
                }
                
                plugin.qTimesMS = System.currentTimeMillis() - start;
                
                if(plugin.qTimesMS > 650) {
                	plugin.getLogger().warning("Saving player data took " + plugin.qTimesMS + "ms. Is the server lagging?");
                }
                processed = 0;
                busy = false;
            }
        }, processrate, processrate);

    static void stopTimer() {
		Timer.timer.cancel();
		final int size = getQSize();
		final int systemsize = updateSystem.size();
		
		if(size == 0 && systemsize == 0) {
			return;
		}
		
		final File cache = new File(plugin.getDataFolder(), File.separator + "Data");
		
		if(systemsize != 0) {
    	    for (int i = 0; i < systemsize; i++) {
    	        final Player p = updateSystem.keys().iterator().next();
    	        final boolean quit = updateSystem.get(p).iterator().next();

    	        final String uuid = p.getUniqueId().toString();
    	        final String name = p.getName();

    	        File f = new File(cache, File.separator + "" + uuid + ".yml");
    	        FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
    	        
                if(!f.exists()) {
                	try {
                		setcache.save(f);
                	} catch (Exception err) {}
            	}

    	        final long lefttime = System.currentTimeMillis();

    	        String IPAdd = p.getAddress().getAddress().toString().replace(p.getAddress().getHostString() + "/", "").replace("/", "");

    	        setcache.set("UUID", uuid);
    	        setcache.set("IP", IPAdd);
    	        setcache.set("Username", name);
    	        setcache.set("Last-On", lefttime);

    	        if (quit) {
    	            final long joined = setcache.getLong("Last-On");
    	            final long current = setcache.getLong("Time-Played");
    	            setcache.set("Time-Played", ((current) + ((lefttime - joined) / 1000)));
    	        }

    	        try {
    	            setcache.save(f);
    	        } catch (Exception err) {
    	            plugin.debug("Error. Was unable to save " + name + "'s file for PUUIDs System update: ");
    	            err.printStackTrace();
    	        }
    	        
    	        plugin.debug("Updated " + name + "'s player data.");
    	        updateSystem.remove(p, quit);
    	    }
		}
		
		if(size != 0) {
			plugin.getLogger().info("Saving " + size + " leftover tasks...");
        
			for (int i = 0; i < size; i++) {
				Quartet<String, String, String, Object> data = rawdata.get(0);
				final String uuid = data.getUUID();
				final String plname = data.getPlugin();
				final String path = data.getPath();
				final Object value = data.getData();
				
				File f = new File(cache, File.separator + "" + uuid + ".yml");
				FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
				
				setcache.set("Plugins." + plname + "." + path, value);
				plugin.debug("Set " + value + " for " + uuid + " under: " + path);
            
				try {
					setcache.save(f);
				} catch (Exception err) {
                    plugin.getLogger().warning("Unable to save PUUIDs file for user " + plugin.UUIDtoname(uuid) + " in plugin: " + plname);
                }
				
				rawdata.remove(data);
			}
		}
    }
}
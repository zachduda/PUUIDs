package com.zach_attack.puuids;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class Timer {
    private static Main plugin = Main.getPlugin(Main.class);

    static long processrate = 10;
    static int sizelimit = 25;
    private static boolean busy = false;

    // K, V                    UUID,   PLUGIN                               UUID,    PLUGIN
    private static Multimap<String, String> updateplayers = ArrayListMultimap.create();
    private static Multimap<String, Object> updateinfo = ArrayListMultimap.create();
    
    //               Player,  Quit?
    static Multimap<Player, Boolean> updateSystem = ArrayListMultimap.create();

    static int getQPlayers() {
    	return updateplayers.size();
    }
    
    static int getQInfo() {
    	return updateinfo.size();
    }
    
    static void queueSet(String pl, String uuid, String location, Object value) {
        Bukkit.getScheduler().runTask(plugin, () -> { // Ensures running SYNC to place.
            updateplayers.put(uuid, pl.toUpperCase());
            updateinfo.put(location, value);
        });
    }

    final static BukkitTask timer = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
            	final int size = updateplayers.size(); 
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
            	        updateSystem.remove(p, quit);

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
            	        plugin.setTimes++;
            	    }
            	}
            	// end of system updates --------------
            	
            	
                final long start = System.currentTimeMillis();
                int processed = 0; // Proccessed non-puuids requests
                plugin.setQRequests = updateinfo.size();
            	
                for (int i = 0; i < size; i++) {
                	final long startset = System.currentTimeMillis();
                	if(processed > sizelimit) {
                		plugin.debug("Q reached size limit of " + sizelimit + "... sending other updates to next run.");
                		break;
                	}
                	
                    final String uuid = updateplayers.keys().iterator().next();
                    final String plname = updateplayers.get(uuid).iterator().next();
                    final String path = updateinfo.keys().iterator().next();
                    final Object value = updateinfo.get(path).iterator().next();
                    updateplayers.remove(uuid, plname);
                    updateinfo.remove(path, value);

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
                    plugin.debug("Set " + value + " for " + uuid + " under: " + path);

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
		
        plugin.getLogger().info("Saving " + updateplayers.size() + " leftover tasks...");
        
        for (int i = 0; i < updateplayers.size(); i++) {
            final String uuid = updateplayers.keys().iterator().next();
            final String plname = updateplayers.get(uuid).iterator().next();
            final String path = updateinfo.keys().iterator().next();
            final Object value = updateinfo.get(uuid).iterator().next();
            updateplayers.remove(uuid, plname);
            updateinfo.remove(path, value);

            File cache = new File(plugin.getDataFolder(), File.separator + "Data");
            File f = new File(cache, File.separator + "" + uuid + ".yml");
            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);
            setcache.set("Plugins." + plname + "." + path, value);
            updateplayers.remove(uuid, plname);
            updateinfo.remove(path, value);
            plugin.debug("Set " + value + " for " + uuid + " under: " + path);
            
            try {
                setcache.save(f);
            } catch (Exception err) {
                    plugin.getLogger().warning("Unable to save PUUIDs file for user " + plugin.UUIDtoname(uuid) + " in plugin: " + plname);
                }
        }
        
        updateplayers.clear();
        updateinfo.clear();
    }
}
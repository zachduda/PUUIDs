package com.zach_attack.puuids;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class Cooldowns {
	private static Main plugin = Main.getPlugin(Main.class);
	
	static ArrayList<UUID> joined = new ArrayList<UUID>();
	static ArrayList<UUID> ontime = new ArrayList<UUID>();
	
	static HashMap<Player, String> confirmall = new HashMap<Player, String>();
	
	protected static boolean canRunLargeTask = true;

	static void clearAll(Player p) {
		joined.clear();
		ontime.clear();
		confirmall.remove(p);
		// Doesn't include queued join updates or plugin lists. 
	}
	
	static void justJoined(UUID p) {
		if(joined.contains(p)) {
			return;
		}
		
		joined.add(p);
		
		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				joined.remove(p);
			}
		}, 20 * 30);
	}
	
	static void onTime(UUID p) {
		if(ontime.contains(p)) {
			return;
		}
		
		if(!plugin.getConfig().getBoolean("Settings.Cooldowns.Enabled")) {
			return;
		}
		
		ontime.add(p);
		
		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				ontime.remove(p);
			}
		}, 20 * plugin.getConfig().getInt("Settings.Cooldowns.On-Time.Seconds"));
	}
	
	static void startLargeTask() {
		canRunLargeTask = false;
	}
	
	static void endLargeTask() {
		if(canRunLargeTask) {
			return;
		}
		
		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				canRunLargeTask = true;
			}
		}, 20 * 45);
	}
	
	static void confirm(Player p) {
		confirmall.put(p, p.getName());
		
		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				confirmall.remove(p, p.getName());
			}
		}, 20 * 8);
	}
}

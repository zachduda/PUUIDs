package com.zach_attack.puuids;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Bukkit;

public class Cooldowns {
	private static Main plugin = Main.getPlugin(Main.class);
	
	static ArrayList<UUID> joined = new ArrayList<UUID>();
	static ArrayList<UUID> ontime = new ArrayList<UUID>();
	
	protected static boolean canRunLargeTask = true;

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
}

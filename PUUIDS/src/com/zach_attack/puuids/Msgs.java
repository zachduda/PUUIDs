package com.zach_attack.puuids;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class Msgs {
	private static Main plugin = Main.getPlugin(Main.class);

	static void send(CommandSender sender, String msg) {
	    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));	
	}
	
	public static void sendPrefix(CommandSender sender, String msg) {
	    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.prefix + " &r" + msg));	
	}
	
}

package com.zachduda.puuids;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class Msgs {

    static String prefix = "&8[&e&lPUUIDs&8]";

    static void send(CommandSender sender, String msg) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    public static void sendPrefix(CommandSender sender, String msg) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + " &r" + msg));
    }

}

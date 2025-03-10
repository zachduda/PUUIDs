package com.zachduda.puuids;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Msgs {

    static String prefix = "&8[&e&lPUUIDs&8]";

    static String color(String msg) {
        msg = msg.replaceAll("&#", "#");
        Pattern pattern = Pattern.compile("(&#|#|&)[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(msg);
        while (matcher.find()) {
            String hexCode = msg.substring(matcher.start(), matcher.end());
            String replaceAmp = hexCode.replaceAll("&#", "x");
            String replaceSharp = replaceAmp.replace('#', 'x');

            char[] ch = replaceSharp.toCharArray();
            StringBuilder builder = new StringBuilder();
            for (char c : ch) {
                builder.append("&").append(c);
            }

            msg = msg.replace(hexCode, builder.toString());
            matcher = pattern.matcher(msg);
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    static void send(CommandSender sender, String msg) {
        sender.sendMessage(color( msg));
    }

    public static void sendPrefix(CommandSender sender, String msg) {
        sender.sendMessage(color(prefix + " &r" + msg));
    }

}

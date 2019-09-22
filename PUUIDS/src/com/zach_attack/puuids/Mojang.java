package com.zach_attack.puuids;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.libs.org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

public class Mojang {
	private static Main plugin = Main.getPlugin(Main.class);
	private static final Pattern FORMATED = Pattern.compile("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})");
	
    static String getUUIDfromName(String name) {
    	if(plugin.debug) {
    	Bukkit.getLogger().info("Had to fetch " + name + "'s UUID from Mojang.");
    	}
    	
        String url = "https://api.mojang.com/users/profiles/minecraft/"+name;
        try {
            String UUIDJson = IOUtils.toString(new URL(url), "UTF-8");           
            if(UUIDJson.isEmpty()) return "0";                       
            JSONObject UUIDObject = (JSONObject) JSONValue.parseWithException(UUIDJson);
            return (FORMATED.matcher(UUIDObject.get("id").toString().replace("-", "")).replaceAll("$1-$2-$3-$4-$5"));
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return "0";
    }
}

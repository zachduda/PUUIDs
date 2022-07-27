package com.zach_attack.puuids;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

// PUUIDs Async Check --> Based off of Benz56's update checker <3
// https://github.com/Benz56/Async-Update-Checker/blob/master/UpdateChecker.java

public class Updater {

    private static final int ID = 71496;
    private static final long CHECK_INTERVAL = 1_728_000; //In ticks.
    static String outdatedversion = "???";
    static boolean outdated = false;
    private final JavaPlugin javaPlugin;
    private final String localPluginVersion;

    public Updater(final JavaPlugin javaPlugin) {
        this.javaPlugin = javaPlugin;
        this.localPluginVersion = javaPlugin.getDescription().getVersion();
    }

    public void checkForUpdate() {
        try {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.getScheduler().runTaskAsynchronously(javaPlugin, () -> {
                        try {
                            URL url = new URL("https://api.spigotmc.org/simple/0.1/index.php?action=getResource&id=" + ID);
                            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
                            String str = br.readLine();
                            outdatedversion = (String) ((JSONObject) new JSONParser().parse(str)).get("current_version");
                        } catch (final IOException | ParseException e) {
                            Bukkit.getConsoleSender().sendMessage("[PUUIDS] &cUnable to check for updates. Is your server online?");
                            cancel();
                            return;
                        }

                        if (("v" + localPluginVersion).equalsIgnoreCase(outdatedversion)) {
                            return;
                        }

                        if (outdatedversion.equalsIgnoreCase("v2.4.1")) {
                            return;
                        }

                        outdated = true;
                        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&r[PUUIDS] &e&lUpdate Available: &rYou're using &7v" + localPluginVersion + "&r, & the latest is &a" + outdatedversion));
                        cancel(); //Cancel the runnable as an update has been found.
                    });
                }
            }.runTaskTimer(javaPlugin, 0, CHECK_INTERVAL);
        } catch (Exception err) {
            javaPlugin.getLogger().warning("Error. There was a problem checking for updates.");
        }
    }
}
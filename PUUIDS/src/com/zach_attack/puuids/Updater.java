package com.zach_attack.puuids;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

// PUUIDs Async Check --> Benz56's update checker <3
// https://github.com/Benz56/Async-Update-Checker/blob/master/UpdateChecker.java

public class Updater {
	
    private final JavaPlugin javaPlugin;
    private final String localPluginVersion;
    
    static String outdatedversion = "???";
	static boolean outdated = false;

    private static final int ID = 71496;
    private static final long CHECK_INTERVAL = 1_728_000; //In ticks.
    
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
                        final HttpsURLConnection connection = (HttpsURLConnection) new URL("https://api.spigotmc.org/legacy/update.php?resource=" + ID).openConnection();
                        connection.setRequestMethod("GET");
                        outdatedversion = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
                    } catch (final IOException e) {
                        Bukkit.getConsoleSender().sendMessage("[PUUIDS] &cUnable to check for updates. Is your server online?");
                        cancel();
                        return;
                    }

                    if (("v" + localPluginVersion).equalsIgnoreCase(outdatedversion)) {
                    	Bukkit.getConsoleSender().sendMessage("[PUUIDS] You are running the latest version. Hooray!");
                    	return;
                    }
                    
                    outdated = true;
                    Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&r[PUUIDS] &e&lUpdate Available: &rYou're running &7v" + localPluginVersion + "&r, while the latest is &a" + outdatedversion));
                    cancel(); //Cancel the runnable as an update has been found.
                });
            }
        }.runTaskTimer(javaPlugin, 0, CHECK_INTERVAL);
    	}catch(Exception err) {
    		javaPlugin.getLogger().warning("Error. There was a problem checking for updates.");
    	}
    }
}
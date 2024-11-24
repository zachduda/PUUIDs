package com.zachduda.puuids;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

public class Updater {

    private final JavaPlugin javaPlugin;
    private final String localPluginVersion;

    static String postedver = "???";
    static boolean outdated = false;

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
                            checkForUpdateAsync();
                        } catch (final IOException | ParseException e) {
                            e.printStackTrace();
                            Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.RED + "[PUUIDs] Unable to check for updates. Is your server online?");
                            cancel();
                            return;
                        }
                        if(outdated) {
                            Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&r[PUUIDs] &e&l&nUpdate Available&r&e&l!&r You're running &7v" + localPluginVersion + "&r, while the latest is &av" + postedver));
                            cancel(); //Cancel the runnable as an update has been found.
                        }
                    });
                }
            }.runTaskTimer(javaPlugin, 0, CHECK_INTERVAL);
        }catch(Exception err) {
            javaPlugin.getLogger().warning("Error. There was a problem checking for updates.");
        }
    }

    private void checkForUpdateAsync() throws IOException, ParseException {
        URL url = new URL("https://api.github.com/repos/zachduda/PUUIDs/releases");
        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
        String str = br.readLine();
        JSONArray rawja = (JSONArray) new JSONParser().parse(str);
        ArrayList<Double> versions = new ArrayList<>();
        Iterator iterator = rawja.iterator();
        while (iterator.hasNext()) {
            JSONObject jsonObject = (JSONObject) iterator.next();
            final String vs = ((String)jsonObject.get("tag_name")).replace("v", "");
            final Boolean prerelease = ((Boolean)jsonObject.get("prerelease"));
            if(!prerelease) {
                if(!localPluginVersion.equalsIgnoreCase(vs)){
                    outdated = true;
                    postedver = vs;
                }
                break;
            }
        }
    }

    public static boolean isOutdated() {
        return outdated;
    }

    public static String getPostedVersion() {
        return postedver;
    }
}

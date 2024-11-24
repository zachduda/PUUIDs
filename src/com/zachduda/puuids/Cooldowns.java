package com.zachduda.puuids;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Cooldowns {
    private boolean canRunLargeTask = true;
    private ArrayList<UUID> joined = new ArrayList<>();
    private ArrayList<UUID> ontime = new ArrayList<>();
    private HashMap<Player, String> confirmall = new HashMap<>();
    private Main plugin;

    public Cooldowns(Main plugin) {
        this.plugin = plugin;
    }

    public void clearAll(Player p) {
        joined.clear();
        ontime.clear();
        confirmall.remove(p);
        // Doesn't include queued join updates or plugin lists.
    }

    public void justJoined(UUID p) {
        if (joined.contains(p)) {
            return;
        }

        joined.add(p);

        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> joined.remove(p), 20 * 30);
    }

    public void onTime(UUID p) {
        if (ontime.contains(p)) {
            return;
        }

        if (!plugin.getConfig().getBoolean("Settings.Cooldowns.Enabled")) {
            return;
        }

        ontime.add(p);

        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> ontime.remove(p), 20 * plugin.getConfig().getInt("Settings.Cooldowns.On-Time.Seconds"));
    }

    public void startLargeTask() {
        canRunLargeTask = false;
    }

    public void endLargeTask() {
        if (canRunLargeTask) {
            return;
        }

        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> canRunLargeTask = true, 20 * 45);
    }

    public void confirm(Player p, String key) {
        confirmall.put(p, key);

        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> confirmall.remove(p, key), 20 * 10);
    }

    public boolean isCanRunLargeTask() {
        return canRunLargeTask;
    }

    public ArrayList<UUID> getJoined() {
        return joined;
    }

    public ArrayList<UUID> getOntime() {
        return ontime;
    }

    public HashMap<Player, String> getConfirmall() {
        return confirmall;
    }
}

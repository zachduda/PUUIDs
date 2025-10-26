package com.zachduda.puuids;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.zachduda.puuids.api.OnNewFile;
import com.zachduda.puuids.api.TimerSaved;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import space.arim.morepaperlib.scheduling.ScheduledTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

public class Timer {
    static long processrate = 10;
    static int sizelimit = 25;
    //               Player,  Quit?
    static Multimap<Player, Boolean> updateSystem = Multimaps.newMultimap(
    new ConcurrentHashMap<>(), 
    ConcurrentLinkedQueue::new
);
    private static final Main plugin = Main.getPlugin(Main.class);
    private static boolean busy = false;
    private static int taskid = 1;
    //                                 UUID   PLUGIN   PATH     DATA    ID
    private static final ArrayList<Quartet<String, String, String, Object, Integer>> rawdata = new ArrayList<>();
    final static ScheduledTask timer = plugin.mpl.scheduling().asyncScheduler().runAtFixedRate(() -> {
        final int size = getQSize();
        final int ssize = updateSystem.size();
        if ((ssize == 0 && size == 0) || busy || plugin.asyncrunning) {
            return;
        }

        busy = true;

        final File cache = new File(plugin.getDataFolder(), File.separator + "Data");

        // internal puuids updates
        while (!updateSystem.isEmpty()) {
            final Player p = updateSystem.keys().iterator().next();
            final boolean quit = Boolean.TRUE.equals(updateSystem.get(p).iterator().next());

            assert p != null;
            final String uuid = p.getUniqueId().toString();
            final String name = p.getName();

            boolean isnewfile = false;

            File f = new File(cache, File.separator + uuid + ".yml");
            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

            if (!f.exists()) {
                try {
                    setcache.save(f);
                    isnewfile = true;
                } catch (Exception err) {
                }
            }

            final long now = System.currentTimeMillis();
            String IPAdd = Objects.requireNonNull(p.getAddress()).getAddress().toString().replace(p.getAddress().getHostString() + "/", "").replace("/", "");

            setcache.set("UUID", uuid);
            setcache.set("IP", IPAdd);
            setcache.set("Username", name);

            if (quit) {
                final long joined = setcache.getLong("Last-On");
                final long current = setcache.getLong("Time-Played");
                final long sub = (now - joined);
                final long secs = sub / 1000;
                final long result = (current + secs);
                setcache.set("Time-Played", result);
                plugin.debug("Joined: " + joined + "   Current: " + current + "  Sub: " + sub + "  Secs: " + secs + "  Result: " + result);
            }

            setcache.set("Last-On", now);

            try {
                setcache.save(f);
            } catch (Exception err) {
                plugin.debug("Error. Was unable to save " + name + "'s file for puuids System update: ");
                err.printStackTrace();
            }

            plugin.debug("Updated " + name + "'s player data.");
            plugin.setTimes++;
            updateSystem.remove(p, quit);

            if (isnewfile) {
                OnNewFile fse = new OnNewFile(p);
                Bukkit.getPluginManager().callEvent(fse);
            }
        }
        // end of system updates --------------


        final long start = System.currentTimeMillis();
        int processed = 0; // Processed non-puuids requests
        plugin.setQRequests = size;

        while (!rawdata.isEmpty()) {
            if (processed > sizelimit) {
                plugin.debug("Q reached size limit of " + sizelimit + "... sending other updates to next run.");
                break;
            }
            final long startset = System.currentTimeMillis();
            Quartet<String, String, String, Object, Integer> data = rawdata.get(0);
            final String uuid = data.getUUID();
            final String plname = data.getPlugin();
            final String path = data.getPath();
            final Object value = data.getData();
            final int taskid = data.getId();

            File f = new File(cache, File.separator + uuid + ".yml");
            FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

            if (!f.exists()) {
                try {
                    setcache.save(f);
                } catch (Exception ignored) {
                    // First creation fail can happen. meh.
                }
            }

            if (path.equals("PUUIDS_SET_AS_ALL_NULL")) {
                setcache.set("Plugins." + plname, null);
            } else {
                setcache.set("Plugins." + plname + "." + path, value);
            }

            plugin.debug("(" + taskid + ") " + plname + " set " + value + " for " + uuid + " under: " + path);

            try {
                setcache.save(f);
                TimerSaved tse = new TimerSaved(plname, uuid, taskid);
                Bukkit.getServer().getPluginManager().callEvent(tse);
            } catch (Exception err) {
                busy = false;
                if (plugin.debug) {
                    plugin.getLogger().warning("Unable to save puuids file for user " + plugin.UUIDtoname(uuid) + " in plugin: " + plname);
                    err.printStackTrace();
                }
            }

            plugin.setTimeMS = (plugin.setTimeMS + System.currentTimeMillis() - startset) / 2;
            plugin.setTimes++;
            processed++;
            rawdata.remove(data);
        }

        plugin.qTimesMS = System.currentTimeMillis() - start;

        if (plugin.qTimesMS > 650) {
            plugin.getLogger().warning("Saving player data took " + plugin.qTimesMS + "ms. Try reducding max your task limit!");
        }
        busy = false;
    }, Duration.ofMillis(processrate), Duration.ofMillis(processrate));

    static int getQSize() {
        return rawdata.size();
    }

    static int queueSet(String pl, String uuid, String location, Object value) {
        final int thisid = taskid;
        taskid += 1;
        plugin.mpl.scheduling().globalRegionalScheduler().run(() -> { // Ensures running SYNC to place.
            Quartet<String, String, String, Object, Integer> quart = new Quartet<String, String, String, Object, Integer>(uuid, pl.toUpperCase(), location, value, thisid);
            rawdata.add(quart);
        });
        return thisid;
    }

    static void stopTimer() {
        Timer.timer.cancel();
        final int size = getQSize();
        final int systemsize = updateSystem.size();

        if (size == 0 && systemsize == 0) {
            return;
        }

        final File cache = new File(plugin.getDataFolder(), File.separator + "Data");

        if (systemsize > 0) {
            for (int i = 0; i < systemsize; i++) {
                final Player p = updateSystem.keys().iterator().next();
                final boolean quit = Boolean.TRUE.equals(updateSystem.get(p).iterator().next());

                assert p != null;
                final String uuid = p.getUniqueId().toString();
                final String name = p.getName();

                File f = new File(cache, File.separator + uuid + ".yml");
                FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

                if (!f.exists()) {
                    try {
                        setcache.save(f);
                    } catch (Exception ignored) {
                        // Forced mostly so we don't care.
                    }
                }

                final long lefttime = System.currentTimeMillis();

                String IPAdd = Objects.requireNonNull(p.getAddress()).getAddress().toString().replace(p.getAddress().getHostString() + "/", "").replace("/", "");

                setcache.set("UUID", uuid);
                setcache.set("IP", IPAdd);
                setcache.set("Username", name);
                setcache.set("Last-On", lefttime);

                if (quit) {
                    final long joined = setcache.getLong("Last-On");
                    final long current = setcache.getLong("Time-Played");
                    setcache.set("Time-Played", ((current) + ((lefttime - joined) / 1000)));
                }

                try {
                    setcache.save(f);
                } catch (Exception err) {
                    plugin.debug("Error. Was unable to save " + name + "'s file for puuids System update: ");
                    err.printStackTrace();
                }

                plugin.debug("Updated " + name + "'s player data.");
                updateSystem.remove(p, quit);
            }
        }

        if (size > 0) {
            plugin.getLogger().info("Saving " + size + " leftover tasks...");

            for (int i = 0; i < size; i++) {
                Quartet<String, String, String, Object, Integer> data = rawdata.get(0);
                final String uuid = data.getUUID();
                final String plname = data.getPlugin();
                final String path = data.getPath();
                final Object value = data.getData();

                File f = new File(cache, File.separator + uuid + ".yml");
                FileConfiguration setcache = YamlConfiguration.loadConfiguration(f);

                setcache.set("Plugins." + plname + "." + path, value);
                plugin.debug("(# " + data.getId() + ") Set " + value + " for " + uuid + " under: " + path);

                try {
                    setcache.save(f);
                } catch (Exception err) {
                    plugin.getLogger().warning("Unable to save puuids file for user " + plugin.UUIDtoname(uuid) + " in plugin: " + plname);
                }

                rawdata.remove(data);
            }
        }
    }
}
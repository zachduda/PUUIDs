package com.zachduda.puuids;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;

import java.util.Queue;
import java.util.UUID;

/**
 * Everything that touches Essentials' own classes, kept out of Main so a server without
 * Essentials never has to load them.
 */
final class EssentialsHook {

    private EssentialsHook() {
    }

    /**
     * Resets the Essentials data of each queued player, as File-Cleanup.Clean-Essentials asks.
     * Banned players are skipped: their data file is what keeps them banned.
     *
     * @return how many players were reset.
     */
    static int reset(Queue<String> uuids, Main plugin) {
        final Essentials ess = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null) {
            uuids.clear();
            return 0;
        }

        int cleaned = 0;
        String id;
        while ((id = uuids.poll()) != null) {
            try {
                final User user = ess.getUser(UUID.fromString(id));
                if (user == null || user.getBase().isBanned()) {
                    continue;
                }
                user.reset();
                cleaned++;
            } catch (Exception err) {
                plugin.debug("Unable to clean up Essentials data for " + id + ": " + err);
            }
        }
        return cleaned;
    }
}

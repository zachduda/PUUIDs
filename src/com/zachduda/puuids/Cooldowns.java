package com.zachduda.puuids;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
  Cooldowns are held as expiry timestamps rather than as scheduled removal tasks. That keeps
  them correct when they're read from the async queue threads, and means no scheduler work at
  all (the Bukkit scheduler this used to call isn't available on Folia anyway).
 */
public class Cooldowns {
    private static final long JOIN_COOLDOWN_MS = 30_000L;
    private static final long LARGE_TASK_COOLDOWN_MS = 45_000L;
    private static final long CONFIRM_MS = 10_000L;

    private static final Map<UUID, Long> joined = new ConcurrentHashMap<>();
    @SuppressWarnings("SpellCheckingInspection")
    private static final Map<UUID, Long> ontime = new ConcurrentHashMap<>();
    @SuppressWarnings("SpellCheckingInspection")
    private static final Map<UUID, String> confirmall = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> confirmexpiry = new ConcurrentHashMap<>();

    private static volatile long largeTaskReadyAt = 0L;

    private static final Main plugin = Main.getPlugin(Main.class);

    private static boolean active(Map<UUID, Long> cooldowns, UUID p) {
        final Long until = cooldowns.get(p);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            cooldowns.remove(p, until);
            return false;
        }
        return true;
    }

    /** Forgets one player. Only their own entries, never everybody else's. */
    static void clearAll(UUID p) {
        joined.remove(p);
        ontime.remove(p);
        clearConfirm(p);
    }

    static boolean recentlyJoined(UUID p) {
        return active(joined, p);
    }

    static void justJoined(UUID p) {
        joined.put(p, System.currentTimeMillis() + JOIN_COOLDOWN_MS);
    }

    @SuppressWarnings("SpellCheckingInspection")
    static boolean onTimeCooling(UUID p) {
        return active(ontime, p);
    }

    @SuppressWarnings("SpellCheckingInspection")
    static void onTime(UUID p) {
        if (!plugin.getConfig().getBoolean("Settings.Cooldowns.On-Time.Enabled", true)) {
            return;
        }

        final long seconds = Math.max(0, plugin.getConfig().getInt("Settings.Cooldowns.On-Time.Seconds", 5));
        if (seconds == 0) {
            return;
        }

        ontime.put(p, System.currentTimeMillis() + (seconds * 1000L));
    }

    static boolean canRunLargeTask() {
        return System.currentTimeMillis() >= largeTaskReadyAt;
    }

    static void startLargeTask() {
        largeTaskReadyAt = Long.MAX_VALUE;
    }

    static void endLargeTask() {
        largeTaskReadyAt = System.currentTimeMillis() + LARGE_TASK_COOLDOWN_MS;
    }

    /** The pending confirmation key for a player, or null once it has expired. */
    static String confirmKey(UUID p) {
        if (!active(confirmexpiry, p)) {
            confirmall.remove(p);
            return null;
        }
        return confirmall.get(p);
    }

    static void confirm(UUID p, String key) {
        confirmall.put(p, key);
        confirmexpiry.put(p, System.currentTimeMillis() + CONFIRM_MS);
    }

    static void clearConfirm(UUID p) {
        confirmall.remove(p);
        confirmexpiry.remove(p);
    }
}

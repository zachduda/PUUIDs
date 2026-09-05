package com.zachduda.puuids;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import space.arim.morepaperlib.MorePaperLib;
import space.arim.morepaperlib.scheduling.ScheduledTask;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Updater {

    private static final String RELEASES_API = "https://api.github.com/repos/zachduda/PUUIDs/releases";

    private static final long FIRST_CHECK_DELAY = 100; // In ticks. (~5s after startup)
    private static final long CHECK_INTERVAL = 1_728_000; // In ticks. (~24h)

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    // Written from the async check thread, read from the main thread.
    static volatile String posted_version = "???";
    static volatile boolean outdated = false;

    private final JavaPlugin javaPlugin;
    private final String localPluginVersion;
    private final MorePaperLib morePaperLib;

    private volatile boolean finished = false;

    public Updater(final JavaPlugin javaPlugin, final MorePaperLib morePaperLib) {
        this.javaPlugin = javaPlugin;
        this.localPluginVersion = javaPlugin.getDescription().getVersion();
        this.morePaperLib = morePaperLib;
    }

    protected volatile ScheduledTask updatetimer;

    public void checkForUpdate() {
        try {
            morePaperLib.scheduling().globalRegionalScheduler().runAtFixedRate(task -> {
                updatetimer = task;
                if (finished) {
                    task.cancel();
                    return;
                }
                // Run network I/O off the main thread
                morePaperLib.scheduling().asyncScheduler().run(() -> runCheck(task));
            }, FIRST_CHECK_DELAY, CHECK_INTERVAL);
        } catch (Exception err) {
            javaPlugin.getLogger().warning("Update check failure: " + err);
        }
    }

    private void runCheck(final ScheduledTask task) {
        if (finished) {
            return;
        }

        final String latest;
        try {
            latest = fetchLatestStableVersion();
        } catch (final IOException | ParseException err) {
            // A blip shouldn't kill the checker for the rest of the runtime -- just try again later.
            javaPlugin.getLogger().warning("Unable to check for updates: " + err.getMessage());
            return;
        }

        if (latest == null || compareVersions(latest, localPluginVersion) <= 0) {
            return;
        }

        posted_version = latest;
        outdated = true;
        finished = true;

        morePaperLib.scheduling().globalRegionalScheduler().run(() -> {
            Bukkit.getServer().getConsoleSender().sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            "&r[PUUIDs] &e&l&nUpdate Available&r&e&l!&r You're running &7v" + localPluginVersion +
                                    "&r, while the latest is &av" + latest)
            );
            task.cancel();
        });
    }

    /**
     * @return the newest non-prerelease version on GitHub, or null if there isn't one.
     */
    private String fetchLatestStableVersion() throws IOException, ParseException {
        final URL url = URI.create(RELEASES_API).toURL();
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            // Without timeouts a stalled connection parks this thread forever.
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "PUUIDs/" + localPluginVersion);

            final int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub responded with HTTP " + code);
            }

            final Object parsed;
            try (Reader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                parsed = new JSONParser().parse(reader);
            }
            if (!(parsed instanceof JSONArray releases)) {
                throw new IOException("Unexpected response from GitHub.");
            }

            // Releases come back newest first, so the first stable one is the latest.
            for (final Object element : releases) {
                if (!(element instanceof JSONObject release)) {
                    continue;
                }
                if (Boolean.TRUE.equals(release.get("prerelease")) || Boolean.TRUE.equals(release.get("draft"))) {
                    continue;
                }
                if (!(release.get("tag_name") instanceof String tag)) {
                    continue;
                }
                final String version = tag.trim().replaceFirst("^[vV]", "");
                if (!version.isEmpty()) {
                    return version;
                }
            }
            return null;
        } finally {
            conn.disconnect();
        }
    }

    private static int compareVersions(final String left, final String right) {
        final int[] a = toNumericParts(left);
        final int[] b = toNumericParts(right);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            final int x = i < a.length ? a[i] : 0;
            final int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static int[] toNumericParts(final String version) {
        final String[] raw = version.split("[-+]", 2)[0].split("\\.");
        final int[] parts = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            try {
                parts[i] = Integer.parseInt(raw[i].trim());
            } catch (final NumberFormatException ignored) {
                parts[i] = 0;
            }
        }
        return parts;
    }

    public static boolean isOutdated() {
        return outdated;
    }

    public static String getPostedVersion() {
        return posted_version;
    }
}
# PUUIDs Documentation

PUUIDs is a Spigot, Paper and Folia plugin that gives other plugins a shared per-player data store. Your plugin calls `PUUIDS.set(...)` to save a value and `PUUIDS.getX(...)` to read it back. PUUIDs handles the files: one YAML file per player, written off the main thread through a save queue, with an optional MySQL mirror.

These pages are written for plugin developers who know Java and the basics of the Bukkit API. If you run a server and just want to configure PUUIDs, go to [Server Owners](Server-Owners.md).

## Contents

**Start here**

- [Getting Started](Getting-Started.md): add the dependency, declare it in `plugin.yml`, and connect your plugin.
- [Storing Data](Storing-Data.md): `set`, paths, supported types, lists, locations and defaults.
- [Reading Data](Reading-Data.md): the typed getters, what they return when nothing is saved, and bulk lookups.
- [Player Info](Player-Info.md): the UUID, username, IP, last seen time and play time PUUIDs records for every player.

**Going deeper**

- [How Saving Works](How-Saving-Works.md): the save queue, its timing, and the one rule you need to design around.
- [Events](Events.md): every event PUUIDs fires, and which thread it arrives on.
- [Patterns and Examples](Patterns.md): caching online players, first-join setup, leaderboards, and a complete example plugin.
- [MySQL](MySQL.md): the optional database mirror, its schema, and multi-server setups.

**Reference**

- [API Reference](API-Reference.md): every public method on one page.
- [Server Owners](Server-Owners.md): installation, `config.yml`, commands and permissions.
- [Troubleshooting](Troubleshooting.md): common errors and how to fix them.

## At a glance

```java
import com.zachduda.puuids.api.PUUIDS;
import com.zachduda.puuids.api.PUUIDS.APIVersion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Register with PUUIDs. This has to happen in onEnable.
        PUUIDS.connect(this, APIVersion.V4);
    }

    public void setChatSounds(Player player, boolean enabled) {
        // Queued, then written to the player's file off the main thread.
        PUUIDS.set(this, player.getUniqueId().toString(), "Settings.Chat-Sounds", enabled);
    }

    public boolean hasChatSounds(Player player) {
        return PUUIDS.getBoolean(this, player.getUniqueId().toString(), "Settings.Chat-Sounds");
    }
}
```

After `setChatSounds` runs, that player's file (`plugins/PUUIDs/Data/<uuid>.yml`) looks something like this:

```yaml
UUID: 6191ff85-e092-4e9a-94bd-63df409c2079
Username: zach_attack
IP: 203.0.113.7
Last-On: 1758990000000
Time-Played: 86400
Plugins:
  MYPLUGIN:
    Settings:
      Chat-Sounds: true
```

The top five keys belong to PUUIDs and are kept up to date for every player automatically. Everything your plugin saves goes under `Plugins.<YOUR PLUGIN NAME>`, so plugins can never overwrite each other's data.

> [!IMPORTANT]
> Writes are queued and land on disk a moment later, but reads go straight to the file. A read immediately after a write can still return the old value. Read [How Saving Works](How-Saving-Works.md) before you build anything that increments counters or edits lists.

## Getting help

- Join the developer Discord at <https://zachduda.com/discord> and ask away.
- Found a bug? Open an issue using the bug report template on GitHub.
- Browse the source of the API itself in [`PUUIDS.java`](../src/com/zachduda/puuids/api/PUUIDS.java).

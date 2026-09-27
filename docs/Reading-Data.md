# Reading Data

Reads mirror the `getConfig()` getters, with your plugin and the player's UUID added in front of the path.

```java
String uuid = player.getUniqueId().toString();

String title   = PUUIDS.getString(this, uuid, "Title");
boolean sounds = PUUIDS.getBoolean(this, uuid, "Settings.Chat-Sounds");
int kills      = PUUIDS.getInt(this, uuid, "Stats.Kills");
```

## How reads work

Every getter opens that player's file and parses it on the thread you call it from. There's no cache in between. Two things follow from that:

1. **Reads don't see queued writes.** A value you passed to `set` a moment ago may not be in the file yet, so the getter returns the old value. This is the most important thing to understand about PUUIDs, and [How Saving Works](How-Saving-Works.md#the-read-after-write-rule) explains how to design around it.
2. **Every call is a small disk read.** A few reads on the main thread (in a command, or when a player joins) are fine. Reads inside loops, or reads for many players, belong on another thread, or in memory. Reading from another thread is safe: PUUIDs replaces files atomically, so a reader never sees a half-written file.

If you need several values from the same player at once, `get` on a section returns them together in a single read (see [below](#get)).

## Typed getters

| Method | Returns | When nothing is saved |
| --- | --- | --- |
| `getString(plugin, uuid, path)` | `String` | `null` |
| `getBoolean(plugin, uuid, path)` | `boolean` | `false` |
| `getInt(plugin, uuid, path)` | `int` | `0` |
| `getLong(plugin, uuid, path)` | `long` | `0` |
| `getDouble(plugin, uuid, path)` | `double` | `0.0` |
| `getStringList(plugin, uuid, path)` | `List<String>` | an empty list |
| `getIntList(plugin, uuid, path)` | `List<Integer>` | an empty list |
| `getNCList(plugin, uuid, path)` | `List<?>` exactly as stored ("not cast") | `null` |
| `getItemStack(plugin, uuid, path)` | `ItemStack` | `null` |
| `getLocation(plugin, uuid, path)` | `Location` | throws, so [check `contains` first](Storing-Data.md#locations) |

The typed getters follow `ConfigurationSection` rules, so numbers convert between types: `getLong` on a value saved as an `int` works fine. The lists they return are fresh copies that you can modify.

The typed getters work even if your plugin hasn't connected. They still only read your own plugin's section.

## `contains`

```java
boolean contains(Plugin plugin, String uuid, String path)
```

Returns `true` if your plugin has a value or section at `path` for this player. It requires your plugin to be connected, and returns `false` if it isn't.

## `get`

```java
Object get(Plugin plugin, String uuid, String path)
```

Returns the raw value exactly as loaded from the file: a `String`, `Integer`, `Boolean`, `List`, `ItemStack` or `ConfigurationSection`. It requires your plugin to be connected.

- If nothing is saved at `path`, it returns `null`.
- If your plugin isn't connected, or an argument is `null`, it returns `Boolean.FALSE`, **not** `null`. Check the type of the result rather than comparing it with `null`.

Calling `get` on a section is a handy way to read several values with a single file read:

```java
if (PUUIDS.get(this, uuid, "Stats") instanceof ConfigurationSection stats) {
    int kills  = stats.getInt("Kills");
    int deaths = stats.getInt("Deaths");
}
```

## Telling missing values apart

`getInt` returns `0` both when the value is `0` and when nothing is saved. When the difference matters, for example to apply a default other than `0`, use `get` and check the type:

```java
Object stored = PUUIDS.get(this, uuid, "Coins");
int coins = stored instanceof Number number ? number.intValue() : 100; // 100 for new players
```

That's one file read, and it saves you from having to store defaults for every player. `contains` followed by `getInt` works too, but reads the file twice.

## Bulk lookups

These methods scan the whole `Data` folder. They require your plugin to be connected, and return an empty list if it isn't.

| Method | Returns |
| --- | --- |
| `getAllPlayerUUIDs(plugin, quickmode)` | The UUID of every player with a file. |
| `getAllPlayerNames(plugin)` | The username of every player with a file. |
| `getAllWithBoolean(plugin, path, quickmode)` | The **usernames** of players whose boolean at `path` is `true`. |
| `getAllWithoutBoolean(plugin, path, quickmode)` | The **usernames** of players whose boolean at `path` is `false` or missing. |

With `quickmode` set to `true`, UUIDs are taken from the file names instead of being read from inside each file. It's faster and gives the same result, so use `true` unless you have a reason not to. Note that `getAllWithBoolean` still opens every file to check the value.

These calls read every player file, which takes a noticeable amount of time on a large server. **Always run them off the main thread:**

```java
Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
    List<String> subscribers = PUUIDS.getAllWithBoolean(this, "Newsletter", true);

    // Back to the main thread before touching players or the world.
    Bukkit.getScheduler().runTask(this, () ->
            sender.sendMessage(subscribers.size() + " players get the newsletter."));
});
```

On Folia, use `Bukkit.getAsyncScheduler()` and `Bukkit.getGlobalRegionScheduler()` instead of `Bukkit.getScheduler()`. [Patterns](Patterns.md#leaderboards-and-other-offline-queries) has a leaderboard example.

## Reading another player's data

Every getter takes a UUID, so reading data for offline players works the same way as for online ones. To go from a username to a UUID, use `PUUIDS.getUUID(name)`. It returns `"0"` if nobody with that name has a file (see [Player Info](Player-Info.md)).

Next: [Player Info](Player-Info.md)

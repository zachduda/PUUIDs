# API Reference

Everything public in `com.zachduda.puuids.api`. All `PUUIDS` methods are `static`.

**Conventions used on this page**

- `plugin` is your plugin instance. Your data lives under `Plugins.<plugin name in upper case>`.
- `uuid` is the player's UUID as a String, in the form `UUID.toString()` produces.
- `path` is a `ConfigurationSection` style path such as `"Stats.Kills"`.
- **Connect** says whether the method needs your plugin to have called [`connect`](#connect) first, and what it does if you haven't.
- A **task id** is the number returned by a write: positive if the write was queued, `0` if it was rejected. See [`TimerSaved`](Events.md#timersaved).

## Connecting

### `connect`

```java
boolean connect(Plugin plugin, PUUIDS.APIVersion version)
```

Registers your plugin. Call it once from `onEnable`, with `APIVersion.V4`. Returns `true` on success, or `false` if the startup window has closed, the version is outdated, or this plugin already connected. See [Getting Started](Getting-Started.md#things-to-know-about-connect).

### `PUUIDS.APIVersion`

| Constant | Status |
| --- | --- |
| `V1`, `V2`, `V3` | Deprecated. Refused by `connect`. |
| `V4` | Current. Locations are saved with their world. |

## Writing

All writes are [queued](How-Saving-Works.md) and return immediately. They are safe to call from any thread.

| Method | Connect | Returns | Notes |
| --- | --- | --- | --- |
| `set(plugin, uuid, path, Object value)` | required, else `0` | task id | `value` can be anything YAML can store. `null` removes the path. |
| `setNull(plugin, uuid, path)` | required, else `0` | task id | Removes the value or section at `path`. |
| `setNull(plugin, uuid)` | required, else `0` | task id | Removes everything your plugin saved for this player. |
| `setLocation(plugin, uuid, path, Location location)` | required, else `0` | task id of the last value written | Saves `X`, `Y`, `Z`, `Pitch`, `World` and `Yaw`. The location must have a world. |
| `addToStringList(plugin, uuid, path, String value)` | required, else `0` | task id | Reads the list from disk, appends, and queues the new list. [Not safe for rapid calls.](Storing-Data.md#lists) |
| `removeFromStringList(plugin, uuid, path, String value)` | required, else `0` | task id | Removes the first match. Same caveat. |
| `addToIntList(plugin, uuid, path, int value)` | required, else `0` | task id | Same caveat. |
| `removeFromIntList(plugin, uuid, path, int value)` | required, else `0` | task id | Removes the first match. Same caveat. |
| `addToAllWithout(plugin, path, Object value)` | required, else `false` | `boolean` | Sets `value` in every file that doesn't have `path` yet. Startup only. Reads every file on the calling thread. |

Every write also returns `0` if `plugin`, `uuid` or `path` is `null`. The path `PUUIDS_SET_AS_ALL_NULL` is reserved.

## Reading your plugin's data

Every read loads the player's file from disk on the calling thread. Reads don't see writes that are still [in the queue](How-Saving-Works.md#the-read-after-write-rule).

| Method | Connect | Returns | When nothing is saved |
| --- | --- | --- | --- |
| `getString(plugin, uuid, path)` | not needed | `String` | `null` |
| `getBoolean(plugin, uuid, path)` | not needed | `boolean` | `false` |
| `getInt(plugin, uuid, path)` | not needed | `int` | `0` |
| `getLong(plugin, uuid, path)` | not needed | `long` | `0` |
| `getDouble(plugin, uuid, path)` | not needed | `double` | `0.0` |
| `getStringList(plugin, uuid, path)` | not needed | `List<String>` | empty list |
| `getIntList(plugin, uuid, path)` | not needed | `List<Integer>` | empty list |
| `getNCList(plugin, uuid, path)` | not needed | `List<?>` | `null` |
| `getItemStack(plugin, uuid, path)` | not needed | `ItemStack` | `null` |
| `get(plugin, uuid, path)` | required, else `Boolean.FALSE` | `Object` | `null` |
| `contains(plugin, uuid, path)` | required, else `false` | `boolean` | `false` |
| `getLocation(plugin, uuid, path)` | required, else `null` | `Location` | throws `NullPointerException` |
| `getLocation(plugin, uuid, path, String world)` | required, else `null` | `Location` | a location at 0, 0.3, 0 |

`getLocation` adds 0.3 to Y and returns pitch and yaw as whole degrees. Its world is `null` if the saved world isn't loaded. The four-argument version is **deprecated**: it ignores the saved world and uses the one you pass, and logs an error for V4 plugins.

## Bulk lookups

These read every file in the `Data` folder. Run them off the main thread.

| Method | Connect | Returns |
| --- | --- | --- |
| `getAllPlayerUUIDs(plugin, boolean quickmode)` | required, else empty | `ArrayList<String>` of UUIDs |
| `getAllPlayerNames(plugin)` | required, else empty | `ArrayList<String>` of usernames |
| `getAllWithBoolean(plugin, path, boolean quickmode)` | required, else empty | `ArrayList<String>` of **usernames** whose value is `true` |
| `getAllWithoutBoolean(plugin, path, boolean quickmode)` | required, else empty | `ArrayList<String>` of **usernames** whose value is `false` or missing |

`quickmode` takes UUIDs from the file names instead of reading them from each file. The result is the same, it's just faster.

## Player info

Details PUUIDs keeps for every player. None of these need your plugin to be connected. See [Player Info](Player-Info.md).

| Method | Returns | When unknown |
| --- | --- | --- |
| `getUUID(String name)` | UUID for a username, case-insensitive | `"0"` |
| `getName(String uuid)` | Username for a UUID | `"0"` |
| `hasFile(String uuid)` | Whether the player has a file | `false` |
| `getIP(String uuid)` | Last IP address | `"0"` |
| `getLastOn(String uuid)` | Last time their details were saved, epoch milliseconds | `0` |
| `getPlayTime(String uuid)` | Play time in seconds, including the current session | `0` |
| `getFormatedPlayTime(String uuid)` | Play time as text, e.g. `"2 hours, 5 minutes"` | `"Nothing Yet!"` |
| `secsToFormatTime(long seconds)` | Any duration as text | `"Nothing Yet!"` for zero or less |
| `getServerId()` | This server's random, persistent ID | |

## Events

All in `com.zachduda.puuids.api`. See [Events](Events.md) for details and examples.

| Event | Thread | Methods |
| --- | --- | --- |
| `OnNewFile` | async | `Player getPlayer()` |
| `TimerSaved` | async | `String getPlugin()` (upper case), `String getUUID()`, `int getId()` |
| `UpdatedPlayerStats` | async | none |
| `PluginRegistered` | async | `String getPlugin()` |
| `ConnectionOpen` | async | none |
| `ConnectionClose` | main | none |

## The data file

`plugins/PUUIDs/Data/<uuid>.yml`:

```yaml
UUID: 6191ff85-e092-4e9a-94bd-63df409c2079   # PUUIDs
Username: zach_attack                         # PUUIDs
IP: 203.0.113.7                               # PUUIDs
Last-On: 1758990000000                        # PUUIDs, epoch ms
Time-Played: 86400                            # PUUIDs, seconds
Plugins:
  MYPLUGIN:                                   # your plugin's section
    Your-Path: your value
```

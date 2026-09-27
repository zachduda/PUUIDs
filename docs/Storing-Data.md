# Storing Data

All writes go through a handful of static methods on `PUUIDS`. They need your plugin to be [connected](Getting-Started.md#3-connect-in-onenable) first.

## `set`

```java
int set(Plugin plugin, String uuid, String path, Object value)
```

| Parameter | Meaning |
| --- | --- |
| `plugin` | Your plugin instance, usually `this`. |
| `uuid` | The player's UUID as a String: `player.getUniqueId().toString()`. |
| `path` | Where to save the value, using dots for nesting, just like `getConfig()`. |
| `value` | The value to save. Passing `null` removes whatever is at `path`. |

```java
String uuid = player.getUniqueId().toString();

PUUIDS.set(this, uuid, "Joined-Discord", true);
PUUIDS.set(this, uuid, "Multiplier", 1.5);
PUUIDS.set(this, uuid, "Title", "The Brave");
PUUIDS.set(this, uuid, "Kits.Starter.Last-Claim", System.currentTimeMillis());
```

`set` doesn't touch the disk. It puts the write in PUUIDs' save queue and returns straight away, which makes it safe and cheap to call from the main thread, or from any other thread. The write reaches the file a short time later (half a second with default settings). See [How Saving Works](How-Saving-Works.md) for what that means for your code.

### The return value

`set` returns an `int` **task id**:

- A positive number means the write was queued. PUUIDs fires a [`TimerSaved`](Events.md#timersaved) event with the same id once it is on disk.
- `0` means the write was rejected and nothing was queued. That happens when your plugin isn't connected, or when `plugin`, `uuid` or `path` is `null`.

Ids count up from 1 and start again when the server restarts, so don't store them.

## Where your data ends up

Each player has one file, `plugins/PUUIDs/Data/<uuid>.yml`. Your values live under `Plugins.<PLUGIN NAME>`, where the plugin name is the `name` from your `plugin.yml` in upper case. The four calls above produce:

```yaml
UUID: 6191ff85-e092-4e9a-94bd-63df409c2079
Username: zach_attack
IP: 203.0.113.7
Last-On: 1758990000000
Time-Played: 86400
Plugins:
  MYPLUGIN:
    Joined-Discord: true
    Multiplier: 1.5
    Title: The Brave
    Kits:
      Starter:
        Last-Claim: 1758990123456
```

Some consequences of the section being tied to your plugin's name:

- Two plugins can use the same path without clashing.
- Every read and write you make is automatically limited to your own section.
- **If you rename your plugin, it loses access to its old data.** The old values stay under the old name. Plan for a migration if you ever need to rename.
- The name is upper-cased, so `MyPlugin` and `myplugin` share a section.

## Paths

Paths behave like `ConfigurationSection` paths:

- A `.` creates a nested section: `"Kits.Starter.Last-Claim"`.
- Setting a value at a path **replaces** everything that was there, including a whole section. `set(this, uuid, "Kits", null)` removes every kit at once.
- Paths are case sensitive: `"Coins"` and `"coins"` are different values.
- Don't use the path `PUUIDS_SET_AS_ALL_NULL`. PUUIDs uses it internally to mean "clear this plugin's whole section".

## What you can store

Anything a Bukkit `YamlConfiguration` can save:

| Type | Notes |
| --- | --- |
| `String`, `boolean`, `int`, `long`, `double` | The everyday types. Read them back with the matching getter. |
| `List<String>`, `List<Integer>` and other lists | Read back with `getStringList`, `getIntList` or `getNCList`. |
| `ItemStack` and other `ConfigurationSerializable` types | Serialized the same way Bukkit saves them in configs. |
| `Map<String, ?>` | Saved as a nested section. Nested paths are usually clearer. |

A few types need converting first:

- **UUIDs:** store `uuid.toString()`.
- **Enums:** store `value.name()` and read it back with `MyEnum.valueOf(...)`.
- **Timestamps:** store epoch milliseconds as a `long`.
- **Locations:** use [`setLocation`](#locations).

### Don't change an object after passing it to `set`

The queue keeps a reference to the object you pass, and PUUIDs serializes it when the queue is drained, not when you call `set`. If you modify a list or `ItemStack` after handing it over, the modified version is what gets saved. Pass a copy when you plan to keep using the original:

```java
PUUIDS.set(this, uuid, "Homes", new ArrayList<>(homes));
PUUIDS.set(this, uuid, "Backpack.Slot-0", item.clone());
```

## Removing data

```java
PUUIDS.setNull(this, uuid, "Title");   // remove one value (or one section)
PUUIDS.setNull(this, uuid);            // remove everything your plugin saved for this player
```

Both return a task id, just like `set`. `set(this, uuid, path, null)` does the same thing as `setNull(this, uuid, path)`.

## Lists

PUUIDs has helpers for adding to and removing from lists:

```java
PUUIDS.addToStringList(this, uuid, "Unlocked-Titles", "The Brave");
PUUIDS.removeFromStringList(this, uuid, "Unlocked-Titles", "The Brave");
PUUIDS.addToIntList(this, uuid, "Completed-Quests", 12);
PUUIDS.removeFromIntList(this, uuid, "Completed-Quests", 12);
```

The remove helpers take out the first matching entry. All four return a task id.

> [!WARNING]
> Each helper reads the current list from disk, changes it, and queues the whole new list. The file doesn't contain queued changes yet, so two calls on the same list before the first one is saved overwrite each other:
>
> ```java
> PUUIDS.addToStringList(this, uuid, "Titles", "A"); // reads [], queues [A]
> PUUIDS.addToStringList(this, uuid, "Titles", "B"); // reads [] again, queues [B]
> // The file ends up with [B]
> ```
>
> To make several changes, build the list yourself and call `set` once. If players can trigger changes in quick succession, keep the list in memory (see [Patterns](Patterns.md#cache-values-for-online-players)).

## Locations

```java
PUUIDS.setLocation(this, uuid, "Home", player.getLocation());

if (PUUIDS.contains(this, uuid, "Home")) {
    Location home = PUUIDS.getLocation(this, uuid, "Home");
}
```

`setLocation` saves six values under the path you give it:

```yaml
Plugins:
  MYPLUGIN:
    Home:
      X: 102.5
      Y: 64.0
      Z: -33.2
      Pitch: 12.5
      World: world
      Yaw: 181.4
```

It returns the task id of the last of those writes (`Yaw`). The location you pass must have a world.

When you read it back with `getLocation`:

- **Y is raised by 0.3**, so a player teleported there doesn't clip into the floor.
- **Pitch and yaw come back as whole degrees**, rounded towards zero.
- **The world is looked up by name.** If that world isn't loaded, the returned `Location` has a `null` world.
- **Check `contains` first.** If nothing is saved at that path, `getLocation` throws a `NullPointerException` rather than returning `null`.

## Defaults for existing players

When you add a new value to your plugin, players who already have a file don't have it yet. `addToAllWithout` sets a default in every file that is missing the value:

```java
@Override
public void onEnable() {
    PUUIDS.connect(this, APIVersion.V4);
    PUUIDS.addToAllWithout(this, "Settings.Chat-Sounds", true);
}
```

- It only works during startup, while connections are accepted. Later calls return `false`.
- It reads every player file on the calling thread, which slows startup on servers with a lot of players.
- It queues one write per player that needs the default. The queue saves a limited number of writes per run (see [How Saving Works](How-Saving-Works.md#timing-and-throughput)), so thousands of defaults take a while to land.
- It returns `true` when it has finished queuing, even if no file needed a default.

Often you don't need stored defaults at all: treat a missing value as the default when you read it. [Reading Data](Reading-Data.md#telling-missing-values-apart) shows how.

## Things to avoid

- **Writing for a player who has never joined.** PUUIDs creates a file that has your data but not the player's details, and deletes it as invalid at the next startup. Check `PUUIDS.hasFile(uuid)` first.
- **Relying on data outliving the inactivity clean-up.** By default PUUIDs deletes the file of anyone who hasn't played for 365 days, including every plugin's data in it. Server owners can change or disable this (see [Server Owners](Server-Owners.md#inactivity-clean-up)).

## Saving on shutdown

Calling `set` from your `onDisable` is fine. Plugins that depend on PUUIDs are disabled before PUUIDs, and when PUUIDs shuts down it saves everything still in the queue.

Next: [Reading Data](Reading-Data.md)

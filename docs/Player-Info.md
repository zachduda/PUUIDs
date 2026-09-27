# Player Info

PUUIDs keeps a few details about every player without any plugin asking for them. These are the top-level keys in each player file:

```yaml
UUID: 6191ff85-e092-4e9a-94bd-63df409c2079
Username: zach_attack
IP: 203.0.113.7
Last-On: 1758990000000
Time-Played: 86400
```

| Key | Meaning |
| --- | --- |
| `UUID` | The player's UUID. It is also the file name. |
| `Username` | Their name as of the last time they were seen. Name changes are picked up when they next join. |
| `IP` | The address they last connected from, without the port. |
| `Last-On` | When PUUIDs last saved these details, in epoch milliseconds. |
| `Time-Played` | Total time played on this server, in seconds. |

## When the details are updated

PUUIDs saves a player's details:

- **when they join** (skipped if they joined or left in the last 30 seconds, to avoid rejoin spam),
- **every `Player-Update-Seconds`** while they are online (300 seconds by default), as a checkpoint,
- **when they quit**.

Play time is counted from join to quit and is exact for a normal quit. The checkpoint only limits how much of a session is lost if the server crashes.

A brand-new player's file is created by the save queue shortly after they join, and [`OnNewFile`](Events.md#onnewfile) fires once it exists. `Time-Played` first appears at their first checkpoint or quit.

## Reading the details

None of these methods need your plugin to be connected.

| Method | Returns | When unknown |
| --- | --- | --- |
| `getUUID(String name)` | The UUID for a username. | `"0"` |
| `getName(String uuid)` | The username for a UUID. | `"0"` |
| `hasFile(String uuid)` | Whether the player has a PUUIDs file. | `false` |
| `getIP(String uuid)` | The address they last joined from. | `"0"` |
| `getLastOn(String uuid)` | `Last-On`, in epoch milliseconds. | `0` |
| `getPlayTime(String uuid)` | Total play time in seconds, including the current session. | `0` |
| `getFormatedPlayTime(String uuid)` | Play time as readable text, such as `"3 hours, 12 minutes"`. | `"Nothing Yet!"` |
| `secsToFormatTime(long seconds)` | Any number of seconds as readable text. | `"Nothing Yet!"` for zero or less |
| `getServerId()` | A random ID that identifies this server. | |

Note that `getFormatedPlayTime` is spelled with one "t".

### Unknown players are `"0"`, not `null`

The String methods return `"0"` when they don't know the answer:

```java
String uuid = PUUIDS.getUUID(args[0]);
if ("0".equals(uuid)) {
    sender.sendMessage("That player has never joined.");
    return true;
}
```

### `getUUID`

- The lookup ignores case, so `"Notch"` and `"notch"` find the same player.
- It only knows players who have a file **on this server**. It never asks Mojang, so it can't resolve a name that has never joined.
- Names PUUIDs has seen are indexed in memory, so looking up a known player is quick. A name that isn't in the index makes PUUIDs read every player file before it can answer `"0"`, so look up names typed by players off the main thread on large servers.

### `getLastOn`

`Last-On` is refreshed at every join, checkpoint and quit. For an offline player it tells you roughly when they left. For an online player it's the time of their latest checkpoint, so check `Player#isOnline()` first if you are building a "last seen" feature.

```java
long lastOn = PUUIDS.getLastOn(uuid);
long daysAgo = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastOn);
```

### `getPlayTime` and formatting

`getPlayTime` includes the part of the current session that hasn't been saved yet, so it's accurate for online players too.

`secsToFormatTime` keeps the two largest units that apply:

| Seconds | Result |
| --- | --- |
| `0` | `Nothing Yet!` |
| `45` | `45 seconds` |
| `60` | `1 minute` |
| `3725` | `1 hour, 2 minutes` |
| `90000` | `1 day, 1 hour` |

```java
player.sendMessage("You've played for " + PUUIDS.getFormatedPlayTime(uuid));
```

### `getServerId`

PUUIDs generates a random UUID the first time it runs and stores it as `Advanced.UUID` in its `config.yml`. It stays the same across restarts, so you can use it to tell servers apart, for example when several servers report to the same web service.

## A note on IP addresses

IP addresses are personal data in many places. If your plugin shows or exports them, limit that to staff, and don't send them anywhere they don't need to go.

Next: [How Saving Works](How-Saving-Works.md)

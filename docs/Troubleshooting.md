# Troubleshooting

Start by turning on debug mode (`Settings.Debug: true`, then `/puuids reload`). With it on, PUUIDs logs every write it saves in this form, which shows what reached the file and when:

```
[PUUIDs] [Debug] (42) MYPLUGIN set 150 for 6191ff85-e092-4e9a-94bd-63df409c2079 under: Coins
```

The number in brackets is the task id your `set` call returned.

## Connecting

### `connect` returns `false`

Check the console for one of these:

| Console message | Cause | Fix |
| --- | --- | --- |
| `Plugin 'X' tried to register with puuids after the connection window.` | `connect` was called after startup, or your plugin was reloaded. | Call `connect` in `onEnable`, and restart instead of reloading. |
| `Plugin X is unable to use puuids, for they are using an outdated version.` | An old `APIVersion`. | Pass `APIVersion.V4`. |
| `Plugin X tried to overwrite another plugin with the exact same name.` | `connect` was called twice. | Call it once. |

If there's no message at all, make sure your `plugin.yml` has `depend: [PUUIDs]` or `softdepend: [PUUIDs]` (not `soft-depend`), and that PUUIDs itself enabled without errors.

### `set` returns `0`

The write was rejected: your plugin isn't connected, or `uuid` or `path` was `null`. Use `/puuids plugins` to see which plugins are connected.

## Data

### A value I just set reads back as the old value

That's expected. Writes are queued and saved a moment later, and reads come straight from the file. See [the read-after-write rule](How-Saving-Works.md#the-read-after-write-rule) for how to structure your code around it.

### Counters come out lower than they should / list entries go missing

Two read-modify-write updates happened before the first was saved, so the second overwrote the first. This includes the `addTo...List` and `removeFrom...List` helpers. Keep the value in memory for online players ([example](Patterns.md#cache-values-for-online-players)), or build the whole list and call `set` once.

### My data disappeared after a restart

In order of likelihood:

1. **The inactivity clean-up deleted the file.** Players who haven't joined for `Max-Days` (365 by default) lose their whole file. See [Server Owners](Server-Owners.md#inactivity-clean-up).
2. **You wrote data for a player who had never joined.** That creates a file without the player's details, which PUUIDs deletes as invalid at startup. Check `hasFile(uuid)` before writing.
3. **Your plugin's name changed.** Data is stored under the `name` from `plugin.yml`, so a renamed plugin reads from a new, empty section.
4. **The server crashed.** Writes still in the queue at the time are lost.

### `getLocation` throws `NullPointerException`

Nothing is saved at that path (or it was saved before API V4, without a world). Check `contains` first.

### `getLocation` returns a location with a `null` world

The saved world isn't loaded, or was renamed or deleted.

### `get` returns `false` for a value I never saved as a boolean

`get` returns `Boolean.FALSE` when your plugin isn't connected or an argument is `null`. Check that `connect` succeeded.

### `getAllWithBoolean` returns names, not UUIDs

That's how it works: both `getAllWithBoolean` and `getAllWithoutBoolean` return usernames. Use `getAllPlayerUUIDs` with your own check if you need UUIDs.

## Build and runtime errors

### `NoClassDefFoundError: com/zachduda/puuids/api/PUUIDS`

PUUIDs isn't installed or didn't enable. If PUUIDs is optional for your plugin, check `isPluginEnabled("PUUIDs")` before using it, and keep every reference to `PUUIDS` in one class ([example](Getting-Started.md#making-puuids-optional)). If it's required, use `depend` so the server refuses to load your plugin without it.

Also make sure you haven't shaded PUUIDs into your jar. It should be `provided` (Maven) or `compileOnly` (Gradle).

### `class file has wrong version 65.0, should be 52.0` (or similar)

You're compiling with a JDK older than 21. Build with JDK 21 or newer.

### Could not find `com.zachduda:PUUIDs`

The artifact id is lower case: `com.zachduda:puuids`. Also check that `https://maven.zachduda.com/releases` is listed as a repository.

### `IllegalStateException` inside a PUUIDs event handler

`OnNewFile`, `TimerSaved`, `UpdatedPlayerStats`, `PluginRegistered` and `ConnectionOpen` are fired asynchronously. Schedule any work on players or the world back onto the right thread ([details](Events.md#most-events-are-asynchronous)).

### `UnsupportedOperationException` from the scheduler on Folia

`Bukkit.getScheduler()` isn't available on Folia. Use the Folia schedulers instead ([details](Patterns.md#folia)).

## Performance

### The server lags when my plugin reads data

Every getter reads a file from disk. Move bulk calls (`getAll...`, loops over many players) off the main thread, and cache values for online players instead of reading them on every use.

### Writes take a long time to show up

Check `/puuids info`. A large "Queued Data" number means writes are arriving faster than the queue saves them (50 per second by default). Server owners can raise `Advanced.Max-Processes-Per-Queue`. Plugin developers can write less often: save a player's data when it changes meaningfully, not every tick.

## Still stuck?

Ask in the Discord at <https://zachduda.com/discord>, or open an issue on GitHub with the output of `/puuids debug`.

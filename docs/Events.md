# Events

PUUIDs fires six Bukkit events, all in the `com.zachduda.puuids.api` package. Listen for them the usual way, with a `Listener` and `@EventHandler`. None of them can be cancelled.

| Event | Fired when | Thread | Methods |
| --- | --- | --- | --- |
| [`OnNewFile`](#onnewfile) | A player's file has just been created. | async | `getPlayer()` |
| [`TimerSaved`](#timersaved) | A queued write has been saved to disk. | async | `getPlugin()`, `getUUID()`, `getId()` |
| [`UpdatedPlayerStats`](#updatedplayerstats) | The periodic checkpoint of online players has been queued. | async | none |
| [`PluginRegistered`](#pluginregistered) | A plugin has connected to PUUIDs. | async | `getPlugin()` |
| [`ConnectionOpen`](#connectionopen) | PUUIDs has finished enabling. | async | none |
| [`ConnectionClose`](#connectionclose) | PUUIDs is about to shut down. | main | none |

## Most events are asynchronous

Every event except `ConnectionClose` is fired from a background thread. In those handlers:

- **Don't touch the world, entities or inventories directly.** Schedule the work back onto the right thread first. With the Bukkit scheduler (Spigot and Paper):

  ```java
  Bukkit.getScheduler().runTask(plugin, () -> player.sendTitle("Welcome!", "", 10, 70, 20));
  ```

  On Folia, and on Paper if you prefer, use the player's own scheduler instead:

  ```java
  player.getScheduler().run(plugin, task -> player.sendTitle("Welcome!", "", 10, 70, 20), null);
  ```

- **Calling PUUIDs is fine.** `set` only adds to the queue, and getters only read files, so both are safe from any thread.
- **Keep handlers short.** `OnNewFile` and `TimerSaved` run on the thread that saves player files, so a slow handler delays saving.

## OnNewFile

Fired after PUUIDs creates a player's file for the first time: on their first join, or on their first join after the [inactivity clean-up](How-Saving-Works.md#files-that-get-removed) deleted their old file. It arrives shortly after `PlayerJoinEvent`, once the save queue has written the file.

```java
@EventHandler
public void onNewFile(OnNewFile event) {
    Player player = event.getPlayer();

    Bukkit.getScheduler().runTask(plugin, () -> {
        if (player.isOnline()) { // they may have left already
            player.getInventory().addItem(new ItemStack(Material.BREAD, 16));
        }
    });
}
```

If all you need is "has this player ever joined this server", Bukkit's `Player#hasPlayedBefore()` works too. `OnNewFile` is useful when the answer should match PUUIDs' own data, including players whose old file was cleaned up.

## TimerSaved

Fired once for **every** write that is saved, from every connected plugin. Use it to find out when a specific write is safely on disk.

| Method | Returns |
| --- | --- |
| `getPlugin()` | The name of the plugin that made the write, **in upper case**. |
| `getUUID()` | The UUID of the player whose file was written. |
| `getId()` | The task id that `set` (or `setNull`, `setLocation` and so on) returned. |

Filter on the plugin name first, because the ids of other plugins' writes arrive here too:

```java
private final Set<Integer> pending = ConcurrentHashMap.newKeySet();

public void saveReceipt(Player player, String receipt) {
    int id = PUUIDS.set(plugin, player.getUniqueId().toString(), "Last-Receipt", receipt);
    if (id != 0) {
        pending.add(id);
    }
}

@EventHandler
public void onSaved(TimerSaved event) {
    if (!event.getPlugin().equalsIgnoreCase(plugin.getName())) {
        return; // another plugin's write
    }
    if (pending.remove(event.getId())) {
        plugin.getLogger().info("Receipt for " + event.getUUID() + " is on disk.");
    }
}
```

Keep in mind:

- When your write with id `N` is saved, every write you queued earlier **for the same player** is saved as well.
- No `TimerSaved` is fired for a write whose save failed, or for the final writes PUUIDs makes while shutting down. Don't wait forever for one.
- The event is fired after the file is written, but possibly before the change reaches MySQL.

## UpdatedPlayerStats

Fired every `Advanced.Player-Update-Seconds` (300 seconds by default), right after PUUIDs has queued a checkpoint of every online player's details and play time. The checkpoint has been queued at this point, not necessarily saved. It's a convenient hook for periodic work of your own that should line up with PUUIDs' checkpoints.

## PluginRegistered

Fired when a plugin connects successfully. `getPlugin()` returns that plugin's name as written in its `plugin.yml`. It's mainly useful for addons that want to react to another plugin connecting.

## ConnectionOpen

Fired once PUUIDs has finished enabling and is accepting connections. PUUIDs is enabled early (`load: STARTUP`), usually before other plugins have registered their listeners, so **most plugins never receive this event**. Don't wait for it; just connect in `onEnable`.

## ConnectionClose

Fired on the main thread at the start of PUUIDs' `onDisable`, before it saves the rest of the queue. Writes queued from a handler are still saved.

However, plugins that depend on PUUIDs are disabled before PUUIDs is, and a disabled plugin's listeners are removed, so **your plugin normally won't receive this event either.** Do your final saves in your own `onDisable`. See [Shutdown and crashes](How-Saving-Works.md#shutdown-and-crashes).

Next: [Patterns and Examples](Patterns.md)

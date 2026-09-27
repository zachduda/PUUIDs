# Patterns and Examples

Tried and tested ways to structure common features on top of PUUIDs. The code targets Spigot and Paper. See [Folia](#folia) for the scheduler changes Folia needs.

## Cache values for online players

For anything that changes often (currencies, stats, counters, lists), keep the live value in memory and treat PUUIDs as the place it gets saved:

1. Load the value from PUUIDs the first time you need it.
2. Serve every read from memory.
3. On every change, update memory and queue a `set` with the new value.

This avoids the [read-after-write problem](How-Saving-Works.md#the-read-after-write-rule) completely: the file is read once, before any of your writes could be pending, and never again while your copy is in use. It also means your plugin reads the disk once per player instead of on every lookup.

```java
import com.zachduda.puuids.api.PUUIDS;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps coin balances in memory and saves every change through PUUIDs. */
public final class CoinStore implements Listener {

    private static final String PATH = "Coins";
    private static final int STARTING_COINS = 100;

    private final Plugin plugin;
    private final Map<UUID, Integer> balances = new ConcurrentHashMap<>();

    public CoinStore(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Loads the balance as the player joins, so commands never wait on the disk. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        balance(event.getPlayer().getUniqueId());
    }

    public int balance(UUID player) {
        return balances.computeIfAbsent(player, this::read);
    }

    public int add(UUID player, int amount) {
        return balances.compute(player, (id, current) -> {
            int updated = (current == null ? read(id) : current) + amount;
            PUUIDS.set(plugin, id.toString(), PATH, updated);
            return updated;
        });
    }

    /** One file read. A player with nothing saved yet starts with STARTING_COINS. */
    private int read(UUID player) {
        Object stored = PUUIDS.get(plugin, player.toString(), PATH);
        return stored instanceof Number number ? number.intValue() : STARTING_COINS;
    }
}
```

A few details worth copying:

- **`set` is called inside `compute`.** That keeps writes in the same order as the updates, even if two threads change the same balance at once.
- **The default lives in `read`**, rather than being written to every file up front. New players get it automatically, and there's nothing to migrate if you change it.
- **Balances stay cached until the server restarts.** If entries were removed when players quit, a player who rejoined before their last write was saved would load the old value from the file. That window is usually half a second, but it grows while saving is [paused](How-Saving-Works.md#timing-and-throughput). One `Integer` per player costs very little, so the simplest fix is to not evict at all.

If you cache something large, such as whole inventories, you'll want to evict when players leave. In that case, only drop a player's entry once the [`TimerSaved`](Events.md#timersaved) event for their last write has arrived.

On a network where [MySQL `Sync-On-Join`](MySQL.md#multi-server-networks) brings in changes made on other servers, a cache that outlives a player's session misses those changes. There, reload the value when the player joins and evict it only after the last write has been saved.

## Setting up new players

`OnNewFile` fires once PUUIDs has created a new player's file, which makes it a good place for first-join rewards and messages. It's fired on PUUIDs' save thread, so switch threads before touching the player:

```java
@EventHandler
public void onNewFile(OnNewFile event) {
    Player player = event.getPlayer();
    Bukkit.getScheduler().runTask(plugin, () -> {
        if (player.isOnline()) {
            player.sendMessage("Welcome! Here's something to get you started.");
            player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        }
    });
}
```

For starting **values**, prefer a default in your read, as `CoinStore` does, over writing them in `OnNewFile`. A cache like `CoinStore` has usually loaded the player before `OnNewFile` fires, and would overwrite a value written there with its own copy.

## Adding a value to every existing player

When a plugin update adds a new setting that must be stored for everyone, `addToAllWithout` fills it in for files that don't have it yet. Call it right after connecting:

```java
@Override
public void onEnable() {
    PUUIDS.connect(this, APIVersion.V4);
    PUUIDS.addToAllWithout(this, "Settings.Chat-Sounds", true);
}
```

It reads every file during startup and queues one write per player who needs the value. On big servers, a default in your read (see above) is usually the better choice.

## Leaderboards and other offline queries

Queries over every player read every file, so run them asynchronously and hop back to the main thread for the result:

```java
private record Entry(String name, int coins) {}

public void showTopCoins(CommandSender sender) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        List<Entry> top = PUUIDS.getAllPlayerUUIDs(plugin, true).stream()
                .map(uuid -> new Entry(PUUIDS.getName(uuid), PUUIDS.getInt(plugin, uuid, "Coins")))
                .sorted(Comparator.comparingInt(Entry::coins).reversed())
                .limit(10)
                .toList();

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < top.size(); i++) {
                sender.sendMessage((i + 1) + ". " + top.get(i).name() + ": " + top.get(i).coins());
            }
        });
    });
}
```

This reads each player file twice (once for the name, once for the coins). That's fine for an occasional command. If a leaderboard is shown constantly, compute it on a timer and cache the result, or query the [MySQL mirror](MySQL.md#querying-the-data-yourself) instead.

## Folia

Folia has no main thread, and `Bukkit.getScheduler()` throws `UnsupportedOperationException` there. PUUIDs itself supports Folia, and the API calls work the same, but your scheduling code needs to change:

| On Spigot / Paper | On Folia |
| --- | --- |
| `Bukkit.getScheduler().runTaskAsynchronously(plugin, task)` | `Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run())` |
| `Bukkit.getScheduler().runTask(plugin, task)` for a player | `player.getScheduler().run(plugin, t -> task.run(), null)` |
| `Bukkit.getScheduler().runTask(plugin, task)` for global work | `Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run())` |

The Folia schedulers are also available on Paper 1.20 and newer, so a plugin that only targets Paper and Folia can use them everywhere.

## A complete example plugin

A small plugin that gives players a coin balance, using the `CoinStore` class from [above](#cache-values-for-online-players).

**plugin.yml**

```yaml
name: Coins
version: 1.0.0
main: com.example.coins.CoinsPlugin
api-version: '1.13'
depend: [PUUIDs]
commands:
  coins:
    description: Check your coins, or give coins to a player.
    usage: /coins [give <player> <amount>]
permissions:
  coins.give:
    default: op
```

**CoinsPlugin.java**

```java
package com.example.coins;

import com.zachduda.puuids.api.PUUIDS;
import com.zachduda.puuids.api.PUUIDS.APIVersion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoinsPlugin extends JavaPlugin {

    private CoinStore coins;

    @Override
    public void onEnable() {
        if (!PUUIDS.connect(this, APIVersion.V4)) {
            getLogger().severe("Couldn't connect to PUUIDs, disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        coins = new CoinStore(this);
        getServer().getPluginManager().registerEvents(coins, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 && sender instanceof Player player) {
            sender.sendMessage("You have " + coins.balance(player.getUniqueId()) + " coins.");
            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("coins.give")) {
            Player target = getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(args[1] + " isn't online.");
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                return false;
            }

            int total = coins.add(target.getUniqueId(), amount);
            sender.sendMessage(target.getName() + " now has " + total + " coins.");
            return true;
        }

        return false;
    }
}
```

Put `CoinStore` in the same package. After `/coins give zach_attack 50`, that player's file contains:

```yaml
Plugins:
  COINS:
    Coins: 150
```

Next: [MySQL](MySQL.md)

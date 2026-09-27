# Server Owners

PUUIDs works out of the box. Put the jar in `plugins/`, restart, and any plugin that uses it will start saving player data. This page covers what you can change and what to keep an eye on.

## Installing

- Supported on Spigot, Paper and Folia, Minecraft 1.13 and newer.
- Needs **Java 21 or newer**. Older Minecraft versions only work if their server software runs on Java 21.
- Restart the server to install or update. Don't use `/reload` or a plugin manager (see [Reloading](#reloading)).

Player data is stored in `plugins/PUUIDs/Data/`, with one `<uuid>.yml` file per player.

## Commands and permissions

Every command needs the `puuids.admin` permission, which operators have by default.

| Command | What it does |
| --- | --- |
| `/puuids help` | Lists the commands. |
| `/puuids version` | Shows the installed version. |
| `/puuids ontime [player]` | Shows how long you, or another player, have played. The console must name a player. |
| `/puuids info` | Shows how quickly saves are running, the size of the save queue, and an overall health rating. |
| `/puuids plugins` | Lists the plugins connected to PUUIDs. |
| `/puuids reload` | Reloads `config.yml`. |
| `/puuids mysql` | Shows the state of the MySQL mirror. |
| `/puuids mysql export` | Copies every player file to MySQL. |
| `/puuids mysql import confirm` | Overwrites the player files with what's in MySQL. |
| `/puuids mysql reconnect` | Retries the MySQL connection. |

These only work while `Settings.Debug` is `true`:

| Command | What it does |
| --- | --- |
| `/puuids debug` | Detailed system info. Include it in bug reports. |
| `/puuids reset ontime` | Sets every player's play time back to zero. **There's no confirmation step.** |
| `/puuids reset all` | Deletes every plugin's data from every player file, keeping only the player details. Must be run in game by an operator who also has `puuids.admin`, and confirmed with a code within 10 seconds. |
| `/puuids togglesave` | Freezes or unfreezes saving. While frozen, changes pile up in memory and aren't written. Same in-game confirmation as `reset all`. |

## Health (`/puuids info`)

The health rating is **GREAT**, **FAIR** or **POOR**, with a reason when there's a problem. Common reasons:

- **Unknown files in the `Data` folder.** Something other than player files was put there. Move those files out.
- **Improper reload.** PUUIDs was reloaded with players online. Restart the server.
- **A plugin tried to connect twice.** That's a bug in the named plugin, not in PUUIDs.
- **Slow saves.** Individual saves taking more than 10 ms, or a run of the queue taking more than 650 ms. Usually a slow disk. Lowering `Max-Processes-Per-Queue` spreads the work out.

## Inactivity clean-up

At every startup, PUUIDs deletes the files of players who haven't played for `Max-Days` days (365 by default). **The file holds every plugin's data for that player, so all of it is deleted together.**

```yaml
Settings:
  File-Cleanup:
    Enabled: true
    Clean-Essentials: true
    Max-Days: 365
```

- Set `Enabled: false` to keep every file forever.
- With `Clean-Essentials: true` and EssentialsX installed, deleting a player's PUUIDs file also resets their EssentialsX user data (the same as `/essentials cleanup` does). **Banned players are skipped**, so their bans stay in place. Set it to `false` if you want EssentialsX data kept.

At the same startup scan, PUUIDs also deletes files that are corrupt (missing their `UUID`, `Username` or `Last-On`), and temporary files left behind by an interrupted save.

## Reloading

Don't reload PUUIDs. It keeps queued changes and play time sessions in memory, and a reload can lose or corrupt them.

- Players running `/reload`, `/rl` or `/restart` are stopped with a warning. Set `Advanced.Allow-Unsafe-Reloads: true` to allow those commands anyway (not recommended, and not supported).
- PUUIDs adds itself to PlugMan's ignore list when PlugMan is installed.
- Plugins can only connect to PUUIDs while the server is starting, so reloading a plugin that uses PUUIDs leaves it unable to save until the next restart.

## Backups

Either stop the server and copy `plugins/PUUIDs/Data/`, or turn on the [MySQL mirror](MySQL.md) and back up the database.

## config.yml reference

Most settings take effect with `/puuids reload`. `Settings.File-Cleanup`, `Settings.Metrics` and `Advanced.Allow-Post-Startup-Connections` need a restart.

### Settings

| Key | Default | Meaning |
| --- | --- | --- |
| `Prefix` | `'&8[#4ec483&lPUUIDs&8]'` | Chat prefix. Supports `&` color codes and `#RRGGBB` hex colors. |
| `Update-Checking` | `true` | Checks GitHub for a newer release shortly after startup and every 24 hours after that, and tells admins when they join. |
| `Cooldowns.On-Time.Enabled` | `true` | Whether `/puuids ontime` has a cooldown for players. |
| `Cooldowns.On-Time.Seconds` | `5` | The length of that cooldown. |
| `File-Cleanup.Enabled` | `true` | See [Inactivity clean-up](#inactivity-clean-up). |
| `File-Cleanup.Clean-Essentials` | `true` | See [Inactivity clean-up](#inactivity-clean-up). |
| `File-Cleanup.Max-Days` | `365` | Days without playing before a file is deleted. At least 1. |
| `Metrics` | `true` | Sends anonymous usage statistics to bStats. |
| `Debug` | `false` | Logs every save in detail and enables the debug commands. |

### Messages

| Key | Default | Meaning |
| --- | --- | --- |
| `No-Permission` | `'&c&lSorry! &fYou are not able to do that.'` | Shown to players without `puuids.admin`. |

### MySQL

See [MySQL](MySQL.md) for how the mirror works. Numbers outside the allowed range fall back to the default.

| Key | Default | Meaning |
| --- | --- | --- |
| `Enabled` | `false` | Turns the mirror on. |
| `Host`, `Port`, `Database` | `localhost`, `3306`, `minecraft` | Where to connect. |
| `Username`, `Password` | `root`, `''` | Login details. Create a dedicated database user rather than using `root`. |
| `Table-Prefix` | `'puuids_'` | Start of every table name. Letters, numbers and `_` only, up to 24 characters. |
| `Use-SSL` | `false` | Encrypt the connection. |
| `Extra-Properties` | `''` | Extra JDBC driver properties, e.g. `'allowPublicKeyRetrieval=true&serverTimezone=UTC'`. MySQL 8's default login method needs `allowPublicKeyRetrieval=true` when SSL is off. |
| `Connection-Timeout-Seconds` | `10` | 1 to 120. Startup waits this long for the database, at most once. |
| `Pool-Size` | `3` | Database connections to keep open, 1 to 16. |
| `Flush-Rate-Ms` | `1000` | How often queued changes are sent, 100 to 600000. |
| `Max-Queued-Writes` | `100000` | How many changes to hold while the database is down, 1000 to 5000000. The oldest are dropped after that. |
| `Import-On-Startup` | `false` | Rebuild the `Data` folder from the database at startup. **Overwrites local files.** |
| `Export-On-Startup` | `false` | Copy the whole `Data` folder to the database at startup. |
| `Sync-On-Join` | `false` | Refresh a player's file from the database as they join. For networks. |

### Advanced

Only change these if you know you need to.

| Key | Default | Meaning |
| --- | --- | --- |
| `Save-Rate-Ticks` | `10` | How often the save queue runs, in ticks (20 ticks is one second). |
| `Max-Processes-Per-Queue` | `25` | The most plugin writes saved per run. Raise it if `/puuids info` shows a growing queue. Lower it if single runs are slow. |
| `Player-Update-Seconds` | `300` | How often online players' details and play time are checkpointed. At least 5. A normal quit always saves play time exactly. This only limits what a crash can lose. |
| `Allow-Post-Startup-Connections` | `false` | Let plugins connect after startup. Useful while developing with a plugin manager. |
| `Allow-Unsafe-Reloads` | `false` | Allow `/reload`, `/rl` and `/restart`. See [Reloading](#reloading). |
| `UUID` | generated | This server's ID, created on first run. Plugins can read it with `PUUIDS.getServerId()`. Leave it alone. |

With the defaults, the queue saves up to 50 plugin writes per second. PUUIDs' own player updates don't count towards that.

## Privacy

PUUIDs stores each player's username, last IP address, last seen time and play time. If your server needs to honour data removal requests, delete the player's file from `plugins/PUUIDs/Data/` while the server is stopped (and their rows from MySQL if you use it).

## Reporting a problem

1. Set `Settings.Debug: true` and run `/puuids reload`.
2. Run `/puuids debug` from the console.
3. Open an issue on GitHub with the output, or ask in the Discord at <https://zachduda.com/discord>.

Next: [Troubleshooting](Troubleshooting.md)

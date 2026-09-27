# MySQL

PUUIDs can mirror its `Data` folder into a MySQL or MariaDB database. It's off by default.

## How it fits in

**The YAML files stay in charge.** Every API read still comes from the files, and every write still goes to the files first. After a batch is saved to disk, PUUIDs queues the same changes for the database, and a separate background thread sends them every `Flush-Rate-Ms`. As a result:

- Turning MySQL on changes nothing about how your plugin uses the API.
- A slow or unreachable database never slows down or blocks saving.
- The database can be a little behind the files, typically by about a second.

What the mirror gives you is a single place to back up, to query from outside Minecraft (a website, a Discord bot), and to share player data between servers on a network.

## Turning it on

In `plugins/PUUIDs/config.yml`:

```yaml
MySQL:
  Enabled: true
  Host: localhost
  Port: 3306
  Database: minecraft
  Username: minecraft
  Password: 'change-me'
```

Then run `/puuids reload` or restart. PUUIDs creates its tables on the first connection, and `/puuids mysql export` copies existing files up.

The server needs a MySQL or MariaDB JDBC driver on its classpath. Most Spigot and Paper builds include one. If yours doesn't, PUUIDs says so in the console and keeps saving to files only. [Server Owners](Server-Owners.md#mysql) lists every MySQL option.

## Schema

All table names start with `Table-Prefix` (`puuids_` by default).

**`puuids_players`**: one row per player, matching the top-level keys of their file.

| Column | Type | Contents |
| --- | --- | --- |
| `uuid` | `CHAR(36)` | Primary key. |
| `username` | `VARCHAR(16)` | Indexed. |
| `ip` | `VARCHAR(45)` | |
| `last_on` | `BIGINT` | Epoch milliseconds. |
| `time_played` | `BIGINT` | Seconds. |
| `updated` | `BIGINT` | When this row was last written, epoch milliseconds. |

**`puuids_plugins`**: which table holds each plugin's data.

| Column | Type | Contents |
| --- | --- | --- |
| `plugin` | `VARCHAR(64)` | Primary key. The plugin name in upper case, as it appears in the files. |
| `table_name` | `VARCHAR(64)` | The data table for that plugin. |
| `updated` | `BIGINT` | |

**`puuids_data_<plugin>`**: one table per plugin, created the first time that plugin's data is mirrored. The plugin name is lower-cased and anything other than letters, digits and `_` becomes `_`. Very long names are shortened and given a hash suffix, so use `puuids_plugins` to find the table rather than guessing.

| Column | Type | Contents |
| --- | --- | --- |
| `uuid` | `CHAR(36)` | Part of the primary key. |
| `path` | `VARCHAR(191)` | Part of the primary key. The full path under your plugin's section, e.g. `Stats.Kills`. |
| `value` | `MEDIUMTEXT` | The value, encoded as YAML (see below). |
| `updated` | `BIGINT` | When this row was last written, epoch milliseconds. |

### How values are stored

- **Only leaf values get rows.** A section is split into one row per value inside it. Saving a location under `Home` produces rows for `Home.X`, `Home.Y`, `Home.Z`, `Home.Pitch`, `Home.Yaw` and `Home.World`.
- **Each value is a small YAML document** with a single key, `v`:

  | Saved with | `value` column |
  | --- | --- |
  | `set(..., "Coins", 150)` | `v: 150` |
  | `set(..., "Title", "The Brave")` | `v: The Brave` |
  | `set(..., "Muted", true)` | `v: true` |
  | `set(..., "Titles", List.of("A", "B"))` | `v:` followed by `- A` and `- B` on their own lines |

  Parse it with any YAML library and read the `v` key. Strings may be quoted when YAML requires it.
- **Removing a value deletes its row**, and the rows of everything nested under it. `setNull(plugin, uuid)` deletes all of that player's rows in your plugin's table.
- **Paths longer than 191 characters are not mirrored.** They are still saved to the file, and PUUIDs logs a warning.

## Querying the data yourself

Treat the database as **read-only** from outside Minecraft. Anything you write directly is ignored by the files, and is overwritten the next time the server saves that value. It would only reach the files through an import or a join sync.

Top ten coin balances for a plugin named `Coins`:

```sql
SELECT p.username,
       CAST(TRIM(REPLACE(REPLACE(d.value, 'v:', ''), '\n', '')) AS SIGNED) AS coins
FROM puuids_data_coins d
JOIN puuids_players p ON p.uuid = d.uuid
WHERE d.path = 'Coins'
ORDER BY coins DESC
LIMIT 10;
```

The `REPLACE` and `TRIM` calls turn `v: 150` (plus its trailing newline) into `150`. That shortcut is fine for numbers. For strings and lists, parse the YAML in your application instead.

Recently active players:

```sql
SELECT username, FROM_UNIXTIME(last_on / 1000) AS last_seen, time_played / 3600 AS hours
FROM puuids_players
ORDER BY last_on DESC
LIMIT 50;
```

## Multi-server networks

Point every server at the same database and turn on `Sync-On-Join`. When a player joins, PUUIDs fetches their row and all of their plugin rows in the background, and writes them into the local file:

- `Username`, `IP` and `Last-On` are only taken from the database if it has seen the player more recently than the local file has.
- `Time-Played` takes whichever of the two is larger.
- Plugin values found in the database replace the local ones. Local values the database doesn't have are left alone.

**What this means for your plugin:**

- **The sync runs in the background, and there's no event for when it finishes.** A read in `PlayerJoinEvent` may run before the sync lands and see this server's older copy. If your data has to follow players between servers, read it a little later (a delay of a second or so is plenty on a healthy database), or read it again when the player first uses your feature.
- **In-memory caches must not outlive the player's session.** A value cached from a previous visit ignores anything that changed on another server since. Reload on join, and only evict after your last write for that player has been saved (see [Patterns](Patterns.md#cache-values-for-online-players)).
- **The last write wins.** If two servers change the same value for the same player, whichever reaches the database last is kept. Usually a player is only on one server at a time, so this rarely matters.

## When the database is unavailable

- PUUIDs keeps saving to files as normal.
- Changes for the database wait in memory, up to `Max-Queued-Writes`. After that, the oldest are dropped and a warning is logged. Nothing is lost from the files.
- `/puuids mysql reconnect` retries the connection. Once it's back, run `/puuids mysql export` to bring the database up to date.

## Import and export

| Action | What it does |
| --- | --- |
| `/puuids mysql export` or `Export-On-Startup: true` | Copies every file in the `Data` folder to the database. Use it when first turning MySQL on, or after an outage. |
| `/puuids mysql import confirm` or `Import-On-Startup: true` | Rebuilds the `Data` folder from the database, **overwriting** local values. Use it for a new server joining a network, or to restore a backup. Saving is paused while it runs. |

Next: [Server Owners](Server-Owners.md)

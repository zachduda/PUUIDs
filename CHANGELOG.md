# Changelog

## 5.0.1 (unreleased)

A stability and performance release. Nothing to configure; existing `Data` folders, `config.yml` files and MySQL tables carry over.

### Fixes that protect player data
- **One bad value can no longer make a player's file unreadable.** In 5.0.0, saving a `UUID`, an enum or any plain Java object with `set()` wrote a value Bukkit refuses to read back, so the whole file looked empty. Its next save then started the player over. Values are now checked when they're queued: UUIDs and enums are saved as their String form, and anything that can't be read back is refused with a console warning (the call returns `0`).
- **Files that are already broken are repaired, not reset.** Reads recover everything they can from a file Bukkit can't parse, and its next save rewrites it in a readable form. A copy of the original still goes to the `Corrupt` folder.
- **The start-up scan no longer deletes files.** It used to delete any file it couldn't parse, and any file without a `Username` or `Last-On` (for example, data a plugin saved for a player who hasn't joined since). Both are now kept. Only empty files and files past `File-Cleanup.Max-Days` are removed, and a `Last-On` in the future no longer counts as old.
- **`/puuids reset ontime` sticks.** The start-up scan raised everyone's play time to Minecraft's own statistic on every start, undoing the reset. That sync now happens once per player and is marked in the file (`Playtime-Synced: true`).
- **No more play time for players who have left.** A checkpoint that ran just after a quit could restart the session, so `getPlayTime` kept growing for an offline player until they rejoined.
- **Failed saves are kept, not dropped after 60 seconds.** A full disk or locked file is retried with a growing gap (up to 30s) for as long as the server runs, with a console update every 5 minutes. A value that can't be written is dropped on its own, so the rest of that player's changes (play time included) still save.
- **Shutdown waits for a save in progress** (up to 30s instead of 1s), so changes always land in order.
- The reset commands always release their pause on saving, even if they fail part-way.
- `setLocation` with no world saves nothing, instead of four of the six values.

### MySQL
- **Sync-On-Join now runs before the join** (during pre-login), so plugins never read or write the stale file. Whichever copy was saved last wins, plugin values included. Before, only the core fields followed that rule. Logins wait for it, up to `Connection-Timeout-Seconds` per query, and skip it while the database is unreachable.
- **Paths are case-sensitive.** `Homes.base` and `homes.base` were the same row, and removing `homes` also removed `Homes`. Existing data tables are converted once, the first time each is written to.
- **The mirror matches the file exactly.** Setting a section where a value used to be, or writing under a former value, could leave stale rows that an import would bring back.
- A table that can't be created no longer stops all mirroring, and plugin names that clean up to the same table name (`MY-PLUGIN`, `MY_PLUGIN`) now get separate tables.
- An import ignores player rows whose `uuid` isn't a valid file name, and registry entries with an invalid table name.
- Connections are only checked when they have been idle for 30s, instead of a round trip before every use.

### Performance
- **Faster start-up.** The scan reads files as plain data (no ItemStacks or Locations are built) and only looks up Minecraft's statistic once per player, instead of re-saving most files on every start.
- **Higher save throughput.** While there is a backlog, a save run keeps writing batches of `Advanced.Max-Processes-Per-Queue` files for up to half of `Save-Rate-Ticks`. Before, it stopped after one batch.
- **Checkpoints are spread out.** Each online player is checkpointed `Player-Update-Seconds` after their last save, instead of every player at once.
- Repeated writes to the same path within one save are merged.
- `get()` copies values from memory instead of re-reading the file. `hasFile` and `getName` answer online players without touching the disk. `addToAllWithout` reads each file once instead of twice.

### Behaviour changes
| What | 5.0.0 | 5.0.1 |
|---|---|---|
| `set()` with a UUID or enum | saved, then the file couldn't be read | saved as its String form |
| `set()` with any other object Bukkit can't read back | saved, then the file couldn't be read | refused with a warning, returns `0` |
| `get()`, `getNCList` | lists copied one level deep | copied all the way down |
| `getAllPlayerNames`, `getAllWith*Boolean` | could include `null` | skip files with no name yet |
| `UpdatedPlayerStats` | after checkpointing every online player | still every interval; each player is checkpointed within it |
| `Settings.File-Cleanup.Clean-Essentials` default | `true` | `false` for new installs (existing `config.yml` files keep their value) |
| Play time sessions | measured with the system clock | measured with a monotonic clock, so clock changes can't skew them |

## 5.0.0

### Before you upgrade

**Server owners**
- **Java 21 is required.** PUUIDs is now built for Java 21, so it runs on Minecraft 1.20.5 and newer. Older versions only work if the server itself runs on Java 21.
- Your existing `Data` folder and `config.yml` carry over. New config keys are added with their defaults (see *New config options* below).
- `/reload` and `/rl` are blocked unless `Advanced.Allow-Unsafe-Reloads` is `true`.

**Developers**
- **New Maven repository and coordinates.** JitPack and GitHub Packages are no longer published to.
  ```xml
  <repository>
      <id>zachduda</id>
      <url>https://maven.zachduda.com/releases</url>
  </repository>

  <dependency>
      <groupId>com.zachduda</groupId>
      <artifactId>puuids</artifactId>
      <version>5.0.0</version>
      <scope>provided</scope>
  </dependency>
  ```
  The old coordinates were `com.github.zachduda:PUUIDs`.
- **Connect with `APIVersion.V5`.** `V4` is still accepted with full support, so existing plugins keep working without a rebuild. `V1` to `V3` are still rejected.
- **Only `com.zachduda.puuids.api` is public API.** `Main` and the `storage` package are internal and can change in any release. If you used `Main.getPlugin(Main.class).getTimes`, it is now an `AtomicInteger` instead of an `int`.

### API behaviour changes

No public API method was removed or changed signature, but these behave differently:

| Call | 4.x | 5.0.0 |
|---|---|---|
| `getAllPlayerNames`, `getAllPlayerUUIDs` when your plugin isn't connected | returned `null` | return an empty list |
| Any getter straight after a `set` | re-read the file from disk | served from an in-memory cache; until the save queue reaches the write, you can still get the old value. Listen for `TimerSaved` if you need to know it's on disk. |
| `getPlayTime` | the saved total only | includes the current session of an online player |
| `getFormatedPlayTime`, `secsToFormatTime` | one unit (`"3 hours"`), and the play time was wrongly divided by 100 | two units (`"3 hours, 12 minutes"`), from the correct number of seconds |
| `getIP` | always the last IP | `"0"` if the server sets `Settings.Store-IPs: false` |
| `getItemStack`, `getNCList` | the stored object | a copy; changing it no longer changes the cached data |
| `addToAllWithout` | saved each file from a main-thread task | queues a normal write for each player missing the value |
| `removeFromIntList` | added the value instead of removing it | removes the first matching entry |
| `getLocation` | pitch and yaw were read back as whole numbers | full precision |

Events are unchanged, but their Javadoc now says which thread they fire on. Note that `ConnectionOpen` and `ConnectionClose` usually fire before your plugin's listeners are registered or after it has been disabled. Connect from `onEnable()` and save from your own `onDisable()` instead of relying on them.

### New
- **Optional MySQL mirror** (`MySQL:` section). The `.yml` files stay the source of truth, and writes are copied to MySQL/MariaDB in the background. Includes import/export on start-up, `/puuids mysql export`, and an opt-in refresh on join for networks.
- **In-memory player file cache.** Online players' files are kept loaded, and recently used ones are cached, so most reads never touch the disk.
- Paper and Folia scheduling through morepaperlib 0.5.3, which is now relocated inside the jar.

### New config options
| Key | Default | Purpose |
|---|---|---|
| `Settings.Store-IPs` | `true` | Save each player's last IP. When off, saved IPs are removed as players are next seen. |
| `Advanced.Cache-Size` | `500` | Player files kept in memory on top of online players'. `0` turns it off. |
| `Advanced.Cache-Revalidate-Seconds` | `5` | How often a cached file is checked against disk, which only matters for hand edits. |
| `Advanced.Player-Update-Seconds` | `300` | Time between checkpoints of online players' stats. This was a fixed 10 minutes before. |
| `MySQL.*` | disabled | See the comments in `config.yml`. |

### Fixes
- Play time accounting.
- Hardened the save pipeline: unreadable files are moved to a `Corrupt` folder instead of being overwritten, and `/puuids reset` no longer wipes files it couldn't read.
- Minecraft server version detection.

# Changelog

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
- **Optional MySQL mirror** (`MySQL:` section). The `.yml` files stay the source of truth, and writes are copied to MySQL/MariaDB in the background. Includes import/export on start-up, `/puuids mysql export`, and an opt-in refresh for networks (`Sync-On-Join`): a player's file is pulled from MySQL as they log in, in `AsyncPlayerPreLoginEvent` before any other plugin's listener, so plugins loading data there or later see what other servers saved. A local file written more recently than MySQL's copy is kept, and a login waits at most `Connection-Timeout-Seconds` for it.
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

[![Discord](https://img.shields.io/discord/469625341837836290?style=flat-square&logo=Discord&logoColor=bdc7fc&label=Support%20Discord)](https://zachduda.com/discord?utm=github_badge)  [![Build Status](https://ci.zachduda.com/job/ChatFeelings/badge/icon)](https://ci.zachduda.com/job/PUUIDs/)
![Alt text](Images/banner.png?raw=true "PUUIDs Banner")
Async per player file saving: Made Easy!

# API
Ready to get started? Check out: [Setting Your Data](https://github.com/zachduda/PUUIDs/wiki/Start-Setting-Data).

# Soft-Depend in plugin.yml
Make sure that you add PUUIDs as a soft-depend plugin like so:
```yaml
soft-depend: [PUUIDs]
```
# Using Maven
If you haven't already, make sure the maven repo is listed:
```xml
<repository>
    <id>zachduda</id>
    <url>https://zachduda.com/maven</url>
</repository>
```

Then add PUUIDs dependency from Github:

```xml
<dependency>
  <groupId>com.zachduda</groupId>
  <artifactId>PUUIDs</artifactId>
  <version>4.0.0</version>
</dependency>
```


# MySQL
PUUIDs can mirror everything in its `Data` folder into MySQL (or MariaDB). It's off by default - turn it on under `MySQL` in your `config.yml`:

```yaml
MySQL:
  Enabled: true
  Host: localhost
  Port: 3306
  Database: minecraft
  Username: root
  Password: ''
```

The `.yml` files stay in charge. Every API read still comes off disk, so switching this on can't slow a plugin down or fail because the database is busy - each write is simply copied up in the background as well. That gives you one place to back up or query, and a way to share player data between the servers on a network.

### How it's laid out
Every plugin that stores data gets its own table, so you can look at one plugin's rows on their own (and drop the table when you stop using it):

| Table | What's in it |
| --- | --- |
| `puuids_players` | One row per player: username, IP, last seen, total play time. |
| `puuids_plugins` | Which table belongs to which plugin. |
| `puuids_data_<plugin>` | One row per value that plugin has stored: `uuid`, `path`, `value`. |

Values keep their type on the round trip - an int comes back an int, a list comes back a list, and ItemStacks come back as themselves.

### Commands
| Command | What it does |
| --- | --- |
| `/puuids mysql` | Connection state, queue depth, and anything that has gone wrong. |
| `/puuids mysql export` | Pushes the whole `Data` folder up. Run this the first time you switch MySQL on. |
| `/puuids mysql import confirm` | Pulls everything down, overwriting local values with the database's. |
| `/puuids mysql reconnect` | Retries a connection that was down at start-up. |

### For a network
Set `Sync-On-Join: true` and a player's file is refreshed from the shared database as they join, so their data follows them between servers. A file that has seen them more recently than the database wins, so nothing is lost when they hop back. On a brand new server, `Import-On-Startup: true` builds the folder from the database before anything reads it.

If the database goes away, puuids keeps saving to file and queues the changes; they're sent as soon as it comes back. Your server needs a MySQL or MariaDB JDBC driver on its classpath - most Spigot and Paper builds ship one, and puuids says so in the console if yours doesn't.

# Spigot
PUUIDs is a Spigot plugin for MC versions 1.13-1.21. Please check out the [Spigot Page](https://www.spigotmc.org/resources/puuids-•-an-async-file-api.71496/). for full documentation.


# License
This project is licensed under [Creative Commons (CC-BY-NC-4)](https://creativecommons.org/licenses/by-nc/4.0/).
You can do whatever you'd like: just give credit and make sure it's non-commercial.


# Need a hand?
Feel free to join the developer discord server and ask me for help with PUUIDs! I'd be happy to answer any questions you might have or implement any functionality you feel would assist you with your projects: https://zachduda.com/discord


# Contact Me
If you have any questions or inquiries, you can reach me at https://zachduda.com/contact

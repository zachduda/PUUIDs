[![Discord](https://img.shields.io/discord/469625341837836290?style=flat-square&logo=Discord&logoColor=bdc7fc&label=Support%20Discord)](https://zachduda.com/discord?utm=github_badge)  [![Build Status](https://ci.zachduda.com/job/ChatFeelings/badge/icon)](https://ci.zachduda.com/job/PUUIDs/)
![Alt text](Images/banner.png?raw=true "PUUIDs Banner")
Async per player file saving: Made Easy!

# Documentation
Everything is in the [wiki](https://github.com/zachduda/PUUIDs/wiki): getting started, storing and reading data, how saving works, the full API reference, and config, commands and permissions for server owners.

Upgrading from v4? Read the [5.0.0 changelog](CHANGELOG.md#500) first.

# Quick Start
Add the repository and dependency. Use `provided` scope, since PUUIDs is already installed on the server:
```xml
<repository>
    <id>zachduda</id>
    <url>https://maven.zachduda.com/releases</url>
</repository>
```

```xml
<dependency>
    <groupId>com.zachduda</groupId>
    <artifactId>puuids</artifactId>
    <version>5.0.0</version>
    <scope>provided</scope>
</dependency>
```

Gradle users can add `maven("https://maven.zachduda.com/releases")` and `compileOnly("com.zachduda:puuids:5.0.0")`.

Declare PUUIDs in your `plugin.yml`, using `depend` if your plugin needs it or `softdepend` if it's optional:
```yaml
depend: [PUUIDs]
```

Then connect in `onEnable`, and start saving:
```java
@Override
public void onEnable() {
    PUUIDS.connect(this, PUUIDS.APIVersion.V5);
}

public void saveTitle(Player player, String title) {
    PUUIDS.set(this, player.getUniqueId().toString(), "Title", title);
}
```

# Spigot
PUUIDs is a Spigot plugin that also runs on Paper and Folia. It requires Java 21, so it is supported on Minecraft 1.20.5 and newer; older versions (back to 1.13) only work where the server itself can be started on Java 21. Check out the [Spigot Page](https://www.spigotmc.org/resources/puuids-•-an-async-file-api.71496/) for downloads.


# License
This project is licensed under [Creative Commons (CC-BY-NC-4)](https://creativecommons.org/licenses/by-nc/4.0/).
You can do whatever you'd like: just give credit and make sure it's non-commercial.


# Need a hand?
Feel free to join the developer discord server and ask me for help with PUUIDs! I'd be happy to answer any questions you might have or implement any functionality you feel would assist you with your projects: https://zachduda.com/discord


# Contact Me
If you have any questions or inquiries, you can reach me at https://zachduda.com/contact

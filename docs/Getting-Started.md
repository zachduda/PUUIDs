# Getting Started

This page takes you from an empty project to a plugin that is connected to PUUIDs and saving data.

## Requirements

- **Server:** Spigot, Paper or Folia on Minecraft 1.13 or newer, with the PUUIDs jar in `plugins/`.
- **Java:** PUUIDs 4.x is compiled for Java 21. The server has to run on Java 21 or newer, so older Minecraft versions only work if their server software runs on Java 21. You also need JDK 21 or newer to build against PUUIDs. An older JDK fails with `class file has wrong version 65.0`.

## 1. Add the dependency

PUUIDs is published to its own Maven repository.

**Maven**

```xml
<repositories>
    <repository>
        <id>zachduda</id>
        <url>https://maven.zachduda.com/releases</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.zachduda</groupId>
        <artifactId>puuids</artifactId>
        <version>4.0.1</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**Gradle (Kotlin DSL)**

```kotlin
repositories {
    maven("https://maven.zachduda.com/releases")
}

dependencies {
    compileOnly("com.zachduda:puuids:4.0.1")
}
```

**Gradle (Groovy DSL)**

```groovy
repositories {
    maven { url 'https://maven.zachduda.com/releases' }
}

dependencies {
    compileOnly 'com.zachduda:puuids:4.0.1'
}
```

Replace `4.0.1` with the PUUIDs release you are targeting.

> [!WARNING]
> Always use `provided` (Maven) or `compileOnly` (Gradle). PUUIDs is a plugin that is already running on the server. If you shade it into your jar, your plugin ends up talking to a copy of the API that isn't connected to anything.

## 2. Declare the dependency

Tell the server that your plugin needs PUUIDs, so PUUIDs is enabled first.

**plugin.yml**, if your plugin can't work without PUUIDs:

```yaml
depend: [PUUIDs]
```

or, if PUUIDs is optional:

```yaml
softdepend: [PUUIDs]
```

The key is `softdepend`, with no hyphen. Bukkit silently ignores `soft-depend`, which means the load order is no longer guaranteed.

**paper-plugin.yml**, if you use Paper's plugin format:

```yaml
dependencies:
  server:
    PUUIDs:
      load: BEFORE
      required: true      # false if PUUIDs is optional
      join-classpath: true
```

## 3. Connect in `onEnable`

Before you can write anything, your plugin has to register with PUUIDs. This is a one-line handshake:

```java
import com.zachduda.puuids.api.PUUIDS;
import com.zachduda.puuids.api.PUUIDS.APIVersion;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        if (!PUUIDS.connect(this, APIVersion.V4)) {
            getLogger().severe("Couldn't connect to PUUIDs. Player data won't be saved.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register listeners, commands, and so on.
    }
}
```

The handshake is how PUUIDs knows which plugins may write data. Every write method, and a few of the read methods, reject a plugin that hasn't connected (see the [API Reference](API-Reference.md) for which ones).

### Things to know about `connect`

- **Call it from `onEnable`.** PUUIDs only accepts connections while the server is starting up. The window opens when PUUIDs enables and closes on the server's first tick, once every plugin has been enabled. A later call returns `false` and logs `tried to register with puuids after the connection window`. Server owners can lift that limit with `Advanced.Allow-Post-Startup-Connections`, but you shouldn't rely on it.
- **Call it once.** Connecting the same plugin a second time returns `false`. During startup it also logs a warning and marks PUUIDs' health as poor in `/puuids info`.
- **Pass `APIVersion.V4`.** It is the only version this release accepts. `V1` to `V3` are deprecated and refused, with a `using an outdated version` error in the console. V4 changed how locations are stored: they now include the world.
- **Hot reloads break it.** Reloading your plugin with PlugMan or a similar tool creates a new plugin instance after the window has closed, so it can't connect again until the server restarts. Restart instead of reloading.

You can check which plugins are connected with `/puuids plugins`.

## 4. Save and read something

Every PUUIDs method identifies players by their UUID **as a String**, in the standard form that `UUID.toString()` produces:

```java
String uuid = player.getUniqueId().toString();

PUUIDS.set(this, uuid, "Nickname", "Captain");      // queue a write
String nickname = PUUIDS.getString(this, uuid, "Nickname");  // read from the file
```

Paths work just like `getConfig()` paths, so `"Settings.Chat-Sounds"` creates a nested section. Continue with [Storing Data](Storing-Data.md) and [Reading Data](Reading-Data.md) for the details.

## Making PUUIDs optional

If you use `softdepend`, your plugin has to cope with PUUIDs not being installed. Any code that touches the `PUUIDS` class throws `NoClassDefFoundError` when PUUIDs is missing, so keep every reference to it inside one class that you only create after checking:

```java
import com.zachduda.puuids.api.PUUIDS;
import com.zachduda.puuids.api.PUUIDS.APIVersion;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** The only class in the plugin that references PUUIDs. */
final class PuuidsHook {

    private final Plugin plugin;

    PuuidsHook(Plugin plugin) {
        this.plugin = plugin;
    }

    boolean connect() {
        return PUUIDS.connect(plugin, APIVersion.V4);
    }

    void saveNickname(UUID player, String nickname) {
        PUUIDS.set(plugin, player.toString(), "Nickname", nickname);
    }

    String loadNickname(UUID player) {
        return PUUIDS.getString(plugin, player.toString(), "Nickname");
    }
}
```

```java
public final class MyPlugin extends JavaPlugin {

    private PuuidsHook puuids; // stays null when PUUIDs isn't available

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().isPluginEnabled("PUUIDs")) {
            PuuidsHook hook = new PuuidsHook(this);
            if (hook.connect()) {
                puuids = hook;
            }
        }

        if (puuids == null) {
            getLogger().info("PUUIDs not found, nicknames won't be saved.");
        }
    }
}
```

The rest of your plugin then checks `puuids != null` before saving or loading.

## Next steps

- [Storing Data](Storing-Data.md) covers everything you can pass to `set`.
- [How Saving Works](How-Saving-Works.md) explains the timing of writes, which affects how you should structure your code.

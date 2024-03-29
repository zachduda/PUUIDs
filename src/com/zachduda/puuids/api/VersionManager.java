package com.zachduda.puuids.api;

import com.zachduda.puuids.api.PUUIDS.APIVersion;

public class VersionManager {

    @SuppressWarnings("deprecation")
    public static VersionTest checks(String pl, APIVersion version) {
        if (version == APIVersion.V1) {
            return VersionTest.FAIL;
        }

        if (version == APIVersion.V2) {
            // Added new Async settings and deprecated name to UUID mojang look up.
            return VersionTest.FAIL;
        }

        if (version == APIVersion.V3) {
            // Removed Result ERR/Success return and changed to task id.
            return VersionTest.FAIL;
        }

        if (version == APIVersion.V4) {
            // Changed Location save / get calls to use the world properly
            return VersionTest.PASS;
        }

        return VersionTest.FAIL;
    }

    public static enum VersionTest {
        FAIL, // Plugin is not at all compatible with this version.
        LEGACY, // puuids will try it's best to make it work, but the plugin isn't running the native latest.
        PASS // puuids and the plugin should work just fine!
    }
}

package com.zach_attack.puuids.api;

import com.zach_attack.puuids.api.PUUIDS.APIVersion;

public class VersionManager {
	
	public static enum VersionTest {
		 FAIL, // Plugin is not at all compatible with this version.
		 LEGACY, // PUUIDs will try it's best to make it work, but the plugin isn't running the native latest.
		 PASS // PUUIDs and the plugin should work just fine!
	}
	
	@SuppressWarnings("deprecation")
	public static VersionTest checks(String pl, APIVersion version) {
		if(version == APIVersion.V1) {
			return VersionTest.LEGACY;
		}
		
		if(version == APIVersion.V2) {
			// Added new Async settings and deprecated nametoUUID mojang look up.
			return VersionTest.PASS;
		}
		
		return VersionTest.FAIL;
	}
}

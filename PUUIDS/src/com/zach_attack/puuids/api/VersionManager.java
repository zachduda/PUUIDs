package com.zach_attack.puuids.api;

import java.util.logging.Logger;

import org.bukkit.Bukkit;

import com.zach_attack.puuids.api.PUUIDS.APIVersion;

public class VersionManager {

	private static Logger log = Bukkit.getLogger();
	
	private static void log(String msg) {
		log.info(msg);
	}
	
	public static enum VersionTest {
		 FAIL, // Plugin is not at all compatible with this version.
		 LEGACY, // PUUIDs will try it's best to make it work, but the plugin isn't running the native latest.
		 PASS // PUUIDs and the plugin should work just fine!
	}
	
	public static VersionTest checks(String pl, APIVersion version) {
		if(version == APIVersion.V1) {
			// Running the Latest Version
			return VersionTest.PASS;
		}
		
		log("Plugin " + pl + " has not updated their plugin to fully function with this version of PUUIDs yet.");
		return VersionTest.FAIL;
	}
}

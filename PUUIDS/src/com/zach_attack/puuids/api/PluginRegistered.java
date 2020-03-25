package com.zach_attack.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PluginRegistered extends Event {

	// Event that is Fired when a plugin is registered with PUUIDs
	
	
	private final String plname;

    public PluginRegistered(String plname) {
    	this.plname = plname;
    }
    
    private static final HandlerList HANDLERS = new HandlerList();

    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
    
    public String getPlugin() {
        return this.plname;
    }

}

package com.zach_attack.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ConnectionOpen extends Event {

	// Event that is Fired when PUUIDs has finished starting up & is accepting connections.
	
    public ConnectionOpen() {}
    
    private static final HandlerList HANDLERS = new HandlerList();

    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

}

package com.zachduda.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired asynchronously once puuids has finished starting up and is accepting connections.
 * <p>
 * puuids enables early, usually before other plugins have registered their listeners, so most plugins
 * never receive this event. Connect from {@code onEnable()} rather than waiting for it.
 */
public class ConnectionOpen extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public ConnectionOpen() {
        super(true);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public HandlerList getHandlers() {
        return HANDLERS;
    }

}

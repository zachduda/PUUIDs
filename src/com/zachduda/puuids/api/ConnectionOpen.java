package com.zachduda.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ConnectionOpen extends Event {

    // Event that is Fired when puuids has finished starting up & is accepting connections.

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

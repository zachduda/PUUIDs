package com.zachduda.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ConnectionClose extends Event {

    // Event that is Fired when puuids is shutting down.

    private static final HandlerList HANDLERS = new HandlerList();

    public ConnectionClose() {
        super(false);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public HandlerList getHandlers() {
        return HANDLERS;
    }

}

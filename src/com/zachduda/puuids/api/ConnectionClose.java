package com.zachduda.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread when puuids starts shutting down, before it saves whatever is left in its
 * queue. Writes queued from a handler are still saved.
 * <p>
 * Plugins that depend on puuids are disabled before it, so they normally won't receive this event. Save
 * your data from your own {@code onDisable()} instead.
 */
public class ConnectionClose extends Event {

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

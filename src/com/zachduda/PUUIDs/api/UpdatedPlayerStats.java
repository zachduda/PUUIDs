package com.zachduda.PUUIDs.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class UpdatedPlayerStats extends Event {

    // Event that is Fired from a 10m timer that saves all online player stats every 10m (eg: playtime stat)

    private static final HandlerList HANDLERS = new HandlerList();

    public UpdatedPlayerStats() {
        super(true);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public HandlerList getHandlers() {
        return HANDLERS;
    }

}

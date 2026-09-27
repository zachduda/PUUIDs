package com.zachduda.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/**
 * Fired asynchronously every {@code Advanced.Player-Update-Seconds} (300 by default), right after puuids
 * has queued a checkpoint of every online player's details and play time. The checkpoint is queued at
 * this point, not necessarily saved yet.
 */
public class UpdatedPlayerStats extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public UpdatedPlayerStats() {
        super(true);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

}

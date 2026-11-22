package com.zachduda.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/**
 * Fired asynchronously every {@code Advanced.Player-Update-Seconds} (300 by default). By then, every
 * online player's details and play time have been checkpointed within the last interval.
 * <p>
 * Checkpoints are spread out: each player is checkpointed one interval after their last save, rather
 * than everyone at the moment this fires. A checkpoint is queued, not necessarily saved yet.
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

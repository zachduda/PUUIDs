package com.zachduda.puuids.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired asynchronously, from the thread that saves player files, once a player's file has been created.
 * That happens shortly after their first join, or their first join after the inactivity clean-up removed
 * their old file.
 * <p>
 * Schedule any work on the player back onto the right thread, and check that they are still online.
 */
public class OnNewFile extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player p;

    public OnNewFile(Player p) {
        super(true);
        this.p = p;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * @return The player whose file was created. They may have left by the time this event arrives.
     */
    public Player getPlayer() {
        return this.p;
    }

}

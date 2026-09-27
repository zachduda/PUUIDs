package com.zachduda.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/**
 * Fired asynchronously once a queued write has been saved to the player's file. It fires for every
 * plugin's writes, so check {@link #getPlugin()} before using {@link #getId()}.
 * <p>
 * It isn't fired for a write whose save failed, or for the final writes made while puuids shuts down.
 */
public class TimerSaved extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    // Event that is fired when a task is actually saved to file. You can get your Task ID from set and track it here!
    private final String plname;
    private final String uuid;
    private final int taskid;

    public TimerSaved(String plname, String uuid, int taskid) {
        super(true);
        this.plname = plname;
        this.taskid = taskid;
        this.uuid = uuid;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * @return The name of the plugin that queued the write, in upper case.
     */
    public String getPlugin() {
        return this.plname;
    }

    /**
     * @return The task id that was returned when the write was queued.
     */
    public int getId() {
        return this.taskid;
    }

    /**
     * @return The UUID of the player whose file was written.
     */
    public String getUUID() {
        return this.uuid;
    }

}

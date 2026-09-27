package com.zachduda.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired asynchronously when a plugin connects to puuids successfully.
 */
public class PluginRegistered extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String plname;

    public PluginRegistered(String plname) {
        super(true);
        this.plname = plname;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * @return The name of the plugin that connected, as written in its plugin.yml.
     */
    public String getPlugin() {
        return this.plname;
    }

}

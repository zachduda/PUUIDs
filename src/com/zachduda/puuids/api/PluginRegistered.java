package com.zachduda.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PluginRegistered extends Event {

    // Event that is Fired when a plugin is registered with puuids


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

    public String getPlugin() {
        return this.plname;
    }

}

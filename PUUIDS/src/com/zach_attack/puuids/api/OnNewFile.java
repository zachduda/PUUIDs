package com.zach_attack.puuids.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class OnNewFile extends Event {
	
	private final Player p;

    public OnNewFile(Player p) {
    	this.p = p;
    }
    
    private static final HandlerList HANDLERS = new HandlerList();

    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Player getPlayer() {
        return this.p;
    }

}

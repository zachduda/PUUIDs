package com.zach_attack.puuids.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TimerSaved extends Event {

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
    
    private static final HandlerList HANDLERS = new HandlerList();

    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
    
    public String getPlugin() {
        return this.plname;
    }
    
    public int getId() {
        return this.taskid;
    }
    
    public String getUUID() {
    	return this.uuid;
    }

}

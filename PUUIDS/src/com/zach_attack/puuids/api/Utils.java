package com.zach_attack.puuids.api;

import java.util.HashMap;

import org.bukkit.entity.Player;


public class Utils {
	private static int setTimes = 0;
	private static int getTimes = 0;
	
	private static int queueSize = 0;
	
	public static int getSetTimes() {
		return setTimes;
	}
	
	public static int getGetTimes() {
		return getTimes;
	}
	
	static void addSetTimes() {
		setTimes++;
	}
	
	static void addGetTimes() {
		getTimes++;
	}
	
	 
	private final HashMap<String,Integer> player_index = new HashMap<String,Integer>();
	private String[] player_queue = new String[queueSize];
	 
	/**
	  *
	  * @return    Next available spot in the queue.
	  */
	private int getNextSpot(){
	  int freeIndex = 0;
	  int i;
	  for(i = 0; i <= queueSize; i++){
	  if(player_queue[i] == "" | player_queue[i] == null){
	    freeIndex = i;
	    continue; // breakpoint
	  }
	  }
	 
	  return freeIndex;
	}
	/**
	  *
	  * @return    True if there is a spot in the queue, false if there is no space in the queue.
	  */
	private boolean hasNextSpot(){
	  if(getNextSpot() > queueSize) return false;
	  else return true;
	}
	 
	/**
	  * Adds a player (by name) to the queue and flags them for selection.
	  * @param p    Player to add to the queue
	  *
	  * @return    If the player was added to the queue, return true else return false.
	  */
	public boolean queue_add(String pName){
	  if(hasNextSpot()){
	  int nextSpot = getNextSpot();
	  player_queue[nextSpot] = pName;
	  player_index.put(pName, nextSpot);
	  return true;
	  }
	  else return false;
	}
	 
	/**
	  * Removes a player (by name) from the queue and unflags them for selection.
	  * @param p    Player to remove from the queue
	  *
	  * @return    If the player was removed from the queue, return true else return false.
	  */
	public boolean queue_rem(String pName){
	  if(hasNextSpot()){
	  player_queue[player_index.get(pName)] = "";
	  player_index.put(pName, -1);
	  return true;
	  }
	  else return false;
	}
	 
	/**
	  * This has to be called in an onPlayerJoin() or other similar method; creates the defaults for the player
	  * @param p
	  */
	public void queue_default(Player p){
	  player_index.put(p.getName(), -1); // -1 value prevents selection
	}
	 
	/**
	  * This is called when you want to select some people from the queue
	  * @return    Array of player names that have been selected from the queue
	  */
	public String[] queue_select(int amountToSelect){
	  String[] selected = new String[amountToSelect];
	 
	  int i;
	  int index = 0;
	  for(i = 0; i <= queueSize; i++){
	  if(index > amountToSelect) continue;
	 
	  if(player_queue[i] != null && player_queue[i] != ""){
	    // Player is in the queue
	    if(player_index.get(player_queue[i]) > 0){ // Check if they are flagged for selection
	    // Add them to the selected players
	    selected[index] = player_queue[index];
	 
	    // Remove them from the queue
	    queue_rem(player_queue[i]);
	 
	    // Increment up the selection index
	    index++;
	    }
	  }
	  }
	 
	  return selected; // Returns a string array of the selected players so that the plugin can do something with them
	}

}

package com.jjmc.chromashift.screens.levels;

import com.jjmc.chromashift.player.Player;

/**
 * Wrapper to make Player loadable.
 * Ensures player is fully initialized before gameplay.
 */
public class LoadablePlayer implements LoadableObject {
    
    private Player player;
    private boolean initialized;
    private boolean ready;
    
    public LoadablePlayer(Player player) {
        this.player = player;
        this.initialized = false;
        this.ready = false;
    }
    
    @Override
    public void initialize() {
        if (initialized) return;
        
        // Player-specific initialization
        // Skills and spawn point are already set
        initialized = true;
        ready = true; // Player is ready immediately
    }
    
    @Override
    public boolean isReady() {
        return ready;
    }
    
    @Override
    public String getLoadingDescription() {
        return "Player";
    }
    
    public Player getPlayer() {
        return player;
    }
}

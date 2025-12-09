package com.jjmc.chromashift.screens.levels;

import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.environment.enemy.Tentacle;

/**
 * Wrapper for enemy entities.
 * Ensures enemies are initialized before gameplay.
 */
public class LoadableEnemies implements LoadableObject {
    
    private Array<Tentacle> tentacles;
    private boolean initialized;
    private boolean ready;
    
    public LoadableEnemies(Array<Tentacle> tentacles) {
        this.tentacles = tentacles;
        this.initialized = false;
        this.ready = false;
    }
    
    @Override
    public void initialize() {
        if (initialized) return;
        
        // Enemies are already created, just mark initialized
        initialized = true;
        ready = true;
    }
    
    @Override
    public boolean isReady() {
        return ready;
    }
    
    @Override
    public String getLoadingDescription() {
        return "Enemies (" + tentacles.size + " tentacles)";
    }
    
    public Array<Tentacle> getTentacles() {
        return tentacles;
    }
}

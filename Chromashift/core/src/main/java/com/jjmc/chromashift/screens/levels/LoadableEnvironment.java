package com.jjmc.chromashift.screens.levels;

import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.Wall;
import com.jjmc.chromashift.environment.interactable.Interactable;
import com.jjmc.chromashift.environment.collectible.Collectible;

/**
 * Wrapper for level environment objects (walls, solids, interactables, collectibles).
 * Groups related objects for simpler loading management.
 */
public class LoadableEnvironment implements LoadableObject {
    
    private Array<Wall> walls;
    private Array<Solid> solids;
    private Array<Interactable> interactables;
    private Array<Collectible> collectibles;
    private boolean initialized;
    private boolean ready;
    private String description;
    
    public LoadableEnvironment(String description, 
                               Array<Wall> walls, 
                               Array<Solid> solids,
                               Array<Interactable> interactables,
                               Array<Collectible> collectibles) {
        this.description = description;
        this.walls = walls;
        this.solids = solids;
        this.interactables = interactables;
        this.collectibles = collectibles;
        this.initialized = false;
        this.ready = false;
    }
    
    @Override
    public void initialize() {
        if (initialized) return;
        
        // Environment objects are already created during level load
        // Just mark as initialized
        initialized = true;
        ready = true;
    }
    
    @Override
    public boolean isReady() {
        return ready;
    }
    
    @Override
    public String getLoadingDescription() {
        return description + " (" + 
               walls.size + " walls, " + 
               solids.size + " solids, " +
               interactables.size + " interactables, " +
               collectibles.size + " collectibles)";
    }
}

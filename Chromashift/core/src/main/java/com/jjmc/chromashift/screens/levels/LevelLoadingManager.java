package com.jjmc.chromashift.screens.levels;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;

/**
 * Manages the loading and initialization of level objects.
 * Ensures all objects are ready before gameplay begins.
 * Tracks progress and coordinates the loading sequence.
 */
public class LevelLoadingManager {
    
    private LevelLoadingState currentState;
    private Array<LoadableObject> pendingObjects;
    private Array<LoadableObject> readyObjects;
    private float fadeAlpha;
    private float fadeSpeed;
    private boolean allObjectsReady;
    private float stateTimer;
    private float minStateTime;
    
    // Callbacks
    private Runnable onLoadingComplete;
    
    public LevelLoadingManager() {
        this.currentState = LevelLoadingState.LOADING_ENVIRONMENT;
        this.pendingObjects = new Array<>();
        this.readyObjects = new Array<>();
        this.fadeAlpha = 1.0f; // Start fully black
        this.fadeSpeed = 1.5f; // Fade speed (1.0 = 1 second for full fade)
        this.allObjectsReady = false;
        this.stateTimer = 0f;
        this.minStateTime = 0.1f; // Minimum time per state (prevents flashing)
    }
    
    /**
     * Register an object that needs to be loaded.
     */
    public void registerLoadableObject(LoadableObject object) {
        if (object != null && !pendingObjects.contains(object, true)) {
            pendingObjects.add(object);
            Gdx.app.log("LevelLoadingManager", "Registered: " + object.getLoadingDescription());
        }
    }
    
    /**
     * Begin the loading process.
     * Initializes all registered objects.
     */
    public void startLoading() {
        Gdx.app.log("LevelLoadingManager", "Starting load sequence for " + pendingObjects.size + " objects");
        
        // Initialize all objects
        for (LoadableObject obj : pendingObjects) {
            try {
                obj.initialize();
            } catch (Exception e) {
                Gdx.app.error("LevelLoadingManager", 
                    "Error initializing " + obj.getLoadingDescription() + ": " + e.getMessage());
            }
        }
        
        currentState = LevelLoadingState.LOADING_ENVIRONMENT;
        stateTimer = 0f;
    }
    
    /**
     * Update the loading manager.
     * Checks object readiness and advances loading states.
     * @param delta time since last frame
     */
    public void update(float delta) {
        stateTimer += delta;
        
        // Wait minimum time per state
        if (stateTimer < minStateTime) {
            return;
        }
        
        // Check pending objects and move ready ones
        for (int i = pendingObjects.size - 1; i >= 0; i--) {
            LoadableObject obj = pendingObjects.get(i);
            if (obj.isReady()) {
                readyObjects.add(obj);
                pendingObjects.removeIndex(i);
                Gdx.app.log("LevelLoadingManager", "Ready: " + obj.getLoadingDescription());
            }
        }
        
        // Check if all objects are ready
        if (pendingObjects.size == 0 && !allObjectsReady) {
            allObjectsReady = true;
            Gdx.app.log("LevelLoadingManager", "All objects ready!");
        }
        
        // Advance states
        if (allObjectsReady) {
            switch (currentState) {
                case LOADING_ENVIRONMENT:
                    currentState = LevelLoadingState.LOADING_INTERACTABLES;
                    stateTimer = 0f;
                    break;
                case LOADING_INTERACTABLES:
                    currentState = LevelLoadingState.LOADING_ENEMIES;
                    stateTimer = 0f;
                    break;
                case LOADING_ENEMIES:
                    currentState = LevelLoadingState.LOADING_BOSSES;
                    stateTimer = 0f;
                    break;
                case LOADING_BOSSES:
                    currentState = LevelLoadingState.LOADING_COLLECTIBLES;
                    stateTimer = 0f;
                    break;
                case LOADING_COLLECTIBLES:
                    currentState = LevelLoadingState.LOADING_PLAYER;
                    stateTimer = 0f;
                    break;
                case LOADING_PLAYER:
                    currentState = LevelLoadingState.FINALIZING;
                    stateTimer = 0f;
                    break;
                case FINALIZING:
                    currentState = LevelLoadingState.FADING_IN;
                    stateTimer = 0f;
                    break;
                case FADING_IN:
                    // Fade in
                    fadeAlpha -= fadeSpeed * delta;
                    if (fadeAlpha <= 0f) {
                        fadeAlpha = 0f;
                        currentState = LevelLoadingState.READY;
                        if (onLoadingComplete != null) {
                            onLoadingComplete.run();
                        }
                    }
                    break;
                case READY:
                    // Gameplay active
                    break;
            }
        }
    }
    
    /**
     * Check if loading is complete and gameplay can begin.
     */
    public boolean isReady() {
        return currentState == LevelLoadingState.READY;
    }
    
    /**
     * Check if currently in loading/initialization phase.
     */
    public boolean isLoading() {
        return currentState != LevelLoadingState.READY;
    }
    
    /**
     * Get current loading state.
     */
    public LevelLoadingState getCurrentState() {
        return currentState;
    }
    
    /**
     * Get loading progress (0.0 to 1.0).
     */
    public float getProgress() {
        if (readyObjects.size + pendingObjects.size == 0) {
            return 1.0f;
        }
        return (float) readyObjects.size / (readyObjects.size + pendingObjects.size);
    }
    
    /**
     * Get fade alpha for overlay (1.0 = fully black, 0.0 = fully transparent).
     */
    public float getFadeAlpha() {
        return fadeAlpha;
    }
    
    /**
     * Get number of pending (not ready) objects.
     */
    public int getPendingCount() {
        return pendingObjects.size;
    }
    
    /**
     * Get number of ready objects.
     */
    public int getReadyCount() {
        return readyObjects.size;
    }
    
    /**
     * Get the array of pending objects (for display purposes).
     */
    public Array<LoadableObject> getPendingObjects() {
        return pendingObjects;
    }
    
    /**
     * Get total registered objects.
     */
    public int getTotalCount() {
        return readyObjects.size + pendingObjects.size;
    }
    
    /**
     * Get human-readable loading status.
     */
    public String getStatusText() {
        switch (currentState) {
            case LOADING_ENVIRONMENT:
                return "Loading Environment...";
            case LOADING_INTERACTABLES:
                return "Loading Interactables...";
            case LOADING_ENEMIES:
                return "Loading Enemies...";
            case LOADING_BOSSES:
                return "Loading Bosses...";
            case LOADING_COLLECTIBLES:
                return "Loading Collectibles...";
            case LOADING_PLAYER:
                return "Initializing Player...";
            case FINALIZING:
                return "Finalizing Level...";
            case FADING_IN:
                return "Starting...";
            case READY:
                return "Ready!";
            default:
                return "Loading...";
        }
    }
    
    /**
     * Set callback for when loading completes.
     */
    public void setOnLoadingComplete(Runnable callback) {
        this.onLoadingComplete = callback;
    }
    
    /**
     * Reset the loading manager for a new level.
     */
    public void reset() {
        currentState = LevelLoadingState.LOADING_ENVIRONMENT;
        pendingObjects.clear();
        readyObjects.clear();
        fadeAlpha = 1.0f;
        allObjectsReady = false;
        stateTimer = 0f;
        onLoadingComplete = null;
    }
    
    /**
     * Force skip loading (for debugging).
     */
    public void forceComplete() {
        currentState = LevelLoadingState.READY;
        fadeAlpha = 0f;
        if (onLoadingComplete != null) {
            onLoadingComplete.run();
        }
    }
}

package com.jjmc.chromashift.screens.levels;

/**
 * Loading state for level initialization.
 */
public enum LevelLoadingState {
    /** Loading environment objects (walls, tiles) */
    LOADING_ENVIRONMENT,
    
    /** Loading interactables (doors, buttons, boxes, etc.) */
    LOADING_INTERACTABLES,
    
    /** Loading enemies and AI entities */
    LOADING_ENEMIES,
    
    /** Loading boss entities */
    LOADING_BOSSES,
    
    /** Loading collectibles and props */
    LOADING_COLLECTIBLES,
    
    /** Initializing player */
    LOADING_PLAYER,
    
    /** Finalizing level connections */
    FINALIZING,
    
    /** Fade in transition */
    FADING_IN,
    
    /** Level ready, gameplay active */
    READY
}

package com.jjmc.chromashift.screens.levels;

/**
 * Interface for level objects that require initialization/loading before gameplay.
 * Implementers can report when they're ready for gameplay to begin.
 */
public interface LoadableObject {
    
    /**
     * Initialize this object for gameplay.
     * Called during the loading phase before gameplay begins.
     */
    void initialize();
    
    /**
     * Check if this object is ready for gameplay.
     * @return true if fully loaded and ready, false if still loading
     */
    boolean isReady();
    
    /**
     * Get a description of this object for loading progress display.
     * @return human-readable object type/name
     */
    String getLoadingDescription();
}

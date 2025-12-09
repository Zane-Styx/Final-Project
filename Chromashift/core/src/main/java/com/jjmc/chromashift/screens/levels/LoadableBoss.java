package com.jjmc.chromashift.screens.levels;

import com.jjmc.chromashift.entity.boss.Boss;

/**
 * Wrapper to make Boss entities loadable.
 * Ensures bosses are fully initialized before gameplay.
 */
public class LoadableBoss implements LoadableObject {
    
    private Boss boss;
    private boolean initialized;
    private boolean ready;
    
    public LoadableBoss(Boss boss) {
        this.boss = boss;
        this.initialized = false;
        this.ready = false;
    }
    
    @Override
    public void initialize() {
        if (initialized) return;
        
        // Boss-specific initialization
        // Environment is already set, just mark as initialized
        initialized = true;
        ready = true; // Bosses are ready immediately after initialization
    }
    
    @Override
    public boolean isReady() {
        return ready;
    }
    
    @Override
    public String getLoadingDescription() {
        return "Boss: " + boss.getClass().getSimpleName();
    }
    
    public Boss getBoss() {
        return boss;
    }
}

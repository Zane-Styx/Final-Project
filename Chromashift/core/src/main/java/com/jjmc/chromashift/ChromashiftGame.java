package com.jjmc.chromashift;

import com.badlogic.gdx.Game;
import com.chromashift.helper.SoundManager;
import com.jjmc.chromashift.config.AudioConfig;
import com.jjmc.chromashift.screens.ui.LoadingScreen;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class ChromashiftGame extends Game {
    @Override
    public void create() {
        // Load audio config first (which applies saved volumes to SoundManager)
        AudioConfig.getInstance();
        
        // Initialize audio system early so UI sounds are ready
        initializeAudio();
        
        // Start with loading screen that enqueues and loads all assets,
        // then transitions to the initial screen.
        setScreen(new LoadingScreen(this));
    }

    private void initializeAudio() {
        try {
            SoundManager.init(false);
            // UI sounds
            SoundManager.addSound("UISelect", "sounds/UISelect.wav");
            SoundManager.addSound("Button", "sounds/Button.wav");
        } catch (Exception e) {
            System.err.println("[ChromashiftGame] Failed to initialize audio: " + e.getMessage());
        }
    }
}


package com.jjmc.chromashift;

import com.badlogic.gdx.Game;
import com.jjmc.chromashift.screens.LoadingScreen;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class ChromashiftGame extends Game {
    @Override
    public void create() {
        // Start with loading screen that enqueues and loads all assets,
        // then transitions to the initial screen.
        setScreen(new LoadingScreen(this));
    }
}


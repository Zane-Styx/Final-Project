package com.jjmc.chromashift;

import com.badlogic.gdx.Game;
import com.jjmc.chromashift.screens.LevelMakerScreen;
import com.jjmc.chromashift.screens.TestSceneScreen;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class ChromashiftGame extends Game {
    @Override
    public void create() {
        // Preload assets (textures/atlases) on the GL thread to avoid costly
        // texture uploads during gameplay. Assets.loadAll() blocks until
        // loaded and should be called before heavy object construction.
        Assets.loadAll();

        setScreen(new TestSceneScreen());
    }
}

package com.jjmc.chromashift;

import com.badlogic.gdx.Game;
import com.jjmc.chromashift.screen.MainMenuScreen;

public class ChromashiftGame extends Game {
    @Override
    public void create() {
        setScreen(new MainMenuScreen(this));
    }
}

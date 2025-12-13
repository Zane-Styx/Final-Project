package com.jjmc.chromashift.screens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class CreditsScreen extends AbstractMenuScreen implements Screen {
    private Stage stage;
    private Skin skin;

    @Override
    public void show() {
        super.show();
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));

        Table root = new Table(skin);
        root.setFillParent(true);
        root.defaults().pad(10);
        root.add(new Label("Credits", skin)).padBottom(20).row();
        root.add(new Label("Game Design: Team Chromashift", skin)).row();
        root.add(new Label("Programming: Team Chromashift", skin)).row();
        root.add(new Label("Art & Audio: Team Chromashift", skin)).row();
        stage.addActor(root);
    }

    @Override
    public void render(float delta) {
        clearAndDrawScene(delta);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
    }
}

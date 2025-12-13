package com.jjmc.chromashift.screens.ui;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.chromashift.helper.UIHelper;
import com.jjmc.chromashift.config.AudioConfig;

public class SettingsScreen extends AbstractMenuScreen implements Screen {
    private Stage stage;
    private Skin skin;
    private AudioConfig audioConfig;

    @Override
    public void show() {
        super.show();
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        audioConfig = AudioConfig.getInstance();
        loadSpriteFonts();
        buildUI();
    }

    private void loadSpriteFonts() {
        try {
            if (!com.chromashift.helper.SpriteFontManager.isLoaded("default")) {
                com.chromashift.helper.SpriteFontManager.load("default", "ui/ctm.uiskin.png");
            }
            com.chromashift.helper.SpriteFontManager.load("custom", "ui/ctm.uiskin.png");
        } catch (Exception e) {
            Gdx.app.error("SettingsScreen", "Failed to load sprite font", e);
            UIHelper.USE_SPRITE_FONTS = false;
        }
    }

    private void buildUI() {
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.defaults().pad(15);
        
        try {
            com.chromashift.helper.SpriteLabel titleLabel = 
                UIHelper.createSpriteLabel("SETTINGS", "custom", 5f);
            if (titleLabel != null) {
                rootTable.add(titleLabel).colspan(2).center().padBottom(40).row();
            }
            
            // Master Volume: 0-1 range converted to 0-100 for display
            createVolumeControl(rootTable, "Master Volume", 
                audioConfig.getMasterVolume() * 100f,
                (value) -> audioConfig.setMasterVolume(value / 100f));
            
            // Music Volume: 0-1 range converted to 0-100 for display
            createVolumeControl(rootTable, "Music Volume", 
                audioConfig.getMusicVolume() * 100f,
                (value) -> audioConfig.setMusicVolume(value / 100f));
            
            // SFX Volume: 0-1 range converted to 0-100 for display
            createVolumeControl(rootTable, "SFX Volume", 
                audioConfig.getSfxVolume() * 100f,
                (value) -> audioConfig.setSfxVolume(value / 100f));
            
            Actor backBtn = UIHelper.createButton("Back", skin, new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new MainMenuScreen());
                }
            });
            rootTable.add(backBtn).colspan(2).center().padTop(40).row();
        } catch (Exception e) {
            Label errorLabel = new Label("Failed to load settings UI", skin);
            errorLabel.setColor(com.badlogic.gdx.graphics.Color.RED);
            rootTable.add(errorLabel).row();
        }
        
        stage.addActor(rootTable);
    }

    private void createVolumeControl(Table parent, String labelText, float initialValue, VolumeCallback callback) {
        com.chromashift.helper.SpriteLabel titleLabel = 
            UIHelper.createSpriteLabel(labelText, "custom", 2f);
        com.chromashift.helper.SpriteLabel valueLabel = 
            UIHelper.createSpriteLabel(String.format("Volume: %.0f%%", initialValue), "custom", 1f);
        
        Slider slider = UIHelper.createSlider(0, 100, 5, false, skin, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float val = ((Slider) actor).getValue();
                valueLabel.setText(String.format("Volume: %.0f%%", val));
                callback.onVolumeChanged(val);
            }
        });
        slider.setValue(initialValue);
        
        parent.add(titleLabel).right().padRight(10).padBottom(30);
        Table volumeRow = new Table();
        volumeRow.add(slider).width(200).padRight(10).row();
        volumeRow.add(valueLabel);
        parent.add(volumeRow).left().row();
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new MainMenuScreen());
        }
        
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
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {}

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
    }

    /**
     * Functional interface for volume change callbacks.
     */
    @FunctionalInterface
    private interface VolumeCallback {
        void onVolumeChanged(float value);
    }
}

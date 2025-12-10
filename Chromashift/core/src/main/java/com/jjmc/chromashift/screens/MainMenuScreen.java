package com.jjmc.chromashift.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.chromashift.helper.SpriteLabel;
import com.chromashift.helper.UIHelper;

public class MainMenuScreen implements Screen {
    private Stage stage;
    private Skin skin;
    @Override
    public void show() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        
        try {
            if (!com.chromashift.helper.SpriteFontManager.isLoaded("default")) {
                com.chromashift.helper.SpriteFontManager.load("default", "ui/ctm.uiskin.png");
            }
        } catch (Exception e) {
            Gdx.app.error("TestMenuScreen", "Failed to load sprite font", e);
            UIHelper.USE_SPRITE_FONTS = false; // Fallback to regular fonts
        }
        
        // Build UI
        buildUI();
    }
    
    private void buildUI() {
        int width = Gdx.graphics.getWidth();
        // Create root table
        Table rootTable = new Table(skin);
        rootTable.setFillParent(true);
        rootTable.defaults().pad(15);
        
        Table leftTable = new Table(skin);
        leftTable.setColor(Color.RED);
        rootTable.add(leftTable).width(width/2);
        Table rightTable = new Table(skin);
        rightTable.setColor(Color.BLUE);
        rootTable.add(rightTable).width(width/2);
        
        SpriteLabel label = UIHelper.createSpriteLabel("CHROMASHIFT", "default");
        label.setScale(8);
        leftTable.add(label).center().colspan(2).padTop(10).padBottom(10).row();;
        
        // Create image button with state-based PNGs (icon, inactive, pressed, hover)
        var continueBtn = UIHelper.createImageButton(
            "ui/ContinueBtn_0.png",        // icon
            "ui/ContinueBtn_0.png",        // inactive
            "ui/ContinueBtn_1.png",        // pressed
            "ui/ContinueBtn_Hover.png",    // hover
            skin,
            new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    System.out.println("Continue");
                    try {
                        // Load player save
                        com.jjmc.chromashift.player.PlayerIO.PlayerState loaded = 
                        com.jjmc.chromashift.player.PlayerIO.load("player_save.json");
                        if (loaded != null && loaded.currentLevel != null) {
                            System.out.println("Continuing from saved game: " + loaded.currentLevel);
                            // Create TestSceneScreen with saved level using SAVED_IF_EXISTS mode
                            TestSceneScreen screen = new TestSceneScreen(
                                loaded.currentLevel, 
                                com.jjmc.chromashift.screens.levels.LevelLoader.LoadMode.SAVED_IF_EXISTS
                            );
                            ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(screen);
                            // Apply player state after screen is created
                            Gdx.app.postRunnable(() -> {
                                try {
                                    if (screen.player != null) {
                                        com.jjmc.chromashift.player.PlayerIO.applyToPlayer(screen.player, loaded);
                                        if (loaded.visitedLevels != null) {
                                            screen.visitedLevels.clear();
                                            screen.visitedLevels.addAll(loaded.visitedLevels);
                                        }
                                        System.out.println("Player state restored with saved level state");
                                    }
                                } catch (Exception ex) {
                                    System.err.println("Failed to restore player state: " + ex.getMessage());
                                }
                            });
                        } else {
                            System.out.println("No save found, starting new game");
                            ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new TestSceneScreen());
                        }
                    } catch (Exception ex) {
                        System.err.println("Failed to load save: " + ex.getMessage());
                        ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new TestSceneScreen());
                    }
                }
            }
        );
        // Center the Continue button across the full width and make it larger
        
        leftTable.add(continueBtn).colspan(2).center().width(260 ).height(48).padTop(20).row();
        
        var playBtn = UIHelper.createImageButton(
            "ui/PlayBtn_0.png",        // icon
            "ui/PlayBtn_0.png",        // inactive
            "ui/PlayBtn_1.png",        // pressed
            "ui/PlayBtn_Hover.png",    // hover
            skin,
            new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    System.out.println("New Game");
                    ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new TestSceneScreen());
                }
            }
        );
        leftTable.add(playBtn).colspan(2).width(260).height(48).row();
        
        var exitBtn = UIHelper.createImageButton(
            "ui/ExitBtn_0.png",        // icon
            "ui/ExitBtn_0.png",        // inactive
            "ui/ExitBtn_1.png",        // pressed
            "ui/ExitBtn_Hover.png",    // hover
            skin,
            new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    System.out.println("Extras");
                    Gdx.app.exit();
                }
            }
        );
        leftTable.add(exitBtn).colspan(2).width(260).height(48).row();
        
        Table table = new Table();
        var settingsBtn = UIHelper.createImageButton(
            "ui/SettingsBtn_0.png",        // icon
            "ui/SettingsBtn_0.png",        // inactive
            "ui/SettingsBtn_1.png",        // pressed
            "ui/SettingsBtn_Hover.png",    // hover
            skin,
            new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    System.out.println("Settings");
                    ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new SettingsScreen());
                }
            }
        );
        table.add(settingsBtn).width(48).height(48).padRight(20);
        
        var creditsBtn = UIHelper.createImageButton(
            "ui/CreditsBtn_0.png",        // icon
            "ui/CreditsBtn_0.png",        // inactive
            "ui/CreditsBtn_1.png",        // pressed
            "ui/CreditsBtn_Hover.png",    // hover
            skin,
            new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    System.out.println("Credits");
                    ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new CreditsScreen());
                }
            }
        ); 
        table.add(creditsBtn).width(48).height(48);
        leftTable.add(table).colspan(2).center().padTop(350);
        stage.addActor(rootTable);
    }
    
    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.06f, 0.07f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
        // Update and draw stage
        stage.act(delta);
        stage.draw();
    }
    
    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }
    
    @Override
    public void pause() {
    }
    
    @Override
    public void resume() {
    }
    
    @Override
    public void hide() {
    }
    
    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
    }
}

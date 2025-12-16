package com.jjmc.chromashift.screens.ui;

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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.jjmc.chromashift.screens.GameSceneScreen;
import com.badlogic.gdx.files.FileHandle;

public class MainMenuScreen extends com.jjmc.chromashift.screens.ui.AbstractMenuScreen implements Screen {
    private Stage stage;
    private Skin skin;
    private com.badlogic.gdx.graphics.Texture mainCharacterTex;
    private com.badlogic.gdx.scenes.scene2d.ui.Image mainCharacterImg;

    @Override
    public void show() {
        super.show();
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        loadSpriteFonts();
        buildUI();
    }

    private void loadSpriteFonts() {
        try {
            if (!com.chromashift.helper.SpriteFontManager.isLoaded("default")) {
                com.chromashift.helper.SpriteFontManager.load("default", "ui/ctm.uiskin.png");
            }
        } catch (Exception e) {
            Gdx.app.error("MainMenuScreen", "Failed to load sprite font", e);
            UIHelper.USE_SPRITE_FONTS = false;
        }
    }
    
    private void buildUI() {
        Table rootTable = new Table(skin);
        rootTable.setFillParent(true);
        
        // Split: left side for buttons, right side reserved for character image
        Table leftSide = new Table(skin);
        leftSide.defaults().pad(15);
        
        SpriteLabel titleLabel = UIHelper.createSpriteLabel("CHROMASHIFT", "default");
        titleLabel.setScale(8);
        leftSide.add(titleLabel).center().padTop(10).padBottom(30).row();
        
        leftSide.add(createContinueButton()).width(260).height(48).row();
        leftSide.add(createPlayButton()).width(260).height(48).padTop(10).row();
        leftSide.add(createExitButton()).width(260).height(48).padTop(10).row();
        
        Table bottomButtonsTable = new Table();
        bottomButtonsTable.add(createSettingsButton()).width(48).height(48).padRight(12);
        bottomButtonsTable.add(createCreditsButton()).width(48).height(48).padRight(12);
        bottomButtonsTable.add(createHowToPlayButton()).width(48).height(48);
        leftSide.add(bottomButtonsTable).center().padTop(350).row();
        
        // Right side: scaled main character image
        Table rightSide = new Table(skin);
        rightSide.top().right();
        try {
            if (mainCharacterTex == null) {
                mainCharacterTex = new com.badlogic.gdx.graphics.Texture(Gdx.files.internal("ui/main_character.png"));
                mainCharacterTex.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
            }
            if (mainCharacterImg == null) {
                mainCharacterImg = new com.badlogic.gdx.scenes.scene2d.ui.Image(mainCharacterTex);
                mainCharacterImg.setScaling(com.badlogic.gdx.utils.Scaling.fit);
            }
            // Scale proportionally to viewport
            float maxW = stage.getViewport().getWorldWidth() * 0.35f;
            float maxH = stage.getViewport().getWorldHeight() * 0.6f;
            float texW = mainCharacterTex.getWidth();
            float texH = mainCharacterTex.getHeight();
            float scale = Math.min(maxW / texW, maxH / texH);
            float drawW = texW * scale;
            float drawH = texH * scale;
            rightSide.add(mainCharacterImg).size(drawW, drawH).padTop(40).padRight(20);
        } catch (Exception e) {
            Gdx.app.error("MainMenuScreen", "Failed to load ui/main_character.png: " + e.getMessage());
        }
        
        // Add left and right to root
        rootTable.add(leftSide).expand().left().top().padLeft(50);
        rootTable.add(rightSide).expand().right().top().padRight(50);
        
        stage.addActor(rootTable);
    }

    private com.badlogic.gdx.scenes.scene2d.ui.ImageButton createContinueButton() {
        return UIHelper.createImageButton(
            "ui/ContinueBtn_0.png", "ui/ContinueBtn_0.png", 
            "ui/ContinueBtn_1.png", "ui/ContinueBtn_Hover.png",
            skin, new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    onContinueClicked();
                }
            }
        );
    }

    private com.badlogic.gdx.scenes.scene2d.ui.ImageButton createPlayButton() {
        return UIHelper.createImageButton(
            "ui/PlayBtn_0.png", "ui/PlayBtn_0.png", 
            "ui/PlayBtn_1.png", "ui/PlayBtn_Hover.png",
            skin, new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    onPlayPressed();
                }
            }
        );
    }

    private com.badlogic.gdx.scenes.scene2d.ui.ImageButton createExitButton() {
        return UIHelper.createImageButton(
            "ui/ExitBtn_0.png", "ui/ExitBtn_0.png", 
            "ui/ExitBtn_1.png", "ui/ExitBtn_Hover.png",
            skin, new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    Gdx.app.exit();
                }
            }
        );
    }

    private com.badlogic.gdx.scenes.scene2d.ui.ImageButton createSettingsButton() {
        return UIHelper.createImageButton(
            "ui/SettingsBtn_0.png", "ui/SettingsBtn_0.png", 
            "ui/SettingsBtn_1.png", "ui/SettingsBtn_Hover.png",
            skin, new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new SettingsScreen());
                }
            }
        );
    }

    private com.badlogic.gdx.scenes.scene2d.ui.ImageButton createCreditsButton() {
        return UIHelper.createImageButton(
            "ui/CreditsBtn_0.png", "ui/CreditsBtn_0.png", 
            "ui/CreditsBtn_1.png", "ui/CreditsBtn_Hover.png",
            skin, new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new CreditsScreen());
                }
            }
        );
    }

    private com.badlogic.gdx.scenes.scene2d.ui.ImageButton createHowToPlayButton() {
        return UIHelper.createImageButton(
            "ui/HowToPlayBtn_0.png", "ui/HowToPlayBtn_0.png",
            "ui/HowToPlayBtn_1.png", "ui/HowToPlayBtn_Hover.png",
            skin, new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    onHowToPlay();
                }
            }
        );
    }

    private void onContinueClicked() {
        try {
            // Load player state from database
            com.jjmc.chromashift.player.PlayerIO.PlayerState loaded = 
                com.jjmc.chromashift.database.PlayerDAO.loadPlayerStateFromDB(1);
            
            if (loaded != null && loaded.currentLevel != null) {
                loadGameFromSave(loaded);
            } else {
                startNewGame();
            }
        } catch (Exception ex) {
            Gdx.app.error("MainMenuScreen", "Failed to load save: " + ex.getMessage());
            startNewGame();
        }
    }

    private void loadGameFromSave(com.jjmc.chromashift.player.PlayerIO.PlayerState loaded) {
        GameSceneScreen screen = new GameSceneScreen(
            loaded.currentLevel,
            com.jjmc.chromashift.screens.levels.LevelLoader.LoadMode.SAVED_IF_EXISTS
        );
        ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(screen);
        
        Gdx.app.postRunnable(() -> {
            if (screen.player != null) {
                com.jjmc.chromashift.player.PlayerIO.applyToPlayer(screen.player, loaded);
                if (loaded.visitedLevels != null) {
                    screen.visitedLevels.clear();
                    screen.visitedLevels.addAll(loaded.visitedLevels);
                }
            }
        });
    }

    private void startNewGame() {
        ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new GameSceneScreen());
    }

    private void onPlayPressed() {
        try {
            boolean hasSave = com.jjmc.chromashift.database.PlayerDAO.hasPlayerSave(1);
            if (hasSave) {
                com.badlogic.gdx.scenes.scene2d.ui.Dialog dlg = new com.badlogic.gdx.scenes.scene2d.ui.Dialog("New Game?", skin) {
                    @Override
                    protected void result(Object obj) {
                        if (Boolean.TRUE.equals(obj)) {
                            try {
                                com.jjmc.chromashift.database.PlayerDAO.deletePlayerSave(1);
                            } catch (Exception ignored) {}
                            showColorChoiceDialog();
                        }
                    }
                };
                dlg.text("Starting a new game will delete your previous progress. Proceed?");
                dlg.button("New Game", true);
                dlg.button("Cancel", false);
                dlg.show(stage);
                dlg.setPosition(
                    (stage.getWidth() - dlg.getWidth()) / 2f,
                    (stage.getHeight() - dlg.getHeight()) / 2f
                );
            } else {
                showColorChoiceDialog();
            }
        } catch (Exception e) {
            // If check fails, default to normal play flow
            showColorChoiceDialog();
        }
    }

    private void onHowToPlay() {
        try {
            com.badlogic.gdx.graphics.Texture controlsTex = new com.badlogic.gdx.graphics.Texture(Gdx.files.internal("ui/ControlsGuide.png"));
            controlsTex.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
            com.badlogic.gdx.scenes.scene2d.ui.Image controlsImage = new com.badlogic.gdx.scenes.scene2d.ui.Image(controlsTex);
            controlsImage.setScaling(com.badlogic.gdx.utils.Scaling.fit);

            float maxW = stage.getWidth() * 0.9f;
            float maxH = stage.getHeight() * 0.9f;
            float texW = controlsTex.getWidth();
            float texH = controlsTex.getHeight();
            float scale = Math.min(maxW / texW, maxH / texH);
            float drawW = texW * scale;
            float drawH = texH * scale;

            com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog = new com.badlogic.gdx.scenes.scene2d.ui.Dialog("", skin);
            dialog.getContentTable().add(controlsImage).size(drawW, drawH).pad(20);
            dialog.button("Close", false);
            dialog.pack();
            dialog.show(stage);
            dialog.setPosition(
                (stage.getWidth() - dialog.getWidth()) / 2f,
                (stage.getHeight() - dialog.getHeight()) / 2f
            );
        } catch (Exception e) {
            Gdx.app.error("MainMenuScreen", "Failed to load controls guide: " + e.getMessage());
        }
    }

    /**
     * Shows a dialog with character selection using images in 2 rows (4 top, 3 bottom).
     */
    private void showColorChoiceDialog() {
        com.badlogic.gdx.scenes.scene2d.ui.Dialog dlg = new com.badlogic.gdx.scenes.scene2d.ui.Dialog("Choose Your Character", skin);

        com.badlogic.gdx.scenes.scene2d.ui.Table content = new com.badlogic.gdx.scenes.scene2d.ui.Table(skin);
        content.defaults().pad(5);

        // Title
        content.add(new com.badlogic.gdx.scenes.scene2d.ui.Label("Select Your Color", skin)).colspan(4).center().padBottom(15).row();

        // First row: 4 characters
        addCharacterOption(content, "ui/character/character-blue.png", "Blue");
        addCharacterOption(content, "ui/character/character-green.png", "Green");
        addCharacterOption(content, "ui/character/character-orange.png", "Orange");
        addCharacterOption(content, "ui/character/character-pink.png", "Pink");
        content.row();

        // Second row: 3 characters (centered via colspan)
        content.add().expandX(); // spacer
        addCharacterOption(content, "ui/character/character-purple.png", "Purple");
        addCharacterOption(content, "ui/character/character-red.png", "Red");
        addCharacterOption(content, "ui/character/character-yellow.png", "Yellow");
        content.add().expandX(); // spacer
        content.row();

        dlg.getContentTable().add(content).pad(20);

        // Cancel button
        dlg.button("Cancel", false);

        dlg.show(stage);
        dlg.setPosition(
            (stage.getWidth() - dlg.getWidth()) / 2f,
            (stage.getHeight() - dlg.getHeight()) / 2f
        );
    }

    private void addCharacterOption(com.badlogic.gdx.scenes.scene2d.ui.Table table, String imagePath, String colorName) {
        try {
            com.badlogic.gdx.graphics.Texture tex = new com.badlogic.gdx.graphics.Texture(Gdx.files.internal(imagePath));
            tex.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
            com.badlogic.gdx.scenes.scene2d.ui.Image img = new com.badlogic.gdx.scenes.scene2d.ui.Image(tex);
            img.setScaling(com.badlogic.gdx.utils.Scaling.fit);

            com.badlogic.gdx.scenes.scene2d.ui.ImageButton btn = new com.badlogic.gdx.scenes.scene2d.ui.ImageButton(
                new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(tex))
            );
            btn.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    onColorChosen(colorName);
                }
            });

            table.add(btn).size(120, 120).pad(8);
        } catch (Exception e) {
            Gdx.app.error("MainMenuScreen", "Failed to load character image: " + imagePath);
        }
    }

    private void onColorChosen(String colorName) {
        try {
            com.jjmc.chromashift.database.PlayerDAO.savePreferredColor(1, colorName);
            Gdx.app.log("MainMenuScreen", "Preferred color saved to DB: " + colorName);
        } catch (Exception e) {
            Gdx.app.error("MainMenuScreen", "Failed saving preferred color to DB: " + e.getMessage());
        }

        ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new GameSceneScreen());
    }

    
    @Override
    public void render(float delta) {
        // Draw shared animated scene (background, stars, asteroids, planet)
        clearAndDrawScene(delta);

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }

        // Draw menu UI on top
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
        super.dispose();
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
        if (mainCharacterTex != null) {
            mainCharacterTex.dispose();
            mainCharacterTex = null;
        }
    }
}

package com.jjmc.chromashift.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.chromashift.helper.UIHelper;

/**
 * Simple test menu to choose quick screens during development.
 * Now uses UIHelper to demonstrate Scene2D UI components.
 * 
 * ===== UIHELPER TUTORIAL =====
 * 
 * UIHelper provides factory methods for creating common Scene2D UI components with consistent styling.
 * All methods accept a Skin and ChangeListener/ClickListener for event handling.
 * 
 * 1. BUTTONS (TextButton):
 *    TextButton btn = UIHelper.createButton("Label", skin, new ClickListener() {
 *        @Override
 *        public void clicked(InputEvent event, float x, float y) {
 *            // Handle click
 *        }
 *    });
 *    - Returns styled TextButton with specified label
 *    - Use ClickListener for click events (simpler than ChangeListener)
 *    - Set size: rootTable.add(btn).width(200).height(48)
 * 
 * 2. SLIDERS (Slider):
 *    Slider slider = UIHelper.createSlider(min, max, stepSize, vertical, skin, new ChangeListener() {
 *        @Override
 *        public void changed(ChangeEvent event, Actor actor) {
 *            float value = ((Slider) actor).getValue();
 *            // Handle value change
 *        }
 *    });
 *    - min/max: range bounds (e.g., 0-100)
 *    - stepSize: increment size (e.g., 1 for integers, 0.1 for decimals)
 *    - vertical: false = horizontal, true = vertical
 *    - Set initial value: slider.setValue(50)
 *    - Get current value: slider.getValue()
 * 
 * 3. TOGGLES/CHECKBOXES (CheckBox):
 *    CheckBox toggle = UIHelper.createToggle(initialState, skin, new ChangeListener() {
 *        @Override
 *        public void changed(ChangeEvent event, Actor actor) {
 *            boolean checked = ((CheckBox) actor).isChecked();
 *            // Handle toggle state change
 *        }
 *    });
 *    - initialState: true = checked, false = unchecked
 *    - Returns CheckBox without label (add label separately in layout)
 *    - Check state: toggle.isChecked()
 *    - Set state: toggle.setChecked(true/false)
 * 
 * LAYOUT TIPS:
 * - Use Table for grid-based layouts (Scene2D's layout container)
 * - Chain methods: table.add(widget).width(200).padRight(10).row()
 * - .row() moves to next row
 * - .colspan(n) spans multiple columns
 * - Use nested Tables for complex layouts (e.g., slider + value label)
 * 
 * COMMON PATTERNS:
 * - Label + Widget Row:
 *   table.add(new Label("Text:", skin)).right().padRight(10);
 *   table.add(widget).left().row();
 * 
 * - Widget + Value Display:
 *   Table row = new Table();
 *   row.add(slider).width(200).padRight(10);
 *   row.add(valueLabel).width(50);
 *   table.add(row).row();
 * 
 * - Centered Button:
 *   table.add(button).colspan(2).width(200).height(48).row();
 * 
 * See buildUI() method below for complete examples.
 */
public class TestMenuScreen implements Screen {
    private Stage stage;
    private Skin skin;
    private Label sliderValueLabel;
    private Label toggleStateLabel;
    private Label volumeLabel;

    @Override
    public void show() {
        // Create stage with screen viewport
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        
        // Load skin
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        
        // Build UI
        buildUI();
    }

    private void buildUI() {
        // Create root table
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.defaults().pad(15);
        
        // ===== TITLE =====
        Label title = new Label("UIHelper Test Menu", skin);
        title.setFontScale(2f);
        rootTable.add(title).colspan(2).padBottom(30).row();
        
        // ===== SECTION: NAVIGATION BUTTONS =====
        Label navLabel = new Label("Navigation:", skin);
        navLabel.setFontScale(1.3f);
        rootTable.add(navLabel).colspan(2).padTop(10).padBottom(10).row();
        
        // Continue Button (loads last save with SAVED_IF_EXISTS mode)
        TextButton continueBtn = UIHelper.createButton("Continue", skin, new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
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
        });
        rootTable.add(continueBtn).colspan(2).width(260).height(48).row();
        
        // Test Scene Button (New Game)
        TextButton testSceneBtn = UIHelper.createButton("New Game", skin, new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                System.out.println("Opening Test Scene...");
                ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new TestSceneScreen());
            }
        });
        rootTable.add(testSceneBtn).colspan(2).width(260).height(48).row();
        
        // Level Maker Button
        TextButton levelMakerBtn = UIHelper.createButton("Open Level Maker", skin, new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                System.out.println("Opening Level Maker...");
                ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new com.jjmc.chromashift.screens.levels.LevelMakerScreen());
            }
        });
        rootTable.add(levelMakerBtn).colspan(2).width(260).height(48).padBottom(20).row();
        
        // ===== SECTION: UI HELPER DEMOS =====
        Label demoLabel = new Label("UIHelper Demos:", skin);
        demoLabel.setFontScale(1.3f);
        rootTable.add(demoLabel).colspan(2).padTop(10).padBottom(10).row();
        
        // Demo: Simple Button
        TextButton demoBtn = UIHelper.createButton("Demo Button", skin, new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                System.out.println("Demo button clicked!");
            }
        });
        rootTable.add(new Label("Simple Button:", skin)).right().padRight(10);
        rootTable.add(demoBtn).left().row();
        
        // Demo: Slider with Value Display
        sliderValueLabel = new Label("50", skin);
        Slider slider = UIHelper.createSlider(0, 100, 1, false, skin, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float val = ((Slider) actor).getValue();
                sliderValueLabel.setText(String.format("%.0f", val));
                System.out.println("Slider value: " + val);
            }
        });
        slider.setValue(50);
        
        rootTable.add(new Label("Slider:", skin)).right().padRight(10);
        Table sliderRow = new Table();
        sliderRow.add(slider).width(200).padRight(10);
        sliderRow.add(sliderValueLabel).width(30);
        rootTable.add(sliderRow).left().row();
        
        // Demo: Toggle Switch (Text)
        toggleStateLabel = new Label("Currently: ON", skin);
        CheckBox toggle = UIHelper.createToggle(true, skin, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean checked = ((CheckBox) actor).isChecked();
                toggleStateLabel.setText("Currently: " + (checked ? "ON" : "OFF"));
                System.out.println("Toggle state: " + (checked ? "ON" : "OFF"));
            }
        });
        
        rootTable.add(new Label("Toggle Switch:", skin)).right().padRight(10);
        Table toggleRow = new Table();
        toggleRow.add(toggle).padRight(10);
        toggleRow.add(toggleStateLabel);
        rootTable.add(toggleRow).left().row();
        
        // Demo: Volume Slider (showing practical use)
        volumeLabel = new Label("Volume: 75%", skin);
        Slider volumeSlider = UIHelper.createSlider(0, 100, 5, false, skin, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float val = ((Slider) actor).getValue();
                volumeLabel.setText(String.format("Volume: %.0f%%", val));
                System.out.println("Volume set to: " + val + "%");
            }
        });
        volumeSlider.setValue(75);
        
        rootTable.add(new Label("Volume Control:", skin)).right().padRight(10);
        Table volumeRow = new Table();
        volumeRow.add(volumeSlider).width(200).padRight(10);
        volumeRow.add(volumeLabel);
        rootTable.add(volumeRow).left().row();
        
        // ===== EXIT BUTTON =====
        TextButton exitBtn = UIHelper.createButton("Exit Game", skin, new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                System.out.println("Exiting game...");
                Gdx.app.exit();
            }
        });
        rootTable.add(exitBtn).colspan(2).width(200).height(40).padTop(20).row();
        
        // ===== INFO LABEL =====
        Label infoLabel = new Label("Press ESC to exit", skin);
        infoLabel.setFontScale(0.8f);
        rootTable.add(infoLabel).colspan(2).padTop(20);
        
        // Add root table to stage
        stage.addActor(rootTable);
    }

    @Override
    public void render(float delta) {
        // Handle ESC key
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
        
        // Clear screen
        Gdx.gl.glClearColor(0.06f, 0.07f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        
        // Update and draw stage
        stage.act(delta);
        stage.draw();
    }

    @Override 
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }
    
    @Override public void pause() { }
    @Override public void resume() { }
    @Override public void hide() { }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
    }
}

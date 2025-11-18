package com.chromashift.helper;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/**
 * UIHelper - Utility class for creating Scene2D UI elements in LibGDX.
 * 
 * This class provides static builder methods to simplify the creation of
 * common UI components like buttons, sliders, and toggle switches.
 * 
 * All returned Actors are ready to add to a Stage or Table.
 * No allocations happen during render() - create once, reuse forever.
 * 
 * Example Usage:
 * <pre>
 * // Setup
 * Stage stage = new Stage(new ScreenViewport());
 * Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
 * Table rootTable = new Table();
 * rootTable.setFillParent(true);
 * 
 * // Create UI elements
 * TextButton btn = UIHelper.createButton("Click Me", skin, new ClickListener() {
 *     public void clicked(InputEvent event, float x, float y) {
 *         System.out.println("Button clicked!");
 *     }
 * });
 * 
 * Slider slider = UIHelper.createSlider(0, 100, 1, false, skin, new ChangeListener() {
 *     public void changed(ChangeEvent event, Actor actor) {
 *         System.out.println("Value: " + ((Slider)actor).getValue());
 *     }
 * });
 * 
 * CheckBox toggle = UIHelper.createToggle(true, skin, new ChangeListener() {
 *     public void changed(ChangeEvent event, Actor actor) {
 *         System.out.println("Toggle: " + ((CheckBox)actor).isChecked());
 *     }
 * });
 * 
 * // Layout
 * rootTable.add(btn).pad(10).row();
 * rootTable.add(slider).width(200).pad(10).row();
 * rootTable.add(toggle).pad(10).row();
 * 
 * stage.addActor(rootTable);
 * </pre>
 */
public class UIHelper {

    // ========================================================================
    // CUSTOMIZATION CONSTANTS
    // Adjust these to change default appearance of UI elements
    // ========================================================================
    
    /** Default padding for buttons (left, right, top, bottom) */
    public static float BUTTON_PAD_X = 20f;
    public static float BUTTON_PAD_Y = 10f;
    
    /** Default minimum button width */
    public static float BUTTON_MIN_WIDTH = 100f;
    
    /** Default minimum button height */
    public static float BUTTON_MIN_HEIGHT = 40f;
    
    /** Icon size when using sprite buttons */
    public static float ICON_SIZE = 24f;
    
    /** Spacing between icon and text in buttons */
    public static float ICON_TEXT_SPACING = 8f;
    
    /** Default slider width (horizontal) or height (vertical) */
    public static float SLIDER_SIZE = 200f;
    
    /** Toggle switch text when ON */
    public static String TOGGLE_ON_TEXT = "ON";
    
    /** Toggle switch text when OFF */
    public static String TOGGLE_OFF_TEXT = "OFF";

    // ========================================================================
    // 1. NORMAL BUTTON (TEXT ONLY)
    // ========================================================================
    
    /**
     * Creates a simple text button with automatic padding and centered text.
     * 
     * @param text The button label text
     * @param skin The UI skin to use for styling
     * @param onClick The click listener (use ClickListener with clicked() method)
     * @return A ready-to-use TextButton
     * 
     * Customization tips:
     * - Change button size: result.setWidth()/setHeight() or use .size() in Table
     * - Change padding: Adjust BUTTON_PAD_X and BUTTON_PAD_Y constants
     * - Change font: Modify the skin's "default-font" or create custom button style
     * - Change colors: Edit skin's button style or use result.setColor()
     */
    public static TextButton createButton(String text, Skin skin, ClickListener onClick) {
        TextButton button = new TextButton(text, skin);
        
        // Apply default padding
        button.pad(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
        
        // Set minimum size
        button.setSize(BUTTON_MIN_WIDTH, BUTTON_MIN_HEIGHT);
        
        // Attach click listener with sound
        if (onClick != null) {
            button.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    com.chromashift.helper.SoundManager.play("UISelect");
                    onClick.clicked(event, x, y);
                }
            });
        }
        
        return button;
    }

    // ========================================================================
    // 2. BUTTON WITH SPRITE/ICON
    // ========================================================================
    
    /**
     * Creates a button with an icon/sprite placed to the left of the text.
     * Returns a Container<Table> that acts as a button with image + text.
     * 
     * @param text The button label text
     * @param icon The texture region for the icon
     * @param skin The UI skin to use for styling
     * @param onClick The click listener
     * @return A Container wrapping a button with icon and text
     * 
     * Customization tips:
     * - Change icon size: Adjust ICON_SIZE constant
     * - Change icon position: Modify the table.add() order (icon, then label)
     * - Change spacing: Adjust ICON_TEXT_SPACING constant
     * - Use right-aligned icon: Add label first, then icon
     * - Use custom button style: Pass skin.get("styleName", TextButton.TextButtonStyle.class)
     */
    public static Container<Table> createButton(String text, TextureRegion icon, Skin skin, ClickListener onClick) {
        // Create a table to hold icon + text
        Table buttonTable = new Table(skin);
        buttonTable.setBackground(skin.getDrawable("button"));
        
        // Add icon
        if (icon != null) {
            Image iconImage = new Image(new TextureRegionDrawable(icon));
            iconImage.setSize(ICON_SIZE, ICON_SIZE);
            buttonTable.add(iconImage).size(ICON_SIZE).padRight(ICON_TEXT_SPACING);
        }
        
        // Add text label
        Label label = new Label(text, skin);
        buttonTable.add(label);
        
        // Apply padding
        buttonTable.pad(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
        
        // Attach click listener with sound
        if (onClick != null) {
            buttonTable.addListener(new ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    com.chromashift.helper.SoundManager.play("UISelect");
                    onClick.clicked(event, x, y);
                }
            });
        }
        
        // Wrap in container for consistent API
        Container<Table> container = new Container<>(buttonTable);
        container.setSize(BUTTON_MIN_WIDTH, BUTTON_MIN_HEIGHT);
        
        return container;
    }

    // ========================================================================
    // 3. SLIDER
    // ========================================================================
    
    /**
     * Creates a slider with automatic sizing and styling.
     * 
     * @param min Minimum value
     * @param max Maximum value
     * @param step Step size (e.g., 1 for integers, 0.1 for decimals)
     * @param vertical True for vertical slider, false for horizontal
     * @param skin The UI skin to use for styling
     * @param onChange Change listener triggered when value changes
     * @return A ready-to-use Slider
     * 
     * Customization tips:
     * - Change size: Use .width()/height() in Table layout or adjust SLIDER_SIZE
     * - Change visual style: Modify skin's slider style (handle, background drawables)
     * - Get current value: slider.getValue()
     * - Set value programmatically: slider.setValue(float)
     * - Disable: slider.setDisabled(true)
     * - Add value label: Create Label next to slider, update in onChange listener
     */
    public static Slider createSlider(float min, float max, float step, boolean vertical, Skin skin, ChangeListener onChange) {
        Slider slider = new Slider(min, max, step, vertical, skin);
        
        // Set default size
        if (vertical) {
            slider.setHeight(SLIDER_SIZE);
        } else {
            slider.setWidth(SLIDER_SIZE);
        }
        
        // Attach change listener with sound
        if (onChange != null) {
            slider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    com.chromashift.helper.SoundManager.play("UISelect");
                    onChange.changed(event, actor);
                }
            });
        }
        
        return slider;
    }

    // ========================================================================
    // 4. TOGGLE SWITCH (TEXT: ON/OFF)
    // ========================================================================
    
    /**
     * Creates a toggle switch using CheckBox styled to show "ON" or "OFF" text.
     * 
     * @param initialState True for ON, false for OFF
     * @param skin The UI skin to use for styling
     * @param onToggle Change listener triggered when toggle state changes
     * @return A CheckBox styled as a toggle switch
     * 
     * Customization tips:
     * - Change ON/OFF text: Adjust TOGGLE_ON_TEXT and TOGGLE_OFF_TEXT constants
     * - Change visual style: Modify skin's checkbox style (on/off drawables)
     * - Get state: checkbox.isChecked()
     * - Set state programmatically: checkbox.setChecked(boolean)
     * - Disable: checkbox.setDisabled(true)
     */
    public static CheckBox createToggle(boolean initialState, Skin skin, ChangeListener onToggle) {
        // Create checkbox with initial text based on state
        String initialText = initialState ? TOGGLE_ON_TEXT : TOGGLE_OFF_TEXT;
        CheckBox toggle = new CheckBox(initialText, skin);
        toggle.setChecked(initialState);
        
        // Add listener to update text when state changes with sound
        toggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                CheckBox cb = (CheckBox) actor;
                cb.setText(cb.isChecked() ? TOGGLE_ON_TEXT : TOGGLE_OFF_TEXT);
                com.chromashift.helper.SoundManager.play("UISelect");
                
                // Call user's listener
                if (onToggle != null) {
                    onToggle.changed(event, actor);
                }
            }
        });
        
        return toggle;
    }

    // ========================================================================
    // 5. TOGGLE SWITCH (WITH SPRITE/ICON)
    // ========================================================================
    
    /**
     * Creates a toggle switch that displays different sprites for ON and OFF states.
     * Returns a Container<Table> with an Image that swaps between sprites.
     * 
     * @param initialState True for ON, false for OFF
     * @param onSprite Texture region shown when toggle is ON
     * @param offSprite Texture region shown when toggle is OFF
     * @param skin The UI skin to use for styling
     * @param onToggle Change listener triggered when toggle state changes
     * @return A Container wrapping a clickable image toggle
     * 
     * Customization tips:
     * - Change icon size: Adjust ICON_SIZE constant
     * - Add border/background: Use table.setBackground(skin.getDrawable("name"))
     * - Add text beside icon: Add Label to the table
     * - Animate transition: Use Actions in the change listener
     * - Get state: Store in user data with actor.setUserObject(Boolean)
     */
    public static Container<Table> createToggle(boolean initialState, TextureRegion onSprite, TextureRegion offSprite, Skin skin, ChangeListener onToggle) {
        // Create table to hold the toggle image
        Table toggleTable = new Table(skin);
        
        // Create image with initial sprite
        TextureRegion initialSprite = initialState ? onSprite : offSprite;
        Image toggleImage = new Image(new TextureRegionDrawable(initialSprite));
        toggleImage.setSize(ICON_SIZE, ICON_SIZE);
        
        // Store state in user object
        toggleTable.setUserObject(initialState);
        
        toggleTable.add(toggleImage).size(ICON_SIZE);
        toggleTable.pad(BUTTON_PAD_Y, BUTTON_PAD_X, BUTTON_PAD_Y, BUTTON_PAD_X);
        
        // Add click listener to toggle state and swap sprite
        toggleTable.addListener(new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                // Toggle state
                boolean currentState = (Boolean) toggleTable.getUserObject();
                boolean newState = !currentState;
                toggleTable.setUserObject(newState);
                
                // Swap sprite
                TextureRegion newSprite = newState ? onSprite : offSprite;
                toggleImage.setDrawable(new TextureRegionDrawable(newSprite));
                
                // Play UI sound
                com.chromashift.helper.SoundManager.play("UISelect");
                
                // Call user's change listener
                if (onToggle != null) {
                    ChangeListener.ChangeEvent changeEvent = new ChangeListener.ChangeEvent();
                    changeEvent.setTarget(toggleTable);
                    onToggle.changed(changeEvent, toggleTable);
                }
            }
        });
        
        // Wrap in container
        Container<Table> container = new Container<>(toggleTable);
        
        return container;
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================
    
    /**
     * Helper to check toggle sprite state.
     * Use with createToggle(sprites) result.
     * 
     * @param toggleContainer The container returned by createToggle with sprites
     * @return True if ON, false if OFF
     */
    public static boolean getToggleState(Container<Table> toggleContainer) {
        if (toggleContainer == null || toggleContainer.getActor() == null) {
            return false;
        }
        Object state = toggleContainer.getActor().getUserObject();
        return state != null && (Boolean) state;
    }
    
    /**
     * Helper to set toggle sprite state programmatically.
     * 
     * @param toggleContainer The container returned by createToggle with sprites
     * @param state True for ON, false for OFF
     * @param onSprite Sprite to show when ON
     * @param offSprite Sprite to show when OFF
     */
    public static void setToggleState(Container<Table> toggleContainer, boolean state, TextureRegion onSprite, TextureRegion offSprite) {
        if (toggleContainer == null || toggleContainer.getActor() == null) {
            return;
        }
        
        Table table = toggleContainer.getActor();
        table.setUserObject(state);
        
        // Find and update image
        if (table.getChildren().size > 0 && table.getChildren().first() instanceof Image) {
            Image img = (Image) table.getChildren().first();
            TextureRegion sprite = state ? onSprite : offSprite;
            img.setDrawable(new TextureRegionDrawable(sprite));
        }
    }

    // ========================================================================
    // EXAMPLE USAGE DEMO
    // ========================================================================
    
    /**
     * Example demonstrating how to use UIHelper to build a complete UI.
     * Call this from a Screen's show() method.
     * 
     * @param stage The Stage to add UI to
     * @param skin The Skin containing UI styles
     * 
     * Example in a Screen class:
     * <pre>
     * public void show() {
     *     stage = new Stage(new ScreenViewport());
     *     skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
     *     Gdx.input.setInputProcessor(stage);
     *     UIHelper.buildExampleUI(stage, skin);
     * }
     * 
     * public void render(float delta) {
     *     ScreenUtils.clear(0.2f, 0.2f, 0.2f, 1f);
     *     stage.act(delta);
     *     stage.draw();
     * }
     * </pre>
     */
    public static void buildExampleUI(com.badlogic.gdx.scenes.scene2d.Stage stage, Skin skin) {
        // Create root table
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.defaults().pad(10);
        
        // Title
        Label title = new Label("UIHelper Demo", skin);
        title.setFontScale(1.5f);
        rootTable.add(title).colspan(2).padBottom(20).row();
        
        // 1. Simple text button
        TextButton simpleBtn = createButton("Simple Button", skin, new ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                System.out.println("Simple button clicked!");
            }
        });
        rootTable.add(new Label("Text Button:", skin)).right();
        rootTable.add(simpleBtn).left().row();
        
        // 2. Button with icon (if you have an icon texture)
        // TextureRegion icon = new TextureRegion(new Texture("icon.png"));
        // Container<Table> iconBtn = createButton("Icon Button", icon, skin, new ClickListener() {
        //     @Override
        //     public void clicked(InputEvent event, float x, float y) {
        //         System.out.println("Icon button clicked!");
        //     }
        // });
        // rootTable.add(new Label("Icon Button:", skin)).right();
        // rootTable.add(iconBtn).left().row();
        
        // 3. Horizontal slider
        final Label sliderValue = new Label("50", skin);
        Slider slider = createSlider(0, 100, 1, false, skin, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float val = ((Slider) actor).getValue();
                sliderValue.setText(String.format("%.0f", val));
                System.out.println("Slider value: " + val);
            }
        });
        slider.setValue(50);
        
        rootTable.add(new Label("Slider:", skin)).right();
        Table sliderRow = new Table();
        sliderRow.add(slider).width(200);
        sliderRow.add(sliderValue).padLeft(10);
        rootTable.add(sliderRow).left().row();
        
        // 4. Text toggle
        CheckBox toggle = createToggle(true, skin, new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean checked = ((CheckBox) actor).isChecked();
                System.out.println("Toggle state: " + (checked ? "ON" : "OFF"));
            }
        });
        rootTable.add(new Label("Toggle:", skin)).right();
        rootTable.add(toggle).left().row();
        
        // 5. Sprite toggle (if you have on/off sprites)
        // TextureRegion onSprite = new TextureRegion(new Texture("on.png"));
        // TextureRegion offSprite = new TextureRegion(new Texture("off.png"));
        // Container<Table> spriteToggle = createToggle(false, onSprite, offSprite, skin, new ChangeListener() {
        //     @Override
        //     public void changed(ChangeEvent event, Actor actor) {
        //         boolean state = getToggleState((Container<Table>) actor.getParent());
        //         System.out.println("Sprite toggle: " + (state ? "ON" : "OFF"));
        //     }
        // });
        // rootTable.add(new Label("Sprite Toggle:", skin)).right();
        // rootTable.add(spriteToggle).left().row();
        
        // Add root table to stage
        stage.addActor(rootTable);
    }
}

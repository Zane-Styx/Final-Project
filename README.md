# **Chromashift**

A fast-paced 2D action platformer built with **[LibGDX](https://libgdx.com/)** where players navigate through challenging levels using light-based mechanics and combat to reach the end-level portal.

This project was generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

---

## How to Play

### Objective
Navigate through each level and reach the end-level portal to progress. Defeat enemies and overcome obstacles using your light-based abilities and combat skills.

### Controls
| Input | Action |
|-------|--------|
| **Arrow Keys** or **WASD** | Move left/right |
| **Space** | Jump |
| **Right Click** | Pick up/Throw object |
| **Q** | Skill 1 |
| **E** | Skill 2 |
| **F** | Interact |
| **ESC** | Pause game |

### Game Mechanics
- **Light-Based Mechanics**: Use light-based abilities to solve puzzles and defeat enemies
- **Platforming**: Navigate challenging platforms and obstacles
- **Progression**: Collect diamonds to buy health potions/sheild in shop

### Tips
- Master the timing of your jumps to avoid hazards
- Use light abilities strategically in combat/traversing the level
- Explore each level for puzzles to progress to another level
- Watch for patterns in boss attacks to dodge effectively

---

## Project Structure

| Module | Purpose |
|--------|---------|
| `core` | Main module with the application logic shared by all platforms |
| `lwjgl3` | Primary desktop platform using LWJGL3 |

---

## Getting Started

### Building the Project

This project uses **[Gradle](https://gradle.org/)** to manage dependencies. The Gradle wrapper is included, so you can run tasks using `gradlew.bat` (Windows) or `./gradlew` (Linux/Mac).

#### Useful Gradle Commands

- `lwjgl3:run` — Starts the application
- `lwjgl3:jar` — Builds a runnable jar file (output: `lwjgl3/build/libs`)
- `build` — Builds sources and archives of all projects
- `clean` — Removes `build` folders
- `test` — Runs unit tests (if any)

#### Gradle Flags

- `--daemon` — Uses Gradle daemon for faster builds
- `--offline` — Uses cached dependency archives
- `--refresh-dependencies` — Forces validation of all dependencies
- `--continue` — Continues running tasks even if errors occur

---

## UI Components

### SpriteLabel

A Scene2D widget that displays text using a sprite-based font. It behaves like a standard Label but renders characters from a sprite sheet for custom visual styling.

#### Features
- Runtime text updates
- Color tinting
- Scaling support
- Text alignment (LEFT, CENTER, RIGHT)
- Efficient batched rendering

#### Constructor

```java
// Basic construction
SpriteLabel label = new SpriteLabel(font, "Hello World");

// With custom scale
SpriteLabel label = new SpriteLabel(font, "Hello World", 2.0f);
```

#### Key Methods

| Method | Description |
|--------|-------------|
| `setText(String text)` | Updates the displayed text |
| `getText()` | Gets the current text |
| `setScale(float scale)` | Sets the text scaling factor |
| `setAlignment(Align alignment)` | Sets text alignment (LEFT, CENTER, RIGHT) |
| `getWidth()` / `getHeight()` | Gets the label dimensions |

#### Usage Example

```java
// Setup
SpriteFont font = new SpriteFont("fonts/main.fnt");
SpriteLabel scoreLabel = new SpriteLabel(font, "Score: 0", 1.5f);
scoreLabel.setAlignment(SpriteLabel.Align.CENTER);

// In game loop - update score
scoreLabel.setText("Score: " + currentScore);

// Add to Stage or Table
table.add(scoreLabel).center().padTop(20);
```

---

### UIHelper

A comprehensive utility class for creating Scene2D UI elements in LibGDX. It provides static builder methods to simplify the creation of common UI components like buttons, sliders, and toggles.

#### Key Features
- Ready-to-use button creation
- Hover effects and disabled states
- Icon/sprite button support
- Automatic sound effects on interaction
- Customizable appearance through constants

#### Customization Constants

```java
// Button appearance
UIHelper.BUTTON_PAD_X = 20f;              // Horizontal padding
UIHelper.BUTTON_PAD_Y = 10f;              // Vertical padding
UIHelper.BUTTON_MIN_WIDTH = 100f;         // Minimum width
UIHelper.BUTTON_MIN_HEIGHT = 40f;         // Minimum height

// Sprite font settings
UIHelper.USE_SPRITE_FONTS = true;         // Enable sprite fonts
UIHelper.SPRITE_FONT_SCALE = 2.0f;        // Font scale for buttons

// Colors
UIHelper.BUTTON_HOVER_COLOR = new Color(1.2f, 1.2f, 1.2f, 1f);      // Hover
UIHelper.BUTTON_DISABLED_COLOR = new Color(0.5f, 0.5f, 0.5f, 0.7f); // Disabled
```

#### Button Creation Methods

##### Simple Text Button

```java
TextButton button = (TextButton) UIHelper.createButton("Click Me", skin, new ClickListener() {
    @Override
    public void clicked(InputEvent event, float x, float y) {
        System.out.println("Button clicked!");
    }
});

table.add(button).pad(10);
```

##### Button with Hover Effects

```java
TextButton button = UIHelper.createHoverButton("Submit", skin, new ClickListener() {
    @Override
    public void clicked(InputEvent event, float x, float y) {
        handleSubmit();
    }
});

// Disable/enable the button
UIHelper.setButtonDisabled(button, true);  // Disable with visual feedback
UIHelper.setButtonDisabled(button, false); // Enable
```

##### Button with Icon/Sprite

```java
TextureRegion icon = new TextureRegion(texture, 0, 0, 32, 32);
Container<Table> iconButton = UIHelper.createButton("Attack", icon, skin, new ClickListener() {
    @Override
    public void clicked(InputEvent event, float x, float y) {
        performAttack();
    }
});

table.add(iconButton).size(150, 50).pad(10);
```

#### Complete UI Setup Example

```java
// Initialize
Stage stage = new Stage(new ScreenViewport());
Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
Table rootTable = new Table();
rootTable.setFillParent(true);

// Create buttons
TextButton startBtn = (TextButton) UIHelper.createButton("Start Game", skin, new ClickListener() {
    public void clicked(InputEvent event, float x, float y) {
        startGame();
    }
});

TextButton settingsBtn = UIHelper.createHoverButton("Settings", skin, new ClickListener() {
    public void clicked(InputEvent event, float x, float y) {
        openSettings();
    }
});

// Layout
rootTable.center();
rootTable.add(startBtn).width(200).padBottom(20).row();
rootTable.add(settingsBtn).width(200).padBottom(20).row();

stage.addActor(rootTable);
```

#### Customizing Button Appearance

```java
// Change default button size
UIHelper.BUTTON_MIN_WIDTH = 150f;
UIHelper.BUTTON_MIN_HEIGHT = 50f;

// Disable sprite fonts (use traditional fonts instead)
UIHelper.USE_SPRITE_FONTS = false;

// Customize hover color
UIHelper.BUTTON_HOVER_COLOR = new Color(1.5f, 1.5f, 0.8f, 1f);

// Change button padding
UIHelper.BUTTON_PAD_X = 30f;
UIHelper.BUTTON_PAD_Y = 15f;
```

### Sliders

Create horizontal or vertical sliders for numeric value input.

#### Creating a Slider

```java
// Horizontal slider (0-100 range, step size of 1)
Slider volumeSlider = UIHelper.createSlider(0, 100, 1, false, skin, new ChangeListener() {
    @Override
    public void changed(ChangeEvent event, Actor actor) {
        float value = ((Slider) actor).getValue();
        System.out.println("Volume: " + value);
    }
});

// Vertical slider
Slider brightnessSlider = UIHelper.createSlider(0, 100, 5, true, skin, new ChangeListener() {
    @Override
    public void changed(ChangeEvent event, Actor actor) {
        float value = ((Slider) actor).getValue();
        applyBrightness(value);
    }
});

table.add(volumeSlider).width(200).padBottom(10).row();
table.add(brightnessSlider).height(150).padBottom(10).row();
```

#### Slider Customization

```java
// Slider size configuration
UIHelper.SLIDER_SIZE = 250f;

// Get/Set slider value
float currentValue = volumeSlider.getValue();
volumeSlider.setValue(50);

// Get min/max range
float min = volumeSlider.getMinValue();
float max = volumeSlider.getMaxValue();
float step = volumeSlider.getStepSize();
```

### Checkboxes & Toggles

Create toggle switches and checkboxes for boolean inputs.

#### Text-based Toggle (ON/OFF)

```java
CheckBox toggle = UIHelper.createToggle(true, skin, new ChangeListener() {
    @Override
    public void changed(ChangeEvent event, Actor actor) {
        boolean isChecked = ((CheckBox) actor).isChecked();
        System.out.println("Toggle state: " + isChecked);
    }
});

// Customize ON/OFF text
UIHelper.TOGGLE_ON_TEXT = "ENABLED";
UIHelper.TOGGLE_OFF_TEXT = "DISABLED";

table.add(toggle).padBottom(10).row();
```

#### Sprite-based Toggle (Icon)

```java
TextureRegion onIcon = new TextureRegion(texture, 0, 0, 32, 32);
TextureRegion offIcon = new TextureRegion(texture, 32, 0, 32, 32);

Container<Table> spriteToggle = UIHelper.createToggle(false, onIcon, offIcon, skin, new ChangeListener() {
    @Override
    public void changed(ChangeEvent event, Actor actor) {
        boolean state = UIHelper.getToggleState((Container<Table>) actor);
        System.out.println("Toggle is: " + (state ? "ON" : "OFF"));
    }
});

// Get/Set toggle state
boolean isOn = UIHelper.getToggleState(spriteToggle);
UIHelper.setToggleState(spriteToggle, true, onIcon, offIcon);

table.add(spriteToggle).size(50).padBottom(10).row();
```

---

## Table Layout & Alignment

The Scene2D `Table` class is used for UI layout. Use these methods to control positioning and alignment:

### Table Setup

```java
Table rootTable = new Table(skin);
rootTable.setFillParent(true);        // Make table fill the stage
rootTable.setBackground("background"); // Set background drawable

// Add padding around table
rootTable.pad(20);                     // All sides: 20 pixels
rootTable.padTop(30);                  // Top only
rootTable.padLeft(15).padRight(15);   // Left and right
```

### Alignment Options

#### Horizontal Alignment

```java
Table table = new Table();

table.left();       // Align all content to the LEFT
table.center();     // Center all content horizontally
table.right();      // Align all content to the RIGHT
```

#### Vertical Alignment

```java
table.top();        // Align all content to the TOP
table.center();     // Center all content vertically
table.bottom();     // Align all content to the BOTTOM
```

#### Combined Alignment

```java
table.left().top();     // Left-top corner
table.center().center(); // Centered both ways
table.right().bottom(); // Right-bottom corner
```

### Cell Methods (Per-Element)

Control individual elements within a table:

```java
Table table = new Table();

TextButton btn = createButton("Click", skin, listener);
Label label = new Label("Score: 100", skin);
Slider slider = createSlider(0, 100, 1, false, skin, listener);

// Add with alignment
table.add(btn).left();              // Left-align this button
table.row();
table.add(label).center();          // Center this label
table.row();
table.add(slider).right();          // Right-align this slider

// Add with sizing
table.add(btn).width(150);          // Fixed width
table.add(btn).height(40);          // Fixed height
table.add(btn).size(150, 40);       // Fixed width and height
table.add(btn).expandX().fillX();   // Expand and fill horizontal space
table.add(btn).expandY().fillY();   // Expand and fill vertical space

// Add with spacing
table.add(btn).pad(10);             // Padding on all sides
table.add(btn).padLeft(20);         // Left padding only
table.add(btn).padTop(15).padBottom(15); // Top and bottom
table.add(btn).spaceRight(10);      // Space to the right (gap after element)
table.add(btn).spaceLeft(10);       // Space to the left (gap before element)
```

### Common Layout Examples

#### Vertical Layout (Stack Top to Bottom)

```java
Table vLayout = new Table(skin);
vLayout.left().top();  // Start from top-left

TextButton btn1 = createButton("Button 1", skin, listener);
TextButton btn2 = createButton("Button 2", skin, listener);
TextButton btn3 = createButton("Button 3", skin, listener);

vLayout.add(btn1).width(200).padBottom(10).row();
vLayout.add(btn2).width(200).padBottom(10).row();
vLayout.add(btn3).width(200).row();

stage.addActor(vLayout);
```

#### Horizontal Layout (Side by Side)

```java
Table hLayout = new Table(skin);
hLayout.center().center(); // Center alignment

TextButton btn1 = createButton("Left", skin, listener);
TextButton btn2 = createButton("Middle", skin, listener);
TextButton btn3 = createButton("Right", skin, listener);

hLayout.add(btn1).width(100).padRight(10);
hLayout.add(btn2).width(100).padRight(10);
hLayout.add(btn3).width(100);

stage.addActor(hLayout);
```

#### Grid Layout (Rows and Columns)

```java
Table gridLayout = new Table(skin);
gridLayout.center();

// 3x3 grid
for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 3; col++) {
        TextButton btn = createButton("" + (row * 3 + col + 1), skin, listener);
        gridLayout.add(btn).size(50, 50).pad(5);
    }
    gridLayout.row();
}

stage.addActor(gridLayout);
```

#### Form Layout (Labels + Inputs)

```java
Table formLayout = new Table(skin);
formLayout.left();
formLayout.pad(20);

// Name field
Label nameLabel = new Label("Name:", skin);
TextField nameInput = new TextField("", skin);
formLayout.add(nameLabel).width(100);
formLayout.add(nameInput).width(200).padBottom(15).row();

// Volume slider
Label volumeLabel = new Label("Volume:", skin);
Slider volumeSlider = createSlider(0, 100, 1, false, skin, listener);
formLayout.add(volumeLabel).width(100);
formLayout.add(volumeSlider).width(200).padBottom(15).row();

// Settings toggle
Label settingsLabel = new Label("Enabled:", skin);
CheckBox toggle = createToggle(true, skin, listener);
formLayout.add(settingsLabel).width(100);
formLayout.add(toggle).padBottom(15).row();

// Submit button
TextButton submitBtn = createButton("Submit", skin, listener);
formLayout.add(submitBtn).colspan(2).center().width(150);

stage.addActor(formLayout);
```

---

## Project Assets

- **Levels**: Located in `assets/levels/` (JSON-based level files)
- **Sprites**: Located in `assets/entity/` and `assets/environment/`
- **UI**: Skin files in `assets/ui/`
- **Sounds**: Audio files in `assets/sounds/`

---

## Building and Running

```bash
# Run the game
gradlew lwjgl3:run

# Build a runnable JAR
gradlew lwjgl3:jar

# Clean build files
gradlew clean
```

---

**Chromashift — A LibGDX Game Project**

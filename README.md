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
- **Combat**: Engage in fast-paced action combat with various enemy types
- **Platforming**: Navigate challenging platforms and obstacles
- **Progression**: Collect items and power-ups to enhance your abilities

### Tips
- Master the timing of your jumps to avoid hazards
- Use light abilities strategically in combat
- Explore each level for hidden items and power-ups
- Watch for patterns in enemy attacks to dodge effectively

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

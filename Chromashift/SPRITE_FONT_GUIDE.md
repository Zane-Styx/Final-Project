# Sprite-Based Text Rendering System

A custom text rendering system that uses image-based character sprite sheets instead of traditional .fnt bitmap fonts.

## Overview

This system provides **zero-dependency sprite font rendering** for LibGDX games:
- ✅ No .fnt files required
- ✅ Fully image-based character rendering
- ✅ Variable character spacing
- ✅ Color tinting support
- ✅ Runtime text updates
- ✅ Multiple font styles
- ✅ Scene2D integration
- ✅ Efficient batched rendering

## Core Components

### 1. `SpriteFont.java`
Low-level sprite font renderer. Converts text strings into batched sprite draws.

**Features:**
- Maps characters to texture regions from a sprite sheet
- Configurable character dimensions (default 8x10 pixels)
- Variable spacing between characters
- Color tinting
- Scaling support
- Width calculation for layout

**Character Layout (128x128 texture):**
```
Row 1 (y=118-128): a b c d e f g h i j k l m n o p
Row 2 (y=108-118): q r s t u v w x y z 1 2 3 4 5 6
Row 3 (y=98-108):  7 8 9 0 # , . ! ? : * % ( ) + -
Row 4 (y=88-98):   \ / = > <
```

### 2. `SpriteLabel.java`
Scene2D widget wrapper for SpriteFont. Provides Label-like behavior.

**Features:**
- Extends `Widget` for full Scene2D integration
- Alignment support (LEFT, CENTER, RIGHT)
- Scaling
- Color tinting with parent alpha
- Runtime text updates
- Automatic size calculation

### 3. `SpriteFontManager.java`
Singleton manager for loading, caching, and disposing sprite fonts.

**Features:**
- Centralized font resource management
- Named font registration
- Automatic texture loading
- Memory cleanup
- Multiple font support

### 4. `UIHelper.java` (Extended)
Added factory methods for easy SpriteLabel creation.

## Quick Start

### Step 1: Prepare Your Sprite Sheet

Create a 128x128 PNG image with characters arranged in the layout above:
- Each character: 8x10 pixels
- 16 characters per row
- 4 rows total
- Top-left origin (0, 118)

Place it in `assets/ui/ctm.uiskin.png` (or any path you choose).

### Step 2: Load the Font

In your screen's `show()` or initialization method:

```java
import com.chromashift.helper.SpriteFontManager;

// Load font with default config (8x10 characters)
SpriteFontManager.load("default", "ui/ctm.uiskin.png");

// OR load with custom config
SpriteFontManager.FontConfig config = new SpriteFontManager.FontConfig(8, 10, 1f);
SpriteFontManager.load("custom", "ui/my_font.png", config);
```

### Step 3: Create Labels

**Method 1: Using UIHelper (Recommended)**

```java
import com.chromashift.helper.UIHelper;
import com.chromashift.helper.SpriteLabel;

// Simple label
SpriteLabel label = UIHelper.createSpriteLabel("hello world", "default");
stage.addActor(label);

// Scaled label (2x size)
SpriteLabel bigLabel = UIHelper.createSpriteLabel("big text!", "default", 2f);

// Colored label
SpriteLabel colorLabel = UIHelper.createSpriteLabel(
    "colored", "default", 1.5f, Color.CYAN
);

// Centered label
SpriteLabel centered = UIHelper.createSpriteLabel(
    "centered", "default", SpriteLabel.Align.CENTER
);
centered.setWidth(200); // Set container width for alignment

// Full customization
SpriteLabel custom = UIHelper.createSpriteLabel(
    "custom", "default", 
    2f,                           // scale
    Color.GREEN,                  // color
    SpriteLabel.Align.RIGHT,      // alignment
    2f                            // character spacing
);
```

**Method 2: Direct Construction**

```java
import com.chromashift.helper.SpriteFont;
import com.chromashift.helper.SpriteLabel;
import com.chromashift.helper.SpriteFontManager;

SpriteFont font = SpriteFontManager.get("default");
SpriteLabel label = new SpriteLabel(font, "direct construction");
label.setScale(1.5f);
label.setColor(Color.YELLOW);
label.setAlignment(SpriteLabel.Align.CENTER);
stage.addActor(label);
```

### Step 4: Add to UI Layout

```java
Table table = new Table();
table.setFillParent(true);

// Add labels to table
SpriteLabel title = UIHelper.createSpriteLabel("game title", "default", 3f);
table.add(title).pad(20).row();

SpriteLabel subtitle = UIHelper.createSpriteLabel("press start", "default", 1.5f);
table.add(subtitle).pad(10).row();

stage.addActor(table);
```

### Step 5: Update Text at Runtime

```java
// Change displayed text
label.setText("new text");

// Change style
label.setScale(2f);
label.setColor(Color.RED);
label.setAlignment(SpriteLabel.Align.RIGHT);
label.setSpacing(3f);

// Get text dimensions
float width = label.getPrefWidth();
float height = label.getPrefHeight();
```

## Advanced Usage

### Multiple Font Styles

```java
// Load multiple fonts
SpriteFontManager.load("retro", "ui/retro_font.png");
SpriteFontManager.load("modern", "ui/modern_font.png");
SpriteFontManager.load("handwritten", "ui/handwritten_font.png");

// Use different fonts in different places
SpriteLabel title = UIHelper.createSpriteLabel("Title", "modern", 3f);
SpriteLabel body = UIHelper.createSpriteLabel("Body text...", "retro", 1f);
SpriteLabel note = UIHelper.createSpriteLabel("Note!", "handwritten", 1.2f);
```

### Custom Character Dimensions

```java
// For fonts with larger characters (e.g., 16x20 pixels each)
SpriteFontManager.FontConfig config = new SpriteFontManager.FontConfig(16, 20);
SpriteFontManager.load("large", "ui/large_font.png", config);

// With custom spacing
SpriteFontManager.FontConfig tightConfig = new SpriteFontManager.FontConfig(8, 10, 0.5f);
SpriteFontManager.load("tight", "ui/tight_font.png", tightConfig);
```

### Direct SpriteBatch Drawing

For non-Scene2D rendering (e.g., during gameplay):

```java
SpriteFont font = SpriteFontManager.get("default");
SpriteBatch batch = new SpriteBatch();

batch.begin();
font.setColor(Color.WHITE);
font.draw(batch, "score: 1234", 10, 500);
font.draw(batch, "level 3", 10, 480, 2f); // 2x scale
batch.end();
```

### Measuring Text Width

```java
SpriteFont font = SpriteFontManager.get("default");
float width = font.getWidth("hello world");
float scaledWidth = font.getWidth("hello world", 2f);

// Center text manually
float x = (screenWidth - width) / 2f;
font.draw(batch, "centered text", x, y);
```

### Performance Optimization

```java
// Pre-load all fonts at startup
SpriteFontManager.load("font1", "ui/font1.png");
SpriteFontManager.load("font2", "ui/font2.png");
SpriteFontManager.load("font3", "ui/font3.png");

// Reuse labels instead of creating new ones
private SpriteLabel scoreLabel;

void create() {
    scoreLabel = UIHelper.createSpriteLabel("score: 0", "default");
}

void update(int score) {
    scoreLabel.setText("score: " + score); // Update, don't recreate
}

// Dispose when done (e.g., in game disposal)
SpriteFontManager.disposeAll();
```

### Dynamic Text Animation

```java
SpriteLabel animLabel = UIHelper.createSpriteLabel("pulsing text", "default");
float time = 0;

// In render loop:
time += delta;
float scale = 1f + 0.2f * MathUtils.sin(time * 2f);
animLabel.setScale(scale);

// Color pulse
Color color = new Color();
color.fromHsv(time * 100f % 360f, 1f, 1f);
animLabel.setColor(color);
```

## Scene2D Integration Examples

### In Tables

```java
Table table = new Table();
SpriteLabel header = UIHelper.createSpriteLabel("inventory", "default", 2f);
SpriteLabel item1 = UIHelper.createSpriteLabel("sword x3", "default");
SpriteLabel item2 = UIHelper.createSpriteLabel("potion x5", "default");

table.add(header).colspan(2).center().row();
table.add(item1).left().padRight(20);
table.add(item2).left().row();
```

### With Buttons

```java
Table buttonRow = new Table();
TextButton btn = UIHelper.createButton("Confirm", skin, clickListener);
SpriteLabel label = UIHelper.createSpriteLabel("cost: 50g", "default", 1f, Color.GOLD);

buttonRow.add(btn).padRight(10);
buttonRow.add(label);
```

### Scrollable List

```java
Table content = new Table();
for (int i = 0; i < 50; i++) {
    SpriteLabel item = UIHelper.createSpriteLabel("item " + i, "default");
    content.add(item).left().row();
}

ScrollPane scroll = new ScrollPane(content, skin);
table.add(scroll).size(300, 400);
```

## Character Set Reference

Supported characters:
- **Lowercase letters:** a-z
- **Numbers:** 0-9
- **Symbols:** # , . ! ? : * % ( ) + - \ / = > <
- **Space:** (invisible, advances cursor)

Unsupported characters render as `?` (if available) or are skipped.

## Cleanup

```java
// Unload specific font
SpriteFontManager.unload("default");

// Dispose all fonts (e.g., in Game.dispose())
@Override
public void dispose() {
    SpriteFontManager.disposeAll();
    super.dispose();
}
```

## Demo

See `TestMenuScreen.java` for a complete working example with:
- Basic sprite labels
- Scaled labels (2x)
- Colored labels (cyan)
- Centered labels
- Error handling

Run the game and open the test menu to see all demos in action!

## Troubleshooting

**Font not loading:**
- Verify texture path is correct
- Check texture dimensions match your layout
- Ensure texture is in `assets/` folder

**Characters not appearing:**
- Check character is in the supported set
- Verify sprite sheet has characters at correct positions
- Ensure texture coordinates match LibGDX's bottom-up system

**Text appears cut off:**
- Increase container width: `label.setWidth(desiredWidth)`
- Check alignment settings
- Verify scale isn't making text too large

**Performance issues:**
- Pre-load fonts at startup, not during gameplay
- Reuse labels instead of creating new ones each frame
- Use `setText()` to update existing labels
- Batch text draws in the same SpriteBatch

## Architecture

```
SpriteFontManager (Singleton)
    ├─ Manages loaded fonts
    └─ Handles resource disposal

SpriteFont (Core Renderer)
    ├─ Character mapping (char -> TextureRegion)
    ├─ Batch drawing
    └─ Width calculation

SpriteLabel (Scene2D Widget)
    ├─ Wraps SpriteFont
    ├─ Widget lifecycle
    └─ Layout integration

UIHelper (Factory)
    └─ Convenience methods
```

## Performance Notes

- Each character is a separate sprite draw call
- Batching happens automatically within SpriteBatch
- Pre-computed texture regions minimize CPU overhead
- No allocations during rendering
- Efficient for dynamic text that changes frequently

## License

Same as the main Chromashift project.

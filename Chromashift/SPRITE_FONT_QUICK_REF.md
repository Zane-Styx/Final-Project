# Sprite Font Quick Reference

## 1. Load Font (Once at Startup)
```java
SpriteFontManager.load("default", "ui/ctm.uiskin.png");
```

## 2. Create Label (UIHelper)
```java
// Basic
SpriteLabel label = UIHelper.createSpriteLabel("text", "default");

// Scaled
SpriteLabel label = UIHelper.createSpriteLabel("text", "default", 2f);

// Colored
SpriteLabel label = UIHelper.createSpriteLabel("text", "default", 2f, Color.CYAN);

// Aligned
SpriteLabel label = UIHelper.createSpriteLabel("text", "default", SpriteLabel.Align.CENTER);
```

## 3. Update at Runtime
```java
label.setText("new text");
label.setScale(2f);
label.setColor(Color.RED);
label.setAlignment(SpriteLabel.Align.RIGHT);
label.setSpacing(2f);
```

## 4. Add to Stage
```java
stage.addActor(label);
// OR
table.add(label).pad(10).row();
```

## 5. Direct Batch Drawing (Non-Scene2D)
```java
SpriteFont font = SpriteFontManager.get("default");
batch.begin();
font.draw(batch, "text", x, y);
font.draw(batch, "text", x, y, scale);
batch.end();
```

## 6. Cleanup
```java
SpriteFontManager.disposeAll(); // In game dispose()
```

## Supported Characters
```
a-z, 0-9, # , . ! ? : * % ( ) + - \ / = > < [space]
```

## Character Layout (128x128, 8x10 each)
```
Row 1: a-p
Row 2: q-z, 1-6
Row 3: 7-0, symbols
Row 4: \ / = > <
```

## Common Patterns

### Title Screen
```java
SpriteLabel title = UIHelper.createSpriteLabel("game title", "default", 4f);
table.add(title).center().padBottom(50).row();
```

### Score Display
```java
SpriteLabel score = UIHelper.createSpriteLabel("score: 0", "default", 1.5f);
// Update in game loop:
score.setText("score: " + playerScore);
```

### Button with Label
```java
TextButton btn = UIHelper.createButton("Buy", skin, listener);
SpriteLabel cost = UIHelper.createSpriteLabel("50g", "default", 1f, Color.GOLD);
table.add(btn).padRight(10);
table.add(cost);
```

### Centered Menu
```java
SpriteLabel item1 = UIHelper.createSpriteLabel("play", "default", 2f);
item1.setAlignment(SpriteLabel.Align.CENTER);
item1.setWidth(200);
table.add(item1).width(200).row();
```

### Animated Text
```java
// In render loop:
float scale = 1f + 0.2f * MathUtils.sin(time * 2f);
label.setScale(scale);
```

## Import Statements
```java
import com.chromashift.helper.SpriteFont;
import com.chromashift.helper.SpriteLabel;
import com.chromashift.helper.SpriteFontManager;
import com.chromashift.helper.UIHelper;
```

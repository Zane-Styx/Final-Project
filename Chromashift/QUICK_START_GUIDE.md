# Quick Start Guide: Using Diamonds and Shops in Your Game Screens

## Setup in Your Game Screen

### 1. Add Required Fields

```java
public class YourGameScreen implements Screen {
    private Player player;
    private Stage uiStage;
    private Array<Collectible> collectibles;
    private Array<Interactable> interactables;
    
    // ... other fields
}
```

### 2. Load Level with Collectibles and Shops

```java
@Override
public void show() {
    // Load level
    LevelLoader.Result result = LevelLoader.load("levels/level1.json");
    
    // Get collectibles (diamonds)
    collectibles = result.collectibles;
    
    // Get regular interactables
    interactables = result.interactables;
    
    // Manually create shops (they need player and stage references)
    LevelIO.LevelState state = LevelIO.load("levels/level1.json");
    if (state.shops != null) {
        for (LevelIO.LevelState.ShopData sd : state.shops) {
            Shop shop = new Shop(sd.x, sd.y, player, uiStage);
            interactables.add(shop);
        }
    }
    
    // Set up player UI
    player.attachUI(viewport);
}
```

### 3. Update Loop

```java
@Override
public void render(float delta) {
    // Update collectibles and check collection
    for (int i = collectibles.size - 1; i >= 0; i--) {
        Collectible c = collectibles.get(i);
        if (!c.isCollected()) {
            c.update(delta);
            c.checkCollision(player);
        } else {
            // Optional: remove collected items
            collectibles.removeIndex(i);
        }
    }
    
    // Update shops and other interactables
    for (Interactable interactable : interactables) {
        interactable.update(delta);
    }
    
    // Update player
    player.update(delta, groundY, solids, interactables, Arrays.asList(walls));
    
    // Update and draw UI stage (for shop dialogs)
    uiStage.act(delta);
    
    // Render
    batch.begin();
    
    // Render collectibles
    for (Collectible c : collectibles) {
        if (!c.isCollected()) {
            c.render(batch);
        }
    }
    
    // Render other game objects...
    
    batch.end();
    
    // Render UI stage last (on top of everything)
    uiStage.draw();
}
```

### 4. Cleanup

```java
@Override
public void dispose() {
    // Dispose collectibles
    for (Collectible c : collectibles) {
        c.dispose();
    }
    
    // Dispose shops
    for (Interactable i : interactables) {
        if (i instanceof Shop) {
            ((Shop) i).dispose();
        }
    }
    
    uiStage.dispose();
}
```

## Customizing the Shop

### Adding Custom Items

```java
// After creating shop
Shop shop = new Shop(x, y, player, uiStage);

// Clear default items if you want a completely custom shop
shop.clearInventory();

// Add your custom items
shop.addItem("Magic Sword", 50, () -> {
    // Your custom purchase logic
    player.addAttackPower(10);
    System.out.println("Purchased Magic Sword!");
});

shop.addItem("Life Extension", 100, () -> {
    player.getHealthSystem().setMaxHealth(
        player.getHealthSystem().getMaxHealth() + 50f
    );
});

shop.addItem("Free Diamonds!", 0, () -> {
    player.addDiamonds(5);
});
```

## Testing Without Assets

If you don't have the diamond.png sprite yet:
1. The Diamond class will log an error but won't crash
2. Diamonds will still be collectible (just invisible)
3. Diamond count will still increment properly
4. You can test the full system without sprites

## Player Interaction Keys

Make sure your player has an interaction key set up. The player should call `interactable.interact()` when:
- Player presses the interact key (usually 'E' or 'F')
- Player is close enough to the interactable
- The interactable's `canInteract()` returns true

Example interaction check:
```java
if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
    Rectangle playerBounds = new Rectangle(
        player.getHitboxX(), 
        player.getHitboxY(), 
        player.getHitboxWidth(), 
        player.getHitboxHeight()
    );
    
    for (Interactable i : interactables) {
        if (i.canInteract() && playerBounds.overlaps(i.getBounds())) {
            i.interact();
            break;
        }
    }
}
```

## Common Issues

### Shop doesn't open
- Check that `uiStage` is not null
- Verify that `player` reference is set
- Make sure input processor includes the stage: 
  ```java
  InputMultiplexer multiplexer = new InputMultiplexer();
  multiplexer.addProcessor(uiStage);
  multiplexer.addProcessor(gameInputProcessor);
  Gdx.input.setInputProcessor(multiplexer);
  ```

### Player can't move after closing shop
- This shouldn't happen if using the updated Shop code
- As a backup, manually call `player.setStunned(false)` after shop interaction

### Diamond sprite doesn't show
- Check that `assets/environment/diamond.png` exists
- Verify it's 192×32 pixels (6 frames of 32×32)
- Check that the asset is in the correct location
- Look for loading errors in the console

### Diamonds collected multiple times
- This shouldn't happen with the current code
- The `checkCollision` method sets `collected = true`
- Make sure you're not manually resetting the collected state

## Advanced: Creating Custom Collectibles

Extend the Collectible base class:

```java
public class HealthGem extends Collectible {
    public HealthGem(float x, float y) {
        super(x, y, 24, 24); // 24x24 size
    }
    
    @Override
    public void update(float delta) {
        // Add animation logic
    }
    
    @Override
    public void render(SpriteBatch batch) {
        // Draw your sprite
    }
    
    @Override
    public void onCollect(Player player) {
        player.getHealthSystem().heal(25f);
        SoundManager.play("Heal");
    }
    
    @Override
    public void dispose() {
        // Cleanup resources
    }
}
```

Then add it to the level loader's build method:
```java
// In LevelLoader.build()
if (state.healthGems != null) {
    for (HealthGemData hg : state.healthGems) {
        out.collectibles.add(new HealthGem(hg.x, hg.y));
    }
}
```

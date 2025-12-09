# Level Loading System

## Overview
The loading system ensures all level objects (tiles, enemies, bosses, triggers, props, etc.) are fully initialized before normal gameplay begins. During the loading phase, player movement, collisions, enemy AI, and boss attack logic are disabled. Only when every object reports "ready" does the game enable its update loop, preventing issues like:
- Player spawning inside walls
- Falling through floors
- Getting hit by bosses during fade-in

## Architecture

### Core Components

#### 1. LoadableObject Interface
```java
void initialize();                  // Called during loading phase
boolean isReady();                  // Returns true when object fully loaded
String getLoadingDescription();     // Human-readable name for progress display
```

#### 2. LevelLoadingState Enum
9-state progression:
1. `LOADING_ENVIRONMENT` - Walls, solids, tiles
2. `LOADING_INTERACTABLES` - Buttons, levers, etc.
3. `LOADING_ENEMIES` - Tentacle enemies
4. `LOADING_BOSSES` - Boss entities
5. `LOADING_COLLECTIBLES` - Diamonds, power-ups
6. `LOADING_PLAYER` - Player entity
7. `FINALIZING` - System prep
8. `FADING_IN` - Visual fade transition (1.0 → 0.0 alpha)
9. `READY` - Gameplay enabled

#### 3. LevelLoadingManager
- **Progress Tracking**: `getProgress()` returns 0.0-1.0 completion percentage
- **Object Registration**: `registerLoadableObject(LoadableObject obj)`
- **Lifecycle**: 
  - `startLoading()` - Initialize all objects and begin state progression
  - `update(delta)` - Check readiness, advance states, update fade
  - `isReady()` / `isLoading()` - Query current state
- **Fade System**: Linear alpha interpolation at 1.5 units/sec (configurable via `fadeSpeed`)
- **Callback**: `setOnLoadingComplete(Runnable callback)` for gameplay activation

#### 4. LoadingOverlay
Visual rendering component:
- Progress bar (400×20 pixels, cyan on dark gray)
- Status text showing current loading state
- Percentage complete
- Object count (ready/total)
- Black fade overlay using ShapeRenderer
- Uses viewport camera for proper UI projection

#### 5. Wrapper Classes
Implement `LoadableObject` for existing game entities:
- `LoadableBoss` - Wraps Boss entities
- `LoadablePlayer` - Wraps Player entity
- `LoadableEnvironment` - Groups walls, solids, interactables, collectibles
- `LoadableEnemies` - Wraps tentacle arrays

**Note**: Current implementations mark `ready=true` immediately in `initialize()` since objects are already created synchronously by LevelLoader. This system provides initialization verification and controlled gameplay activation.

## Integration (TestSceneScreen)

### Initialization (show() method)
```java
// Create loading system
loadingManager = new LevelLoadingManager();
loadingOverlay = new LoadingOverlay(loadingManager, batch, shape, font, new ScreenViewport());
gameplayEnabled = false;

// Register all level objects
registerLoadableObjects();

// Set completion callback
loadingManager.setOnLoadingComplete(() -> {
    gameplayEnabled = true;
    Gdx.app.log("TestSceneScreen", "Level loading complete - gameplay enabled!");
});

// Start loading sequence
loadingManager.startLoading();
```

### Render Flow
```java
public void render(float delta) {
    // Update loading manager first
    if (loadingManager != null && !loadingManager.isReady()) {
        loadingManager.update(delta);
    }
    
    // ... (minimal updates: background animator, ESC key)
    
    // Skip gameplay if not ready
    if (!gameplayEnabled || (loadingManager != null && !loadingManager.isReady())) {
        renderLoadingScreen(delta);
        return;
    }
    
    // ... (normal gameplay updates: player, enemies, boss, collisions, etc.)
    
    // Render loading overlay on top of everything (for fade effect)
    if (loadingManager != null && loadingOverlay != null) {
        loadingOverlay.render();
    }
}
```

### Loading Screen Render
```java
private void renderLoadingScreen(float delta) {
    Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    
    if (loadingOverlay != null) {
        loadingOverlay.render();
    }
}
```

## Configuration

### Timing Adjustments
In `LevelLoadingManager.java`:
```java
private float fadeSpeed = 1.5f;        // Fade transition speed
private float minStateTime = 0.1f;     // Minimum time per state (prevents flashing)
```

### Visual Customization
In `LoadingOverlay.java`:
```java
private float progressBarWidth = 400f;
private float progressBarHeight = 20f;
private Color progressBarColor = new Color(0.3f, 0.7f, 1.0f, 1f);  // Cyan
```

## Testing Checklist

- [ ] All objects register correctly in `registerLoadableObjects()`
- [ ] Gameplay stays disabled during loading states
- [ ] Player cannot move/take damage during loading
- [ ] Enemies do not update AI during loading
- [ ] Boss attacks do not trigger during loading
- [ ] Progress bar displays correctly
- [ ] Status text updates through all 9 states
- [ ] Fade-in effect is smooth (not jarring)
- [ ] `gameplayEnabled` flag activates exactly when fade completes
- [ ] No race conditions between object creation and gameplay start
- [ ] ESC key still works during loading (can exit to menu)

## Future Enhancements

1. **Debug Controls**: Add F11 key to force-skip loading for testing
2. **Async Loading**: Extend wrapper classes to support true async initialization
3. **Error Handling**: Add timeout detection for objects that never become ready
4. **Multi-Screen**: Apply loading system to LevelMakerScreen if needed
5. **Custom Animations**: Replace progress bar with animated logo/spinner
6. **Loading Tips**: Display gameplay tips during longer loads

## File Locations

**Core System**:
- `core/src/main/java/com/jjmc/chromashift/screens/levels/LoadableObject.java`
- `core/src/main/java/com/jjmc/chromashift/screens/levels/LevelLoadingState.java`
- `core/src/main/java/com/jjmc/chromashift/screens/levels/LevelLoadingManager.java`
- `core/src/main/java/com/jjmc/chromashift/screens/levels/LoadingOverlay.java`

**Wrappers**:
- `core/src/main/java/com/jjmc/chromashift/screens/levels/LoadableBoss.java`
- `core/src/main/java/com/jjmc/chromashift/screens/levels/LoadablePlayer.java`
- `core/src/main/java/com/jjmc/chromashift/screens/levels/LoadableEnvironment.java`
- `core/src/main/java/com/jjmc/chromashift/screens/levels/LoadableEnemies.java`

**Integration**:
- `core/src/main/java/com/jjmc/chromashift/screens/TestSceneScreen.java` (lines 83-86, 120-126, 260-309, 773-787)

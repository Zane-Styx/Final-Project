# BossGuardian Spawn Sequence Integration Guide

## Overview
The BossGuardian boss now features a complete spawn sequence system that displays all three guardians entering one at a time with cinematic camera effects.

## Spawn Sequence Breakdown

### Phase 1: Guardian 2 Enters from Left (1.8s)
- Guardian 2 slides in smoothly from the left side
- Ease-out animation for natural deceleration
- Camera begins zooming in (1.0x → 1.2x zoom)

### Phase 2: Guardian 3 Enters from Right (1.8s)
- Guardian 3 slides in from the right side after Guardian 2 settles
- Same ease-out animation for consistency
- 0.4s delay between Guardian 2 and Guardian 3 arrivals

### Phase 3: Guardian 1 Drops from Top (1.5s)
- Guardian 1 drops down from above
- Ease-out animation with bounce effect
- 0.4s delay between Guardian 3 and Guardian 1 arrivals

### Phase 4: Camera Return to Normal
- Last 20% of spawn sequence gradually returns camera to normal (1.0x zoom)
- Focus smoothly returns to gameplay center

## Total Spawn Duration
**~6.5 seconds total** with all timing and delays included.

## Integration in TestSceneScreen

### Step 1: Initialize and Start Spawn
```java
// When creating BossGuardian in your level
bossGuardian = new BossGuardian();
bossGuardian.setEnvironment(solids, walls);

// Register spawn completion callback
bossGuardian.setOnSpawnSequenceComplete(() -> {
    // Called when all three guardians have finished appearing
    // Re-enable player controls, start boss AI, etc.
    gameplayEnabled = true;
    Gdx.app.log("BossGuardian", "Spawn sequence complete - boss fight begins!");
});

// Start the spawn sequence
bossGuardian.startSpawn();
```

### Step 2: Update Camera During Spawn
In your `render()` or camera update method:

```java
// Check if spawn is active
if (bossGuardian.isSpawning()) {
    // Get spawn camera zoom
    float spawnZoom = bossGuardian.getSpawnCameraZoom();
    camera.zoom = spawnZoom;
    
    // Get spawn camera focus (optional - for dramatic effect)
    Vector2 spawnFocus = bossGuardian.getSpawnCameraFocus();
    if (spawnFocus != null) {
        // Lerp camera position toward spawn focus
        camera.position.x += (spawnFocus.x - camera.position.x) * 0.05f;
        camera.position.y += (spawnFocus.y - camera.position.y) * 0.05f;
    }
} else if (!bossGuardian.hasSpawned() && bossGuardian.getSpawnCameraZoom() > 1.0f) {
    // Smoothly return to normal zoom after spawn
    camera.zoom += (1.0f - camera.zoom) * 0.04f;
}
```

### Step 3: Disable Gameplay During Spawn
```java
// In your render() method, skip player/enemy updates during spawn
if (bossGuardian.isSpawning()) {
    // Don't update player controls
    // Don't run boss attack logic
    // Don't process collisions
    // Just render and update camera
} else {
    // Normal gameplay updates
    player.update(delta);
    bossGuardian.update(delta);
    // ... etc
}
```

## Customization

All timing parameters can be adjusted in the BossGuardian constructor or via public fields:

### Timing Parameters
```java
private float guardian2EntryDuration = 1.8f;  // Time for Guardian 2 to slide in
private float guardian3EntryDuration = 1.8f;  // Time for Guardian 3 to slide in
private float guardian1DropDuration = 1.5f;   // Time for Guardian 1 to drop
private float delayBetweenGuardians = 0.4f;   // Gap between each entrance
private float spawnPhaseStartDelay = 0.5f;    // Initial delay before Guardian 2
```

### Camera Timing
```java
private float cameraZoomReturnSpeed = 0.8f;   // How quickly zoom returns to 1.0
private float cameraFocusReturnSpeed = 0.6f;  // How quickly focus returns
```

### Example: Faster Spawn (4 seconds total)
```java
bossGuardian.setSpawnDuration(4.0f);
// Adjust in constructor and rebuild or add setters as needed
```

## Available Methods

### Query Methods
```java
// Check if spawn is currently active
boolean spawning = bossGuardian.isSpawning();

// Check if spawn completed
boolean completed = bossGuardian.hasSpawned();

// Get current camera zoom (1.0 = normal, 1.2 = zoomed in)
float zoom = bossGuardian.getSpawnCameraZoom();

// Get camera focus point during spawn (null if not spawning)
Vector2 focus = bossGuardian.getSpawnCameraFocus();
```

### Configuration Methods
```java
// Set callback when spawn completes
bossGuardian.setOnSpawnSequenceComplete(() -> {
    // Gameplay starts here
});

// Start the spawn sequence (called automatically if using loading system)
bossGuardian.startSpawn();

// Configure spawn duration (in seconds)
bossGuardian.setSpawnDuration(8.0f);
```

## Visual Flow

```
T=0.0s: Guardian 2 starts sliding from left
        Camera begins zoom in (1.0 → 1.2)
        
T=1.8s: Guardian 2 reaches position, settles
T=2.2s: Guardian 3 starts sliding from right
        
T=4.0s: Guardian 3 reaches position, settles
T=4.4s: Guardian 1 starts dropping from top
        
T=5.9s: Guardian 1 reaches position
        All three guardians now visible and idle
        Camera begins returning to normal zoom
        
T=6.5s: Spawn complete
        Camera back to 1.0x zoom
        Ready for boss fight!
```

## Integration with Level Loading System

If using the `LevelLoadingManager`, the spawn sequence can be triggered automatically:

```java
// In your LoadableObject wrapper
public class LoadableBossGuardian implements LoadableObject {
    private BossGuardian boss;
    
    @Override
    public void initialize() {
        // Start spawn after level assets loaded
        boss.startSpawn();
    }
    
    @Override
    public boolean isReady() {
        // Consider ready when spawn is complete
        return boss.hasSpawned();
    }
}
```

## Animation Details

### Easing Functions Used

**Guardian 2 & 3 (Horizontal Entry):**
- Ease-out cubic: `1 - (1-t)³`
- Starts fast, slows down for smooth arrival

**Guardian 1 (Vertical Drop):**
- Ease-out quadratic: `1 - (1-t)²·⁵`
- Similar effect with slightly different curve for drop feel

### Guardian Positioning

During spawn:
- **Guardian 2**: Moves from `centerX - 800` to `centerX + offset2X`
- **Guardian 3**: Moves from `centerX + 800` to `centerX + offset3X`
- **Guardian 1**: Moves from `centerY - 400` to `centerY + offset1Y`

After spawn, they return to normal triangle formation rotation.

## Performance Considerations

- Spawn animations run at half speed (`delta * 0.5f`) to reduce processing
- All transitions use simple lerp/ease functions (O(1) operations)
- No garbage allocation during spawn (reuses Vector2 instances)
- Camera updates are optional (only needed for visual polish)

## Troubleshooting

### Guardians not appearing?
- Check `bossGuardian.isSpawning()` returns true
- Verify guardian positions are being updated in `updateSpawn()`
- Ensure `render()` is called even during spawn

### Camera not zooming?
- Confirm `getSpawnCameraZoom()` is being used in camera update
- Check camera.zoom is being set to returned value
- Verify spawn is actually active

### Spawn completes too fast?
- Adjust timing parameters in constructor
- Check if `delta` is being passed correctly to `update()`
- Verify spawn duration matches total of all phases

## Future Enhancements

Consider adding:
- Particle effects during guardian arrivals
- Sound effects for each entrance
- Screen shake on impact
- Guardian-specific arrival animations
- Dialogue/text overlays during spawn

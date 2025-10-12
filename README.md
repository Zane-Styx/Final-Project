# **com.chromashift.helper Package Developer Guide**

**Purpose:**  
This package contains all utility classes for animation, audio, camera behavior, and tilemap handling.  
Each class is **self-contained**, **reusable**, and designed for **flexible integration** with any LibGDX project — whether it’s a 2D platformer, action, or adventure game.

---

## Overview

| Class                                      | Purpose                                                        |
| ------------------------------------------ | -------------------------------------------------------------- |
| [`SpriteAnimator`](#1-spriteanimator)      | Frame-based animation handler for sprite sheets.               |
| [`SoundManager`](#2-soundmanager)          | Centralized sound and music management system.                 |
| [`TileMapLoader`](#3-tilemaploader)        | TMX map loader with physics and background music support.      |
| [`CameraController`](#4-cameracontroller)  | Smooth camera follow system with bounds, dead zone, and shake. |
| [`CameraShake`](#5-camerashake)            | Handles screen shake offsets for impact feedback.              |
| [`CameraBounds`](#6-camerabounds)          | Restrains camera movement within world limits.                 |

---

## 1.  SpriteAnimator

### **Description**
Manages sprite animations from sprite sheets or separate textures.  
Supports multiple animation sources, flipping, looping, and frame duration control.

### **Key Methods**

| Method                                                                                                                                | Description                                     |
| ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| `SpriteAnimator(String spritePath, int rows, int cols)`                                                                               | Loads a sprite sheet divided into rows/columns. |
| `addAnimation(String name, int row, int startFrame, int endFrame, float frameDuration, boolean loop)`                                 | Adds an animation sequence.                     |
| `addAnimationFromTexture(String name, String texturePath, int frameW, int frameH, int frameCount, float frameDuration, boolean loop)` | Adds an animation from a separate texture.      |
| `play(String name, boolean flipX)`                                                                                                    | Plays a specific animation.                     |
| `update(float delta)`                                                                                                                 | Updates the animation timer.                    |
| `render(SpriteBatch batch, float x, float y, float width, float height)`                                                              | Renders the current frame.                      |
| `isAnimationFinished()`                                                                                                               | Checks if a non-looping animation ended.        |
| `getCurrentAnimationName()`                                                                                                           | Gets the active animation name.                 |
| `dispose()`                                                                                                                           | Frees all texture resources.                    |

### **Example**

```java
SpriteAnimator anim = new SpriteAnimator("sprites/player.png", 4, 10);
anim.addAnimation("idle", 0, 0, 3, 0.1f, true);
anim.addAnimation("run", 1, 0, 5, 0.08f, true);
anim.addAnimationFromTexture("attack", "sprites/player_attack.png", 64, 64, 6, 0.07f, false);

// In game loop
anim.play("run", false);
anim.update(delta);
anim.render(batch, playerX, playerY, 64, 64);
```
---

## 2. SoundManager

### **Description**
A complete sound and music manager for LibGDX.  
Supports:
- Background music playlists & shuffling
- Sound effect cutoff control
- Crossfading and smooth volume transitions
- Separate master, music, and SFX channels

### **Key Methods**

| Method | Description |
|--------|--------------|
| `init(boolean cutoff)` | Initializes manager; `cutoff=true` stops old sounds before new ones. |
| `addSound(String name, String path)` | Register sound effect. |
| `addMusic(String group, String path, boolean loop)` | Register music track under a group name. |
| `play(String name)` | Plays a sound or music by name/group. |
| `playPlaylist(String group, float crossfadeDuration, boolean shuffle)` | Plays a shuffled or sequential playlist with crossfade. |
| `fadeMusicVolume(float target, float duration)` | Smoothly fades music volume. |
| `setMasterVolume(float volume)` | Directly sets global volume. |
| `stopAll()` | Stops all audio. |
| `dispose()` | Disposes all loaded assets. |

### **Example**

```java
SoundManager.init(true);
SoundManager.addSound("jump", "sfx/jump.wav");
SoundManager.addMusic("bgm", "music/level1.ogg", true);
SoundManager.addMusic("bgm", "music/level2.ogg", true);

SoundManager.playPlaylist("bgm", 2f, true); // Crossfade & shuffle
SoundManager.play("jump"); // Play sound effect

// Fade volume when pausing
SoundManager.fadeMusicVolume(0.2f, 1.5f);
```
---

## 3. TileMapLoader

### **Description**
Handles Tiled (.tmx) maps — rendering, physics generation, and background music linking.  
Features include:
- Auto collision generation (rectangles, polygons, polylines)
- Layer-based physics properties
- Optional Box2D integration
- Auto background music playback via SoundManager

### **Key Methods**

| Method | Description |
|--------|--------------|
| `TileMapLoader(String path, float unitScale, World world, SoundManager sm)` | Loads a TMX file. |
| `setCollisionLayers(String... layers)` | Specify which layers have physics. |
| `renderLayers(String... names)` | Render specific map layers. |
| `renderDebug()` | Renders physics debug overlay. |
| `setCamera(OrthographicCamera cam)` | Attach camera. |
| `setDebug(boolean enabled)` | Toggle debug renderer. |
| `dispose()` | Cleans all resources. |

### **Example**

```java
World world = new World(new Vector2(0, -9.8f), true);
TileMapLoader mapLoader = new TileMapLoader("maps/level1.tmx", 1/16f, world, SoundManager);
mapLoader.setCamera(camera);
mapLoader.setCollisionLayers("Ground", "Walls");

// Inside render()
mapLoader.renderLayers("Background", "Ground", "Decor");
mapLoader.renderDebug();
```
---

## 4. CameraController

### **Description**
Controls an `OrthographicCamera` that smoothly follows a target with adjustable **dead zones**, **zoom**, and **shake**.

### **Key Methods**

| Method                                        | Description                                                |
| --------------------------------------------- | ---------------------------------------------------------- |
| `setTarget(Vector2 target)`                   | Sets position for camera to follow.                        |
| `setFollowMode(FollowTargetMode mode)`        | Switches follow behavior (NONE / PLAYER / POINT / LOCKED). |
| `setBounds(CameraBounds bounds)`              | Restricts camera movement.                                 |
| `setDeadZone(float width, float height)`      | Sets dead zone dimensions.                                 |
| `setSmoothSpeed(float speed)`                 | Controls camera movement smoothness.                       |
| `setZoom(float zoom)`                         | Instantly sets zoom level.                                 |
| `setTargetZoom(float zoom, float smoothTime)` | Smoothly transitions to target zoom.                       |
| `setDebugZoneVisible(boolean visible)`        | Enables visual dead zone for debugging.                    |
| `lockCamera(Vector3 position)`                | Fixes camera at position (e.g. for cutscenes).             |
| `unlockCamera()`                              | Resumes normal following.                                  |
| `shake(float intensity, float duration)`      | Triggers screen shake effect.                              |
| `update(float delta)`                         | Updates position and zoom.                                 |

### **Examples**

#### Player Follow
```java
cameraController.setFollowMode(CameraController.FollowTargetMode.PLAYER);
cameraController.setTarget(new Vector2(player.getX(), player.getY()));
cameraController.update(delta);

batch.setProjectionMatrix(cameraController.getCamera().combined);
```
#### Boss Fight Zoom Out
```java
if (bossActive) {
    cameraController.lockCamera(new Vector3(boss.getX(), boss.getY(), 0));
    cameraController.setTargetZoom(1.6f, 2f);
} else {
    cameraController.unlockCamera();
    cameraController.setTargetZoom(1f, 1.5f);
}
```
#### Screen Shake
```java
if (playerHit) {
    cameraController.shake(10f, 0.4f);
}
```
#### Debug Dead Zone
```java
cameraController.setDeadZone(120, 80);
cameraController.setDebugZoneVisible(true);
```
---

## 5. CameraShake

### **Description**
Applies short randomized offsets to simulate camera shaking.

### **Key Methods**

| Method                                   | Description                                     |
| ---------------------------------------- | ----------------------------------------------- |
| `start(float intensity, float duration)` | Begins shaking.                                 |
| `isShaking()`                            | Returns true while active.                      |
| `Vector3 update(float delta)`            | Returns the offset to apply to camera position. |

### **Example**

```java
CameraShake shake = new CameraShake();
shake.start(8f, 0.5f);

if (shake.isShaking()) {
    Vector3 offset = shake.update(delta);
    camera.position.add(offset);
}
```
---

## 6. CameraBounds

### **Description**
Restricts camera to stay within a world region.

### **Key Methods**

| Method                                                               | Description                           |
| -------------------------------------------------------------------- | ------------------------------------- |
| `CameraBounds(float minX, float minY, float maxX, float maxY)`       | Defines camera boundaries.            |
| `Vector3 clamp(Vector3 position, float viewWidth, float viewHeight)` | Clamps camera position within limits. |

### **Example**

```java
CameraBounds bounds = new CameraBounds(0, 0, 5000, 1200);
cameraController.setBounds(bounds);
```
---

## Integration Example (Full Setup)

```java
OrthographicCamera cam = new OrthographicCamera();
CameraController cameraController = new CameraController(cam);
CameraBounds bounds = new CameraBounds(0, 0, 5000, 1200);

cameraController.setBounds(bounds);
cameraController.setFollowMode(CameraController.FollowTargetMode.PLAYER);
cameraController.setDeadZone(150, 100);
cameraController.setSmoothSpeed(4f);

// Game loop
cameraController.setTarget(new Vector2(player.getX(), player.getY()));
cameraController.update(delta);
batch.setProjectionMatrix(cameraController.getCamera().combined);

if (bossAppears) {
    cameraController.lockCamera(new Vector3(boss.getX(), boss.getY(), 0));
    cameraController.setTargetZoom(1.5f, 1.2f);
}
```
---

## Integration Tips

| System | Suggested Use |
|---------|----------------|
| `SpriteAnimator` | Call `update()` before rendering player; `dispose()` on screen exit. |
| `SoundManager` | Initialize once (in main game class); reuse globally. |
| `TileMapLoader` | Load maps per level; call `dispose()` when switching maps. |
| `CameraController` | Update after player movement but before rendering map/layers. |
---

## Recommended Package Structure

```
com.chromashift.helper
│
├── CameraController.java
├── CameraShake.java
├── CameraBounds.java
├── SoundManager.java
├── SpriteAnimator.java
└── TileMapLoader.java
```
---

**End of `com.chromashift.helper` Developer Guide**

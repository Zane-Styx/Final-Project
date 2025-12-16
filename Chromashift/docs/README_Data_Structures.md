# Data Structures Used

This document lists only the data structures present and used in this project, with brief explanations grounded in the codebase.

## LibGDX Geometry
- **Rectangle**
  - **Why chosen:** Axis-aligned bounding boxes are simple and fast for collision checks.
  - **Implementation:** Created and used directly from `com.badlogic.gdx.math.Rectangle`.
    - Example/pseudocode:
      - Create: `Rectangle r = new Rectangle(x, y, w, h)`
      - Overlap: `if (r.overlaps(other)) { /* handle collision */ }`
  - **Where used:**
    - Player hitbox and attack areas in `Player` and `PlayerLogic`.
    - Enemy and environment collision (`GuardianCrystal.getBounds()`, `IceWall.getDamageHitbox()`).
    - Solid and wall collision resolution (`PlayerCollision.resolveSolidCollision`, `resolveWallCollision`).
  - **Memory notes:** Lightweight object; frequently constructed as temporaries (e.g., sensors). Avoids heavy allocations.

- **Circle**
  - **Why chosen:** Compact sensor shape for wall proximity checks; rotationally symmetric detection.
  - **Implementation:** `com.badlogic.gdx.math.Circle` for simple sensor checks.
    - Example/pseudocode:
      - Create: `Circle c = new Circle(x, y, r)`
      - Check: `PlayerCollision.checkWallCollision(c, walls)`
  - **Where used:**
    - Player wall sensor and back sensor in `Player.updateWallSensor()`.
  - **Memory notes:** Very small; used as persistent sensors cached on the player.

## LibGDX Collections
- **Array<T> (com.badlogic.gdx.utils.Array)**
  - **Why chosen:** Low-overhead, resizable array optimized for LibGDX without boxing costs of Java collections in some contexts.
  - **Implementation:** Used for dynamic lists of game objects and resources.
    - Example/pseudocode:
      - Declare: `Array<Enemy> enemies = new Array<>()`
      - Iterate backwards for removal: `for (int i = arr.size - 1; i >= 0; i--) { if (shouldRemove) arr.removeIndex(i); }`
  - **Where used:**
    - Player enemies list (`Player.setEnemies()`), solids, projectiles (`Player.activeProjectiles`).
    - Guardian crystal projectiles and ice walls arrays.
    - Passing walls/solids to player movement logic.
  - **Memory notes:** Backing array resizes; removal by index avoids iterator allocation.

## Game Helper Classes (as structured data containers)
- **SpriteAnimator**
  - **Why chosen:** Centralized frame animation management for sprites.
  - **Implementation:** Constructed with texture paths and frame layout; exposes `addAnimation()`, `play()`, `update()`, `render()`.
    - Example/pseudocode:
      - `anim = new SpriteAnimator(path, rows, cols)`
      - `anim.addAnimation(name, row, startCol, frameCount, speed, looping)`
      - `anim.play(name, flipX)`
  - **Where used:**
    - Player animations (run, jump, dash, attack).
    - Guardian crystal spawn/idle.
    - Ice wall playback.
  - **Memory notes:** Holds texture regions and timing; reused per entity to avoid reallocation.

- **HealthSystem**
  - **Why chosen:** Encapsulates HP, regen, invulnerability, and death callbacks.
  - **Implementation:** Instantiated per player; extended via anonymous subclass to override `damage()`.
    - Example/pseudocode:
      - `HealthSystem hs = new HealthSystem(maxHp) { @Override boolean damage(float a, Object s) { /* shield, invuln */ } }`
  - **Where used:**
    - Player HP management; listeners for health change and death.
  - **Memory notes:** Persistent per-player; listeners maintained internally.

package com.jjmc.chromashift.entity.boss;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.chromashift.helper.SpriteAnimator;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.enemy.Enemy;
import com.jjmc.chromashift.enemy.skill.EnemyProjectile;
import com.badlogic.gdx.math.Vector2;

/**
 * GuardianCrystal: spawned by Guardian 1 at a specific attack frame.
 * - Plays a spawn animation once, then idles/attacks.
 * - Falls straight down until grounded; then hovers 50–80px above ground with subtle sine motion.
 * - Hit-based health: dies after 4 valid hits, independent of damage value.
 * - Provides two attacks with independent cooldowns (logic hooks).
 */
public class GuardianCrystal implements Enemy {
    // Anim
    private static final String CRYSTAL_TEXTURE = "entity/boss1/skill/crystal.png";
    private static final int CRYSTAL_FRAME_W = 32;
    private static final int CRYSTAL_FRAME_H = 64;
    private static final int CRYSTAL_TOTAL_FRAMES = 24;
    private static final float CRYSTAL_ANIM_SPEED = 0.08f;

    private final SpriteAnimator anim;
    private boolean spawnDone = false; // set true after spawn animation completes

    // Position/physics
    private float x; // left
    private float y; // bottom
    private float prevY = Float.NaN;
    private float vx = 0f;
    private float vy = 0f;
    private boolean grounded = false;
    private float groundY = Float.NEGATIVE_INFINITY;
    private final Rectangle bounds;

    // Hover
    private float hoverBaseY = 0f;
    private float hoverRangeMin = 35f;
    private float hoverRangeMax = 40f;
    private float hoverPhase = 0f;
    private float hoverSpeed = 1.2f; // radians/sec

    // Health (hit-based)
    private int hitsRemaining = 4;
    private boolean alive = true;

    // On-destroy callback (provides center position)
    private java.util.function.BiConsumer<Float, Float> onDestroyed;

    // Attacks cooldowns
    private float icePickCooldown = 0f;
    private float iceWallCooldown = 0f;
    private static final float ICE_PICK_COOLDOWN_MAX = 8.0f;
    // Make Ice Wall share the same cooldown as Ice Pick
    private static final float ICE_WALL_COOLDOWN_MAX = ICE_PICK_COOLDOWN_MAX;

    // Player tracking for attack direction and proximity checks
    private com.jjmc.chromashift.player.Player player;

    // Environment solids for ground detection
    private Array<Solid> solids;

    // Enemy projectiles spawned by this crystal
    private final Array<EnemyProjectile> projectiles = new Array<>();
    // Stationary ice walls spawned by this crystal
    private final Array<com.jjmc.chromashift.enemy.skill.IceWall> iceWalls = new Array<>();

    // Red flash telegraph (replaces HSV usage)
    private final Color tintColor = new Color(1,1,1,1);
    private static final float COLOR_FLASH_DURATION = 1.0f; // total flash time
    private static final float COLOR_FLASH_INTERVAL = 0.15f; // toggle interval for red/normal
    private static final float ICE_WALL_WINDUP_DURATION = 1.0f; // ice wall windup duration
    private boolean flashActive = false;
    private boolean flashOn = false;
    private float flashTimer = 0f;
    private float flashCycleAccum = 0f;

    // Ice Wall windup synced to HSV telegraph
    private float iceWallWindupTimer = 0f; // counts down toward spawn
    private boolean iceWallWindupActive = false;
    private boolean iceWallWindupLeft = false;

    public GuardianCrystal(float centerX, float centerY, com.jjmc.chromashift.player.Player player,
                           Array<Solid> solids) {
        this.x = centerX - CRYSTAL_FRAME_W / 2f;
        this.y = centerY - CRYSTAL_FRAME_H / 2f;
        this.player = player;
        this.solids = solids;
        this.bounds = new Rectangle(x, y, CRYSTAL_FRAME_W, CRYSTAL_FRAME_H);

        anim = new SpriteAnimator(CRYSTAL_TEXTURE, 1, CRYSTAL_TOTAL_FRAMES);
        anim.addAnimation("SPAWN", 0, 0, CRYSTAL_TOTAL_FRAMES, CRYSTAL_ANIM_SPEED, false);
        // Looping idle reuses the same frames for a subtle shimmer
        anim.addAnimation("IDLE", 0, 0, CRYSTAL_TOTAL_FRAMES, CRYSTAL_ANIM_SPEED, true);
        anim.play("SPAWN", false);
    }

    public void update(float delta) {
        if (!alive) return;

        // Update animation
        anim.update(delta);

        // Physics: fall until grounded
        if (!grounded) {
            prevY = y;
            vy -= 980f * delta; // gravity px/s^2
            y += vy * delta;

            // Clamp to designated arena ground at Y=246 if passed below it
            if (y <= 246f) {
                grounded = true;
                groundY = 246f;
                y = groundY;
                vy = 0f;
                hoverBaseY = groundY + MathUtils.random(hoverRangeMin, hoverRangeMax);
            } else {
                // Ground check: only treat as ground when crossing a top surface from above
                Rectangle feet = new Rectangle(x, y, CRYSTAL_FRAME_W, CRYSTAL_FRAME_H);
                for (int i = 0; i < solids.size && !grounded; i++) {
                    Solid s = solids.get(i);
                    if (!s.isBlocking()) continue;
                    Rectangle sb = s.getBounds();
                    float top = sb.y + sb.height;
                    // Horizontal overlap
                    boolean hOverlap = (x + CRYSTAL_FRAME_W > sb.x && x < sb.x + sb.width);
                    // Crossing from above to below the top surface this frame
                    boolean crossedTop = (prevY >= top && y <= top);
                    if (hOverlap && crossedTop) {
                        grounded = true;
                        groundY = top;
                        y = groundY;
                        vy = 0f;
                        hoverBaseY = groundY + MathUtils.random(hoverRangeMin, hoverRangeMax);
                    }
                }
            }
        } else {
            // Hover with subtle sine motion around hoverBaseY
            hoverPhase += hoverSpeed * delta;
            float offset = MathUtils.sin(hoverPhase) * 6f; // small motion
            y = hoverBaseY + offset - CRYSTAL_FRAME_H / 2f;
        }

        // When spawn animation finishes, switch to looping idle once
        if (!spawnDone && anim.isAnimationFinished()) {
            spawnDone = true;
            try { anim.play("IDLE", false); } catch (Throwable ignored) {}
        }

        // Cooldowns
        if (icePickCooldown > 0f) icePickCooldown -= delta;
        if (iceWallCooldown > 0f) iceWallCooldown -= delta;

        // Attacks
        if (grounded && spawnDone) {
            // Ice Pick flash/attack handled in updateTint()
            tryIceWallAttack(delta);
        }

        // Update flashing telegraph and fire Ice Pick at end
        updateTint(delta);

        // Update bounds
        bounds.set(x, y, CRYSTAL_FRAME_W, CRYSTAL_FRAME_H);

        // Update spawned projectiles
        for (int i = projectiles.size - 1; i >= 0; i--) {
            EnemyProjectile p = projectiles.get(i);
            p.update(delta, solids, player);
            if (!p.isActive()) projectiles.removeIndex(i);
        }
        // Update spawned ice walls
        for (int i = iceWalls.size - 1; i >= 0; i--) {
            com.jjmc.chromashift.enemy.skill.IceWall w = iceWalls.get(i);
            w.update(delta);
            if (!w.isActive()) iceWalls.removeIndex(i);
        }
    }

    public void render(SpriteBatch batch) {
        if (!alive || batch == null) return;
        Color prev = batch.getColor();
        if (flashActive && flashOn) {
            tintColor.set(1f, 0f, 0f, prev.a);
            batch.setColor(tintColor);
        }
        anim.render(batch, x, y, CRYSTAL_FRAME_W, CRYSTAL_FRAME_H);
        batch.setColor(prev);
        // Render projectiles and walls without tint
        batch.setColor(Color.WHITE);
        for (int i = 0; i < projectiles.size; i++) {
            projectiles.get(i).render(batch);
        }
        for (int i = 0; i < iceWalls.size; i++) {
            iceWalls.get(i).render(batch);
        }
        batch.setColor(prev);
    }

    /** Debug draw zones and projectile hitboxes. */
    public void debugDraw(com.badlogic.gdx.graphics.glutils.ShapeRenderer shape) {
        if (shape == null) return;
        // Crystal bounds
        shape.setColor(com.badlogic.gdx.graphics.Color.CYAN);
        shape.rect(bounds.x, bounds.y, bounds.width, bounds.height);
        // Proximity zones
        float cx = x + CRYSTAL_FRAME_W / 2f;
        float cy = y + CRYSTAL_FRAME_H / 2f;
        shape.setColor(com.badlogic.gdx.graphics.Color.YELLOW);
        shape.rect(cx - 80f, cy - 30f, 80f, 60f); // left zone
        shape.rect(cx,        cy - 30f, 80f, 60f); // right zone
        // Ground line at y=246
        shape.setColor(com.badlogic.gdx.graphics.Color.ORANGE);
        shape.rect(-1000f, 246f, 2000f, 2f);
        // Projectiles collision bounds
        shape.setColor(com.badlogic.gdx.graphics.Color.RED);
        for (int i = 0; i < projectiles.size; i++) {
            EnemyProjectile p = projectiles.get(i);
            com.badlogic.gdx.math.Rectangle cb = p.getCollisionBounds();
            shape.rect(cb.x, cb.y, cb.width, cb.height);
        }
    }

    public void setOnDestroyed(java.util.function.BiConsumer<Float, Float> cb) { this.onDestroyed = cb; }

    private void tryIcePickAttack() {
        if (player == null) return;
        // Gate: skip if cooldown active or already flashing
        if (icePickCooldown > 0f || flashActive) return;
        // Gate: skip if player is in Ice Wall range
        if (isPlayerInIceWallRange()) return;
        // Start red/normal flashing; actual fire happens at flash end in updateTint()
        flashActive = true;
        flashOn = true;
        flashTimer = COLOR_FLASH_DURATION;
        flashCycleAccum = 0f;
    }

    private void fireIcePick() {
        // Fire a fan of projectiles toward the player
        float cx = x + CRYSTAL_FRAME_W / 2f;
        float cy = y + CRYSTAL_FRAME_H / 2f;
        float px = player.getX() + player.getHitboxWidth() / 2f;
        float py = player.getY() + player.getHitboxHeight() / 2f;
        Vector2 baseDir = new Vector2(px - cx, py - cy);
        if (baseDir.isZero()) baseDir.set(1, 0);
        // 3-shot fan: -15, 0, +15 degrees
        float[] angles = new float[]{-15f, 0f, 15f};
        for (float a : angles) {
            Vector2 dir = new Vector2(baseDir).nor().rotateDeg(a);
            EnemyProjectile proj = new EnemyProjectile(
                cx - 32f, cy - 16f,
                    dir,
                    300f,
                    12f,
                    "entity/boss1/skill/ice_pick.png",
                    30,
                    0.1f
            );
            // Sprite assumed 64x32; actual damage hitbox 64x16 centered (8px padding top/bottom)
            proj.setSize(64f, 32f);
            proj.setCustomCenteredHitbox(64f, 16f);
            projectiles.add(proj);
        }
        // Reset cooldown for next cycle
        icePickCooldown = ICE_PICK_COOLDOWN_MAX;
        Gdx.app.log("GuardianCrystal", "Ice Pick: fired 3 projectiles toward player");
    }

    private void updateTint(float delta) {
        if (!grounded || !spawnDone) return;
        // Manage flashing and fire at end
        if (flashActive) {
            flashTimer -= delta;
            flashCycleAccum += delta;
            if (flashCycleAccum >= COLOR_FLASH_INTERVAL) {
                flashCycleAccum -= COLOR_FLASH_INTERVAL;
                flashOn = !flashOn;
            }
            if (flashTimer <= 0f) {
                flashActive = false;
                flashOn = false;
                if (!isPlayerInIceWallRange()) {
                    fireIcePick();
                }
            }
            return;
        }
        // If cooldown expired and player not in wall range, initiate flash
        if (icePickCooldown <= 0f && !isPlayerInIceWallRange()) {
            flashActive = true;
            flashOn = true;
            flashTimer = COLOR_FLASH_DURATION;
            flashCycleAccum = 0f;
        }
    }

    private void tryIceWallAttack(float delta) {
        if (player == null) return;
        // Proximity zones centered vertically on ground reference 246
        float cx = x + CRYSTAL_FRAME_W / 2f;
        float cy = 246 + CRYSTAL_FRAME_H / 2f;
        Rectangle playerRect = player.getHitboxRect();
        if (playerRect == null) return;
        Rectangle leftZone = new Rectangle(cx - 80f, cy - 30f, 80f, 60f);
        Rectangle rightZone = new Rectangle(cx, cy - 30f, 80f, 60f);

        boolean inLeft = playerRect.overlaps(leftZone);
        boolean inRight = playerRect.overlaps(rightZone);

        // Cancel windup if player leaves zones or cooldown running
        if (iceWallCooldown > 0f || (!inLeft && !inRight)) {
            iceWallWindupActive = false;
            iceWallWindupTimer = 0f;
            return;
        }

        // Start windup when entering a zone and off cooldown
        if (!iceWallWindupActive) {
            iceWallWindupActive = true;
            iceWallWindupTimer = ICE_WALL_WINDUP_DURATION;
            // Choose side: if both, bias to player's relative position
            if (inLeft && inRight) {
                float px = player.getX() + player.getHitboxWidth() / 2f;
                iceWallWindupLeft = (px < cx);
            } else {
                iceWallWindupLeft = inLeft;
            }
            return;
        }

        // Windup in progress
        if (iceWallWindupActive && iceWallWindupTimer > 0f) {
            iceWallWindupTimer -= delta;
            return;
        }

        // Windup complete: spawn if still in zone
        boolean shouldSpawnLeft = iceWallWindupLeft ? inLeft : inRight;
        if (iceWallWindupActive && iceWallWindupTimer <= 0f && shouldSpawnLeft) {
            iceWallCooldown = ICE_WALL_COOLDOWN_MAX;
            float wallBottomY = 246 + 98f; // pass top to constructor which subtracts height
            float wallX = iceWallWindupLeft ? (cx - 103f) : (cx);
            com.jjmc.chromashift.enemy.skill.IceWall wall = new com.jjmc.chromashift.enemy.skill.IceWall(wallX, wallBottomY, player, iceWallWindupLeft);
            iceWalls.add(wall);
            flashActive = true; // begin tint fade back after attack
            Gdx.app.log("GuardianCrystal", "Ice Wall spawned on " + (iceWallWindupLeft ? "left" : "right") + " side at bottomY=246");
        }

        // Reset windup state if done (spawned or canceled)
        if (iceWallWindupTimer <= 0f) {
            iceWallWindupActive = false;
            iceWallWindupTimer = 0f;
        }
    }

    private boolean isPlayerInIceWallRange() {
        if (player == null) return false;
        float cx = x + CRYSTAL_FRAME_W / 2f;
        float cy = 246 + CRYSTAL_FRAME_H / 2f;
        Rectangle playerRect = player.getHitboxRect();
        if (playerRect == null) return false;
        Rectangle leftZone = new Rectangle(cx - 80f, cy - 30f, 80f, 60f);
        Rectangle rightZone = new Rectangle(cx, cy - 30f, 80f, 60f);
        return playerRect.overlaps(leftZone) || playerRect.overlaps(rightZone);
    }

    // Enemy interface (hit-based health)
    @Override
    public void takeDamage(float damage) {
        if (!alive) return;
        hitsRemaining -= 1;
        if (hitsRemaining <= 0) {
            alive = false;
            Gdx.app.log("GuardianCrystal", "Destroyed after 4 hits.");
            // Optional: play destroy animation before removal
            if (onDestroyed != null) {
                float cx = x + CRYSTAL_FRAME_W / 2f;
                float cy = y + CRYSTAL_FRAME_H / 2f;
                try { onDestroyed.accept(cx, cy); } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public float getHealth() {
        return hitsRemaining;
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public Rectangle getBounds() {
        // Return a zero-size rectangle when dead to avoid NPEs in callers
        return alive ? bounds : new Rectangle(0, 0, 0, 0);
    }
}

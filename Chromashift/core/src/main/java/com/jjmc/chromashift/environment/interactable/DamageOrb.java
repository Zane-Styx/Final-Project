package com.jjmc.chromashift.environment.interactable;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.chromashift.helper.SpriteAnimator;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.enemy.Enemy;
import com.jjmc.chromashift.player.Player;
import com.jjmc.chromashift.player.PlayerCollision;

/**
 * DamageOrb - spawns from GuardianCrystal death.
 * - Not affected by gravity, but bounces on solids.
 * - Uses orb.png (48x48), 3 rows x 4 frames. Each bounce advances the row.
 *   Row0 = initial, Row1 = after 1st bounce, Row2 = after 2nd bounce.
 * - On the 3rd wall hit, it disappears.
 * - Damages boss enemies on overlap.
 */
public class DamageOrb implements Interactable, Pickable {
    private static final String ORB_TEXTURE = "entity/orb.png"; // adjust path if needed
    private static final int FRAME_W = 48;
    private static final int FRAME_H = 48;
    private static final int ROWS = 3;
    private static final int COLS = 4;
    private static final float ANIM_SPEED = 0.08f;

    private float x, y; // bottom-left
    private float vx, vy;
    private final Rectangle bounds;
    private final SpriteAnimator anim;
    private final Array<Solid> solids;
    private final Array<Enemy> bossEnemies; // guardians to damage

    private int wallHits = 0;
    private boolean removed = false;
    private float bounciness = 1.0f;
    // Skip solid collision resolution briefly after spawn so it clearly appears mid-air
    private float spawnGraceTime = 0.12f; // seconds

    public DamageOrb(float centerX, float centerY, float initialVx, float initialVy,
                     Array<Solid> solids, Array<Enemy> bossEnemies) {
        this.x = centerX - FRAME_W / 2f;
        this.y = centerY - FRAME_H / 2f;
        // Start stationary; movement begins only after the player throws it
        this.vx = 0f;
        this.vy = 0f;
        this.solids = solids;
        this.bossEnemies = bossEnemies;
        this.bounds = new Rectangle(x, y, FRAME_W, FRAME_H);

        anim = new SpriteAnimator(ORB_TEXTURE, ROWS, COLS);
        anim.addAnimation("ROW0", 0, 0, COLS, ANIM_SPEED, true);
        anim.addAnimation("ROW1", 1, 0, COLS, ANIM_SPEED, true);
        anim.addAnimation("ROW2", 2, 0, COLS, ANIM_SPEED, true);
        anim.play("ROW0", false);
    }

    // Pickable/hold state
    private boolean held = false;
    private Player holder = null;
    private boolean thrown = false; // becomes true after throwWithVelocity
    private boolean inRange = false;
    private final float holdOffsetX = 0f;
    private final float holdOffsetY = 8f;

    @Override
    public void update(float delta) {
        if (removed) return;

        anim.update(delta);

        // Follow holder if being carried
        if (held && holder != null) {
            float centerX = holder.getHitboxX() + holder.getHitboxWidth() / 2f;
            float centerY = holder.getHitboxY() + holder.getHitboxHeight() / 2f;
            x = centerX - FRAME_W / 2f + holdOffsetX;
            y = centerY + holdOffsetY;
            bounds.set(x, y, FRAME_W, FRAME_H);
            return;
        }

        // If not thrown yet, remain stationary at spawn location
        if (!thrown) {
            bounds.set(x, y, FRAME_W, FRAME_H);
            return;
        }

        // Move (no gravity) only after thrown
        float prevX = x;
        float prevY = y;
        x += vx * delta;
        y += vy * delta;

        boolean blockedX = false;
        boolean blockedY = false;
        if (spawnGraceTime > 0f) {
            spawnGraceTime -= delta;
        } else {
            Rectangle before = new Rectangle(prevX, prevY, FRAME_W, FRAME_H);
            Rectangle after = new Rectangle(x, y, FRAME_W, FRAME_H);
            PlayerCollision.resolveSolidCollision(after, solids);

            float appliedX = after.x - before.x;
            float appliedY = after.y - before.y;

            blockedX = Math.abs(appliedX - (x - prevX)) > 0.001f;
            blockedY = Math.abs(appliedY - (y - prevY)) > 0.001f;

            x = after.x;
            y = after.y;
        }

        if (blockedX || blockedY) {
            // Reflect velocities, count one wall hit per collision event
            if (blockedX) vx = -vx * bounciness;
            if (blockedY) vy = -vy * bounciness;
            wallHits++;
            if (wallHits == 1) anim.play("ROW1", false);
            else if (wallHits == 2) anim.play("ROW2", false);
            else if (wallHits >= 3) {
                removed = true; // delete on 3rd hit
                Gdx.app.log("DamageOrb", "Removed after 3 wall hits.");
            }
        }

        // Update bounds
        bounds.set(x, y, FRAME_W, FRAME_H);

        // Damage boss enemies on overlap only when active (thrown)
        if (thrown && bossEnemies != null) {
            for (int i = 0; i < bossEnemies.size; i++) {
                Enemy e = bossEnemies.get(i);
                if (e == null || !e.isAlive()) continue;
                Rectangle eb = e.getBounds();
                if (eb == null) continue;
                if (bounds.overlaps(eb)) {
                    try {
                        // Double damage on each bounce
                        e.takeDamage((wallHits + 1) * 40f); // deal damage once
                    } catch (Throwable ignored) {}
                    // Despawn orb immediately after first boss hit to prevent multi-hit bursts
                    removed = true;
                    Gdx.app.log("DamageOrb", "Removed after hitting a boss enemy.");
                    break;
                }
            }
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        if (removed) return;
        anim.render(batch, x, y, FRAME_W, FRAME_H);
    }

    @Override public void interact() {}
    @Override public void checkInteraction(Rectangle playerHitbox) {
        // Allow pickup when player is near
        Rectangle r = new Rectangle(x - 8, y - 8, FRAME_W + 16, FRAME_H + 16);
        inRange = playerHitbox != null && playerHitbox.overlaps(r);
    }
    @Override public boolean canInteract() { return inRange; }
    @Override public Rectangle getBounds() { return removed ? new Rectangle(0,0,0,0) : bounds; }
    @Override public void debugDraw(ShapeRenderer shape) { if (!removed) shape.rect(bounds.x, bounds.y, bounds.width, bounds.height); }

    // --- Pickable implementation ---
    @Override
    public void pickUp(Player player) {
        if (player == null) return;
        held = true;
        thrown = false;
        holder = player;
        vx = 0f; vy = 0f;
    }

    @Override
    public void throwWithVelocity(float vx, float vy) {
        held = false;
        holder = null;
        thrown = true;
        this.vx = vx;
        this.vy = vy;
        // small grace to prevent instant collision on release
        spawnGraceTime = Math.max(spawnGraceTime, 0.06f);
    }

    @Override
    public boolean isHeld() { return held; }

    @Override
    public void drop() {
        held = false;
        holder = null;
        thrown = false;
        vx = 0f; vy = 0f;
    }
}

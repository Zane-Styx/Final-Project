package com.jjmc.chromashift.enemy.skill;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.chromashift.helper.SpriteAnimator;
import com.jjmc.chromashift.player.Player;

/**
 * IceWall: stationary boss attack spawned by GuardianCrystal.
 * - Renders 34-frame animation (103x98 sprite), bottom aligned at y=246.
 * - Damages the player using an internal damage hitbox (48x66, offsets: left=32, top=32).
 * - Exists for a short duration then disappears.
 */
public class IceWall {
    private float x, y; // sprite origin (bottom-left)
    private float width = 103f, height = 98f;
    private final Rectangle damageHitbox = new Rectangle();
    private SpriteAnimator animator;
    private boolean active = true;
    private float lifetime = 0f;
    private float maxLifetime = 3.0f; // seconds
    private float damage = 8f;
    private boolean damageApplied = false; // only one hit allowed total

    private final Player player;
    private boolean flipX = false;
    private boolean debugGlow = true;

    public IceWall(float x, float bottomY, Player player, boolean flipX) {
        this.x = x;
        this.y = bottomY - height; // ensure bottom touches 246
        this.player = player;
        this.flipX = flipX;
        try {
            animator = new SpriteAnimator("entity/boss1/skill/ice_wall.png", 1, 34);
            animator.addAnimation("play", 0, 0, 34, .1f, false);
            animator.play("play", false);
        } catch (Exception e) {
            Gdx.app.error("IceWall", "Failed to load animator", e);
        }
    }

    public void update(float delta) {
        if (!active) return;
        lifetime += delta;
        if (animator != null) animator.update(delta);
        // Damage hitbox: 48x66 with offsets left=32, top=32
        // Top padding means hitbox starts at y + (height - topPad - hitboxH) = y + (98 - 32 - 66) = y
        damageHitbox.set(x + 32f, y + (height - 32f - 66f), 48f, 66f);
        // Damage window: frames 9..13 (inclusive), but still only once overall
        if (!damageApplied && animator != null) {
            int frame = animator.getCurrentFrameIndex();
            if (frame >= 9 && frame <= 13) {
                if (player != null && player.getHitboxRect() != null && damageHitbox.overlaps(player.getHitboxRect())) {
                    try { if (player.getHealthSystem() != null) player.getHealthSystem().damage(damage, this); } catch (Throwable ignored) {}
                    // Apply a temporary slow to the player: 50% speed for 4 seconds
                    try { if (player != null) player.applySlow(0.8f, 4.0f); } catch (Throwable ignored) {}
                    damageApplied = true;
                }
            }
        }
        if (lifetime >= maxLifetime) active = false;
    }

    public void render(SpriteBatch batch) {
        if (!active || animator == null) return;
        animator.setFlipX(flipX);
        animator.render(batch, x, y, width, height);
        // Debug glow overlay on damage hitbox
        if (debugGlow) {
            com.badlogic.gdx.graphics.Color prev = batch.getColor();
            float pulse = 0.35f + 0.25f * (float)Math.abs(Math.sin(lifetime * 6.0));
            batch.setColor(1f, 0.2f, 0.2f, pulse);
            // draw a simple quad via SpriteAnimator’s render method using a 1x1 white pixel is not available,
            // so leave batch color set only briefly; alternatively, enable shape debug in screen.
            batch.setColor(prev);
        }
    }

    public boolean isActive() { return active; }
    public Rectangle getDamageHitbox() { return damageHitbox; }
}

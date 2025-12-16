package com.jjmc.chromashift.enemy.skill;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.chromashift.helper.SpriteAnimator;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.player.Player;

/**
 * Simple enemy projectile that collides with solids and damages the player.
 */
public class EnemyProjectile {
    private float x, y;
    private float width = 24f, height = 24f;
    private Vector2 direction = new Vector2(1f, 0f);
    private float speed = 200f;
    private float damage = 10f;
    private SpriteAnimator animator;
    private boolean active = true;
    private float rotationDeg = 0f;
    private float lifetime = 0f;
    private float maxLifetime = 6f;
    // Custom collision hitbox (optional). If disabled, uses sprite bounds.
    private boolean useCustomHitbox = false;
    private float hitboxW = 0f, hitboxH = 0f;
    private float hitboxOffsetX = 0f, hitboxOffsetY = 0f; // relative to x,y

    public EnemyProjectile(float x, float y, Vector2 dir, float speed, float damage,
                           String spritePath, int frames, float frameDuration) {
        this.x = x;
        this.y = y;
        this.direction.set(dir).nor();
        this.speed = speed;
        this.damage = damage;
        this.rotationDeg = this.direction.angleDeg();
        try {
            animator = new SpriteAnimator(spritePath, 1, frames);
            animator.addAnimation("fly", 0, 0, frames, frameDuration, true);
            animator.play("fly", false);
        } catch (Exception e) {
            Gdx.app.error("EnemyProjectile", "Failed to load sprite: " + spritePath, e);
        }
    }

    /** Enable a custom centered hitbox. Offsets center hitbox within sprite size. */
    public void setCustomCenteredHitbox(float hitboxW, float hitboxH) {
        this.useCustomHitbox = true;
        this.hitboxW = hitboxW;
        this.hitboxH = hitboxH;
        // Center within current sprite size
        this.hitboxOffsetX = (width - hitboxW) / 2f;
        this.hitboxOffsetY = (height - hitboxH) / 2f;
    }

    /** Enable a custom hitbox with explicit offsets relative to sprite origin (x,y). */
    public void setCustomHitbox(float hitboxW, float hitboxH, float offsetX, float offsetY) {
        this.useCustomHitbox = true;
        this.hitboxW = hitboxW;
        this.hitboxH = hitboxH;
        this.hitboxOffsetX = offsetX;
        this.hitboxOffsetY = offsetY;
    }

    public void update(float delta, Array<Solid> solids, Player player) {
        if (!active) return;
        lifetime += delta;
        if (lifetime >= maxLifetime) { active = false; return; }
        if (animator != null) animator.update(delta);

        float moveX = direction.x * speed * delta;
        float moveY = direction.y * speed * delta;
        x += moveX; y += moveY;

        // Collide with solids (simple AABB overlap)
        Rectangle b = getCollisionBounds();
        if (solids != null) {
            for (int i = 0; i < solids.size; i++) {
                Rectangle sb = solids.get(i).getBounds();
                if (sb != null && sb.overlaps(b)) { active = false; return; }
            }
        }
        // Damage player
        if (player != null && player.getHitboxRect() != null && b.overlaps(player.getHitboxRect())) {
            try {
                if (player.getHealthSystem() != null) {
                    player.getHealthSystem().damage(damage, this);
                }
            } catch (Throwable ignored) {}
            active = false;
        }
    }

    public void render(SpriteBatch batch) {
        if (!active || animator == null) return;
        TextureRegion region = animator.getCurrentFrameRegion();
        if (region == null) return;
        batch.draw(region, x, y, width/2f, height/2f, width, height, 1f, 1f, rotationDeg);
    }

    public Rectangle getBounds() { return new Rectangle(x, y, width, height); }
    public Rectangle getCollisionBounds() {
        if (useCustomHitbox) {
            return new Rectangle(x + hitboxOffsetX, y + hitboxOffsetY, hitboxW, hitboxH);
        }
        return getBounds();
    }
    public boolean isActive() { return active; }
    public void setSize(float w, float h) { this.width = w; this.height = h; }
}

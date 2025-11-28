package com.jjmc.chromashift.player.skill;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.chromashift.helper.SpriteAnimator;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.enemy.Enemy;
import com.jjmc.chromashift.player.Player;

/**
 * Generic projectile class for skills like Shuriken and Split.
 * Handles animation, movement, collision detection, and damage.
 */
public class Projectile {
    private float x, y;
    private float width = 32f, height = 32f;
    private Vector2 direction = new Vector2(1f, 0f);
    private float speed = 200f;
    private float damage = 10f;
    private SpriteAnimator animator;
    private boolean isActive = true;
    private Player owner;
    private float lifetime = 0f;
    private float maxLifetime = 10f; // Max 10 seconds before auto-destroy
    private boolean hasHit = false;
    // Rotation in degrees for rendering the projectile
    private float rotationDeg = 0f;
    
    public Projectile(float x, float y, Vector2 direction, float speed, float damage,
                     String spritePath, int frameCount, float frameDuration, Player owner) {
        this.x = x;
        this.y = y;
        this.direction = direction.nor();
        // compute rotation based on direction
        this.rotationDeg = this.direction.angleDeg();
        this.speed = speed;
        this.damage = damage;
        this.owner = owner;
        
        // Create animator
        try {
            animator = new SpriteAnimator(spritePath, 1, frameCount);
            animator.addAnimation("fly", 0, 0, frameCount, frameDuration, true);
            animator.play("fly", false);
        } catch (Exception e) {
            Gdx.app.error("Projectile", "Failed to load projectile sprite: " + spritePath, e);
        }
    }
    public void update(float delta, Array<Solid> solids, Array<Enemy> enemies) {
        if (!isActive) return;
        
        lifetime += delta;
        if (lifetime > maxLifetime) {
            isActive = false;
            return;
        }
        
        // Move projectile
        float moveX = direction.x * speed * delta;
        float moveY = direction.y * speed * delta;
        x += moveX;
        y += moveY;
        
        // Update animation
        if (animator != null) {
            animator.update(delta);
        }
        
        // Check collisions with solids
        Rectangle projBounds = getBounds();
        for (Solid solid : solids) {
            if (solid.getBounds().overlaps(projBounds)) {
                isActive = false;
                hasHit = true;
                return;
            }
        }
        
        // Check collisions with enemies
        for (Enemy enemy : enemies) {
            if (enemy.getBounds().overlaps(projBounds)) {
                // Don't damage owner
                if (enemy.getBounds().x != owner.getX() || enemy.getBounds().y != owner.getY()) {
                    enemy.takeDamage((int) damage);
                    isActive = false;
                    hasHit = true;
                    return;
                }
            }
        }
    }
    
    public void render(SpriteBatch batch) {
        if (!isActive || animator == null) return;
        TextureRegion region = animator.getCurrentFrameRegion();
        if (region == null) return;
        // Draw rotated around the center
        batch.draw(region, x, y, width / 2f, height / 2f, width, height, 1f, 1f, rotationDeg);
    }
    
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public boolean isFinished() {
        return !isActive;
    }
    
    public boolean hasHit() {
        return hasHit;
    }
    
    public void setWidth(float width) {
        this.width = width;
    }
    
    public void setHeight(float height) {
        this.height = height;
    }
    
    public void dispose() {
        if (animator != null) {
            animator.dispose();
        }
    }
}

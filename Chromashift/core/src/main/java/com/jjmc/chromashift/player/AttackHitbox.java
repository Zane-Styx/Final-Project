package com.jjmc.chromashift.player;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import java.util.HashSet;
import java.util.Set;

/**
 * Transient attack hitbox that exists for a short duration.
 * Tracks which entities have been hit to prevent multi-hitting in one swing.
 */
public class AttackHitbox {
    private final Rectangle hitbox;
    private final Set<Object> alreadyHit = new HashSet<>();
    private float lifetime;
    private final float maxLifetime;
    private boolean active;
    private final Player owner;
    
    // Hitbox configuration
    private static final float BASE_HITBOX_WIDTH = 50f;
    // Height will dynamically match player's hitbox height each frame for reliable segment contact
    private static final float MIN_HITBOX_HEIGHT = 40f; // safety minimum
    // When overlap > 0, the hitbox starts partially inside the player's body
    // so that the leading edge begins exactly at (or slightly behind) the front.
    private static final float EDGE_OVERLAP = 14f; // emulate original -10 offset intent
    private static final float HITBOX_DURATION = 0.12f; // How long hitbox exists
    
    public AttackHitbox(Player player) {
        this.owner = player;
        this.hitbox = new Rectangle();
        this.maxLifetime = HITBOX_DURATION;
        this.lifetime = 0f;
        this.active = false;
    }
    
    /**
     * Activate the hitbox for a new attack swing.
     */
    public void activate() {
        if (active) return; // Already active
        
        active = true;
        lifetime = maxLifetime;
        alreadyHit.clear();
        updatePosition();
    }
    
    /**
     * Update hitbox position based on player position and facing.
     */
    private void updatePosition() {
        // Use actual hitbox for alignment (avoids sprite width discrepancies)
        float hbX = owner.getHitboxX();
        float hbY = owner.getHitboxY();
        float hbW = owner.getHitboxWidth();
        float hbH = owner.getHitboxHeight();

        float dynamicHeight = Math.max(MIN_HITBOX_HEIGHT, hbH); // ensure covers vertical span of nearby segments
        float width = BASE_HITBOX_WIDTH;

        if (owner.isFacingLeft()) {
            float rightEdge = hbX + EDGE_OVERLAP;
            float x = rightEdge - width;
            float y = hbY + hbH * 0.5f - dynamicHeight * 0.5f;
            hitbox.set(x, y, width, dynamicHeight);
        } else {
            float leftEdge = hbX + hbW - EDGE_OVERLAP;
            float x = leftEdge;
            float y = hbY + hbH * 0.5f - dynamicHeight * 0.5f;
            hitbox.set(x, y, width, dynamicHeight);
        }
    }
    
    /**
     * Update the hitbox (decrement lifetime, update position).
     */
    public void update(float delta) {
        if (!active) return;
        
        lifetime -= delta;
        if (lifetime <= 0f) {
            active = false;
            return;
        }
        
        // Update position to follow player
        updatePosition();
    }
    
    /**
     * Check collision with enemy entities and apply damage.
     * @param enemies Array of Enemy objects to check
     */
    public void checkEnemyCollisions(Array<com.jjmc.chromashift.environment.enemy.Enemy> enemies) {
        if (!active || enemies == null) return;
        
        for (com.jjmc.chromashift.environment.enemy.Enemy enemy : enemies) {
            if (enemy == null || !enemy.isAlive()) continue;
            if (alreadyHit.contains(enemy)) continue; // Already hit this enemy
            
            // Check if any tentacle segment overlaps the hitbox
            if (enemy instanceof com.jjmc.chromashift.environment.enemy.Tentacle) {
                com.jjmc.chromashift.environment.enemy.Tentacle tentacle = 
                    (com.jjmc.chromashift.environment.enemy.Tentacle) enemy;
                
                boolean segmentHit = false;
                com.badlogic.gdx.math.Circle[] segments = tentacle.getSegmentHitboxes();
                for (int i = 0; i < segments.length; i++) {
                    com.badlogic.gdx.math.Circle seg = segments[i];
                    if (seg != null && overlaps(hitbox, seg)) {
                        tentacle.applyDamage(1, i);
                        segmentHit = true;
                        break; // one segment per swing
                    }
                }
                // Fallback: if no individual segment registered but bounding box overlaps, still apply damage
                if (!segmentHit) {
                    Rectangle tb = tentacle.getBounds();
                    if (tb != null && hitbox.overlaps(tb)) {
                        tentacle.applyDamage(1, segments.length - 1); // treat as tip damage
                        // Mark as hit so we don't spam damage every frame of lifetime
                        segmentHit = true; // reuse flag to unify hit bookkeeping
                    }
                }
                if (segmentHit) {
                    alreadyHit.add(enemy);
                }
            } else {
                // For other enemies, use getBounds()
                Rectangle enemyBounds = enemy.getBounds();
                if (enemyBounds != null && hitbox.overlaps(enemyBounds)) {
                    enemy.takeDamage(1);
                    alreadyHit.add(enemy);
                }
            }
        }
    }
    
    /**
     * Check if a rectangle overlaps with a circle.
     */
    private boolean overlaps(Rectangle rect, com.badlogic.gdx.math.Circle circle) {
        // Find closest point on rectangle to circle center
        float closestX = Math.max(rect.x, Math.min(circle.x, rect.x + rect.width));
        float closestY = Math.max(rect.y, Math.min(circle.y, rect.y + rect.height));
        
        // Calculate distance from circle center to this closest point
        float distanceX = circle.x - closestX;
        float distanceY = circle.y - closestY;
        float distanceSquared = distanceX * distanceX + distanceY * distanceY;
        
        // Check if distance is less than circle radius
        return distanceSquared < (circle.radius * circle.radius);
    }
    
    /**
     * Debug draw the hitbox.
     */
    public void debugDraw(ShapeRenderer shape) {
        if (!active) return;
        
        // Draw with fade based on lifetime
        float alpha = lifetime / maxLifetime;
        shape.setColor(1f, 0f, 0f, alpha * 0.5f);
        shape.rect(hitbox.x, hitbox.y, hitbox.width, hitbox.height);
    }
    
    public boolean isActive() {
        return active;
    }
    
    public Rectangle getHitbox() { return hitbox; }

    /**
     * Optional: expose overlap configuration if future tuning is needed.
     */
    public static float getEdgeOverlap() { return EDGE_OVERLAP; }
}

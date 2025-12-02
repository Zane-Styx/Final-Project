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
 * Projectile for skills. Moves, animates, hits stuff.
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
    // Rotation (deg) for drawing
    private float rotationDeg = 0f;
    
    // Perp wobble (shuriken)
    private boolean useSineWave = false;
    private float sineTime = 0f;
    private float sineAmplitude = 12f; // Wobble amplitude (smaller by default)
    private float sineFrequency = 3f; // Wobbles per second
    private float baseX = 0f; // Base position along travel path
    private float baseY = 0f; // Base position along travel path
    
    // Split timer for the main shot
    private boolean isMainProjectile = false;
    private float splitTimer = 0f;
    private float splitDelay = 1.5f; // splits after this if it doesn't hit
    private boolean splitTriggered = false; // avoid double-spawn
    
    // Homing for split shots
    private boolean isHoming = false;
    private float maxTurnRatePerSecond = 180f; // max turn speed
    private Enemy currentTarget = null;
    
    public Projectile(float x, float y, Vector2 direction, float speed, float damage,
                     String spritePath, int frameCount, float frameDuration, Player owner) {
        this.x = x;
        this.y = y;
        this.baseX = x; // Store initial base position for sine wave
        this.baseY = y; // Store initial base position for sine wave
        this.direction = direction.nor();
        // compute rotation based on direction
        this.rotationDeg = this.direction.angleDeg();
        this.speed = speed;
        this.damage = damage;
        this.owner = owner;
        
        // Sprite anim
        try {
            animator = new SpriteAnimator(spritePath, 1, frameCount);
            animator.addAnimation("fly", 0, 0, frameCount, frameDuration, true);
            animator.play("fly", false);
        } catch (Exception e) {
            Gdx.app.error("Projectile", "Failed to load projectile sprite: " + spritePath, e);
        }
    }
    
    /** Mark as the main shot that can split. */
    public void setMainProjectile(boolean isMain, float splitDelay) {
        this.isMainProjectile = isMain;
        this.splitDelay = splitDelay;
        this.splitTimer = 0f;
        this.splitTriggered = false;
    }
    
    /** Enable/disable homing. */
    public void setHoming(boolean homing) {
        this.isHoming = homing;
    }
    
    /** Enable shuriken wobble. */
    public void setSineWave(boolean enabled, float amplitude, float frequency) {
        this.useSineWave = enabled;
        this.sineAmplitude = amplitude;
        this.sineFrequency = frequency;
    }
    
    /** Set homing turn rate (deg/sec). */
    public void setMaxTurnRate(float degreesPerSecond) {
        this.maxTurnRatePerSecond = degreesPerSecond;
    }
    
    /** Main shot timed out and didn't hit? */
    public boolean shouldSplit() {
        return isMainProjectile && splitTriggered && !hasHit;
    }
    
    /** Current center position. */
    public Vector2 getPosition() {
        return new Vector2(x + width / 2f, y + height / 2f);
    }
    
    /** Current direction. */
    public Vector2 getDirection() {
        return direction.cpy();
    }
    public void update(float delta, Array<Solid> solids, Array<Enemy> enemies) {
        if (!isActive) return;
        
        lifetime += delta;
        if (lifetime > maxLifetime) {
            isActive = false;
            return;
        }
        
        // Split timer (main shot)
        if (isMainProjectile && !hasHit && !splitTriggered) {
            splitTimer += delta;
            if (splitTimer >= splitDelay) {
                // Time to split: flip flag and end this one
                splitTriggered = true;
                Gdx.app.log("Projectile", "SPLIT TRIGGERED: elapsed=" + splitTimer + ", delay=" + splitDelay + ", hasHit=" + hasHit);
                isActive = false;
                return;
            }
        }
        
        // Homing (split shots)
        if (isHoming && enemies != null) {
            // Pick a target when needed
            if (currentTarget == null || !currentTarget.isAlive()) {
                currentTarget = findClosestEnemy(enemies);
            }
            
            // Turn toward target with rate limit
            if (currentTarget != null && currentTarget.isAlive()) {
                Vector2 myPos = new Vector2(x + width / 2f, y + height / 2f);
                Rectangle targetBounds = currentTarget.getBounds();
                Vector2 targetPos = new Vector2(
                    targetBounds.x + targetBounds.width / 2f,
                    targetBounds.y + targetBounds.height / 2f
                );
                
                Vector2 desiredDir = targetPos.sub(myPos).nor();
                float currentAngle = direction.angleDeg();
                float desiredAngle = desiredDir.angleDeg();
                
                // Shortest angle delta
                float angleDiff = desiredAngle - currentAngle;
                while (angleDiff > 180f) angleDiff -= 360f;
                while (angleDiff < -180f) angleDiff += 360f;
                
                // Clamp by turn rate
                float maxTurnThisFrame = maxTurnRatePerSecond * delta;
                float turnAmount = Math.max(-maxTurnThisFrame, Math.min(maxTurnThisFrame, angleDiff));
                
                // Apply turn and update rotation
                float newAngle = currentAngle + turnAmount;
                direction.set(1f, 0f).rotateDeg(newAngle).nor();
                rotationDeg = newAngle;
            }
        }
        
        // Move
        float moveX = direction.x * speed * delta;
        float moveY = direction.y * speed * delta;

        if (useSineWave) {
            // Step along the path
            baseX += moveX;
            baseY += moveY;

            // Perp to travel dir
            Vector2 perp = new Vector2(-direction.y, direction.x).nor();

            // Sine offset perpendicular to path
            sineTime += delta;
            float sineOffset = (float) Math.sin(sineTime * sineFrequency * 2f * Math.PI) * sineAmplitude;

            // Base + perp wobble
            x = baseX + perp.x * sineOffset;
            y = baseY + perp.y * sineOffset;
        } else {
            // No wobble: straight move
            x += moveX;
            y += moveY;
        }
        
        // Anim tick
        if (animator != null) {
            animator.update(delta);
        }
        
        // Solids
        Rectangle projBounds = getBounds();
        for (Solid solid : solids) {
            Rectangle solidBounds = solid.getBounds();
            if (solidBounds != null && solidBounds.overlaps(projBounds)) {
                if (isMainProjectile) {
                    Gdx.app.log("Projectile", "COLLISION with solid: hasHit=true, splitTriggered=" + splitTriggered);
                }
                isActive = false;
                hasHit = true;
                splitTriggered = false; // no split on hit
                return;
            }
        }
        
        // Enemies
        for (Enemy enemy : enemies) {
            Rectangle enemyBounds = enemy.getBounds();
            if (enemyBounds != null && enemyBounds.overlaps(projBounds)) {
                // Skip owner
                if (enemyBounds.x != owner.getX() || enemyBounds.y != owner.getY()) {
                    // Projectiles count as 2 hits for tentacles
                    if (enemy instanceof com.jjmc.chromashift.environment.enemy.Tentacle) {
                        ((com.jjmc.chromashift.environment.enemy.Tentacle) enemy).applyHit(2);
                    } else {
                        enemy.takeDamage((int) damage);
                    }
                    if (isMainProjectile) {
                        Gdx.app.log("Projectile", "COLLISION with enemy: hasHit=true, splitTriggered=" + splitTriggered);
                    }
                    isActive = false;
                    hasHit = true;
                    splitTriggered = false; // no split on hit
                    return;
                }
            }
        }
    }
    
    /** Closest living enemy. */
    private Enemy findClosestEnemy(Array<Enemy> enemies) {
        if (enemies == null || enemies.size == 0) return null;
        
        Enemy closest = null;
        float closestDist = Float.MAX_VALUE;
        Vector2 myPos = new Vector2(x + width / 2f, y + height / 2f);
        
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) continue;
            
            Rectangle bounds = enemy.getBounds();
            Vector2 enemyPos = new Vector2(
                bounds.x + bounds.width / 2f,
                bounds.y + bounds.height / 2f
            );
            
            float dist = myPos.dst(enemyPos);
            if (dist < closestDist) {
                closestDist = dist;
                closest = enemy;
            }
        }
        
        return closest;
    }
    
    public void render(SpriteBatch batch) {
        if (!isActive || animator == null) return;
        TextureRegion region = animator.getCurrentFrameRegion();
        if (region == null) return;
        // Draw rotated at center
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

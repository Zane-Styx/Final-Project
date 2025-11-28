package com.jjmc.chromashift.player.skill;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.jjmc.chromashift.player.Player;

/**
 * Abstract base class for all player skills.
 * Each skill handles its own animation, cooldown, and execution logic.
 */
public abstract class BaseSkill {
    protected Player player;
    protected String skillName;
    protected float cooldownTime = 0f;
    protected float currentCooldown = 0f;
    protected boolean isActive = false;
    protected float animationTimer = 0f;
    protected float totalAnimationTime = 0f;
    
    // Flags that skills can set to request player state changes
    protected boolean requestInvulnerability = false;
    protected boolean requestInvisibility = false;
    protected boolean resetDash = false;
    protected boolean resetJump = false;
    // Request to lock player movement while skill animation is active
    protected boolean requestMovementLock = false;
    // Request to disable gravity/vertical movement while skill animation is active
    protected boolean requestDisableGravity = false;
    protected float movementOverrideX = 0f;
    protected float movementOverrideY = 0f;
    protected boolean hasMovementOverride = false;
    
    public BaseSkill(Player player, String skillName, float cooldownTime) {
        this.player = player;
        this.skillName = skillName;
        this.cooldownTime = cooldownTime;
    }
    
    /**
     * Activate the skill. Called when player presses the skill key.
     * Override to implement skill logic.
     */
    public abstract void activate();
    
    /**
     * Update the skill each frame.
     * Handles animation, cooldown, and active skill updates.
     */
    public void update(float delta) {
        // Update cooldown
        if (currentCooldown > 0) {
            currentCooldown -= delta;
        }
        
        // Update active skill animation/logic
        if (isActive) {
            animationTimer += delta;
            updateActive(delta);
            
            // Check if animation finished
            if (animationTimer >= totalAnimationTime) {
                deactivate();
            }
        }
    }
    
    /**
     * Update logic while skill is active. Override in subclasses.
     */
    protected abstract void updateActive(float delta);
    
    /**
     * Render the skill. Called by player.render().
     */
    public abstract void render(SpriteBatch batch);
    
    /**
     * Optional debug rendering.
     */
    public void debugDraw(ShapeRenderer shape) {
        // Override if needed
    }
    
    /**
     * Deactivate the skill and apply cooldown.
     */
    public void deactivate() {
        isActive = false;
        animationTimer = 0f;
        currentCooldown = cooldownTime;
        
        // Apply any pending resets
        if (resetDash) {
            player.resetDash();
        }
        if (resetJump) {
            player.setCanJump(true);
        }
        
        // Clear transient requests
        requestInvulnerability = false;
        requestInvisibility = false;
        requestMovementLock = false;
        requestDisableGravity = false;
        hasMovementOverride = false;

        resetDash = false;
        resetJump = false;
    }

    public boolean isRequestingDisableGravity() {
        return requestDisableGravity && isActive;
    }
    
    /**
     * Check if the skill can be cast (not on cooldown).
     */
    public boolean canCast() {
        return currentCooldown <= 0 && !isActive;
    }
    
    /**
     * Get the current cooldown progress (0 to 1).
     */
    public float getCooldownProgress() {
        if (cooldownTime <= 0) return 1f;
        return 1f - (currentCooldown / cooldownTime);
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public String getSkillName() {
        return skillName;
    }
    
    public boolean isRequestingInvulnerability() {
        return requestInvulnerability && isActive;
    }
    
    public boolean isRequestingInvisibility() {
        return requestInvisibility && isActive;
    }

    public boolean isRequestingMovementLock() {
        return requestMovementLock && isActive;
    }
    
    public boolean hasMovementOverride() {
        return hasMovementOverride && isActive;
    }
    
    public float getMovementOverrideX() {
        return movementOverrideX;
    }
    
    public float getMovementOverrideY() {
        return movementOverrideY;
    }
    
    public void dispose() {
        // Override if resources need cleanup
    }
}

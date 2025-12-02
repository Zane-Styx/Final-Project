package com.jjmc.chromashift.player.skill;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.jjmc.chromashift.player.Player;

/**
 * Base for player skills. Handles cooldown and timing.
 */
public abstract class BaseSkill {
    protected Player player;
    protected String skillName;
    protected float cooldownTime = 0f;
    protected float currentCooldown = 0f;
    protected boolean isActive = false;
    protected float animationTimer = 0f;
    protected float totalAnimationTime = 0f;
    
    // Skill can ask for these while active
    protected boolean requestInvulnerability = false;
    protected boolean requestInvisibility = false;
    protected boolean resetDash = false;
    protected boolean resetJump = false;
    // Lock player movement during the skill
    protected boolean requestMovementLock = false;
    // Kill gravity while the skill runs
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
     * Called on cast.
     */
    public abstract void activate();
    
    /**
     * Per-frame update. Handles cooldown and active state.
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
            
            // End when the timer is up
            if (animationTimer >= totalAnimationTime) {
                deactivate();
            }
        }
    }
    
    /**
     * Active tick for the skill.
     */
    protected abstract void updateActive(float delta);
    
    /**
     * Draw any skill visuals.
     */
    public abstract void render(SpriteBatch batch);
    
    /**
     * Optional debug draw.
     */
    public void debugDraw(ShapeRenderer shape) {
        // Override if needed
    }
    
    /**
     * Stop the skill and start cooldown.
     */
    public void deactivate() {
        isActive = false;
        animationTimer = 0f;
        currentCooldown = cooldownTime;
        
        // Apply any queued resets
        if (resetDash) {
            player.resetDash();
        }
        if (resetJump) {
            player.setCanJump(true);
        }
        
        // Clear one-shot flags
        requestInvulnerability = false;
        requestInvisibility = false;
        requestMovementLock = false;
        requestDisableGravity = false;
        hasMovementOverride = false;

        resetDash = false;
        resetJump = false;
    }

    // Accessors for save/restore
    public float getCurrentCooldown() {
        return currentCooldown;
    }

    public void setCurrentCooldown(float v) {
        this.currentCooldown = v;
    }

    public float getAnimationTimer() {
        return animationTimer;
    }

    public void setAnimationTimer(float v) {
        this.animationTimer = v;
    }

    public void setActiveState(boolean active) {
        this.isActive = active;
    }

    public boolean getActiveState() {
        return this.isActive;
    }

    public boolean isRequestingDisableGravity() {
        return requestDisableGravity && isActive;
    }
    
    /**
     * Can we cast right now?
     */
    public boolean canCast() {
        return currentCooldown <= 0 && !isActive;
    }
    
    /**
     * Cooldown progress (0..1).
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
        // Override if we allocate stuff
    }
}

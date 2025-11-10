package com.jjmc.chromashift.skill;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.effects.SFX;

/**
 * Base class for all skills. Handles:
 * - Skill animation and effects
 * - Damage timing (which frames apply damage)
 * - Hitbox management
 * - Cooldown tracking
 */
public abstract class BaseSkill {
    // Visual effect for the skill
    protected SFX effect;
    
    // Optional indicator shown where the skill will spawn/land
    protected SFX indicator;
    protected boolean indicatorVisible = false;
    protected float indicatorX, indicatorY;
    protected float indicatorOffsetX, indicatorOffsetY;
    
    /**
     * Initialize the skill's visual effect
     * @param atlasPath Path to the texture atlas containing animation frames
     * @param animationName Name of the animation in the atlas
     */
    protected void initializeEffect(String atlasPath, String animationName, int frameWidth, int frameHeight, int frameCount, float frameDuration, boolean loop) {
        effect = new SFX(atlasPath, animationName);
        effect.setSize(width, height);
        effect.getAnim().addAnimationFromTexture(animationName, atlasPath, frameWidth, frameHeight, frameCount, frameDuration, loop);
        effect.play(animationName);
    }

    /**
     * Initialize an optional indicator effect (separate sprite/animation) that
     * can be shown to indicate where the skill will land/spawn.
     * indicator animation will not be auto-removed by default so you can
     * control its visibility from the skill.
     */
    protected void initializeIndicator(String atlasPath, String animationName, int frameWidth, int frameHeight, int frameCount, float frameDuration, boolean loop, float sizeW, float sizeH) {
        indicator = new SFX(atlasPath, animationName);
        indicator.setSize(sizeW, sizeH);
        indicator.getAnim().addAnimationFromTexture(animationName, atlasPath, frameWidth, frameHeight, frameCount, frameDuration, loop);
        indicator.setAutoRemove(false);
        indicator.play(animationName);
    }
    
    // Skill properties
    protected float damage;
    protected float cooldown;
    protected float cooldownRemaining;
    protected boolean active;
    
    // Timing/frame control
    protected Array<Integer> damageFrames; // Which frames should apply damage
    protected int currentFrame;
    protected boolean damageAppliedThisFrame;
    
    // Position and hitbox
    protected float x, y;
    protected float width, height;
    protected Rectangle hitbox;
    protected boolean facingLeft;
    
    public BaseSkill(float damage, float cooldown, float width, float height) {
        this.damage = damage;
        this.cooldown = cooldown;
        this.cooldownRemaining = 0;
        this.active = false;
        this.damageFrames = new Array<>();
        this.hitbox = new Rectangle();
        this.damageAppliedThisFrame = false;
        this.width = width;
        this.height = height;
    }
    
    /**
     * Set which animation frames should apply damage.
     * For example, a sword slash might damage on frames 2-4.
     */
    protected void setDamageFrames(int... frames) {
        damageFrames.clear();
        for (int frame : frames) {
            damageFrames.add(frame);
        }
    }
    
    /**
     * Check if the current frame should apply damage.
     */
    protected boolean isDamageFrame() {
        return damageFrames.contains(currentFrame, false);
    }
    
    /**
     * Try to activate the skill. Returns false if on cooldown.
     */
    public boolean activate(float x, float y, boolean facingLeft) {
        if (cooldownRemaining > 0 || active) return false;
        
        this.x = x;
        this.y = y;
        this.facingLeft = facingLeft;
        this.active = true;
        this.currentFrame = 0;
        this.damageAppliedThisFrame = false;
        
        // Implement in subclass: create effect, set position, play animation
        return onActivate();
    }
    
    public void update(float delta) {
        if (cooldownRemaining > 0) {
            cooldownRemaining -= delta;
        }
        
        if (!active) return;
        
        // Update indicator (if present) independently from main effect
        if (indicator != null && indicatorVisible) {
            indicator.update(delta);
        }

        if (effect != null) {
            effect.update(delta);
            // Track current frame for damage timing
            updateFrameTracking();
            // Update hitbox position/size based on current frame
            updateHitbox();

            if (effect.isFinished()) {
                active = false;
                cooldownRemaining = cooldown;
            }
        }
    }
    
    public void render(SpriteBatch batch) {
        // Draw indicator first if visible (so it appears under/around the effect)
        if (indicator != null && indicatorVisible) {
            indicator.setPosition(indicatorX + indicatorOffsetX, indicatorY + indicatorOffsetY);
            indicator.render(batch);
        }

        if (active && effect != null) {
            effect.render(batch);
        }
    }

    /**
     * Show the previously-initialized indicator at the given world position.
     * If you didn't call initializeIndicator(), this is a no-op.
     */
    protected void showIndicatorAt(float worldX, float worldY, float offsetX, float offsetY) {
        if (indicator == null) return;
        this.indicatorX = worldX;
        this.indicatorY = worldY;
        this.indicatorOffsetX = offsetX;
        this.indicatorOffsetY = offsetY;
        this.indicatorVisible = true;
    }

    /** Hide the indicator if present. */
    protected void hideIndicator() {
        this.indicatorVisible = false;
    }
    
    public Rectangle getHitbox() {
        return hitbox;
    }
    
    public float getDamage() {
        return damage;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void dispose() {
        if (effect != null) {
            effect.dispose();
        }
    }
    
    // --- Abstract methods to implement in subclasses ---
    
    /**
     * Called when skill is activated. Create and setup the effect here.
     * @return true if activation succeeded
     */
    protected abstract boolean onActivate();
    
    /**
     * Update the frame counter based on animation progress.
     * Implementation depends on how frames are tracked in the specific skill.
     */
    protected void updateFrameTracking() {
        if (effect != null) {
            currentFrame = effect.getCurrentFrame();
        }
    }
    
    /**
     * Update hitbox size and position based on current frame.
     * Implementation depends on the skill's hit area pattern.
     */
    protected void updateHitbox() {
        float hitboxX = facingLeft ? x - width : x;
        hitbox.set(hitboxX, y, width, height);
    }
}

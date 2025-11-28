package com.jjmc.chromashift.player.skill;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.jjmc.chromashift.player.Player;

/**
 * JumpSkill: Launches player upward with strong vertical velocity.
 * Resets dash but NOT jump.
 * Player is NOT invulnerable.
 * Sprite: skill_jump.png (1 row, 5 frames, 48×48)
 */
public class JumpSkill extends BaseSkill {
    private com.chromashift.helper.SpriteAnimator animator;
    private final float JUMP_VELOCITY = 350f; // Strong upward velocity to add
    private final float MAX_UPWARD_VELOCITY = 700f; // Cap on total upward velocity (allows stacking with LaunchPad)
    private boolean hasAppliedVelocity = false;
    
    public JumpSkill(Player player) {
        super(player, "JumpSkill", 1.5f); // 1 second cooldown
        this.totalAnimationTime = 0.3f; // 5 frames at 0.06s each
        
        // Load sprite
        try {
            animator = new com.chromashift.helper.SpriteAnimator("player/sfx/skill_jump.png", 1, 5);
            animator.addAnimation("jump", 0, 0, 5, 0.06f, false);
        } catch (Exception e) {
            Gdx.app.error("JumpSkill", "Failed to load skill_jump.png", e);
        }
    }
    
    @Override
    public void activate() {
        if (!canCast()) return;
        
        isActive = true;
        animationTimer = 0f;
        hasAppliedVelocity = false;
        requestInvulnerability = false;
        requestInvisibility = false;
        // According to spec: JumpSkill should NOT reset dash, but should reset jump
        resetDash = false;
        resetJump = true;
        
        // Start animation
        if (animator != null) {
            animator.play("jump", false);
        }

        // Play skill sound
        try {
            com.chromashift.helper.SoundManager.play("JumpSkill");
        } catch (Exception ignored) {}
        
        Gdx.app.log("JumpSkill", "Activated!");
    }
    
    @Override
    protected void updateActive(float delta) {
        if (!isActive || animator == null) return;
        
        // Update animation
        animator.update(delta);
        
        // Add jump velocity boost on top of existing velocity (e.g., LaunchPad boost)
        if (!hasAppliedVelocity) {
            float currentVelocityY = player.getVelocityY();
            float newVelocityY = currentVelocityY + JUMP_VELOCITY;
            
            // Cap at max upward velocity to prevent excessive stacking
            if (newVelocityY > MAX_UPWARD_VELOCITY) {
                newVelocityY = MAX_UPWARD_VELOCITY;
            }
            
            player.setVelocityY(newVelocityY);
            player.setOnGround(false);
            hasAppliedVelocity = true;
        }
    }
    
    @Override
    public void render(SpriteBatch batch) {
        if (!isActive || animator == null) return;
        // Render centered exactly on player's hitbox center
        float centerX = player.getHitboxX() + player.getHitboxWidth() / 2f;
        float centerY = player.getHitboxY() + player.getHitboxHeight() / 2f;
        animator.render(batch, centerX - 24f, centerY - 24f, 48f, 48f);
    }
    
    @Override
    public void deactivate() {
        isActive = false;
        animationTimer = 0f;
        currentCooldown = cooldownTime;
        
        // Apply resets
        if (resetJump) {
            player.setCanJump(true);
        }
        
        Gdx.app.log("JumpSkill", "Deactivated!");
    }
    
    public void dispose() {
        if (animator != null) {
            animator.dispose();
        }
    }
}

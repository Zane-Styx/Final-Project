package com.jjmc.chromashift.player.skill;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.jjmc.chromashift.environment.enemy.Enemy;
import com.jjmc.chromashift.player.Player;
import com.badlogic.gdx.utils.Array;

/**
 * SlashSkill: AoE attack around the player that damages enemies.
 * Makes player invulnerable during animation.
 * Resets dash and jump if at least 1 enemy is hit.
 * Sprite: skill_slash.png (1 row, 12 frames, 128×128)
 */
public class SlashSkill extends BaseSkill {
    private com.chromashift.helper.SpriteAnimator animator;
    private final float AOE_SIZE = 128f;
    private Array<Enemy> hitEnemies = new Array<>();
    private final int SLASH_DAMAGE = 20;
    
    public SlashSkill(Player player) {
        super(player, "SlashSkill", 1.5f); // 1.5 second cooldown
        this.totalAnimationTime = 1f; // 12 frames spread over 0.9 seconds
        
        // Load sprite
        try {
            animator = new com.chromashift.helper.SpriteAnimator("player/sfx/skill_slash.png", 1, 12);
            animator.addAnimation("slash", 0, 0, 12, 1f / 12f, false);
        } catch (Exception e) {
            Gdx.app.error("SlashSkill", "Failed to load skill_slash.png", e);
        }
    }
    
    @Override
    public void activate() {
        if (!canCast()) return;
        
        isActive = true;
        animationTimer = 0f;
        hitEnemies.clear();
        requestInvulnerability = true;
        // Make player invisible for entire animation and lock movement
        requestInvisibility = true;
        requestMovementLock = true;
        // Keep player suspended and disable gravity for the duration of the slash
        requestDisableGravity = true;
        resetDash = false;
        resetJump = false;
        
        // Start animation
        if (animator != null) {
            animator.play("slash", false);
        }

        // Play skill sound
        try {
            com.chromashift.helper.SoundManager.play("SlashSkill");
        } catch (Exception ignored) {}
        
        Gdx.app.log("SlashSkill", "Activated!");
    }
    
    @Override
    protected void updateActive(float delta) {
        if (!isActive || animator == null) return;
        
        // Update animation
        animator.update(delta);
        
        // Create AoE box centered on player's hitbox center
        float centerX = player.getHitboxX() + player.getHitboxWidth() / 2f;
        float centerY = player.getHitboxY() + player.getHitboxHeight() / 2f;
        float aoeX = centerX - AOE_SIZE / 2f;
        float aoeY = centerY - AOE_SIZE / 2f;
        Rectangle aoeBounds = new Rectangle(aoeX, aoeY, AOE_SIZE, AOE_SIZE);
        
        // Check for enemy hits
        for (Enemy enemy : player.getEnemies()) {
            if (enemy.getBounds().overlaps(aoeBounds) && !hitEnemies.contains(enemy, true)) {
                enemy.takeDamage(SLASH_DAMAGE);
                hitEnemies.add(enemy);
            }
        }
    }
    
    @Override
    public void render(SpriteBatch batch) {
        if (!isActive || animator == null) return;
        
        // Render centered on player's hitbox center
        float centerX = player.getHitboxX() + player.getHitboxWidth() / 2f;
        float centerY = player.getHitboxY() + player.getHitboxHeight() / 2f;
        float renderX = centerX - AOE_SIZE / 2f;
        float renderY = centerY - AOE_SIZE / 2f;
        animator.render(batch, renderX, renderY, AOE_SIZE, AOE_SIZE);
    }
    
    @Override
    public void debugDraw(ShapeRenderer shape) {
        if (!isActive) return;
        
        float aoeX = player.getX() - AOE_SIZE / 2f;
        float aoeY = player.getY() - AOE_SIZE / 2f;
        shape.setColor(Color.RED);
        shape.rect(aoeX, aoeY, AOE_SIZE, AOE_SIZE);
    }
    
    @Override
    public void deactivate() {
        isActive = false;
        requestInvulnerability = false;
        requestInvisibility = false;
        requestMovementLock = false;
        animationTimer = 0f;
        currentCooldown = cooldownTime;
        
        // Reset dash/jump only if we hit at least 1 enemy
        if (hitEnemies.size > 0) {
            resetDash = true;
            resetJump = true;
            
            if (resetDash) {
                player.resetDash();
            }
            if (resetJump) {
                player.setCanJump(true);
            }
        }
        
        Gdx.app.log("SlashSkill", "Deactivated! Hit " + hitEnemies.size + " enemies.");
    }
    
    public void dispose() {
        if (animator != null) {
            animator.dispose();
        }
    }
}

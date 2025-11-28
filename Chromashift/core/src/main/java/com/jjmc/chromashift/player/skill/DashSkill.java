package com.jjmc.chromashift.player.skill;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.chromashift.helper.SpriteAnimator;
import com.jjmc.chromashift.environment.enemy.Enemy;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.Wall;
import com.jjmc.chromashift.player.Player;
import com.jjmc.chromashift.player.PlayerCollision;

/**
 * DashSkill: Invulnerable invisibile horizontal dash that damages enemies on the path.
 * Sprite: skill_dash.png (1 row, 6 frames, 176×96)
 */
public class DashSkill extends BaseSkill {
    private com.chromashift.helper.SpriteAnimator animator;
    private final float DASH_DISTANCE = 176f; // Total dash distance
    private float dashStartX = 0f;
    private float dashStartY = 0f;
    private float currentDashX = 0f;
    // dashTargetCenterX is the center X used for animation; dashTargetX is final left X for teleport
    private float dashTargetCenterX = 0f;
    private float dashTargetX = 0f;
    private Array<Enemy> damagedEnemies = new Array<>();
    private final float DASH_SPEED = 500f; // pixels per second during dash animation
    private final int DASH_DAMAGE = 15;
    
    public DashSkill(Player player) {
        super(player, "DashSkill", 2.5f); // 2.5 second cooldown
        this.totalAnimationTime = 0.36f; // 6 frames at ~60fps = ~0.1s per frame * 6
        
        // Load sprite
        try {
            animator = new com.chromashift.helper.SpriteAnimator("player/sfx/skill_dash.png", 1, 6);
            animator.addAnimation("dash", 0, 0, 6, 0.06f, false);
        } catch (Exception e) {
            Gdx.app.error("DashSkill", "Failed to load skill_dash.png", e);
        }
    }
    
    @Override
    public void activate() {
        if (!canCast()) return;
        
        isActive = true;
        animationTimer = 0f;
        // Anchor the animation at the player's hitbox center (exact trigger point)
        float playerLeft = player.getHitboxX();
        float playerBottom = player.getHitboxY();
        dashStartX = playerLeft + player.getHitboxWidth() / 2f;
        dashStartY = playerBottom + player.getHitboxHeight() / 2f;
        currentDashX = 0f;
        damagedEnemies.clear();
        requestInvulnerability = true;
        requestInvisibility = true;
        // lock movement while dashing animation plays
        requestMovementLock = true;
        // According to spec: DashSkill should reset the jump only (not dash)
        resetDash = false;
        resetJump = true;

        // Start animation
        if (animator != null) {
            // Flip animation based on facing direction — sprite is right->left oriented so invert
            boolean facingLeft = player.isFacingLeft();
            animator.play("dash", !facingLeft);
        }

        // Disable gravity while the skill runs
        requestDisableGravity = true;

        // Play skill sound
        try {
            com.chromashift.helper.SoundManager.play("DashSkill");
        } catch (Exception ignored) {}

        // Compute final dash target respecting solids and damage enemies along the path immediately
        // Compute final dash target using hitbox center, respecting solids
        float intendedCenterTarget = dashStartX + (player.isFacingLeft() ? -DASH_DISTANCE : DASH_DISTANCE);
        dashTargetCenterX = intendedCenterTarget;
        float minCenter = Math.min(dashStartX, intendedCenterTarget);
        float maxCenter = Math.max(dashStartX, intendedCenterTarget);
        // Only consider solids that are in front of the player (forward raycast)
        for (Solid solid : player.getSolids()) {
            if (!solid.isBlocking()) continue;
            Rectangle b = solid.getBounds();
            // check vertical overlap with player's hitbox
            if (b.y > playerBottom + player.getHitboxHeight() || b.y + b.height < playerBottom) continue;
            if (player.isFacingLeft()) {
                // Solid must be between intendedCenterTarget and dashStartX on the X axis (its right edge)
                float solidRight = b.x + b.width;
                if (solidRight <= dashStartX && solidRight >= intendedCenterTarget) {
                    dashTargetCenterX = solidRight + player.getHitboxWidth() / 2f;
                    if (dashTargetCenterX > dashStartX) dashTargetCenterX = dashStartX;
                    break;
                }
            } else {
                // Facing right: consider solids whose left edge is between dashStartX and intendedCenterTarget
                float solidLeft = b.x;
                if (solidLeft >= dashStartX && solidLeft <= intendedCenterTarget) {
                    dashTargetCenterX = solidLeft - player.getHitboxWidth() / 2f - 1f;
                    if (dashTargetCenterX < dashStartX) dashTargetCenterX = dashStartX;
                    break;
                }
            }
        }

        // Compute final left X for teleport (player X is the left coordinate)
        dashTargetX = dashTargetCenterX - player.getHitboxWidth() / 2f;

        // Build dash bounds for hitting enemies (use center sweep and include full hitbox width)
        float dashBoundsLeft = Math.min(dashStartX, dashTargetCenterX) - player.getHitboxWidth() / 2f;
        Rectangle dashBounds = new Rectangle(dashBoundsLeft, playerBottom, Math.abs(dashTargetCenterX - dashStartX) + player.getHitboxWidth(), player.getHitboxHeight());
        for (Enemy enemy : player.getEnemies()) {
            if (enemy.getBounds().overlaps(dashBounds) && !damagedEnemies.contains(enemy, true)) {
                enemy.takeDamage(DASH_DAMAGE);
                damagedEnemies.add(enemy);
            }
        }

        Gdx.app.log("DashSkill", "Activated!");
    }
    
    @Override
    protected void updateActive(float delta) {
        if (!isActive || animator == null) return;

        // Advance animation (anchored at dashStartX/dashStartY)
        animator.update(delta);
        // Calculate visual offset for the animation based on animation progress
        float progress = Math.min(1f, animationTimer / totalAnimationTime);
        currentDashX = progress * Math.abs(dashTargetX - dashStartX);
    }
    
    @Override
    public void render(SpriteBatch batch) {
        if (!isActive || animator == null) return;

        // Render at anchored animation position (do not follow player while animating)
        float dir = player.isFacingLeft() ? -1f : 1f;
        float renderX = dashStartX + (dir * currentDashX);
        // Center the animation vertically on the player's hitbox center
        float animW = 96f;
        float animH = 96f;
        animator.render(batch, renderX - animW / 2f, dashStartY - animH / 2f, animW, animH);
    }
    
    @Override
    public void deactivate() {
        isActive = false;
        requestInvulnerability = false;
        requestInvisibility = false;
        hasMovementOverride = false;
        requestMovementLock = false;
        animationTimer = 0f;
        currentCooldown = cooldownTime;

        // Apply resets
        if (resetDash) {
            player.resetDash();
        }
        if (resetJump) {
            player.setCanJump(true);
        }

        // Before teleporting, resolve collisions at the target position. This avoids
        // placing the player inside solids. We construct a hitbox at the intended
        // X and current Y and let PlayerCollision resolve it against walls/solids.
        try {
            Rectangle candidate = new Rectangle(dashTargetX, player.getHitboxY(), player.getHitboxWidth(), player.getHitboxHeight());
            // Build walls list from solids
            Array<Wall> walls = new Array<>();
            for (Solid s : player.getSolids()) {
                if (s instanceof Wall w) walls.add(w);
            }
            // Resolve collisions
            PlayerCollision.resolveWallCollision(candidate, walls);
            PlayerCollision.resolveSolidCollision(candidate, player.getSolids());

            // Place player at resolved position (candidate.x is hitbox.x)
            player.setX(candidate.x - player.hitboxOffsetX);
            // Also adjust Y if needed
            player.setY(candidate.y - player.hitboxOffsetY);

            // Zero vertical velocity and give a short hover to avoid instant gravity snap
            player.setVelocityY(0f);
            player.setDashHover(0.15f); // 150ms hover
            player.setOnGround(false);
        } catch (Exception e) {
            // Fallback: teleport directly
            player.setX(dashTargetX);
        }

        Gdx.app.log("DashSkill", "Deactivated!");
    }
    
    public void dispose() {
        if (animator != null) {
            animator.dispose();
        }
    }
}

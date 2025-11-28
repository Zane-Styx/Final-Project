package com.jjmc.chromashift.player.skill;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.jjmc.chromashift.player.Player;
import com.badlogic.gdx.utils.Array;

/**
 * SplitSkill: Launches a projectile that splits into 3 mini-projectiles if it doesn't hit anything.
 * Primary sprite: skill_split.png (1 row, 4 frames, 32×32)
 * Secondary sprite: the_split.png (1 row, 4 frames, 32×32)
 */
public class SplitSkill extends BaseSkill {
    private com.chromashift.helper.SpriteAnimator animator;
    private Projectile mainProjectile;
    private final float PROJECTILE_SPEED = 280f;
    private final int MAIN_DAMAGE = 15;
    private final int SPLIT_DAMAGE = 8;
    private Array<Projectile> projectiles = new Array<>();
    private final float SPLIT_SPREAD_ANGLE = 30f; // degrees
    
    public SplitSkill(Player player) {
        super(player, "SplitSkill", 1.2f); // 1.2 second cooldown
        this.totalAnimationTime = 0.24f; // 4 frames at 0.06s each
        
        // Load sprite for skill activation animation
        try {
            animator = new com.chromashift.helper.SpriteAnimator("player/sfx/skill_split.png", 1, 4);
            animator.addAnimation("cast", 0, 0, 4, 0.06f, false);
        } catch (Exception e) {
            Gdx.app.error("SplitSkill", "Failed to load skill_split.png", e);
        }
    }
    
    @Override
    public void activate() {
        if (!canCast()) return;
        
        isActive = true;
        animationTimer = 0f;
        requestInvulnerability = false;
        requestInvisibility = false;
        
        // Get direction toward mouse
        Vector2 mousePos = getMouseWorldPosition();
        Vector2 playerPos = new Vector2(player.getX(), player.getY());
        Vector2 direction = mousePos.sub(playerPos);
        
        // Launch main projectile
        try {
            mainProjectile = new Projectile(
                player.getX(),
                player.getY(),
                direction,
                PROJECTILE_SPEED,
                MAIN_DAMAGE,
                "player/sfx/skill_split.png",
                4,
                0.06f,
                player
            );
            mainProjectile.setWidth(32f);
            mainProjectile.setHeight(32f);
            projectiles.add(mainProjectile);
            player.activeProjectiles.add(mainProjectile);
        } catch (Exception e) {
            Gdx.app.error("SplitSkill", "Failed to create main projectile", e);
        }
        
        // Start cast animation
        if (animator != null) {
            animator.play("cast", false);
        }
        
        Gdx.app.log("SplitSkill", "Split projectile launched!");
    }
    
    @Override
    protected void updateActive(float delta) {
        if (!isActive || animator == null) return;
        
        // Update cast animation
        animator.update(delta);
        
        // Update main projectile
        if (mainProjectile != null && mainProjectile.isActive()) {
            mainProjectile.update(delta, player.getSolids(), player.getEnemies());
        } else if (mainProjectile != null && !mainProjectile.isActive() && !mainProjectile.hasHit()) {
            // Main projectile finished without hitting - spawn splits
            spawnSplitProjectiles();
            mainProjectile = null;
        }
    }
    
    private void spawnSplitProjectiles() {
        if (mainProjectile == null) return;
        
        Vector2 playerPos = new Vector2(player.getX(), player.getY());
        Vector2 mainDir = new Vector2(
            mainProjectile.getBounds().x - playerPos.x,
            mainProjectile.getBounds().y - playerPos.y
        ).nor();
        
        // Create 3 spread projectiles in a fan pattern
        float[] angles = { -SPLIT_SPREAD_ANGLE, 0f, SPLIT_SPREAD_ANGLE };
        for (float angleOffset : angles) {
            try {
                Vector2 spreadDir = rotateVector(mainDir, angleOffset);
                Projectile split = new Projectile(
                    mainProjectile.getBounds().x,
                    mainProjectile.getBounds().y,
                    spreadDir,
                    PROJECTILE_SPEED * 0.8f, // Slightly slower
                    SPLIT_DAMAGE,
                    "player/sfx/the_split.png",
                    4,
                    0.06f,
                    player
                );
                split.setWidth(32f);
                split.setHeight(32f);
                projectiles.add(split);
                player.activeProjectiles.add(split);
            } catch (Exception e) {
                Gdx.app.error("SplitSkill", "Failed to create split projectile", e);
            }
        }
        
        Gdx.app.log("SplitSkill", "Spawned 3 split projectiles!");
    }
    
    private Vector2 rotateVector(Vector2 vec, float angle) {
        float rad = (float) Math.toRadians(angle);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vector2(vec.x * cos - vec.y * sin, vec.x * sin + vec.y * cos);
    }
    
    public void updateProjectiles(float delta) {
        for (int i = projectiles.size - 1; i >= 0; --i) {
            Projectile proj = projectiles.get(i);
            if (!proj.isActive()) {
                projectiles.removeIndex(i);
            }
        }
    }
    
    @Override
    public void render(SpriteBatch batch) {
        // Render cast animation at player
        if (isActive && animator != null) {
            animator.render(batch, player.getX() - 16f, player.getY() - 16f, 32f, 32f);
        }
        
        // Render projectiles
        for (Projectile proj : projectiles) {
            proj.render(batch);
        }
    }
    
    @Override
    public void deactivate() {
        isActive = false;
        animationTimer = 0f;
        currentCooldown = cooldownTime;
        mainProjectile = null;
        
        Gdx.app.log("SplitSkill", "Deactivated!");
    }
    
    private Vector2 getMouseWorldPosition() {
        // Convert screen mouse position to world coordinates
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.input.getY();
        
        // Approximate world position
        Vector2 playerPos = new Vector2(player.getX(), player.getY());
        Vector2 screenCenter = new Vector2(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f);
        Vector2 mouseDiff = new Vector2(mouseX - screenCenter.x, screenCenter.y - mouseY);
        
        return playerPos.add(mouseDiff.nor().scl(100f));
    }
    
    public void dispose() {
        if (animator != null) {
            animator.dispose();
        }
        for (Projectile proj : projectiles) {
            proj.dispose();
        }
        projectiles.clear();
    }
}

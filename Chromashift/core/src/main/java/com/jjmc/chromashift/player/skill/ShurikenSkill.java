package com.jjmc.chromashift.player.skill;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.jjmc.chromashift.player.Player;
import com.badlogic.gdx.utils.Array;

/**
 * Throw a shuriken toward the mouse.
 */
public class ShurikenSkill extends BaseSkill {
    private com.chromashift.helper.SpriteAnimator animator;
    private Projectile activeProjectile;
    private final float PROJECTILE_SPEED = 300f;
    private final int PROJECTILE_DAMAGE = 12;
    private Array<Projectile> projectiles = new Array<>();
    
    public ShurikenSkill(Player player) {
        super(player, "ShurikenSkill", 0.8f); // 0.8 second cooldown
        this.totalAnimationTime = 0.24f; // 4 frames at 0.06s each
        
        // Load cast anim
        try {
            animator = new com.chromashift.helper.SpriteAnimator("player/sfx/skill_shuriken.png", 1, 4);
            animator.addAnimation("cast", 0, 0, 4, 0.06f, false);
        } catch (Exception e) {
            Gdx.app.error("ShurikenSkill", "Failed to load skill_shuriken.png", e);
        }
    }
    
    @Override
    public void activate() {
        if (!canCast()) return;
        
        isActive = true;
        animationTimer = 0f;
        requestInvulnerability = false;
        requestInvisibility = false;
        
        // Aim at mouse
        Vector2 mousePos = getMouseWorldPosition();
        Vector2 playerPos = new Vector2(player.getX(), player.getY());
        Vector2 direction = mousePos.sub(playerPos);
        
        // Fire the shuriken
        try {
            // Spawn above the head (avoid ground)
            float spawnX = player.getX() + player.getHitboxWidth() / 2f - 16f;
            float spawnY = player.getY() + player.getHitboxHeight() + 8f;
            
            Projectile proj = new Projectile(
                spawnX,
                spawnY,
                direction,
                PROJECTILE_SPEED,
                PROJECTILE_DAMAGE,
                "player/sfx/skill_shuriken.png",
                4,
                0.06f,
                player
            );
            // Perpendicular sine wobble
            proj.setSineWave(true, 12f, 3f); // 12px amplitude, ~3 wobbles/sec
            projectiles.add(proj);
            player.activeProjectiles.add(proj);
            activeProjectile = proj;
        } catch (Exception e) {
            Gdx.app.error("ShurikenSkill", "Failed to create projectile", e);
        }
        
        // Play cast anim
        if (animator != null) {
            animator.play("cast", false);
        }
        
        Gdx.app.log("ShurikenSkill", "Shuriken launched toward: " + mousePos);
    }
    
    @Override
    protected void updateActive(float delta) {
        if (!isActive || animator == null) return;
        
        // Cast anim tick
        animator.update(delta);
        
        // Step the projectile
        if (activeProjectile != null && activeProjectile.isActive()) {
            activeProjectile.update(delta, player.getSolids(), player.getEnemies());
        } else {
            // Done; end the skill
            animationTimer = totalAnimationTime;
        }
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
        // Draw cast anim on player
        if (isActive && animator != null) {
            animator.render(batch, player.getX() - 16f, player.getY() - 16f, 32f, 32f);
        }
        
        // Draw projectiles
        for (Projectile proj : projectiles) {
            proj.render(batch);
        }
    }
    
    @Override
    public void deactivate() {
        isActive = false;
        animationTimer = 0f;
        currentCooldown = cooldownTime;
        activeProjectile = null;
        
        Gdx.app.log("ShurikenSkill", "Deactivated!");
    }
    
    private Vector2 getMouseWorldPosition() {
        // Screen -> world (approx)
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.input.getY();
        
        // Use relative direction from screen center
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

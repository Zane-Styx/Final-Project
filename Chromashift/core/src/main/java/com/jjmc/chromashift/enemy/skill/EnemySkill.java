package com.jjmc.chromashift.enemy.skill;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Base class for enemy skills. Mirrors player BaseSkill timing/cooldown,
 * but without player-specific hooks.
 */
public abstract class EnemySkill {
    protected String skillName;
    protected float cooldownTime = 0f;
    protected float currentCooldown = 0f;
    protected boolean isActive = false;
    protected float animationTimer = 0f;
    protected float totalAnimationTime = 0f;

    public EnemySkill(String skillName, float cooldownTime) {
        this.skillName = skillName;
        this.cooldownTime = cooldownTime;
    }

    public abstract void activate();

    public void update(float delta) {
        if (currentCooldown > 0f) currentCooldown -= delta;
        if (isActive) {
            animationTimer += delta;
            updateActive(delta);
            if (animationTimer >= totalAnimationTime) {
                deactivate();
            }
        }
    }

    protected abstract void updateActive(float delta);
    public abstract void render(SpriteBatch batch);
    public void debugDraw(ShapeRenderer shape) {}

    public void deactivate() {
        isActive = false;
        animationTimer = 0f;
        currentCooldown = cooldownTime;
    }

    public boolean canCast() { return currentCooldown <= 0f && !isActive; }
    public boolean isActive() { return isActive; }
    public String getSkillName() { return skillName; }
}

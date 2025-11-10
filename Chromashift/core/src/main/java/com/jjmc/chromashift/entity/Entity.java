package com.jjmc.chromashift.entity;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.jjmc.chromashift.healthsystem.HealthListener;
import com.jjmc.chromashift.healthsystem.HealthSystem;

/**
 * Generic game Entity base class.
 * Holds position and a HealthSystem and provides basic update/render hooks.
 */
public class Entity {
    protected final HealthSystem health;

    // Position in world space
    protected float x;
    protected float y;

    public Entity(float maxHealth) {
        this.health = new HealthSystem(maxHealth);
        this.health.setRegenPerSecond(0f);
        // Attach a listener that forwards events to overridable protected methods
        this.health.addListener(new HealthListener() {
            @Override
            public void onHealthChanged(HealthSystem hs, float delta, float current, float max) {
                Entity.this.onHealthChanged(delta, current, max);
            }

            @Override
            public void onDeath(HealthSystem hs, Object source) {
                Entity.this.onDeath(source);
            }
        });
    }

    // --- Position helpers ---
    public void setPosition(float x, float y) { this.x = x; this.y = y; }
    public float getX() { return x; }
    public float getY() { return y; }

    // --- Lifecycle ---
    /** Update entity (called by owner game loop) */
    public void update(float delta) {
        if (delta <= 0f) return;
        health.update(delta);
    }

    /** Render entity. Subclasses should override. */
    public void render(SpriteBatch batch) {
        // Default: nothing to draw
    }

    // --- Health / damage API ---
    public boolean damage(float amount, Object source) {
        return health.damage(amount, source);
    }

    public boolean heal(float amount) { return health.heal(amount); }

    public boolean isDead() { return health.isDead(); }

    public HealthSystem getHealthSystem() { return health; }

    // --- hooks for subclasses ---
    protected void onHealthChanged(float delta, float current, float max) {
        // override to add VFX/SFX/UI updates
    }

    protected void onDeath(Object source) {
        // override to handle death (drop loot, play animation, etc)
    }
}

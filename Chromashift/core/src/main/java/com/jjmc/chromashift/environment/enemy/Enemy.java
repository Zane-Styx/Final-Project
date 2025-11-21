package com.jjmc.chromashift.environment.enemy;

import com.badlogic.gdx.math.Rectangle;

/**
 * Interface for all enemy entities that can take damage from the player.
 * Enemies must provide health tracking and collision detection.
 */
public interface Enemy {
    /**
     * Apply damage to this enemy.
     * @param damage The amount of damage to apply
     */
    void takeDamage(int damage);
    
    /**
     * Get the current health of this enemy.
     * @return Current health value
     */
    int getHealth();
    
    /**
     * Check if this enemy is still alive.
     * @return true if health > 0, false otherwise
     */
    boolean isAlive();
    
    /**
     * Get the collision bounds for this enemy.
     * Used for hitbox collision with player attacks.
     * @return Rectangle representing the enemy's hitbox
     */
    Rectangle getBounds();
}

package com.jjmc.chromashift.environment.collectible;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.jjmc.chromashift.player.Player;

/**
 * Abstract base class for all collectible objects.
 * Collectibles are items that the player can pick up by overlapping with them.
 */
public abstract class Collectible {
    protected float x, y;
    protected float width, height;
    protected boolean collected = false;

    public Collectible(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Update collectible logic (animations, etc.)
     */
    public abstract void update(float delta);

    /**
     * Render the collectible to the screen
     */
    public abstract void render(SpriteBatch batch);

    /**
     * Debug rendering
     */
    public void debugDraw(ShapeRenderer shape) {
        if (!collected) {
            shape.rect(x, y, width, height);
        }
    }

    /**
     * Called when the player collects this item.
     * Override this to implement collection behavior.
     */
    public abstract void onCollect(Player player);

    /**
     * Check if the player is overlapping with this collectible and collect it if so.
     */
    public void checkCollision(Player player) {
        if (collected) return;
        
        Rectangle collectibleBounds = getBounds();
        Rectangle playerBounds = new Rectangle(
            player.getHitboxX(), 
            player.getHitboxY(), 
            player.getHitboxWidth(), 
            player.getHitboxHeight()
        );

        if (collectibleBounds.overlaps(playerBounds)) {
            onCollect(player);
            collected = true;
        }
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    public boolean isCollected() {
        return collected;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    /**
     * Cleanup resources
     */
    public abstract void dispose();
}

package com.jjmc.chromashift.environment;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

/**
 * Represents any physical object that can block or interact with collisions.
 * Examples: Wall, Slope, Door.
 */
public interface Solid {
    Rectangle getBounds();

    /**
     * Returns the bounds used for collision. Can return null (e.g., for open doors).
     */
    default Rectangle getCollisionBounds() {
        return getBounds();
    }

    boolean isSolid();

    void render(SpriteBatch batch);

    void debugDraw(ShapeRenderer shape);
    boolean isBlocking();
}

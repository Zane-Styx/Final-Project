package com.jjmc.chromashift.environment;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

/**
 * Represents a right-upward slope (bottom-left to top-right).
 * Can be extended later for left-upward slopes too.
 */
public class Slope implements Solid {
    private final Rectangle bounds;
    private final boolean rightUp; // true = rising to the right

    public Slope(float x, float y, float w, float h, boolean rightUp) {
        bounds = new Rectangle(x, y, w, h);
        this.rightUp = rightUp;
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public boolean isSolid() {
        return true;
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public void render(SpriteBatch batch) {}

    @Override
    public void debugDraw(ShapeRenderer shape) {
        shape.setColor(Color.ORANGE);
        if (rightUp) {
            shape.triangle(bounds.x, bounds.y,
                bounds.x + bounds.width, bounds.y,
                bounds.x + bounds.width, bounds.y + bounds.height);
        } else {
            shape.triangle(bounds.x, bounds.y,
                bounds.x + bounds.width, bounds.y,
                bounds.x, bounds.y + bounds.height);
        }
    }

    /**
     * Returns the surface Y height of this slope at a given X coordinate.
     * Use this in your collision/ground detection code.
     */
    public float getHeightAtX(float worldX) {
        float localX = worldX - bounds.x;
        if (localX < 0 || localX > bounds.width) {
            return Float.NEGATIVE_INFINITY; // outside slope
        }

        float t = localX / bounds.width;
        if (rightUp) {
            return bounds.y + t * bounds.height;
        } else {
            return bounds.y + (1f - t) * bounds.height;
        }
    }
}

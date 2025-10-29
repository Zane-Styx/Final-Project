package com.jjmc.chromashift.environment;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

public class Wall implements Solid {
    public final Rectangle bounds;

    public Wall(float x, float y, float w, float h) {
        bounds = new Rectangle(x, y, w, h);
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
    public void render(SpriteBatch batch) {
        // Walls currently don't use sprites
    }

    @Override
    public void debugDraw(ShapeRenderer shape) {
        shape.setColor(Color.DARK_GRAY);
        shape.rect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    public boolean isBlocking() {
        return true;
    }
}

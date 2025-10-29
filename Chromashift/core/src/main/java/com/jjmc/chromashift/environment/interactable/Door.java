package com.jjmc.chromashift.environment.interactable;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.jjmc.chromashift.environment.Solid;

public class Door implements Interactable, Solid {
    private final Rectangle bounds;
    private boolean open;
    private float openProgress; // For smooth animation (0 = closed, 1 = open)
    private final float OPEN_SPEED = 3f; // units per second

    public Door(float x, float y, float w, float h) {
        bounds = new Rectangle(x, y, w, h);
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public Rectangle getCollisionBounds() {
        // When fully open, disable collision
        return openProgress >= 1f ? null : bounds;
    }

    @Override
    public boolean isSolid() {
        return !open;
    }

    @Override
    public boolean isBlocking() {
        return !open;
    }

    @Override
    public void update(float delta) {
        // Smooth open/close interpolation
        float target = open ? 1f : 0f;
        if (openProgress != target) {
            float diff = target - openProgress;
            openProgress += Math.signum(diff) * OPEN_SPEED * delta;
            openProgress = Math.max(0, Math.min(1, openProgress));
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        // no texture yet
    }

    @Override
    public void debugDraw(ShapeRenderer shape) {
        // Fade color to show open/close progress
        Color c = new Color(0, 0, 1, 1 - 0.5f * openProgress);
        shape.setColor(c);
        float visibleHeight = bounds.height * (1f - openProgress);
        shape.rect(bounds.x, bounds.y, bounds.width, visibleHeight);
    }

    @Override
    public void interact() {
        open = !open;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }
}

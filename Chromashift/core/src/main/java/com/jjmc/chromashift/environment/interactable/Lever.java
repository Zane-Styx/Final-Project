package com.jjmc.chromashift.environment.interactable;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

public class Lever implements Interactable {
    private final Rectangle bounds;
    private boolean on;
    private Runnable onToggle;

    public Lever(float x, float y, float w, float h) {
        bounds = new Rectangle(x, y, w, h);
    }

    public void setOnToggle(Runnable action) {
        this.onToggle = action;
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public void update(float delta) {}

    @Override
    public void render(SpriteBatch batch) {}

    @Override
    public void debugDraw(ShapeRenderer shape) {
        shape.setColor(on ? Color.ORANGE : Color.GRAY);
        shape.rect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    public void interact() {
        on = !on;
        if (onToggle != null) onToggle.run();
    }

    public boolean isOn() {
        return on;
    }
}

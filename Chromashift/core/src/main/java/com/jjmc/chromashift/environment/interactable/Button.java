package com.jjmc.chromashift.environment.interactable;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

public class Button implements Interactable {
    private final Rectangle bounds;
    private final Door linkedDoor;
    private boolean pressed;

    public Button(float x, float y, float w, float h, Door linkedDoor) {
        this.bounds = new Rectangle(x, y, w, h);
        this.linkedDoor = linkedDoor;
    }

    public void update(float delta, Rectangle playerHitbox) {
        boolean wasPressed = pressed;
        pressed = playerHitbox.overlaps(bounds);
        if (pressed != wasPressed && linkedDoor != null) {
            linkedDoor.setOpen(pressed);
        }
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
        shape.setColor(pressed ? Color.GREEN : Color.RED);
        shape.rect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    public void interact() {
        // Pressure plates don't use F key
    }

    public boolean isPressed() {
        return pressed;
    }
}

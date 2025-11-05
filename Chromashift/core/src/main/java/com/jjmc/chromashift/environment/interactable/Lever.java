package com.jjmc.chromashift.environment.interactable;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;

public class Lever implements Interactable {
    private final Rectangle bounds;
    private boolean on;
    private Runnable onToggle;
    private Interactable target; // optional linked interactable (e.g., a Door)
    private boolean playerNearby = false;

    public Lever(float x, float y, float w, float h) {
        bounds = new Rectangle(x, y, w, h);
    }

    public Lever(float x, float y, float w, float h, Interactable target) {
        bounds = new Rectangle(x, y, w, h);
        this.target = target;
    }

    public void setOnToggle(Runnable action) {
        this.onToggle = action;
    }

    public void setTarget(Interactable target) {
        this.target = target;
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public void update(float delta) {}

    @Override
    public void checkInteraction(Rectangle playerHitbox) {
        // update whether player is standing in interaction bounds
        playerNearby = playerHitbox.overlaps(bounds);
    }

    @Override
    public boolean canInteract() {
        return playerNearby;
    }

    @Override
    public void render(SpriteBatch batch) {}

    @Override
    public void debugDraw(ShapeRenderer shape) {
        // Base block
        shape.setColor(on ? Color.ORANGE : Color.GRAY);
        shape.rect(bounds.x, bounds.y, bounds.width, bounds.height);

        // Draw lever arm pivoting from the center
        float cx = bounds.x + bounds.width * 0.5f;
        float cy = bounds.y + bounds.height * 0.5f;
        float armLen = Math.max(bounds.width, bounds.height) * 1.5f;
        float angleDeg = on ? 30f : -30f;
        double rad = Math.toRadians(angleDeg);
        float ex = cx + (float) Math.cos(rad) * armLen;
        float ey = cy + (float) Math.sin(rad) * armLen;

        // Highlight when player nearby
        shape.setColor(playerNearby ? Color.YELLOW : Color.BLACK);
        shape.line(cx, cy, ex, ey);
    }

    @Override
    public void interact() {
        if (!canInteract()) return;

        on = !on;
        if (onToggle != null) onToggle.run();
        if (target != null) {
            target.interact();
        }
    }

    public boolean isOn() {
        return on;
    }

    public boolean isPlayerNearby() {
        return playerNearby;
    }
}

package com.jjmc.chromashift.environment.interactable;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.chromashift.helper.SoundManager;
import com.chromashift.helper.SpriteAnimator;
import com.jjmc.chromashift.player.Player;
import com.jjmc.chromashift.helper.VisibilityCuller;
import com.jjmc.chromashift.environment.Solid;

/**
 * LockedDoor: solid while closed; consumes one player key to open permanently.
 * Implements both Interactable and Solid for collision participation until opened.
 */
public class LockedDoor implements Interactable, Solid {
    private final Rectangle bounds;
    private SpriteAnimator animator;
    private boolean open = false;
    private static final float WIDTH = 48f;
    private static final float HEIGHT = 96f;
    private Player player; // set by screen after construction

    public LockedDoor(float x, float y) {
        this.bounds = new Rectangle(x, y, WIDTH, HEIGHT);
        try {
            animator = new SpriteAnimator("environment/locked_door.png", 2, 1);
            animator.addAnimation("closed", 0, 0, 1, 1f, true);
            animator.addAnimation("open", 1, 0, 1, 1f, true);
            animator.play("closed", false);
        } catch (Exception e) {
            Gdx.app.error("LockedDoor", "Failed to load door sprite: " + e.getMessage());
        }
    }

    public void setPlayer(Player player) { this.player = player; }

    @Override
    public Rectangle getBounds() {
        // Return null when open to signal no collision (engine checks isSolid too)
        if (open) return null; // collision disabled
        return bounds;
    }

    @Override
    public void update(float delta) {
        if (animator == null) return;
        if (VisibilityCuller.isEnabled() && !VisibilityCuller.isVisible(bounds, 128f)) return;
        animator.update(delta);
    }

    @Override
    public void render(SpriteBatch batch) {
        if (animator == null) return;
        if (VisibilityCuller.isEnabled() && !VisibilityCuller.isVisible(bounds, 128f)) return;
        // Draw appropriate frame based on open state
        animator.setFrame(open ? 1 : 0);
        Rectangle drawRect = bounds;
        animator.render(batch, drawRect.x, drawRect.y, drawRect.width, drawRect.height);
    }

    @Override
    public void debugDraw(ShapeRenderer shape) {
        shape.setColor(open ? Color.GREEN : Color.ORANGE);
        shape.rect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    public void interact() {
        // Called by generic interaction (e.g., F key). Requires player + key.
        if (open || player == null) return;
        if (player.getKeyCount() > 0 && player.consumeKey()) {
            open = true;
            try { SoundManager.play("DoorOpen"); } catch (Exception ignored) {}
            if (animator != null) animator.play("open", true);
            Gdx.app.log("LockedDoor", "Opened. Remaining keys: " + player.getKeyCount());
        } else {
            try { SoundManager.play("Error"); } catch (Exception ignored) {}
        }
    }

    public boolean isOpen() { return open; }

    @Override
    public boolean canInteract() {
        if (open || player == null) return false;
        return player.getKeyCount() > 0 && bounds != null && player.getHitboxRect().overlaps(bounds);
    }

    public void dispose() {
        if (animator != null) animator.dispose();
    }

    // Solid implementation
    @Override
    public boolean isSolid() { return !open; }

    @Override
    public boolean isBlocking() { return !open; }
}

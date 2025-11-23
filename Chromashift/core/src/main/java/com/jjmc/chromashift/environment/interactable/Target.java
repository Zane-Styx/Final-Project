package com.jjmc.chromashift.environment.interactable;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.chromashift.helper.SpriteAnimator;
import com.jjmc.chromashift.environment.Solid;

public class Target implements Interactable, Solid {
    // Reusing ButtonColor for consistency with rows (RED=0, BLUE=1, GREEN=2,
    // YELLOW=3, PURPLE=4)
    private final Button.ButtonColor color;
    private final Rectangle bounds;
    private final SpriteAnimator anim;

    private boolean active = false;
    private boolean hitThisFrame = false;

    private final Array<Door> linkedDoors = new Array<>();
    private final Array<Interactable> linkedInteractables = new Array<>();

    // Target is 32x32
    private static final float SIZE = 32f;

    // Registry of all targets so lasers (which may run before/after targets) can
    // reliably finalize activation state once all lasers have cast for the frame.
    private static final Array<Target> INSTANCES = new Array<>();

    public Target(float x, float y, Button.ButtonColor color) {
        this.bounds = new Rectangle(x, y, SIZE, SIZE);
        this.color = color;

        // Texture: 5 rows (colors), 2 columns (inactive/active)
        this.anim = new SpriteAnimator("environment/laser/target.png", 5, 2);
        // Add animation for this color's row
        // We use 2 frames (col 0 and col 1). We'll manually set the frame based on
        // active state.
        this.anim.addAnimation("default", color.getRowIndex(), 0, 2, 0.1f, false);
        this.anim.play("default", false);
        
        // Register instance for global finalize pass
        INSTANCES.add(this);
    }

    public void addLinkedDoor(Door d) {
        if (d != null && !linkedDoors.contains(d, true)) {
            linkedDoors.add(d);
        }
    }

    public void addLinkedInteractable(Interactable i) {
        if (i != null && !linkedInteractables.contains(i, true)) {
            linkedInteractables.add(i);
        }
    }

    public Button.ButtonColor getColor() {
        return color;
    }

    /**
     * Called by LaserRay when it hits this target.
     * 
     * @param laserColor The color of the laser beam hitting the target.
     */
    public void onLaserHit(Color laserColor) {
        // Check if laser color matches target color
        // We need to map ButtonColor to libGDX Color for comparison
        Color targetColorVal = getColorValue(this.color);

        // Use a small epsilon for float comparison if needed, or just checking equality
        // if they are the same constants
        // LaserRay uses Color.RED, etc. Glass tints it.
        // We should use a tolerance.
        if (colorsMatch(targetColorVal, laserColor)) {
            // Mark that we were hit this frame
            hitThisFrame = true;
            // If not already active, immediately activate and trigger links so
            // activation is instantaneous regardless of update ordering
            if (!active) {
                active = true;
                triggerLinks(true);
            }
        }
    }

    private Color getColorValue(Button.ButtonColor bc) {
        switch (bc) {
            case RED:
                return Color.RED;
            case BLUE:
                return Color.BLUE;
            case GREEN:
                return Color.GREEN;
            case YELLOW:
                return Color.YELLOW;
            case PURPLE:
                return Color.PURPLE;
            default:
                return Color.WHITE;
        }
    }

    private boolean colorsMatch(Color c1, Color c2) {
        float eps = 0.1f; // Tolerance for tinted lasers
        return Math.abs(c1.r - c2.r) < eps &&
                Math.abs(c1.g - c2.g) < eps &&
                Math.abs(c1.b - c2.b) < eps;
    }

    @Override
    public void update(float delta) {
        // Visual update only. Activation/deactivation are handled immediately in
        // onLaserHit() and in the global finalize pass `finalizeFrame()` which is
        // invoked once per frame after all lasers have been updated.
        anim.setFrame(active ? 1 : 0);
        anim.update(delta);
    }

    /**
     * Called once per frame after all Laser/LaserRay instances have been updated
     * so targets that were not hit this frame can be deactivated immediately.
     */
    public static void finalizeFrame() {
        for (int i = 0; i < INSTANCES.size; i++) {
            Target t = INSTANCES.get(i);
            boolean wasActive = t.active;
            // If this frame we were not hit but were previously active, deactivate
            if (!t.hitThisFrame && wasActive) {
                t.active = false;
                t.triggerLinks(false);
            }
            // reset hit flag for next frame
            t.hitThisFrame = false;
            // update sprite frame to reflect final state
            if (t.anim != null) t.anim.setFrame(t.active ? 1 : 0);
        }
    }

    private void triggerLinks(boolean open) {
        for (Door d : linkedDoors) {
            d.setOpen(open);
        }
        for (Interactable i : linkedInteractables) {
            // For generic interactables, we just call interact().
            // If they need specific state (like open/close), they might not support it via
            // interact().
            // But the prompt says "Trigger all linked objects... Emit
            // activation/deactivation events just like a lever."
            // Lever calls interact() on toggle.
            // But for continuous state (active/inactive), we might need to set state.
            // However, Interactable interface only has interact().
            // If the linked object is a Door, we handled it.
            // If it's something else, we call interact() which usually toggles.
            // But Target is "Active while laser hits". If it toggles something else
            // repeatedly, that's bad.
            // But usually Targets link to Doors or Platforms (Solids).
            // We'll stick to interact() for generic, setOpen for Doors.
            if (open) {
                i.interact();
            } else {
                // If it needs deactivation, Interactable doesn't support it generically.
                // But Lever only calls interact().
                // Wait, Lever usually toggles. Target is momentary (hold).
                // If we link a Target to a Lever-controlled object, it might behave weirdly if
                // it expects toggle.
                // But for now, this is the best we can do.
                // Actually, if the user wants "Emit activation/deactivation events just like a
                // lever",
                // Lever usually calls setOpen on doors.
            }
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        anim.render(batch, bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    public void debugDraw(ShapeRenderer shape) {
        shape.setColor(active ? Color.GREEN : Color.RED);
        shape.rect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    public void interact() {
        // Player cannot interact directly
    }

    @Override
    public boolean canInteract() {
        return false;
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    // Solid implementation
    @Override
    public Rectangle getCollisionBounds() {
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

    public Array<Door> getLinkedDoors() {
        return linkedDoors;
    }

    public Array<Interactable> getLinkedInteractables() {
        return linkedInteractables;
    }
}

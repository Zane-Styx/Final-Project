package com.jjmc.chromashift.environment;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.jjmc.chromashift.environment.interactable.Interactable;

/**
 * Simple non-blocking trigger zone that can be placed in LevelMaker.
 * It renders as a colored rectangle in debug view and exposes bounds for detection.
 * Triggers are identified by a unique id for easy reference by boss/other systems.
 */
public class TriggerZone implements Interactable {
    private final String id;
    private final Rectangle bounds;
    private final Color color;

    public TriggerZone(float x, float y, float width, float height, String id, Color color) {
        this.bounds = new Rectangle(x, y, width, height);
        this.id = id == null ? "" : id;
        this.color = color == null ? Color.RED : color.cpy();
    }

    public String getId() {
        return id;
    }

    public Color getColor() {
        return color;
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public void update(float delta) {
        // no-op
    }

    @Override
    public void render(SpriteBatch batch) {
        // invisible in normal render pass
    }

    @Override
    public void debugDraw(ShapeRenderer shape) {
        if (shape == null) return;
        shape.setColor(color);
        shape.rect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    public void interact() {
        // triggers are passive; interaction handled by systems that query bounds
    }
}

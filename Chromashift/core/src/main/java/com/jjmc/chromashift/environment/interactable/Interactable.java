package com.jjmc.chromashift.environment.interactable;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

public interface Interactable {
    Rectangle getBounds();
    void update(float delta);
    void render(SpriteBatch batch);
    void debugDraw(ShapeRenderer shape);
    void interact(); // what happens when the player activates it
    default void checkInteraction(Rectangle playerHitbox) {
        // Default implementation - override in subclasses if needed
        // By default, do nothing when checking interaction
    }
    default boolean canInteract() {
        // Default implementation - override in subclasses if needed
        return false;
    }
}

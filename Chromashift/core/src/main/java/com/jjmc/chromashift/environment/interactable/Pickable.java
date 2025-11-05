package com.jjmc.chromashift.environment.interactable;

import com.jjmc.chromashift.player.Player;

/**
 * Simple marker interface for objects that can be picked up and thrown by the player.
 */
public interface Pickable {
    /** Called when the player picks up this object. */
    void pickUp(Player player);

    /** Called to throw the object with an initial velocity. */
    void throwWithVelocity(float vx, float vy);

    /** Returns true when currently being held by a player. */
    boolean isHeld();

    /** Force-drop the object (no initial velocity). */
    void drop();
}

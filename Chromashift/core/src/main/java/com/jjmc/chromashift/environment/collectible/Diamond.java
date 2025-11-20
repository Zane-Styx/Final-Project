package com.jjmc.chromashift.environment.collectible;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.chromashift.helper.SpriteAnimator;
import com.jjmc.chromashift.player.Player;
import com.chromashift.helper.SoundManager;

/**
 * Diamond collectible that animates and can be collected by the player.
 * Uses assets/environment/diamond.png (32x32, 6 frames).
 */
public class Diamond extends Collectible {
    private SpriteAnimator animator;
    private static final float DIAMOND_SIZE = 32f;
    private static final int FRAME_COUNT = 6;
    private static final float FRAME_DURATION = 0.15f;

    public Diamond(float x, float y) {
        super(x, y, DIAMOND_SIZE, DIAMOND_SIZE);
        
        try {
            // Create animator with diamond sprite sheet (1 row, 6 columns)
            animator = new SpriteAnimator("environment/diamond.png", 1, FRAME_COUNT);
            animator.addAnimation("spin", 0, 0, FRAME_COUNT, FRAME_DURATION, true);
            animator.play("spin", false);
        } catch (Exception e) {
            Gdx.app.error("Diamond", "Failed to load diamond sprite: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(float delta) {
        if (!collected && animator != null) {
            animator.update(delta);
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        if (!collected && animator != null) {
            animator.render(batch, x, y, width, height);
        }
    }

    @Override
    public void onCollect(Player player) {
        if (player != null) {
            player.addDiamonds(1);
            // Play collection sound
            try {
                SoundManager.play("PickUpItem");
            } catch (Exception e) {
                // Sound not available, continue silently
                Gdx.app.log("Diamond", "Collection sound not available");
            }
            Gdx.app.log("Diamond", "Collected! Player now has " + player.getDiamonds() + " diamonds");
        }
    }

    @Override
    public void dispose() {
        if (animator != null) {
            animator.dispose();
        }
    }
}

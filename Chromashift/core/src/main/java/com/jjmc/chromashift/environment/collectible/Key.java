package com.jjmc.chromashift.environment.collectible;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.chromashift.helper.SpriteAnimator;
import com.chromashift.helper.VisibilityCuller;
import com.jjmc.chromashift.player.Player;
import com.chromashift.helper.SoundManager;

/**
 * Animated key collectible increasing player's key count.
 */
public class Key extends Collectible {
    private SpriteAnimator animator;
    private static final float SIZE = 32f;

    public Key(float x, float y) {
        super(x, y, SIZE, SIZE);
        try {
            animator = new SpriteAnimator("environment/key.png", 1, 16);
            animator.addAnimation("idle", 0, 0, 16, 0.08f, true);
            animator.play("idle", false);
        } catch (Exception e) {
            Gdx.app.error("Key", "Failed to load key sprite: " + e.getMessage());
        }
    }

    @Override
    public void update(float delta) {
        if (collected || animator == null) return;
        if (VisibilityCuller.isEnabled() && !VisibilityCuller.isVisible(getBounds(), 64f)) return;
        animator.update(delta);
    }

    @Override
    public void render(SpriteBatch batch) {
        if (collected || animator == null) return;
        if (VisibilityCuller.isEnabled() && !VisibilityCuller.isVisible(getBounds(), 64f)) return;
        animator.render(batch, x, y, width, height);
    }

    @Override
    public void onCollect(Player player) {
        if (player != null) {
            player.addKey(1);
            try { SoundManager.play("PickUpItem"); } catch (Exception ignored) {}
            Gdx.app.log("Key", "Collected! Keys now: " + player.getKeyCount());
        }
    }

    @Override
    public void dispose() {
        if (animator != null) animator.dispose();
    }
}

package com.jjmc.chromashift.environment.collectible;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jjmc.chromashift.player.Player;
import com.chromashift.helper.SoundManager;
import com.jjmc.chromashift.helper.VisibilityCuller;

/**
 * Health Potion collectible that restores player health when collected.
 * Static sprite (no animation) at 32x32 size.
 */
public class HealthPotion extends Collectible {
    private static final float POTION_SIZE = 32f;
    private Texture potionTexture;
    private TextureRegion potionRegion;

    public HealthPotion(float x, float y) {
        super(x, y, POTION_SIZE, POTION_SIZE);
        loadTexture();
    }

    private void loadTexture() {
        try {
            potionTexture = new Texture(Gdx.files.internal("player/ui/HealthPotion.png"));
            potionRegion = new TextureRegion(potionTexture, 0, 0, (int) POTION_SIZE, (int) POTION_SIZE);
        } catch (Exception e) {
            Gdx.app.error("HealthPotion", "Failed to load potion sprite: " + e.getMessage());
        }
    }

    @Override
    public void update(float delta) {
        if (collected) return;
        // Skip culling when disabled (editor)
        if (VisibilityCuller.isEnabled() && !VisibilityCuller.isVisible(getBounds(), 64f)) return;
        // No animation, static sprite
    }

    @Override
    public void render(SpriteBatch batch) {
        if (collected || potionRegion == null) return;
        if (VisibilityCuller.isEnabled() && !VisibilityCuller.isVisible(getBounds(), 64f)) return;
        batch.draw(potionRegion, x, y, width, height);
    }

    @Override
    public void onCollect(Player player) {
        if (player != null) {
            player.addPotion(1);
            // Play collection sound
            try {
                SoundManager.play("PickUpItem");
            } catch (Exception e) {
                Gdx.app.log("HealthPotion", "Collection sound not available");
            }
            Gdx.app.log("HealthPotion", "Collected! Player now has " + player.getPotionCount() + " potions");
        }
    }

    @Override
    public void dispose() {
        if (potionTexture != null) {
            potionTexture.dispose();
        }
    }
}

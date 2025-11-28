package com.jjmc.chromashift.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jjmc.chromashift.player.Player;
import com.jjmc.chromashift.player.PlayerType;

public class PlayerUI {
    private final Player player;
    private Viewport viewport;

    // Textures
    private final Texture playerIconTexture;
    private final Texture hpBarTexture;
    private final Texture hpFrameTexture;
    private final Texture armorTexture;
    private final Texture dashCdTexture;
    // Preallocated region for health bar cropping
    private final TextureRegion hpBarRegion;

    // Diamond UI
    private Texture diamondTexture;
    private TextureRegion diamondRegion;
    private BitmapFont font;
    // Key UI
    private Texture keyIconTexture;
    private TextureRegion keyIconRegion;
    // Potion UI
    private Texture potionTexture;
    private TextureRegion potionRegion;

    // Regions for player icon variations
    private final TextureRegion[] iconRegions;

    // Dimensions
    private static final float UI_WIDTH = 581f;
    private static final float UI_HEIGHT = 174f;
    private static final float ARMOR_SIZE = 32f;
    private static final float DASH_SIZE = 32f;

    // HP bar placement within the 581x174 texture (pixel-perfect)
    private static final int BAR_LEFT = 159; // px from left
    private static final int BAR_TOP = 116; // px from top
    private static final int BAR_BOTTOM = 38; // px from bottom
    private static final int BAR_WIDTH_PX = 395; // full bar pixel width
    private static final int BAR_HEIGHT_PX = 20; // 174 - 116 - 38

    // Default scaled down UI
    private float scale = 0.4f;

    // Margin from top-left corner
    private static final float MARGIN_X = 10f;
    private static final float MARGIN_Y = 10f;

    public PlayerUI(Player player, Viewport viewport) {
        this.player = player;
        this.viewport = viewport;

        // Load assets
        playerIconTexture = new Texture(Gdx.files.internal("player/ui/PlayerIcon.png"));
        hpBarTexture = new Texture(Gdx.files.internal("player/ui/HP_bar.png"));
        hpFrameTexture = new Texture(Gdx.files.internal("player/ui/HP_frame.png"));
        armorTexture = new Texture(Gdx.files.internal("player/ui/Armor.png"));
        dashCdTexture = new Texture(Gdx.files.internal("player/ui/DashCd.png"));
        hpBarRegion = new TextureRegion(hpBarTexture);

        // Setup icon regions (7 rows)
        // Texture is 581 wide, 174 * 7 high
        int iconWidth = 581;
        int iconHeight = 174;
        iconRegions = new TextureRegion[7];
        for (int i = 0; i < 7; i++) {
            iconRegions[i] = new TextureRegion(playerIconTexture, 0, i * iconHeight, iconWidth, iconHeight);
        }
    }

    public void render(SpriteBatch batch) {

        float camX = viewport.getCamera().position.x;
        float camY = viewport.getCamera().position.y;
        float camViewportWidth = viewport.getCamera().viewportWidth;
        float camViewportHeight = viewport.getCamera().viewportHeight;
        if (viewport.getCamera() instanceof com.badlogic.gdx.graphics.OrthographicCamera ortho) {
            camViewportWidth *= ortho.zoom;
            camViewportHeight *= ortho.zoom;
        }
        // Anchor to top-left of visible camera area
        float uiX = camX - camViewportWidth / 2f + MARGIN_X;
        float uiY = camY + camViewportHeight / 2f - MARGIN_Y;

        int colorIndex = getColorIndex(player.getType());
        if (colorIndex >= 0 && colorIndex < iconRegions.length) {
            batch.draw(iconRegions[colorIndex], uiX, uiY - UI_HEIGHT * scale, UI_WIDTH * scale, UI_HEIGHT * scale);
        }

        float healthPercent = player.getHealthSystem().getCurrentHealth() / player.getHealthSystem().getMaxHealth();
        if (healthPercent < 0f)
            healthPercent = 0f;
        if (healthPercent > 1f)
            healthPercent = 1f;

        int filledPixels = Math.round(BAR_WIDTH_PX * healthPercent);
        if (filledPixels > 0) {
            // Crop the region to the filled area, starting at the bar's left/top offsets
            hpBarRegion.setRegion(BAR_LEFT, BAR_TOP, filledPixels, BAR_HEIGHT_PX);
            float frameBottomY = uiY - UI_HEIGHT * scale;
            float barDrawX = uiX + BAR_LEFT * scale;
            float barDrawY = frameBottomY + BAR_BOTTOM * scale;
            batch.draw(hpBarRegion, barDrawX, barDrawY, filledPixels * scale, BAR_HEIGHT_PX * scale);
        }

        batch.draw(hpFrameTexture, uiX, uiY - UI_HEIGHT * scale, UI_WIDTH * scale, UI_HEIGHT * scale);

        // 4. Armor Icons
        float armorY = uiY - UI_HEIGHT * scale - ARMOR_SIZE * scale - 10 * scale; // 10px padding
        float armorStartX = uiX + 20 * scale; // 20px padding from left

        int shield = player.getShield();
        for (int i = 0; i < 3; i++) {
            float x = armorStartX + i * (ARMOR_SIZE + 5) * scale; // 5px gap
            if (i < shield) {
                batch.setColor(Color.WHITE);
            } else {
                batch.setColor(0.3f, 0.3f, 0.3f, 0.5f); // Dimmed for empty
            }
            batch.draw(armorTexture, x, armorY, ARMOR_SIZE * scale, ARMOR_SIZE * scale);
        }
        batch.setColor(Color.WHITE); // Reset

        // 5. Dash Cooldown
        float dashY = armorY - DASH_SIZE * scale - 10 * scale;
        float dashX = armorStartX;

        float dashMax = player.getDashCooldownMax();
        float dashTimer = player.getDashCooldownTimer();
        float progress = 1f;
        if (dashMax > 0) {
            progress = 1f - (dashTimer / dashMax);
        }
        if (progress < 0)
            progress = 0;
        if (progress > 1)
            progress = 1;

        float c = 0.3f + 0.7f * progress; // Start darker (0.3) to be more visible as "cooldown"
        batch.setColor(c, c, c, 1f);
        batch.draw(dashCdTexture, dashX, dashY, DASH_SIZE * scale, DASH_SIZE * scale);
        batch.setColor(Color.WHITE);

        // 6. Diamond Count (Top-Left)
        if (diamondTexture == null) {
            try {
                diamondTexture = new Texture(Gdx.files.internal("environment/diamond.png"));
                diamondRegion = new TextureRegion(diamondTexture, 0, 0, 32, 32);
            } catch (Exception ignored) {
            }
        }

        if (diamondRegion != null) {
            float diamondSize = 32f * scale;
            float diamondX = uiX + 20 * scale;
            float diamondY = dashY - diamondSize - 10 * scale;
            float keyY = 0f;;

            batch.draw(diamondRegion, diamondX, diamondY, diamondSize, diamondSize);

            if (font != null) {
                font.getData().setScale(scale * 2f);
                font.draw(batch, "x " + player.getDiamonds(), diamondX + diamondSize + 5 * scale,
                        diamondY + diamondSize / 1.5f);
            }
            // Keys below diamonds
            ensureKeyIcon();
            if (keyIconRegion != null) {
                float keySize = 28f * scale;
                float keyX = diamondX;
                keyY = diamondY - keySize - 6 * scale;
                batch.draw(keyIconRegion, keyX, keyY, keySize, keySize);
                if (font != null) {
                    font.getData().setScale(scale * 2f);
                    font.draw(batch, "x " + player.getKeys(), keyX + keySize + 5 * scale,
                            keyY + keySize / 1.5f);
                }
            }
            // Potions below keys
            ensurePotionIcon();
            if (potionRegion != null) {
                float potionSize = 28f * scale;
                float potionX = diamondX;
                float potionY = keyY - potionSize - 6 * scale;
                batch.draw(potionRegion, potionX, potionY, potionSize, potionSize);
                if (font != null) {
                    font.getData().setScale(scale * 2f);
                    font.draw(batch, "x " + player.getPotionCount(), potionX + potionSize + 5 * scale,
                            potionY + potionSize / 1.5f);
                }
            }
        }
    }

    private int getColorIndex(PlayerType type) {
        switch (type) {
            case RED:
                return 0;
            case BLUE:
                return 1;
            case GREEN:
                return 2;
            case PINK:
                return 3;
            case PURPLE:
                return 4;
            case ORANGE:
                return 5;
            case YELLOW:
                return 6;
            default:
                return 0;
        }
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public void setFont(BitmapFont font) {
        this.font = font;
    }

    public void dispose() {
        playerIconTexture.dispose();
        hpBarTexture.dispose();
        hpFrameTexture.dispose();
        armorTexture.dispose();
        dashCdTexture.dispose();
        if (diamondTexture != null)
            diamondTexture.dispose();
        if (keyIconTexture != null) keyIconTexture.dispose();
        if (potionTexture != null) potionTexture.dispose();
    }

    public void setViewport(Viewport viewport) {
        if (viewport != null) {
            this.viewport = viewport;
        }
    }

    private void ensureKeyIcon() {
        if (keyIconTexture != null) return;
        try {
            keyIconTexture = new Texture(Gdx.files.internal("environment/key_icon.png"));
            keyIconRegion = new TextureRegion(keyIconTexture, 0, 0, keyIconTexture.getWidth(), keyIconTexture.getHeight());
        } catch (Exception ignored) {}
    }
    
    private void ensurePotionIcon() {
        if (potionTexture != null) return;
        try {
            potionTexture = new Texture(Gdx.files.internal("player/ui/HealthPotion.png"));
            potionRegion = new TextureRegion(potionTexture, 0, 0, potionTexture.getWidth(), potionTexture.getHeight());
        } catch (Exception ignored) {}
    }
}

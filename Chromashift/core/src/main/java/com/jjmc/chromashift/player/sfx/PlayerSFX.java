package com.jjmc.chromashift.player.sfx;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.chromashift.helper.SpriteAnimator;
import com.jjmc.chromashift.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * PlayerSFX: Dedicated visual-effects handler for the player.
 * Supports per-instance follow/static behavior, per-direction offsets, flipping,
 * and automatic lifecycle management based on animation completion.
 */
public class PlayerSFX {

    /**
     * Definition for a reusable SFX type.
     */
    public static class SFXDefinition {
        public final String spritePath;
        public final int rows;
        public final int cols;
        public final int frameCount;
        public final int frameWidth;
        public final int frameHeight;
        public final float frameDuration;
        public final boolean loop;

        // Default offsets (can be overridden per-spawn)
        public final Vector2 defaultOffsetRight;
        public final Vector2 defaultOffsetLeft;

        public SFXDefinition(String spritePath, int rows, int cols, int frameCount,
                             int frameWidth, int frameHeight, float frameDuration,
                             Vector2 defaultOffsetRight, Vector2 defaultOffsetLeft, boolean loop) {
            this.spritePath = spritePath;
            this.rows = rows;
            this.cols = cols;
            this.frameCount = frameCount;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.frameDuration = frameDuration;
            this.defaultOffsetRight = defaultOffsetRight == null ? new Vector2(0,0) : defaultOffsetRight;
            this.defaultOffsetLeft = defaultOffsetLeft == null ? new Vector2(0,0) : defaultOffsetLeft;
            this.loop = loop;
        }
    }

    /**
     * A single active SFX instance.
     */
    public static class SFXInstance {
        public final SpriteAnimator animator;
        public final Player owner; // may be null for static ones
        public final boolean followPlayer;
        public boolean flipX;
        public final Vector2 offsetR;
        public final Vector2 offsetL;
        public final int width;
        public final int height;

        // current world position
        public final Vector2 pos = new Vector2();

        public SFXInstance(SpriteAnimator animator, Player owner, boolean followPlayer,
                           Vector2 offsetR, Vector2 offsetL, boolean initialFlip,
                           float spawnX, float spawnY, int width, int height) {
            this.animator = animator;
            this.owner = owner;
            this.followPlayer = followPlayer;
            this.offsetR = offsetR == null ? new Vector2(0,0) : offsetR.cpy();
            this.offsetL = offsetL == null ? new Vector2(0,0) : offsetL.cpy();
            this.flipX = initialFlip;
            this.width = width;
            this.height = height;
            this.pos.set(spawnX, spawnY);
            // ensure animator respects initial flip
            animator.setFlipX(initialFlip);
        }

        public boolean isFinished() {
            return animator.isAnimationFinished();
        }

        public void dispose() {
            animator.dispose();
        }
    }

    // Registry of SFX definitions: id -> SFXDefinition
    private final Map<String, SFXDefinition> registry = new HashMap<>();

    // Active SFX instances
    private final Array<SFXInstance> activeSFX = new Array<>();

    public PlayerSFX() {
        registerDefaultSFX();
    }

    private void registerDefaultSFX() {
        // Dash SFX: behind player. width/height 32x32, quick playback
        registry.put("dash", new SFXDefinition(
                "player/sfx/dash.png",
                1, 10, 10,
                32, 32,
                0.01f,
                new Vector2(-16f, 0f), // offset when facing right (spawn behind)
                new Vector2(16f, 0f),  // offset when facing left (spawn behind on left)
                false
        ));
    }

    public void registerSFX(String id, SFXDefinition def) {
        registry.put(id, def);
    }

    /**
     * Spawn a dash SFX. Dash trail should be static (not follow the player)
     * and respect facing direction.
     */
    public void spawnDashSFX(Player player) {
        // Use the registered dash definition and spawn as static (follow=false)
        SFXDefinition def = registry.get("dash");
        if (def == null) {
            com.badlogic.gdx.Gdx.app.error("PlayerSFX", "dash definition missing");
            return;
        }
        // Choose offsets based on facing direction
        Vector2 offR = def.defaultOffsetRight.cpy();
        Vector2 offL = def.defaultOffsetLeft.cpy();
        spawnCustomSFX(player, "dash", false, offR, offL);
    }

    /**
     * Spawn a custom SFX instance with full control over follow/static and offsets.
     * @param player owner (used for followPlayer true)
     * @param id registered SFX id
     * @param followPlayer if true, SFX follows player's position with offsets; otherwise stays static
     * @param offsetR offset when player faces right (local offset)
     * @param offsetL offset when player faces left (local offset)
     */
    public void spawnCustomSFX(Player player, String id, boolean followPlayer, Vector2 offsetR, Vector2 offsetL) {
        SFXDefinition def = registry.get(id);
        if (def == null) {
            com.badlogic.gdx.Gdx.app.error("PlayerSFX", "Unknown SFX ID: " + id);
            return;
        }

        try {
            SpriteAnimator animator = new SpriteAnimator(def.spritePath, def.rows, def.cols);
            animator.addAnimation("play", 0, 0, def.frameCount, def.frameDuration, def.loop);

            boolean facingLeft = player != null && player.isFacingLeft();

            // pick offsets: prefer provided offsets, otherwise fallback to definition defaults
            Vector2 oR = offsetR != null ? offsetR.cpy() : def.defaultOffsetRight.cpy();
            Vector2 oL = offsetL != null ? offsetL.cpy() : def.defaultOffsetLeft.cpy();

            // Determine spawn position based on facing direction
            Vector2 chosen = facingLeft ? oL : oR;
            float spawnX = player.getX() + chosen.x;
            float spawnY = player.getY() + chosen.y;

            // instantiate and add
            SFXInstance instance = new SFXInstance(animator, player, followPlayer, oR, oL, facingLeft, spawnX, spawnY, def.frameWidth, def.frameHeight);
            animator.play("play", facingLeft);
            activeSFX.add(instance);

        } catch (Exception e) {
            com.badlogic.gdx.Gdx.app.error("PlayerSFX", "Failed to spawn SFX '" + id + "': " + e.getMessage(), e);
        }
    }

    /**
     * Update all active SFX instances.
     */
    public void update(float delta) {
        for (int i = activeSFX.size - 1; i >= 0; --i) {
            SFXInstance s = activeSFX.get(i);
            // If follows player, recalc pos and flip based on current facing
            if (s.followPlayer && s.owner != null) {
                boolean facingLeft = s.owner.isFacingLeft();
                Vector2 off = facingLeft ? s.offsetL : s.offsetR;
                s.pos.set(s.owner.getX() + off.x, s.owner.getY() + off.y);
                // Update flip without restarting animation
                if (s.flipX != facingLeft) {
                    s.flipX = facingLeft;
                    s.animator.setFlipX(facingLeft);
                }
            }

            s.animator.update(delta);

            if (s.isFinished()) {
                s.dispose();
                activeSFX.removeIndex(i);
            }
        }
    }

    /**
     * Render active SFX instances.
     */
    public void render(SpriteBatch batch) {
        for (SFXInstance s : activeSFX) {
            if (s == null || s.animator == null) continue;
            s.animator.render(batch, s.pos.x, s.pos.y, s.width, s.height);
        }
    }

    public int getActiveSFXCount() { return activeSFX.size; }

    public void clear() {
        for (SFXInstance s : activeSFX) s.dispose();
        activeSFX.clear();
    }

    public void dispose() {
        clear();
        registry.clear();
    }
}

package com.chromashift.helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.Assets;

import java.util.HashMap;
import java.util.Map;

/**
 * Local SpriteAnimator replacement that uses the project's AssetManager so
 * textures are preloaded on the GL thread (Assets.loadAll()).
 *
 * This provides the minimal API used by the game code: constructors,
 * addAnimation / addAnimationFromTexture, play, update, render, reset, dispose.
 */
public class SpriteAnimator {
    private final Map<String, Animation<TextureRegion>> animations = new HashMap<>();
    private final Map<String, Float> animFrameDuration = new HashMap<>();
    private final Map<String, Boolean> animLoop = new HashMap<>();

    private String currentName = null;
    private float stateTime = 0f;
    private boolean flipX = false;

    // optional sprite sheet (if constructed with a tiled sheet)
    private Texture baseTexture;
    private TextureRegion[][] splitRegions;
    private int rows = 0, cols = 0;

    public SpriteAnimator() {
    }

    public SpriteAnimator(String spritePath, int rows, int cols) {
        try {
            this.baseTexture = Assets.manager.get(spritePath, Texture.class);
            this.rows = rows;
            this.cols = cols;
            if (baseTexture != null && rows > 0 && cols > 0) {
                splitRegions = TextureRegion.split(baseTexture, baseTexture.getWidth() / cols, baseTexture.getHeight() / rows);
            }
        } catch (Exception e) {
            Gdx.app.error("SpriteAnimator", "Failed to load spritePath=" + spritePath, e);
        }
    }

    /**
     * Add an animation from a tiled sprite sheet previously provided to the
     * constructor via (spritePath, rows, cols).
     */
    public void addAnimation(String name, int row, int startCol, int frames, float frameDuration, boolean loop) {
        if (splitRegions == null) return;
        Array<TextureRegion> regs = new Array<>();
        for (int c = startCol; c < startCol + frames; c++) {
            if (row < splitRegions.length && c < splitRegions[row].length) regs.add(splitRegions[row][c]);
        }
        Animation<TextureRegion> a = new Animation<>(frameDuration, regs, Animation.PlayMode.NORMAL);
        animations.put(name, a);
        animFrameDuration.put(name, frameDuration);
        animLoop.put(name, loop);
    }

    /**
     * Add an animation by slicing an arbitrary texture into frames of size
     * frameWidth x frameHeight. Useful when the animation is a separate file.
     */
    public void addAnimationFromTexture(String animationName, String atlasPath, int frameWidth, int frameHeight, int frameCount, float frameDuration, boolean loop) {
        try {
            Texture tex = Assets.manager.get(atlasPath, Texture.class);
            if (tex == null) return;
            TextureRegion[][] split = TextureRegion.split(tex, frameWidth, frameHeight);
            Array<TextureRegion> regs = new Array<>();
            int collected = 0;
            outer:
            for (TextureRegion[] row : split) {
                for (TextureRegion r : row) {
                    regs.add(r);
                    collected++;
                    if (collected >= frameCount) break outer;
                }
            }
            Animation<TextureRegion> a = new Animation<>(frameDuration, regs);
            animations.put(animationName, a);
            animFrameDuration.put(animationName, frameDuration);
            animLoop.put(animationName, loop);
        } catch (Exception e) {
            Gdx.app.error("SpriteAnimator", "addAnimationFromTexture failed for=" + atlasPath, e);
        }
    }

    public void play(String name, boolean flipX) {
        if (!animations.containsKey(name)) return;
        this.currentName = name;
        this.stateTime = 0f;
        this.flipX = flipX;
    }

    /**
     * Set the current animation to show a specific frame index immediately.
     * If no animation is playing, this is a no-op.
     */
    public void setFrame(int frameIndex) {
        if (currentName == null) return;
        float fd = animFrameDuration.getOrDefault(currentName, 0.1f);
        if (fd <= 0f) fd = 0.1f;
        this.stateTime = frameIndex * fd;
    }

    public void update(float delta) {
        if (currentName == null) return;
        stateTime += delta;
    }

    public void render(SpriteBatch batch, float x, float y, float width, float height) {
        if (currentName == null) return;
        Animation<TextureRegion> a = animations.get(currentName);
        if (a == null) return;
        boolean loop = animLoop.getOrDefault(currentName, true);
        TextureRegion frame = a.getKeyFrame(stateTime, loop);
        if (frame == null) return;
        if (flipX) {
            boolean old = frame.isFlipX();
            frame.flip(!old, frame.isFlipY());
            batch.draw(frame, x, y, width, height);
            frame.flip(!old, frame.isFlipY());
        } else {
            batch.draw(frame, x, y, width, height);
        }
    }

    public boolean isAnimationFinished() {
        if (currentName == null) return true;
        Animation<TextureRegion> a = animations.get(currentName);
        if (a == null) return true;
        return a.isAnimationFinished(stateTime);
    }

    public void reset() {
        this.stateTime = 0f;
        this.currentName = null;
    }

    public String getCurrentAnimationName() { return currentName; }

    public void dispose() {
        // Do NOT dispose textures here: Assets.manager owns them.
        animations.clear();
        animFrameDuration.clear();
        animLoop.clear();
    }

    @Override
    public String toString() {
        return "SpriteAnimator[animations=" + animations.keySet() + "]";
    }
}

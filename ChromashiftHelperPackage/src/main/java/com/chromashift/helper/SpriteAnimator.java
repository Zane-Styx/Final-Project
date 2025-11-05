package com.chromashift.helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SpriteAnimator - handles multiple named animations from a sprite sheet,
 * supports per-animation flipping and adding animations from separate textures.
 */
public class SpriteAnimator {

    // Optional primary (grid) texture and split frames
    private final Texture baseTexture;
    private final TextureRegion[][] gridFrames;

    // Extra textures loaded for separate animations (disposed on dispose())
    private final List<Texture> extraTextures = new ArrayList<>();

    private final Map<String, Animation> animations = new HashMap<>();

    private Animation currentAnimation;
    private TextureRegion currentFrame;
    private float stateTime = 0f;
    private boolean isPlaying = false;

    /** Create from a uniform sprite sheet (rows x cols). */
    public SpriteAnimator(String path, int rows, int cols) {
        if (rows <= 0 || cols <= 0) throw new IllegalArgumentException("rows/cols must be > 0");
        baseTexture = new Texture(Gdx.files.internal(path));
        gridFrames = TextureRegion.split(baseTexture,
                baseTexture.getWidth() / cols,
                baseTexture.getHeight() / rows);
    }

    /** Create an empty animator (useful when adding animations from separate textures). */
    public SpriteAnimator() {
        baseTexture = null;
        gridFrames = null;
    }

    // ---------------- Grid-based helper (keeps original behavior) ----------------

    /**
     * Adds an animation using the base grid (row, startCol, frameCount).
     */
    public void addAnimation(String name, int row, int startCol, int frameCount, float frameDuration, boolean looping) {
        if (gridFrames == null) throw new IllegalStateException("No base grid available. Use grid constructor.");
        TextureRegion[] frames = new TextureRegion[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frames[i] = gridFrames[row][startCol + i];
        }
        animations.put(name, new Animation(frames, frameDuration, looping));
    }

    // ---------------- Custom animation support ----------------

    /**
     * Adds an animation from an explicit TextureRegion array (any size / texture).
     */
    public void addAnimation(String name, TextureRegion[] frames, float frameDuration, boolean looping) {
        animations.put(name, new Animation(frames, frameDuration, looping));
    }

    /**
     * Loads a separate texture and slices frames horizontally from it.
     *
     * @param name         animation name
     * @param path         internal path to texture
     * @param frameWidth   width of each frame in pixels
     * @param frameHeight  height of each frame in pixels
     * @param frameCount   number of frames (reads left-to-right starting at x=0,y=0)
     * @param frameDuration seconds per frame
     * @param looping      whether to loop
     */
    public void addAnimationFromTexture(String name, String path, int frameWidth, int frameHeight, int frameCount, float frameDuration, boolean looping) {
        Texture tex = new Texture(Gdx.files.internal(path));
        extraTextures.add(tex);

        TextureRegion[] frames = new TextureRegion[frameCount];
        // slice across the top row; if texture contains more rows you'd adapt this method
        for (int i = 0; i < frameCount; i++) {
            int x = i * frameWidth;
            frames[i] = new TextureRegion(tex, x, 0, frameWidth, frameHeight);
        }
        animations.put(name, new Animation(frames, frameDuration, looping));
    }

    // ---------------- Playback control ----------------

    /**
     * Plays a named animation, optionally flipped horizontally.
     */
    public void play(String name, boolean flipX) {
        Animation anim = animations.get(name);
        if (anim == null) throw new IllegalArgumentException("Animation not found: " + name);

        // Only restart if different animation or flip state changed
        if (currentAnimation != anim || anim.flippedX != flipX) {
            anim.applyFlip(flipX);
            currentAnimation = anim;
            stateTime = 0f;
            currentFrame = anim.frames.length > 0 ? anim.frames[0] : null;
        }
        isPlaying = true;
    }

    public void play(String name) { play(name, false); }

    public void pause() { isPlaying = false; }
    public void resume() { isPlaying = true; }
    public void reset() {
        stateTime = 0f;
        if (currentAnimation != null) currentFrame = currentAnimation.frames[0];
    }

    /**
     * Update the animation (advance stateTime). Call every frame with delta time.
     */
    public void update(float delta) {
        if (!isPlaying || currentAnimation == null) return;

        stateTime += delta;
        int frameIndex = (int) (stateTime / currentAnimation.frameDuration);
        if (currentAnimation.looping) {
            if (currentAnimation.frames.length > 0)
                frameIndex = frameIndex % currentAnimation.frames.length;
            else
                frameIndex = 0;
        } else {
            frameIndex = Math.min(frameIndex, currentAnimation.frames.length - 1);
        }
        currentFrame = currentAnimation.frames[frameIndex];
    }

    /**
     * Set the current frame index of the currently selected animation.
     * This updates the internal stateTime so subsequent calls to update()
     * will continue from the chosen frame.
     *
     * @param index frame index (0-based)
     * @throws IllegalStateException if no animation is currently selected
     * @throws IllegalArgumentException if index is out of bounds
     */
    public void setFrame(int index) {
        if (currentAnimation == null) throw new IllegalStateException("No current animation selected");
        if (currentAnimation.frames == null || currentAnimation.frames.length == 0) return;
        if (index < 0 || index >= currentAnimation.frames.length)
            throw new IllegalArgumentException("Frame index out of range: " + index);

        // Align stateTime to the requested frame so update() behavior remains consistent
        stateTime = index * currentAnimation.frameDuration;
        currentFrame = currentAnimation.frames[index];
    }

    // ---------------- Rendering ----------------

    public void render(SpriteBatch batch, float x, float y) {
        if (currentFrame != null) batch.draw(currentFrame, x, y);
    }

    public void render(SpriteBatch batch, float x, float y, float width, float height) {
        if (currentFrame != null) batch.draw(currentFrame, x, y, width, height);
    }

    // ---------------- Helpers / Introspection ----------------

    /** Returns the name of currently playing animation or null. */
    public String getCurrentAnimationName() {
        for (Map.Entry<String, Animation> e : animations.entrySet()) {
            if (e.getValue() == currentAnimation) return e.getKey();
        }
        return null;
    }

    /** Returns true if current non-looping animation has finished. */
    public boolean isAnimationFinished() {
        if (currentAnimation == null) return true;
        if (currentAnimation.looping) return false;
        int frameIndex = (int) (stateTime / currentAnimation.frameDuration);
        return frameIndex >= currentAnimation.frames.length;
    }

    /** Dispose textures (base + extras). */
    public void dispose() {
        if (baseTexture != null) baseTexture.dispose();
        for (Texture t : extraTextures) {
            if (t != null) t.dispose();
        }
        extraTextures.clear();
    }

    // ---------------- Inner class ----------------

    private static class Animation {
        TextureRegion[] frames;
        float frameDuration;
        boolean looping;
        boolean flippedX = false;

        Animation(TextureRegion[] frames, float frameDuration, boolean looping) {
            this.frames = frames != null ? frames : new TextureRegion[0];
            this.frameDuration = frameDuration;
            this.looping = looping;
        }

        void applyFlip(boolean flipX) {
            if (flipX == flippedX) return;
            for (TextureRegion frame : frames) {
                if (frame != null) frame.flip(true, false);
            }
            flippedX = flipX;
        }
    }
}

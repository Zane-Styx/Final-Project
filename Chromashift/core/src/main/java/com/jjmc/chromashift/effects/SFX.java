package com.jjmc.chromashift.effects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.chromashift.helper.SpriteAnimator;

/**
 * Special effects player that handles sprite animations with options for:
 * - Position and scale
 * - Auto-removal when done
 * - Looping/one-shot playback
 */
public class SFX {
    private final SpriteAnimator anim;
    private float x, y;
    private float width, height;
    private boolean autoRemove;
    private boolean isFinished;
    private boolean flipX = false;
    
    public SFX(String spritePath, int rows, int cols, float width, float height) {
        this.anim = new SpriteAnimator(spritePath, rows, cols);
        this.width = width;
        this.height = height;
        this.autoRemove = true; // default: remove when done
    }
    
    public SFX(String atlasPath, String animationName) {
        this.anim = new SpriteAnimator();
        this.width = 1f; // Default size, should be set by caller
        this.height = 1f;
        this.autoRemove = true;
        anim.addAnimationFromTexture(animationName, atlasPath, 32, 32, 1, 0.1f, false); // Default values
    }
    
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }
    
    public void setSize(float width, float height) {
        this.width = width;
        this.height = height;
    }
    
    public void setFlipX(boolean flip) {
        this.flipX = flip;
    }
    
    public void setAutoRemove(boolean auto) {
        this.autoRemove = auto;
    }
    
    /**
     * Add an animation sequence that can be played by name.
     */
    public void addAnimation(String name, int row, int startCol, int frames, float frameDuration, boolean loop) {
        anim.addAnimation(name, row, startCol, frames, frameDuration, loop);
    }
    
    /**
     * Start playing a named animation sequence.
     * @return false if animation name not found
     */
    public boolean play(String name) {
        anim.play(name, flipX);
        return true;
    }
    
    private int currentFrame = 0;
    
    public void update(float delta) {
        if (isFinished) return;
        
        anim.update(delta);
        currentFrame++; // Simple frame counting
        
        // Check if non-looping animation finished
        if (autoRemove && anim.isAnimationFinished()) {
            isFinished = true;
        }
    }
    
    public int getCurrentFrame() {
        return currentFrame;
    }
    
    public void render(SpriteBatch batch) {
        if (isFinished) return;
        anim.render(batch, x, y, width, height);
    }
    
    public boolean isFinished() { return isFinished; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    
    // Animation access for frame tracking
    public SpriteAnimator getAnim() { return anim; }
    public String getCurrentAnimationName() { return anim.getCurrentAnimationName(); }
    public boolean isAnimationFinished() { return anim.isAnimationFinished(); }
    
    public void dispose() {
        anim.dispose();
    }

    /**
     * Reset the SFX to allow reuse from a pool. Resets animation state and
     * clears the finished flag so the SFX can be played again.
     */
    public void reset() {
        this.isFinished = false;
        this.currentFrame = 0;
        try { anim.reset(); } catch (Exception ignored) {}
    }

    /**
     * Mark this SFX as finished so it can be considered available for reuse
     * by pooling logic. This does not dispose resources; it simply sets the
     * finished flag so `obtainPooledSFX` can pick it up.
     */
    public void markFinishedForReuse() {
        this.isFinished = true;
    }
}

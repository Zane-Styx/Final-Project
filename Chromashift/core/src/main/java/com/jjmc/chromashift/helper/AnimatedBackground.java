package com.jjmc.chromashift.helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.List;

/**
 * Animated background using sprite sheet frames (simulates GIF animation).
 * Load individual frames from a texture and cycle through them at a specified frame rate.
 */
public class AnimatedBackground {
    private List<TextureRegion> frames;
    private float frameTime; // Duration of each frame in seconds
    private float stateTime; // Accumulated time
    private int currentFrameIndex;
    private boolean loop;
    private float screenWidth;
    private float screenHeight;
    private float scale; // Scale factor for the background (1.0 = full screen)
    
    /**
     * Create an animated background
     * @param frameRate FPS for animation (e.g., 24 for 24fps)
     * @param screenWidth Target screen width
     * @param screenHeight Target screen height
     */
    public AnimatedBackground(float frameRate, float screenWidth, float screenHeight) {
        this.frames = new ArrayList<>();
        this.frameTime = 1.0f / frameRate;
        this.stateTime = 0f;
        this.currentFrameIndex = 0;
        this.loop = true;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.scale = 1.0f;
    }
    
    /**
     * Load frames from a single texture that contains multiple frames in a grid
     * @param texturePath Path to texture file
     * @param cols Number of columns in the sprite sheet
     * @param rows Number of rows in the sprite sheet
     */
    public void loadFromSpriteSheet(String texturePath, int cols, int rows) {
        try {
            Texture texture = new Texture(Gdx.files.internal(texturePath));
            int frameWidth = texture.getWidth() / cols;
            int frameHeight = texture.getHeight() / rows;
            
            // Load frames row by row, left to right
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    TextureRegion region = new TextureRegion(texture, 
                        col * frameWidth, row * frameHeight, frameWidth, frameHeight);
                    frames.add(region);
                }
            }
            Gdx.app.log("AnimatedBackground", "Loaded " + frames.size() + " frames from " + texturePath);
        } catch (Exception e) {
            Gdx.app.error("AnimatedBackground", "Failed to load sprite sheet: " + texturePath, e);
        }
    }
    
    /**
     * Load individual frame textures
     * @param framePaths Array of paths to frame textures
     */
    public void loadFrames(String... framePaths) {
        try {
            for (String path : framePaths) {
                Texture texture = new Texture(Gdx.files.internal(path));
                frames.add(new TextureRegion(texture));
            }
            Gdx.app.log("AnimatedBackground", "Loaded " + frames.size() + " frame textures");
        } catch (Exception e) {
            Gdx.app.error("AnimatedBackground", "Failed to load frame textures", e);
        }
    }
    
    /**
     * Update animation state
     */
    public void update(float delta) {
        if (frames.isEmpty()) return;
        
        stateTime += delta;
        
        // Calculate current frame index
        int frameCount = (int) (stateTime / frameTime);
        if (loop) {
            currentFrameIndex = frameCount % frames.size();
        } else {
            currentFrameIndex = Math.min(frameCount, frames.size() - 1);
        }
    }
    
    /**
     * Render the background in world space (respects camera zoom/position)
     */
    public void renderInWorldSpace(com.badlogic.gdx.graphics.g2d.SpriteBatch batch, 
                                    com.badlogic.gdx.graphics.OrthographicCamera camera) {
        if (frames.isEmpty()) {
            // No frames - render dark fallback background that fills screen
            com.badlogic.gdx.graphics.Color originalColor = batch.getColor();
            batch.setColor(0.05f, 0.02f, 0.1f, 1f); // Dark purple
            
            // Create white pixel texture for drawing rectangle
            com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(1, 1, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            pixmap.setColor(1, 1, 1, 1);
            pixmap.fill();
            com.badlogic.gdx.graphics.Texture whiteTexture = new com.badlogic.gdx.graphics.Texture(pixmap);
            pixmap.dispose();
            
            // Calculate visible area in world coordinates
            float width = camera.viewportWidth * camera.zoom;
            float height = camera.viewportHeight * camera.zoom;
            float x = camera.position.x - width / 2;
            float y = camera.position.y - height / 2;
            
            batch.draw(whiteTexture, x, y, width, height);
            whiteTexture.dispose();
            batch.setColor(originalColor);
            return;
        }
        
        TextureRegion currentFrame = frames.get(currentFrameIndex);
        if (currentFrame != null) {
            com.badlogic.gdx.graphics.Color originalColor = batch.getColor();
            batch.setColor(1, 1, 1, 1); // Reset to white
            
            // Calculate visible area in world coordinates
            float width = camera.viewportWidth * camera.zoom;
            float height = camera.viewportHeight * camera.zoom;
            float x = camera.position.x - width / 2;
            float y = camera.position.y - height / 2;
            
            batch.draw(currentFrame, x, y, width, height);
            batch.setColor(originalColor);
        }
    }
    
    /**
     * Render the background (screen space - legacy method)
     */
    public void render(SpriteBatch batch) {
        if (frames.isEmpty()) {
            // No frames loaded - fill with dark color to indicate missing texture
            // Batch must be active for drawing
            com.badlogic.gdx.graphics.Color originalColor = batch.getColor();
            batch.setColor(0.1f, 0.05f, 0.15f, 1f); // Dark purple
            
            // Draw a simple rectangle using a 1x1 white texture
            com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(1, 1, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            pixmap.setColor(1, 1, 1, 1);
            pixmap.fill();
            com.badlogic.gdx.graphics.Texture whiteTexture = new com.badlogic.gdx.graphics.Texture(pixmap);
            pixmap.dispose();
            
            batch.draw(whiteTexture, 0, 0, screenWidth, screenHeight);
            whiteTexture.dispose();
            batch.setColor(originalColor);
            return;
        }
        
        TextureRegion currentFrame = frames.get(currentFrameIndex);
        if (currentFrame != null) {
            com.badlogic.gdx.graphics.Color originalColor = batch.getColor();
            batch.setColor(1, 1, 1, 1); // Reset to white (no tint)
            
            float width = screenWidth * scale;
            float height = screenHeight * scale;
            float x = (screenWidth - width) / 2; // Center if scaled down
            float y = (screenHeight - height) / 2;
            
            batch.draw(currentFrame, x, y, width, height);
            batch.setColor(originalColor);
        }
    }
    
    /**
     * Get current frame index (for debugging)
     */
    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }
    
    /**
     * Get total frame count
     */
    public int getFrameCount() {
        return frames.size();
    }
    
    /**
     * Reset animation to start
     */
    public void reset() {
        stateTime = 0f;
        currentFrameIndex = 0;
    }
    
    /**
     * Set animation scale (1.0 = full screen)
     */
    public void setScale(float scale) {
        this.scale = Math.max(0.1f, scale);
    }
    
    /**
     * Set whether animation loops
     */
    public void setLoop(boolean loop) {
        this.loop = loop;
    }
    
    /**
     * Check if animation has finished (only relevant if not looping)
     */
    public boolean isFinished() {
        return !loop && currentFrameIndex >= frames.size() - 1;
    }
    
    /**
     * Dispose of all textures
     */
    public void dispose() {
        for (TextureRegion region : frames) {
            if (region.getTexture() != null) {
                region.getTexture().dispose();
            }
        }
        frames.clear();
    }
}

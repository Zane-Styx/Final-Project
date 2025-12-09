package com.jjmc.chromashift.screens.levels;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Visual overlay for level loading screen.
 * Displays loading progress, fade effects, and status messages.
 */
public class LoadingOverlay {
    
    private LevelLoadingManager loadingManager;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch batch;
    private BitmapFont font;
    private Viewport viewport;
    
    // Visual settings
    private float progressBarWidth = 400f;
    private float progressBarHeight = 20f;
    private Color backgroundColor = new Color(0, 0, 0, 1);
    private Color progressBarColor = new Color(0.3f, 0.7f, 1.0f, 1);
    private Color progressBarBgColor = new Color(0.2f, 0.2f, 0.2f, 1);
    private Color textColor = Color.WHITE;
    
    public LoadingOverlay(LevelLoadingManager loadingManager, SpriteBatch batch, 
                         ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport) {
        this.loadingManager = loadingManager;
        this.batch = batch;
        this.shapeRenderer = shapeRenderer;
        this.font = font;
        this.viewport = viewport;
    }
    
    /*
     * Render the loading overlay.
     * Draws fade overlay, progress bar, and status text.
     */
    public void render() {
        if (loadingManager.isReady()) {
            return; // Nothing to render when fully loaded
        }
        
        float fadeAlpha = loadingManager.getFadeAlpha();
        
        // Update viewport to current screen size
        viewport.update(com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight(), true);
        viewport.apply();
        
        float screenWidth = viewport.getWorldWidth();
        float screenHeight = viewport.getWorldHeight();
        
        // Draw black fade overlay
        if (fadeAlpha > 0f) {
            shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0, 0, 0, fadeAlpha);
            shapeRenderer.rect(0, 0, screenWidth, screenHeight);
            shapeRenderer.end();
        }
        
        // Only show loading UI during actual loading states (not during fade)
        if (loadingManager.getCurrentState() != LevelLoadingState.FADING_IN && 
            loadingManager.getCurrentState() != LevelLoadingState.READY) {
            
            float centerX = screenWidth / 2f;
            float centerY = screenHeight / 2f;
            
            // Draw progress bar
            renderProgressBar(centerX, centerY);
            
            // Draw loading objects list
            renderLoadingObjects(centerX, centerY);
            
            // Draw status text
            renderStatusText(centerX, centerY);
        }
    }
    
    /**
     * Render the progress bar.
     */
    private void renderProgressBar(float centerX, float centerY) {
        float progress = loadingManager.getProgress();
        
        float barX = centerX - progressBarWidth / 2f;
        float barY = centerY - progressBarHeight / 2f;
        
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        
        // Background
        shapeRenderer.setColor(progressBarBgColor);
        shapeRenderer.rect(barX, barY, progressBarWidth, progressBarHeight);
        
        // Progress fill
        shapeRenderer.setColor(progressBarColor);
        shapeRenderer.rect(barX, barY, progressBarWidth * progress, progressBarHeight);
        
        shapeRenderer.end();
        
        // Border
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(barX, barY, progressBarWidth, progressBarHeight);
        shapeRenderer.end();
    }
    
    /**
     * Render the list of objects currently being loaded.
     */
    private void renderLoadingObjects(float centerX, float centerY) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        
        com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
        
        // Display currently loading objects above the progress bar
        float yOffset = centerY + 100f;
        int maxObjectsToShow = 5; // Limit displayed objects to prevent overflow
        int objectCount = 0;
        
        // Show pending objects
        for (LoadableObject obj : loadingManager.getPendingObjects()) {
            if (objectCount >= maxObjectsToShow) {
                // Show "and X more..." if there are more objects
                int remaining = loadingManager.getPendingCount() - maxObjectsToShow;
                if (remaining > 0) {
                    String moreText = "... and " + remaining + " more";
                    layout.setText(font, moreText);
                    font.setColor(new Color(0.7f, 0.7f, 0.7f, 1f));
                    font.draw(batch, moreText, centerX - layout.width / 2f, yOffset);
                }
                break;
            }
            
            String objText = "> " + obj.getLoadingDescription();
            layout.setText(font, objText);
            font.setColor(new Color(1f, 0.8f, 0.3f, 1f)); // Yellow for pending
            font.draw(batch, objText, centerX - layout.width / 2f, yOffset);
            yOffset -= 25f;
            objectCount++;
        }
        
        batch.end();
    }
    
    /**
     * Render status text and progress percentage.
     */
    private void renderStatusText(float centerX, float centerY) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        
        // Status text
        String statusText = loadingManager.getStatusText();
        com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
        layout.setText(font, statusText);
        font.setColor(textColor);
        font.draw(batch, statusText, centerX - layout.width / 2f, centerY + 60f);
        
        // Progress percentage
        int percentage = (int) (loadingManager.getProgress() * 100);
        String percentText = percentage + "%";
        layout.setText(font, percentText);
        font.draw(batch, percentText, centerX - layout.width / 2f, centerY - 40f);
        
        // Object count
        String countText = loadingManager.getReadyCount() + " / " + loadingManager.getTotalCount() + " objects ready";
        layout.setText(font, countText);
        font.draw(batch, countText, centerX - layout.width / 2f, centerY - 60f);
        
        batch.end();
    }
    
    /**
     * Set progress bar dimensions.
     */
    public void setProgressBarSize(float width, float height) {
        this.progressBarWidth = width;
        this.progressBarHeight = height;
    }
    
    /**
     * Set progress bar color.
     */
    public void setProgressBarColor(Color color) {
        this.progressBarColor = new Color(color);
    }
    
    /**
     * Set text color.
     */
    public void setTextColor(Color color) {
        this.textColor = new Color(color);
    }
}

package com.jjmc.chromashift.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.chromashift.helper.CameraController;
import com.chromashift.helper.SoundManager;
import com.jjmc.chromashift.player.Player;
import com.jjmc.chromashift.player.PlayerConfig;
import com.jjmc.chromashift.player.PlayerType;
import com.badlogic.gdx.Input;

/**
 * Lightweight initializer for common per-level systems (camera, batch, font,
 * shape renderer and player). Levels can call this once to avoid duplicating
 * boilerplate in each level/screen.
 *
 * Usage example:
 * Initialize.Context ctx = Initialize.createCommon(800, 480, playerConfig);
 * // add environment objects to your level, then place player via ctx.createPlayer(...)
 */
public class Initialize {

    public static class Context {
        public OrthographicCamera camera;
        public CameraController camController;
        public SpriteBatch batch;
        public ShapeRenderer shape;
        public BitmapFont font;
        public Player player;

        /** Dispose resources created by the context (font, batch, shape). */
        public void dispose() {
            try { if (font != null) font.dispose(); } catch (Exception ignored) {}
            try { if (batch != null) batch.dispose(); } catch (Exception ignored) {}
            try { if (shape != null) shape.dispose(); } catch (Exception ignored) {}
        }

        /** Helper to create a Player with a PlayerConfig and spawn position. */
        public Player createPlayer(float spawnX, float spawnY, PlayerConfig cfg) {
            if (cfg == null) cfg = new PlayerConfig();
            // Mirror the sensible defaults used in TestSceneScreen
            player = new Player(
                120, spawnY,
                Input.Keys.A, Input.Keys.D,
                Input.Keys.W, Input.Keys.SPACE,
                PlayerType.PURPLE,
                6, 10,
                64, 32, 5,
                cfg
            );
            player.setCamera(camera);
            player.setX(spawnX);
            player.setY(spawnY);
            return player;
        }
    }

    /**
     * Create and return a preconfigured Context with camera, controller, batch,
     * shape renderer and font. Caller may further create player(s) using
     * Context.createPlayer.
     *
     * @param width viewport width
     * @param height viewport height
     * @param playerConfig optional PlayerConfig used by createPlayer; can be null
     */
    public static Context createCommon(float width, float height, PlayerConfig playerConfig) {
        Context c = new Context();
        c.camera = new OrthographicCamera();
        c.camera.setToOrtho(false, width, height);
        c.camController = new CameraController(c.camera);
        // Default follow settings - caller may override
        c.camController.setFollowMode(CameraController.FollowTargetMode.PLAYER);
        c.camController.setDeadZone(120f, 80f);
        c.camController.setSmoothSpeed(3f);
        c.camController.setLookAheadEnabled(false);
        c.camController.setDebugZoneVisible(false);

        c.batch = new SpriteBatch();
        c.shape = new ShapeRenderer();
        try {
            c.font = new BitmapFont(Gdx.files.internal("ui/default.fnt"));
            c.font.getData().setScale(0.7f);
        } catch (Exception e) {
            // Font load failure should not crash initialization; create fallback
            try { c.font = new BitmapFont(); } catch (Exception ignored) { c.font = null; }
        }

        // --- SOUND MANAGER INIT & SOUND LOADING ---
        try {
            SoundManager.init(false);
            SoundManager.addSound("Dash", "sounds/Dash.wav");
            SoundManager.addSound("Defeat1", "sounds/Defeat1.wav");
            SoundManager.addSound("Defeat2", "sounds/Defeat2.wav");
            SoundManager.addSound("Button", "sounds/Button.wav");
            SoundManager.addSound("Lever", "sounds/Lever.wav");
            SoundManager.addSound("Launchpad", "sounds/Launchpad.wav");
            SoundManager.addSound("UISelect", "sounds/UISelect.wav");
            SoundManager.addLoopingSfx("Walking", "sounds/Walking.wav");

            // Add ambient background music
            SoundManager.addMusic("ambience", "sounds/Ambience1.wav", false);
            SoundManager.addMusic("ambience", "sounds/Ambience2.wav", false);
            SoundManager.addMusic("ambience", "sounds/Ambience3.wav", false);
            SoundManager.addMusic("ambience", "sounds/Ambience4.wav", false);
            
            // Start playing ambient music with 2sec crossfade, random selection
            SoundManager.playPlaylist("ambience", 2f, true);
            SoundManager.init(false);
        } catch (Exception e) {
            System.err.println("[SoundManager] Failed to load one or more sounds: " + e.getMessage());
        }

        // Optionally create a player instance (not forced)
        if (playerConfig != null) {
            // spawn near ground by default; caller should call createPlayer explicitly if they want control
            c.createPlayer(120f, 40f, playerConfig);
        }

        return c;
    }
}

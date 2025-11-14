package com.jjmc.chromashift.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.Wall;
import com.jjmc.chromashift.environment.interactable.Button;
import com.jjmc.chromashift.environment.interactable.Interactable;
import com.jjmc.chromashift.environment.interactable.Box;
import com.jjmc.chromashift.environment.interactable.Orb;
import com.jjmc.chromashift.environment.Spawn;
import com.jjmc.chromashift.player.Player;
import com.jjmc.chromashift.player.PlayerConfig;
import com.jjmc.chromashift.entity.boss.BossInstance;
import com.chromashift.helper.CameraController;

/**
 * Test scene specifically designed to exercise Doors, Buttons and Levers.
 * - Left: Button (pressure) -> Door A
 * - Center: Lever (press F) -> Door B (toggle)
 * - Right: Independent door/obstacles for edge cases
 * Provides on-screen instructions and debug visuals.
 */
public class TestSceneScreen implements Screen {
    private OrthographicCamera camera;
    private CameraController camController;
    private SpriteBatch batch;
    private ShapeRenderer shape;
    private BitmapFont font;
    private Player player;
    private BossInstance boss;
    private Initialize.Context ctx;

    private Array<Wall> walls;
    private Array<Interactable> interactables;
    private Array<Solid> solids;

    // Player spawn for respawn key
    private float playerSpawnX;
    private float playerSpawnY;
    private Spawn spawnMarker;

    // Base area walls so render can reference their bounds
    private Wall baseLeft;
    private Wall baseCenter;
    private Wall baseRight;

    private float groundY = -64f;

    @Override
    public void show() {
        // Use Initialize helper to create common systems
        ctx = Initialize.createCommon(800, 480, null);
        camera = ctx.camera;
        camController = ctx.camController;
        batch = ctx.batch;
        shape = ctx.shape;
        font = ctx.font;

        // Load everything via the unified LevelLoader
        String levelPath = "levels/level1.json";
        com.jjmc.chromashift.screens.levels.LevelLoader.Result loaded =
                com.jjmc.chromashift.screens.levels.LevelLoader.load(levelPath);

        // Adopt loaded collections so updates/render iterate the same instances
        this.walls = loaded.walls;
        this.solids = loaded.solids;
        this.interactables = loaded.interactables;

        // Boss (only for BossRoom level)
        if (loaded.boss != null) {
            this.boss = loaded.boss;
        } else if (levelPath.contains("bossroom")) {
            this.boss = new BossInstance();
            // place default boss above the first wall if available
            Wall base = (walls.size > 0) ? walls.first() : new Wall(0, groundY, 10, 1);
            boss.setPosition(base.bounds.x + base.bounds.width / 2f, base.bounds.y + base.bounds.height + 200f);
            boss.setEnvironment(solids, walls);
        } else {
            this.boss = null;
        }

        // Player at spawn
        PlayerConfig cfg = new PlayerConfig();
        player = ctx.createPlayer(loaded.spawnX, loaded.spawnY, cfg);
        player.setRespawnPoint(player.getX(), player.getY());
        playerSpawnX = player.getX();
        playerSpawnY = player.getY();
        // Visible spawn marker (static frame by default)
        spawnMarker = new Spawn(playerSpawnX, playerSpawnY);
    }

    @Override
    public void render(float delta) {
        // Basic input: close app with ESC
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }

        // Respawn player to initial spawn with R
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            player.respawn();
            if (spawnMarker != null) {
                spawnMarker.setPosition(playerSpawnX, playerSpawnY);
                spawnMarker.playOnce();
            }
        }

        // First update non-button interactables
        Array<Rectangle> objectBounds = new Array<>();
        for (int i = 0; i < interactables.size; i++) {
            Interactable interactable = interactables.get(i);
            interactable.checkInteraction(player.getHitboxRect());
            if (interactable instanceof Box box) {
                objectBounds.add(box.getActivationBounds());
            } else if (interactable instanceof Orb orb) {
                objectBounds.add(orb.getActivationBounds());
            }
            // Check launchpad triggers
            if (interactable instanceof com.jjmc.chromashift.environment.Launchpad launchpad) {
                launchpad.checkAndLaunchPlayer(player, walls);
                // Check for boxes and orbs on launchpad (use indexed loop to avoid nested iterator)
                for (int j = 0; j < interactables.size; j++) {
                    Interactable obj = interactables.get(j);
                    if (obj instanceof Box box) {
                        launchpad.checkAndLaunchBox(box);
                    } else if (obj instanceof Orb orb) {
                        launchpad.checkAndLaunchOrb(orb);
                    }
                }
            }
            if (!(interactable instanceof Button)) {
                interactable.update(delta);
            }
        }
        
        // Then update buttons with collected bounds
        for (int i = 0; i < interactables.size; i++) {
            Interactable interactable = interactables.get(i);
            if (interactable instanceof Button b) {
                b.update(delta, player.getHitboxRect(), objectBounds);
            }
        }

        // Player update
        player.update(delta, groundY, solids, interactables, 1);

        // Boss update - set target to player position
        if (boss != null) {
            boss.setTarget(player.getX() + player.getHitboxWidth()/2, player.getY() + player.getHitboxHeight()/2);
            boss.update(delta);
        }

        // Camera update / follow
        Vector2 playerCenter = new Vector2(player.getX() + player.getHitboxWidth() / 2f,
                                           player.getY() + player.getHitboxHeight() / 2f);
        camController.setTarget(playerCenter);
        camController.update(delta);

        // Draw
        Gdx.gl.glClearColor(0.08f, 0.09f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shape.end();

        batch.setProjectionMatrix(camController.getCamera().combined);
        batch.begin();
        // Draw world: walls first, then interactables, spawn marker, boss, and player
        for (Wall w : walls) w.render(batch);
        for (Interactable i : interactables) i.render(batch);
        if (spawnMarker != null) {
            spawnMarker.update(delta);
            if (spawnMarker.isVisible()) {
                spawnMarker.render(batch);
            }
        }
        if (boss != null) {
            boss.render(batch);
        }
        player.render(batch);

        // Draw debug UI with clean layout
        float baseX = camController.getCamera().position.x - 480 + 8;  // Left align
        float baseY = camController.getCamera().position.y + 260;      // Top of screen
        float lineHeight = 20f;  // Space between lines
        
        // Title
        font.setColor(Color.GOLD);
        font.draw(batch, "Debug Info (F3 for hitboxes)", baseX, baseY);
        
        // Movement controls
        font.setColor(Color.WHITE);
        baseY -= lineHeight;
        font.draw(batch, "Movement:", baseX, baseY);
        font.setColor(Color.LIGHT_GRAY);
        baseY -= lineHeight;
        font.draw(batch, "• A/D - Move Left/Right", baseX + 10, baseY);
        baseY -= lineHeight;
        font.draw(batch, "• W - Jump", baseX + 10, baseY);
        baseY -= lineHeight;
        font.draw(batch, "• Space - Attack", baseX + 10, baseY);
        baseY -= lineHeight;
        font.draw(batch, "• SHIFT - Dash (once until landing)", baseX + 10, baseY);
        
        // Combat/Health
        baseY -= lineHeight * 1.5f;
        font.setColor(Color.WHITE);
        font.draw(batch, "Combat:", baseX, baseY);
        font.setColor(Color.LIGHT_GRAY);
        baseY -= lineHeight;
        String healthText = String.format("• Player Health: %.0f/%.0f", player.getHealthSystem().getCurrentHealth(), player.getHealthSystem().getMaxHealth());
        font.draw(batch, healthText, baseX + 10, baseY);
        baseY -= lineHeight;
        String bossHealthText = boss != null ? String.format("• Boss Health: %.0f/%.0f", boss.getHealthSystem().getCurrentHealth(), boss.getHealthSystem().getMaxHealth()) : "• Boss: None";
        font.draw(batch, bossHealthText, baseX + 10, baseY);
        baseY -= lineHeight;
        font.draw(batch, "• O - Test damage (100)", baseX + 10, baseY);
        
        // Interaction controls
        baseY -= lineHeight * 1.5f;
        font.setColor(Color.WHITE);
        font.draw(batch, "Interaction:", baseX, baseY);
        font.setColor(Color.LIGHT_GRAY);
        baseY -= lineHeight;
        font.draw(batch, "• G - Pick up/Throw objects (aim with mouse)", baseX + 10, baseY);
        baseY -= lineHeight;
        font.draw(batch, "• F - Interact", baseX + 10, baseY);
        
        // System controls
        baseY -= lineHeight * 1.5f;
        font.setColor(Color.WHITE);
        font.draw(batch, "System:", baseX, baseY);
        font.setColor(Color.LIGHT_GRAY);
        baseY -= lineHeight;
        font.draw(batch, "• R - Respawn", baseX + 10, baseY);
        baseY -= lineHeight;
        font.draw(batch, "• F3 - Show Debug Lines", baseX + 10, baseY);
        baseY -= lineHeight;
        font.draw(batch, "• ESC - Exit game", baseX + 10, baseY);
        
        // Status effects (if any active)
        if (player.getHealthSystem().isInvulnerable()) {
            font.setColor(Color.YELLOW);
            font.draw(batch, "INVULNERABLE", 
                     camController.getCamera().position.x - 40, // Center on screen
                     camController.getCamera().position.y + 40);
        }
        batch.end();

        // Debug visuals
        shape.setProjectionMatrix(camController.getCamera().combined);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(Color.FOREST);
        shape.rect(-2000, groundY - 5, 4000, 5);
        shape.end();

        if(Gdx.input.isKeyPressed(Input.Keys.F3)) {
            shape.begin(ShapeRenderer.ShapeType.Line);
            for (Wall w : walls) w.debugDraw(shape);
            for (Interactable i : interactables) i.debugDraw(shape);
            player.debugDrawHitbox(shape);
            shape.end();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.O)) {
            player.getHealthSystem().damage(100f, null);        
        }

        if (boss != null && Gdx.input.isKeyJustPressed(Input.Keys.K)) {
            boss.getHealthSystem().damage(100f, null);        
        }
        if (boss != null && Gdx.input.isKeyJustPressed(Input.Keys.L)) {
            boss.getHealthSystem().heal(100f);        
        }
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        camera.setToOrtho(false, width, height);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        if (ctx != null) ctx.dispose();
        player.dispose();
        if (boss != null) boss.disposeParts();
        // dispose button sprites
        for (Interactable i : interactables) if (i instanceof Button b) b.dispose();
        // dispose wall/shared textures
        com.jjmc.chromashift.environment.Wall.dispose();
    }
}

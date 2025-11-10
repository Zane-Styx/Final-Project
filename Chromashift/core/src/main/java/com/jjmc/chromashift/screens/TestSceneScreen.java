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
import com.jjmc.chromashift.environment.interactable.Door;
import com.jjmc.chromashift.environment.interactable.Interactable;
import com.jjmc.chromashift.environment.interactable.Lever;
import com.jjmc.chromashift.environment.interactable.Box;
import com.jjmc.chromashift.environment.interactable.Orb;
import com.jjmc.chromashift.player.Player;
import com.jjmc.chromashift.player.PlayerConfig;
import com.jjmc.chromashift.player.PlayerType;
import com.jjmc.chromashift.entity.boss.BossInstance;
import com.chromashift.helper.CameraController;
import com.jjmc.chromashift.screens.Initialize;
import com.jjmc.chromashift.screens.LevelIO;
import java.util.HashMap;
import java.util.Map;

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

    // Base area walls so render can reference their bounds
    private Wall baseLeft;
    private Wall baseCenter;
    private Wall baseRight;

    private float groundY = 40f;

    @Override
    public void show() {
        // Use Initialize helper to create common systems
        ctx = Initialize.createCommon(800, 480, null);
        camera = ctx.camera;
        camController = ctx.camController;
        batch = ctx.batch;
        shape = ctx.shape;
        font = ctx.font;

        // init containers
        walls = new Array<>();
        interactables = new Array<>();
        solids = new Array<>();

        // Load level JSON (fall back to level1 by default)
        LevelIO.LevelState state = LevelIO.load("levels/level1.json");

        // create walls from JSON
        for (LevelIO.LevelState.WallData wd : state.walls) {
                Wall w = new Wall(wd.x, wd.y, (int)wd.width, (int)wd.height);
            walls.add(w);
            solids.add(w);
        }

        // If no walls loaded, create default bases (backwards compatible)
        if (walls.size == 0) {
            baseLeft = new Wall(-360, groundY, 13, 1);
            baseCenter = new Wall(0, groundY, 25, 1);
            baseRight = new Wall(720, groundY, 13, 1);
            walls.add(baseLeft); walls.add(baseCenter); walls.add(baseRight);
            solids.add(baseLeft); solids.add(baseCenter); solids.add(baseRight);
        } else {
            // derive baseLeft/center/right heuristically from first three walls
            if (walls.size >= 3) {
                baseLeft = walls.get(0);
                baseCenter = walls.get(1);
                baseRight = walls.get(2);
            } else {
                baseLeft = walls.get(0);
                baseCenter = walls.get(0);
                baseRight = walls.get(0);
            }
        }

        // Instantiate interactables and keep a map of ids for linking (doors/buttons)
        Map<String, Door> doorMap = new HashMap<>();
        for (LevelIO.LevelState.InteractableData idd : state.interactables) {
            if (idd.type == null) continue;
            String t = idd.type.toLowerCase();
            if (t.equals("door")) {
                // choose anchor wall by proximity to given x
                Wall anchor = baseCenter;
                for (Wall w : walls) {
                    if (Math.abs(w.bounds.x - idd.x) < 1e-2) { anchor = w; break; }
                }
                Door.OpenDirection dir = Door.OpenDirection.UP;
                try { dir = Door.OpenDirection.valueOf(idd.openDirection); } catch (Exception ignored) {}
                Door d = new Door(anchor, idd.cols, 0, idd.cols, idd.rows, dir, 2f, 0.5f);
                interactables.add(d);
                solids.add(d);
                if (idd.id != null) doorMap.put(idd.id, d);
            }
        }

        // Buttons/Levers that may reference doors
        for (LevelIO.LevelState.InteractableData idd : state.interactables) {
            if (idd.type == null) continue;
            String t = idd.type.toLowerCase();
            if (t.equals("button")) {
                Wall anchor = baseLeft;
                for (Wall w : walls) { if (Math.abs(w.bounds.x - idd.x) < 1e-2) { anchor = w; break; } }
                Door target = idd.targetId != null ? doorMap.get(idd.targetId) : null;
                Button.ButtonColor col = Button.ButtonColor.GREEN;
                try { col = Button.ButtonColor.valueOf(idd.color); } catch (Exception ignored) {}
                Button b = new Button(anchor.bounds.x , anchor, target, col);
                interactables.add(b);
                solids.add(b);
            } else if (t.equals("lever")) {
                Wall anchor = baseCenter;
                for (Wall w : walls) { if (Math.abs(w.bounds.x - idd.x) < 1e-2) { anchor = w; break; } }
                Door target = idd.targetId != null ? doorMap.get(idd.targetId) : null;
                Lever l = new Lever(anchor.bounds.x + 200f, anchor.bounds.y + anchor.bounds.height, 16, 36, target);
                if (target != null) {
                    l.setOnToggle(() -> { target.setOpen(!target.isOpen()); target.interact(); });
                }
                interactables.add(l);
            }
        }

        // Boxes
        for (LevelIO.LevelState.BoxData bd : state.boxes) {
            Box box = new Box(bd.x, bd.y, solids);
            interactables.add(box);
        }

        // Orbs
        for (LevelIO.LevelState.OrbData od : state.orbs) {
            Orb orb = new Orb(od.x, od.y, solids);
            orb.setBounciness(2f);
            interactables.add(orb);
        }

        // Create boss if defined
        if (state.boss != null) {
            boss = new BossInstance();
            boss.setPosition(state.boss.x, state.boss.y);
            boss.setEnvironment(solids, walls);
        } else {
            boss = new BossInstance();
            boss.setPosition(baseCenter.bounds.x + baseCenter.bounds.width/2, baseCenter.bounds.y + baseCenter.bounds.height + 200);
            boss.setEnvironment(solids, walls);
        }

        // Create player at spawn
        PlayerConfig cfg = new PlayerConfig();
        player = ctx.createPlayer(state.spawn.x, state.spawn.y, cfg);
        player.setRespawnPoint(player.getX(), player.getY());
        playerSpawnX = player.getX();
        playerSpawnY = player.getY();
    }

    @Override
    public void render(float delta) {
        // Basic input: close app with ESC
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }

        // Respawn player to initial spawn with R
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            player.setX(playerSpawnX);
            player.setY(playerSpawnY);
        }

        // First update non-button interactables
        Array<Rectangle> objectBounds = new Array<>();
        for (Interactable i : interactables) {
            i.checkInteraction(player.getHitboxRect());
            if (i instanceof Box box) {
                objectBounds.add(box.getActivationBounds());
            } else if (i instanceof Orb orb) {
                objectBounds.add(orb.getActivationBounds());
            }
            if (!(i instanceof Button)) {
                i.update(delta);
            }
        }
        
        // Then update buttons with collected bounds
        for (Interactable i : interactables) {
            if (i instanceof Button b) {
                b.update(delta, player.getHitboxRect(), objectBounds);
            }
        }

        // Player update
        player.update(delta, groundY, solids, interactables, 1);

        // Boss update - set target to player position
        boss.setTarget(player.getX() + player.getHitboxWidth()/2, player.getY() + player.getHitboxHeight()/2);
        boss.update(delta);

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
        // Draw world: walls first, then interactables, boss, and player
        for (Wall w : walls) w.render(batch);
        for (Interactable i : interactables) i.render(batch);
        boss.render(batch);
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
        String bossHealthText = String.format("• Boss Health: %.0f/%.0f", boss.getHealthSystem().getCurrentHealth(), boss.getHealthSystem().getMaxHealth());
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

        if (Gdx.input.isKeyJustPressed(Input.Keys.K)) {
            boss.getHealthSystem().damage(100f, null);        
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.L)) {
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
        boss.disposeParts();
        // dispose button sprites
        for (Interactable i : interactables) if (i instanceof Button b) b.dispose();
        // dispose wall/shared textures
        com.jjmc.chromashift.environment.Wall.dispose();
    }
}

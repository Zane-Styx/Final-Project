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
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 800, 480);
        camController = new CameraController(camera);
        camController.setFollowMode(CameraController.FollowTargetMode.PLAYER);
        camController.setDeadZone(120f, 80f);
        camController.setSmoothSpeed(3f);
        camController.setLookAheadEnabled(false);
        camController.setDebugZoneVisible(false);

        batch = new SpriteBatch();
        shape = new ShapeRenderer();
        font = new BitmapFont(Gdx.files.internal("ui/default.fnt"));
        font.getData().setScale(0.7f);

        PlayerConfig cfg = new PlayerConfig();
        player = new Player(
            120, groundY,
            Input.Keys.A, Input.Keys.D,
            Input.Keys.W, Input.Keys.SPACE,
            PlayerType.PURPLE,
            6, 10,
            64, 32, 5,
            cfg
        );

        // --- initialize containers ---
        walls = new Array<>();
        interactables = new Array<>();
        solids = new Array<>();

        // Create three separated base areas (left, center, right)
        baseLeft = new Wall(-360, groundY, 13, 1);   // left area
        baseCenter = new Wall(0, groundY, 25, 1);    // center area
        baseRight = new Wall(720, groundY, 13, 1);   // right area (far)
        walls.add(baseLeft);
        walls.add(baseCenter);
        walls.add(baseRight);

        // Register walls into solids
        for (Wall w : walls) solids.add(w);

        // --- Player spawn ---
        playerSpawnX = baseLeft.bounds.x + 120f; // start at left test area
        playerSpawnY = baseLeft.bounds.y + baseLeft.bounds.height;

        // Ensure player is placed at the spawn
        player.setX(playerSpawnX);
        player.setY(playerSpawnY);

        // Door A (controlled by Button) - left area (anchor at column 5, placed on top)
        Door doorA = new Door(baseLeft, 5, 0, 1, 4, Door.OpenDirection.UP, 3f, 3f);
        Button buttonA = new Button(baseLeft.bounds.x , baseLeft, doorA, Button.ButtonColor.GREEN);
        interactables.add(buttonA);
        interactables.add(doorA);
        solids.add(doorA);
        solids.add(buttonA);    

        // Door B (controlled by Lever) - center area (anchor at col 10 on top), opens to the RIGHT
        Door doorB = new Door(baseCenter, 10, 0, 5, 1, Door.OpenDirection.RIGHT, 2f, .5f);
        Lever leverB = new Lever(baseCenter.bounds.x + 200f, baseCenter.bounds.y + baseCenter.bounds.height, 16, 36, doorB);
        // register toggle so lever's visual flips door as well
        leverB.setOnToggle(() -> {
            doorB.setOpen(!doorB.isOpen());
            doorB.interact();
        });
        interactables.add(leverB);
        interactables.add(doorB);
        solids.add(doorB);

        // Button C + tall platform - right area (anchor at col 3 on top)
        Door doorC = new Door(baseRight, 3, 0, 1, 4, Door.OpenDirection.DOWN, 2.5f, 1.5f);
        Button buttonC = new Button(baseRight.bounds.x + 0f, baseRight, doorC, Button.ButtonColor.RED);
        interactables.add(buttonC);
        interactables.add(doorC);
        solids.add(doorC);
        solids.add(buttonC);

        // Add a movable box and orb (player can interact with them). They collide with solids but do not block the player.
        Box box = new Box(baseCenter.bounds.x + 80f, baseCenter.bounds.y + baseCenter.bounds.height, solids);
        Orb orb = new Orb(baseCenter.bounds.x + 140f, baseCenter.bounds.y + baseCenter.bounds.height, solids);
        orb.setBounciness(2f);
        interactables.add(box);
        interactables.add(orb);

        player.setCamera(camera);
        box.setCamera(camera);
        orb.setCamera(camera);
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
        // Draw world: walls first, then interactables and player
        for (Wall w : walls) w.render(batch);
        for (Interactable i : interactables) i.render(batch);
        player.render(batch);

        // UI instructions
        font.setColor(Color.WHITE);
        font.draw(batch, "Test Scene - Buttons and Levers", camController.getCamera().position.x - 480 + 8, camController.getCamera().position.y + 260);
        font.draw(batch, "Move: A/D   Jump: W   Dash: SHIFT   Interact: F   Pick/Throw: G", camController.getCamera().position.x - 480 + 8, camController.getCamera().position.y + 240);
        font.draw(batch, "Left: Pressure Button -> Green Door (walk onto button)", camController.getCamera().position.x - 480 + 8, camController.getCamera().position.y + 220);
        font.draw(batch, "Center: Lever -> Blue Door (press F when near)", camController.getCamera().position.x - 480 + 8, camController.getCamera().position.y + 200);
        font.draw(batch, "Right: Red Button -> Red Door (pressure)", camController.getCamera().position.x - 480 + 8, camController.getCamera().position.y + 180);
        font.setColor(Color.WHITE);
        font.draw(batch, "Press R to respawn", camController.getCamera().position.x + 280, camController.getCamera().position.y - 220);
        batch.end();

        // Debug visuals
        shape.setProjectionMatrix(camController.getCamera().combined);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(Color.FOREST);
        shape.rect(-2000, groundY - 5, 4000, 5);
        shape.end();

        shape.begin(ShapeRenderer.ShapeType.Line);
        // draw walls and interactables debug shapes (rect/circle outlines)
        for (Wall w : walls) w.debugDraw(shape);
        for (Interactable i : interactables) i.debugDraw(shape);
        // player.debugDrawHitbox(shape);
        shape.end();
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
        batch.dispose();
        shape.dispose();
        font.dispose();
        player.dispose();
        // dispose button sprites
        for (Interactable i : interactables) if (i instanceof Button b) b.dispose();
        // dispose wall/shared textures
        com.jjmc.chromashift.environment.Wall.dispose();
    }
}

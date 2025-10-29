package com.jjmc.chromashift.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.Wall;
import com.jjmc.chromashift.environment.Slope;
import com.jjmc.chromashift.environment.interactable.Button;
import com.jjmc.chromashift.environment.interactable.Door;
import com.jjmc.chromashift.environment.interactable.Interactable;
import com.jjmc.chromashift.player.Player;
import com.jjmc.chromashift.player.PlayerType;
import com.jjmc.chromashift.player.PlayerConfig;
import com.chromashift.helper.CameraController;

public class FirstScreen implements Screen {
    private OrthographicCamera camera;
    private CameraController camController;
    private SpriteBatch batch;
    private ShapeRenderer shape;
    private Player player;

    private Array<Wall> walls;
    private Array<Slope> slopes;
    private Array<Interactable> interactables;
    private Array<Solid> solids;

    private float groundY = 50f;

    @Override
    public void show() {
        // --- CAMERA ---
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 800, 480);
        camController = new CameraController(camera);
        camController.setFollowMode(CameraController.FollowTargetMode.PLAYER);
        camController.setDeadZone(120f, 80f);
        camController.setSmoothSpeed(5f);
        camController.setLookAheadEnabled(false);
        camController.setDebugZoneVisible(false);

        // --- RENDERING ---
        batch = new SpriteBatch();
        shape = new ShapeRenderer();

        // --- PLAYER ---
        PlayerConfig cfg = new PlayerConfig();

        player = new Player(
            100, groundY,
            Input.Keys.A, Input.Keys.D,
            Input.Keys.W, Input.Keys.SPACE,
            PlayerType.PURPLE,
            6, 10,
            64, 32, 5,
            cfg
        );

        // --- WALLS ---
        walls = new Array<>();
        walls.add(new Wall(100, groundY, 20, 200));
        walls.add(new Wall(400, groundY, 40, 250));
        walls.add(new Wall(700, groundY, 20, 200));
        walls.add(new Wall(-200, groundY, 200, 60));
        walls.add(new Wall(900, groundY + 100, 300, 40));

        // --- SLOPES ---
        slopes = new Array<>();
        Slope slope = new Slope(600, groundY, 100, 50, true);
        slopes.add(slope);

        // --- INTERACTABLES ---
        interactables = new Array<>();
        Door door = new Door(500, groundY, 40, 120);
        Button button = new Button(470, groundY, 30, 10, door);
        interactables.addAll(button, door);

        // --- SOLIDS ---
        solids = new Array<>(); // ✅ fixed: use field, not local variable

        // Add walls
        for (Wall wall : walls) {
            solids.add(wall);
        }

        // Add other solids
        solids.add(door);
        solids.addAll(slopes); // ✅ simpler than manual loop
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // --- UPDATE INTERACTABLES FIRST ---
        for (Interactable i : interactables) {
            if (i instanceof Button button) {
                button.update(delta, player.getHitboxRect());
            } else {
                i.update(delta);
            }
        }

        // --- UPDATE PLAYER ---
        player.update(delta, groundY, solids, interactables);

        // --- UPDATE CAMERA ---
        Vector2 playerCenter = new Vector2(
            player.getX() + player.getHitboxWidth() / 2f,
            player.getY() + player.getHitboxHeight() / 2f
        );
        camController.setTarget(playerCenter);
        camController.update(delta);

        // --- CAMERA EFFECTS ---
        if (player.isAttackingJustNow()) camController.shake(1f, 0.15f);
        if (player.isDashing()) camController.setTargetZoom(0.5f, 3f);
        else camController.setTargetZoom(1f, 2f);

        // --- DRAW SCENE ---
        batch.setProjectionMatrix(camController.getCamera().combined);
        batch.begin();
        player.render(batch);
        batch.end();

        // --- DEBUG VISUALS ---
        shape.setProjectionMatrix(camController.getCamera().combined);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(Color.FOREST);
        shape.rect(-2000, groundY - 5, 4000, 5);
        shape.end();

        shape.begin(ShapeRenderer.ShapeType.Line);
        player.debugDrawHitbox(shape);
        for (Wall wall : walls) wall.debugDraw(shape);
        for (Slope slope : slopes) slope.debugDraw(shape);
        for (Interactable i : interactables) i.debugDraw(shape);
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
        player.dispose();
        camController.dispose();
    }
}

package com.jjmc.chromashift;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.jjmc.chromashift.player.Player;
import com.jjmc.chromashift.player.PlayerType;

import java.security.Key;

public class FirstScreen implements Screen {

    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;

    private static final float groundY = 100f;

    // Players
    private Player player1;
    private int colorIndex = 0; // tracks current color

    @Override
    public void show() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();

        // Create two players with different colors and starting positions
        player1 = new Player(
            100, groundY,                  // start position
            com.badlogic.gdx.Input.Keys.A,  // left key
            com.badlogic.gdx.Input.Keys.D,  // right key
            com.badlogic.gdx.Input.Keys.W,  // jump key
            com.badlogic.gdx.Input.Keys.SPACE, // attack key
            PlayerType.RED,                 // color/type
            6, 10,                          // rows, cols of base sprite sheet
            64, 32, 5,                      // attack frame width, height, frame count
            100f, 250f, -600f               // speed, jumpForce, gravity
        );

        // Optional: configure different movement per player
        player1.setSpeed(120f);
        player1.setJumpForce(280f);
    }

    @Override
    public void render(float delta) {
        // Clear screen
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // ===== UPDATE =====
        handleColorInput();
        player1.update(delta, groundY);

        // ===== DRAW =====
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.FOREST);
        shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), groundY);
        shapeRenderer.end();

        batch.begin();
        player1.render(batch);
        batch.end();
    }

    private void handleColorInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.E) && !player1.isAttacking()) {
            colorIndex = (colorIndex + 1) % PlayerType.values().length;
            PlayerType newType = PlayerType.values()[colorIndex];
            player1.setPlayerColor(newType);
            reloadPlayerAnimations(newType);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q) && !player1.isAttacking()) {
            colorIndex--;
            if (colorIndex < 0) colorIndex = PlayerType.values().length - 1;
            PlayerType newType = PlayerType.values()[colorIndex];
            player1.setPlayerColor(newType);
            reloadPlayerAnimations(newType);
        }
    }

    private void reloadPlayerAnimations(PlayerType type) {
        // reload the base sprite sheet
        player1.getAnimator().dispose(); // dispose old
        // recreate animator using type's paths and same sizes as constructor
        player1.reloadAnimator(type);
    }

    @Override public void resize(int width, int height) {}
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        player1.dispose();
    }
}

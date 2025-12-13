package com.jjmc.chromashift.screens.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.jjmc.chromashift.Assets;

/**
 * Shared animated scene for UI screens.
 */
public abstract class AbstractMenuScreen implements Screen {
    protected SpriteBatch batch;
    protected ScreenViewport viewport;

    protected Texture bgTex;
    protected Texture starsTex;
    protected Texture asteroid1Tex;
    protected Texture asteroid2Tex;
    protected Texture planetTex;

    private float starsAlpha = 0f;
    private float starsFadeDir = 1f;
    private float starsFadeSpeed = 0.5f;

    protected static class Asteroid {
        float x, y, vx, vy, scale;
        Texture tex;
    }
    protected Array<Asteroid> asteroids = new Array<>();

    // Planets (multiple with tint variations)
    protected static class Planet {
        float cx, cy;       // center
        float radius;
        float speed;        // cycles per second
        float time;         // local time
        float jitterAmp;    // subtle sine jitter
        float scale;        // render scale
        com.badlogic.gdx.graphics.Color tint;
    }
    protected Array<Planet> planets = new Array<>();

    @Override
    public void show() {
        viewport = new ScreenViewport();
        batch = new SpriteBatch();

        // Load shared visuals independently so LoadingScreen can play animations
        bgTex = loadSharedTexture("ui/bg/layered/blue-back.png");
        starsTex = loadSharedTexture("ui/bg/layered/blue-stars.png");
        asteroid1Tex = loadSharedTexture("ui/bg/layered/asteroid-1.png");
        asteroid2Tex = loadSharedTexture("ui/bg/layered/asteroid-2.png");
        planetTex = loadSharedTexture("ui/bg/layered/prop-planet-big.png");

        bgTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        starsTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        asteroid1Tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        asteroid2Tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        planetTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        // Spawn a few asteroids with varied seeds for diversity
        spawnAsteroid(asteroid1Tex);
        spawnAsteroid(asteroid2Tex);
        spawnAsteroid(asteroid1Tex);
        spawnAsteroid(asteroid2Tex);
        spawnAsteroid(asteroid1Tex);

        // Spawn a random set of planets per run for variety
        int planetCount = MathUtils.random(2, 4);
        for (int i = 0; i < planetCount; i++) {
            float cx = MathUtils.random(0f, Gdx.graphics.getWidth());
            float cy = MathUtils.random(0f, Gdx.graphics.getHeight());
            float radius = MathUtils.random(28f, 65f);
            float speed = MathUtils.random(0.18f, 0.38f);
            float jitter = MathUtils.random(3f, 7f);
            float scale = MathUtils.random(0.9f, 1.4f);
            // Soft pastel tint variations
            float r = MathUtils.random(0.8f, 1.0f);
            float g = MathUtils.random(0.7f, 1.0f);
            float b = MathUtils.random(0.7f, 1.0f);
            com.badlogic.gdx.graphics.Color tint = new com.badlogic.gdx.graphics.Color(r, g, b, 1f);
            spawnPlanet(cx, cy, radius, speed, jitter, scale, tint);
        }
    }    private Texture loadSharedTexture(String path) {
        try {
            if (Assets.manager.isLoaded(path, Texture.class)) {
                return Assets.manager.get(path, Texture.class);
            }
        } catch (Exception ignored) {}
        // Fallback: load directly so loading visuals work before AssetManager completes
        Texture t = new Texture(Gdx.files.internal(path));
        return t;
    }

    private void spawnAsteroid(Texture tex) {
        Asteroid a = new Asteroid();
        a.tex = tex;
        a.x = -tex.getWidth() - MathUtils.random(0, 300);
        a.y = MathUtils.random(Gdx.graphics.getHeight() * 0.2f, Gdx.graphics.getHeight());
        float base = tex == asteroid1Tex ? 120f : 80f;
        a.vx = base + MathUtils.random(0f, 60f);
        a.vy = -(base * 0.5f) + MathUtils.random(-20f, 20f);
        // Make asteroids bigger overall
        a.scale = 0.9f + MathUtils.random(0f, 0.6f);
        asteroids.add(a);
    }

    protected void clearAndDrawScene(float delta) {
        updateScene(delta);
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.draw(bgTex, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.setColor(1f, 1f, 1f, starsAlpha);
        batch.draw(starsTex, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.setColor(1f, 1f, 1f, 1f);
        for (Asteroid a : asteroids) {
            float w = a.tex.getWidth() * a.scale;
            float h = a.tex.getHeight() * a.scale;
            batch.draw(a.tex, a.x, a.y, w, h);
        }
        // Draw multiple planets with tint
        for (int i = 0; i < planets.size; i++) {
            Planet p = planets.get(i);
            float pw = planetTex.getWidth() * p.scale;
            float ph = planetTex.getHeight() * p.scale;
            float angle = p.time * MathUtils.PI2 * p.speed;
            float jitter = MathUtils.sin(p.time * 2f) * p.jitterAmp;
            float px = p.cx + MathUtils.cos(angle) * p.radius + jitter;
            float py = p.cy + MathUtils.sin(angle) * (p.radius * 0.6f);
            batch.setColor(p.tint);
            batch.draw(planetTex, px, py, pw, ph);
            batch.setColor(1f, 1f, 1f, 1f);
        }
        batch.end();
    }

    private void updateScene(float delta) {
        starsAlpha += starsFadeDir * starsFadeSpeed * delta;
        if (starsAlpha >= 1f) { starsAlpha = 1f; starsFadeDir = -1f; }
        else if (starsAlpha <= 0f) { starsAlpha = 0f; starsFadeDir = 1f; }

        for (Asteroid a : asteroids) {
            a.x += a.vx * delta;
            a.y += a.vy * delta;
            if (a.x > Gdx.graphics.getWidth() + a.tex.getWidth() ||
                a.y < -a.tex.getHeight() ||
                a.y > Gdx.graphics.getHeight() + a.tex.getHeight()) {
                a.x = -a.tex.getWidth() - MathUtils.random(0, 300);
                a.y = MathUtils.random(Gdx.graphics.getHeight() * 0.2f, Gdx.graphics.getHeight());
                float base = a.tex == asteroid1Tex ? 120f : 80f;
                a.vx = base + MathUtils.random(0f, 60f);
                a.vy = -(base * 0.5f) + MathUtils.random(-20f, 20f);
                a.scale = 0.6f + MathUtils.random(0f, 0.4f);
            }
        }

        // Advance planet times
        for (int i = 0; i < planets.size; i++) {
            planets.get(i).time += delta;
        }
    }

    private void spawnPlanet(float cx, float cy, float radius, float speed, float jitterAmp,
                              float scale, com.badlogic.gdx.graphics.Color tint) {
        Planet p = new Planet();
        p.cx = cx;
        p.cy = cy;
        p.radius = radius;
        p.speed = speed;
        p.jitterAmp = jitterAmp;
        p.scale = scale;
        p.time = MathUtils.random(0f, 100f); // desynchronize phases
        p.tint = tint;
        planets.add(p);
    }

    @Override
    public void render(float delta) { clearAndDrawScene(delta); }

    @Override
    public void resize(int width, int height) { viewport.update(width, height, true); }

    @Override
    public void pause() {}
    @Override
    public void resume() {}
    @Override
    public void hide() {}

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        // Dispose textures only if we created them directly (safe to dispose regardless)
        try { if (bgTex != null) bgTex.dispose(); } catch (Exception ignored) {}
        try { if (starsTex != null) starsTex.dispose(); } catch (Exception ignored) {}
        try { if (asteroid1Tex != null) asteroid1Tex.dispose(); } catch (Exception ignored) {}
        try { if (asteroid2Tex != null) asteroid2Tex.dispose(); } catch (Exception ignored) {}
        try { if (planetTex != null) planetTex.dispose(); } catch (Exception ignored) {}
    }
}
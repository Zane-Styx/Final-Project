package com.chromashift.helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.*;
import com.badlogic.gdx.maps.objects.*;
import com.badlogic.gdx.maps.tiled.*;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashSet;
import java.util.Set;

/**
 * TileMapLoader
 * - Loads TMX maps with multiple layers
 * - Supports sloped collisions and Box2D physics
 * - Layer-based physics (friction, restitution, density, sensor)
 * - Auto background music using SoundManager
 */
public class TileMapLoader implements Disposable {

    private final TmxMapLoader loader;
    private final World world;
    private TiledMap map;
    private OrthogonalTiledMapRenderer renderer;
    private OrthographicCamera camera;
    private float unitScale;

    private final Set<String> collisionLayers = new HashSet<>();
    private final Array<Body> collisionBodies = new Array<>();

    private Box2DDebugRenderer debugRenderer;
    private boolean debugEnabled = false;

    private SoundManager soundManager;
    private String currentMusic;

    /**
     * @param path       Path to TMX file (internal)
     * @param unitScale  Scale factor (e.g. 1/16f for 16px tiles)
     * @param world      Box2D world (can be null)
     * @param soundManager  SoundManager instance (optional)
     */
    public TileMapLoader(String path, float unitScale, World world, SoundManager soundManager) {
        this.loader = new TmxMapLoader();
        this.unitScale = unitScale;
        this.world = world;
        this.soundManager = soundManager;
        this.debugRenderer = new Box2DDebugRenderer(true, true, false, true, false, false);
        load(path);
    }

    /** Loads or reloads TMX map */
    public void load(String path) {
        dispose();
        map = loader.load(path);
        renderer = new OrthogonalTiledMapRenderer(map, unitScale);
        rebuildCollisionData();
        handleMapMusic();
    }

    public void setCamera(OrthographicCamera camera) {
        this.camera = camera;
    }

    public void setCollisionLayers(String... layers) {
        collisionLayers.clear();
        for (String l : layers) collisionLayers.add(l);
        rebuildCollisionData();
    }

    public void setDebug(boolean enabled) {
        this.debugEnabled = enabled;
    }

    private void rebuildCollisionData() {
        clearCollisionBodies();
        if (map == null) return;

        for (MapLayer layer : map.getLayers()) {
            if (!collisionLayers.contains(layer.getName())) continue;

            float friction = getLayerProperty(layer, "friction", 0.8f);
            float restitution = getLayerProperty(layer, "restitution", 0f);
            float density = getLayerProperty(layer, "density", 1f);
            boolean isSensor = getLayerProperty(layer, "sensor", false);

            if (layer instanceof TiledMapTileLayer tileLayer) {
                int width = tileLayer.getWidth();
                int height = tileLayer.getHeight();
                float tileWidth = tileLayer.getTileWidth() * unitScale;
                float tileHeight = tileLayer.getTileHeight() * unitScale;

                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        TiledMapTileLayer.Cell cell = tileLayer.getCell(x, y);
                        if (cell != null && cell.getTile() != null) {
                            Rectangle rect = new Rectangle(x * tileWidth, y * tileHeight, tileWidth, tileHeight);
                            if (world != null)
                                createStaticBox(rect, friction, restitution, density, isSensor);
                        }
                    }
                }
            }

            MapObjects objects = layer.getObjects();
            for (MapObject obj : objects) {
                if (obj instanceof RectangleMapObject rectObj) {
                    Rectangle rect = rectObj.getRectangle();
                    Rectangle scaled = new Rectangle(
                            rect.x * unitScale,
                            rect.y * unitScale,
                            rect.width * unitScale,
                            rect.height * unitScale
                    );
                    if (world != null)
                        createStaticBox(scaled, friction, restitution, density, isSensor);

                } else if (obj instanceof PolygonMapObject polyObj) {
                    float[] vertices = polyObj.getPolygon().getTransformedVertices().clone();
                    scaleVertices(vertices);
                    if (world != null)
                        createStaticPolygon(vertices, friction, restitution, density, isSensor);

                } else if (obj instanceof PolylineMapObject lineObj) {
                    float[] vertices = lineObj.getPolyline().getTransformedVertices().clone();
                    scaleVertices(vertices);
                    if (world != null)
                        createStaticChain(vertices, friction, restitution, density, isSensor);
                }
            }
        }
    }

    private void createStaticBox(Rectangle rect, float friction, float restitution, float density, boolean isSensor) {
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.StaticBody;
        def.position.set(rect.x + rect.width / 2f, rect.y + rect.height / 2f);

        Body body = world.createBody(def);
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(rect.width / 2f, rect.height / 2f);

        FixtureDef fdef = new FixtureDef();
        fdef.shape = shape;
        fdef.friction = friction;
        fdef.restitution = restitution;
        fdef.density = density;
        fdef.isSensor = isSensor;

        body.createFixture(fdef);
        shape.dispose();
        collisionBodies.add(body);
    }

    private void createStaticPolygon(float[] vertices, float friction, float restitution, float density, boolean isSensor) {
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.StaticBody;
        Body body = world.createBody(def);

        PolygonShape shape = new PolygonShape();
        shape.set(vertices);
        FixtureDef fdef = new FixtureDef();
        fdef.shape = shape;
        fdef.friction = friction;
        fdef.restitution = restitution;
        fdef.density = density;
        fdef.isSensor = isSensor;

        body.createFixture(fdef);
        shape.dispose();
        collisionBodies.add(body);
    }

    private void createStaticChain(float[] vertices, float friction, float restitution, float density, boolean isSensor) {
        BodyDef def = new BodyDef();
        def.type = BodyDef.BodyType.StaticBody;
        Body body = world.createBody(def);

        ChainShape shape = new ChainShape();
        shape.createChain(vertices);

        FixtureDef fdef = new FixtureDef();
        fdef.shape = shape;
        fdef.friction = friction;
        fdef.restitution = restitution;
        fdef.density = density;
        fdef.isSensor = isSensor;

        body.createFixture(fdef);
        shape.dispose();
        collisionBodies.add(body);
    }

    private void scaleVertices(float[] vertices) {
        for (int i = 0; i < vertices.length; i++) vertices[i] *= unitScale;
    }

    private <T> T getLayerProperty(MapLayer layer, String key, T defaultValue) {
        MapProperties props = layer.getProperties();
        if (props.containsKey(key)) {
            Object val = props.get(key);
            if (defaultValue instanceof Boolean)
                return (T) Boolean.valueOf(val.toString());
            if (defaultValue instanceof Float)
                return (T) Float.valueOf(val.toString());
        }
        return defaultValue;
    }

    /** Handles embedded background music via SoundManager */
    private void handleMapMusic() {
        if (soundManager == null || map == null) return;

        MapProperties props = map.getProperties();
        if (props.containsKey("music")) {
            String musicValue = props.get("music", String.class);
            if (musicValue == null) return;

            // Support multiple tracks (comma separated) for randomization
            String[] tracks = musicValue.split(",");
            String chosen = tracks[(int) (Math.random() * tracks.length)].trim();

            if (!chosen.equals(currentMusic)) {
                soundManager.play(chosen);
                currentMusic = chosen;
            }
        }
    }

    public void renderLayers(String... names) {
        if (camera == null || renderer == null || map == null) return;
        renderer.setView(camera);

        // Collect indices of matching layers
        Array<Integer> indices = new Array<>();
        for (String name : names) {
            int index = map.getLayers().getIndex(name);
            if (index != -1 && map.getLayers().get(name).isVisible()) {
                indices.add(index);
            }
        }

        // Convert to int[] because renderer.render() expects primitive array
        int[] layerIndices = new int[indices.size];
        for (int i = 0; i < indices.size; i++) {
            layerIndices[i] = indices.get(i);
        }

        renderer.render(layerIndices);
    }

    public void renderDebug() {
        if (debugEnabled && world != null && camera != null)
            debugRenderer.render(world, camera.combined);
    }

    private void clearCollisionBodies() {
        if (world == null) return;
        for (Body b : collisionBodies) world.destroyBody(b);
        collisionBodies.clear();
    }

    @Override
    public void dispose() {
        clearCollisionBodies();
        if (renderer != null) renderer.dispose();
        if (map != null) map.dispose();
        if (debugRenderer != null) debugRenderer.dispose();
    }
}

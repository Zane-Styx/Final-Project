package com.jjmc.chromashift.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.Wall;
import com.jjmc.chromashift.environment.interactable.Button;
import com.jjmc.chromashift.environment.interactable.Interactable;
import com.jjmc.chromashift.environment.interactable.Box;
import com.jjmc.chromashift.environment.interactable.Target;
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
    private Stage uiStage;

    private Array<Wall> walls;
    private Array<Interactable> interactables;
    private Array<Solid> solids;
    private Array<com.jjmc.chromashift.environment.collectible.Collectible> collectibles;
    private Array<com.jjmc.chromashift.environment.interactable.Shop> shops;

    // Player spawn for respawn key
    private float playerSpawnX;
    private float playerSpawnY;
    private Spawn spawnMarker;

    // Base area walls so render can reference their bounds
    private Wall baseLeft;
    private Wall baseCenter;
    private Wall baseRight;

    private float groundY = -64f;

    // Current level path for save/load and visited levels tracking
    private String currentLevelPath = "levels/level1.json";
    private Array<String> visitedLevels = new Array<>();
    // Camera zoom settings
    private float desiredZoom = 0.4f; // <1 = zoom in a bit
    private float zoomLerpSpeed = 2.5f; // how fast camera zooms to target

    // Tentacle System
    private Array<com.jjmc.chromashift.environment.enemy.Tentacle> tentacles;
    private Array<com.jjmc.chromashift.environment.enemy.TentacleCapture> tentacleCaptures;
    // Cache enemies list so we can perform a post-tentacle-update collision pass.
    private Array<com.jjmc.chromashift.environment.enemy.Enemy> enemies;

    @Override
    public void show() {
        // Detach any previous UI Stage (e.g., menu) so its actors stop receiving input
        try { Gdx.input.setInputProcessor(null); } catch (Throwable ignored) {}
        // Reset editor flags when entering game mode
        com.jjmc.chromashift.environment.interactable.Box.EDITOR_DELETE_MODE = false;
        com.jjmc.chromashift.environment.interactable.Orb.EDITOR_DELETE_MODE = false;
        // Enable gameplay-only visibility culling
        try { com.jjmc.chromashift.helper.VisibilityCuller.setEnabled(true); } catch (Throwable ignored) {}

        // Use Initialize helper to create common systems
        ctx = Initialize.createCommon(500, 180, null);
        camera = ctx.camera;
        camController = ctx.camController;
        batch = ctx.batch;
        shape = ctx.shape;
        font = ctx.font;

        // Load everything via the unified LevelLoader
        String levelPath = "levels/level1.json";
        // Track current level and mark visited for save/load
        this.currentLevelPath = levelPath;
        this.visitedLevels.clear();
        this.visitedLevels.add(levelPath);
        // Prefer workspace copy when available so editor changes (door speeds, links)
        // are reflected immediately during playtesting.
        com.jjmc.chromashift.screens.levels.LevelLoader.Result loaded;
        try {
            loaded = com.jjmc.chromashift.screens.levels.LevelLoader.loadFromWorkspace(levelPath);
        } catch (Exception ex) {
            loaded = com.jjmc.chromashift.screens.levels.LevelLoader.load(levelPath);
        }

        // Adopt loaded collections so updates/render iterate the same instances
        this.walls = loaded.walls;
        this.solids = loaded.solids;
        this.interactables = loaded.interactables;
        this.collectibles = loaded.collectibles;

        // Initialize UI stage for shop dialogs
        uiStage = new Stage(new ScreenViewport());

        // Use InputMultiplexer to allow both UI and game input
        // Stage gets priority for UI clicks, but keyboard input still works for player
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        Gdx.input.setInputProcessor(multiplexer);

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

        // Initialize player skills
        player.equipSkillToSlot(new com.jjmc.chromashift.player.skill.DashSkill(player), 'Q');
        player.equipSkillToSlot(new com.jjmc.chromashift.player.skill.JumpSkill(player), 'E');

        // Visible spawn marker (static frame by default)
        spawnMarker = new Spawn(playerSpawnX, playerSpawnY);

        // Instantiate shops now that we have Player and Stage
        shops = new Array<>();
        for (com.jjmc.chromashift.screens.levels.LevelIO.LevelState.ShopData sd : loaded.shopDataList) {
            com.jjmc.chromashift.environment.interactable.Shop shop = new com.jjmc.chromashift.environment.interactable.Shop(
                    sd.x, sd.y, player, uiStage);
            shops.add(shop);
            interactables.add(shop); // Add to interactables for collision/interaction
        }
        // Assign player to any LockedDoor instances so they can check keys
        for (int i = 0; i < interactables.size; i++) {
            com.jjmc.chromashift.environment.interactable.Interactable it = interactables.get(i);
            if (it instanceof com.jjmc.chromashift.environment.interactable.LockedDoor ld) {
                ld.setPlayer(player);
            }
        }

        // Initialize Tentacle System from loaded data
        tentacles = loaded.tentacles;
        tentacleCaptures = new Array<>();
        for (com.jjmc.chromashift.environment.enemy.Tentacle t : tentacles) {
            tentacleCaptures.add(new com.jjmc.chromashift.environment.enemy.TentacleCapture(t, player));
            // Ensure diamonds from death go into level collectibles
            t.setDropTarget(collectibles);
        }

        // Set up enemy tracking for player melee attacks (store in field for later use)
        enemies = new Array<>();
        for (com.jjmc.chromashift.environment.enemy.Tentacle t : tentacles) {
            enemies.add(t);
        }
        player.setEnemies(enemies);
    }

    @Override
    public void render(float delta) {
        // Basic input: ESC returns to test menu
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            ((com.badlogic.gdx.Game) Gdx.app.getApplicationListener()).setScreen(new TestMenuScreen());
            return;
        }

        // Respawn player to initial spawn with R
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            player.respawn();
            if (spawnMarker != null) {
                spawnMarker.setPosition(playerSpawnX, playerSpawnY);
                spawnMarker.playOnce();
            }
            // Reset tentacle capture states on respawn
            for (com.jjmc.chromashift.environment.enemy.Tentacle t : tentacles) {
                t.setPlayerCaptured(false);
            }
        }

        // Quick save/load: F11 = save, F12 = load
        try {
            if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) {
                com.jjmc.chromashift.player.PlayerIO.PlayerState state = com.jjmc.chromashift.player.PlayerIO.capture(player, currentLevelPath, visitedLevels);
                boolean ok = com.jjmc.chromashift.player.PlayerIO.saveToWorkspace("player_save.json", state);
                Gdx.app.log("TestSceneScreen", "Player save " + (ok ? "saved" : "failed"));
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.F12)) {
                com.jjmc.chromashift.player.PlayerIO.PlayerState loaded = com.jjmc.chromashift.player.PlayerIO.load("player_save.json");
                if (loaded != null) {
                    com.jjmc.chromashift.player.PlayerIO.applyToPlayer(player, loaded);
                    if (loaded.currentLevel != null) currentLevelPath = loaded.currentLevel;
                    if (loaded.visitedLevels != null) visitedLevels = loaded.visitedLevels;
                    Gdx.app.log("TestSceneScreen", "Player loaded from save");
                } else {
                    Gdx.app.log("TestSceneScreen", "No player save found to load");
                }
            }
        } catch (Exception ignored) {}

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
                // Check for boxes and orbs on launchpad (use indexed loop to avoid nested
                // iterator)
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

        // Finalize target states after all laser updates
        try {
            Target.finalizeFrame();
        } catch (Throwable ignored) {
        }

        // Then update buttons with collected bounds
        for (int i = 0; i < interactables.size; i++) {
            Interactable interactable = interactables.get(i);
            if (interactable instanceof Button b) {
                b.update(delta, player.getHitboxRect(), objectBounds);
            }
        }

        // Update collectibles and check for collection
        for (int i = collectibles.size - 1; i >= 0; i--) {
            com.jjmc.chromashift.environment.collectible.Collectible c = collectibles.get(i);
            c.update(delta);
            c.checkCollision(player);
            // Remove collected items
            if (c.isCollected()) {
                collectibles.removeIndex(i);
            }
        }

        // Player update
        player.update(delta, groundY, solids, interactables, 1);

        // Update Tentacle System (physics & capture applied AFTER player attack activation)
        for (com.jjmc.chromashift.environment.enemy.TentacleCapture tc : tentacleCaptures) {
            tc.update(delta);
        }
        // Perform a second collision check now that tentacle segment positions are freshly updated.
        // This fixes ordering issue where Player updated (and attacked) before Tentacle updated its segment hitboxes.
        if (player.getAttackHitbox() != null && player.getAttackHitbox().isActive()) {
            player.getAttackHitbox().checkEnemyCollisions(enemies);
        }

        // Prune dead tentacles and associated capture handlers & enemy list entries
        if (tentacles.size > 0) {
            for (int i = tentacles.size - 1; i >= 0; i--) {
                com.jjmc.chromashift.environment.enemy.Tentacle t = tentacles.get(i);
                if (!t.isAlive()) {
                    // Remove capture handler referencing this tentacle
                    for (int c = tentacleCaptures.size - 1; c >= 0; c--) {
                        if (tentacleCaptures.get(c).getTentacle() == t) {
                            tentacleCaptures.removeIndex(c);
                            break;
                        }
                    }
                    // Remove from enemies list used by Player attack system
                    if (enemies != null) {
                        enemies.removeValue(t, false);
                        player.setEnemies(enemies);
                    }
                    tentacles.removeIndex(i);
                }
            }
        }

        // Boss update - set target to player position
        if (boss != null) {
            boss.setTarget(player.getX() + player.getHitboxWidth() / 2, player.getY() + player.getHitboxHeight() / 2);
            boss.update(delta);
        }

        // Check laser beams against the player hitbox; if intersecting, kill the player
        Rectangle playerHit = player.getHitboxRect();
        float beamThickness = 6f; // matches Laser/LaserRay outer thickness
        for (int i = 0; i < interactables.size; i++) {
            Interactable it = interactables.get(i);
            java.util.ArrayList<com.badlogic.gdx.math.Vector2> pts = null;
            if (it instanceof com.jjmc.chromashift.environment.interactable.Laser l) {
                pts = l.getCachedPoints();
            } else if (it instanceof com.jjmc.chromashift.environment.interactable.LaserRay lr) {
                pts = lr.getCachedPoints();
            }
            if (pts == null || pts.size() < 2)
                continue;
            if (beamIntersectsRect(pts, playerHit, beamThickness)) {
                // kill player instantly
                try {
                    player.getHealthSystem().kill(it);
                } catch (Throwable ignored) {
                }
                break;
            }
        }

        // Camera update / follow
        Vector2 playerCenter = new Vector2(player.getX() + player.getHitboxWidth() / 2f,
                player.getY() + player.getHitboxHeight() / 2f);
        camController.setTarget(playerCenter);
        camController.update(delta);

        // Smoothly adjust camera zoom toward desiredZoom so view focuses slightly on
        // player
        if (camera != null) {
            float cur = camera.zoom;
            float t = Math.min(1f, zoomLerpSpeed * delta);
            camera.zoom = cur + (desiredZoom - cur) * t;
            camera.update();
        }

        // Draw
        Gdx.gl.glClearColor(0.08f, 0.09f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shape.setProjectionMatrix(camController.getCamera().combined);
        // Draw Tentacles (behind everything or in front? Let's draw behind player but in
        // front of walls)
        // Actually, ShapeRenderer needs begin/end.
        // The existing code ends 'shape' before batch.begin().
        // We can draw tentacles here.
        for (com.jjmc.chromashift.environment.enemy.Tentacle t : tentacles) {
            t.draw(shape);
        }
        shape.end();

        batch.setProjectionMatrix(camController.getCamera().combined);
        batch.begin();
        // Draw world: walls first, then interactables, collectibles, spawn marker,
        // boss, and player
        for (Wall w : walls)
            w.render(batch);
        for (Interactable i : interactables)
            i.render(batch);
        // Render collectibles (diamonds, etc.)
        for (com.jjmc.chromashift.environment.collectible.Collectible c : collectibles) {
            c.render(batch);
        }
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

        // // Draw debug UI with clean layout
        // float baseX = camController.getCamera().position.x - 480 + 8; // Left align
        // float baseY = camController.getCamera().position.y + 260; // Top of screen
        // float lineHeight = 20f; // Space between lines

        // // Title
        // font.setColor(Color.GOLD);
        // font.draw(batch, "Debug Info (F3 for hitboxes)", baseX, baseY);

        // // Movement controls
        // font.setColor(Color.WHITE);
        // baseY -= lineHeight;
        // font.draw(batch, "Movement:", baseX, baseY);
        // font.setColor(Color.LIGHT_GRAY);
        // baseY -= lineHeight;
        // font.draw(batch, "• A/D - Move Left/Right", baseX + 10, baseY);
        // baseY -= lineHeight;
        // font.draw(batch, "• W - Jump", baseX + 10, baseY);
        // baseY -= lineHeight;
        // font.draw(batch, "• Space - Attack", baseX + 10, baseY);
        // baseY -= lineHeight;
        // font.draw(batch, "• SHIFT - Dash (once until landing)", baseX + 10, baseY);

        // // Combat/Health
        // baseY -= lineHeight * 1.5f;
        // font.setColor(Color.WHITE);
        // font.draw(batch, "Combat:", baseX, baseY);
        // font.setColor(Color.LIGHT_GRAY);
        // baseY -= lineHeight;
        // String healthText = String.format("• Player Health: %.0f/%.0f",
        // player.getHealthSystem().getCurrentHealth(),
        // player.getHealthSystem().getMaxHealth());
        // font.draw(batch, healthText, baseX + 10, baseY);
        // baseY -= lineHeight;
        // String bossHealthText = boss != null ? String.format("• Boss Health:
        // %.0f/%.0f", boss.getHealthSystem().getCurrentHealth(),
        // boss.getHealthSystem().getMaxHealth()) : "• Boss: None";
        // font.draw(batch, bossHealthText, baseX + 10, baseY);
        // baseY -= lineHeight;
        // font.draw(batch, "• O - Test damage (100)", baseX + 10, baseY);

        // // Interaction controls
        // baseY -= lineHeight * 1.5f;
        // font.setColor(Color.WHITE);
        // font.draw(batch, "Interaction:", baseX, baseY);
        // font.setColor(Color.LIGHT_GRAY);
        // baseY -= lineHeight;
        // font.draw(batch, "• G - Pick up/Throw objects (aim with mouse)", baseX + 10,
        // baseY);
        // baseY -= lineHeight;
        // font.draw(batch, "• F - Interact", baseX + 10, baseY);

        // // System controls
        // baseY -= lineHeight * 1.5f;
        // font.setColor(Color.WHITE);
        // font.draw(batch, "System:", baseX, baseY);
        // font.setColor(Color.LIGHT_GRAY);
        // baseY -= lineHeight;
        // font.draw(batch, "• R - Respawn", baseX + 10, baseY);
        // baseY -= lineHeight;
        // font.draw(batch, "• F3 - Show Debug Lines", baseX + 10, baseY);
        // baseY -= lineHeight;
        // font.draw(batch, "• ESC - Exit game", baseX + 10, baseY);

        // Status effects (if any active)
        if (player.getHealthSystem().isInvulnerable()) {
            font.setColor(Color.YELLOW);
            font.draw(batch, "INVULNERABLE",
                    camController.getCamera().position.x - 40, // Center on screen
                    camController.getCamera().position.y + 40);
        }
        batch.end();

        // Update and draw UI stage for shop dialogs
        uiStage.act(delta);
        uiStage.draw();

        // Debug visuals
        shape.setProjectionMatrix(camController.getCamera().combined);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(Color.FOREST);
        shape.rect(-2000, groundY - 5, 4000, 5);
        shape.end();

        if (Gdx.input.isKeyPressed(Input.Keys.F3)) {
            shape.begin(ShapeRenderer.ShapeType.Line);
            for (Wall w : walls)
                w.debugDraw(shape);
            for (Interactable i : interactables)
                i.debugDraw(shape);
            player.debugDrawHitbox(shape);

            // Draw tentacle segment hitboxes and curl detection
            for (com.jjmc.chromashift.environment.enemy.Tentacle t : tentacles) {
                if (!t.isAlive()) continue;

                // Draw segment hitboxes (cyan circles)
                shape.setColor(new Color(0f, 1f, 1f, 0.5f));
                com.badlogic.gdx.math.Circle[] hitboxes = t.getSegmentHitboxes();
                if (hitboxes != null) {
                    for (com.badlogic.gdx.math.Circle c : hitboxes) {
                        if (c != null) {
                            shape.circle(c.x, c.y, c.radius, 16);
                        }
                    }
                }

                // Draw curl center and radius if curled (yellow)
                if (t.isCurled() && t.hasFullCurl()) {
                    com.badlogic.gdx.math.Vector2 center = t.getCurlCenter();
                    float radius = t.getCurlRadius();

                    // Draw curl circle
                    shape.setColor(new Color(1f, 1f, 0f, 0.6f));
                    shape.circle(center.x, center.y, radius, 32);

                    // Draw center crosshair
                    shape.setColor(new Color(1f, 0f, 0f, 0.8f));
                    float crossSize = 10f;
                    shape.line(center.x - crossSize, center.y, center.x + crossSize, center.y);
                    shape.line(center.x, center.y - crossSize, center.x, center.y + crossSize);

                    // Draw hit counter
                    shape.end();
                    batch.begin();
                    font.setColor(Color.YELLOW);
                    font.draw(batch, "Hits: " + t.getHitsThisCapture() + "/3", center.x - 20, center.y + radius + 20);
                    batch.end();
                    shape.begin(ShapeRenderer.ShapeType.Line);
                }
            }

            // Draw player attack hitbox (red)
            if (player.getAttackHitbox() != null) {
                player.getAttackHitbox().debugDraw(shape);
            }

            // Draw respawn areas for boxes and orbs
            shape.setColor(new Color(0f, 0.5f, 1f, 0.25f));
            for (Interactable i : interactables) {
                if (i instanceof com.jjmc.chromashift.environment.interactable.Box b) {
                    Rectangle area = b.getRespawnArea();
                    if (area != null)
                        shape.rect(area.x, area.y, area.width, area.height);
                } else if (i instanceof com.jjmc.chromashift.environment.interactable.Orb o) {
                    Rectangle area = o.getRespawnArea();
                    if (area != null)
                        shape.rect(area.x, area.y, area.width, area.height);
                }
            }
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

    /**
     * Return true if any segment in the polyline `points` intersects rectangle `r`
     * within `thickness`.
     */
    private boolean beamIntersectsRect(java.util.ArrayList<com.badlogic.gdx.math.Vector2> points, Rectangle r,
            float thickness) {
        if (points == null || points.size() < 2 || r == null)
            return false;
        float pad = thickness * 0.5f;
        Rectangle re = new Rectangle(r.x - pad, r.y - pad, r.width + pad * 2f, r.height + pad * 2f);

        // helper: check segment intersects rectangle `re`
        for (int i = 0; i < points.size() - 1; i++) {
            com.badlogic.gdx.math.Vector2 a = points.get(i);
            com.badlogic.gdx.math.Vector2 b = points.get(i + 1);
            // if either endpoint inside rect -> hit
            if (re.contains(a.x, a.y) || re.contains(b.x, b.y))
                return true;
            // check intersection with each rectangle edge
            com.badlogic.gdx.math.Vector2 r1 = new com.badlogic.gdx.math.Vector2(re.x, re.y);
            com.badlogic.gdx.math.Vector2 r2 = new com.badlogic.gdx.math.Vector2(re.x + re.width, re.y);
            com.badlogic.gdx.math.Vector2 r3 = new com.badlogic.gdx.math.Vector2(re.x + re.width, re.y + re.height);
            com.badlogic.gdx.math.Vector2 r4 = new com.badlogic.gdx.math.Vector2(re.x, re.y + re.height);
            if (segmentsIntersect(a, b, r1, r2))
                return true;
            if (segmentsIntersect(a, b, r2, r3))
                return true;
            if (segmentsIntersect(a, b, r3, r4))
                return true;
            if (segmentsIntersect(a, b, r4, r1))
                return true;
        }
        return false;
    }

    // Standard 2D segment intersection test
    private boolean segmentsIntersect(com.badlogic.gdx.math.Vector2 p1, com.badlogic.gdx.math.Vector2 p2,
            com.badlogic.gdx.math.Vector2 q1, com.badlogic.gdx.math.Vector2 q2) {
        float o1 = orient(p1, p2, q1);
        float o2 = orient(p1, p2, q2);
        float o3 = orient(q1, q2, p1);
        float o4 = orient(q1, q2, p2);

        if (o1 * o2 < 0f && o3 * o4 < 0f)
            return true;
        return false;
    }

    private float orient(com.badlogic.gdx.math.Vector2 a, com.badlogic.gdx.math.Vector2 b,
            com.badlogic.gdx.math.Vector2 c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0)
            return;
        camera.setToOrtho(false, width, height);
        if (uiStage != null) {
            uiStage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
        // Disable culling when leaving gameplay (e.g., to editor/menu)
        try { com.jjmc.chromashift.helper.VisibilityCuller.setEnabled(false); } catch (Throwable ignored) {}
    }

    @Override
    public void dispose() {
        if (ctx != null)
            ctx.dispose();
        player.dispose();
        if (boss != null)
            boss.disposeParts();
        // dispose button sprites
        for (Interactable i : interactables)
            if (i instanceof Button b)
                b.dispose();
        // dispose collectibles
        for (com.jjmc.chromashift.environment.collectible.Collectible c : collectibles)
            c.dispose();
        // dispose shops
        for (com.jjmc.chromashift.environment.interactable.Shop s : shops)
            s.dispose();
        // dispose UI stage
        if (uiStage != null)
            uiStage.dispose();
        // dispose wall/shared textures
        com.jjmc.chromashift.environment.Wall.dispose();
    }
}

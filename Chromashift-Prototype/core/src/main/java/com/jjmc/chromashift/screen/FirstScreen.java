package com.jjmc.chromashift.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.jjmc.chromashift.ChromashiftGame; // Added for context, but not used directly
import com.jjmc.chromashift.network.GameClient;
import com.jjmc.chromashift.network.HostServer;
import com.jjmc.chromashift.network.Network;
import com.jjmc.chromashift.player.Player;
import com.jjmc.chromashift.player.PlayerType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main game screen where gameplay occurs.
 * Manages the host/client lifecycle, player objects, and game loop.
 */
public class FirstScreen implements Screen, Disposable {

    // private final ChromashiftGame game; // Removed, unnecessary field
    private final boolean isHost;
    private final String hostAddress;
    private final String playerName;

    // Networking
    private HostServer hostServer;
    private GameClient gameClient;
    private int myPlayerId = -1;

    // Rendering
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;

    // Game Objects
    private Player localPlayer;
    private final float GROUND_Y = 100f;

    // Players managed by ID (for easy lookup and update)
    private final ConcurrentHashMap<Integer, Player> activePlayers = new ConcurrentHashMap<>();

    public FirstScreen(boolean isHost, String hostAddress, String playerName) {
        // this.game = game; // Removed
        this.isHost = isHost;
        this.hostAddress = hostAddress;
        this.playerName = playerName;
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera(800, 600);
        camera.setToOrtho(false, 800, 600);
        camera.update();

        // 1. Setup Networking (Host or Client)
        if (isHost) {
            try {
                hostServer = new HostServer();
                hostServer.start();
                System.out.println("HostServer started successfully.");
            } catch (IOException e) {
                Gdx.app.error("NETWORK", "Failed to start HostServer: " + e.getMessage());
                // In a real game, you would exit the screen here.
            }
        }

        // Always create a client instance to connect to the game
        gameClient = new GameClient(playerName);
        gameClient.start();
        gameClient.connectAsync(hostAddress);

        // 2. Initialize Local Player
        localPlayer = new Player(
            50, GROUND_Y,
            Input.Keys.A, Input.Keys.D, Input.Keys.W, Input.Keys.SPACE, // WASD for movement, Space for Attack
            PlayerType.BLUE,
            6, 10,
            64, 32, 5, // 5 frames for attack, based on your final debug
            200f, 350f, -900f
        );
        localPlayer.setLocal(true); // This instance reads input!

        // Use a temporary ID until the server assigns a real one.
        activePlayers.put(-1, localPlayer);
    }

    @Override
    public void render(float delta) {
        // 1. Network Consumption (must happen frequently)
        consumeNetwork();

        // 2. Local Player Update
        // This is where local physics and input are run (only for localPlayer).
        localPlayer.update(delta, GROUND_Y);

        // 3. Send Input to Server (only if we are connected and have an ID)
        if (myPlayerId != -1) {
            sendInputToServer();
        }

        // 4. Rendering
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Render all active players (local and remote)
        for (Player p : activePlayers.values()) {
            p.render(batch);
        }
        batch.end();

        // Debug Rendering (Hitboxes)
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 1f);
        shapeRenderer.rect(0, 0, 800, GROUND_Y); // Draw the ground
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (Player p : activePlayers.values()) {
            p.debugDrawHitbox(shapeRenderer);
        }
        shapeRenderer.end();
    }

    private void consumeNetwork() {
        gameClient.consumeNetworkMessages();

        // CHECK 1: Did we receive our ID?
        if (myPlayerId == -1 && gameClient.getMyId() != -1) {
            myPlayerId = gameClient.getMyId();
            System.out.println("Client officially assigned ID: " + myPlayerId);
            // Move localPlayer from temp ID (-1) to its real, assigned ID
            activePlayers.remove(-1);
            activePlayers.put(myPlayerId, localPlayer);
        }

        // CHECK 2: Update all players based on GameState
        ArrayList<Network.PlayerState> latestStates = gameClient.getLatestStates();

        // Housekeeping: Remove players no longer present in the server's state
        activePlayers.entrySet().removeIf(entry -> {
            int id = entry.getKey();
            if (id == myPlayerId || id == -1) return false; // Don't touch our player or temp ID
            return latestStates.stream().noneMatch(ps -> ps.id == id);
        });

        // Add or update players
        for (Network.PlayerState state : latestStates) {

            // If this is our player (the local one), skip state application
            // since we are running local prediction/physics.
            if (state.id == myPlayerId) {
                // If you implemented client-side prediction, you would do reconciliation here.
                continue;
            }

            Player remotePlayer = activePlayers.get(state.id);

            // Case 1: New remote player found
            if (remotePlayer == null) {
                // Initialize remote player instance
                // NOTE: You must ensure PlayerType.RED constructor args match localPlayer's in number/type
                remotePlayer = new Player(
                    state.x, state.y,
                    0, 0, 0, 0, // Remote players should not read local input
                    PlayerType.RED, // Use a different color for remote players
                    6, 10,
                    64, 32, 5,
                    200f, 350f, -900f
                );
                remotePlayer.setLocal(false); // CRITICAL: Skip input/physics for remote
                activePlayers.put(state.id, remotePlayer);
            }

            remotePlayer.applyState(state.x, state.y, state.vx, state.vy);

            // --- CRITICAL: Apply new boolean states to remote player ---
            // NOTE: This assumes you have added these getters/setters in Player.java
            // and Network.PlayerState
            remotePlayer.setIsDashing(state.isDashing);
            remotePlayer.setIsAttacking(state.isAttacking);
            remotePlayer.setOnGround(state.onGround);
            remotePlayer.setFacingLeft(state.facingLeft);
            // -----------------------------------------------------------

            // Now call update to advance animation only (since local=false)
            remotePlayer.update(Gdx.graphics.getDeltaTime(), GROUND_Y);
        }
    }

    private void sendInputToServer() {
        Network.InputMessage msg = new Network.InputMessage();
        msg.playerId = myPlayerId;
        msg.clientTime = System.currentTimeMillis();

        float horizontal = 0f;
        if (Gdx.input.isKeyPressed(localPlayer.getKeyLeft())) horizontal = -1f;
        else if (Gdx.input.isKeyPressed(localPlayer.getKeyRight())) horizontal = 1f;

        msg.horizontal = horizontal;
        msg.jump = Gdx.input.isKeyJustPressed(localPlayer.getKeyJump());

        // --- CRITICAL: Populate new state flags from localPlayer ---
        msg.isDashing = localPlayer.isDashing();
        msg.isAttacking = localPlayer.isAttacking();
        msg.facingLeft = localPlayer.isFacingLeft();
        // ------------------------------------------------------------

        gameClient.sendInput(msg);
    }

    @Override public void resize(int width, int height) { camera.update(); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        // Shut down network connections
        if (hostServer != null) hostServer.stop();
        if (gameClient != null) gameClient.stop();

        // Dispose rendering resources
        batch.dispose();
        shapeRenderer.dispose();

        // Dispose player resources
        for (Player p : activePlayers.values()) {
            p.dispose();
        }
    }
}

package com.jjmc.chromashift.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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
public class FirstScreen implements Screen {

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
        gameClient = new GameClient(playerName, PlayerType.BLUE.ordinal());
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
        Network.GameState currentState = gameClient.getCurrentState();
        if (currentState == null || currentState.players == null) return;

        float renderAlpha = calculateInterpolationAlpha();

        // Housekeeping: Remove players no longer present in the server's state
        activePlayers.entrySet().removeIf(entry -> {
            int id = entry.getKey();
            if (id == myPlayerId || id == -1) return false; // Don't touch our player or temp ID
            return currentState.players.stream().noneMatch(ps -> ps.id == id);
        });

                // Add or update players
        for (Network.PlayerState state : currentState.players) {
            if (state.id == myPlayerId) continue; // Skip our own player

            // Get or create remote player
            Player player = activePlayers.computeIfAbsent(state.id, id -> {
                System.out.println("Creating new remote player with id: " + id);
                Network.PlayerProfile profile = null;
                // Try to find profile info in game state
                if (currentState.gameData != null && currentState.gameData.containsKey("profile_" + id)) {
                    profile = (Network.PlayerProfile) currentState.gameData.get("profile_" + id);
                }
                
                Player p = new Player(
                    state.x, state.y,
                    Input.Keys.A, Input.Keys.D, Input.Keys.W, Input.Keys.SPACE,
                    profile != null ? PlayerType.values()[profile.characterType] : PlayerType.RED,
                    6, 10,
                    64, 32, 5,
                    200f, 350f, -900f
                );
                p.setLocal(false); // Mark as remote player
                return p;
            });

            // Get interpolated state
            Network.PlayerState interpolated = gameClient.interpolatePlayerState(state.id, renderAlpha);
            if (interpolated == null) interpolated = state;

            // Apply server state to remote player
            player.applyState(
                interpolated.x, interpolated.y,
                interpolated.vx, interpolated.vy,
                interpolated.onGround,
                interpolated.isDashing,
                interpolated.isAttacking,
                interpolated.facingLeft
            );
            
            // Update animation if it changed
            if (interpolated.currentAnim != null) {
                player.getAnimator().play(interpolated.currentAnim, interpolated.facingLeft);
            }
            
            // Advance animation/update for remote player (they are marked local=false)
            player.update(Gdx.graphics.getDeltaTime(), GROUND_Y);
            
        }
}

    private float calculateInterpolationAlpha() {
        Network.GameState current = gameClient.getCurrentState();
        Network.GameState previous = gameClient.getPreviousState();
        
        if (current == null || previous == null) return 1.0f;
        
        long currentTime = gameClient.getServerTime();
        long dt = current.serverTime - previous.serverTime;
        if (dt <= 0) return 1.0f;
        
        float alpha = (float)(currentTime - previous.serverTime) / dt;
        return Math.min(1.0f, Math.max(0.0f, alpha));
    }

    private void sendInputToServer() {
        Network.InputMessage msg = new Network.InputMessage();
        msg.playerId = myPlayerId;
        msg.clientTime = System.currentTimeMillis();

        float horizontal = 0f;
        if (Gdx.input.isKeyPressed(localPlayer.getKeyLeft())) horizontal = -1f;
        else if (Gdx.input.isKeyPressed(localPlayer.getKeyRight())) horizontal = 1f;

        msg.horizontal = horizontal;
        msg.jump = Gdx.input.isKeyPressed(localPlayer.getKeyJump());

        // State flags
        msg.isDashing = localPlayer.isDashing();
        msg.isAttacking = localPlayer.isAttacking();
        msg.facingLeft = localPlayer.isFacingLeft();

        // Add current predicted state
        msg.position = new float[] { localPlayer.getX(), localPlayer.getY() };
        // Note: Player class doesn't expose velocity directly, we'll have to track state changes
        // for prediction if needed

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

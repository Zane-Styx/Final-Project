package com.jjmc.chromashift.environment.enemy;

import com.badlogic.gdx.math.Vector2;
import com.jjmc.chromashift.player.Player;

/**
 * Manages the interaction between a Tentacle and the Player,
 * specifically handling the capture mechanic and movement constraints.
 * 
 * Capture Requirements:
 * - Tentacle must form a closed curl (hasFullCurl() == true)
 * - Player center must be inside the curl circle (center + radius)
 * - Can only capture once per curl cycle
 * - Releases after 3 hits or when tentacle dies
 */
public class TentacleCapture {
    private final Tentacle tentacle;
    private final Player player;
    private final Vector2 tempVec = new Vector2();
    private final Vector2 playerPos = new Vector2();
    private final Vector2 playerCenter = new Vector2();

    private boolean wasCapturedPrevFrame = false;

    // Tuner for debug/tweaking if needed (placeholder)
    // private Tuner tuner;

    // Timer for periodic damage while player is captured
    private float captureDamageTimer = 0f;
    private float captureDamageInterval = 0.333f; // ~20 frames at 60fps

    public TentacleCapture(Tentacle tentacle, Player player) {
        this.tentacle = tentacle;
        this.player = player;
    }

    public void update(float delta) {
        // Get current player position (center of hitbox)
        playerPos.set(player.getX() + player.getHitboxWidth() / 2f,
                player.getY() + player.getHitboxHeight() / 2f);
        playerCenter.set(playerPos);
        float playerRadius = Math.max(player.getHitboxWidth(), player.getHitboxHeight()) / 2f;

        // --- 1. Update Tentacle Target ---
        // If not captured, track the player normally
        // If captured, lock player at curl center to maintain the curl
        boolean nowCaptured = tentacle.isPlayerCaptured();
        // Sync player capture state early (will be re-set after new capture or release)
        player.setCapturedByTentacle(nowCaptured);
        
        if (!nowCaptured) {
            tentacle.update(delta, playerPos.x, playerPos.y);
        } else {
            // When captured, lock player movement and keep tentacle targeting curl center
            Vector2 captureCenter = tentacle.getCurlCenter();
            tentacle.update(delta, captureCenter.x, captureCenter.y);
            
            // Lock player at center (no stun needed - position override is enough)
            player.setX(captureCenter.x - player.getHitboxWidth() / 2f);
            player.setY(captureCenter.y - player.getHitboxHeight() / 2f);
            player.setVelocityX(0f);
            player.setVelocityY(0f);
        }

        // --- 2. Check for New Capture (Strict Curl Detection) ---
        // Only capture if tentacle can capture AND player is fully inside curl circle
        if (tentacle.canCapture()) {
            Vector2 curlCenter = tentacle.getCurlCenter();
            float curlRadius = tentacle.getCurlRadius();
            
            // Calculate distance from player center to curl center
            float distToCenter = playerCenter.dst(curlCenter);
            
            // Player must be fully inside the curl (distance + playerRadius < curlRadius)
            if (distToCenter + playerRadius < curlRadius) {
                // Valid capture: closed loop fully encloses player
                // Do NOT reset tentacle health here; previous logic caused perpetual
                // re-captures each frame (health reset cleared capture flags allowing immediate re-capture),
                // making damage appear to do nothing while captured.
                tentacle.setPlayerCaptured(true); // sets captureHitCounter=0 & hasCapturedThisCurl=true
                nowCaptured = true;

                // Lock player at curl center immediately
                player.setX(curlCenter.x - player.getHitboxWidth() / 2f);
                player.setY(curlCenter.y - player.getHitboxHeight() / 2f);
                player.setVelocityX(0f);
                player.setVelocityY(0f);
                player.setCapturedByTentacle(true);
            }
        }

        // --- 3. Release Check ---
        // Release player if tentacle is no longer alive or after 3 hits
        if (nowCaptured && (!tentacle.isAlive() || tentacle.getHitsThisCapture() >= 3)) {
            tentacle.releasePlayer();
            player.setCapturedByTentacle(false);
            nowCaptured = false;
            captureDamageTimer = 0f; // reset damage timer
        }

        wasCapturedPrevFrame = nowCaptured;
        // Final sync of capture state
        player.setCapturedByTentacle(nowCaptured);

        // --- 4. Periodic damage while captured ---
        if (nowCaptured && tentacle.isAlive()) {
            captureDamageTimer += delta;
            if (captureDamageTimer >= captureDamageInterval) {
                captureDamageTimer -= captureDamageInterval;
                int dmg = 5 + (int)(Math.random() * 3); // 5-7 damage
                try {
                    if (player.getHealthSystem() != null) {
                        player.getHealthSystem().damage(dmg, tentacle);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Get the tentacle managed by this capture handler.
     * @return The tentacle instance
     */
    public Tentacle getTentacle() {
        return tentacle;
    }
}

package com.jjmc.chromashift.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.environment.Wall;

public class PlayerLogic {
    public static void handleAttackCooldown(Player player, float delta) {
        if (player.attackCooldownTimer > 0) player.attackCooldownTimer -= delta;
        if (player.airAttacking) {
            player.airAttackTimer -= delta;
            if (player.airAttackTimer <= 0f) player.airAttacking = false;
        }
        if (player.dashCooldownTimer > 0) player.dashCooldownTimer -= delta;
    }

    public static void handleDash(Player player, float delta, Array<Wall> walls) {
        if (!player.isDashing()) return;
        
        player.dashTimer -= delta;
        float dir = player.isFacingLeft() ? -1f : 1f;
        
        // Store position before dash movement
        Rectangle beforeDash = player.getHitboxRect();
        float oldX = player.getX();
        
        // Apply dash movement
        player.setX(player.getX() + dir * player.getConfig().dashSpeed * delta);
        
        // Resolve collision and update position
        Rectangle afterDash = player.getHitboxRect();
        PlayerCollision.resolveWallCollision(afterDash, walls);
        player.setX(player.getX() + afterDash.x - beforeDash.x);
        
        // If we hit a wall (barely moved), end dash
        if (Math.abs(player.getX() - oldX) < player.getConfig().dashSpeed * delta * 0.5f) {
            player.dashTimer = 0f;
            player.setDashing(false);
            player.dashCooldownTimer = player.getConfig().dashCooldown;
            player.dashUsed = true;
        }
        
        player.setVelocityY(0f);
        player.getAnim().play("dash", player.isFacingLeft());
        
        if (player.dashTimer <= 0f) {
            player.setDashing(false);
            player.dashCooldownTimer = player.getConfig().dashCooldown;
            player.dashUsed = true;
        }
    }

    public static void handleGroundMovement(Player player, float delta, Array<Wall> walls) {
        if (player.isAttacking()) return;

        Rectangle beforeMove = player.getHitboxRect();
        float oldX = player.getX();
        
        if (Gdx.input.isKeyPressed(player.getKeyLeft())) {
            player.setX(player.getX() - player.getConfig().speed * delta);
            player.setFacingLeft(true);
            player.setMoving(true);
        } else if (Gdx.input.isKeyPressed(player.getKeyRight())) {
            player.setX(player.getX() + player.getConfig().speed * delta);
            player.setFacingLeft(false);
            player.setMoving(true);
        }
        
        // After movement, check and resolve collisions
        Rectangle afterMove = player.getHitboxRect();
        PlayerCollision.resolveWallCollision(afterMove, walls);
        
        // Update player position based on resolved hitbox, but preserve some movement
        float intendedMove = player.getX() - oldX;
        float actualMove = afterMove.x - beforeMove.x;
        
        if (Math.abs(intendedMove) > 0 && Math.abs(actualMove) < 0.01f) {
            player.setX(oldX + (intendedMove * 0.1f));
        } else {
            player.setX(player.getX() + actualMove);
        }
    }

    public static void handleJump(Player player) {
        if (!player.isAttacking() && Gdx.input.isKeyJustPressed(player.getKeyJump()) && player.canJump) {
            player.setVelocityY(player.getConfig().jumpForce);
            player.setOnGround(false);
            player.canJump = false;
        }
    }

    public static void handleAttack(Player player, float delta) {
        if (!player.isAttacking() && 
            Gdx.input.isKeyJustPressed(player.getKeyAttack()) && 
            !player.isWallSliding() && 
            !player.isDashing() && 
            player.attackCooldownTimer <= 0f) {
            
            player.setAttacking(true);
            player.getAnim().play("attack", player.isFacingLeft());
            player.attackCooldownTimer = player.getConfig().attackCooldown;
            
            if (!player.isOnGround()) {
                player.airAttacking = true;
                player.airAttackTimer = player.getConfig().airAttackDuration;
                player.setVelocityY(0f);
                float d = player.isFacingLeft() ? -1f : 1f;
                player.setX(player.getX() + d * player.getConfig().airAttackLungeSpeed * delta);
            }
        }
    }

    public static void handleVerticalMovement(Player player, float delta, Array<Wall> walls) {
        if (!player.isOnGround() && !player.isWallSliding()) { 
            Rectangle beforeMove = player.getHitboxRect();
            player.setVelocityY(player.getVelocityY() + player.getConfig().gravity * delta);
            
            // Terminal velocity to prevent excessive speed
            float maxFallSpeed = -400f;
            if (player.getVelocityY() < maxFallSpeed) player.setVelocityY(maxFallSpeed);
            
            player.setY(player.getY() + player.getVelocityY() * delta);
            
            // After vertical movement, check and resolve collisions
            Rectangle afterMove = player.getHitboxRect();
            PlayerCollision.resolveWallCollision(afterMove, walls);
            
            // Update player position based on resolved hitbox
            float yDiff = afterMove.y - beforeMove.y;
            player.setY(player.getY() + yDiff);
            
            // Handle ceiling and floor collisions
            if (player.getVelocityY() > 0 && Math.abs(yDiff) < Math.abs(player.getVelocityY() * delta * 0.5f)) {
                player.setVelocityY(0);
            }
            if (player.getVelocityY() < 0 && Math.abs(yDiff) < Math.abs(player.getVelocityY() * delta * 0.5f)) {
                player.setVelocityY(0);
            }
        }
        else if (player.isWallSliding()) { 
            Rectangle beforeMove = player.getHitboxRect();
            player.setVelocityY(player.getConfig().wallSlideSpeed);
            player.setY(player.getY() + player.getVelocityY() * delta);
            
            Rectangle afterMove = player.getHitboxRect();
            PlayerCollision.resolveWallCollision(afterMove, walls);
            player.setY(player.getY() + afterMove.y - beforeMove.y);
        }
        else {
            player.setVelocityY(0f);
        }
    }

    public static void handlePlatformLanding(Player player, Array<Wall> walls) {
        Wall landingWall = null;
        float closestDistance = Float.MAX_VALUE;

        // Find the best wall to land on
        for (Wall w : walls) {
            float feetY = player.getHitboxY();
            float platformY = w.bounds.y + w.bounds.height;
            
            boolean withinPlatformX = player.getHitboxX() + player.getHitboxWidth() - 2f > w.bounds.x && 
                                    player.getHitboxX() + 2f < w.bounds.x + w.bounds.width;
            
            float verticalDistance = Math.abs(feetY - platformY);
            float verticalThreshold = player.isWallSliding() ? 6f : 4f;
            
            if (withinPlatformX && verticalDistance <= verticalThreshold && verticalDistance < closestDistance) {
                closestDistance = verticalDistance;
                landingWall = w;
            }
        }
        
        if (landingWall != null) {
            float platformY = landingWall.bounds.y + landingWall.bounds.height;
            
            if (player.getVelocityY() <= 0 || closestDistance < 2f) {
                player.setY(platformY);
                player.groundedBySolid = true;
                player.setVelocityY(0f);
                player.setOnGround(true);
                player.canJump = true;
                player.setWallSliding(false);
                player.setOnWall(false);
                
                // Only adjust position if very close to edges
                float leftEdgeDist = Math.abs(player.getHitboxX() - landingWall.bounds.x);
                float rightEdgeDist = Math.abs((player.getHitboxX() + player.getHitboxWidth()) - 
                                             (landingWall.bounds.x + landingWall.bounds.width));
                
                if (leftEdgeDist < 2f) {
                    player.setX(landingWall.bounds.x + 2f - player.hitboxOffsetX);
                } else if (rightEdgeDist < 2f) {
                    player.setX(landingWall.bounds.x + landingWall.bounds.width - 
                              (player.hitboxWidth + player.hitboxOffsetX) - 2f);
                }
            }
        }
    }

    public static void handleGroundCheck(Player player, float groundY) {
        if (player.getY() <= groundY) {
            player.setY(groundY);
            player.setVelocityY(0f);
            player.setOnGround(true);
            player.canJump = true;
            player.setWallSliding(false);
            player.setOnWall(false);
        }
    }

    public static void updateWallState(Player player, Array<Wall> walls) {
        player.updateWallSensor();
        boolean colliding = PlayerCollision.checkWallCollision(player.getWallSensor(), walls);
        player.setOnWall(colliding && !player.isOnGround());
        player.setWallSliding(player.isOnWall() && player.getVelocityY() < 0f);
    }

    public static void handleWallJump(Player player) {
        if (player.isWallSliding() && Gdx.input.isKeyJustPressed(player.getKeyJump())) {
            player.setWallSliding(false);
            player.setOnWall(false);
            player.setOnGround(false);
            player.canJump = false;
            player.setVelocityY(player.getConfig().jumpForce);
            player.setX(player.getX() + (player.isFacingLeft() ? 
                       player.getConfig().wallJumpForceX : -player.getConfig().wallJumpForceX) * 0.016f);
            player.getAnim().play("jump", player.isFacingLeft());
        }
    }

    public static void handleAnimationState(Player player) {
        if (player.isAttacking() && player.getAnim().isAnimationFinished()) {
            player.setAttacking(false);
            player.airAttacking = false;
        }
        
        if (player.isAttacking()) {
            if (!"attack".equals(player.getAnim().getCurrentAnimationName())) {
                player.getAnim().play("attack", player.isFacingLeft());
            }
            return;
        }
        
        if (player.isWallSliding()) {
            player.getAnim().play("wallslide", !player.isFacingLeft());
        } else if (player.isOnGround()) {
            player.getAnim().play(player.isMoving() ? "run" : "idle", player.isFacingLeft());
        } else {
            float velocityY = player.getVelocityY();
            player.getAnim().play(Math.abs(velocityY) > 1f ? 
                                (velocityY > 0f ? "jump" : "fall") : "fall", 
                                player.isFacingLeft());
        }
    }
}

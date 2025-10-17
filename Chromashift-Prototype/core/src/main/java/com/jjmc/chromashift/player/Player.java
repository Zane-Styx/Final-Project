package com.jjmc.chromashift.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.chromashift.helper.SpriteAnimator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Color;

/**
 * Handles player movement, physics, animations, and combat.
 * Includes dash mechanics with midair jump cancel, gravity suppression, and grounded cooldown reset.
 *
 * Multiplayer notes:
 *  - By default a Player is local (reads input). Call setLocal(false) for remote players
 *    and update their position via setPosition(...) or applyState(...).
 */
public class Player {
    private SpriteAnimator anim;

    // Input keys
    private final int keyLeft, keyRight, keyJump, keyAttack;
    private final int keyDash = Input.Keys.SHIFT_LEFT;

    // Position and physics
    private float x, y;
    private float velocityY = 0f;
    private boolean onGround = true;
    private boolean facingLeft = false;
    private boolean attacking = false;
    private boolean dashing = false;
    private boolean canJump = true;

    // Dash properties
    private float dashSpeed = 500f;
    private float dashTime = 0.15f;
    private float dashTimer = 0f;
    private float dashCooldown = 1f;
    private boolean dashUsed = false;
    private float dashCooldownTimer = 0f;

    // Gameplay tuning
    private float speed, jumpForce, gravity;

    // Sizes
    private final float baseWidth, baseHeight;
    private final float attackWidth, attackHeight;
    private PlayerType type;
    private int rows, cols, attackFrames;

    // --- Custom hitbox ---
    private float hitboxOffsetX = 0f;
    private float hitboxOffsetY = 0f;
    private float hitboxWidth = 32f;
    private float hitboxHeight = 32f;

    // --- Multiplayer control flag ---
    // true = this player is controlled locally (reads input). false = remote (driven by network).
    private boolean local = true;

    public Player(float startX, float startY,
                  int keyLeft, int keyRight, int keyJump, int keyAttack,
                  PlayerType type,
                  int rows, int cols,
                  int attackFrameW, int attackFrameH, int attackFrames,
                  float speed, float jumpForce, float gravity) {

        this.x = startX;
        this.y = startY;
        this.keyLeft = keyLeft;
        this.keyRight = keyRight;
        this.keyJump = keyJump;
        this.keyAttack = keyAttack;
        this.speed = speed;
        this.jumpForce = jumpForce;
        this.gravity = gravity;

        this.baseWidth = 32f;
        this.baseHeight = 32f;
        this.attackWidth = attackFrameW;
        this.attackHeight = attackFrameH;
        this.type = type;

        this.rows = rows;
        this.cols = cols;
        this.attackFrames = attackFrames;

        anim = new SpriteAnimator(this.type.getSpritePath(), rows, cols);
        anim.addAnimation("run", 0, 0, 9, 0.1f, true);
        anim.addAnimation("jump", 1, 0, 9, 0.1f, true);
        anim.addAnimation("fall", 2, 0, 9, 0.1f, true);
        anim.addAnimation("idle", 3, 0, 1, 0.1f, true);
        anim.addAnimation("wallslide", 4, 0, 9, 0.1f, true);
        anim.addAnimation("dash", 5, 0, 9, 0.05f, false);
        anim.addAnimationFromTexture("attack", type.getAttackSpritePath(), (int)attackFrameW, (int)attackFrameH, attackFrames, 0.08f, false);
    }

    /**
     * Mark player as local (reads input) or remote (driven by setPosition/applyState).
     */
    public void setLocal(boolean isLocal) {
        this.local = isLocal;
    }

    public boolean isLocal() {
        return local;
    }

    /**
     * Apply a server-authoritative state for this player.
     * Useful when receiving Network.PlayerState.
     * This does not run any physics locally — it simply applies values.
     */
    public void applyState(float nx, float ny, float nvx, float nvy, boolean onGround, boolean isDashing, boolean isAttacking, boolean facingLeft) {
        this.x = nx;
        this.y = ny;
        this.velocityY = nvy;
        this.onGround = onGround;
        this.dashing = isDashing;
        this.attacking = isAttacking;
        this.facingLeft = facingLeft;
    }    /**
     * Set position directly (used by networking code)
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Main update method. If this player is local, it processes input & physics.
     * If remote, it only advances animations.
     */
    public void update(float delta, float groundY) {
        boolean moving = false;

        // If player is remote, skip input/physics and only update animations
        if (!local) {
            // advance animation time so remote still animates
            anim.update(delta);
            return;
        }

        // --- LOCAL PLAYER: handle cooldowns, dash, input, physics (existing logic kept) ---

        // Cooldown handling
        if (dashUsed) {
            dashCooldownTimer -= delta;
            if (dashCooldownTimer <= 0 && onGround) {
                dashUsed = false;
                dashCooldownTimer = 0;
            }
        }

        // --- DASH HANDLING ---
        if (dashing) {
            dashTimer -= delta;

            if (Gdx.input.isKeyJustPressed(keyJump)) {
                dashing = false;
                dashUsed = true;
                velocityY = jumpForce;
                onGround = false;
                canJump = false;
                anim.play("jump", facingLeft);
            } else {
                float dashDir = facingLeft ? -1 : 1;
                x += dashDir * dashSpeed * delta;
                velocityY = 0;
                anim.play("dash", facingLeft);

                if (dashTimer <= 0) {
                    dashing = false;
                    dashUsed = true;
                    dashCooldownTimer = dashCooldown;
                }
            }

            anim.update(delta);
            return;
        }

        // --- INPUT & MOVEMENT ---
        if (!attacking) {
            if (Gdx.input.isKeyPressed(keyLeft)) {
                x -= speed * delta;
                facingLeft = true;
                moving = true;
            } else if (Gdx.input.isKeyPressed(keyRight)) {
                x += speed * delta;
                facingLeft = false;
                moving = true;
            }

            if (Gdx.input.isKeyJustPressed(keyJump) && canJump) {
                velocityY = jumpForce;
                onGround = false;
                canJump = false;
            }

            if (Gdx.input.isKeyJustPressed(keyAttack) && !attacking) {
                attacking = true;
                anim.play("attack", facingLeft);
            }

            if (Gdx.input.isKeyJustPressed(keyDash) && !dashUsed) {
                dashing = true;
                dashTimer = dashTime;
                anim.play("dash", facingLeft);

                canJump = true;
                velocityY = 0;
                return;
            }
        }

        // gravity & ground
        velocityY += gravity * delta;
        y += velocityY * delta;

        if (y <= groundY) {
            y = groundY;
            velocityY = 0;
            onGround = true;
            canJump = true;
            dashUsed = false;
        } else {
            onGround = false;
        }

        anim.update(delta);

        if (attacking && anim.isAnimationFinished()) attacking = false;

        if (attacking) {
            if (!"attack".equals(anim.getCurrentAnimationName())) {
                anim.play("attack", facingLeft);
            }
            return;
        }

        if (!onGround) {
            if (velocityY > 0) anim.play("jump", facingLeft);
            else anim.play("fall", facingLeft);
        } else if (moving) {
            anim.play("run", facingLeft);
        } else {
            anim.play("idle", facingLeft);
        }
    }

    public void render(SpriteBatch batch) {
        String cur = anim.getCurrentAnimationName();
        float width = "attack".equals(cur) ? attackWidth : baseWidth;
        float height = baseHeight;
        float drawX = x;

        if ("attack".equals(cur) && facingLeft) {
            drawX = x - (width - baseWidth);
        }

        anim.render(batch, drawX, y, width, height);
    }

    public void reloadAnimator(PlayerType type) {
        this.type = type;
        anim.dispose();

        SpriteAnimator newAnim = new SpriteAnimator(type.getSpritePath(), rows, cols);
        newAnim.addAnimation("run", 0, 0, 9, 0.1f, true);
        newAnim.addAnimation("jump", 1, 0, 9, 0.1f, true);
        newAnim.addAnimation("fall", 2, 0, 9, 0.1f, true);
        newAnim.addAnimation("idle", 3, 0, 1, 0.1f, true);
        newAnim.addAnimation("wallslide", 4, 0, 9, 0.1f, true);
        newAnim.addAnimation("dash", 5, 0, 9, 0.05f, false);
        newAnim.addAnimationFromTexture("attack", type.getAttackSpritePath(), (int) attackWidth, (int) attackHeight, attackFrames, 0.08f, false);
        this.anim = newAnim;
    }

    // --- Adjustable parameters ---
    public void setDashSpeed(float dashSpeed) { this.dashSpeed = dashSpeed; }
    public void setDashTime(float dashTime) { this.dashTime = dashTime; }
    public void setSpeed(float speed) { this.speed = speed; }
    public void setJumpForce(float jumpForce) { this.jumpForce = jumpForce; }
    public void setGravity(float gravity) { this.gravity = gravity; }
    public void setPlayerColor(PlayerType type) { this.type = type; }
    public void setIsDashing(boolean isDashing) { this.dashing = isDashing; }
    public void setIsAttacking(boolean isAttacking) { this.attacking = isAttacking; }
    public void setCanJump(boolean canJump) { this.canJump = canJump; }

    // --- Networking / utility setters (kept for multiplayer) ---
    public void setPositionDirect(float x, float y) { setPosition(x, y); }
    public void setVelocity(float vx, float vy) { this.velocityY = vy; }
    public void setFacingLeft(boolean facingLeft) { this.facingLeft = facingLeft; }
    public void setOnGround(boolean onGround) { this.onGround = onGround; }

    // --- Hitbox configuration ---
    public void setHitbox(float offsetX, float offsetY, float width, float height) {
        this.hitboxOffsetX = offsetX;
        this.hitboxOffsetY = offsetY;
        this.hitboxWidth = width;
        this.hitboxHeight = height;
    }

    public float getHitboxX() { return x + hitboxOffsetX; }
    public float getHitboxY() { return y + hitboxOffsetY; }
    public float getHitboxWidth() { return hitboxWidth; }
    public float getHitboxHeight() { return hitboxHeight; }
    // --- Add these methods ---
    public int getKeyLeft() { return keyLeft; }
    public int getKeyRight() { return keyRight; }
    public int getKeyJump() { return keyJump; }
    public int getKeyAttack() { return keyAttack; }

    public SpriteAnimator getAnimator() { return anim; }
    public float getX() { return x; }
    public float getY() { return y; }
    public boolean isAttacking() { return attacking; }
    public boolean isOnGround() { return onGround; }
    public boolean isFacingLeft() { return facingLeft; }
    public boolean isDashing() { return dashing; }

    public void dispose() { anim.dispose(); }

    /**
     * Draws the player's hitbox for debugging.
     * Call this after batch.end() and before batch.begin() (since ShapeRenderer uses its own pipeline).
     */
    public void debugDrawHitbox(ShapeRenderer shape) {
        shape.setColor(new Color(1f, 0f, 0f, 0.4f)); // semi-transparent red
        shape.rect(
            getHitboxX(),
            getHitboxY(),
            getHitboxWidth(),
            getHitboxHeight()
        );
    }
}

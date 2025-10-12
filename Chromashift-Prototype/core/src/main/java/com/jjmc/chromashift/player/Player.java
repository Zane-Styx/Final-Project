package com.jjmc.chromashift.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.chromashift.helper.SpriteAnimator;

/**
 * Encapsulates a player: input, physics, animations, and rendering.
 * Supports dynamic speed/jump/gravity changes at runtime.
 */
public class Player {
    private SpriteAnimator anim;

    // Input keys
    private final int keyLeft, keyRight, keyJump, keyAttack;

    // Position and physics
    private float x, y;
    private float velocityY = 0f;
    private boolean onGround = true;
    private boolean facingLeft = false;
    private boolean attacking = false;

    // Gameplay tuning (can be updated at runtime)
    private float speed, jumpForce, gravity;

    // Sizes per animation
    private final float baseWidth, baseHeight;
    private final float attackWidth, attackHeight;
    private PlayerType type;

    private int rows, cols, attackFrames;

    /**
     * Constructor using PlayerType.
     *
     * @param startX        Initial X position
     * @param startY        Initial Y position
     * @param keyLeft       Keycode to move left
     * @param keyRight      Keycode to move right
     * @param keyJump       Keycode to jump
     * @param keyAttack     Keycode to attack
     * @param type          PlayerType enum (provides base/attack sprite paths)
     * @param rows          Rows in base sprite sheet
     * @param cols          Columns in base sprite sheet
     * @param attackFrameW  Width of a single attack frame
     * @param attackFrameH  Height of a single attack frame
     * @param attackFrames  Number of attack frames
     * @param speed         Movement speed
     * @param jumpForce     Jump force
     * @param gravity       Gravity
     */
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

        // Sizes
        this.baseWidth = 32f;
        this.baseHeight = 32f;
        this.attackWidth = attackFrameW;
        this.attackHeight = attackFrameH;
        this.type = type;

        this.attackFrames = attackFrames;
        this.rows = rows;
        this.cols = cols;
        // SpriteAnimator setup using PlayerType paths
        anim = new SpriteAnimator(this.type.getSpritePath(), rows, cols);

        // Base grid animations
        anim.addAnimation("run", 0, 0, 9, 0.1f, true);
        anim.addAnimation("jump", 1, 0, 9, 0.1f, true);
        anim.addAnimation("fall", 2, 0, 9, 0.1f, true);
        anim.addAnimation("idle", 3, 0, 1, 0.1f, true);
        anim.addAnimation("wallslide", 4, 0, 9, 0.1f, true);
        anim.addAnimation("dash", 5, 0, 9, 0.1f, true);

        // Attack animation from PlayerType attack path
        anim.addAnimationFromTexture("attack", type.getAttackSpritePath(), attackFrameW, attackFrameH, attackFrames, 0.08f, false);
    }

    /** Update method called every frame */
    public void update(float delta, float groundY) {
        boolean moving = false;

        // input & movement (skip movement while attacking)
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

            if (Gdx.input.isKeyJustPressed(keyJump) && onGround) {
                velocityY = jumpForce;
                onGround = false;
            }

            if (Gdx.input.isKeyJustPressed(keyAttack) && !attacking) {
                attacking = true;
                anim.play("attack", facingLeft);
            }
        }

        // gravity
        velocityY += gravity * delta;
        y += velocityY * delta;

        if (y <= groundY) {
            y = groundY;
            velocityY = 0;
            onGround = true;
        }

        // animation update
        anim.update(delta);

        // clear attacking flag when finished
        if (attacking && anim.isAnimationFinished()) attacking = false;

        // if attacking, don't override animation
        if (attacking) {
            if (!"attack".equals(anim.getCurrentAnimationName())) {
                anim.play("attack", facingLeft);
            }
            return;
        }

        // choose proper animation
        if (!onGround) {
            if (velocityY > 0) anim.play("jump", facingLeft);
            else anim.play("fall", facingLeft);
        } else if (moving) {
            anim.play("run", facingLeft);
        } else {
            anim.play("idle", facingLeft);
        }
    }

    /** Render left-aligned */
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

        // recreate animator
        SpriteAnimator newAnim = new SpriteAnimator(type.getSpritePath(), rows, cols);
        newAnim.addAnimation("run", 0, 0, 9, 0.1f, true);
        newAnim.addAnimation("jump", 1, 0, 9, 0.1f, true);
        newAnim.addAnimation("fall", 2, 0, 9, 0.1f, true);
        newAnim.addAnimation("idle", 3, 0, 1, 0.1f, true);
        newAnim.addAnimation("wallslide", 4, 0, 9, 0.1f, true);
        newAnim.addAnimation("dash", 5, 0, 9, 0.1f, true);
        newAnim.addAnimationFromTexture("attack", type.getAttackSpritePath(), (int)attackWidth, (int)attackHeight, attackFrames, 0.08f, false);

        // swap anim
        this.anim = newAnim;
    }

    /** Dynamically adjust speed at runtime */
    public void setSpeed(float speed) { this.speed = speed; }
    public void setJumpForce(float jumpForce) { this.jumpForce = jumpForce; }
    public void setGravity(float gravity) { this.gravity = gravity; }
    public void setPlayerColor(PlayerType type) {
        this.type = type;
    }

    public SpriteAnimator getAnimator() { return anim; }
    public float getX() { return x; }
    public float getY() { return y; }
    public boolean isAttacking() { return attacking; }
    public boolean isOnGround() { return onGround; }
    public boolean isFacingLeft() { return facingLeft; }

    public void dispose() { anim.dispose(); }
}

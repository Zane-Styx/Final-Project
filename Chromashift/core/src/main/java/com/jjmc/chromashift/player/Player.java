package com.jjmc.chromashift.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.environment.interactable.Box;
import com.jjmc.chromashift.environment.interactable.Orb;
import com.jjmc.chromashift.healthsystem.HealthListener;
import com.jjmc.chromashift.healthsystem.HealthSystem;
import com.chromashift.helper.SpriteAnimator;
import com.chromashift.helper.SoundManager;
import com.jjmc.chromashift.environment.Wall;
import com.jjmc.chromashift.environment.interactable.Interactable;
import com.jjmc.chromashift.environment.interactable.Pickable;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.ui.PlayerUI;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jjmc.chromashift.player.sfx.PlayerSFX;

/**
 * Cleaned Player implementation — single, consistent class.
 */
public class Player {
    private SpriteAnimator anim;
    private PlayerSFX sfx;

    // Input keys
    private final int keyLeft, keyRight, keyJump, keyAttack;
    private final int keyDash = Input.Keys.SHIFT_LEFT;

    // Position and physics
    private float x, y;
    private float velocityX = 0f;
    private float velocityY = 0f;
    private boolean onGround = true;
    private boolean facingLeft = false;
    private boolean dashing = false;
    protected boolean canJump = true;
    private boolean moving = false;
    private com.badlogic.gdx.graphics.Camera gameCamera;
    // Persistent UI overlay for this player
    private PlayerUI playerUI;

    // Timers / state
    protected float dashTimer = 0f;
    protected float dashCooldownTimer = 0f;
    protected boolean dashUsed = false;
    // Short hover after skill-based dash to prevent instant gravity snap (seconds)
    protected float dashHoverRemaining = 0f;

    public void setCamera(com.badlogic.gdx.graphics.Camera camera) {
        this.gameCamera = camera;
        // Also set camera for held object if any
        if (heldObject instanceof Box box) {
            box.setCamera(camera);
        } else if (heldObject instanceof Orb orb) {
            orb.setCamera(camera);
        }
        // Camera change may imply viewport change; caller should reattach viewport if
        // needed
    }

    private boolean attacking = false;
    protected boolean airAttacking = false;
    protected float airAttackTimer = 0f;
    protected float attackCooldownTimer = 0f;

    // Walking sound state
    private boolean walkingSoundPlaying = false;

    // Wall
    private boolean onWall = false;
    private boolean wallSliding = false;
    private Circle wallSensor;
    private Circle backSensor;

    // Config
    private final PlayerConfig config;
    protected final float baseWidth, baseHeight;
    public float hitboxOffsetX;
    public float hitboxOffsetY;
    protected float hitboxWidth;
    protected float hitboxHeight;

    protected boolean groundedBySolid = false;
    private PlayerType type;
    protected int rows, cols, attackFrames;
    protected int attackFrameW, attackFrameH;

    // Shield / Armor
    private int shield = 0;
    private final int maxShield = 3;
    // Keys
    private int keyCount = 0;
    
    // Potions
    private int potionCount = 0;
    private static final int POTION_HEAL_AMOUNT = 30;
    private static final int MAX_POTIONS = 9;

    // Enemy tracking for melee attacks
    private Array<com.jjmc.chromashift.environment.enemy.Enemy> enemies = new Array<>();
    private AttackHitbox attackHitbox;
    
    // Solids tracking for skill collision detection
    private Array<com.jjmc.chromashift.environment.Solid> solids;
    
    // Animation state tracking to prevent resetting animations every frame
    private String lastAnimationName = null;
    private Boolean lastFlipX = null; // track last horizontal flip state
    // Capture state (tentacle) allowing attacks while immobilized
    private boolean capturedByTentacle = false;
    
    // Skill system
    private com.jjmc.chromashift.player.skill.BaseSkill skillSlotQ;
    private com.jjmc.chromashift.player.skill.BaseSkill skillSlotE;
    private com.jjmc.chromashift.player.skill.BaseSkill activeSkill;
    private boolean isInvulnerable = false;
    private boolean isInvisible = false;
    public Array<com.jjmc.chromashift.player.skill.Projectile> activeProjectiles = new Array<>();

    public Player(float startX, float startY,
            int keyLeft, int keyRight, int keyJump, int keyAttack,
            PlayerType type,
            int rows, int cols,
            int attackFrameW, int attackFrameH, int attackFrames,
            PlayerConfig config) {
        this.x = startX;
        this.y = startY;
        this.keyLeft = keyLeft;
        this.keyRight = keyRight;
        this.keyJump = keyJump;
        this.keyAttack = keyAttack;
        this.config = config;
        this.type = type;
        this.rows = rows;
        this.cols = cols;
        this.attackFrames = attackFrames;
        this.attackFrameW = attackFrameW;
        this.attackFrameH = attackFrameH;

        this.baseWidth = config.baseWidth;
        this.baseHeight = config.baseHeight;
        this.hitboxOffsetX = config.hitboxOffsetX;
        this.hitboxOffsetY = config.hitboxOffsetY;
        this.hitboxWidth = config.hitboxWidth;
        this.hitboxHeight = config.hitboxHeight;

        anim = new SpriteAnimator(this.type.getSpritePath(), rows, cols);
        anim.addAnimation("run", 0, 0, 9, 0.1f, true);
        anim.addAnimation("jump", 1, 0, 8, 0.1f, true);
        anim.addAnimation("fall", 2, 0, 8, 0.1f, true);
        anim.addAnimation("idle", 3, 0, 1, 0.1f, true);
        anim.addAnimation("wallslide", 4, 0, 8, 0.1f, true);
        anim.addAnimation("dash", 5, 0, 9, 0.05f, false);
        anim.addAnimationFromTexture("attack", type.getAttackSpritePath(), attackFrameW, attackFrameH, attackFrames,
                0.08f, false);

        wallSensor = new Circle(x, y, 5f);
        backSensor = new Circle(x, y, 3f);

        // Respawn defaults to initial spawn
        this.respawnX = startX;
        this.respawnY = startY;
        
        // Initialize attack hitbox
        this.attackHitbox = new AttackHitbox(this);
        
        // Initialize SFX handler
        this.sfx = new PlayerSFX();

        // Pointer animator for held-object throw direction indicator
        try {
            pointerAnimator = new com.chromashift.helper.SpriteAnimator("player/ui/ArrowPointer.png", 1, 5);
            pointerAnimator.addAnimation("point", 0, 0, 5, 0.1f, true);
            pointerAnimator.play("point", false);
        } catch (Exception ignored) {
            pointerAnimator = null;
        }

        // Initialize health system with shield logic
        this.health = new HealthSystem(200f) {
            @Override
            public boolean damage(float amount, Object source) {
                if (amount <= 0f)
                    return false;
                if (isInvulnerable())
                    return false;
                if (isDead())
                    return false;

                // Shield absorbs damage first
                if (shield > 0) {
                    shield--;
                    // Shield absorbs the hit completely
                    return true;
                }

                return super.damage(amount, source);
            }
        };
        // small auto-regen after 1.5s
        this.health.setRegenPerSecond(1f);
        this.health.setRegenDelayAfterDamage(1.5f);
        // Listen for death to respawn the player
        this.health.addListener(new HealthListener() {
            @Override
            public void onHealthChanged(HealthSystem hs, float delta, float current, float max) {
                // Optional: could hook damage flash, sound, UI here
            }

            @Override
            public void onDeath(HealthSystem hs, Object source) {
                // On death, respawn at the last respawn point
                respawn();
            }
        });
    }

    // Currently held pickable
    private com.jjmc.chromashift.environment.interactable.Pickable heldObject = null;
    // Pointer shown when holding an object (arrow sprite)
    private com.chromashift.helper.SpriteAnimator pointerAnimator;
    private float pointerOffsetY = 16f; // height above player to spawn pointer (adjustable)

    // Health system and respawn
    private final HealthSystem health;
    private float respawnX, respawnY;
    // temporary invulnerability and stun after respawn (seconds)
    private float respawnInvulDuration = 1.0f;
    private float respawnInvulRemaining = 0f;
    private float respawnStunDuration = 0.5f; // How long player can't move after respawn
    private float respawnStunRemaining = 0f;
    private boolean isStunned = false;

    public void update(float delta, float groundY, Array<Wall> walls) {
        // update health first (regen, timers)
        if (health != null)
            health.update(delta);

        // handle respawn timers
        if (respawnInvulRemaining > 0f) {
            respawnInvulRemaining -= delta;
            if (respawnInvulRemaining <= 0f) {
                health.setInvulnerable(false);
                respawnInvulRemaining = 0f;
            }
        }
        if (respawnStunRemaining > 0f) {
            respawnStunRemaining -= delta;
            if (respawnStunRemaining <= 0f) {
                isStunned = false;
                respawnStunRemaining = 0f;
            }
        }

        // Don't update movement if stunned (respawn stun only)
        if (!isStunned) {
            update(delta, groundY, walls, null);
        } else {
            // Still update animation while stunned (respawn only)
            anim.update(delta);
            setAnimation("idle", facingLeft);
        }
    }

    public void update(float delta, float groundY, Array<Wall> walls, Array<Solid> solids) {
        // Store solids reference for skill collision detection
        this.solids = solids;
        
        // update health (regen, timers)
        if (health != null)
            health.update(delta);

        // handle respawn timers
        if (respawnInvulRemaining > 0f) {
            respawnInvulRemaining -= delta;
            if (respawnInvulRemaining <= 0f) {
                health.setInvulnerable(false);
                respawnInvulRemaining = 0f;
            }
        }
        if (respawnStunRemaining > 0f) {
            respawnStunRemaining -= delta;
            if (respawnStunRemaining <= 0f) {
                isStunned = false;
                respawnStunRemaining = 0f;
            }
        }

        moving = false;
        groundedBySolid = false;

        PlayerLogic.handleAttackCooldown(this, delta);

        // Tick cooldowns for equipped skills (even when not active)
        if (skillSlotQ != null && skillSlotQ != activeSkill) skillSlotQ.update(delta);
        if (skillSlotE != null && skillSlotE != activeSkill) skillSlotE.update(delta);

        // Update active skill (if any)
        boolean skillLock = false;
        if (activeSkill != null) {
            activeSkill.update(delta);

            // Apply skill state overrides
            if (activeSkill.isRequestingInvulnerability()) {
                isInvulnerable = true;
            }
            if (activeSkill.isRequestingInvisibility()) {
                isInvisible = true;
            }

            // Movement lock requested by skill
            skillLock = activeSkill.isRequestingMovementLock();

            // If the skill finished, clear reference and flags (BaseSkill.deactivate already ran)
            if (!activeSkill.isActive()) {
                activeSkill = null;
                isInvulnerable = false;
                isInvisible = false;
            }
        } else {
            // Reset invulnerability/invisibility when no skill active
            isInvulnerable = false;
            isInvisible = false;
        }
        
        // Update projectiles
        if (activeProjectiles != null && !activeProjectiles.isEmpty()) {
            for (int i = activeProjectiles.size - 1; i >= 0; i--) {
                com.jjmc.chromashift.player.skill.Projectile proj = activeProjectiles.get(i);
                proj.update(delta, solids, enemies);
                if (proj.isFinished()) {
                    activeProjectiles.removeIndex(i);
                }
            }
        }
        
        // Handle skill key input (Q and E keys)
        if (!isStunned && Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            castSkill('Q');
        }
        if (!isStunned && Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            castSkill('E');
        }

        if (dashing && !isStunned) {
            PlayerLogic.handleDash(this, delta, walls, solids);
            anim.update(delta);
            return;
        }

        // Apply velocityX from external sources (e.g., launchpads) - applies in air or
        // on ground
        if (velocityX != 0f) {
            x += velocityX * delta;
            // No air friction: preserve horizontal momentum while airborne for clean
            // parabolic arcs
            float friction = onGround ? 0.75f : 1.0f;
            velocityX *= friction;
            if (Math.abs(velocityX) < 1f) {
                velocityX = 0f;
            }
        }

        // Movement & jump only when not stunned, not captured, and not locked by a skill
        if (!isStunned && !capturedByTentacle && !skillLock) {
            PlayerLogic.handleGroundMovement(this, delta, walls, solids);
            PlayerLogic.handleJump(this);
        }
        // Allow facing direction changes while captured (so attacks can aim)
        if (capturedByTentacle && !isStunned) {
            boolean inputLeft = Gdx.input.isKeyPressed(keyLeft);
            boolean inputRight = Gdx.input.isKeyPressed(keyRight);
            if (inputLeft && !inputRight) facingLeft = true;
            else if (inputRight && !inputLeft) facingLeft = false;
        }
        // Allow attacking when not stunned (including when captured)
        if (!isStunned) {
            PlayerLogic.handleAttack(this, delta);
        }

        if (!isStunned && !capturedByTentacle && !skillLock && Gdx.input.isKeyJustPressed(keyDash) && !dashing && dashCooldownTimer <= 0f && !dashUsed) {
            dashing = true;
            dashTimer = config.dashTime;
            setAnimation("dash", facingLeft);
            canJump = true;
            velocityY = 0f;
            // Cancel horizontal launch momentum if dashing in the opposite direction
            int dashDir = facingLeft ? -1 : 1;
            if ((dashDir < 0 && velocityX > 0f) || (dashDir > 0 && velocityX < 0f)) {
                velocityX = 0f;
            }
            updateWallSensor();
            // Play dash sound
            SoundManager.play("Dash");
            // Spawn dash SFX
            if (sfx != null) {
                sfx.spawnDashSFX(this);
            }
            return;
        }

        boolean hitSideWall = PlayerCollision.resolveWallCollision(getHitboxRect(), walls);
        if (solids != null) {
            hitSideWall = PlayerCollision.resolveSolidCollision(getHitboxRect(), solids) || hitSideWall;
        }
        // Check if back/side hits a wall and cancel horizontal velocity
        // Do not cancel when holding an object to preserve launch momentum while
        // carrying
        boolean backOrSideHit = PlayerCollision.checkWallCollision(backSensor, walls) || hitSideWall;
        if (backOrSideHit) {
            velocityX = 0f;
        }
        PlayerLogic.updateWallState(this, walls);
        PlayerLogic.handleWallJump(this);

        if (airAttacking) {
            anim.update(delta);
            updateWallSensor();
            return;
        }

        PlayerLogic.handleVerticalMovement(this, delta, walls, solids);
        PlayerLogic.handleGroundCheck(this, groundY);

        // Zero horizontal velocity during wallsliding
        if (wallSliding) {
            velocityX = 0f;
        }

        // Play walking sound if moving on ground (looped)
        if ((onGround || onWall) && isMoving() && !dashing) {
            if (!walkingSoundPlaying) {
                SoundManager.playLoopingSfx("Walking");
                walkingSoundPlaying = true;
            }
        } else {
            // Stop walking sound when not moving
            if (walkingSoundPlaying) {
                SoundManager.stopLoopingSfx("Walking");
                walkingSoundPlaying = false;
            }
        }

        if (onGround || onWall || wallSliding)
            dashUsed = false;

        // Update attack hitbox
        if (attackHitbox != null) {
            attackHitbox.update(delta);
            attackHitbox.checkEnemyCollisions(enemies);
        }
        
        // Update SFX
        if (sfx != null) {
            sfx.update(delta);
        }
        
        // Handle potion usage (H key)
        if (!isStunned && Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            usePotion();
        }
        
        anim.update(delta);
        if (isStunned) {
            setAnimation("idle", facingLeft);
        } else {
            PlayerLogic.handleAnimationState(this);
        }
    }

    public void update(float delta, float groundY, Array<Solid> solids, Array<Interactable> interactables, float x) {
        // Store solids reference for skill collision detection
        this.solids = solids;
        
        Array<Wall> walls = new Array<>();
        for (Solid s : solids) {
            if (!s.isBlocking())
                continue;
            if (s instanceof Wall w)
                walls.add(w);
        }
        groundedBySolid = false;

        // health handled in the chained update
        update(delta, groundY, walls, solids);

        if (onGround || onWall || wallSliding)
            dashUsed = false;

        // Handle interactables
        Rectangle playerHitbox = getHitboxRect();
        for (Interactable i : interactables) {
            i.checkInteraction(playerHitbox);
        }
        if (!isStunned && Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            for (Interactable i : interactables) {
                // Activate any interactable that reports it can be interacted with.
                // Buttons (pressure plates) should return false for canInteract()
                // so they won't be triggered by F; levers and similar will.
                if (i.canInteract()) {
                    i.interact();
                }
            }
        }
        // Pickup/throw with G key (only when not stunned)
        if (!isStunned && com.badlogic.gdx.Gdx.input.isButtonJustPressed(com.badlogic.gdx.Input.Buttons.RIGHT)) {
            if (heldObject != null) {
                // Get mouse position in world coordinates
                com.badlogic.gdx.math.Vector3 mousePos = new com.badlogic.gdx.math.Vector3(
                        Gdx.input.getX(), Gdx.input.getY(), 0);
                if (gameCamera != null) {
                    gameCamera.unproject(mousePos);
                }
                float mouseX = mousePos.x;
                float mouseY = mousePos.y;

                // Get player center position for throw origin
                float playerCenterX = getHitboxX() + getHitboxWidth() / 2f;
                float playerCenterY = getHitboxY() + getHitboxHeight() / 2f;

                // Calculate throw direction and velocity
                float dirX = mouseX - playerCenterX;
                float dirY = mouseY - playerCenterY;
                float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);

                if (len > 0.001f) {
                    // Normalize and scale to desired throw speed
                    float throwSpeed = 400f;
                    float vx = (dirX / len) * throwSpeed;
                    float vy = (dirY / len) * throwSpeed;
                    throwHeldWithVelocity(vx, vy);
                } else {
                    // If no direction, just drop it
                    heldObject.drop();
                    heldObject = null;
                }
            } else {
                // Try to pick up nearby object
                for (Interactable i : interactables) {
                    if (i instanceof Pickable p && i.canInteract()) {
                        p.pickUp(this);
                        heldObject = p;
                        break;
                    }
                }
            }
        }
        // pickup/throw handled by screen (needs camera/mouse); Player exposes
        // held-object API
    }

    public void update(float delta, float groundY, Array<Solid> solids, Array<Interactable> interactables,
            boolean dummy) {
        // Store solids reference for skill collision detection
        this.solids = solids;
        
        Array<Wall> walls = new Array<>();
        for (Solid s : solids) {
            if (!s.isBlocking())
                continue;
            if (s instanceof Wall w)
                walls.add(w);
        }
        groundedBySolid = false;

        // health handled in the chained update
        update(delta, groundY, walls, solids);

        if (onGround || onWall || wallSliding)
            dashUsed = false;

        // Handle interactables
        Rectangle playerHitbox = getHitboxRect();
        for (Interactable i : interactables) {
            i.checkInteraction(playerHitbox);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            for (Interactable i : interactables) {
                // Activate any interactable that reports it can be interacted with.
                // Buttons (pressure plates) should return false for canInteract()
                // so they won't be triggered by F; levers and similar will.
                if (i.canInteract()) {
                    i.interact();
                }
            }
        }

        // Check for enemy hits when attacking
        if (isAttacking() && anim.getCurrentFrameIndex() == 3) { // Hit frame at frame 3
            PlayerLogic.checkEnemyHits(this, enemies);
        }
    }

    /**
     * Set the enemies array for melee combat.
     * @param enemies Array of Enemy objects to track for attacks
     */
    public void setEnemies(Array<com.jjmc.chromashift.environment.enemy.Enemy> enemies) {
        this.enemies = enemies;
    }
    
    /**
     * Helper to set animation only when it changes (prevents resetting stateTime).
     */
    public void setAnimation(String name, boolean flip) {
        if (!name.equals(lastAnimationName) || lastFlipX == null || flip != lastFlipX) {
            anim.play(name, flip);
            lastAnimationName = name;
            lastFlipX = flip;
        }
    }
    
    /**
     * Perform a melee attack. Spawns attack hitbox that damages enemies.
     */
    public void attack() {
        if (attackHitbox != null) {
            attackHitbox.activate();
        }
    }
    
    /**
     * Get the attack hitbox for debug drawing.
     */
    public AttackHitbox getAttackHitbox() {
        return attackHitbox;
    }

    // Tentacle capture state accessors
    public boolean isCapturedByTentacle() {
        return capturedByTentacle;
    }
    public void setCapturedByTentacle(boolean captured) {
        this.capturedByTentacle = captured;
    }

    protected void updateWallSensor() {
        float centerX = x + hitboxOffsetX + hitboxWidth / 2f;
        float centerY = y + hitboxOffsetY + hitboxHeight / 2f;
        float offsetX = facingLeft ? -6f : 6f;
        wallSensor.setPosition(centerX + offsetX, centerY);
        wallSensor.setRadius(3f);

        // Update back sensor (opposite side from wall sensor)
        float backOffsetX = facingLeft ? 6f : -6f;
        backSensor.setPosition(centerX + backOffsetX, centerY);
        backSensor.setRadius(3f);
    }

    public void render(SpriteBatch batch) {
        // Skip player sprite if invisible from skill
        if (!isInvisible) {
            String cur = anim.getCurrentAnimationName();
            float width = "attack".equals(cur) ? attackFrameW : baseWidth;
            float height = baseHeight;
            float drawX = x;
            if ("attack".equals(cur) && facingLeft)
                drawX = x - (width - baseWidth);
            anim.render(batch, drawX, y, width, height);
        }
        
        // Render SFX on top of player layer
        if (sfx != null) {
            sfx.render(batch);
        }
        
        // Render active skill if present
        if (activeSkill != null) {
            activeSkill.render(batch);
        }

        // Render held-object pointer above player, rotating toward mouse
        if (heldObject != null && pointerAnimator != null) {
            try {
                // Advance pointer animation
                pointerAnimator.update(com.badlogic.gdx.Gdx.graphics.getDeltaTime());

                // Compute world mouse position
                com.badlogic.gdx.math.Vector3 mousePos = new com.badlogic.gdx.math.Vector3(
                    Gdx.input.getX(), Gdx.input.getY(), 0f);
                if (gameCamera != null) gameCamera.unproject(mousePos);
                float mouseX = mousePos.x;
                float mouseY = mousePos.y;

                // Spawn pointer above player's hitbox center
                float centerX = getHitboxX() + getHitboxWidth() / 2f;
                float centerY = getHitboxY() + getHitboxHeight() / 2f + pointerOffsetY;

                // Compute angle (degrees) where arrow default faces right
                float angle = com.badlogic.gdx.math.MathUtils.atan2(mouseY - centerY, mouseX - centerX) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;

                com.badlogic.gdx.graphics.g2d.TextureRegion region = pointerAnimator.getCurrentFrameRegion();
                if (region != null) {
                    float pw = 32f;
                    float ph = 32f;
                    // Anchor at left-center of sprite
                    float originX = 0f;
                    float originY = ph / 2f;
                    float drawX = centerX - originX;
                    float drawY = centerY - originY;
                    batch.draw(region, drawX, drawY, originX, originY, pw, ph, 1f, 1f, angle);
                }
            } catch (Exception ignored) {}
        }
        
        // Render active projectiles
        if (activeProjectiles != null && !activeProjectiles.isEmpty()) {
            for (com.jjmc.chromashift.player.skill.Projectile proj : activeProjectiles) {
                proj.render(batch);
            }
        }
        
        // Render persistent UI overlay after player
        if (playerUI != null) {
            playerUI.render(batch);
        }
    }

    // Hitbox helpers
    public void setHitbox(float offsetX, float offsetY, float width, float height) {
        this.hitboxOffsetX = offsetX;
        this.hitboxOffsetY = offsetY;
        this.hitboxWidth = width;
        this.hitboxHeight = height;
    }

    public float getHitboxX() {
        return x + hitboxOffsetX;
    }

    public float getHitboxY() {
        return y + hitboxOffsetY;
    }

    public float getHitboxWidth() {
        return hitboxWidth;
    }

    public float getHitboxHeight() {
        return hitboxHeight;
    }

    public Rectangle getHitboxRect() {
        return new Rectangle(getHitboxX(), getHitboxY(), getHitboxWidth(), getHitboxHeight());
    }

    // Getters
    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getVelocityX() {
        return velocityX;
    }

    public float getVelocityY() {
        return velocityY;
    }

    public boolean isFacingLeft() {
        return facingLeft;
    }

    public boolean isWallSliding() {
        return wallSliding;
    }

    public boolean isDashing() {
        return dashing;
    }

    public boolean isAttacking() {
        return attacking;
    }

    public boolean isAttackingJustNow() {
        return (attacking || airAttacking);
    }

    public boolean isOnGround() {
        return onGround;
    }

    public boolean isOnWall() {
        return onWall;
    }

    public boolean isMoving() {
        return moving;
    }

    public Circle getWallSensor() {
        return wallSensor;
    }

    public Circle getBackSensor() {
        return backSensor;
    }

    public PlayerConfig getConfig() {
        return config;
    }

    public SpriteAnimator getAnim() {
        return anim;
    }

    public int getKeyLeft() {
        return keyLeft;
    }

    public int getKeyRight() {
        return keyRight;
    }

    public int getKeyJump() {
        return keyJump;
    }

    public int getKeyAttack() {
        return keyAttack;
    }

    // Setters
    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setVelocityX(float velocityX) {
        if (config != null) {
            float max = Math.abs(config.maxHorizontalSpeed);
            if (velocityX > max) velocityX = max;
            if (velocityX < -max) velocityX = -max;
        }
        this.velocityX = velocityX;
    }

    public void setVelocityY(float velocityY) {
        this.velocityY = velocityY;
    }

    public void setFacingLeft(boolean facingLeft) {
        this.facingLeft = facingLeft;
    }

    public void setWallSliding(boolean wallSliding) {
        this.wallSliding = wallSliding;
    }

    public void setDashing(boolean dashing) {
        this.dashing = dashing;
    }

    public void setAttacking(boolean attacking) {
        this.attacking = attacking;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    /**
     * Set a short hover period where gravity is suppressed.
     * @param seconds hover duration in seconds
     */
    public void setDashHover(float seconds) {
        this.dashHoverRemaining = Math.max(0f, seconds);
    }

    public void setOnWall(boolean onWall) {
        this.onWall = onWall;
    }

    public void setMoving(boolean moving) {
        this.moving = moving;
    }
    
    // ============ SKILL SYSTEM ============
    
    public void equipSkillToSlot(com.jjmc.chromashift.player.skill.BaseSkill skill, char slot) {
        if (slot == 'Q') {
            skillSlotQ = skill;
        } else if (slot == 'E') {
            skillSlotE = skill;
        }
    }
    
    public void castSkill(char slot) {
        com.jjmc.chromashift.player.skill.BaseSkill skill = (slot == 'Q') ? skillSlotQ : skillSlotE;
        if (skill != null && skill.canCast() && activeSkill == null) {
            activeSkill = skill;
            skill.activate();
        }
    }

    public com.jjmc.chromashift.player.skill.BaseSkill getActiveSkill() {
        return activeSkill;
    }
    
    public Array<com.jjmc.chromashift.environment.Solid> getSolids() {
        return solids != null ? solids : new Array<>();
    }
    
    public Array<com.jjmc.chromashift.environment.enemy.Enemy> getEnemies() {
        return enemies;
    }
    
    public void setCanJump(boolean canJump) {
        this.canJump = canJump;
    }

    public void dispose() {
        anim.dispose();
        if (playerUI != null)
            playerUI.dispose();
        if (sfx != null)
            sfx.dispose();
        // Dispose active skill if present
        if (activeSkill != null) {
            // BaseSkill doesn't have dispose, but we could add it if needed
            activeSkill = null;
        }
        // Clear projectiles
        if (activeProjectiles != null) {
            activeProjectiles.clear();
        }
        // Dispose pointer animator
        if (pointerAnimator != null) {
            pointerAnimator.dispose();
            pointerAnimator = null;
        }
    }

    // --- Health / Respawn API ---
    public HealthSystem getHealthSystem() {
        return health;
    }

    /**
     * Set the respawn point for this player. When the player dies they'll be moved
     * here.
     */
    public void setRespawnPoint(float rx, float ry) {
        this.respawnX = rx;
        this.respawnY = ry;
    }

    /**
     * Respawn the player at the last set respawn point. Restores HP to full, clears
     * velocities and
     * gives a short invulnerability window.
     */
    public void respawn() {
        // move player
        setX(respawnX);
        setY(respawnY);
        // reset movement state
        this.velocityY = 0f;
        this.dashing = false;
        this.dashTimer = 0f;
        this.dashCooldownTimer = 0f;
        this.dashUsed = false;
        this.attacking = false;
        this.airAttacking = false;

        // restore health to full (even if dead)
        health.reset();

        // Play defeat sound (randomize between Defeat1 and Defeat2)
        if (Math.random() < 0.5) {
            SoundManager.play("Defeat1");
        } else {
            SoundManager.play("Defeat2");
        }

        // give brief invulnerability and movement stun
        health.setInvulnerable(true);
        respawnInvulRemaining = respawnInvulDuration;
        isStunned = true;
        respawnStunRemaining = respawnStunDuration;

        // Reset shield
        this.shield = 0;
    }

    public int getShield() {
        return shield;
    }

    public void setShield(int shield) {
        this.shield = shield;
    }

    public void addShield(int amount) {
        this.shield += amount;
        if (this.shield > maxShield) this.shield = maxShield; // enforce cap
    }

    public int getMaxShield() { return maxShield; }

    // Key API
    public int getKeyCount() { return keyCount; }
    public void addKey(int amount) { keyCount = Math.max(0, keyCount + amount); }
    public boolean consumeKey() { if (keyCount > 0) { keyCount--; return true; } return false; }

    public boolean isStunned() {
        return isStunned;
    }

    public void setStunned(boolean stunned) {
        this.isStunned = stunned;
    }

    // Collectibles
    private int diamondCount = 0;
    
    public int getDiamonds() {
        return diamondCount;
    }

    public void addDiamonds(int amount) {
        this.diamondCount += amount;
    }

    public void setDiamonds(int amount) {
        this.diamondCount = amount;
    }
    
    // Potion API
    public int getPotionCount() {
        return potionCount;
    }
    
    public int getMaxPotions() {
        return MAX_POTIONS;
    }
    
    public boolean canAddPotion() {
        return potionCount < MAX_POTIONS;
    }

    public void addPotion(int amount) {
        potionCount = Math.min(MAX_POTIONS, Math.max(0, potionCount + amount));
    }

    public void setPotionCount(int amount) {
        potionCount = Math.min(MAX_POTIONS, Math.max(0, amount));
    }
    
    /**
     * Heal the player by the specified amount, capped at maxHealth.
     */
    public void heal(int amount) {
        if (health != null && amount > 0) {
            float currentHealth = health.getCurrentHealth();
            float maxHealth = health.getMaxHealth();
            float newHealth = Math.min(currentHealth + amount, maxHealth);
            health.setHealth(newHealth);
        }
    }
    
    /**
     * Use a potion to heal the player.
     * Can only use potion if player has potions and is not at full health.
     */
    private void usePotion() {
        if (potionCount <= 0) {
            return; // No potions available
        }
        
        if (health == null) {
            return; // No health system
        }
        
        float currentHealth = health.getCurrentHealth();
        float maxHealth = health.getMaxHealth();
        
        if (currentHealth >= maxHealth) {
            return; // Already at full health
        }
        
        // Use potion
        potionCount--;
        heal(POTION_HEAL_AMOUNT);
        
        // Play healing sound
        try {
            SoundManager.play("PickUpItem");
        } catch (Exception ignored) {
        }
        
        Gdx.app.log("Player", "Used potion! Health: " + health.getCurrentHealth() + " / " + maxHealth + ". Potions left: " + potionCount);
    }

    public PlayerType getType() {
        return type;
    }

    public float getDashCooldownTimer() {
        return dashCooldownTimer;
    }

    public float getDashCooldownMax() {
        return config.dashCooldown;
    }

    public void debugDrawHitbox(ShapeRenderer shape) {
        shape.setColor(new Color(1f, 0f, 0f, 0.4f));
        shape.rect(getHitboxX(), getHitboxY(), getHitboxWidth(), getHitboxHeight());
        shape.setColor(onWall ? Color.RED : Color.CYAN);
        shape.circle(wallSensor.x, wallSensor.y, wallSensor.radius);
        // Draw back sensor in magenta
        shape.setColor(Color.MAGENTA);
        shape.circle(backSensor.x, backSensor.y, backSensor.radius);
        float centerX = getHitboxX() + getHitboxWidth() / 2;
        shape.setColor(onGround ? Color.GREEN : Color.YELLOW);
        shape.circle(centerX, getHitboxY(), 3f);
        if (groundedBySolid) {
            shape.setColor(Color.WHITE);
            shape.circle(centerX, getHitboxY() - 4f, 2f);
        }
    }

    // Held object API used by screens
    public com.jjmc.chromashift.environment.interactable.Pickable getHeldObject() {
        return heldObject;
    }

    public void setHeldObject(com.jjmc.chromashift.environment.interactable.Pickable obj) {
        this.heldObject = obj;
    }

    public void clearHeldObject() {
        this.heldObject = null;
    }

    public void throwHeldWithVelocity(float vx, float vy) {
        if (heldObject == null)
            return;
        // Cap throw velocity to prevent objects from moving too fast
        float maxThrowSpeed = 600f;
        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (speed > maxThrowSpeed) {
            float scale = maxThrowSpeed / speed;
            vx *= scale;
            vy *= scale;
        }
        heldObject.throwWithVelocity(vx, vy);
        heldObject = null;
    }

    /**
     * Reset dash state so the player may dash again. Used when external forces
     * (e.g., launchpads) propel the player and should restore dash availability.
     */
    public void resetDash() {
        this.dashing = false;
        this.dashTimer = 0f;
        this.dashCooldownTimer = 0f;
        this.dashUsed = false;
    }

    /**
     * Attach or update a persistent UI for this player without reinitializing
     * textures.
     */
    public void attachUI(Viewport viewport) {
        if (viewport == null)
            return;
        if (playerUI == null) {
            playerUI = new PlayerUI(this, viewport);
        } else {
            playerUI.setViewport(viewport);
        }
    }

    public PlayerUI getPlayerUI() {
        return playerUI;
    }

    public boolean getCanJump() {
        return canJump;
    }

    // Expose key count to UI without granting mutation
    public int getKeys() { return keyCount; }
}
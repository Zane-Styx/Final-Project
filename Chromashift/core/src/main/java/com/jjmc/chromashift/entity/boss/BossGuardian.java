package com.jjmc.chromashift.entity.boss;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.chromashift.helper.SpriteAnimator;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.Wall;

/**
 * BossGuardian - A boss with 3 floating entities arranged in a triangle pattern.
 * Each entity has its own idle and attack animations.
 */
public class BossGuardian extends Boss {
    // Guardian entities
    private static class GuardianEntity {
        SpriteAnimator idleAnimator;
        SpriteAnimator attackAnimator;
        Vector2 position;
        Vector2 targetOffset; // Offset from boss center for triangle pattern
        float width;
        float height;
        float bobPhase;
        float bobSpeed;
        float bobAmplitude;
        boolean flipX;
        boolean attacking;
        float alpha = 1f; // Visibility control (0-1)
        
        // Actual hitbox dimensions (content within sprite padding)
        float hitboxWidth;
        float hitboxHeight;
        // Padding from sprite edges: top, left, right, bottom
        float paddingTop;
        float paddingLeft;
        float paddingRight;
        float paddingBottom;
        
        public GuardianEntity(String idleSpritePath, String attackSpritePath, 
                             int idleFrames, int attackFrames, 
                             float width, float height, Vector2 offset,
                             float hitboxWidth, float hitboxHeight,
                             float padTop, float padLeft, float padRight, float padBottom) {
            // Separate animators for idle and attack
            this.idleAnimator = new SpriteAnimator(idleSpritePath, 1, idleFrames);
            this.attackAnimator = new SpriteAnimator(attackSpritePath, 1, attackFrames);
            
            this.position = new Vector2();
            this.targetOffset = offset;
            this.width = width;
            this.height = height;
            this.hitboxWidth = hitboxWidth;
            this.hitboxHeight = hitboxHeight;
            this.paddingTop = padTop;
            this.paddingLeft = padLeft;
            this.paddingRight = padRight;
            this.paddingBottom = padBottom;
            this.bobPhase = (float)(Math.random() * Math.PI * 2);
            this.bobSpeed = 1.2f + (float)Math.random() * 0.8f;
            this.bobAmplitude = 10f + (float)Math.random() * 10f;
            this.flipX = false;
            this.attacking = false;
            
            // Setup animations
            idleAnimator.addAnimation("idle", 0, 0, idleFrames, 0.1f, true);
            attackAnimator.addAnimation("attack", 0, 0, attackFrames, 0.08f, false);
            idleAnimator.play("idle", false);
        }
        
        public void update(float delta, Vector2 bossCenter, float globalTime) {
            if (attacking) {
                attackAnimator.update(delta);
                return;
            }
            idleAnimator.update(delta);
            float bob = (float)Math.sin(globalTime * bobSpeed + bobPhase) * bobAmplitude;
            Vector2 target = new Vector2(bossCenter.x + targetOffset.x, bossCenter.y + targetOffset.y + bob);
            position.lerp(target, 0.05f);
        }
            
        public void render(SpriteBatch batch) {
            if (alpha <= 0f) return; // Don't render if invisible
            SpriteAnimator animator = attacking ? attackAnimator : idleAnimator;
            animator.setFlipX(flipX);
            // Set color with alpha
            Color prevColor = batch.getColor();
            batch.setColor(prevColor.r, prevColor.g, prevColor.b, prevColor.a * alpha);
            animator.render(batch, position.x - width/2, position.y - height/2, width, height);
            batch.setColor(prevColor); // Restore original color
        }

        public void startAttack(boolean faceLeft) {
            attacking = true;
            attackAnimator.play("attack", false);
            attackAnimator.setFlipX(faceLeft);
        }

        public void stopAttack(boolean faceLeft) {
            attacking = false;
            idleAnimator.play("idle", false);
            idleAnimator.setFlipX(faceLeft);
        }

        public void updateAttack(float delta, float animSpeedScale) {
            attackAnimator.update(delta * animSpeedScale);
        }

        public int getAttackFrameIndex() {
            return attackAnimator.getCurrentFrameIndex();
        }
        
        public boolean isAttackAnimationFinished() {
            return attackAnimator.isAnimationFinished();
        }

        public void setPosition(float x, float y) {
            position.set(x, y);
        }
        
        /**
         * Get the actual hitbox rectangle based on sprite padding and flip state.
         * Padding values represent the empty space from each edge to the actual content.
         */
        public Rectangle getHitbox() {
            float spriteLeft = position.x - width / 2;
            float spriteTop = position.y - height / 2;
            
            float hitboxX, hitboxY;
            if (flipX) {
                // When flipped, left/right padding swap
                hitboxX = spriteLeft + paddingRight;
            } else {
                hitboxX = spriteLeft + paddingLeft;
            }
            hitboxY = spriteTop + paddingTop;
            
            return new Rectangle(hitboxX, hitboxY, hitboxWidth, hitboxHeight);
        }
        
        public Rectangle getBounds() {
            return new Rectangle(position.x - width/2, position.y - height/2, width, height);
        }
        
        public void dispose() {
            idleAnimator.dispose();
            attackAnimator.dispose();
        }
    }
    
    // Three guardians arranged in triangle
    private GuardianEntity guardian1; // Top
    private GuardianEntity guardian2; // Bottom-left
    private GuardianEntity guardian3; // Bottom-right
    
    // Movement parameters
    private Vector2 bossCenter;
    private float hoverSpeed = 50f;
    private float hoverAmplitude = 30f;
    private float hoverTimer = 0f;
    private float timeAccum = 0f;
    
    // Triangle pattern parameters
    private float triangleRadius = 200f; // Distance from center
    private float rotationSpeed = 20f; // Degrees per second
    private float currentRotation = 0f;

    // Target tracking for facing
    private float targetX = 0f;
    private float targetY = 0f;

    // Debug flag for overall display
    private boolean debugDisplay = true; // Set to true to show all debug info

    // Guardian 3 (Boss 2) attack control
    private enum Boss2AttackState { FORMATION, RISING, TRACKING, PLUNGING, RETURNING }
    private Boss2AttackState boss2State = Boss2AttackState.FORMATION;
    private static final int BOSS2_PLUNGE_FRAME = 14;
    private static final int BOSS2_RETURN_FRAME = 23;
    private static final float BOSS2_ATTACK_FRAME_DURATION = 0.08f; // matches animator setup
    private float boss2AnimSpeedScale = 0.5f;
    private float boss2TrackDuration = 2.5f;
    private float boss2AttackCooldown = 5f;
    private float boss2CooldownTimer = 2f;
    private float boss2TrackSpeed = 320f;
    private float boss2PlungeSpeed = 1400f;
    private float boss2MinHeightToAttack = 600f; // Minimum height before attacking
    private float boss2RiseSpeed = 300f; // Speed to rise to safe height
    private final Vector2 boss2CommitPosition = new Vector2();
    private final Vector2 boss2Velocity = new Vector2();
    private boolean debugBoss2HitBox = true; // Debug: set true to draw boss2 attack hitbox
    private Rectangle boss2HitboxForDebug = new Rectangle(); // Store for debug rendering
    
    // Guardian 2 (Boss1) lightning attack
    private enum Boss1AttackState { FORMATION, MOVING_TO_ATTACK, SUMMONING, COOLDOWN }
    private Boss1AttackState boss1State = Boss1AttackState.FORMATION;
    private float boss1AttackCooldown = 6f;
    private float boss1CooldownTimer = 2f;
    private SpriteAnimator lightningAnimator = null;
    private static final int LIGHTNING_FRAMES = 31;
    private Vector2 lightningFront = new Vector2(); // Lightning in front of player
    private Vector2 lightningBack = new Vector2(); // Lightning behind player
    private Vector2 lightningCenter = new Vector2(); // Lightning spawned in center
    private Vector2 committedLightningFront = new Vector2(); // Locked position where lightning was activated
    private Vector2 committedLightningBack = new Vector2(); // Locked position where lightning was activated
    private Vector2 committedLightningCenter = new Vector2(); // Locked position for center lightning
    private java.util.List<Vector2> randomLightningPositions = new java.util.ArrayList<>(); // Random lightning positions
    private java.util.List<Float> randomLightningDelays = new java.util.ArrayList<>(); // Per-bolt spawn delays
    private float lightningAnimTimer = 0f;
    private float lightningDuration = 3.5f; // How long lightning plays (3s anim + spawn delays)
    private boolean lightningActive = false;
    private boolean lightningCenterActive = false; // Track if center lightning is active
    private float lightningCenterDelay = 1f; // Delay before center lightning spawns
    private float lightningDistance = 100f; // Distance from player for front/back placement (200px total spacing)
    private float lightningScale = 2.0f; // Scale of lightning sprite
    private float lightningWidth = 111f; // Base width of lightning sprite (before scale)
    private float lightningHeight = 75f; // Base height of lightning sprite (before scale)
    private float boss1AttackPosX = 0f; // Target X for guardian2 pre-attack positioning
    private float boss1AttackPosY = 450f; // Target Y for guardian2 pre-attack positioning
    private float boss1MoveSpeed = 300f; // Speed guardian2 moves to pre-attack spot
    
    // Lightning damage system
    private static final int[] LIGHTNING_DAMAGE_FRAMES = {17, 20, 23};
    private static final int[] LIGHTNING_DAMAGE_VALUES = {15, 10, 5};
    private static final float LIGHTNING_HITBOX_WIDTH = 48f; // Actual damage hitbox width
    private static final float LIGHTNING_HITBOX_HEIGHT = 361f; // Actual damage hitbox height
    private static final float LIGHTNING_HITBOX_PAD_LEFT = 32f; // Left padding
    private static final float LIGHTNING_HITBOX_PAD_RIGHT = 31f; // Right padding
    private java.util.Set<Integer> damageFramesHit = new java.util.HashSet<>(); // Track which damage frames fired
    
    // Player reference for damage
    private com.jjmc.chromashift.player.Player player = null;
    
    // Trigger zone tracking
    private enum TriggerZone { TRIGGER_1, TRIGGER_2, TRIGGER_3, TRIGGER_4, TRIGGER_5, TRIGGER_6, NONE }
    private TriggerZone activeTriggerZone = TriggerZone.NONE;
    
    // Spawn sequence state
    private enum SpawnPhase { GUARDIAN2_ENTER, GUARDIAN3_ENTER, GUARDIAN1_DROP, COMPLETE }
    private SpawnPhase spawnPhase = SpawnPhase.GUARDIAN2_ENTER;
    private float spawnPhaseTimer = 0f;
    private float spawnPhaseStartDelay = 0.5f; // delay before first guardian starts
    private float guardian2EntryDuration = 1.8f; // guardian 2 enters from left
    private float guardian3EntryDuration = 1.8f; // guardian 3 enters from right
    private float guardian1DropDuration = 1.5f; // guardian 1 drops from top
    private float delayBetweenGuardians = 0.4f; // gap between each entrance
    
    // Camera zoom/focus state during spawn
    private float spawnCameraZoom = 1.0f; // target camera zoom
    private Vector2 spawnCameraFocus = new Vector2(); // target camera position
    private float cameraZoomReturnSpeed = 0.8f; // how quickly to return to normal
    private float cameraFocusReturnSpeed = 0.6f; // how quickly to return focus to normal
    private boolean spawnCameraActive = false;
    
    // Callback for spawn completion (called by screen)
    private Runnable onSpawnSequenceComplete = null;
    
    // References
    private Array<Solid> solids;
    private Array<Wall> walls;
    
    // Debug
    private BitmapFont debugFont;
    
    public BossGuardian() {
        super(1500f); // 1500 health points total (500 per guardian)
        
        bossCenter = new Vector2(0, 0);
        
        try {
            debugFont = new BitmapFont();
            debugFont.getData().setScale(1.0f);
            debugFont.setColor(Color.WHITE);
        } catch (Exception ignored) {}
        
        // Scale for guardians
        final float guardian1Width = 200f;
        final float guardian1Height = 200f;
        final float guardian3Width = 200f;
        final float guardian3Height = 200f;
        // Guardian2 scale - adjust this to make guardian2 (boss1) smaller or larger
        final float guardian2Scale = 0.7f; // 0.8 = 80% of original, 1.0 = 100%, 1.2 = 120%
        final float guardian2Width = 330f * guardian2Scale;
        final float guardian2Height = 330f * guardian2Scale;
        final float guardian2HitboxWidth = 119f * guardian2Scale;
        final float guardian2HitboxHeight = 152f * guardian2Scale;
        final float guardian2PadTop = 63f * guardian2Scale;
        final float guardian2PadLeft = 106f * guardian2Scale;
        final float guardian2PadRight = 105f * guardian2Scale;
        final float guardian2PadBottom = 115f * guardian2Scale;
        
        // Create triangle pattern offsets
        // Top guardian (0 degrees)
        Vector2 offset1 = new Vector2(0, triangleRadius);
        // Bottom-left guardian (240 degrees)
        Vector2 offset2 = new Vector2(
            (float)(triangleRadius * Math.cos(Math.toRadians(240))),
            (float)(triangleRadius * Math.sin(Math.toRadians(240)))
        );
        // Bottom-right guardian (120 degrees)
        Vector2 offset3 = new Vector2(
            (float)(triangleRadius * Math.cos(Math.toRadians(120))),
            (float)(triangleRadius * Math.sin(Math.toRadians(120)))
        );
        
        // Initialize guardians with their respective sprites (idle and attack separate)
        // Guardian1: 200x200 sprite, 73x93 hitbox, padding: top=32, left=71, right=56, bottom=75
        guardian1 = new GuardianEntity("entity/boss1/boss_idle.png", "entity/boss1/boss_attack.png",
                                      18, 44, guardian1Width, guardian1Height, offset1,
                                      73f, 93f, 32f, 71f, 56f, 75f);
        
        // Guardian2: 330x330 sprite, 119x152 hitbox, padding: top=63, left=106, right=105, bottom=115
        guardian2 = new GuardianEntity("entity/boss1/boss1_idle.png", "entity/boss1/boss1_attack.png",
                                      33, 33, guardian2Width, guardian2Height, offset2,
                                      guardian2HitboxWidth, guardian2HitboxHeight,
                                      guardian2PadTop, guardian2PadLeft, guardian2PadRight, guardian2PadBottom);
        
        // Guardian3: 200x200 sprite, 76x91 hitbox, padding: top=31, left=60, right=64, bottom=78
        guardian3 = new GuardianEntity("entity/boss1/boss2_idle.png", "entity/boss1/boss2_attack.png",
                                      23, 26, guardian3Width, guardian3Height, offset3,
                                      76f, 91f, 31f, 60f, 64f, 78f);
        
        // Setup boss body (invisible center point)
        initBody("entity/boss1/boss.png", 1, 1, 32f, 32f, 0, 0);
        setPosition(0f, 400f);
        
        // Load lightning animator for Guardian 2 attack (single setup)
        try {
            lightningAnimator = new SpriteAnimator("entity/boss1/skill/boss1_lightning.png", 1, LIGHTNING_FRAMES);
            // 3 seconds total across all frames
            lightningAnimator.addAnimation("lightning", 0, 0, LIGHTNING_FRAMES, 3f / LIGHTNING_FRAMES, false);
            lightningAnimator.play("lightning", false);
        } catch (Exception e) {
            Gdx.app.log("BossGuardian", "Failed to load lightning animator: " + e.getMessage());
        }
    }

    /**
     * Set callback to be invoked when entire spawn sequence completes.
     */
    public void setOnSpawnSequenceComplete(Runnable callback) {
        this.onSpawnSequenceComplete = callback;
    }

    @Override
    protected void onSpawnStart() {
        // Initialize spawn sequence
        spawnPhase = SpawnPhase.GUARDIAN2_ENTER;
        spawnPhaseTimer = -spawnPhaseStartDelay; // Start with delay before first guardian
        spawnCameraActive = true;
        
        // Set all guardians invisible initially
        guardian1.alpha = 0f;
        guardian2.alpha = 0f;
        guardian3.alpha = 0f;
        
        // Position guardians off-screen for entrance animations (far from center)
        // Guardian 2 starts far left (keep Y centered)
        guardian2.position.set(bossCenter.x - 1200f, bossCenter.y + guardian2.targetOffset.y);
        // Guardian 3 starts far right (keep Y centered)
        guardian3.position.set(bossCenter.x + 1200f, bossCenter.y + guardian3.targetOffset.y);
        // Guardian 1 starts high above (keep X centered)
        guardian1.position.set(bossCenter.x + guardian1.targetOffset.x, bossCenter.y - 600f);
        
        setSpawnDuration(spawnPhaseStartDelay + guardian2EntryDuration + delayBetweenGuardians + 
                        guardian3EntryDuration + delayBetweenGuardians + guardian1DropDuration + 0.5f);
    }

    @Override
    protected void updateSpawn(float delta, float progress) {
        // Update spawn phase timing
        spawnPhaseTimer += delta;
        
        // Guardian 2 enters from left
        if (spawnPhase == SpawnPhase.GUARDIAN2_ENTER) {
            float phaseDuration = guardian2EntryDuration;
            if (spawnPhaseTimer >= 0f && spawnPhaseTimer < phaseDuration) {
                float phaseProgress = spawnPhaseTimer / phaseDuration;
                // Ease-out motion: starts fast, slows down
                float easeProgress = 1f - (float)Math.pow(1f - phaseProgress, 3f);
                float startX = bossCenter.x - 1200f;
                float targetX = bossCenter.x + guardian2.targetOffset.x;
                guardian2.position.x = startX + (targetX - startX) * easeProgress;
                guardian2.position.y = bossCenter.y + guardian2.targetOffset.y; // Keep Y at target
                // Fade in guardian 2
                guardian2.alpha = easeProgress;
            } else if (spawnPhaseTimer >= phaseDuration) {
                guardian2.position.set(bossCenter.x + guardian2.targetOffset.x, bossCenter.y + guardian2.targetOffset.y);
                guardian2.alpha = 1f;
                spawnPhase = SpawnPhase.GUARDIAN3_ENTER;
                spawnPhaseTimer = 0f;
            }
        }
        
        // Guardian 3 enters from right
        if (spawnPhase == SpawnPhase.GUARDIAN3_ENTER) {
            float phaseDuration = guardian3EntryDuration;
            if (spawnPhaseTimer < phaseDuration) {
                float phaseProgress = spawnPhaseTimer / phaseDuration;
                // Ease-out motion
                float easeProgress = 1f - (float)Math.pow(1f - phaseProgress, 3f);
                float startX = bossCenter.x + 1200f;
                float targetX = bossCenter.x + guardian3.targetOffset.x;
                guardian3.position.x = startX + (targetX - startX) * easeProgress;
                guardian3.position.y = bossCenter.y + guardian3.targetOffset.y; // Keep Y at target
                // Fade in guardian 3
                guardian3.alpha = easeProgress;
            } else {
                guardian3.position.set(bossCenter.x + guardian3.targetOffset.x, bossCenter.y + guardian3.targetOffset.y);
                guardian3.alpha = 1f;
                spawnPhase = SpawnPhase.GUARDIAN1_DROP;
                spawnPhaseTimer = 0f;
            }
        }
        
        // Guardian 1 drops from top
        if (spawnPhase == SpawnPhase.GUARDIAN1_DROP) {
            float phaseDuration = guardian1DropDuration;
            if (spawnPhaseTimer < phaseDuration) {
                float phaseProgress = spawnPhaseTimer / phaseDuration;
                // Ease-out motion with some bounce
                float easeProgress = 1f - (float)Math.pow(1f - phaseProgress, 2.5f);
                float startY = bossCenter.y - 600f;
                float targetY = bossCenter.y + guardian1.targetOffset.y;
                guardian1.position.x = bossCenter.x + guardian1.targetOffset.x; // Keep X at target
                guardian1.position.y = startY + (targetY - startY) * easeProgress;
                // Fade in guardian 1
                guardian1.alpha = easeProgress;
            } else {
                guardian1.position.set(bossCenter.x + guardian1.targetOffset.x, bossCenter.y + guardian1.targetOffset.y);
                guardian1.alpha = 1f;
                spawnPhase = SpawnPhase.COMPLETE;
            }
        }
        
        // Update camera zoom and focus during spawn
        updateSpawnCamera(delta, progress);
        
        // Update guardian animations and idle bobbing (partial)
        guardian1.update(delta * 0.5f, bossCenter, timeAccum);
        guardian2.update(delta * 0.5f, bossCenter, timeAccum);
        guardian3.update(delta * 0.5f, bossCenter, timeAccum);
    }

    @Override
    protected void onSpawnComplete() {
        // Snap all guardians to their final positions
        guardian1.position.set(bossCenter.x + guardian1.targetOffset.x, 
                              bossCenter.y + guardian1.targetOffset.y);
        guardian2.position.set(bossCenter.x + guardian2.targetOffset.x,
                              bossCenter.y + guardian2.targetOffset.y);
        guardian3.position.set(bossCenter.x + guardian3.targetOffset.x,
                              bossCenter.y + guardian3.targetOffset.y);
        
        spawnCameraActive = false;
        
        // Invoke completion callback if set
        if (onSpawnSequenceComplete != null) {
            onSpawnSequenceComplete.run();
        }
    }
    
    /**
     * Update camera zoom and focus during spawn sequence.
     * Call from updateSpawn() to smoothly transition camera effects.
     */
    private void updateSpawnCamera(float delta, float progress) {
        // Calculate target zoom (zoom in to 1.2x during spawn)
        float targetZoom = 1.2f;
        spawnCameraZoom += (targetZoom - spawnCameraZoom) * 0.08f; // smooth transition
        
        // Focus camera on boss center area
        spawnCameraFocus.set(bossCenter);
        
        // During return phase (last 20% of spawn), smoothly return to normal
        if (progress > 0.8f) {
            float returnProgress = (progress - 0.8f) / 0.2f;
            spawnCameraZoom += (1.0f - spawnCameraZoom) * returnProgress * cameraZoomReturnSpeed * delta;
        }
    }
    
    /**
     * Get current camera zoom for spawn sequence.
     * Use this in your camera update to zoom during spawn.
     */
    public float getSpawnCameraZoom() {
        return spawnCameraActive ? spawnCameraZoom : 1.0f;
    }
    
    /**
     * Get camera focus point during spawn sequence.
     * Use this in your camera update to focus on boss center.
     */
    public Vector2 getSpawnCameraFocus() {
        return spawnCameraActive ? spawnCameraFocus : null;
    }
    
    public void setEnvironment(Array<Solid> solids, Array<Wall> walls) {
        this.solids = solids;
        this.walls = walls;
    }
    
    /**
     * Set the current trigger zone (called by level when player enters a trigger).
     * Only spawn lightning if in TRIGGER_1.
     */
    public void setActiveTriggerZone(String triggerId) {
        if (triggerId == null) {
            activeTriggerZone = TriggerZone.NONE;
            return;
        }
        
        switch (triggerId.toLowerCase()) {
            case "trigger_1":
                activeTriggerZone = TriggerZone.TRIGGER_1;
                break;
            case "trigger_2":
                activeTriggerZone = TriggerZone.TRIGGER_2;
                break;
            case "trigger_3":
                activeTriggerZone = TriggerZone.TRIGGER_3;
                break;
            case "trigger_4":
                activeTriggerZone = TriggerZone.TRIGGER_4;
                break;
            case "trigger_5":
                activeTriggerZone = TriggerZone.TRIGGER_5;
                break;
            case "trigger_6":
                activeTriggerZone = TriggerZone.TRIGGER_6;
                break;
            default:
                activeTriggerZone = TriggerZone.NONE;
                break;
        }
    }
    
    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);
        bossCenter.set(x, y);
    }
    
    @Override
    public void update(float delta) {
        super.update(delta);
        timeAccum += delta;
        
        // Debug: Force guardian attacks with 1, 2, 3 keys (only if debug enabled)
        if (debugDisplay) {
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_1)) {
                boolean faceLeft = targetX < bossCenter.x;
                if (!guardian1.attacking) {
                    guardian1.startAttack(faceLeft);
                }
            }
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_2)) {
                boolean faceLeft = targetX < bossCenter.x;
                if (!guardian2.attacking) {
                    guardian2.startAttack(faceLeft);
                }
            }
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.NUM_3)) {
                boolean faceLeft = targetX < bossCenter.x;
                startBoss2Attack(faceLeft);
            }
        }
        
        // Update hover movement
        hoverTimer += delta;
        float hoverY = (float)Math.sin(hoverTimer * hoverSpeed / 30f) * hoverAmplitude;
        bossCenter.y = getY() + hoverY;
        
        // Rotate triangle pattern
        currentRotation += rotationSpeed * delta;
        if (currentRotation >= 360f) currentRotation -= 360f;
        
        // Update triangle positions with rotation
        updateTrianglePositions();
        
        // Face player: flip if target is left of guardian
        boolean faceLeft = targetX < bossCenter.x;
        guardian1.flipX = faceLeft;
        guardian2.flipX = faceLeft;
        guardian3.flipX = faceLeft;

        // Update each guardian
        guardian1.update(delta, bossCenter, timeAccum);
        updateBoss1(delta, faceLeft);
        updateBoss2(delta, faceLeft);
    }

    // Allow screen to set target (player position)
    public void setTarget(float x, float y) {
        this.targetX = x;
        this.targetY = y;
    }
    
    // Set player reference for damage checks
    public void setPlayer(com.jjmc.chromashift.player.Player player) {
        this.player = player;
    }

    private void startBoss2Attack(boolean faceLeft) {
        // First check if guardian3 is too close to ground
        if (guardian3.position.y > boss2MinHeightToAttack) {
            // Too low, rise first instead of attacking
            boss2State = Boss2AttackState.RISING;
        } else {
            // Safe height, start tracking
            boss2State = Boss2AttackState.TRACKING;
            boss2TrackDuration = MathUtils.random(2f, 3f);
            boss2AnimSpeedScale = (BOSS2_PLUNGE_FRAME * BOSS2_ATTACK_FRAME_DURATION) / boss2TrackDuration;
            boss2CooldownTimer = boss2AttackCooldown;
            boss2Velocity.setZero();
            guardian3.startAttack(faceLeft);
        }
    }

    private void startBoss2Plunge() {
        boss2State = Boss2AttackState.PLUNGING;
        boss2CommitPosition.set(targetX, targetY);
        guardian3.setPosition(boss2CommitPosition.x, guardian3.position.y);
        boss2Velocity.set(0f, -boss2PlungeSpeed);
    }

    private void startBoss2Return(boolean faceLeft) {
        boss2State = Boss2AttackState.RETURNING;
        guardian3.stopAttack(faceLeft);
    }

    private static final int GUARDIAN2_LIGHTNING_FRAME = 22;
    
    private void activateBoss1Lightning() {
        lightningActive = true;
        lightningAnimTimer = 0f;
        randomLightningPositions.clear();
        randomLightningDelays.clear();
        damageFramesHit.clear(); // Reset damage tracking for new attack
        
        if (lightningAnimator != null) {
            lightningAnimator.play("lightning", false);
        }
        
        // Spawn 6-10 random lightning bolts (no trigger gate)
        int randomCount = MathUtils.random(6, 10);
        float scaledHeight = lightningHeight * lightningScale;
        float adjustedY = 251 + scaledHeight; // keep requested offset
        float minSpacing = (lightningWidth * lightningScale) * 0.7f; // avoid overlap
        
        for (int i = 0; i < randomCount; i++) {
            // Random X within reasonable bounds, enforcing min spacing
            float randomX;
            int attempts = 0;
            do {
                randomX = targetX + MathUtils.random(-400f, 400f);
                attempts++;
            } while (attempts < 10 && overlapsExisting(randomX, randomLightningPositions, minSpacing));

            Vector2 randomPos = new Vector2(randomX, adjustedY);
            randomLightningPositions.add(randomPos);
            // Assign a small random delay so bolts don't all appear at once
            randomLightningDelays.add(MathUtils.random(0f, 0.5f));
        }
    }
    
    private void startBoss1LightningAttack() {
        boss1State = Boss1AttackState.MOVING_TO_ATTACK;
        boss1AttackPosX = bossCenter.x;
        boss1AttackPosY = 450f;
        boss1CooldownTimer = boss1AttackCooldown;
    }
    
    private void updateBoss1(float delta, boolean faceLeft) {
        // Check if guardian2 attack animation has finished
        if (guardian2.attacking && guardian2.isAttackAnimationFinished()) {
            guardian2.stopAttack(faceLeft);
        }
        
        switch (boss1State) {
            case FORMATION:
                guardian2.update(delta, bossCenter, timeAccum);
                boss1CooldownTimer -= delta;
                if (boss1CooldownTimer <= 0f) {
                    startBoss1LightningAttack();
                }
                break;
            case MOVING_TO_ATTACK:
                // Move guardian2 to the designated attack position using idle animation (like guardian3 rise/track)
                guardian2.flipX = faceLeft;
                guardian2.update(delta, bossCenter, timeAccum);

                float dxMove = boss1AttackPosX - guardian2.position.x;
                float dyMove = boss1AttackPosY - guardian2.position.y;

                if (Math.abs(dxMove) > 5f) {
                    float dirX = Math.signum(dxMove);
                    guardian2.position.x += dirX * boss1MoveSpeed * delta;
                } else {
                    guardian2.position.x = boss1AttackPosX;
                }

                if (Math.abs(dyMove) > 5f) {
                    float dirY = Math.signum(dyMove);
                    guardian2.position.y += dirY * boss1MoveSpeed * delta;
                } else {
                    guardian2.position.y = boss1AttackPosY;
                }

                // Once close enough, start the attack animation
                if (Math.abs(dxMove) <= 5f && Math.abs(dyMove) <= 5f) {
                    guardian2.position.set(boss1AttackPosX, boss1AttackPosY);
                    guardian2.startAttack(faceLeft);
                    boss1State = Boss1AttackState.SUMMONING;
                }
                break;
            case SUMMONING:
                guardian2.update(delta, bossCenter, timeAccum);
                
                // Trigger lightning at frame 22 of attack
                if (!lightningActive && guardian2.getAttackFrameIndex() >= GUARDIAN2_LIGHTNING_FRAME) {
                    activateBoss1Lightning();
                }
                
                // Update lightning animation
                if (lightningActive && lightningAnimator != null) {
                    lightningAnimator.update(delta);
                    lightningAnimTimer += delta;
                    
                    // Check for lightning damage frames
                    int currentFrame = lightningAnimator.getCurrentFrameIndex();
                    checkLightningDamage(currentFrame);
                    
                    // End lightning after duration
                    if (lightningAnimTimer >= lightningDuration) {
                        lightningActive = false;
                        lightningCenterActive = false;
                        boss1State = Boss1AttackState.COOLDOWN;
                    }
                }
                break;
            case COOLDOWN:
                guardian2.update(delta, bossCenter, timeAccum);
                boss1CooldownTimer -= delta;
                if (boss1CooldownTimer <= 0f) {
                    boss1State = Boss1AttackState.FORMATION;
                }
                break;
        }
    }

    private void updateBoss2(float delta, boolean faceLeft) {
        switch (boss2State) {
            case FORMATION:
                if (guardian3.attacking) {
                    guardian3.stopAttack(faceLeft);
                }
                guardian3.update(delta, bossCenter, timeAccum);
                boss2CooldownTimer -= delta;
                if (boss2CooldownTimer <= 0f) {
                    startBoss2Attack(faceLeft);
                }
                break;
            case RISING:
                // Rise until reaching safe height - use idle animation and track X
                guardian3.flipX = faceLeft;
                guardian3.update(delta, bossCenter, boss2RiseSpeed); // Use idle animation
                guardian3.position.y -= boss2RiseSpeed * delta;
                
                // Continue tracking X movement while rising
                float dxRise = targetX - guardian3.position.x;
                if (Math.abs(dxRise) > 5f) {
                    float riseTrackDir = Math.signum(dxRise);
                    guardian3.position.x += riseTrackDir * boss2TrackSpeed * delta;
                }
                
                if (guardian3.position.y <= boss2MinHeightToAttack) {
                    guardian3.position.y = boss2MinHeightToAttack;
                    // Now safe height, start attack animation
                    boss2State = Boss2AttackState.TRACKING;
                    boss2TrackDuration = MathUtils.random(2f, 3f);
                    boss2AnimSpeedScale = (BOSS2_PLUNGE_FRAME * BOSS2_ATTACK_FRAME_DURATION) / boss2TrackDuration;
                    boss2CommitPosition.set(targetX, targetY); // Commit to current player position
                    boss2CooldownTimer = boss2AttackCooldown;
                    boss2Velocity.setZero();
                    guardian3.startAttack(faceLeft);
                }
                break;
            case TRACKING:
                guardian3.flipX = faceLeft;
                guardian3.updateAttack(delta, boss2AnimSpeedScale);
                
                // At frame 14, stop X movement and commit position
                if (guardian3.getAttackFrameIndex() >= BOSS2_PLUNGE_FRAME) {
                    // Commit current position for plunge
                    boss2CommitPosition.set(guardian3.position.x, guardian3.position.y);
                    boss2State = Boss2AttackState.PLUNGING;
                    boss2Velocity.set(0f, -boss2PlungeSpeed); // negative Y plunges downward (rising uses -speed*delta)
                } else {
                    // Track player horizontally (X only) before frame 14
                    float dxTrack = targetX - guardian3.position.x;
                    float trackDir = dxTrack != 0 ? Math.signum(dxTrack) : 0;
                    guardian3.position.x += trackDir * boss2TrackSpeed * delta;
                }
                break;
            case PLUNGING:
                guardian3.flipX = faceLeft;
                guardian3.updateAttack(delta, boss2AnimSpeedScale);
                guardian3.position.x = boss2CommitPosition.x;
                guardian3.position.y += boss2Velocity.y * delta;
                
                // Use guardian3's actual hitbox for wall collision
                Rectangle guardian3Hitbox = guardian3.getHitbox();
                boss2HitboxForDebug.set(guardian3Hitbox);
                float hitboxBottomOffset = guardian3Hitbox.y - guardian3.position.y; // negative number
                
                // Stop when colliding with the specific wall at x:-800, y:224 (or solids)
                float stopY = Float.NaN;
                if (walls != null) {
                    for (Wall w : walls) {
                        // Only collide with the wall at x:-800, y:224
                        if (w.bounds.x == -800f && w.bounds.y == 224f && w.getBounds().overlaps(guardian3Hitbox)) {
                            stopY = w.getBounds().y + w.getBounds().height; // top of wall
                            break;
                        }
                    }
                }
                if (Float.isNaN(stopY) && solids != null) {
                    for (Solid s : solids) {
                        if (s.getBounds().overlaps(guardian3Hitbox)) {
                            stopY = s.getBounds().y + s.getBounds().height; // top of solid
                            break;
                        }
                    }
                }
                if (!Float.isNaN(stopY)) {
                    guardian3.position.y = stopY - hitboxBottomOffset;
                    boss2Velocity.y = 0f;
                }
                
                if (guardian3.getAttackFrameIndex() >= BOSS2_RETURN_FRAME) {
                    startBoss2Return(faceLeft);
                }
                break;
            case RETURNING:
                guardian3.flipX = faceLeft;
                float targetPosX = bossCenter.x + guardian3.targetOffset.x;
                float targetPosY = bossCenter.y + guardian3.targetOffset.y;
                // Move back to formation quickly
                guardian3.position.lerp(new Vector2(targetPosX, targetPosY), 0.15f);
                if (guardian3.position.dst(targetPosX, targetPosY) < 5f) {
                    guardian3.position.set(targetPosX, targetPosY);
                    boss2State = Boss2AttackState.FORMATION;
                    boss2CooldownTimer = boss2AttackCooldown;
                    guardian3.stopAttack(faceLeft);
                }
                break;
        }
    }
    
    private void updateTrianglePositions() {
        // Recalculate offsets based on current rotation
        float rad1 = (float)Math.toRadians(currentRotation + 90);
        guardian1.targetOffset.set(
            (float)Math.cos(rad1) * triangleRadius,
            (float)Math.sin(rad1) * triangleRadius
        );
        
        float rad2 = (float)Math.toRadians(currentRotation + 210);
        guardian2.targetOffset.set(
            (float)Math.cos(rad2) * triangleRadius,
            (float)Math.sin(rad2) * triangleRadius
        );
        
        float rad3 = (float)Math.toRadians(currentRotation + 330);
        guardian3.targetOffset.set(
            (float)Math.cos(rad3) * triangleRadius,
            (float)Math.sin(rad3) * triangleRadius
        );
    }

    // Check if a candidate X overlaps existing lightning positions within a minimum spacing
    private boolean overlapsExisting(float candidateX, java.util.List<Vector2> positions, float minSpacing) {
        for (Vector2 p : positions) {
            if (Math.abs(candidateX - p.x) < minSpacing) {
                return true;
            }
        }
        return false;
    }
    
    private void checkLightningDamage(int currentFrame) {
        // Check if current frame is a damage frame
        for (int i = 0; i < LIGHTNING_DAMAGE_FRAMES.length; i++) {
            if (currentFrame == LIGHTNING_DAMAGE_FRAMES[i]) {
                // Skip if we already hit on this frame
                if (damageFramesHit.contains(currentFrame)) {
                    continue;
                }
                damageFramesHit.add(currentFrame);
                
                int damageValue = LIGHTNING_DAMAGE_VALUES[i];
                
                // Check collision with player for each lightning bolt
                if (player != null && player.getHealthSystem() != null) {
                    Rectangle playerBounds = player.getHitboxRect();
                    
                    // Scale the hitbox by lightning scale
                    float scaledHitboxWidth = LIGHTNING_HITBOX_WIDTH * lightningScale;
                    float scaledHitboxHeight = LIGHTNING_HITBOX_HEIGHT * lightningScale;
                    float scaledPadLeft = LIGHTNING_HITBOX_PAD_LEFT * lightningScale;
                    float scaledPadRight = LIGHTNING_HITBOX_PAD_RIGHT * lightningScale;
                    
                    Gdx.app.log("LightningDMG", "Checking frame " + currentFrame + ", damage=" + damageValue + 
                        ", playerHealth=" + player.getHealthSystem().getCurrentHealth() + 
                        ", playerBounds=" + playerBounds);
                    
                    for (Vector2 boltPos : randomLightningPositions) {
                        // Calculate the actual hitbox (use the content width without sprite padding)
                        // The 48x361 hitbox is the ACTUAL damage area within the sprite
                        float hitboxLeft = boltPos.x - (scaledHitboxWidth / 2);
                        float hitboxTop = boltPos.y - scaledHitboxHeight;  // Top of the hitbox (sprite goes from top to pos.y)
                        
                        Rectangle lightningHitbox = new Rectangle(hitboxLeft, hitboxTop, scaledHitboxWidth, scaledHitboxHeight);
                        
                        Gdx.app.log("LightningDMG", "  Bolt at " + boltPos + ", hitbox=" + lightningHitbox);
                        Gdx.app.log("LightningDMG", "    Lightning: X[" + lightningHitbox.x + " to " + (lightningHitbox.x + lightningHitbox.width) + "], Y[" + lightningHitbox.y + " to " + (lightningHitbox.y + lightningHitbox.height) + "]");
                        Gdx.app.log("LightningDMG", "    Player: X[" + playerBounds.x + " to " + (playerBounds.x + playerBounds.width) + "], Y[" + playerBounds.y + " to " + (playerBounds.y + playerBounds.height) + "]");
                        
                        // Check collision
                        boolean overlaps = lightningHitbox.overlaps(playerBounds);
                        Gdx.app.log("LightningDMG", "    overlaps()=" + overlaps);
                        if (overlaps) {
                            Gdx.app.log("LightningDMG", "  COLLISION DETECTED!");
                            boolean damageApplied = player.getHealthSystem().damage(damageValue, this);
                            if (damageApplied) {
                                Gdx.app.log("Lightning", "HIT! Frame: " + currentFrame + ", Damage: " + damageValue + ", Health: " + player.getHealthSystem().getCurrentHealth());
                            } else {
                                Gdx.app.log("Lightning", "Damage returned false");
                            }
                        } else {
                            Gdx.app.log("LightningDMG", "  No collision");
                        }
                    }
                } else {
                    Gdx.app.log("LightningDMG", "Player or health system is null!");
                }
            }
        }
    }


    
    @Override
    public void render(SpriteBatch batch) {
        // Render guardians
        guardian1.render(batch);
        guardian2.render(batch);
        guardian3.render(batch);
        
        // Render lightning effects if active
        if (lightningActive && lightningAnimator != null) {
            float scaledWidth = lightningWidth * lightningScale;
            float scaledHeight = lightningHeight * lightningScale;
            
            // Render random lightning bolts with individual delays
            for (int i = 0; i < randomLightningPositions.size(); i++) {
                if (i < randomLightningDelays.size()) {
                    float delay = randomLightningDelays.get(i);
                    if (lightningAnimTimer < delay) {
                        continue; // not yet spawned
                    }
                }
                Vector2 pos = randomLightningPositions.get(i);
                lightningAnimator.render(batch, pos.x - scaledWidth / 2, 
                                        pos.y - scaledHeight, 
                                        scaledWidth, scaledHeight);
            }
        }
    }
    
    public void renderDebug(ShapeRenderer shape) {
        if (!debugDisplay) return;
        
        // Draw guardian sprite bounds (light color)
        shape.setColor(new Color(1f, 1f, 0f, 0.3f)); // Transparent yellow
        shape.rect(guardian1.getBounds().x, guardian1.getBounds().y, 
                  guardian1.getBounds().width, guardian1.getBounds().height);
        shape.rect(guardian2.getBounds().x, guardian2.getBounds().y, 
                  guardian2.getBounds().width, guardian2.getBounds().height);
        shape.rect(guardian3.getBounds().x, guardian3.getBounds().y, 
                  guardian3.getBounds().width, guardian3.getBounds().height);
        
        // Draw actual hitboxes (bright color, respects flip)
        shape.setColor(Color.RED);
        Rectangle g1Hit = guardian1.getHitbox();
        Rectangle g2Hit = guardian2.getHitbox();
        Rectangle g3Hit = guardian3.getHitbox();
        shape.rect(g1Hit.x, g1Hit.y, g1Hit.width, g1Hit.height);
        shape.rect(g2Hit.x, g2Hit.y, g2Hit.width, g2Hit.height);
        shape.rect(g3Hit.x, g3Hit.y, g3Hit.width, g3Hit.height);
        
        // Draw triangle formation lines
        shape.setColor(Color.CYAN);
        shape.line(guardian1.position, guardian2.position);
        shape.line(guardian2.position, guardian3.position);
        shape.line(guardian3.position, guardian1.position);
        
        // Debug: Draw boss2 attack hitbox during plunge
        if (boss2State == Boss2AttackState.PLUNGING) {
            shape.setColor(Color.RED);
            shape.rect(boss2HitboxForDebug.x, boss2HitboxForDebug.y, 
                      boss2HitboxForDebug.width, boss2HitboxForDebug.height);
        }
        
        // Debug: Draw lightning damage hitboxes (cyan with light transparency)
        if (lightningActive && randomLightningPositions.size() > 0) {
            shape.setColor(new Color(0f, 1f, 1f, 0.4f)); // Cyan with transparency
            float scaledHitboxWidth = LIGHTNING_HITBOX_WIDTH * lightningScale;
            float scaledHitboxHeight = LIGHTNING_HITBOX_HEIGHT * lightningScale;
            
            for (Vector2 boltPos : randomLightningPositions) {
                float hitboxLeft = boltPos.x - (scaledHitboxWidth / 2);
                float hitboxTop = boltPos.y - scaledHitboxHeight;
                shape.rect(hitboxLeft, hitboxTop, scaledHitboxWidth, scaledHitboxHeight);
            }
        }
        
        // Draw boss center
        shape.setColor(Color.GREEN);
        shape.circle(bossCenter.x, bossCenter.y, 5f);
        
        // Draw target position
        shape.setColor(Color.MAGENTA);
        shape.circle(targetX, targetY, 8f);
        
        // Draw boss2 state info
        shape.setColor(Color.WHITE);
        if (debugFont != null) {
            shape.end();
            // Note: font rendering requires batch, not shape renderer
            // State info can be logged to console or drawn via batch in a separate method
            shape.begin(ShapeRenderer.ShapeType.Line);
        }
    }
    
    /**
     * Render debug text information via SpriteBatch.
     * Call this after shape.end() and batch.begin().
     */
    public void renderDebugText(SpriteBatch batch) {
        if (!debugDisplay || debugFont == null) return;
        
        debugFont.setColor(Color.WHITE);
        float y = 100f;
        float lineHeight = 20f;
        
        debugFont.draw(batch, "BossGuardian Debug", 10f, y);
        y -= lineHeight;
        debugFont.draw(batch, "Center: " + String.format("(%.1f, %.1f)", bossCenter.x, bossCenter.y), 10f, y);
        y -= lineHeight;
        debugFont.draw(batch, "Target: " + String.format("(%.1f, %.1f)", targetX, targetY), 10f, y);
        y -= lineHeight;
        debugFont.draw(batch, "Boss2 State: " + boss2State, 10f, y);
        y -= lineHeight;
        debugFont.draw(batch, "Boss2 Frame: " + guardian3.getAttackFrameIndex(), 10f, y);
        y -= lineHeight;
        debugFont.draw(batch, "Boss2 Cooldown: " + String.format("%.2f", boss2CooldownTimer), 10f, y);
        y -= lineHeight;
        debugFont.draw(batch, "G1 Attacking: " + guardian1.attacking, 10f, y);
        y -= lineHeight;
        debugFont.draw(batch, "G2 Attacking: " + guardian2.attacking, 10f, y);
        y -= lineHeight;
        debugFont.draw(batch, "G3 Attacking: " + guardian3.attacking, 10f, y);
        
    }
    
    public Rectangle getHitbox() {
        // Combined hitbox covering all three guardians
        float minX = Math.min(guardian1.position.x, Math.min(guardian2.position.x, guardian3.position.x));
        float maxX = Math.max(guardian1.position.x, Math.max(guardian2.position.x, guardian3.position.x));
        float minY = Math.min(guardian1.position.y, Math.min(guardian2.position.y, guardian3.position.y));
        float maxY = Math.max(guardian1.position.y, Math.max(guardian2.position.y, guardian3.position.y));
        
        return new Rectangle(minX - 150f, minY - 150f, maxX - minX + 300f, maxY - minY + 300f);
    }
    
    public void dispose() {
        guardian1.dispose();
        guardian2.dispose();
        guardian3.dispose();
        if (debugFont != null) debugFont.dispose();
    }
}

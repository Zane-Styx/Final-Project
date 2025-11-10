package com.jjmc.chromashift.entity.boss;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.effects.SFX;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.Wall;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.Color;

/**
 * Example boss instance with configured attacks and health.
 */
public class BossInstance extends Boss {
    // store last known player position (updated when setTarget is called)
    private float lastPlayerX = 0f;
    private float lastPlayerY = 0f;
    private float prevPlayerX = 0f;  // Previous frame's player position for movement prediction
    private float prevPlayerY = 0f;
    // Configurable ground offset for attack 2 (distance from player Y position)
    public float groundAttackOffset = 100f;
    // Flag to track if an attack is in progress
    private boolean isAttacking = false;
    // References to game objects for collision detection
    private Array<Solid> solids;
    private Array<Wall> walls;
    // keep references to the attacks so debug keys can trigger them
    private Attack debugAttack1, debugAttack2, debugAttack3, debugAttack4;
    // Pools to reuse SFX instances and reduce allocations
    private final List<SFX> indicatorPool = new ArrayList<>();
    private final List<SFX> attackPool = new ArrayList<>();

    

    public void setEnvironment(Array<Solid> solids, Array<Wall> walls) {
        this.solids = solids;
        this.walls = walls;
    }

    public BossInstance() {
        super(1000f); // 1000 health points

        // debug overlay font (constructed on game thread)
        try {
            debugFont = new BitmapFont();
            debugFont.getData().setScale(1.0f);
            debugFont.setColor(Color.WHITE);
        } catch (Exception ignored) {}

        // Explicit mapping of sprite rows for each attack (0-based indices)
        final int ROW_ATTACK_1 = 0; // attack one -> row 1 in editor (0-based index 0)
        final int ROW_ATTACK_2 = 1; // attack two -> row 2
        final int ROW_ATTACK_3 = 2; // attack three -> row 3
        final int ROW_ATTACK_4 = 3; // attack four -> row 4

        // Position the boss in the world (can be changed later)
        setPosition(0f, 0f);

    // Use custom scheduler (this instance will manage attack patterns)
    setUseCustomAttackScheduler(true);

        // Create attack definitions with staggered spawning to prevent freezing
        final String attackSprite = "entity/boss/attack/boss_attack.png";
        final int spriteRows = 4;
        final int spriteCols = 25;
        final float attackWidth = 182f;
        final float attackHeight = 182f;

        // Prewarm SFX pools to avoid first-use GL stalls when attacks trigger.
        // Instead of creating everything in a single frame (which causes a big
        // hitch), stagger creation across several frames using scheduleAction
        // so texture uploads happen gradually during idle frames.
        try {
            for (int i = 0; i < 6; i++) {
                final int idx = i;
                // small stagger so work is spread over frames
                float delay = 0.02f * idx;
                scheduleAction(delay, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SFX indicator = new SFX(attackSprite, spriteRows, spriteCols, attackWidth, attackHeight * 0.5f);
                            indicator.addAnimation("warning", 0, 0, 5, 0.08f, true);
                            indicator.setAutoRemove(false);
                            indicator.reset();
                            // mark finished so obtainPooledSFX can find it for reuse
                            indicator.markFinishedForReuse();
                            indicatorPool.add(indicator);

                            SFX atk = new SFX(attackSprite, spriteRows, spriteCols, attackWidth, attackHeight);
                            atk.addAnimation("attack", ROW_ATTACK_1, 0, 25, 0.03f, false);
                            atk.setAutoRemove(true);
                            atk.reset();
                            atk.markFinishedForReuse();
                            attackPool.add(atk);
                        } catch (Exception ignored) {
                            // ignore errors during staggered prewarm
                        }
                    }
                });
            }
        } catch (Exception ignored) {
            // If scheduling fails, fall back to on-demand creation.
        }
    
        // Attack 1 - Directional wave attack based on player movement
        debugAttack1 = new Attack(4.5f) { // longer cooldown for powerful attack
            @Override
            protected void start(final Boss boss) {
                final BossInstance bossInst = (boss instanceof BossInstance) ? (BossInstance) boss : null;
                final float px = bossInst != null ? bossInst.lastPlayerX : boss.getX();
                final float py = bossInst != null ? bossInst.lastPlayerY : boss.getY();

                // Determine attack direction based on player movement
                float moveX = bossInst != null ? (bossInst.lastPlayerX - bossInst.prevPlayerX) : 0;
                boolean movingRight = moveX >= 0; // true if moving right or standing still

                if (bossInst != null && bossInst.isAttacking) {
                    return; // Don't start another attack if one is in progress
                }
                if (bossInst != null) {
                    bossInst.isAttacking = true;
                }

                // Calculate spacing and positions
                float spacing = attackWidth * 0.5f; // Closer spacing between attacks
                float firstAttackX = !movingRight ? px - (spacing * 2.5f) : px + (spacing * 2.5f);

                // No warning indicators for this attack (removed to reduce allocations/hitches)

                // Spawn 6 attacks in sequence based on movement direction
                for (int i = 0; i < 6; i++) {
                    final int index = i;
                    boss.scheduleAction(0.6f + (i * 0.15f), new Runnable() {
                        public void run() {
                            float xOffset = !movingRight ? (index * spacing) : (-index * spacing);
                            final float spawnX = firstAttackX + xOffset - attackWidth / 2;
                            final float spawnY = py - attackHeight + 150;
                            // obtain or create attack SFX from pool
                            SFX atk = obtainPooledSFX(attackPool, new Supplier<SFX>() {
                                @Override
                                public SFX get() {
                                    SFX s = new SFX(attackSprite, spriteRows, spriteCols, attackWidth, attackHeight);
                                    s.addAnimation("attack", ROW_ATTACK_1, 0, 25, 0.03f, false);
                                    s.setAutoRemove(true);
                                    return s;
                                }
                            });
                            if (atk != null) {
                                // Ensure this pooled instance uses the correct animation definition
                                atk.addAnimation("attack", ROW_ATTACK_1, 0, 25, 0.03f, false);
                                atk.reset();
                                atk.play("attack");
                                boss.spawnEffect(atk, spawnX, spawnY);
                            }

                            // No indicator to remove for this attack

                            // Reset attacking flag after last impact
                            if (index == 5 && bossInst != null) {
                                bossInst.isAttacking = false;
                            }
                        }
                    });
                }
            }
        };
        addAttack(debugAttack1);
    
    // Configure phase-specific attack priorities and frequency multipliers
        // Attack 2 definition (ground eruption)
        debugAttack2 = new Attack(6.0f) {
            @Override
            protected void start(final Boss boss) {
                final BossInstance bossInst = (boss instanceof BossInstance) ? (BossInstance) boss : null;
                if (bossInst != null && bossInst.isAttacking) {
                    return; // Don't start if another attack is in progress
                }
                if (bossInst != null) {
                    bossInst.isAttacking = true;
                }

                final float px = bossInst != null ? bossInst.lastPlayerX : boss.getX();
                final float py = bossInst != null ? bossInst.lastPlayerY : boss.getY();

                // Find the nearest ground below the player
                float groundY = py - groundAttackOffset; // default ground level
                // Check all solids and walls for a higher ground
                if (bossInst != null) {
                    Rectangle searchArea = new Rectangle(
                            px - (attackWidth / 2),
                            py - groundAttackOffset,
                            attackWidth,
                            groundAttackOffset
                    );

                    // Check solid objects
                    if (bossInst.solids != null) {
                        for (Solid solid : bossInst.solids) {
                            if (!solid.isBlocking()) continue;
                            Rectangle bounds = solid.getCollisionBounds();
                            if (bounds != null && bounds.overlaps(searchArea)) {
                                float solidTop = bounds.y + bounds.height;
                                if (solidTop < py && solidTop > groundY) {
                                    groundY = solidTop;
                                }
                            }
                        }
                    }

                    // Check walls
                    if (bossInst.walls != null) {
                        for (Wall wall : bossInst.walls) {
                            if (wall.getBounds().overlaps(searchArea)) {
                                float wallTop = wall.getBounds().y + wall.getBounds().height;
                                if (wallTop < py && wallTop > groundY) {
                                    groundY = wallTop;
                                }
                            }
                        }
                    }
                }

                // Ground crack warning effect (positioned at ground level)
                final AtomicReference<SFX> warningRef = new AtomicReference<>();
                final float warnX = px - attackWidth / 2;
                final float warnY = groundY - 30;
                // obtain a pooled indicator (should be prewarmed); reset and spawn synchronously
                SFX warning = obtainPooledSFX(indicatorPool, new Supplier<SFX>() {
                    @Override
                    public SFX get() {
                        SFX w = new SFX(attackSprite, spriteRows, spriteCols, attackWidth, attackHeight * 0.5f);
                        // use the same animation mapping as the prewarmed indicators (row 0)
                        w.addAnimation("warning", 0, 0, 5, 0.08f, true);
                        return w;
                    }
                });
                if (warning != null) {
                    warning.reset();
                    // Ensure the pooled instance has the expected animation mapping for this attack
                    warning.addAnimation("warning", 0, 0, 5, 0.08f, true);
                    warning.play("warning");
                    boss.spawnEffect(warning, warnX, warnY);
                    warningRef.set(warning);
                }

                // Schedule the eruption
                final float eruption_px = px;
                final float eruption_y = groundY;
                boss.scheduleAction(2.0f, new Runnable() {
                    public void run() {
                        // Vertical eruption effect (use pooled SFX to avoid GL allocations)
                                SFX eruption = obtainPooledSFX(attackPool, new Supplier<SFX>() {
                            @Override
                            public SFX get() {
                                SFX s = new SFX(attackSprite, spriteRows, spriteCols, attackWidth, attackHeight * 2f);
                                s.addAnimation("attack", ROW_ATTACK_2, 0, 20, .25f, false);
                                s.setAutoRemove(true);
                                return s;
                            }
                        });
                        if (eruption != null) {
                            // ensure pooled instance has correct animation row for the eruption
                            eruption.addAnimation("attack", ROW_ATTACK_2, 0, 20, .1f, false);
                            eruption.reset();
                            eruption.play("attack");
                            boss.spawnEffect(eruption, eruption_px - attackWidth / 2, eruption_y - 35);
                        }
                        boss.safeRemoveEffect(warningRef.get());

                        // Reset attack flag after animation completes
                        boss.scheduleAction(0.25f, new Runnable() {
                            public void run() {
                                if (bossInst != null) {
                                    bossInst.isAttacking = false;
                                }
                            }
                        });
                    }
                });
            }
        };
        addAttack(debugAttack2);

        // Attack 3 - Quick burst attack (original)
        debugAttack3 = new Attack(2.5f) {
            @Override
            protected void start(Boss boss) {
                final BossInstance bossInst = (boss instanceof BossInstance) ? (BossInstance) boss : null;
                if (bossInst != null && bossInst.isAttacking) {
                    return; // Don't start if another attack is in progress
                }
                if (bossInst != null) {
                    bossInst.isAttacking = true;
                }

                float px = bossInst != null ? bossInst.lastPlayerX : boss.getX();
                float py = bossInst != null ? bossInst.lastPlayerY : boss.getY();
                // Use pooled SFX for quick burst attack to avoid GL allocations during spawn.
                        SFX burst = obtainPooledSFX(attackPool, new Supplier<SFX>() {
                    @Override
                    public SFX get() {
                        SFX s = new SFX(attackSprite, spriteRows, spriteCols, attackWidth, attackHeight);
                        s.addAnimation("attack", ROW_ATTACK_3, 0, 20, 0.04f, false);
                        s.setAutoRemove(true);
                        return s;
                    }
                });
                if (burst != null) {
                    // ensure the pooled instance has the correct animation mapping for this attack
                    burst.addAnimation("attack", ROW_ATTACK_3, 0, 20, 0.04f, false);
                    burst.reset();
                    burst.play("attack");
                    boss.spawnEffect(burst, px - attackWidth / 2, py - attackHeight / 2);
                }

                // Reset attack flag after animation completes
                boss.scheduleAction(0.04f * 20, new Runnable() {
                    public void run() {
                        if (bossInst != null) {
                            bossInst.isAttacking = false;
                        }
                    }
                });
            }
        };
        addAttack(debugAttack3);

        // Attack 4 - Directional wave attack like Attack 1 but with different animation
        debugAttack4 = new Attack(5.0f) { // longer cooldown since it's a wave
            @Override
            protected void start(final Boss boss) {
                final BossInstance bossInst = (boss instanceof BossInstance) ? (BossInstance) boss : null;
                if (bossInst != null && bossInst.isAttacking) {
                    return; // Don't start if another attack is in progress
                }
                if (bossInst != null) {
                    bossInst.isAttacking = true;
                }

                final float px = bossInst != null ? bossInst.lastPlayerX : boss.getX();
                final float py = bossInst != null ? bossInst.lastPlayerY : boss.getY();

                // Determine attack direction based on player movement
                float moveX = bossInst != null ? (bossInst.lastPlayerX - bossInst.prevPlayerX) : 0;
                boolean movingRight = moveX >= 0;

                // Calculate spacing and positions
                float spacing = attackWidth * 0.25f;
                float firstAttackX = !movingRight ? px - (spacing * 2.5f) : px + (spacing * 2.5f);

                // No warning indicators for this attack (removed to cut allocations/hitches)

                // Spawn attacks in sequence
                for (int i = 0; i < 13; i++) {
                    final int index = i;
                    boss.scheduleAction(1.5f + (i * 0.25f), new Runnable() {
                        public void run() {
                            float xOffset = !movingRight ? (index * spacing) : (-index * spacing);
                            final float spawnX = firstAttackX + xOffset - attackWidth / 2;
                            final float spawnY = py - attackHeight + 150;
                            // Create attack SFX on the GL thread to avoid OpenGL calls from background threads
                            // Use pooled attack SFX to avoid creating a new SpriteAnimator here
                            SFX wave = obtainPooledSFX(attackPool, new Supplier<SFX>() {
                                @Override
                                public SFX get() {
                                    SFX s = new SFX(attackSprite, spriteRows, spriteCols, attackWidth, attackHeight);
                                    s.addAnimation("attack", ROW_ATTACK_4, 0, 20, 0.05f, false);
                                    s.setAutoRemove(true);
                                    return s;
                                }
                            });
                            if (wave != null) {
                                // Ensure a pooled instance gets the correct animation mapping
                                wave.addAnimation("attack", ROW_ATTACK_4, 0, 20, 0.05f, false);
                                wave.reset();
                                wave.play("attack");
                                boss.spawnEffect(wave, spawnX, spawnY);
                            }

                            // No indicator to remove for this attack

                            // Reset attacking flag after last impact
                            if (index == 12 && bossInst != null) {
                                bossInst.isAttacking = false;
                            }
                        }
                    });
                }
            }
        };
        addAttack(debugAttack4);

    // Configure phase-specific weights and per-attack cooldowns.
    // Attacks are in order: 0=attack1, 1=attack2, 2=attack3, 3=attack4
    // Phase weights (higher = more likely). These arrays map to attack indices above.
    // Phase 1: mostly attacks 1 and 2
    configurePhaseWeights(1, new float[]{0.40f, 0.40f, 0.10f, 0.10f});
    // Phase 2: balanced across all attacks
    configurePhaseWeights(2, new float[]{0.25f, 0.25f, 0.25f, 0.25f});
    // Phase 3: prioritize attacks 3 and 4 (more frequent)
    configurePhaseWeights(3, new float[]{0.10f, 0.10f, 0.40f, 0.40f});

    // Per-attack per-phase cooldown overrides (index 0 unused — indexes 1..3 = phases)
    // If you leave these unset, the boss will fall back to baseCooldown * phaseMultiplier.
    debugAttack1.configurePerPhaseCooldowns(new float[]{0f, 4.5f, 4.0f, 3.5f}); // attack1 speeds up across phases
    debugAttack2.configurePerPhaseCooldowns(new float[]{0f, 6.0f, 5.0f, 4.0f}); // ground eruption slows slightly then speeds
    debugAttack3.configurePerPhaseCooldowns(new float[]{0f, 2.5f, 2.0f, 1.5f}); // quick burst becomes quicker
    debugAttack4.configurePerPhaseCooldowns(new float[]{0f, 5.0f, 4.0f, 3.0f}); // wave attack becomes faster in later phases

    // Keep general per-phase multipliers as fallbacks (optional tuning)
    configurePhaseCooldownMultiplier(1, 1.0f);
    configurePhaseCooldownMultiplier(2, 0.85f);
    configurePhaseCooldownMultiplier(3, 0.7f);
    }

    // Helper: obtain pooled SFX instance (reuses finished instances or creates new via supplier)
    // Must be called on the GL thread when creating new instances
    private SFX obtainPooledSFX(List<SFX> pool, Supplier<SFX> creator) {
        for (SFX s : pool) {
            if (s.isFinished()) {
                return s;
            }
        }
        long t0 = Boss.SFX_PROFILING ? System.nanoTime() : 0L;
        SFX n = creator.get();
        long t1 = Boss.SFX_PROFILING ? System.nanoTime() : 0L;
        pool.add(n);
        if (Boss.SFX_PROFILING) {
            try { com.badlogic.gdx.Gdx.app.log("SFX_PROF", "obtainPooledSFX: createdMillis=" + ((t1-t0)/1_000_000.0)); } catch (Exception ignored) {}
        }
        return n;
    }

    // --- Debug overlay ---
    private BitmapFont debugFont;
    private boolean debugOverlayEnabled = true;
    private final GlyphLayout glyph = new GlyphLayout();

    // --- Custom scheduling state ---
    private boolean phase1ChainActive = false;
    // Phase1 patterns (A/B/C)
    private final int[][] phase1Patterns = new int[][]{
        {1, 3, 1, 2}, // A: attack2 -> attack4 -> attack2 -> attack3
        {3, 1, 2, 3}, // B: attack4 -> attack2 -> attack3 -> attack4
        {2, 1, 3, 1}  // C: attack3 -> attack2 -> attack4 -> attack2
    };
    private int phase1PatternIndex = 0;
    private final float phase1Pause = 0.75f; // slightly longer pause between attacks in phase1 sequence

    private int phase2RandomsSinceGuaranteed = 0;
    private int lastStartedAttackIndex = -1;
    // Phase2 patterns (A/B/C)
    private final int[][] phase2Patterns = new int[][]{
        {0, 3, 1, 2}, // A: 1,4,2,3
        {3, 1, 0, 3}, // B: 4,2,1,4
        {2, 3, 0, 1}  // C: 3,4,1,2
    };
    private int phase2PatternIndex = 0;

    // Phase3 sequence state
    // Phase3 patterns (A/B/C) — sequences of 5 attacks each
    private final int[][] phase3Patterns = new int[][]{
        {0, 3, 2, 1, 3}, // A: 1,4,3,2,4
        {1, 0, 2, 3, 1}, // B: 2,1,3,4,2
        {3, 2, 0, 1, 2}  // C: 4,3,1,2,3
    };
    private int phase3PatternIndex = 0;
    private int phase3SeqPos = 0;
    private int phase3SeqRepeatCount = 0;
    private final float phase3AttackPause = 0.9f; // slightly longer
    private final float phase3ShortPause = 0.6f;

    // Low-HP modifiers
    private final float lowHpThreshold = 0.15f;
    private float attackDamageMultiplier = 1.0f; // exposed for attacks to use if desired

    @Override
    public void disposeParts() {
        // Dispose animators first
        super.disposeParts();
        // Dispose debug font
        if (debugFont != null) {
            try { debugFont.dispose(); } catch (Exception ignored) {}
            debugFont = null;
        }
    }

    @Override
    public void render(com.badlogic.gdx.graphics.g2d.SpriteBatch batch) {
        // Render boss visuals and active effects
        super.render(batch);

        // Draw debug overlay if enabled and font is available
        if (!debugOverlayEnabled || debugFont == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            int p = getPhase();
            sb.append("Phase: ").append(p).append('\n');

            // Phase weights
            float[] weights = getPhaseWeights(p);
            if (weights != null) {
                sb.append("Weights: ");
                for (int i = 0; i < weights.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(String.format("%d:%.2f", i, weights[i]));
                }
                sb.append('\n');
            }

            // Per-attack cooldowns for current phase
            List<Attack> atks = getAttacks();
            for (int i = 0; i < atks.size(); i++) {
                Attack a = atks.get(i);
                float base = a.getCooldown();
                float phaseCd = a.getCooldownForPhase(this);
                String name = a.getClass().getSimpleName();
                if (name == null || name.isEmpty()) name = "Attack#" + i;
                sb.append(String.format("%s (idx %d): base=%.2fs phase=%.2fs", name, i, base, phaseCd));
                if (i < atks.size()-1) sb.append('\n');
            }

            // Draw text in top-left corner above boss position
            float drawX = getX() - 200f; // offset left of boss
            float drawY = getY() + 300f; // above boss
            glyph.setText(debugFont, sb.toString());
            debugFont.draw(batch, glyph, drawX, drawY);
        } catch (Exception ignored) {}
    }

    // --- Custom scheduler helpers ---
    private boolean tryStartAttackIndex(int idx) {
        List<Attack> atks = getAttacks();
        if (atks == null || idx < 0 || idx >= atks.size()) return false;
        Attack a = atks.get(idx);
        if (a.tryStart(this)) {
            // set boss cooldown so automatic scheduling (if any) doesn't interfere
            setAttackCooldownTimer(a.getCooldownForPhase(this));
            lastStartedAttackIndex = idx;
            return true;
        }
        return false;
    }

    private void startPhase1Chain() {
        if (phase1ChainActive) return;
        phase1ChainActive = true;
        final int patternsLen = phase1Patterns.length;
        int chosen1 = phase1PatternIndex;
        if (patternsLen > 1) {
            int attempts = 0;
            while (attempts++ < 5) {
                chosen1 = (int) (Math.random() * patternsLen);
                if (chosen1 != phase1PatternIndex) break;
            }
        }
        final int[] pattern = phase1Patterns[chosen1];
        phase1PatternIndex = chosen1;
        // schedule the pattern attacks in sequence
        for (int i = 0; i < pattern.length; i++) {
            final int attackIdx = pattern[i];
            final float delay = i * phase1Pause;
            scheduleAction(delay, new Runnable() {
                @Override
                public void run() {
                    if (getPhase() != 1) { phase1ChainActive = false; return; }
                    tryStartAttackIndex(attackIdx);
                }
            });
        }
        // after the pattern, allow next pattern to start (loop)
        scheduleAction(pattern.length * phase1Pause + 0.05f, new Runnable() {
            @Override
            public void run() {
                if (getPhase() != 1) { phase1ChainActive = false; return; }
                // pattern index was randomized at start; leave as chosen
                phase1ChainActive = false; // allow restart which will schedule next loop
            }
        });
    }

    private boolean phase2ChainActive = false;
    private final float phase2Pause = 0.5f;
    private void startPhase2Chain() {
        if (phase2ChainActive) return;
        phase2ChainActive = true;
        final int[][] patterns = phase2Patterns;
        int patternsLen = patterns.length;
        int chosen2 = phase2PatternIndex;
        if (patternsLen > 1) {
            int attempts = 0;
            while (attempts++ < 6) {
                chosen2 = (int) (Math.random() * patternsLen);
                if (chosen2 != phase2PatternIndex) break;
            }
        }
        int[] pattern = patterns[chosen2];
        phase2PatternIndex = chosen2;
        // if the pattern would start with the same attack that just ran, try a different one
        if (pattern.length > 0 && pattern[0] == lastStartedAttackIndex) {
            for (int i = 0; i < patternsLen; i++) {
                int alt = (chosen2 + 1 + i) % patternsLen;
                if (patterns[alt].length > 0 && patterns[alt][0] != lastStartedAttackIndex) {
                    pattern = patterns[alt];
                    phase2PatternIndex = alt;
                    break;
                }
            }
        }
        // schedule the pattern
        for (int i = 0; i < pattern.length; i++) {
            final int attackIdx = pattern[i];
            final float delay = i * phase2Pause;
            scheduleAction(delay, new Runnable() {
                @Override
                public void run() {
                    if (getPhase() != 2) { phase2ChainActive = false; return; }
                    tryStartAttackIndex(attackIdx);
                }
            });
        }
        // after pattern, advance and allow restart
        scheduleAction(pattern.length * phase2Pause + 0.05f, new Runnable() {
            @Override
            public void run() {
                if (getPhase() != 2) { phase2ChainActive = false; return; }
                // randomized already; allow next run to randomize again
                phase2ChainActive = false;
            }
        });
    }

    private boolean phase3ChainActive = false;
    private void startPhase3Chain() {
        if (phase3ChainActive) return;
        phase3ChainActive = true;
        final int patternsLen = phase3Patterns.length;
        int chosen3 = phase3PatternIndex;
        if (patternsLen > 1) {
            int attempts = 0;
            while (attempts++ < 6) {
                chosen3 = (int)(Math.random() * patternsLen);
                if (chosen3 != phase3PatternIndex) break;
            }
        }
        final int[] pattern = phase3Patterns[chosen3];
        phase3PatternIndex = chosen3;
        // Determine low-HP speed multiplier
        float speedMult = 1.0f;
        if (getHealthSystem() != null) {
            float cur = getHealthSystem().getCurrentHealth();
            float max = getHealthSystem().getMaxHealth();
            if (max > 0f && (cur / max) <= lowHpThreshold) speedMult = 0.75f; // faster when low HP
        }
        final float pause = phase3AttackPause * speedMult;
        // schedule pattern twice
        float offset = 0f;
        for (int rep = 0; rep < 2; rep++) {
            for (int s = 0; s < pattern.length; s++) {
                final int idx = pattern[s];
                final float delay = offset + s * pause;
                scheduleAction(delay, new Runnable() {
                    @Override
                    public void run() {
                        if (getPhase() != 3) { phase3ChainActive = false; return; }
                        tryStartAttackIndex(idx);
                    }
                });
            }
            offset += pattern.length * pause;
        }
        // after two repeats, short pause then allow restart
        final float totalDur = offset + phase3ShortPause * speedMult;
        scheduleAction(totalDur, new Runnable() {
            @Override
            public void run() {
                if (getPhase() != 3) { phase3ChainActive = false; return; }
                // randomized already; allow next run to randomize again
                phase3ChainActive = false;
            }
        });
    }

    @Override
    public void update(float delta) {
        super.update(delta);

        // Debug controls: press 1-4 to manually trigger each configured attack.
        try {
            if (!isAttacking) {  // Only allow new attacks if no attack is in progress
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
                    if (debugAttack1 != null) debugAttack1.tryStart(this);
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
                    if (debugAttack2 != null) debugAttack2.tryStart(this);
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
                    if (debugAttack3 != null) debugAttack3.tryStart(this);
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) {
                    if (debugAttack4 != null) debugAttack4.tryStart(this);
                }
            }
        } catch (Exception ignored) {
        }

        // Toggle debug overlay with F2
        try {
            if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
                debugOverlayEnabled = !debugOverlayEnabled;
            }
        } catch (Exception ignored) {}
        // Custom attack scheduler (this instance manages patterns per phase)
        try {
            // adjust low-HP modifiers
            if (getHealthSystem() != null) {
                float cur = getHealthSystem().getCurrentHealth();
                float max = getHealthSystem().getMaxHealth();
                if (max > 0f && (cur / max) <= lowHpThreshold) {
                    attackDamageMultiplier = 1.15f;
                } else {
                    attackDamageMultiplier = 1.0f;
                }
            }

            int p = getPhase();
            // Phase 1: patterned sequences until phase change
            if (p == 1) {
                // Ensure the chain is running
                startPhase1Chain();
            }

            // Phase 2: random (no same twice); after 3 randoms -> guaranteed attack4
            if (p == 2) {
                // Use patterned sequences for phase2
                startPhase2Chain();
            }

            // Phase 3: deterministic sequence [1,4,3,2] repeat twice, short pause, repeat
            if (p == 3) {
                // start a chained sequence if not active
                startPhase3Chain();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void setTarget(float x, float y) {
        // Store previous position before updating
        this.prevPlayerX = this.lastPlayerX;
        this.prevPlayerY = this.lastPlayerY;
        // capture last known player position for attacks that target the player
        this.lastPlayerX = x;
        this.lastPlayerY = y;
        super.setTarget(x, y);
    }
}
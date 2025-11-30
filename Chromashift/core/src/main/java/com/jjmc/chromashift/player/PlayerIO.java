package com.jjmc.chromashift.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;

import java.io.File;

/**
 * Simple JSON-based player save/load utility.
 * Saves to workspace `assets/saves/<name>` when possible and mirrors into
 * build resources when available.
 */
public class PlayerIO {

    public static class PlayerState {
        public float x, y;
        public float velocityX, velocityY;
        public boolean facingLeft;
        public boolean onGround;
        public boolean canJump;
        public boolean dashing;
        public float dashTimer;
        public float dashCooldownTimer;
        public boolean dashUsed;
        public float dashHoverRemaining;

        public boolean attacking;
        public boolean airAttacking;
        public float airAttackTimer;
        public float attackCooldownTimer;

        public int shield;
        public int keyCount;
        public int potionCount;
        public float respawnX, respawnY;
        public float respawnInvulRemaining;
        public float respawnStunRemaining;
        public boolean isStunned;

        public int diamonds;
        public float healthCurrent;
        public float healthMax;

        // Skills
        public SkillState skillQ;
        public SkillState skillE;
        public SkillState activeSkill;

        public String currentLevel;
        public Array<String> visitedLevels;

        public static class SkillState {
            public String skillName;
            public float currentCooldown;
            public boolean isActive;
            public float animationTimer;
        }

        public PlayerState() {
            visitedLevels = new Array<>();
        }
    }

    private static final Json json = new Json();

    static {
        try { json.setIgnoreUnknownFields(true); } catch (Throwable ignored) {}
    }

    /**
     * Capture player state into a PlayerState object.
     * @param player source
     * @param currentLevel optional current level id
     * @param visited levels list (may be null)
     */
    public static PlayerState capture(Player player, String currentLevel, Array<String> visited) {
        PlayerState s = new PlayerState();
        s.x = player.getX();
        s.y = player.getY();
        s.velocityX = player.getVelocityX();
        s.velocityY = player.getVelocityY();
        s.facingLeft = player.isFacingLeft();
        s.onGround = player.isOnGround();
        s.canJump = player.getCanJump();
        s.dashing = player.isDashing();
        s.dashTimer = player.dashTimer;
        s.dashCooldownTimer = player.dashCooldownTimer;
        s.dashUsed = player.dashUsed;
        s.dashHoverRemaining = player.dashHoverRemaining;

        s.attacking = player.isAttacking();
        s.airAttacking = player.airAttacking;
        s.airAttackTimer = player.airAttackTimer;
        s.attackCooldownTimer = player.attackCooldownTimer;

        s.shield = player.getShield();
        s.keyCount = player.getKeyCount();
        s.potionCount = player.getPotionCount();
        s.respawnX = player.getRespawnX();
        s.respawnY = player.getRespawnY();
        s.respawnInvulRemaining = player.getRespawnInvulRemaining();
        s.respawnStunRemaining = player.getRespawnStunRemaining();
        s.isStunned = player.isStunned();

        s.diamonds = player.getDiamonds();
        if (player.getHealthSystem() != null) {
            s.healthCurrent = player.getHealthSystem().getCurrentHealth();
            s.healthMax = player.getHealthSystem().getMaxHealth();
        }

        // Skills
        com.jjmc.chromashift.player.skill.BaseSkill q = player.getSkillInSlot('Q');
        com.jjmc.chromashift.player.skill.BaseSkill e = player.getSkillInSlot('E');
        if (q != null) {
            s.skillQ = new PlayerState.SkillState();
            s.skillQ.skillName = q.getSkillName();
            s.skillQ.currentCooldown = q.getCurrentCooldown();
            s.skillQ.isActive = q.getActiveState();
            s.skillQ.animationTimer = q.getAnimationTimer();
        }
        if (e != null) {
            s.skillE = new PlayerState.SkillState();
            s.skillE.skillName = e.getSkillName();
            s.skillE.currentCooldown = e.getCurrentCooldown();
            s.skillE.isActive = e.getActiveState();
            s.skillE.animationTimer = e.getAnimationTimer();
        }
        if (player.getActiveSkill() != null) {
            s.activeSkill = new PlayerState.SkillState();
            s.activeSkill.skillName = player.getActiveSkill().getSkillName();
            s.activeSkill.currentCooldown = player.getActiveSkill().getCurrentCooldown();
            s.activeSkill.isActive = player.getActiveSkill().getActiveState();
            s.activeSkill.animationTimer = player.getActiveSkill().getAnimationTimer();
        }

        s.currentLevel = currentLevel;
        if (visited != null) s.visitedLevels = new Array<>(visited);
        return s;
    }

    /**
     * Apply a loaded PlayerState to a Player instance.
     * This will restore the basic fields and re-create skill instances when possible.
     */
    public static void applyToPlayer(Player player, PlayerState s) {
        if (s == null || player == null) return;
        try {
            player.setX(s.x);
            player.setY(s.y);
            player.setVelocityX(s.velocityX);
            player.setVelocityY(s.velocityY);
            player.setFacingLeft(s.facingLeft);
            player.setOnGround(s.onGround);
            player.setCanJump(s.canJump);
            player.setDashing(s.dashing);
            player.setDashTimer(s.dashTimer);
            player.setDashCooldownTimer(s.dashCooldownTimer);
            player.setDashUsed(s.dashUsed);
            player.setDashHover(s.dashHoverRemaining);

            player.setAttacking(s.attacking);
            player.setAirAttacking(s.airAttacking);
            player.setAirAttackTimer(s.airAttackTimer);
            player.setAttackCooldownTimer(s.attackCooldownTimer);

            player.setShield(s.shield);
            player.setKeyCount(s.keyCount);
            player.setPotionCount(s.potionCount);
            player.setRespawnPoint(s.respawnX, s.respawnY);
            player.setRespawnInvulRemaining(s.respawnInvulRemaining);
            player.setRespawnStunRemaining(s.respawnStunRemaining);
            player.setStunned(s.isStunned);

            player.setDiamonds(s.diamonds);
            if (player.getHealthSystem() != null) {
                player.getHealthSystem().setHealth(s.healthCurrent);
            }

            // Recreate skills if names present
            if (s.skillQ != null && s.skillQ.skillName != null) {
                player.equipSkillToSlot(createSkillByName(player, s.skillQ.skillName), 'Q');
                com.jjmc.chromashift.player.skill.BaseSkill qs = player.getSkillInSlot('Q');
                if (qs != null) {
                    qs.setCurrentCooldown(s.skillQ.currentCooldown);
                    qs.setAnimationTimer(s.skillQ.animationTimer);
                    qs.setActiveState(s.skillQ.isActive);
                    if (s.skillQ.isActive) player.setActiveSkill(qs);
                }
            }
            if (s.skillE != null && s.skillE.skillName != null) {
                player.equipSkillToSlot(createSkillByName(player, s.skillE.skillName), 'E');
                com.jjmc.chromashift.player.skill.BaseSkill es = player.getSkillInSlot('E');
                if (es != null) {
                    es.setCurrentCooldown(s.skillE.currentCooldown);
                    es.setAnimationTimer(s.skillE.animationTimer);
                    es.setActiveState(s.skillE.isActive);
                    if (s.skillE.isActive) player.setActiveSkill(es);
                }
            }
            // If activeSkill saved and not assigned yet, try to create it
            if (s.activeSkill != null && s.activeSkill.skillName != null && player.getActiveSkill() == null) {
                com.jjmc.chromashift.player.skill.BaseSkill as = createSkillByName(player, s.activeSkill.skillName);
                if (as != null) {
                    player.setActiveSkill(as);
                    as.setCurrentCooldown(s.activeSkill.currentCooldown);
                    as.setAnimationTimer(s.activeSkill.animationTimer);
                    as.setActiveState(s.activeSkill.isActive);
                }
            }

        } catch (Exception ex) {
            Gdx.app.error("PlayerIO", "Failed to apply PlayerState: " + ex.getMessage(), ex);
        }
    }

    private static com.jjmc.chromashift.player.skill.BaseSkill createSkillByName(Player p, String name) {
        try {
            if (name == null) return null;
            switch (name) {
                case "DashSkill":
                    return new com.jjmc.chromashift.player.skill.DashSkill(p);
                case "JumpSkill":
                    return new com.jjmc.chromashift.player.skill.JumpSkill(p);
                case "SlashSkill":
                    return new com.jjmc.chromashift.player.skill.SlashSkill(p);
                case "ShurikenSkill":
                    return new com.jjmc.chromashift.player.skill.ShurikenSkill(p);
                case "SplitSkill":
                    return new com.jjmc.chromashift.player.skill.SplitSkill(p);
                default:
                    // Unknown skill name — return null
                    return null;
            }
        } catch (Exception ex) {
            Gdx.app.error("PlayerIO", "Failed to create skill: " + name + ": " + ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Save the given PlayerState as JSON under `assets/saves/<filename>` (filename may include .json)
     */
    public static boolean saveToWorkspace(String filename, PlayerState state) {
        try {
            String text = json.prettyPrint(state);
            File assetsDir = findProjectAssetsDir();
            if (assetsDir == null) {
                Gdx.app.error("PlayerIO", "Project assets folder not found; aborting save for: " + filename);
                return false;
            }
            File out = new File(assetsDir, ("saves/" + filename).replace('/', File.separatorChar));
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Gdx.files.absolute(out.getAbsolutePath()).writeString(text, false);
            Gdx.app.log("PlayerIO", "Saved player state to workspace: " + out.getAbsolutePath());
            try { boolean ok = writeToBuildResources(("saves/" + filename), text); if (ok) Gdx.app.log("PlayerIO","Mirrored player save to build resources: " + filename); } catch (Exception ignored) {}
            return true;
        } catch (Exception ex) {
            Gdx.app.error("PlayerIO", "Failed to save player state: " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Load a PlayerState JSON from workspace `assets/saves/<filename>` if present, else try internal.
     */
    public static PlayerState load(String filename) {
        try {
            String relative = ("saves/" + filename).replace('\\', '/');
            // Try workspace first
            File assetsDir = findProjectAssetsDir();
            if (assetsDir != null) {
                File candidate = new File(assetsDir, relative.replace('/', File.separatorChar));
                if (candidate.exists()) {
                    String text = Gdx.files.absolute(candidate.getAbsolutePath()).readString();
                    try {
                        PlayerState s = json.fromJson(PlayerState.class, text);
                        return s;
                    } catch (Exception ex) {
                        Gdx.app.error("PlayerIO", "Failed to parse player save: " + ex.getMessage(), ex);
                    }
                }
            }
            // Fallback: try internal asset
            FileHandle internal = Gdx.files.internal(relative);
            if (internal != null && internal.exists()) {
                String text = internal.readString();
                try {
                    PlayerState s = json.fromJson(PlayerState.class, text);
                    return s;
                } catch (Exception ex) {
                    Gdx.app.error("PlayerIO", "Failed to parse internal player save: " + ex.getMessage(), ex);
                }
            }
        } catch (Exception ex) {
            Gdx.app.error("PlayerIO", "Error loading player save: " + ex.getMessage(), ex);
        }
        return null;
    }

    // --- Utilities copied from LevelIO for locating the workspace assets and build resources ---
    private static File findProjectAssetsDir() {
        try {
            File dir = new File(System.getProperty("user.dir"));
            int depth = 0;
            while (dir != null && depth < 8) {
                File assets = new File(dir, "assets");
                if (assets.exists() && assets.isDirectory()) {
                    if (new File(assets, "assets.txt").exists() || new File(assets, "levels").exists()) {
                        return assets;
                    }
                }
                dir = dir.getParentFile();
                depth++;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean writeToBuildResources(String path, String text) {
        try {
            File buildRes = findBuildResourcesDir();
            if (buildRes == null) return false;
            File out = new File(buildRes, path.replace('/', File.separatorChar));
            File parent = out.getParentFile(); if (parent != null && !parent.exists()) parent.mkdirs();
            Gdx.files.absolute(out.getAbsolutePath()).writeString(text, false);
            return true;
        } catch (Exception ex) {
            Gdx.app.error("PlayerIO", "Failed writing to build resources: " + ex.getMessage(), ex);
            return false;
        }
    }

    private static File findBuildResourcesDir() {
        try {
            File dir = new File(System.getProperty("user.dir"));
            int depth = 0;
            while (dir != null && depth < 8) {
                File[] candidates = new File[] {
                    new File(dir, "lwjgl3/build/resources/main"),
                    new File(dir, "core/build/resources/main"),
                    new File(dir, "build/resources/main")
                };
                for (File c : candidates) if (c.exists() && c.isDirectory()) return c;
                dir = dir.getParentFile(); depth++;
            }
        } catch (Exception ignored) {}
        return null;
    }
}

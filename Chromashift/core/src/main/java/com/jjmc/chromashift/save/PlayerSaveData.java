package com.jjmc.chromashift.save;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete, serializable snapshot of player state.
 * Database-agnostic, pure data container with no game logic.
 * Can be serialized to JSON, databases, or any other backend.
 */
public class PlayerSaveData {
    
    // === Core Transform & Physics ===
    public float x;
    public float y;
    public float velocityX;
    public float velocityY;
    public boolean facingLeft;
    
    // === Movement State ===
    public boolean onGround;
    public boolean canJump;
    public boolean dashing;
    public float dashTimer;
    public float dashCooldownTimer;
    public boolean dashUsed;
    public float dashHoverRemaining;
    
    // === Combat State ===
    public boolean attacking;
    public boolean airAttacking;
    public float airAttackTimer;
    public float attackCooldownTimer;
    public int currentCombo;
    
    // === Health & Stamina ===
    public float healthCurrent;
    public float healthMax;
    public float manaCurrent;
    public float manaMax;
    public float staminaCurrent;
    public float staminaMax;
    
    // === Invulnerability & Status ===
    public boolean isStunned;
    public float respawnInvulRemaining;
    public float respawnStunRemaining;
    
    // === Inventory & Items ===
    public int diamonds;
    public int shield;
    public int keyCount;
    public int potionCount;
    public String heldObjectId; // null if nothing held
    
    // === Skills ===
    public SkillSaveData skillQ;
    public SkillSaveData skillE;
    public SkillSaveData activeSkill;
    public List<String> unlockedSkillNames; // e.g., ["DashSkill", "SlashSkill"]
    
    // === Respawn & Checkpoints ===
    public float respawnX;
    public float respawnY;
    
    // === Level Progression ===
    public String currentLevel;
    public List<String> visitedLevels;
    
    // === Custom Timers & Counters ===
    public Map<String, Float> timerMap; // for any game-specific timers
    public Map<String, Integer> intCounterMap; // for counters
    public Map<String, Boolean> flagMap; // for boolean states
    public Map<String, Float> floatMap; // for any other floats
    
    // === Metadata ===
    public long saveTimestamp;
    public int saveSlot;
    
    /**
     * Default constructor for deserialization
     */
    public PlayerSaveData() {
        x = 0;
        y = 0;
        velocityX = 0;
        velocityY = 0;
        facingLeft = false;
        onGround = true;
        canJump = true;
        dashing = false;
        dashTimer = 0;
        dashCooldownTimer = 0;
        dashUsed = false;
        dashHoverRemaining = 0;
        attacking = false;
        airAttacking = false;
        airAttackTimer = 0;
        attackCooldownTimer = 0;
        currentCombo = 0;
        healthCurrent = 100;
        healthMax = 100;
        manaCurrent = 50;
        manaMax = 50;
        staminaCurrent = 100;
        staminaMax = 100;
        isStunned = false;
        respawnInvulRemaining = 0;
        respawnStunRemaining = 0;
        diamonds = 0;
        shield = 0;
        keyCount = 0;
        potionCount = 0;
        heldObjectId = null;
        skillQ = null;
        skillE = null;
        activeSkill = null;
        unlockedSkillNames = new ArrayList<>();
        respawnX = 0;
        respawnY = 0;
        currentLevel = "levels/level1.json";
        visitedLevels = new ArrayList<>();
        timerMap = new HashMap<>();
        intCounterMap = new HashMap<>();
        flagMap = new HashMap<>();
        floatMap = new HashMap<>();
        saveTimestamp = System.currentTimeMillis();
        saveSlot = 0;
    }
    
    /**
     * Full constructor for complete initialization
     */
    public PlayerSaveData(
            float x, float y, float velocityX, float velocityY, boolean facingLeft,
            boolean onGround, boolean canJump,
            float healthCurrent, float healthMax,
            float manaCurrent, float manaMax,
            float staminaCurrent, float staminaMax,
            int diamonds, int shield, int keyCount, int potionCount,
            float respawnX, float respawnY,
            String currentLevel, List<String> visitedLevels) {
        this();
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.facingLeft = facingLeft;
        this.onGround = onGround;
        this.canJump = canJump;
        this.healthCurrent = healthCurrent;
        this.healthMax = healthMax;
        this.manaCurrent = manaCurrent;
        this.manaMax = manaMax;
        this.staminaCurrent = staminaCurrent;
        this.staminaMax = staminaMax;
        this.diamonds = diamonds;
        this.shield = shield;
        this.keyCount = keyCount;
        this.potionCount = potionCount;
        this.respawnX = respawnX;
        this.respawnY = respawnY;
        this.currentLevel = currentLevel;
        if (visitedLevels != null) {
            this.visitedLevels = new ArrayList<>(visitedLevels);
        }
    }
    
    /**
     * Convert to a map for database storage
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("x", x);
        map.put("y", y);
        map.put("velocityX", velocityX);
        map.put("velocityY", velocityY);
        map.put("facingLeft", facingLeft);
        map.put("onGround", onGround);
        map.put("canJump", canJump);
        map.put("dashing", dashing);
        map.put("dashTimer", dashTimer);
        map.put("dashCooldownTimer", dashCooldownTimer);
        map.put("dashUsed", dashUsed);
        map.put("dashHoverRemaining", dashHoverRemaining);
        map.put("attacking", attacking);
        map.put("airAttacking", airAttacking);
        map.put("airAttackTimer", airAttackTimer);
        map.put("attackCooldownTimer", attackCooldownTimer);
        map.put("currentCombo", currentCombo);
        map.put("healthCurrent", healthCurrent);
        map.put("healthMax", healthMax);
        map.put("manaCurrent", manaCurrent);
        map.put("manaMax", manaMax);
        map.put("staminaCurrent", staminaCurrent);
        map.put("staminaMax", staminaMax);
        map.put("isStunned", isStunned);
        map.put("respawnInvulRemaining", respawnInvulRemaining);
        map.put("respawnStunRemaining", respawnStunRemaining);
        map.put("diamonds", diamonds);
        map.put("shield", shield);
        map.put("keyCount", keyCount);
        map.put("potionCount", potionCount);
        map.put("heldObjectId", heldObjectId);
        map.put("respawnX", respawnX);
        map.put("respawnY", respawnY);
        map.put("currentLevel", currentLevel);
        map.put("visitedLevels", visitedLevels);
        map.put("unlockedSkillNames", unlockedSkillNames);
        map.put("skillQ", skillQ != null ? skillQ.toMap() : null);
        map.put("skillE", skillE != null ? skillE.toMap() : null);
        map.put("activeSkill", activeSkill != null ? activeSkill.toMap() : null);
        map.put("saveTimestamp", saveTimestamp);
        map.put("saveSlot", saveSlot);
        return map;
    }
    
    /**
     * Restore from a map
     */
    public static PlayerSaveData fromMap(Map<String, Object> map) {
        PlayerSaveData data = new PlayerSaveData();
        if (map == null) return data;
        
        data.x = ((Number) map.getOrDefault("x", 0f)).floatValue();
        data.y = ((Number) map.getOrDefault("y", 0f)).floatValue();
        data.velocityX = ((Number) map.getOrDefault("velocityX", 0f)).floatValue();
        data.velocityY = ((Number) map.getOrDefault("velocityY", 0f)).floatValue();
        data.facingLeft = (Boolean) map.getOrDefault("facingLeft", false);
        data.onGround = (Boolean) map.getOrDefault("onGround", true);
        data.canJump = (Boolean) map.getOrDefault("canJump", true);
        data.dashing = (Boolean) map.getOrDefault("dashing", false);
        data.dashTimer = ((Number) map.getOrDefault("dashTimer", 0f)).floatValue();
        data.dashCooldownTimer = ((Number) map.getOrDefault("dashCooldownTimer", 0f)).floatValue();
        data.dashUsed = (Boolean) map.getOrDefault("dashUsed", false);
        data.dashHoverRemaining = ((Number) map.getOrDefault("dashHoverRemaining", 0f)).floatValue();
        data.attacking = (Boolean) map.getOrDefault("attacking", false);
        data.airAttacking = (Boolean) map.getOrDefault("airAttacking", false);
        data.airAttackTimer = ((Number) map.getOrDefault("airAttackTimer", 0f)).floatValue();
        data.attackCooldownTimer = ((Number) map.getOrDefault("attackCooldownTimer", 0f)).floatValue();
        data.currentCombo = ((Number) map.getOrDefault("currentCombo", 0)).intValue();
        data.healthCurrent = ((Number) map.getOrDefault("healthCurrent", 100f)).floatValue();
        data.healthMax = ((Number) map.getOrDefault("healthMax", 100f)).floatValue();
        data.manaCurrent = ((Number) map.getOrDefault("manaCurrent", 50f)).floatValue();
        data.manaMax = ((Number) map.getOrDefault("manaMax", 50f)).floatValue();
        data.staminaCurrent = ((Number) map.getOrDefault("staminaCurrent", 100f)).floatValue();
        data.staminaMax = ((Number) map.getOrDefault("staminaMax", 100f)).floatValue();
        data.isStunned = (Boolean) map.getOrDefault("isStunned", false);
        data.respawnInvulRemaining = ((Number) map.getOrDefault("respawnInvulRemaining", 0f)).floatValue();
        data.respawnStunRemaining = ((Number) map.getOrDefault("respawnStunRemaining", 0f)).floatValue();
        data.diamonds = ((Number) map.getOrDefault("diamonds", 0)).intValue();
        data.shield = ((Number) map.getOrDefault("shield", 0)).intValue();
        data.keyCount = ((Number) map.getOrDefault("keyCount", 0)).intValue();
        data.potionCount = ((Number) map.getOrDefault("potionCount", 0)).intValue();
        data.heldObjectId = (String) map.get("heldObjectId");
        data.respawnX = ((Number) map.getOrDefault("respawnX", 0f)).floatValue();
        data.respawnY = ((Number) map.getOrDefault("respawnY", 0f)).floatValue();
        data.currentLevel = (String) map.getOrDefault("currentLevel", "levels/level1.json");
        Object visitedObj = map.get("visitedLevels");
        if (visitedObj instanceof List) {
            data.visitedLevels = new ArrayList<>((List<String>) visitedObj);
        }
        Object unlockedObj = map.get("unlockedSkillNames");
        if (unlockedObj instanceof List) {
            data.unlockedSkillNames = new ArrayList<>((List<String>) unlockedObj);
        }
        data.saveTimestamp = ((Number) map.getOrDefault("saveTimestamp", System.currentTimeMillis())).longValue();
        data.saveSlot = ((Number) map.getOrDefault("saveSlot", 0)).intValue();
        return data;
    }
    
    @Override
    public String toString() {
        return "PlayerSaveData{" +
                "x=" + x + ", y=" + y +
                ", health=" + healthCurrent + "/" + healthMax +
                ", level='" + currentLevel + '\'' +
                ", slot=" + saveSlot +
                '}';
    }
    
    /**
     * Nested class for skill state
     */
    public static class SkillSaveData {
        public String skillName;
        public float currentCooldown;
        public boolean isActive;
        public float animationTimer;
        
        public SkillSaveData() {
            skillName = "";
            currentCooldown = 0;
            isActive = false;
            animationTimer = 0;
        }
        
        public SkillSaveData(String skillName, float currentCooldown, boolean isActive, float animationTimer) {
            this.skillName = skillName;
            this.currentCooldown = currentCooldown;
            this.isActive = isActive;
            this.animationTimer = animationTimer;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("skillName", skillName);
            map.put("currentCooldown", currentCooldown);
            map.put("isActive", isActive);
            map.put("animationTimer", animationTimer);
            return map;
        }
        
        public static SkillSaveData fromMap(Map<String, Object> map) {
            if (map == null) return new SkillSaveData();
            SkillSaveData skill = new SkillSaveData();
            skill.skillName = (String) map.getOrDefault("skillName", "");
            skill.currentCooldown = ((Number) map.getOrDefault("currentCooldown", 0f)).floatValue();
            skill.isActive = (Boolean) map.getOrDefault("isActive", false);
            skill.animationTimer = ((Number) map.getOrDefault("animationTimer", 0f)).floatValue();
            return skill;
        }
    }
}

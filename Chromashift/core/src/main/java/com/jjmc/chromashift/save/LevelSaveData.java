package com.jjmc.chromashift.save;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete, serializable snapshot of a level and all its GameObjects.
 * Database-agnostic, pure data container with no game logic.
 * Stores the full state of all entities so the level can be perfectly recreated.
 */
public class LevelSaveData {
    
    // === Level Identity ===
    public String levelId; // e.g., "levels/level1.json"
    public String levelName;
    public long saveTimestamp;
    
    // === Spawn Point ===
    public float spawnX;
    public float spawnY;
    
    // === Arena/Trigger Configuration ===
    public float arenaLeft;
    public float arenaRight;
    public float triggerWidth;
    public float launchpadLeftX;
    public float launchpadRightX;
    public float launchpadY;
    
    // === Saved GameObjects ===
    public List<GameObjectSaveData> walls;
    public List<GameObjectSaveData> solids;
    public List<GameObjectSaveData> interactables;
    public List<GameObjectSaveData> collectibles;
    public List<GameObjectSaveData> enemies;
    public List<GameObjectSaveData> effects;
    public List<GameObjectSaveData> triggers;
    public List<GameObjectSaveData> customObjects; // for any other game objects
    
    // === Boss State (if present) ===
    public GameObjectSaveData bossState;
    
    // === Global Level Timers/Flags ===
    public Map<String, Float> timerMap;
    public Map<String, Boolean> flagMap;
    public Map<String, Integer> intCounterMap;
    
    // === Metadata ===
    public int version; // for migration if schema changes
    
    /**
     * Default constructor for deserialization
     */
    public LevelSaveData() {
        levelId = "";
        levelName = "";
        saveTimestamp = System.currentTimeMillis();
        spawnX = 0;
        spawnY = 0;
        arenaLeft = -288;
        arenaRight = 1024;
        triggerWidth = 0;
        launchpadLeftX = -32;
        launchpadRightX = 736;
        launchpadY = 256;
        walls = new ArrayList<>();
        solids = new ArrayList<>();
        interactables = new ArrayList<>();
        collectibles = new ArrayList<>();
        enemies = new ArrayList<>();
        effects = new ArrayList<>();
        triggers = new ArrayList<>();
        customObjects = new ArrayList<>();
        bossState = null;
        timerMap = new HashMap<>();
        flagMap = new HashMap<>();
        intCounterMap = new HashMap<>();
        version = 1;
    }
    
    /**
     * Full constructor with essential parameters
     */
    public LevelSaveData(String levelId, String levelName, float spawnX, float spawnY) {
        this();
        this.levelId = levelId;
        this.levelName = levelName;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
    }
    
    /**
     * Add a GameObject snapshot
     */
    public void addGameObject(GameObjectSaveData obj) {
        if (obj == null) return;
        switch (obj.type) {
            case "Wall":
                walls.add(obj);
                break;
            case "Solid":
                solids.add(obj);
                break;
            case "Interactable":
            case "Door":
            case "Button":
            case "Lever":
            case "Portal":
            case "LockedDoor":
            case "Laser":
            case "Mirror":
            case "Glass":
            case "Shop":
                interactables.add(obj);
                break;
            case "Collectible":
            case "Diamond":
            case "HealthPotion":
                collectibles.add(obj);
                break;
            case "Enemy":
            case "Tentacle":
                enemies.add(obj);
                break;
            case "Effect":
            case "SFX":
                effects.add(obj);
                break;
            case "TriggerZone":
                triggers.add(obj);
                break;
            default:
                customObjects.add(obj);
                break;
        }
    }
    
    /**
     * Convert to a map for database storage
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("levelId", levelId);
        map.put("levelName", levelName);
        map.put("saveTimestamp", saveTimestamp);
        map.put("spawnX", spawnX);
        map.put("spawnY", spawnY);
        map.put("arenaLeft", arenaLeft);
        map.put("arenaRight", arenaRight);
        map.put("triggerWidth", triggerWidth);
        map.put("launchpadLeftX", launchpadLeftX);
        map.put("launchpadRightX", launchpadRightX);
        map.put("launchpadY", launchpadY);
        
        // Convert GameObject lists to maps
        List<Map<String, Object>> wallMaps = new ArrayList<>();
        for (GameObjectSaveData w : walls) {
            wallMaps.add(w.toMap());
        }
        map.put("walls", wallMaps);
        
        List<Map<String, Object>> solidMaps = new ArrayList<>();
        for (GameObjectSaveData s : solids) {
            solidMaps.add(s.toMap());
        }
        map.put("solids", solidMaps);
        
        List<Map<String, Object>> interactableMaps = new ArrayList<>();
        for (GameObjectSaveData i : interactables) {
            interactableMaps.add(i.toMap());
        }
        map.put("interactables", interactableMaps);
        
        List<Map<String, Object>> collectibleMaps = new ArrayList<>();
        for (GameObjectSaveData c : collectibles) {
            collectibleMaps.add(c.toMap());
        }
        map.put("collectibles", collectibleMaps);
        
        List<Map<String, Object>> enemyMaps = new ArrayList<>();
        for (GameObjectSaveData e : enemies) {
            enemyMaps.add(e.toMap());
        }
        map.put("enemies", enemyMaps);
        
        map.put("bossState", bossState != null ? bossState.toMap() : null);
        map.put("timerMap", timerMap);
        map.put("flagMap", flagMap);
        map.put("intCounterMap", intCounterMap);
        map.put("version", version);
        
        return map;
    }
    
    /**
     * Restore from a map
     */
    public static LevelSaveData fromMap(Map<String, Object> map) {
        LevelSaveData data = new LevelSaveData();
        if (map == null) return data;
        
        data.levelId = (String) map.getOrDefault("levelId", "");
        data.levelName = (String) map.getOrDefault("levelName", "");
        data.saveTimestamp = ((Number) map.getOrDefault("saveTimestamp", System.currentTimeMillis())).longValue();
        data.spawnX = ((Number) map.getOrDefault("spawnX", 0f)).floatValue();
        data.spawnY = ((Number) map.getOrDefault("spawnY", 0f)).floatValue();
        data.arenaLeft = ((Number) map.getOrDefault("arenaLeft", -288f)).floatValue();
        data.arenaRight = ((Number) map.getOrDefault("arenaRight", 1024f)).floatValue();
        data.triggerWidth = ((Number) map.getOrDefault("triggerWidth", 0f)).floatValue();
        data.launchpadLeftX = ((Number) map.getOrDefault("launchpadLeftX", -32f)).floatValue();
        data.launchpadRightX = ((Number) map.getOrDefault("launchpadRightX", 736f)).floatValue();
        data.launchpadY = ((Number) map.getOrDefault("launchpadY", 256f)).floatValue();
        data.version = ((Number) map.getOrDefault("version", 1)).intValue();
        
        // Restore GameObject lists from maps
        Object wallsObj = map.get("walls");
        if (wallsObj instanceof List) {
            for (Object wallObj : (List<?>) wallsObj) {
                if (wallObj instanceof Map) {
                    data.walls.add(GameObjectSaveData.fromMap((Map<String, Object>) wallObj));
                }
            }
        }
        
        Object solidsObj = map.get("solids");
        if (solidsObj instanceof List) {
            for (Object solidObj : (List<?>) solidsObj) {
                if (solidObj instanceof Map) {
                    data.solids.add(GameObjectSaveData.fromMap((Map<String, Object>) solidObj));
                }
            }
        }
        
        Object interactablesObj = map.get("interactables");
        if (interactablesObj instanceof List) {
            for (Object intObj : (List<?>) interactablesObj) {
                if (intObj instanceof Map) {
                    data.interactables.add(GameObjectSaveData.fromMap((Map<String, Object>) intObj));
                }
            }
        }
        
        Object collectiblesObj = map.get("collectibles");
        if (collectiblesObj instanceof List) {
            for (Object colObj : (List<?>) collectiblesObj) {
                if (colObj instanceof Map) {
                    data.collectibles.add(GameObjectSaveData.fromMap((Map<String, Object>) colObj));
                }
            }
        }
        
        Object enemiesObj = map.get("enemies");
        if (enemiesObj instanceof List) {
            for (Object enmObj : (List<?>) enemiesObj) {
                if (enmObj instanceof Map) {
                    data.enemies.add(GameObjectSaveData.fromMap((Map<String, Object>) enmObj));
                }
            }
        }
        
        Object bossObj = map.get("bossState");
        if (bossObj instanceof Map) {
            data.bossState = GameObjectSaveData.fromMap((Map<String, Object>) bossObj);
        }
        
        return data;
    }
    
    @Override
    public String toString() {
        return "LevelSaveData{" +
                "levelId='" + levelId + '\'' +
                ", objects=" + (walls.size() + solids.size() + interactables.size() + collectibles.size() + enemies.size()) +
                ", timestamp=" + saveTimestamp +
                '}';
    }
    
    /**
     * Nested class representing a single GameObject snapshot.
     * Stores all state needed to reconstruct any game object.
     */
    public static class GameObjectSaveData {
        public String id; // unique identifier
        public String type; // class name, e.g., "Wall", "Door", "Tentacle"
        public String name; // optional display name
        
        // === Transform ===
        public float x, y;
        public float width, height;
        public float rotation;
        
        // === Physics ===
        public float velocityX, velocityY;
        public boolean isActive;
        
        // === Generic state storage ===
        public Map<String, Float> floatProperties; // health, timers, cooldowns, etc.
        public Map<String, Integer> intProperties; // counts, indices, etc.
        public Map<String, Boolean> boolProperties; // flags, states
        public Map<String, String> stringProperties; // IDs, names, states
        public Map<String, Object> customData; // for any complex nested data
        
        /**
         * Default constructor
         */
        public GameObjectSaveData() {
            id = "";
            type = "";
            name = "";
            x = 0;
            y = 0;
            width = 0;
            height = 0;
            rotation = 0;
            velocityX = 0;
            velocityY = 0;
            isActive = true;
            floatProperties = new HashMap<>();
            intProperties = new HashMap<>();
            boolProperties = new HashMap<>();
            stringProperties = new HashMap<>();
            customData = new HashMap<>();
        }
        
        /**
         * Full constructor with basic fields
         */
        public GameObjectSaveData(String id, String type, float x, float y, float width, float height) {
            this();
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        
        /**
         * Add a float property (health, timer, cooldown, etc.)
         */
        public void setFloatProperty(String key, float value) {
            floatProperties.put(key, value);
        }
        
        /**
         * Get a float property with default fallback
         */
        public float getFloatProperty(String key, float defaultValue) {
            return floatProperties.getOrDefault(key, defaultValue);
        }
        
        /**
         * Add an integer property (count, index, etc.)
         */
        public void setIntProperty(String key, int value) {
            intProperties.put(key, value);
        }
        
        /**
         * Get an integer property
         */
        public int getIntProperty(String key, int defaultValue) {
            return intProperties.getOrDefault(key, defaultValue);
        }
        
        /**
         * Add a boolean flag
         */
        public void setBoolProperty(String key, boolean value) {
            boolProperties.put(key, value);
        }
        
        /**
         * Get a boolean flag
         */
        public boolean getBoolProperty(String key, boolean defaultValue) {
            return boolProperties.getOrDefault(key, defaultValue);
        }
        
        /**
         * Add a string property
         */
        public void setStringProperty(String key, String value) {
            stringProperties.put(key, value);
        }
        
        /**
         * Get a string property
         */
        public String getStringProperty(String key, String defaultValue) {
            return stringProperties.getOrDefault(key, defaultValue);
        }
        
        /**
         * Convert to a map for storage
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("type", type);
            map.put("name", name);
            map.put("x", x);
            map.put("y", y);
            map.put("width", width);
            map.put("height", height);
            map.put("rotation", rotation);
            map.put("velocityX", velocityX);
            map.put("velocityY", velocityY);
            map.put("isActive", isActive);
            map.put("floatProperties", floatProperties);
            map.put("intProperties", intProperties);
            map.put("boolProperties", boolProperties);
            map.put("stringProperties", stringProperties);
            map.put("customData", customData);
            return map;
        }
        
        /**
         * Restore from a map
         */
        public static GameObjectSaveData fromMap(Map<String, Object> map) {
            GameObjectSaveData obj = new GameObjectSaveData();
            if (map == null) return obj;
            
            obj.id = (String) map.getOrDefault("id", "");
            obj.type = (String) map.getOrDefault("type", "");
            obj.name = (String) map.getOrDefault("name", "");
            obj.x = ((Number) map.getOrDefault("x", 0f)).floatValue();
            obj.y = ((Number) map.getOrDefault("y", 0f)).floatValue();
            obj.width = ((Number) map.getOrDefault("width", 0f)).floatValue();
            obj.height = ((Number) map.getOrDefault("height", 0f)).floatValue();
            obj.rotation = ((Number) map.getOrDefault("rotation", 0f)).floatValue();
            obj.velocityX = ((Number) map.getOrDefault("velocityX", 0f)).floatValue();
            obj.velocityY = ((Number) map.getOrDefault("velocityY", 0f)).floatValue();
            obj.isActive = (Boolean) map.getOrDefault("isActive", true);
            
            Object floatObj = map.get("floatProperties");
            if (floatObj instanceof Map) {
                obj.floatProperties = (Map<String, Float>) floatObj;
            }
            
            Object intObj = map.get("intProperties");
            if (intObj instanceof Map) {
                obj.intProperties = (Map<String, Integer>) intObj;
            }
            
            Object boolObj = map.get("boolProperties");
            if (boolObj instanceof Map) {
                obj.boolProperties = (Map<String, Boolean>) boolObj;
            }
            
            Object strObj = map.get("stringProperties");
            if (strObj instanceof Map) {
                obj.stringProperties = (Map<String, String>) strObj;
            }
            
            Object customObj = map.get("customData");
            if (customObj instanceof Map) {
                obj.customData = (Map<String, Object>) customObj;
            }
            
            return obj;
        }
        
        @Override
        public String toString() {
            return "GameObject{" +
                    "id='" + id + '\'' +
                    ", type='" + type + '\'' +
                    ", pos=(" + x + "," + y + ")" +
                    '}';
        }
    }
}

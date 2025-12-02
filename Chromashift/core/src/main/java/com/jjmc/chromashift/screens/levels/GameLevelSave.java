package com.jjmc.chromashift.screens.levels;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Save/load level overrides per visited level.
 * Capture: record all fields per object with unique ID.
 * Apply: match by ID and set fields reflectively.
 * Saves in `assets/saves/levels/<levelname>.json`.
 */
public class GameLevelSave {

    private static final Json json = new Json();

    static {
        try { json.setIgnoreUnknownFields(true); } catch (Throwable ignored) {}
    }

    public static class SavedLevel {
        public String levelPath;
        public long timestamp;
        public Array<SavedObject> objects = new Array<>();
        public java.util.ArrayList<String> removedObjectIds = new java.util.ArrayList<>(); // removed/killed/collected IDs
    }

    public static class SavedObject {
        public String className; // simple name
        public String fullClassName; // full name
        public String uniqueId; // unique ID
        public float x, y;
        public Map<String, Object> fields = new HashMap<>();
    }

    /** Save overrides to workspace saves folder. */
    public static boolean saveLevelOverrides(String levelPath, LevelLoader.Result result) {
        try {
            SavedLevel sl = new SavedLevel();
            sl.levelPath = levelPath;
            sl.timestamp = System.currentTimeMillis();

            // Load original to detect removals
            Gdx.app.log("GameLevelSave", "Loading original level to detect removed objects: " + levelPath);
            LevelLoader.Result originalResult = LevelLoader.load(levelPath, LevelLoader.LoadMode.ORIGINAL);
            
            // Collect candidates
            Array<Object> candidates = new Array<>();
            java.util.ArrayList<String> collectedIds = new java.util.ArrayList<>();
            
            // Compare original vs current to find removals
            if (originalResult.collectibles != null) {
                Gdx.app.log("GameLevelSave", "Original collectibles count: " + originalResult.collectibles.size);
                Gdx.app.log("GameLevelSave", "Current collectibles count: " + (result.collectibles != null ? result.collectibles.size : 0));
                
                for (int i = 0; i < originalResult.collectibles.size; i++) {
                    Object origObj = originalResult.collectibles.get(i);
                    String origId = null;
                    
                    // Get original ID
                    if (origObj instanceof com.jjmc.chromashift.environment.collectible.Collectible c) {
                        origId = c.getId();
                    }
                    if (origId == null) origId = generateObjectId(origObj, i);
                    
                    // Check if ID in current
                    boolean foundInCurrent = false;
                    if (result.collectibles != null) {
                        for (int j = 0; j < result.collectibles.size; j++) {
                            Object currObj = result.collectibles.get(j);
                            String currId = null;
                            if (currObj instanceof com.jjmc.chromashift.environment.collectible.Collectible c) {
                                currId = c.getId();
                            }
                            if (currId == null) currId = generateObjectId(currObj, j);
                            
                            if (origId.equals(currId)) {
                                foundInCurrent = true;
                                break;
                            }
                        }
                    }
                    
                    // If not found in current, it was collected
                    if (!foundInCurrent) {
                        collectedIds.add(origId);
                        Gdx.app.log("GameLevelSave", "Detected COLLECTED: " + origId);
                    }
                }
            }
            
            // Compare original tentacles with current tentacles to find what's missing
            if (originalResult.tentacles != null) {
                Gdx.app.log("GameLevelSave", "Original tentacles count: " + originalResult.tentacles.size);
                Gdx.app.log("GameLevelSave", "Current tentacles count: " + (result.tentacles != null ? result.tentacles.size : 0));
                
                for (int i = 0; i < originalResult.tentacles.size; i++) {
                    Object origObj = originalResult.tentacles.get(i);
                    String origId = generateObjectId(origObj, i);
                    
                    // Check if this ID exists in current tentacles
                    boolean foundInCurrent = false;
                    if (result.tentacles != null) {
                        for (int j = 0; j < result.tentacles.size; j++) {
                            Object currObj = result.tentacles.get(j);
                            String currId = generateObjectId(currObj, j);
                            
                            if (origId.equals(currId)) {
                                foundInCurrent = true;
                                break;
                            }
                        }
                    }
                    
                    // If not found in current, it was killed
                    if (!foundInCurrent) {
                        collectedIds.add(origId);
                        Gdx.app.log("GameLevelSave", "Detected DEAD: " + origId);
                    }
                }
            }
            
            // Add current objects to candidates for state saving
            if (result.walls != null) for (Object o : result.walls) candidates.add(o);
            if (result.interactables != null) for (Object o : result.interactables) candidates.add(o);
            if (result.collectibles != null) for (Object o : result.collectibles) candidates.add(o);
            if (result.tentacles != null) for (Object o : result.tentacles) candidates.add(o);
            if (result.boss != null) candidates.add(result.boss);
            
            // Store the collected/dead IDs for removal on load
            Gdx.app.log("GameLevelSave", "Total removed objects detected: " + collectedIds.size());
            sl.removedObjectIds.addAll(collectedIds);
            Gdx.app.log("GameLevelSave", "removedObjectIds size AFTER addAll: " + sl.removedObjectIds.size());
            Gdx.app.log("GameLevelSave", "removedObjectIds contents: " + sl.removedObjectIds.toString());

            int objectIndex = 0;
            for (Object obj : candidates) {
                if (obj == null) continue;
                SavedObject so = new SavedObject();
                Class<?> cls = obj.getClass();
                so.className = cls.getSimpleName();
                so.fullClassName = cls.getName();

                // Attempt to obtain position and common bounds
                try {
                    Field boundsF = null;
                    try { boundsF = cls.getDeclaredField("bounds"); } catch (Exception ignored) {}
                    if (boundsF != null) {
                        boundsF.setAccessible(true);
                        Object bounds = boundsF.get(obj);
                        if (bounds instanceof Rectangle r) {
                            so.x = r.x;
                            so.y = r.y;
                        }
                    } else {
                        // try x,y fields
                        try { Field fx = cls.getDeclaredField("x"); fx.setAccessible(true); so.x = ((Number)fx.get(obj)).floatValue(); } catch (Exception ignored) {}
                        try { Field fy = cls.getDeclaredField("y"); fy.setAccessible(true); so.y = ((Number)fy.get(obj)).floatValue(); } catch (Exception ignored) {}
                    }
                } catch (Exception ex) {
                    // ignore
                }

                // Generate unique ID
                so.uniqueId = generateObjectId(obj, objectIndex);
                objectIndex++;

                // Extract ALL fields recursively (including private/inherited)
                captureAllFields(obj, cls, so.fields);

                sl.objects.add(so);
            }

            // Write JSON into workspace saves/levels
            Gdx.app.log("GameLevelSave", "BEFORE SERIALIZATION - removedObjectIds size: " + sl.removedObjectIds.size());
            Gdx.app.log("GameLevelSave", "BEFORE SERIALIZATION - removedObjectIds content: " + sl.removedObjectIds.toString());
            String text = json.prettyPrint(sl);
            Gdx.app.log("GameLevelSave", "JSON output length: " + text.length());
            File assetsDir = findProjectAssetsDir();
            if (assetsDir == null) {
                Gdx.app.error("GameLevelSave", "Project assets folder not found; aborting save for: " + levelPath);
                return false;
            }
            File out = new File(assetsDir, ("saves/levels/" + new File(levelPath).getName()).replace('/', File.separatorChar));
            File parent = out.getParentFile(); if (parent != null && !parent.exists()) parent.mkdirs();
            Gdx.files.absolute(out.getAbsolutePath()).writeString(text, false);
            Gdx.app.log("GameLevelSave", "=== SAVED LEVEL STATE ===");
            Gdx.app.log("GameLevelSave", "File: " + out.getAbsolutePath());
            Gdx.app.log("GameLevelSave", "Total objects: " + sl.objects.size);
            Gdx.app.log("GameLevelSave", "Removed/Collected IDs: " + sl.removedObjectIds.size());
            for (String id : sl.removedObjectIds) {
                Gdx.app.log("GameLevelSave", "  - " + id);
            }
            try { writeToBuildResources(("saves/levels/" + new File(levelPath).getName()).replace('\\','/'), text); } catch (Exception ignored) {}
            return true;
        } catch (Exception ex) {
            Gdx.app.error("GameLevelSave", "Failed to save level overrides: " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Generate a unique ID for an object using id/targetId fields or deterministic position-based ID.
     */
    private static String generateObjectId(Object obj, int index) {
        Class<?> cls = obj.getClass();
        String className = cls.getSimpleName();
        float x = 0, y = 0;
        
        // Get position
        try {
            Field boundsF = cls.getDeclaredField("bounds");
            boundsF.setAccessible(true);
            Object bounds = boundsF.get(obj);
            if (bounds instanceof Rectangle r) {
                x = r.x;
                y = r.y;
            }
        } catch (Exception ignored) {
            try { Field fx = cls.getDeclaredField("x"); fx.setAccessible(true); x = ((Number)fx.get(obj)).floatValue(); } catch (Exception ignored2) {}
            try { Field fy = cls.getDeclaredField("y"); fy.setAccessible(true); y = ((Number)fy.get(obj)).floatValue(); } catch (Exception ignored2) {}
        }
        
        // Try Collectible interface first
        String uid = null;
        try {
            if (obj instanceof com.jjmc.chromashift.environment.collectible.Collectible c) {
                uid = c.getId();
                if (uid != null && !uid.isEmpty()) {
                    return uid; // Use collectible's stable ID
                }
            }
        } catch (Exception ignored) {}
        
        // Try to get existing ID fields via reflection
        try {
            try { Field idf = cls.getDeclaredField("id"); idf.setAccessible(true); Object v = idf.get(obj); if (v != null) uid = v.toString(); } catch (Exception ignored) {}
            if (uid == null) {
                try { Field tid = cls.getDeclaredField("targetId"); tid.setAccessible(true); Object v = tid.get(obj); if (v != null) uid = v.toString(); } catch (Exception ignored) {}
            }
            if (uid == null) {
                try { java.lang.reflect.Method m = cls.getMethod("getId"); Object v = m.invoke(obj); if (v != null) uid = v.toString(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        
        // Fallback to deterministic ID based on position (not index, for stability)
        if (uid == null || uid.isEmpty()) {
            uid = className + "_" + ((int)x) + "_" + ((int)y);
        }
        
        return uid;
    }
    
    /**
     * Recursively capture all fields from object and parent classes.
     * Handles primitives, String, Boolean, Number, Rectangle, Vector2, Color, Enums.
     */
    private static void captureAllFields(Object obj, Class<?> cls, Map<String, Object> fieldsMap) {
        if (cls == null || cls == Object.class) return;
        
        // Capture parent class fields first
        captureAllFields(obj, cls.getSuperclass(), fieldsMap);
        
        Field[] fields = cls.getDeclaredFields();
        for (Field f : fields) {
            try {
                // Skip static and transient fields
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) continue;
                
                f.setAccessible(true);
                String name = f.getName();
                Object val = f.get(obj);
                
                if (val == null) {
                    fieldsMap.put(name, null);
                    continue;
                }
                
                Class<?> t = f.getType();
                
                // Primitives and wrappers
                if (t.isPrimitive() || Number.class.isAssignableFrom(t) || 
                    t == String.class || t == Boolean.class || t == Character.class) {
                    fieldsMap.put(name, val);
                }
                // Rectangle
                else if (val instanceof Rectangle r) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("x", r.x); m.put("y", r.y); 
                    m.put("w", r.width); m.put("h", r.height);
                    fieldsMap.put(name, m);
                }
                // Vector2
                else if (val instanceof Vector2 v2) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("x", v2.x); m.put("y", v2.y);
                    fieldsMap.put(name, m);
                }
                // Color
                else if (val instanceof Color c) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("r", c.r); m.put("g", c.g); 
                    m.put("b", c.b); m.put("a", c.a);
                    fieldsMap.put(name, m);
                }
                // Enum
                else if (t.isEnum()) {
                    fieldsMap.put(name, val.toString());
                }
                // Skip complex objects (textures, batches, arrays of complex objects)
                // These should be recreated on load, not serialized
            } catch (Exception ignored) {
                // Skip fields that can't be accessed
            }
        }
    }
    
    private static Map<String,Object> mapRect(Rectangle r) {
        Map<String,Object> m = new HashMap<>(); 
        m.put("x", r.x); m.put("y", r.y); 
        m.put("w", r.width); m.put("h", r.height); 
        return m;
    }

    /** Load overrides for level (if present) and apply to runtime objects in result. 
     * @return true if save was found and applied, false if no save exists */
    public static boolean applyOverridesIfPresent(String levelPath, LevelLoader.Result result) {
        try {
            String fname = new File(levelPath).getName();
            String rel = ("saves/levels/" + fname).replace('\\','/');
            // check workspace
            File assetsDir = findProjectAssetsDir();
            if (assetsDir != null) {
                File candidate = new File(assetsDir, rel.replace('/', File.separatorChar));
                if (candidate.exists()) {
                    String text = Gdx.files.absolute(candidate.getAbsolutePath()).readString();
                    SavedLevel sl = json.fromJson(SavedLevel.class, text);
                    if (sl != null && sl.objects != null) applySavedLevelToResult(sl, result);
                    Gdx.app.log("GameLevelSave","Applied overrides from workspace: " + candidate.getAbsolutePath());
                    return true;
                }
            }
            // fallback to internal
            FileHandle internal = Gdx.files.internal(rel);
            if (internal != null && internal.exists()) {
                String text = internal.readString();
                SavedLevel sl = json.fromJson(SavedLevel.class, text);
                if (sl != null && sl.objects != null) applySavedLevelToResult(sl, result);
                Gdx.app.log("GameLevelSave","Applied overrides from internal: " + rel);
                return true;
            }
            return false; // No save found
        } catch (Exception ex) {
            Gdx.app.error("GameLevelSave","Failed to apply overrides: " + ex.getMessage(), ex);
            return false;
        }
    }

    private static void applySavedLevelToResult(SavedLevel sl, LevelLoader.Result result) {
        Gdx.app.log("GameLevelSave", "=== LOADING SAVED LEVEL STATE ===");
        Gdx.app.log("GameLevelSave", "Saved objects: " + sl.objects.size);
        Gdx.app.log("GameLevelSave", "Removed IDs: " + (sl.removedObjectIds != null ? sl.removedObjectIds.size() : 0));
        
        // First, remove objects that were killed/collected
        if (sl.removedObjectIds != null && sl.removedObjectIds.size() > 0) {
            Gdx.app.log("GameLevelSave", "Processing removed objects...");
            // Remove from collectibles using their stable IDs
            if (result.collectibles != null) {
                int originalCount = result.collectibles.size;
                for (int i = result.collectibles.size - 1; i >= 0; i--) {
                    Object obj = result.collectibles.get(i);
                    String id = null;
                    // Try to get ID from Collectible interface first
                    try {
                        if (obj instanceof com.jjmc.chromashift.environment.collectible.Collectible c) {
                            id = c.getId();
                        }
                    } catch (Exception ignored) {}
                    // Fallback to generated ID
                    if (id == null) id = generateObjectId(obj, i);
                    
                    if (sl.removedObjectIds.contains(id)) {
                        result.collectibles.removeIndex(i);
                        Gdx.app.log("GameLevelSave", "✓ Removed collected: " + id);
                    }
                }
                Gdx.app.log("GameLevelSave", "Collectibles: " + originalCount + " -> " + result.collectibles.size);
            }
            // Remove from tentacles
            if (result.tentacles != null) {
                int originalCount = result.tentacles.size;
                for (int i = result.tentacles.size - 1; i >= 0; i--) {
                    Object obj = result.tentacles.get(i);
                    String id = generateObjectId(obj, i);
                    if (sl.removedObjectIds.contains(id)) {
                        result.tentacles.removeIndex(i);
                        Gdx.app.log("GameLevelSave", "✓ Removed dead enemy: " + id);
                    }
                }
                Gdx.app.log("GameLevelSave", "Tentacles: " + originalCount + " -> " + result.tentacles.size);
            }
        }
        
        // Then apply saved state to remaining objects
        for (SavedObject so : sl.objects) {
            // Try to find match in walls
            boolean applied = false;
            applied = applied || applyToList(so, result.walls.items, result.walls.size);
            applied = applied || applyToList(so, result.interactables.items, result.interactables.size);
            applied = applied || applyToList(so, result.collectibles.items, result.collectibles.size);
            applied = applied || applyToList(so, result.tentacles.items, result.tentacles.size);
            if (!applied && result.boss != null) {
                applied = applyToObject(so, result.boss);
            }
        }
    }

    private static boolean applyToList(SavedObject so, Object[] items, int size) {
        if (items == null) return false;
        for (int i=0;i<size;i++) {
            Object obj = items[i];
            if (obj == null) continue;
            if (!obj.getClass().getSimpleName().equals(so.className)) continue;
            
            // Try to match by ID first (for collectibles)
            boolean matched = false;
            try {
                if (obj instanceof com.jjmc.chromashift.environment.collectible.Collectible c) {
                    if (c.getId() != null && c.getId().equals(so.uniqueId)) {
                        matched = true;
                    }
                }
            } catch (Exception ignored) {}
            
            // Fall back to position match
            if (!matched) {
                Rectangle bounds = tryGetBounds(obj);
                if (bounds != null) {
                    if (Math.abs(bounds.x - so.x) < 2f && Math.abs(bounds.y - so.y) < 2f) {
                        matched = true;
                    }
                } else {
                    // No bounds and no ID match; try exact position
                    try {
                        Field fx = obj.getClass().getDeclaredField("x");
                        Field fy = obj.getClass().getDeclaredField("y");
                        fx.setAccessible(true);
                        fy.setAccessible(true);
                        float objX = ((Number)fx.get(obj)).floatValue();
                        float objY = ((Number)fy.get(obj)).floatValue();
                        if (Math.abs(objX - so.x) < 2f && Math.abs(objY - so.y) < 2f) {
                            matched = true;
                        }
                    } catch (Exception ignored) {}
                }
            }
            
            if (matched) {
                applyFields(obj, so.fields);
                Gdx.app.log("GameLevelSave", "Applied state to: " + so.className + " id=" + so.uniqueId);
                return true;
            }
        }
        return false;
    }

    private static boolean applyToObject(SavedObject so, Object obj) {
        if (obj == null) return false;
        if (obj.getClass().getSimpleName().equals(so.className)) {
            applyFields(obj, so.fields);
            return true;
        }
        return false;
    }

    private static Rectangle tryGetBounds(Object obj) {
        try {
            Field bf = obj.getClass().getDeclaredField("bounds");
            bf.setAccessible(true);
            Object b = bf.get(obj);
            if (b instanceof Rectangle) return (Rectangle)b;
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Apply saved fields to object, handling all types recursively through class hierarchy.
     */
    private static void applyFields(Object obj, Map<String,Object> fields) {
        if (fields == null || fields.isEmpty()) return;
        applyFieldsToClass(obj, obj.getClass(), fields);
    }
    
    private static void applyFieldsToClass(Object obj, Class<?> cls, Map<String, Object> fields) {
        if (cls == null || cls == Object.class) return;
        
        // Apply parent class fields first
        applyFieldsToClass(obj, cls.getSuperclass(), fields);
        
        for (Map.Entry<String,Object> e : fields.entrySet()) {
            String name = e.getKey();
            Object val = e.getValue();
            try {
                Field f = null;
                try { 
                    f = cls.getDeclaredField(name); 
                } catch (NoSuchFieldException ex) { 
                    continue; // Field doesn't exist in this class level
                }
                
                // Skip static/transient
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) continue;
                
                f.setAccessible(true);
                Class<?> t = f.getType();
                
                if (val == null) {
                    f.set(obj, null);
                }
                // Primitives
                else if (t == float.class || t == Float.class) {
                    Number n = (Number) val; f.setFloat(obj, n.floatValue());
                } else if (t == double.class || t == Double.class) {
                    Number n = (Number) val; f.setDouble(obj, n.doubleValue());
                } else if (t == int.class || t == Integer.class) {
                    Number n = (Number) val; f.setInt(obj, n.intValue());
                } else if (t == long.class || t == Long.class) {
                    Number n = (Number) val; f.setLong(obj, n.longValue());
                } else if (t == short.class || t == Short.class) {
                    Number n = (Number) val; f.setShort(obj, n.shortValue());
                } else if (t == byte.class || t == Byte.class) {
                    Number n = (Number) val; f.setByte(obj, n.byteValue());
                } else if (t == boolean.class || t == Boolean.class) {
                    f.setBoolean(obj, (Boolean) val);
                } else if (t == char.class || t == Character.class) {
                    if (val instanceof Character) f.setChar(obj, (Character) val);
                    else if (val instanceof String s && s.length() > 0) f.setChar(obj, s.charAt(0));
                }
                // String
                else if (t == String.class) {
                    f.set(obj, val.toString());
                }
                // Rectangle
                else if (t == Rectangle.class && val instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) val;
                    Rectangle r = new Rectangle(
                        ((Number)m.get("x")).floatValue(), 
                        ((Number)m.get("y")).floatValue(), 
                        ((Number)m.get("w")).floatValue(), 
                        ((Number)m.get("h")).floatValue()
                    );
                    f.set(obj, r);
                }
                // Vector2
                else if (t == Vector2.class && val instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) val;
                    Vector2 v = new Vector2(
                        ((Number)m.get("x")).floatValue(),
                        ((Number)m.get("y")).floatValue()
                    );
                    f.set(obj, v);
                }
                // Color
                else if (t == Color.class && val instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) val;
                    Color c = new Color(
                        ((Number)m.get("r")).floatValue(),
                        ((Number)m.get("g")).floatValue(),
                        ((Number)m.get("b")).floatValue(),
                        ((Number)m.get("a")).floatValue()
                    );
                    f.set(obj, c);
                }
                // Enum
                else if (t.isEnum()) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    Enum enumVal = Enum.valueOf((Class<Enum>) t, val.toString());
                    f.set(obj, enumVal);
                }
            } catch (Exception ex) {
                // Ignore individual field failures (type mismatch, etc.)
            }
        }
    }

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
                dir = dir.getParentFile(); depth++;
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
            Gdx.app.error("GameLevelSave", "Failed writing to build resources: " + ex.getMessage(), ex);
            return false;
        }
    }

    private static File findBuildResourcesDir() {
        try {
            File dir = new File(System.getProperty("user.dir"));
            int depth = 0;
            while (dir != null && depth < 8) {
                File[] candidates = new File[] { new File(dir, "lwjgl3/build/resources/main"), new File(dir, "core/build/resources/main"), new File(dir, "build/resources/main") };
                for (File c : candidates) if (c.exists() && c.isDirectory()) return c;
                dir = dir.getParentFile(); depth++;
            }
        } catch (Exception ignored) {}
        return null;
    }
}

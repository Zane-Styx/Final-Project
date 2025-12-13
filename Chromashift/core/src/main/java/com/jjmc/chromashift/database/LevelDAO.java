package com.jjmc.chromashift.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jjmc.chromashift.save.LevelSaveData;
import com.jjmc.chromashift.save.LevelSaveData.GameObjectSaveData;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LevelDAO {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Register a level in the database
     */
    public static int registerLevel(String levelName, String levelPath, 
                                    float spawnX, float spawnY,
                                    float arenaLeft, float arenaRight) throws SQLException {
        String sql = "INSERT INTO levels (level_name, level_path, spawn_x, spawn_y, " +
                     "arena_left, arena_right) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setString(1, levelName);
            ps.setString(2, levelPath);
            ps.setFloat(3, spawnX);
            ps.setFloat(4, spawnY);
            ps.setFloat(5, arenaLeft);
            ps.setFloat(6, arenaRight);
            ps.executeUpdate();
            
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int levelId = keys.getInt(1);
                System.out.println("✓ Level registered: " + levelName + " (ID: " + levelId + ")");
                return levelId;
            }
        }
        throw new SQLException("Failed to register level");
    }
    
    /**
     * Save level state to database with raw JSON string (from GameLevelSave).
     * This stores the SavedLevel JSON directly in level_data_json column.
     * Automatically registers the level if it doesn't exist.
     */
    public static void saveLevelStateJson(int playerId, int levelId, String levelPath, String levelDataJson) throws SQLException {
        // First, ensure the level exists in the database
        ensureLevelExists(levelId, levelPath);
        
        String checkSql = "SELECT save_id FROM level_saves WHERE player_id = ? AND level_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            
            ps.setInt(1, playerId);
            ps.setInt(2, levelId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                // Update existing save
                int saveId = rs.getInt("save_id");
                String updateSql = "UPDATE level_saves SET level_data_json=?, save_timestamp=?, updated_at=NOW() WHERE save_id=?";
                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setString(1, levelDataJson);
                    updatePs.setLong(2, System.currentTimeMillis());
                    updatePs.setInt(3, saveId);
                    updatePs.executeUpdate();
                    System.out.println("✓ Level save updated (Player: " + playerId + ", Level: " + levelId + ")");
                }
            } else {
                // Insert new save
                String insertSql = "INSERT INTO level_saves (player_id, level_id, level_data_json, save_timestamp) VALUES (?, ?, ?, ?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    insertPs.setInt(1, playerId);
                    insertPs.setInt(2, levelId);
                    insertPs.setString(3, levelDataJson);
                    insertPs.setLong(4, System.currentTimeMillis());
                    insertPs.executeUpdate();
                    System.out.println("✓ Level save inserted (Player: " + playerId + ", Level: " + levelId + ")");
                }
            }
        }
    }
    
    /**
     * Ensure a level record exists in the database.
     * If it doesn't exist, create it with default values.
     */
    private static void ensureLevelExists(int levelId, String levelPath) throws SQLException {
        String checkSql = "SELECT level_id FROM levels WHERE level_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            
            checkPs.setInt(1, levelId);
            ResultSet rs = checkPs.executeQuery();
            
            if (!rs.next()) {
                // Level doesn't exist, create it
                String insertSql = "INSERT INTO levels (level_id, level_name, level_path, spawn_x, spawn_y, arena_left, arena_right) " +
                                   "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    insertPs.setInt(1, levelId);
                    // Extract level name from path (e.g., "levels/level1.json" -> "level1")
                    String levelName = new java.io.File(levelPath).getName().replace(".json", "");
                    insertPs.setString(2, levelName);
                    insertPs.setString(3, levelPath);
                    insertPs.setFloat(4, 0); // default spawn_x
                    insertPs.setFloat(5, 0); // default spawn_y
                    insertPs.setFloat(6, -288); // default arena_left
                    insertPs.setFloat(7, 1024); // default arena_right
                    insertPs.executeUpdate();
                    System.out.println("✓ Auto-registered level: " + levelName + " (ID: " + levelId + ")");
                }
            }
        }
    }
    
    /**
     * Save level state to database with raw JSON string (from GameLevelSave).
     * This stores the SavedLevel JSON directly in level_data_json column.
     */
    public static void saveLevelStateJson(int playerId, int levelId, String levelDataJson) throws SQLException {
        // This is the old signature for backward compatibility
        saveLevelStateJson(playerId, levelId, "", levelDataJson);
    }
    
    /**
     * Save level state to database (unchanged schema). Note: PlayerIO governs player data only.
     */
    public static void saveLevelState(int playerId, int levelId, LevelSaveData levelData) throws SQLException {
        String checkSql = "SELECT save_id FROM level_saves WHERE player_id = ? AND level_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            
            ps.setInt(1, playerId);
            ps.setInt(2, levelId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                // Update existing save
                int saveId = rs.getInt("save_id");
                updateLevelSave(conn, saveId, levelData);
            } else {
                // Insert new save
                insertLevelSave(conn, playerId, levelId, levelData);
            }
        }
    }
    
    /**
     * Insert new level save
     */
    private static void insertLevelSave(Connection conn, int playerId, int levelId, 
                                        LevelSaveData levelData) throws SQLException {
        String sql = "INSERT INTO level_saves (player_id, level_id, level_data_json, save_timestamp) " +
                     "VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, playerId);
            ps.setInt(2, levelId);
            ps.setString(3, gson.toJson(levelData));
            ps.setLong(4, levelData.saveTimestamp);
            ps.executeUpdate();
            
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int saveId = keys.getInt(1);
                saveGameObjects(conn, saveId, levelData);
                System.out.println("✓ Level save inserted (Player: " + playerId + ", Level: " + levelId + ")");
            }
        }
    }
    
    /**
     * Update existing level save
     */
    private static void updateLevelSave(Connection conn, int saveId, LevelSaveData levelData) throws SQLException {
        String sql = "UPDATE level_saves SET level_data_json=?, save_timestamp=?, updated_at=NOW() " +
                     "WHERE save_id=?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, gson.toJson(levelData));
            ps.setLong(2, levelData.saveTimestamp);
            ps.setInt(3, saveId);
            ps.executeUpdate();
            
            // Update game objects
            deleteGameObjects(conn, saveId);
            saveGameObjects(conn, saveId, levelData);
            System.out.println("✓ Level save updated (Save ID: " + saveId + ")");
        }
    }
    
    /**
     * Save individual game objects for denormalized queries
     */
    private static void saveGameObjects(Connection conn, int saveId, LevelSaveData levelData) throws SQLException {
        String sql = "INSERT INTO game_objects (save_id, object_type, object_id_str, x, y, width, height, is_active, object_data_json) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        List<GameObjectSaveData> allObjects = new ArrayList<>();
        allObjects.addAll(levelData.walls);
        allObjects.addAll(levelData.solids);
        allObjects.addAll(levelData.interactables);
        allObjects.addAll(levelData.collectibles);
        allObjects.addAll(levelData.enemies);
        allObjects.addAll(levelData.effects);
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (GameObjectSaveData obj : allObjects) {
                ps.setInt(1, saveId);
                ps.setString(2, obj.type);
                ps.setString(3, obj.id);
                ps.setFloat(4, obj.x);
                ps.setFloat(5, obj.y);
                ps.setFloat(6, obj.width);
                ps.setFloat(7, obj.height);
                ps.setBoolean(8, obj.isActive);
                ps.setString(9, gson.toJson(obj));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
    
    /**
     * Delete game objects for a save
     */
    private static void deleteGameObjects(Connection conn, int saveId) throws SQLException {
        String sql = "DELETE FROM game_objects WHERE save_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, saveId);
            ps.executeUpdate();
        }
    }
    
    /**
     * Load level state from database
     */
    public static LevelSaveData loadLevelState(int playerId, int levelId) throws SQLException {
        String sql = "SELECT level_data_json FROM level_saves WHERE player_id = ? AND level_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, playerId);
            ps.setInt(2, levelId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                String jsonData = rs.getString("level_data_json");
                LevelSaveData levelData = gson.fromJson(jsonData, LevelSaveData.class);
                System.out.println("✓ Level state loaded (Player: " + playerId + ", Level: " + levelId + ")");
                return levelData;
            }
        }
        throw new SQLException("Level save not found for player " + playerId + ", level " + levelId);
    }
    
    /**
     * Get all game objects of a specific type
     */
    public static List<GameObjectSaveData> getGameObjectsByType(int saveId, String objectType) throws SQLException {
        String sql = "SELECT object_data_json FROM game_objects WHERE save_id = ? AND object_type = ?";
        List<GameObjectSaveData> objects = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, saveId);
            ps.setString(2, objectType);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                String jsonData = rs.getString("object_data_json");
                objects.add(gson.fromJson(jsonData, GameObjectSaveData.class));
            }
        }
        return objects;
    }
}
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
     * Save level state to database
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
# XAMPP/MySQL & JDBC Integration Tutorial

## Overview
This tutorial demonstrates how to use `PlayerSaveData` and `LevelSaveData` classes with XAMPP/MySQL database using JDBC for persistent game saves, normalization, and data management.

---

## Prerequisites

1. **XAMPP Installed** with MySQL running
2. **MySQL Driver** in project: `mysql-connector-java:8.0.x` (add to `build.gradle`)
3. **Database**: Create `chromashift_db`
4. **LibGDX Project**: Already have `PlayerSaveData` and `LevelSaveData` classes

---

## Step 1: Add MySQL Dependency

**Update `core/build.gradle`:**
```gradle
dependencies {
    // ... existing dependencies
    implementation 'mysql:mysql-connector-java:8.0.33'
    implementation 'com.google.code.gson:gson:2.10.1'  // For JSON serialization
}
```

---

## Step 2: Create Database Schema (XAMPP/MySQL)

Open phpMyAdmin in XAMPP (http://localhost/phpmyadmin/) or use MySQL command line:

```sql
-- Create database
CREATE DATABASE IF NOT EXISTS chromashift_db;
USE chromashift_db;

-- Players table (normalized)
CREATE TABLE IF NOT EXISTS players (
    player_id INT PRIMARY KEY AUTO_INCREMENT,
    player_name VARCHAR(255) NOT NULL UNIQUE,
    level_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX (player_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Player saves table (stores all player state as JSON)
CREATE TABLE IF NOT EXISTS player_saves (
    save_id INT PRIMARY KEY AUTO_INCREMENT,
    player_id INT NOT NULL UNIQUE,

    -- Core Transform & Physics
    x FLOAT,
    y FLOAT,
    velocity_x FLOAT,
    velocity_y FLOAT,
    facing_left BOOLEAN DEFAULT FALSE,

    -- Movement State
    on_ground BOOLEAN DEFAULT TRUE,
    can_jump BOOLEAN DEFAULT TRUE,
    dashing BOOLEAN DEFAULT FALSE,
    dash_timer FLOAT DEFAULT 0,
    dash_cooldown_timer FLOAT DEFAULT 0,
    dash_used BOOLEAN DEFAULT FALSE,
    dash_hover_remaining FLOAT DEFAULT 0,

    -- Combat State
    attacking BOOLEAN DEFAULT FALSE,
    air_attacking BOOLEAN DEFAULT FALSE,
    air_attack_timer FLOAT DEFAULT 0,
    attack_cooldown_timer FLOAT DEFAULT 0,

    -- Inventory & Items
    shield INT DEFAULT 0,
    key_count INT DEFAULT 0,
    potion_count INT DEFAULT 0,

    -- Respawn & Status
    respawn_x FLOAT DEFAULT 0,
    respawn_y FLOAT DEFAULT 0,
    respawn_invul_remaining FLOAT DEFAULT 0,
    respawn_stun_remaining FLOAT DEFAULT 0,
    is_stunned BOOLEAN DEFAULT FALSE,

    -- Resources
    diamonds INT DEFAULT 0,
    health_current FLOAT DEFAULT 100,
    health_max FLOAT DEFAULT 100,

    -- Skills
    skill_q_json LONGTEXT,          -- JSON SkillState
    skill_e_json LONGTEXT,          -- JSON SkillState
    active_skill_json LONGTEXT,     -- JSON SkillState

    -- Level Progression
    current_level VARCHAR(255) DEFAULT 'levels/level1.json',
    visited_levels_json LONGTEXT,   -- JSON array of strings

    -- Metadata
    save_data_json LONGTEXT,        -- Full PlayerState as backup JSON

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE,
    INDEX (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE,
    INDEX (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Levels table
CREATE TABLE IF NOT EXISTS levels (
    level_id INT PRIMARY KEY AUTO_INCREMENT,
    level_name VARCHAR(255) NOT NULL,
    level_path VARCHAR(255) NOT NULL UNIQUE,
    spawn_x FLOAT,
    spawn_y FLOAT,
    arena_left FLOAT,
    arena_right FLOAT,
    trigger_width FLOAT,
    launchpad_left_x FLOAT,
    launchpad_right_x FLOAT,
    launchpad_y FLOAT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX (level_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Level saves table (stores all GameObjects state)
CREATE TABLE IF NOT EXISTS level_saves (
    save_id INT PRIMARY KEY AUTO_INCREMENT,
    player_id INT NOT NULL,
    level_id INT NOT NULL,
    level_data_json LONGTEXT,  -- Full LevelSaveData as JSON
    save_timestamp BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE,
    FOREIGN KEY (level_id) REFERENCES levels(level_id) ON DELETE CASCADE,
    UNIQUE KEY (player_id, level_id),
    INDEX (player_id, level_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Game objects table (denormalized for query efficiency)
CREATE TABLE IF NOT EXISTS game_objects (
    object_id INT PRIMARY KEY AUTO_INCREMENT,
    save_id INT NOT NULL,
    object_type VARCHAR(50) NOT NULL,
    object_id_str VARCHAR(255),
    x FLOAT,
    y FLOAT,
    width FLOAT,
    height FLOAT,
    is_active BOOLEAN,
    object_data_json LONGTEXT,  -- Full GameObjectSaveData as JSON
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (save_id) REFERENCES level_saves(save_id) ON DELETE CASCADE,
    INDEX (save_id, object_type),
    INDEX (object_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## Step 3: Create Database Connection Manager

**New file: `core/src/main/java/com/jjmc/chromashift/database/DatabaseConnection.java`**

```java
package com.jjmc.chromashift.database;

import java.sql.*;

public class DatabaseConnection {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/chromashift_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";
    
    /**
     * Get a connection to the XAMPP MySQL database
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found", e);
        }
    }
    
    /**
     * Test connection to database
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            System.out.println("✓ Connected to: " + meta.getDatabaseProductName());
            System.out.println("✓ Version: " + meta.getDatabaseProductVersion());
            return true;
        } catch (SQLException e) {
            System.err.println("✗ Database connection failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Close resources safely
     */
    public static void close(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
```

---

## Step 4: Create Player Data Access Object (DAO)

**New file: `core/src/main/java/com/jjmc/chromashift/database/PlayerDAO.java`**

```java
package com.jjmc.chromashift.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jjmc.chromashift.player.PlayerIO;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class PlayerDAO {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Create a new player and return the player_id
     */
    public static int createPlayer(String playerName, String levelId) throws SQLException {
        String sql = "INSERT INTO players (player_name, level_id) VALUES (?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setString(1, playerName);
            ps.setString(2, levelId);
            ps.executeUpdate();
            
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int playerId = keys.getInt(1);
                System.out.println("✓ Player created: " + playerName + " (ID: " + playerId + ")");
                return playerId;
            }
        }
        throw new SQLException("Failed to create player");
    }
    
    /**
     * Save player state to database
     */
    public static void savePlayer(int playerId, PlayerIO.PlayerState playerState) throws SQLException {
        // First, check if save exists
        String checkSql = "SELECT COUNT(*) FROM player_saves WHERE player_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            boolean exists = rs.getInt(1) > 0;
            
            if (exists) {
                updatePlayerSave(conn, playerId, playerState);
            } else {
                insertPlayerSave(conn, playerId, playerState);
            }
        }
    }
    
    /**
     * Insert new player save
     */
    private static void insertPlayerSave(Connection conn, int playerId, PlayerIO.PlayerState playerState) throws SQLException {
        String sql = "INSERT INTO player_saves (" +
                "player_id, x, y, velocity_x, velocity_y, facing_left, " +
                "on_ground, can_jump, dashing, dash_timer, dash_cooldown_timer, dash_used, dash_hover_remaining, " +
                "attacking, air_attacking, air_attack_timer, attack_cooldown_timer, " +
                "shield, key_count, potion_count, " +
                "respawn_x, respawn_y, respawn_invul_remaining, respawn_stun_remaining, is_stunned, " +
                "diamonds, health_current, health_max, " +
                "skill_q_json, skill_e_json, active_skill_json, " +
                "current_level, visited_levels_json, " +
                "save_data_json" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            ps.setInt(paramIndex++, playerId);
            ps.setFloat(paramIndex++, playerState.x);
            ps.setFloat(paramIndex++, playerState.y);
            ps.setFloat(paramIndex++, playerState.velocityX);
            ps.setFloat(paramIndex++, playerState.velocityY);
            ps.setBoolean(paramIndex++, playerState.facingLeft);
            ps.setBoolean(paramIndex++, playerState.onGround);
            ps.setBoolean(paramIndex++, playerState.canJump);
            ps.setBoolean(paramIndex++, playerState.dashing);
            ps.setFloat(paramIndex++, playerState.dashTimer);
            ps.setFloat(paramIndex++, playerState.dashCooldownTimer);
            ps.setBoolean(paramIndex++, playerState.dashUsed);
            ps.setFloat(paramIndex++, playerState.dashHoverRemaining);
            ps.setBoolean(paramIndex++, playerState.attacking);
            ps.setBoolean(paramIndex++, playerState.airAttacking);
            ps.setFloat(paramIndex++, playerState.airAttackTimer);
            ps.setFloat(paramIndex++, playerState.attackCooldownTimer);
            ps.setInt(paramIndex++, playerState.shield);
            ps.setInt(paramIndex++, playerState.keyCount);
            ps.setInt(paramIndex++, playerState.potionCount);
            ps.setFloat(paramIndex++, playerState.respawnX);
            ps.setFloat(paramIndex++, playerState.respawnY);
            ps.setFloat(paramIndex++, playerState.respawnInvulRemaining);
            ps.setFloat(paramIndex++, playerState.respawnStunRemaining);
            ps.setBoolean(paramIndex++, playerState.isStunned);
            ps.setInt(paramIndex++, playerState.diamonds);
            ps.setFloat(paramIndex++, playerState.healthCurrent);
            ps.setFloat(paramIndex++, playerState.healthMax);
            ps.setString(paramIndex++, playerState.skillQ != null ? gson.toJson(playerState.skillQ) : null);
            ps.setString(paramIndex++, playerState.skillE != null ? gson.toJson(playerState.skillE) : null);
            ps.setString(paramIndex++, playerState.activeSkill != null ? gson.toJson(playerState.activeSkill) : null);
            ps.setString(paramIndex++, playerState.currentLevel);
            ps.setString(paramIndex++, gson.toJson(playerState.visitedLevels));
            ps.setString(paramIndex++, gson.toJson(playerState));

            ps.executeUpdate();
            System.out.println("✓ Player save inserted (ID: " + playerId + ")");
        }
    }
    
    /**
     * Update existing player save
     */
    private static void updatePlayerSave(Connection conn, int playerId, PlayerIO.PlayerState playerState) throws SQLException {
        String sql = "UPDATE player_saves SET " +
                "x=?, y=?, velocity_x=?, velocity_y=?, facing_left=?, " +
                "on_ground=?, can_jump=?, dashing=?, dash_timer=?, dash_cooldown_timer=?, dash_used=?, dash_hover_remaining=?, " +
                "attacking=?, air_attacking=?, air_attack_timer=?, attack_cooldown_timer=?, " +
                "shield=?, key_count=?, potion_count=?, " +
                "respawn_x=?, respawn_y=?, respawn_invul_remaining=?, respawn_stun_remaining=?, is_stunned=?, " +
                "diamonds=?, health_current=?, health_max=?, " +
                "skill_q_json=?, skill_e_json=?, active_skill_json=?, " +
                "current_level=?, visited_levels_json=?, " +
                "save_data_json=?, updated_at=NOW() " +
                "WHERE player_id=?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            ps.setFloat(paramIndex++, playerState.x);
            ps.setFloat(paramIndex++, playerState.y);
            ps.setFloat(paramIndex++, playerState.velocityX);
            ps.setFloat(paramIndex++, playerState.velocityY);
            ps.setBoolean(paramIndex++, playerState.facingLeft);
            ps.setBoolean(paramIndex++, playerState.onGround);
            ps.setBoolean(paramIndex++, playerState.canJump);
            ps.setBoolean(paramIndex++, playerState.dashing);
            ps.setFloat(paramIndex++, playerState.dashTimer);
            ps.setFloat(paramIndex++, playerState.dashCooldownTimer);
            ps.setBoolean(paramIndex++, playerState.dashUsed);
            ps.setFloat(paramIndex++, playerState.dashHoverRemaining);
            ps.setBoolean(paramIndex++, playerState.attacking);
            ps.setBoolean(paramIndex++, playerState.airAttacking);
            ps.setFloat(paramIndex++, playerState.airAttackTimer);
            ps.setFloat(paramIndex++, playerState.attackCooldownTimer);
            ps.setInt(paramIndex++, playerState.shield);
            ps.setInt(paramIndex++, playerState.keyCount);
            ps.setInt(paramIndex++, playerState.potionCount);
            ps.setFloat(paramIndex++, playerState.respawnX);
            ps.setFloat(paramIndex++, playerState.respawnY);
            ps.setFloat(paramIndex++, playerState.respawnInvulRemaining);
            ps.setFloat(paramIndex++, playerState.respawnStunRemaining);
            ps.setBoolean(paramIndex++, playerState.isStunned);
            ps.setInt(paramIndex++, playerState.diamonds);
            ps.setFloat(paramIndex++, playerState.healthCurrent);
            ps.setFloat(paramIndex++, playerState.healthMax);
            ps.setString(paramIndex++, playerState.skillQ != null ? gson.toJson(playerState.skillQ) : null);
            ps.setString(paramIndex++, playerState.skillE != null ? gson.toJson(playerState.skillE) : null);
            ps.setString(paramIndex++, playerState.activeSkill != null ? gson.toJson(playerState.activeSkill) : null);
            ps.setString(paramIndex++, playerState.currentLevel);
            ps.setString(paramIndex++, gson.toJson(playerState.visitedLevels));
            ps.setString(paramIndex++, gson.toJson(playerState));
            ps.setInt(paramIndex++, playerId);

            ps.executeUpdate();
            System.out.println("✓ Player save updated (ID: " + playerId + ")");
        }
    }
    
    /**
     * Load player save from database
     */
    public static PlayerIO.PlayerState loadPlayer(int playerId) throws SQLException {
        String sql = "SELECT save_data_json FROM player_saves WHERE player_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                String jsonData = rs.getString("save_data_json");
                PlayerIO.PlayerState playerState = gson.fromJson(jsonData, PlayerIO.PlayerState.class);
                System.out.println("✓ Player loaded (ID: " + playerId + ")");
                return playerState;
            }
        }
        throw new SQLException("Player save not found for ID: " + playerId);
    }
    
    /**
     * Get player ID by name
     */
    public static int getPlayerIdByName(String playerName) throws SQLException {
        String sql = "SELECT player_id FROM players WHERE player_name = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("player_id");
            }
        }
        throw new SQLException("Player not found: " + playerName);
    }
    
    /**
     * Delete player and all associated saves
     */
    public static void deletePlayer(int playerId) throws SQLException {
        String sql = "DELETE FROM players WHERE player_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, playerId);
            ps.executeUpdate();
            System.out.println("✓ Player deleted (ID: " + playerId + ")");
        }
    }
}
```

---

## Step 5: Create Level Data Access Object (DAO)

**New file: `core/src/main/java/com/jjmc/chromashift/database/LevelDAO.java`**

```java
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
```

---

## Step 6: Integration Example in Game Screen

**Usage in your game screen (e.g., `TestSceneScreen.java`):**

```java
import com.jjmc.chromashift.database.DatabaseConnection;
import com.jjmc.chromashift.database.PlayerDAO;
import com.jjmc.chromashift.database.LevelDAO;
import com.jjmc.chromashift.player.PlayerIO;

public class TestSceneScreen extends ScreenAdapter {
    private int playerId = -1;
    private int levelId = -1;
    
    @Override
    public void show() {
        // Test database connection
        if (!DatabaseConnection.testConnection()) {
            System.err.println("Cannot connect to database!");
            return;
        }
        
        try {
            // Create or get player
            playerId = PlayerDAO.createPlayer("Hero_001", "levels/level1.json");
            
            // Register level
            levelId = LevelDAO.registerLevel("Level 1", "levels/level1.json", 100, 200, -288, 1024);
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Save player state (call periodically or on pause)
     */
    public void savePlayerState() {
        try {
            // Use PlayerIO to capture current state
            PlayerIO.PlayerState playerState = PlayerIO.capture(player, currentLevel, visitedLevels);
            
            PlayerDAO.savePlayer(playerId, playerState);
            
        } catch (SQLException e) {
            System.err.println("Failed to save player: " + e.getMessage());
        }
    }
    
    /**
     * Load player state (call on level start)
     */
    public void loadPlayerState() {
        try {
            PlayerIO.PlayerState playerState = PlayerDAO.loadPlayer(playerId);
            
            // Use PlayerIO to apply the loaded state
            PlayerIO.applyToPlayer(player, playerState);
            
            System.out.println("Player state restored from database");
            
        } catch (SQLException e) {
            System.err.println("Failed to load player: " + e.getMessage());
        }
    }
    
    /**
     * Save level state (call when exiting level)
     */
    public void saveLevelState() {
        try {
            LevelSaveData levelData = new LevelSaveData("levels/level1.json", "Level 1", 100, 200);
            levelData.saveTimestamp = System.currentTimeMillis();
            
            // Add all game objects
            for (Wall wall : walls) {
                LevelSaveData.GameObjectSaveData obj = new LevelSaveData.GameObjectSaveData(
                    "wall_" + wall.getId(),
                    "Wall",
                    wall.getX(), wall.getY(),
                    wall.getWidth(), wall.getHeight()
                );
                levelData.addGameObject(obj);
            }
            
            // Add enemies, doors, etc. similarly...
            
            LevelDAO.saveLevelState(playerId, levelId, levelData);
            
        } catch (SQLException e) {
            System.err.println("Failed to save level: " + e.getMessage());
        }
    }
    
    /**
     * Load level state (call when entering level)
     */
    public void loadLevelState() {
        try {
            LevelSaveData levelData = LevelDAO.loadLevelState(playerId, levelId);
            
            // Restore all game objects
            for (LevelSaveData.GameObjectSaveData objData : levelData.walls) {
                Wall wall = new Wall(objData.x, objData.y, objData.width, objData.height);
                walls.add(wall);
            }
            
            // Restore other objects similarly...
            
            System.out.println("Level state restored from database");
            
        } catch (SQLException e) {
            System.err.println("Failed to load level: " + e.getMessage());
        }
    }
}
```

---

## Step 7: Data Normalization Strategy

### 1. **Atomic Saves** (One Player = One Save)
```sql
-- Each player has exactly one active save
SELECT * FROM player_saves WHERE player_id = 1;
```

### 2. **Level Checkpoint Saves** (One Level Save Per Player)
```sql
-- Each player has one save per level
SELECT * FROM level_saves WHERE player_id = 1 AND level_id = 3;
```

### 3. **Denormalized GameObject Index** (Fast Queries)
```sql
-- Quick lookup: Find all enemies in player 1's level 3 save
SELECT object_data_json FROM game_objects 
WHERE save_id = (SELECT save_id FROM level_saves WHERE player_id = 1 AND level_id = 3)
AND object_type = 'Enemy';
```

### 4. **JSON Blobs** (Flexibility + Performance)
- Main data as JSON: `player_saves.save_data_json`, `level_saves.level_data_json`
- Individual columns for frequent queries: `x`, `y`, `health`, etc.
- Best of both worlds: normalized queries + flexible schema

---

## Step 8: Useful SQL Queries

### Get all player saves
```sql
SELECT p.player_name, ps.x, ps.y, ps.health_current, ps.health_max, ps.diamonds, ps.updated_at 
FROM players p
JOIN player_saves ps ON p.player_id = ps.player_id;
```

### Get level progress for a player
```sql
SELECT l.level_name, ls.save_timestamp, COUNT(go.object_id) as object_count
FROM players p
JOIN level_saves ls ON p.player_id = ls.player_id
JOIN levels l ON ls.level_id = l.level_id
LEFT JOIN game_objects go ON ls.save_id = go.save_id
WHERE p.player_id = 1
GROUP BY l.level_id;
```

### Get all enemies in a level save
```sql
SELECT object_id_str, x, y, object_data_json
FROM game_objects
WHERE save_id = 1 AND object_type IN ('Enemy', 'Tentacle');
```

### Find player with highest health
```sql
SELECT p.player_name, ps.health_current, ps.health_max, ps.diamonds, ps.current_level
FROM players p
JOIN player_saves ps ON p.player_id = ps.player_id
ORDER BY ps.health_current DESC LIMIT 1;
```

---

## Step 9: Backup & Restore

### Backup (XAMPP PhpMyAdmin or command line)
```bash
mysqldump -u root -p chromashift_db > chromashift_backup.sql
```

### Restore
```bash
mysql -u root -p chromashift_db < chromashift_backup.sql
```

---

## Step 10: Testing

**Create a simple test class:**

```java
package com.jjmc.chromashift.database;

public class DatabaseTest {
    public static void main(String[] args) {
        try {
            // 1. Test connection
            System.out.println("=== Testing Database Connection ===");
            DatabaseConnection.testConnection();
            
            // 2. Create player
            System.out.println("\n=== Creating Player ===");
            int playerId = PlayerDAO.createPlayer("TestHero", "levels/level1.json");
            
            // 3. Create player save
            System.out.println("\n=== Saving Player ===");
            PlayerIO.PlayerState playerState = new PlayerIO.PlayerState();
            playerState.x = 100;
            playerState.y = 200;
            playerState.healthCurrent = 100;
            playerState.healthMax = 100;
            playerState.diamonds = 5;
            playerState.currentLevel = "levels/level1.json";
            playerState.visitedLevels = new com.badlogic.gdx.utils.Array<>();
            playerState.visitedLevels.add("levels/tutorial.json");
            PlayerDAO.savePlayer(playerId, playerState);
            
            // 4. Load player
            System.out.println("\n=== Loading Player ===");
            PlayerIO.PlayerState loaded = PlayerDAO.loadPlayer(playerId);
            System.out.println("Loaded: x=" + loaded.x + ", y=" + loaded.y + 
                             ", health=" + loaded.healthCurrent + "/" + loaded.healthMax +
                             ", diamonds=" + loaded.diamonds);
            
            // 5. Register level
            System.out.println("\n=== Registering Level ===");
            int levelId = LevelDAO.registerLevel("Level 1", "levels/level1.json", 
                                                100, 200, -288, 1024);
            
            // 6. Save level
            System.out.println("\n=== Saving Level ===");
            LevelSaveData levelData = new LevelSaveData("levels/level1.json", "Level 1", 100, 200);
            LevelDAO.saveLevelState(playerId, levelId, levelData);
            
            // 7. Load level
            System.out.println("\n=== Loading Level ===");
            LevelSaveData levelLoaded = LevelDAO.loadLevelState(playerId, levelId);
            System.out.println("Loaded: " + levelLoaded);
            
            System.out.println("\n✓ All tests passed!");
            
        } catch (Exception e) {
            System.err.println("✗ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

---

## Summary

| Component | Purpose |
|-----------|---------|
| `DatabaseConnection` | JDBC connection pooling to XAMPP |
| `PlayerDAO` | Save/load `PlayerIO.PlayerState` to `player_saves` |
| `LevelDAO` | Save/load `LevelSaveData` to `level_saves` |
| `player_saves` | Stores player state (position, health, skills, etc.) |
| `level_saves` | Stores full level snapshots (all GameObjects) |
| `game_objects` | Denormalized index for efficient queries by type |

All data flows through `PlayerIO.capture()`/`PlayerIO.applyToPlayer()` for seamless serialization using `PlayerIO.PlayerState`.


package com.jjmc.chromashift.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jjmc.chromashift.save.PlayerSaveData;
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
    public static void savePlayer(int playerId, PlayerSaveData playerData) throws SQLException {
        // First, check if save exists
        String checkSql = "SELECT COUNT(*) FROM player_saves WHERE player_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            boolean exists = rs.getInt(1) > 0;
            
            if (exists) {
                updatePlayerSave(conn, playerId, playerData);
            } else {
                insertPlayerSave(conn, playerId, playerData);
            }
        }
    }
    
    /**
     * Insert new player save
     */
    private static void insertPlayerSave(Connection conn, int playerId, PlayerSaveData playerData) throws SQLException {
        String sql = "INSERT INTO player_saves (" +
                "player_id, x, y, velocity_x, velocity_y, facing_left, " +
                "on_ground, can_jump, dashing, dash_timer, dash_cooldown_timer, dash_used, dash_hover_remaining, " +
                "attacking, air_attacking, air_attack_timer, attack_cooldown_timer, current_combo, " +
                "health_current, health_max, mana_current, mana_max, stamina_current, stamina_max, " +
                "is_stunned, respawn_invul_remaining, respawn_stun_remaining, " +
                "diamonds, shield, key_count, potion_count, held_object_id, " +
                "skill_q_json, skill_e_json, active_skill_json, unlocked_skill_names_json, " +
                "respawn_x, respawn_y, current_level, visited_levels_json, " +
                "timer_map_json, int_counter_map_json, flag_map_json, float_map_json, " +
                "save_data_json, save_timestamp, save_slot" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            ps.setInt(paramIndex++, playerId);
            ps.setFloat(paramIndex++, playerData.x);
            ps.setFloat(paramIndex++, playerData.y);
            ps.setFloat(paramIndex++, playerData.velocityX);
            ps.setFloat(paramIndex++, playerData.velocityY);
            ps.setBoolean(paramIndex++, playerData.facingLeft);
            ps.setBoolean(paramIndex++, playerData.onGround);
            ps.setBoolean(paramIndex++, playerData.canJump);
            ps.setBoolean(paramIndex++, playerData.dashing);
            ps.setFloat(paramIndex++, playerData.dashTimer);
            ps.setFloat(paramIndex++, playerData.dashCooldownTimer);
            ps.setBoolean(paramIndex++, playerData.dashUsed);
            ps.setFloat(paramIndex++, playerData.dashHoverRemaining);
            ps.setBoolean(paramIndex++, playerData.attacking);
            ps.setBoolean(paramIndex++, playerData.airAttacking);
            ps.setFloat(paramIndex++, playerData.airAttackTimer);
            ps.setFloat(paramIndex++, playerData.attackCooldownTimer);
            ps.setInt(paramIndex++, playerData.currentCombo);
            ps.setFloat(paramIndex++, playerData.healthCurrent);
            ps.setFloat(paramIndex++, playerData.healthMax);
            ps.setFloat(paramIndex++, playerData.manaCurrent);
            ps.setFloat(paramIndex++, playerData.manaMax);
            ps.setFloat(paramIndex++, playerData.staminaCurrent);
            ps.setFloat(paramIndex++, playerData.staminaMax);
            ps.setBoolean(paramIndex++, playerData.isStunned);
            ps.setFloat(paramIndex++, playerData.respawnInvulRemaining);
            ps.setFloat(paramIndex++, playerData.respawnStunRemaining);
            ps.setInt(paramIndex++, playerData.diamonds);
            ps.setInt(paramIndex++, playerData.shield);
            ps.setInt(paramIndex++, playerData.keyCount);
            ps.setInt(paramIndex++, playerData.potionCount);
            ps.setString(paramIndex++, playerData.heldObjectId);
            ps.setString(paramIndex++, playerData.skillQ != null ? gson.toJson(playerData.skillQ) : null);
            ps.setString(paramIndex++, playerData.skillE != null ? gson.toJson(playerData.skillE) : null);
            ps.setString(paramIndex++, playerData.activeSkill != null ? gson.toJson(playerData.activeSkill) : null);
            ps.setString(paramIndex++, gson.toJson(playerData.unlockedSkillNames));
            ps.setFloat(paramIndex++, playerData.respawnX);
            ps.setFloat(paramIndex++, playerData.respawnY);
            ps.setString(paramIndex++, playerData.currentLevel);
            ps.setString(paramIndex++, gson.toJson(playerData.visitedLevels));
            ps.setString(paramIndex++, gson.toJson(playerData.timerMap));
            ps.setString(paramIndex++, gson.toJson(playerData.intCounterMap));
            ps.setString(paramIndex++, gson.toJson(playerData.flagMap));
            ps.setString(paramIndex++, gson.toJson(playerData.floatMap));
            ps.setString(paramIndex++, gson.toJson(playerData));
            ps.setLong(paramIndex++, playerData.saveTimestamp);
            ps.setInt(paramIndex++, playerData.saveSlot);
            
            ps.executeUpdate();
            System.out.println("✓ Player save inserted (ID: " + playerId + ")");
        }
    }
    
    /**
     * Update existing player save
     */
    private static void updatePlayerSave(Connection conn, int playerId, PlayerSaveData playerData) throws SQLException {
        String sql = "UPDATE player_saves SET " +
                "x=?, y=?, velocity_x=?, velocity_y=?, facing_left=?, " +
                "on_ground=?, can_jump=?, dashing=?, dash_timer=?, dash_cooldown_timer=?, dash_used=?, dash_hover_remaining=?, " +
                "attacking=?, air_attacking=?, air_attack_timer=?, attack_cooldown_timer=?, current_combo=?, " +
                "health_current=?, health_max=?, mana_current=?, mana_max=?, stamina_current=?, stamina_max=?, " +
                "is_stunned=?, respawn_invul_remaining=?, respawn_stun_remaining=?, " +
                "diamonds=?, shield=?, key_count=?, potion_count=?, held_object_id=?, " +
                "skill_q_json=?, skill_e_json=?, active_skill_json=?, unlocked_skill_names_json=?, " +
                "respawn_x=?, respawn_y=?, current_level=?, visited_levels_json=?, " +
                "timer_map_json=?, int_counter_map_json=?, flag_map_json=?, float_map_json=?, " +
                "save_data_json=?, save_timestamp=?, save_slot=?, updated_at=NOW() " +
                "WHERE player_id=?";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            ps.setFloat(paramIndex++, playerData.x);
            ps.setFloat(paramIndex++, playerData.y);
            ps.setFloat(paramIndex++, playerData.velocityX);
            ps.setFloat(paramIndex++, playerData.velocityY);
            ps.setBoolean(paramIndex++, playerData.facingLeft);
            ps.setBoolean(paramIndex++, playerData.onGround);
            ps.setBoolean(paramIndex++, playerData.canJump);
            ps.setBoolean(paramIndex++, playerData.dashing);
            ps.setFloat(paramIndex++, playerData.dashTimer);
            ps.setFloat(paramIndex++, playerData.dashCooldownTimer);
            ps.setBoolean(paramIndex++, playerData.dashUsed);
            ps.setFloat(paramIndex++, playerData.dashHoverRemaining);
            ps.setBoolean(paramIndex++, playerData.attacking);
            ps.setBoolean(paramIndex++, playerData.airAttacking);
            ps.setFloat(paramIndex++, playerData.airAttackTimer);
            ps.setFloat(paramIndex++, playerData.attackCooldownTimer);
            ps.setInt(paramIndex++, playerData.currentCombo);
            ps.setFloat(paramIndex++, playerData.healthCurrent);
            ps.setFloat(paramIndex++, playerData.healthMax);
            ps.setFloat(paramIndex++, playerData.manaCurrent);
            ps.setFloat(paramIndex++, playerData.manaMax);
            ps.setFloat(paramIndex++, playerData.staminaCurrent);
            ps.setFloat(paramIndex++, playerData.staminaMax);
            ps.setBoolean(paramIndex++, playerData.isStunned);
            ps.setFloat(paramIndex++, playerData.respawnInvulRemaining);
            ps.setFloat(paramIndex++, playerData.respawnStunRemaining);
            ps.setInt(paramIndex++, playerData.diamonds);
            ps.setInt(paramIndex++, playerData.shield);
            ps.setInt(paramIndex++, playerData.keyCount);
            ps.setInt(paramIndex++, playerData.potionCount);
            ps.setString(paramIndex++, playerData.heldObjectId);
            ps.setString(paramIndex++, playerData.skillQ != null ? gson.toJson(playerData.skillQ) : null);
            ps.setString(paramIndex++, playerData.skillE != null ? gson.toJson(playerData.skillE) : null);
            ps.setString(paramIndex++, playerData.activeSkill != null ? gson.toJson(playerData.activeSkill) : null);
            ps.setString(paramIndex++, gson.toJson(playerData.unlockedSkillNames));
            ps.setFloat(paramIndex++, playerData.respawnX);
            ps.setFloat(paramIndex++, playerData.respawnY);
            ps.setString(paramIndex++, playerData.currentLevel);
            ps.setString(paramIndex++, gson.toJson(playerData.visitedLevels));
            ps.setString(paramIndex++, gson.toJson(playerData.timerMap));
            ps.setString(paramIndex++, gson.toJson(playerData.intCounterMap));
            ps.setString(paramIndex++, gson.toJson(playerData.flagMap));
            ps.setString(paramIndex++, gson.toJson(playerData.floatMap));
            ps.setString(paramIndex++, gson.toJson(playerData));
            ps.setLong(paramIndex++, playerData.saveTimestamp);
            ps.setInt(paramIndex++, playerData.saveSlot);
            ps.setInt(paramIndex++, playerId);
            
            ps.executeUpdate();
            System.out.println("✓ Player save updated (ID: " + playerId + ")");
        }
    }
    
    /**
     * Load player save from database
     */
    public static PlayerSaveData loadPlayer(int playerId) throws SQLException {
        String sql = "SELECT save_data_json FROM player_saves WHERE player_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                String jsonData = rs.getString("save_data_json");
                PlayerSaveData playerData = gson.fromJson(jsonData, PlayerSaveData.class);
                System.out.println("✓ Player loaded (ID: " + playerId + ")");
                return playerData;
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
# XAMPP Database Setup for Chromashift

This guide explains how to set up and use the MySQL database with XAMPP for the Chromashift game.

## Prerequisites

- XAMPP installed and running (MySQL service enabled)
- Java project with JDBC MySQL driver dependency
- Database connection configured in `DatabaseConnection.java`

## Quick Start

### 1. Start XAMPP Services

1. Open XAMPP Control Panel
2. Start **Apache** (optional, only if using web interface)
3. Start **MySQL** (required)

### 2. Access phpMyAdmin

1. Open browser and navigate to: `http://localhost/phpmyadmin`
2. Login with:
   - **Username**: `root`
   - **Password**: (leave blank by default)

### 3. Create the Database

Run this SQL in phpMyAdmin to create the Chromashift database:

```sql
CREATE DATABASE IF NOT EXISTS chromashift_db;
USE chromashift_db;
```

### 4. Create Tables

#### Players Table
```sql
CREATE TABLE IF NOT EXISTS players (
    player_id INT AUTO_INCREMENT PRIMARY KEY,
    player_name VARCHAR(255) NOT NULL UNIQUE,
    level_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### Player Saves Table
```sql
CREATE TABLE IF NOT EXISTS player_saves (
    save_id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    x FLOAT,
    y FLOAT,
    velocity_x FLOAT,
    velocity_y FLOAT,
    facing_left BOOLEAN,
    on_ground BOOLEAN,
    can_jump BOOLEAN,
    dashing BOOLEAN,
    dash_timer FLOAT,
    dash_cooldown_timer FLOAT,
    dash_used BOOLEAN,
    dash_hover_remaining FLOAT,
    attacking BOOLEAN,
    air_attacking BOOLEAN,
    air_attack_timer FLOAT,
    attack_cooldown_timer FLOAT,
    health_current FLOAT,
    health_max FLOAT,
    is_stunned BOOLEAN,
    respawn_invul_remaining FLOAT,
    respawn_stun_remaining FLOAT,
    diamonds INT,
    shield INT,
    key_count INT,
    potion_count INT,
    skill_q_json TEXT,
    skill_e_json TEXT,
    active_skill_json TEXT,
    respawn_x FLOAT,
    respawn_y FLOAT,
    current_level VARCHAR(255),
    visited_levels_json TEXT,
    save_data_json LONGTEXT,
    save_timestamp BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE
);
```

#### Levels Table
```sql
CREATE TABLE IF NOT EXISTS levels (
    level_id INT AUTO_INCREMENT PRIMARY KEY,
    level_name VARCHAR(255) NOT NULL,
    level_path VARCHAR(255) NOT NULL,
    spawn_x FLOAT,
    spawn_y FLOAT,
    arena_left FLOAT,
    arena_right FLOAT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### Level Saves Table
```sql
CREATE TABLE IF NOT EXISTS level_saves (
    save_id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    level_id INT NOT NULL,
    level_data_json LONGTEXT,
    save_timestamp BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE,
    FOREIGN KEY (level_id) REFERENCES levels(level_id) ON DELETE CASCADE
);
```

#### Game Objects Table (for level state denormalization)
```sql
CREATE TABLE IF NOT EXISTS game_objects (
    object_id INT AUTO_INCREMENT PRIMARY KEY,
    save_id INT NOT NULL,
    object_type VARCHAR(255),
    object_id_str VARCHAR(255),
    x FLOAT,
    y FLOAT,
    width FLOAT,
    height FLOAT,
    is_active BOOLEAN,
    object_data_json LONGTEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (save_id) REFERENCES level_saves(save_id) ON DELETE CASCADE
);
```

## Java Integration

### Configuration

Edit `DatabaseConnection.java`:

```java
private static final String DB_URL = "jdbc:mysql://localhost:3306/chromashift_db";
private static final String DB_USER = "root";
private static final String DB_PASSWORD = "";  // Empty by default
```

### Usage Examples

#### Save Player State
```java
PlayerIO.PlayerState state = PlayerIO.capture(player, currentLevel, visitedLevels);
PlayerDAO.savePlayerState(playerId, state);
```

#### Load Player State
```java
PlayerIO.PlayerState loaded = PlayerDAO.loadPlayerState(playerId);
if (loaded != null) {
    PlayerIO.applyToPlayer(player, loaded);
}
```

#### Save Level State
```java
LevelSaveData levelData = new LevelSaveData();
// ... populate levelData ...
LevelDAO.saveLevelState(playerId, levelId, levelData);
```

#### Load Level State
```java
LevelSaveData levelData = LevelDAO.loadLevelState(playerId, levelId);
if (levelData != null) {
    // ... restore level state ...
}
```

## Player Data Schema (PlayerIO-aligned)

The database stores **only** PlayerIO.PlayerState fields:

- **Position**: x, y
- **Velocity**: velocity_x, velocity_y
- **Movement**: facing_left, on_ground, can_jump
- **Dash**: dashing, dash_timer, dash_cooldown_timer, dash_used, dash_hover_remaining
- **Attack**: attacking, air_attacking, air_attack_timer, attack_cooldown_timer
- **Health**: health_current, health_max
- **Respawn**: respawn_x, respawn_y, respawn_invul_remaining, respawn_stun_remaining, is_stunned
- **Inventory**: diamonds, shield, key_count, potion_count
- **Skills**: skill_q_json, skill_e_json, active_skill_json (stored as JSON)
- **Level Tracking**: current_level, visited_levels_json
- **Full State**: save_data_json (entire PlayerState as JSON backup)

**No unsupported fields** (mana, stamina, combo, heldObjectId, maps) are stored.

## Troubleshooting

### "Connection Refused"
- Ensure MySQL service is running in XAMPP
- Check port 3306 is not in use
- Verify credentials in `DatabaseConnection.java`

### "Unknown Database"
- Confirm database creation in phpMyAdmin
- Check database name matches in connection string

### "Access Denied"
- Default user is `root` with empty password
- If changed, update `DatabaseConnection.java`

### JDBC Driver Not Found
- Ensure `mysql-connector-java` JAR is in classpath
- Add to Gradle: `implementation 'mysql:mysql-connector-java:8.0.33'`

## References

- XAMPP Documentation: https://www.apachefriends.org/
- MySQL Documentation: https://dev.mysql.com/doc/
- JDBC Guide: https://dev.mysql.com/doc/connector-j/en/

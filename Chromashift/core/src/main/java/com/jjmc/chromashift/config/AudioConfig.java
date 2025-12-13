package com.jjmc.chromashift.config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.chromashift.helper.SoundManager;

/**
 * Manages audio volume settings persistence to Config.json.
 * Stores and restores Master, Music, and SFX volumes between game sessions.
 */
public class AudioConfig {
    private static final String CONFIG_PATH = "Config.json";
    
    private float masterVolume = 1f;
    private float musicVolume = 1f;
    private float sfxVolume = 1f;

    private static AudioConfig instance;

    private AudioConfig() {}

    /**
     * Get or create the singleton instance.
     */
    public static AudioConfig getInstance() {
        if (instance == null) {
            instance = new AudioConfig();
            instance.load();
        }
        return instance;
    }

    /**
     * Load audio settings from Config.json. If file doesn't exist or is empty,
     * use defaults and create it.
     */
    public void load() {
        try {
            FileHandle configFile = Gdx.files.internal(CONFIG_PATH);
            
            if (configFile.exists() && configFile.length() > 0) {
                Json json = new Json();
                AudioConfigData data = json.fromJson(AudioConfigData.class, configFile);
                
                if (data != null) {
                    this.masterVolume = clamp(data.masterVolume);
                    this.musicVolume = clamp(data.musicVolume);
                    this.sfxVolume = clamp(data.sfxVolume);
                    
                    // Apply loaded volumes to SoundManager
                    SoundManager.setMasterVolume(this.masterVolume);
                    SoundManager.setMusicVolume(this.musicVolume);
                    SoundManager.setSfxVolume(this.sfxVolume);
                    
                    Gdx.app.log("AudioConfig", "Loaded volumes - Master: " + masterVolume + 
                        ", Music: " + musicVolume + ", SFX: " + sfxVolume);
                    return;
                }
            }
        } catch (Exception e) {
            Gdx.app.error("AudioConfig", "Failed to load config: " + e.getMessage());
        }
        
        // Use defaults if load failed
        this.masterVolume = 1f;
        this.musicVolume = 1f;
        this.sfxVolume = 1f;
        save();
    }

    /**
     * Save current audio settings to Config.json.
     */
    public void save() {
        try {
            AudioConfigData data = new AudioConfigData();
            data.masterVolume = this.masterVolume;
            data.musicVolume = this.musicVolume;
            data.sfxVolume = this.sfxVolume;
            
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            String jsonString = json.prettyPrint(data);
            
            FileHandle configFile = Gdx.files.local(CONFIG_PATH);
            configFile.writeString(jsonString, false);
            
            Gdx.app.log("AudioConfig", "Saved volumes - Master: " + masterVolume + 
                ", Music: " + musicVolume + ", SFX: " + sfxVolume);
        } catch (Exception e) {
            Gdx.app.error("AudioConfig", "Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Update master volume and sync with SoundManager.
     */
    public void setMasterVolume(float volume) {
        this.masterVolume = clamp(volume);
        SoundManager.setMasterVolume(this.masterVolume);
        save();
    }

    /**
     * Update music volume and sync with SoundManager.
     */
    public void setMusicVolume(float volume) {
        this.musicVolume = clamp(volume);
        SoundManager.setMusicVolume(this.musicVolume);
        save();
    }

    /**
     * Update SFX volume and sync with SoundManager.
     */
    public void setSfxVolume(float volume) {
        this.sfxVolume = clamp(volume);
        SoundManager.setSfxVolume(this.sfxVolume);
        save();
    }

    public float getMasterVolume() {
        return masterVolume;
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public float getSfxVolume() {
        return sfxVolume;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /**
     * Inner class for JSON serialization.
     */
    public static class AudioConfigData {
        public float masterVolume = 1f;
        public float musicVolume = 1f;
        public float sfxVolume = 1f;
    }
}

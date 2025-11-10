package com.jjmc.chromashift.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.Array;

/**
 * Simple JSON-based level load/save utility using libGDX Json and file handles.
 * Levels should call LevelIO.load("levels/level1.json") to get a LevelState
 * structure. Saved changes are written to the local storage so they persist
 * between runs.
 */
public class LevelIO {

    public static class LevelState {
        public Meta meta;
        public SpawnData spawn;
        public Array<WallData> walls;
        public Array<InteractableData> interactables;
        public Array<BoxData> boxes;
        public Array<OrbData> orbs;
        public BossData boss;

        public static class Meta { public String name; public int version; }
        public static class SpawnData { public float x, y; }
        public static class WallData { public float x, y, width, height; }
        public static class InteractableData { public String type; public String id; public float x, y; public String targetId; public String color; public String openDirection; public int cols; public int rows; }
        public static class BoxData { public float x, y; }
        public static class OrbData { public float x, y; }
        public static class BossData { public float x, y; }
    }

    private static final Json json = new Json();

    /**
     * Load a level JSON. First try local (writable) path so user edits persist.
     * If not present locally, fall back to internal (packaged) asset and copy it
     * to local for edits.
     * @param path relative path under assets (e.g. "levels/level1.json")
     */
    public static LevelState load(String path) {
        try {
            FileHandle local = Gdx.files.local(path);
            if (local.exists()) {
                String text = local.readString();
                return json.fromJson(LevelState.class, text);
            }
            // try internal (packaged in assets)
            FileHandle internal = Gdx.files.internal(path);
            if (internal.exists()) {
                String text = internal.readString();
                // write a copy to local so editors can save
                try { Gdx.files.local(path).writeString(text, false); } catch (Exception ignored) {}
                return json.fromJson(LevelState.class, text);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // return empty default state
        LevelState s = new LevelState();
        s.meta = new LevelState.Meta(); s.meta.name = path; s.meta.version = 1;
        s.spawn = new LevelState.SpawnData(); s.spawn.x = 0; s.spawn.y = 40;
        s.walls = new Array<>();
        s.interactables = new Array<>();
        s.boxes = new Array<>();
        s.orbs = new Array<>();
        s.boss = null;
        return s;
    }

    /** Save the level state to local storage (overwrites). */
    public static boolean save(String path, LevelState state) {
        try {
            String text = json.prettyPrint(state);
            Gdx.files.local(path).writeString(text, false);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

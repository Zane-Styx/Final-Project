package com.jjmc.chromashift.screens.levels;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
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
        public Array<LaunchpadData> launchpads;
        public Array<LaserData> lasers;
        public Array<MirrorData> mirrors;
        public Array<GlassData> glasses;
        public Array<DiamondData> diamonds;
        public Array<ShopData> shops;
        public Array<TentacleData> tentacles;
            public Array<KeyData> keys;
            public Array<LockedDoorData> lockedDoors;
        public BossData boss;

        public static class Meta {
            public String name;
            public int version;
        }

        public static class SpawnData {
            public float x, y;
        }

        public static class WallData {
            public float x, y, width, height;
        }

        public static class InteractableData {
            public String type;
            public String id;
            public float x, y;
            public String targetId;
            public String color;
            public String openDirection;
            public String orientation;
            public int cols;
            public int rows;
            // Door open/close speeds (units per second). Defaults to 3.0f when not set.
            public float openSpeed = 3f;
            public float closeSpeed = 3f;
        }

        public static class BoxData {
            public float x, y;
            public String color = "CYAN";
            // Respawn area dimensions (centered on x,y). Defaults set by editor if 0.
            public float areaW = 1600f;
            public float areaH = 1200f;
        }

        public static class OrbData {
            public float x, y;
            // Whether this orb should bounce on collisions (default: true for backward
            // compatibility)
            public boolean bouncy = true;
            // Respawn area dimensions (centered on x,y). Defaults set by editor if 0.
            public float areaW = 1600f;
            public float areaH = 1200f;
        }

        public static class LaunchpadData {
            public float x, y;
            public String direction; // UP, DOWN, LEFT, RIGHT, etc.
            public float speed = 600f; // default launch speed
        }

        public static class LaserData {
            public String id;
            public float x, y;
            public float rotation = 0f; // degrees
            public int maxBounces = 8;
            // Optional control links: button IDs to rotate left/right, and lever ID to
            // rotate on toggle
            public String leftButtonId;
            public String rightButtonId;
            public String leverId;
            public float rotateStep = 90f;
            // If true, spawn as an interactable LaserRay that the player can rotate
            public boolean rotating = false;
        }

        public static class MirrorData {
            public String id; // optional unique id for linking
            public float x, y, width, height;
            public float angleDeg = 45f;
        }

        public static class GlassData {
            public float x, y, width, height;
            public boolean rainbow = true;
            public float speed = 1.5f;
            public String color = "CYAN"; // user-selected base color (RAINBOW overrides)
        }

        public static class DiamondData {
            public float x, y;
        }

        public static class ShopData {
            public float x, y;
        }

        public static class TentacleData {
            public float x, y;
            public int segments = 30; // Default segment count
        }

        public static class KeyData {
            public float x, y;
        }

        public static class LockedDoorData {
            public float x, y;
        }

        public static class BossData {
            public float x, y;
        }
    }

    private static final Json json = new Json();

    static {
        // Be permissive when parsing level files: ignore unknown fields so
        // that small-format differences or extra values in shipped JSON won't
        // break the editor. This helps avoid SerializationException when
        // loading hand-edited or legacy level data.
        try {
            json.setIgnoreUnknownFields(true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Load a level JSON. First try local (writable) path so user edits persist.
     * If not present locally, fall back to internal (packaged) asset and copy it
     * to local for edits.
     * 
     * @param path relative path under assets (e.g. "levels/level1.json")
     */
    public static LevelState load(String path) {
        try {
            // Primary behavior: load the packaged internal (build/resource) level first
            // so both TestSceneScreen and LevelMakerScreen use the same source of truth.
            FileHandle internal = Gdx.files.internal(path);
            if (internal != null && internal.exists()) {
                String text = internal.readString();
                try {
                    LevelState s = json.fromJson(LevelState.class, text);
                    // Ensure new fields are initialized (for backward compatibility with old level files)
                    ensureArraysInitialized(s);
                    // Copy the internal asset into the project's `assets/` folder so editors
                    // and source control can pick up the canonical file. Overwrite if present.
                    try {
                        java.io.File assetsDir = findProjectAssetsDir();
                        if (assetsDir != null) {
                            java.io.File out = new java.io.File(assetsDir,
                                    path.replace('/', java.io.File.separatorChar));
                            java.io.File parent = out.getParentFile();
                            if (parent != null && !parent.exists())
                                parent.mkdirs();
                            Gdx.files.absolute(out.getAbsolutePath()).writeString(text, false);
                            Gdx.app.log("LevelIO",
                                    "Copied internal level to workspace assets: " + out.getAbsolutePath());
                        }
                    } catch (Exception ex) {
                        Gdx.app.error("LevelIO",
                                "Failed to copy internal level to workspace assets: " + ex.getMessage());
                    }
                    return s;
                } catch (Exception parseEx) {
                    Gdx.app.error("LevelIO", "Failed to parse level JSON from internal asset: " + path, parseEx);
                    try {
                        Gdx.app.error("LevelIO", "Content preview: " + text.substring(0, Math.min(400, text.length())));
                    } catch (Exception ignored) {
                    }
                }
            }
            // If internal not available or parsing failed, try the workspace assets folder
            // as a fallback.
            java.io.File assetsDir = findProjectAssetsDir();
            if (assetsDir != null) {
                java.io.File candidate = new java.io.File(assetsDir, path.replace('/', java.io.File.separatorChar));
                if (candidate.exists()) {
                    String text = Gdx.files.absolute(candidate.getAbsolutePath()).readString();
                    try {
                        LevelState s = json.fromJson(LevelState.class, text);
                        ensureArraysInitialized(s);
                        return s;
                    } catch (Exception parseEx) {
                        Gdx.app.error("LevelIO",
                                "Failed to parse level JSON from workspace asset: " + candidate.getAbsolutePath(),
                                parseEx);
                        try {
                            Gdx.app.error("LevelIO",
                                    "Content preview: " + text.substring(0, Math.min(400, text.length())));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // return empty default state
        LevelState s = new LevelState();
        s.meta = new LevelState.Meta();
        s.meta.name = path;
        s.meta.version = 1;
        s.spawn = new LevelState.SpawnData();
        s.spawn.x = 0;
        s.spawn.y = 40;
        s.walls = new Array<>();
        s.interactables = new Array<>();
        s.boxes = new Array<>();
        s.orbs = new Array<>();
        s.launchpads = new Array<>();
        s.lasers = new Array<>();
        s.mirrors = new Array<>();
        s.glasses = new Array<>();
        s.diamonds = new Array<>();
        s.shops = new Array<>();
        s.boss = null;
        s.tentacles = new Array<>();
        s.keys = new Array<>();
        s.lockedDoors = new Array<>();
        return s;
    }

    /**
     * Save the level state to workspace assets and mirror into build resources
     * (overwrites).
     */
    public static boolean save(String path, LevelState state) {
        try {
            String text = json.prettyPrint(state);
            // Attempt to write directly into the project's assets folder. Do NOT write to
            // application-local storage to avoid creating duplicate level files outside the
            // project's assets directory.
            java.io.File assetsDir = findProjectAssetsDir();
            if (assetsDir == null) {
                Gdx.app.error("LevelIO", "Project assets folder not found; aborting save for: " + path);
                return false;
            }
            java.io.File out = new java.io.File(assetsDir, path.replace('/', java.io.File.separatorChar));
            java.io.File parent = out.getParentFile();
            if (parent != null && !parent.exists())
                parent.mkdirs();
            try {
                Gdx.files.absolute(out.getAbsolutePath()).writeString(text, false);
                Gdx.app.log("LevelIO", "Saved level to workspace assets: " + out.getAbsolutePath());
                // Also mirror to build resources so the running game/editor sees the latest
                // file
                try {
                    boolean ok = writeToBuildResources(path, text);
                    if (ok) {
                        Gdx.app.log("LevelIO", "Mirrored level to build resources: " + path);
                    } else {
                        Gdx.app.log("LevelIO", "Build resources directory not found; skipping mirror for: " + path);
                    }
                } catch (Exception ex) {
                    Gdx.app.error("LevelIO", "Failed to mirror saved level to build resources: " + ex.getMessage(), ex);
                }
                return true;
            } catch (Exception ex) {
                Gdx.app.error("LevelIO", "Failed to write level file to workspace assets: " + ex.getMessage(), ex);
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Load a level preferring the workspace `assets/` copy first, then copy it into
     * the game's build resources folder (if found) so the running game sees the
     * latest file. This is intended for use by the Level Maker/editor which edits
     * files in the workspace and needs them synced into the built resources.
     */
    public static LevelState loadFromWorkspaceThenCopyToBuild(String path) {
        try {
            // Try workspace assets first
            java.io.File assetsDir = findProjectAssetsDir();
            if (assetsDir != null) {
                java.io.File candidate = new java.io.File(assetsDir, path.replace('/', java.io.File.separatorChar));
                if (candidate.exists()) {
                    String text = Gdx.files.absolute(candidate.getAbsolutePath()).readString();
                    try {
                        LevelState s = json.fromJson(LevelState.class, text);
                        ensureArraysInitialized(s);
                        // Try to write into build resources so the packaged/internal copy
                        // reflects the editor changes for runtime tests.
                        try {
                            boolean ok = writeToBuildResources(path, text);
                            if (ok)
                                Gdx.app.log("LevelIO", "Copied workspace level to build resources: " + path);
                        } catch (Exception ex) {
                            Gdx.app.error("LevelIO",
                                    "Failed to copy workspace level to build resources: " + ex.getMessage(), ex);
                        }
                        return s;
                    } catch (Exception parseEx) {
                        Gdx.app.error("LevelIO",
                                "Failed to parse level JSON from workspace asset: " + candidate.getAbsolutePath(),
                                parseEx);
                        try {
                            Gdx.app.error("LevelIO",
                                    "Content preview: " + text.substring(0, Math.min(400, text.length())));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            Gdx.app.error("LevelIO", "Error while loading from workspace: " + e.getMessage(), e);
        }
        // Fallback to the regular load (internal-first behavior)
        return load(path);
    }

    /**
     * Attempt to write the given content into a discovered build resources folder.
     * Searches common module build output locations.
     */
    private static boolean writeToBuildResources(String path, String text) {
        try {
            java.io.File buildRes = findBuildResourcesDir();
            if (buildRes == null)
                return false;
            java.io.File out = new java.io.File(buildRes, path.replace('/', java.io.File.separatorChar));
            java.io.File parent = out.getParentFile();
            if (parent != null && !parent.exists())
                parent.mkdirs();
            Gdx.files.absolute(out.getAbsolutePath()).writeString(text, false);
            return true;
        } catch (Exception ex) {
            Gdx.app.error("LevelIO", "Failed writing to build resources: " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Heuristic: find a build/resources/main folder for common modules so the
     * editor can copy files into the build output used by the desktop launcher.
     */
    private static java.io.File findBuildResourcesDir() {
        try {
            java.io.File dir = new java.io.File(System.getProperty("user.dir"));
            int depth = 0;
            while (dir != null && depth < 8) {
                // check common module build locations
                java.io.File[] candidates = new java.io.File[] {
                        new java.io.File(dir, "lwjgl3/build/resources/main"),
                        new java.io.File(dir, "core/build/resources/main"),
                        new java.io.File(dir, "build/resources/main")
                };
                for (java.io.File c : candidates) {
                    if (c.exists() && c.isDirectory())
                        return c;
                }
                dir = dir.getParentFile();
                depth++;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Ensure all array fields in the level state are initialized to prevent null pointer exceptions.
     */
    private static void ensureArraysInitialized(LevelState s) {
        if (s.walls == null) s.walls = new Array<>();
        if (s.interactables == null) s.interactables = new Array<>();
        if (s.boxes == null) s.boxes = new Array<>();
        if (s.orbs == null) s.orbs = new Array<>();
        if (s.launchpads == null) s.launchpads = new Array<>();
        if (s.lasers == null) s.lasers = new Array<>();
        if (s.mirrors == null) s.mirrors = new Array<>();
        if (s.glasses == null) s.glasses = new Array<>();
        if (s.diamonds == null) s.diamonds = new Array<>();
        if (s.shops == null) s.shops = new Array<>();
        if (s.tentacles == null) s.tentacles = new Array<>();
        // New collectible/interactable arrays
        if (s.keys == null) s.keys = new Array<>();
        if (s.lockedDoors == null) s.lockedDoors = new Array<>();
    }

    /**
     * Try to discover the project's assets directory by walking up from the current
     * working
     * directory and looking for a folder named `assets` that also contains an
     * `assets.txt` or
     * `levels` subfolder. Returns null if not found.
     */
    private static java.io.File findProjectAssetsDir() {
        try {
            java.io.File dir = new java.io.File(System.getProperty("user.dir"));
            int depth = 0;
            while (dir != null && depth < 8) {
                java.io.File assets = new java.io.File(dir, "assets");
                if (assets.exists() && assets.isDirectory()) {
                    // heuristics: assets.txt or levels folder present
                    if (new java.io.File(assets, "assets.txt").exists()
                            || new java.io.File(assets, "levels").exists()) {
                        return assets;
                    }
                }
                dir = dir.getParentFile();
                depth++;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

package com.jjmc.chromashift.screens.levels;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.math.Rectangle;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.Wall;
import com.jjmc.chromashift.environment.interactable.*;
import com.jjmc.chromashift.entity.boss.BossInstance;

import java.util.HashMap;
import java.util.Map;

/**
 * Unified level loader that converts LevelIO.LevelState into runtime objects (walls,
 * solids, interactables, boss, spawn). Scenes and editors should use this to ensure
 * consistent placement and linking semantics across the project.
 */
public final class LevelLoader {

    private LevelLoader() {}

    public static class Result {
        public final Array<Wall> walls = new Array<>();
        public final Array<Solid> solids = new Array<>();
        public final Array<Interactable> interactables = new Array<>();
        public BossInstance boss; // optional
        public float spawnX;
        public float spawnY;
        // Door id -> instance for linking
        public final Map<String, Door> doorMap = new HashMap<>();
    }

    /** Load using packaged/internal-first assets. */
    public static Result load(String path) {
        LevelIO.LevelState state = LevelIO.load(path);
        return build(state);
    }

    /** Load preferring workspace assets and copying into build output (for editors). */
    public static Result loadFromWorkspace(String path) {
        LevelIO.LevelState state = LevelIO.loadFromWorkspaceThenCopyToBuild(path);
        return build(state);
    }

    /** Build runtime objects from a LevelState. */
    public static Result build(LevelIO.LevelState state) {
        Result out = new Result();
        if (state == null) return out;

        // Walls first (these are the base Solids)
        if (state.walls != null) {
            for (LevelIO.LevelState.WallData wd : state.walls) {
                Wall w = new Wall(wd.x, wd.y, (int) wd.width, (int) wd.height);
                out.walls.add(w);
                out.solids.add(w);
            }
        }

        // Create doors first so other interactables can link to them
        if (state.interactables != null) {
            for (LevelIO.LevelState.InteractableData idd : state.interactables) {
                if (idd == null || idd.type == null) continue;
                String t = idd.type.trim().toLowerCase();
                if (!"door".equals(t)) continue;

                Door.OpenDirection dir = Door.OpenDirection.UP;
                try { if (idd.openDirection != null) dir = Door.OpenDirection.valueOf(idd.openDirection.toUpperCase()); } catch (Exception ignored) {}

                int cols = Math.max(1, idd.cols);
                int rows = Math.max(1, idd.rows);

                // Always honor saved position and allow anywhere: no anchoring.
                Wall dummy = new Wall(idd.x, idd.y - 32, Math.max(1, cols), 1);
                float os = 3f, cs = 3f;
                try { os = (idd.openSpeed > 0f) ? idd.openSpeed : 3f; } catch (Exception ignored) {}
                try { cs = (idd.closeSpeed > 0f) ? idd.closeSpeed : 3f; } catch (Exception ignored) {}
                Door d = new Door(idd.x, dummy, cols, rows, dir, os, cs);

                out.interactables.add(d);
                out.solids.add(d);
                if (idd.id != null) out.doorMap.put(idd.id, d);
            }
        }

        // Other interactables (Buttons, Levers) possibly linking to doors
        if (state.interactables != null) {
            for (LevelIO.LevelState.InteractableData idd : state.interactables) {
                if (idd == null || idd.type == null) continue;
                String t = idd.type.trim().toLowerCase();
                if ("door".equals(t)) continue; // already handled

                switch (t) {
                    case "button" -> {
                        // Support multiple door ids comma-separated
                        Array<Door> targets = new Array<>();
                        if (idd.targetId != null && !idd.targetId.isEmpty()) {
                            String[] parts = idd.targetId.split(",");
                            for (String p : parts) {
                                String key = p.trim();
                                Door dd = out.doorMap.get(key);
                                if (dd != null) targets.add(dd);
                            }
                        }
                        Button.ButtonColor col = Button.ButtonColor.GREEN;
                        try { if (idd.color != null) col = Button.ButtonColor.valueOf(idd.color.toUpperCase()); } catch (Exception ignored) {}
                        Solid base = findBaseSolidFor(idd.x, idd.y, out.solids);
                        // Fallback hierarchy for base solid:
                        // 1. Nearest existing wall/solid under X.
                        // 2. Any existing wall (first).
                        // 3. Dummy single-cell wall directly beneath button so constructor logic does not NPE.
                        if (base == null) {
                            if (out.walls.size > 0) {
                                base = out.walls.first();
                            } else {
                                // Create a lightweight dummy wall one cell below intended button Y
                                float cellX = (float)Math.floor(idd.x / 32f) * 32f;
                                float cellY = (float)Math.floor(idd.y / 32f) * 32f - 32f; // one cell below
                                Wall dummy = new Wall(cellX, cellY, 1, 1);
                                base = dummy; // do not add to out.solids to avoid unintended collisions
                            }
                        }
                        Button b = new Button(idd.x, base, targets, col);
                        out.interactables.add(b);
                        out.solids.add(b);
                    }
                    case "lever" -> {
                        // Support multiple door ids comma-separated
                        Array<Door> targets = new Array<>();
                        if (idd.targetId != null && !idd.targetId.isEmpty()) {
                            String[] parts = idd.targetId.split(",");
                            for (String p : parts) {
                                Door dd = out.doorMap.get(p.trim());
                                if (dd != null) targets.add(dd);
                            }
                        }
                        boolean horizontal = false;
                        try { if (idd.orientation != null) horizontal = "HORIZONTAL".equalsIgnoreCase(idd.orientation); } catch (Exception ignored) {}
                        float lx = idd.x;
                        float ly = idd.y;
                        // If missing Y, place on top of nearest base solid
                        if (ly == 0f) {
                            Solid base = findBaseSolidFor(idd.x, idd.y, out.solids);
                            if (base != null) ly = base.getBounds().y + base.getBounds().height;
                        }
                        Lever l = new Lever(lx, ly, 16, 36, horizontal, null);
                        for (Door dd : targets) l.setTarget(dd);
                        out.interactables.add(l);
                    }
                    default -> {}
                }
            }
        }

        // Boxes
        if (state.boxes != null) {
            for (LevelIO.LevelState.BoxData bd : state.boxes) {
                Box box = new Box(bd.x, bd.y, out.solids);
                out.interactables.add(box);
            }
        }
        // Orbs
        if (state.orbs != null) {
            for (LevelIO.LevelState.OrbData od : state.orbs) {
                Orb orb = new Orb(od.x, od.y, out.solids);
                // Respect saved bounce-enabled flag (default true if absent)
                try { orb.setBounceEnabled(od.bouncy); } catch (Throwable ignored) {}
                out.interactables.add(orb);
            }
        }

        // Launchpads
        if (state.launchpads != null) {
            for (LevelIO.LevelState.LaunchpadData lpd : state.launchpads) {
                com.jjmc.chromashift.environment.Launchpad.LaunchDirection dir = 
                    com.jjmc.chromashift.environment.Launchpad.LaunchDirection.UP;
                try {
                    if (lpd.direction != null) {
                        dir = com.jjmc.chromashift.environment.Launchpad.LaunchDirection.valueOf(
                            lpd.direction.toUpperCase()
                        );
                    }
                } catch (Exception ignored) {}
                
                float speed = (lpd.speed > 0f) ? lpd.speed : 600f;
                com.jjmc.chromashift.environment.Launchpad launchpad = 
                    new com.jjmc.chromashift.environment.Launchpad(lpd.x, lpd.y, dir, speed);
                out.interactables.add(launchpad);
                out.solids.add(launchpad);
            }
        }

        // Boss
        if (state.boss != null) {
            out.boss = new BossInstance();
            out.boss.setPosition(state.boss.x, state.boss.y);
            out.boss.setEnvironment(out.solids, out.walls);
        }

        // Spawn
        if (state.spawn != null) {
            out.spawnX = state.spawn.x;
            out.spawnY = state.spawn.y;
        }

        return out;
    }

    // (Removed anchoring helpers; doors no longer anchor to walls.)

    private static Solid findBaseSolidFor(float x, float y, Array<Solid> solids) {
        if (solids == null || solids.size == 0) return null;
        Solid best = null;
        float bestTopY = -Float.MAX_VALUE;
        for (Solid s : solids) {
            Rectangle r = s.getBounds();
            if (r == null) continue;
            boolean withinX = x >= r.x - 1e-3f && x <= r.x + r.width + 1e-3f;
            float top = r.y + r.height;
            // choose the solid with top just below (or at) y, or simply the highest under x if y is 0
            boolean below = (y == 0f) ? true : top <= y + 1e-3f;
            if (withinX && below && top > bestTopY) {
                bestTopY = top;
                best = s;
            }
        }
        return best;
    }
}

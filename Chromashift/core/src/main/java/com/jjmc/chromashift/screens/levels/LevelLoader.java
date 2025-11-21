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
        public final Array<com.jjmc.chromashift.environment.collectible.Collectible> collectibles = new Array<>();
        // Shop position data (shops need Player and Stage, so they're instantiated by screens)
        public final Array<LevelIO.LevelState.ShopData> shopDataList = new Array<>();
        // Tentacle instances
        public final Array<com.jjmc.chromashift.environment.enemy.Tentacle> tentacles = new Array<>();
        public BossInstance boss; // optional
        public float spawnX;
        public float spawnY;
        // Door id -> instance for linking
        public final Map<String, Door> doorMap = new HashMap<>();
        // Button/Lever maps for control wiring
        public final Map<String, Button> buttonMap = new HashMap<>();
        public final Map<String, Lever> leverMap = new HashMap<>();
        // Laser id -> runtime Laser or LaserRay instance (as Interactable)
        public final Map<String, Interactable> laserMap = new HashMap<>();
        // Mirror id -> instance for linking
        public final Map<String, Mirror> mirrorMap = new HashMap<>();
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

        // Mirrors (create early so Buttons/Levers can link to them)
        if (state.mirrors != null) {
            for (LevelIO.LevelState.MirrorData md : state.mirrors) {
                com.jjmc.chromashift.environment.interactable.Mirror mirror =
                        new com.jjmc.chromashift.environment.interactable.Mirror(md.x, md.y, md.width, md.height);
                try { mirror.setAngleDegrees(md.angleDeg); } catch (Throwable ignored) {}
                out.interactables.add(mirror);
                if (md.id != null && !md.id.isEmpty()) out.mirrorMap.put(md.id, mirror);
            }
        }

        // Other interactables (Buttons, Levers) possibly linking to doors, lasers, mirrors
        if (state.interactables != null) {
            for (LevelIO.LevelState.InteractableData idd : state.interactables) {
                if (idd == null || idd.type == null) continue;
                String t = idd.type.trim().toLowerCase();
                if ("door".equals(t)) continue; // already handled

                switch (t) {
                    case "button" -> {
                        // Support multiple target ids comma-separated (doors, lasers, mirrors)
                        Array<Door> targets = new Array<>();
                        Array<Interactable> otherTargets = new Array<>();
                        if (idd.targetId != null && !idd.targetId.isEmpty()) {
                            String[] parts = idd.targetId.split(",");
                            for (String p : parts) {
                                String key = p.trim();
                                Door dd = out.doorMap.get(key);
                                if (dd != null) targets.add(dd);
                                else {
                                    Interactable li = out.laserMap.get(key);
                                    if (li != null) otherTargets.add(li);
                                    else {
                                        Mirror mi = out.mirrorMap.get(key);
                                        if (mi != null) otherTargets.add(mi);
                                    }
                                }
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
                        // Map button id to runtime instance if id present
                        if (idd.id != null && !idd.id.isEmpty()) {
                            out.buttonMap.put(idd.id, b);
                        }
                        // Attach any non-door interactable targets (e.g., LaserRay emitters)
                        if (otherTargets.size > 0) {
                            for (Interactable it : otherTargets) b.addLinkedInteractable(it);
                        }
                    }
                    case "lever" -> {
                        // Support multiple target ids comma-separated (doors, lasers, mirrors)
                        Array<Door> targets = new Array<>();
                        Array<Interactable> otherTargets = new Array<>();
                        if (idd.targetId != null && !idd.targetId.isEmpty()) {
                            String[] parts = idd.targetId.split(",");
                            for (String p : parts) {
                                String key = p.trim();
                                Door dd = out.doorMap.get(key);
                                if (dd != null) targets.add(dd);
                                else {
                                    Interactable li = out.laserMap.get(key);
                                    if (li != null) otherTargets.add(li);
                                    else {
                                        Mirror mi = out.mirrorMap.get(key);
                                        if (mi != null) otherTargets.add(mi);
                                    }
                                }
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
                        for (Interactable it : otherTargets) l.setTarget(it);
                        out.interactables.add(l);
                        // Map lever id to runtime instance if id present
                        if (idd.id != null && !idd.id.isEmpty()) {
                            out.leverMap.put(idd.id, l);
                        }
                    }
                    default -> {}
                }
            }
        }

        // Boxes
        if (state.boxes != null) {
            for (LevelIO.LevelState.BoxData bd : state.boxes) {
                Box box = new Box(bd.x, bd.y, out.solids);
                // apply saved color if present
                if (bd.color != null) {
                    com.badlogic.gdx.graphics.Color base = com.badlogic.gdx.graphics.Color.CYAN;
                    switch (bd.color.toUpperCase()) {
                        case "RED" -> base = com.badlogic.gdx.graphics.Color.RED;
                        case "BLUE" -> base = com.badlogic.gdx.graphics.Color.BLUE;
                        case "GREEN" -> base = com.badlogic.gdx.graphics.Color.GREEN;
                        case "YELLOW" -> base = com.badlogic.gdx.graphics.Color.YELLOW;
                        case "PURPLE" -> base = com.badlogic.gdx.graphics.Color.PURPLE;
                        default -> base = com.badlogic.gdx.graphics.Color.CYAN;
                    }
                    try { box.setColor(base); } catch (Throwable ignored) {}
                }
                out.interactables.add(box);
                // Apply respawn area if dimensions present
                float aw = (bd.areaW > 0f) ? bd.areaW : 1600f;
                float ah = (bd.areaH > 0f) ? bd.areaH : 1200f;
                try { box.setRespawnArea(new com.badlogic.gdx.math.Rectangle(bd.x - aw/2f, bd.y - ah/2f, aw, ah)); } catch (Throwable ignored) {}
            }
        }
        // Orbs
        if (state.orbs != null) {
            for (LevelIO.LevelState.OrbData od : state.orbs) {
                Orb orb = new Orb(od.x, od.y, out.solids);
                // Respect saved bounce-enabled flag (default true if absent)
                try { orb.setBounceEnabled(od.bouncy); } catch (Throwable ignored) {}
                // Apply respawn area
                float aw = (od.areaW > 0f) ? od.areaW : 1600f;
                float ah = (od.areaH > 0f) ? od.areaH : 1200f;
                try { orb.setRespawnArea(new com.badlogic.gdx.math.Rectangle(od.x - aw/2f, od.y - ah/2f, aw, ah)); } catch (Throwable ignored) {}
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

        // Lasers (create first; we'll wire references after all interactables are collected)
        Array<com.jjmc.chromashift.environment.interactable.Laser> lasersTemp = new Array<>();
        if (state.lasers != null) {
            for (LevelIO.LevelState.LaserData ld : state.lasers) {
                if (ld.rotating) {
                    // Create a player-rotatable LaserRay interactable at bottom-left (bounds 32x32)
                    com.jjmc.chromashift.environment.interactable.LaserRay lray =
                        new com.jjmc.chromashift.environment.interactable.LaserRay(ld.x, ld.y, true);
                    lray.setRotation(ld.rotation);
                    lray.setMaxBounces(ld.maxBounces);
                    out.interactables.add(lray);
                    if (ld.id != null && !ld.id.isEmpty()) out.laserMap.put(ld.id, lray);
                    // We still use lasersTemp list to later wire mirrors/glasses/solids uniformly
                    // by adapting common setter names on LaserRay as well.
                    // Wrap via interface type compatibility by overloading list type if needed.
                } else {
                    com.jjmc.chromashift.environment.interactable.Laser laser =
                            new com.jjmc.chromashift.environment.interactable.Laser(ld.x, ld.y);
                    laser.setRotation(ld.rotation);
                    laser.setMaxBounces(ld.maxBounces);
                    out.interactables.add(laser);
                    if (ld.id != null && !ld.id.isEmpty()) out.laserMap.put(ld.id, laser);
                    lasersTemp.add(laser);
                }
            }
        }

        

        // Glasses
        if (state.glasses != null) {
            for (LevelIO.LevelState.GlassData gd : state.glasses) {
                com.badlogic.gdx.graphics.Color base = com.badlogic.gdx.graphics.Color.CYAN;
                if (gd.color != null) {
                    switch (gd.color.toUpperCase()) {
                        case "RED" -> base = com.badlogic.gdx.graphics.Color.RED;
                        case "BLUE" -> base = com.badlogic.gdx.graphics.Color.BLUE;
                        case "GREEN" -> base = com.badlogic.gdx.graphics.Color.GREEN;
                        case "YELLOW" -> base = com.badlogic.gdx.graphics.Color.YELLOW;
                        case "PURPLE" -> base = com.badlogic.gdx.graphics.Color.PURPLE;
                        default -> base = com.badlogic.gdx.graphics.Color.CYAN;
                    }
                }
                com.jjmc.chromashift.environment.interactable.Glass glass =
                        new com.jjmc.chromashift.environment.interactable.Glass(
                                gd.x, gd.y, gd.width, gd.height,
                                base, true, 1f, gd.rainbow
                        );
                glass.setSpeed(gd.speed);
                out.interactables.add(glass);
            }
        }

        // Wire mirrors, glasses, and solids into lasers, and wire control inputs
        {
            java.util.ArrayList<com.jjmc.chromashift.environment.interactable.Mirror> mlist = new java.util.ArrayList<>();
            java.util.ArrayList<com.jjmc.chromashift.environment.interactable.Glass> glist = new java.util.ArrayList<>();
            java.util.ArrayList<com.jjmc.chromashift.environment.Solid> slist = new java.util.ArrayList<>();
            // collect current mirrors and glasses from interactables
            for (int i = 0; i < out.interactables.size; i++) {
                Interactable it = out.interactables.get(i);
                if (it instanceof com.jjmc.chromashift.environment.interactable.Mirror mi) mlist.add(mi);
                if (it instanceof com.jjmc.chromashift.environment.interactable.Glass gi) glist.add(gi);
            }
            for (int i = 0; i < out.solids.size; i++) {
                slist.add(out.solids.get(i));
            }
            // Also include any Box instances from interactables so lasers can consider them
            // as blocking solids (boxes are pickable interactables but may still block beams
            // when their color matches the beam). We avoid adding other interactables.
            for (int i = 0; i < out.interactables.size; i++) {
                Interactable it = out.interactables.get(i);
                if (it instanceof com.jjmc.chromashift.environment.interactable.Box bx) {
                    slist.add(bx);
                }
            }
            // Wire for Lasers in lasersTemp (mirrors/glasses/solids only)
            for (int i = 0; i < lasersTemp.size; i++) {
                com.jjmc.chromashift.environment.interactable.Laser L = lasersTemp.get(i);
                L.setMirrors(mlist);
                L.setGlasses(glist);
                L.setSolids(slist);
            }
            // Also wire any LaserRay interactables present in out.interactables
            for (int i = 0; i < out.interactables.size; i++) {
                Interactable it = out.interactables.get(i);
                if (it instanceof com.jjmc.chromashift.environment.interactable.LaserRay lr) {
                    lr.setMirrors(mlist);
                    lr.setGlasses(glist);
                    lr.setSolids(slist);
                }
            }
        }

        // Diamonds (collectibles)
        if (state.diamonds != null) {
            for (LevelIO.LevelState.DiamondData dd : state.diamonds) {
                com.jjmc.chromashift.environment.collectible.Diamond diamond = 
                    new com.jjmc.chromashift.environment.collectible.Diamond(dd.x, dd.y);
                out.collectibles.add(diamond);
            }
        }

        // Shops (interactables) - Store data for screen-level instantiation
        // Shop requires Player and Stage references, so screens must create them
        if (state.shops != null) {
            for (LevelIO.LevelState.ShopData sd : state.shops) {
                out.shopDataList.add(sd);
            }
        }

        // Tentacles
        if (state.tentacles != null) {
            for (LevelIO.LevelState.TentacleData td : state.tentacles) {
                com.jjmc.chromashift.environment.enemy.Tentacle tentacle = 
                    new com.jjmc.chromashift.environment.enemy.Tentacle(td.x, td.y, td.segments);
                out.tentacles.add(tentacle);
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

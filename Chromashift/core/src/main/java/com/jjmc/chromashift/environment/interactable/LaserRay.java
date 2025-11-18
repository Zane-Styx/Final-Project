package com.jjmc.chromashift.environment.interactable;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jjmc.chromashift.Assets;
import com.jjmc.chromashift.environment.Solid;
import java.util.ArrayList;

public class LaserRay implements Interactable {
    private static final String TEX_PATH = "environment/laser/laser.png"; // default faces right

    public Vector2 position;
    private float rotation; // degrees
    private float maxLength = 1000000f;
    private int maxBounces = 8; // limit number of reflections
    // per-segment colors recorded by last cast()
    private ArrayList<Color> segmentColors = null;

    // Optional emitter bounds to allow player interaction (used when placed as object)
    private Rectangle bounds = null;
    private boolean playerNearby = false;
    private float rotateStep = 90f;
    private ArrayList<Mirror> mirrors = new ArrayList<>();
    private ArrayList<Glass> glasses = new ArrayList<>();
    private ArrayList<Solid> solids = new ArrayList<>();
    private ArrayList<Vector2> cachedPoints = new ArrayList<>();

    public LaserRay(float x, float y) {
        this.position = new Vector2(x, y);
        this.rotation = 0f;
    }

    // Constructor for interactable emitter: x,y are bottom-left; size fixed 32x32
    public LaserRay(float x, float y, boolean asEmitter) {
        if (asEmitter) {
            this.bounds = new Rectangle(x, y, 32f, 32f);
            this.position = new Vector2(x + 16f, y + 16f);
        } else {
            this.position = new Vector2(x, y);
        }
        this.rotation = 0f;
    }

    public void setMaxBounces(int max) {
        this.maxBounces = Math.max(0, max);
    }

    public int getMaxBounces() {
        return this.maxBounces;
    }

    public void setRotation(float degrees) {
        rotation = degrees % 360f;
    }

    public float getRotation() { return rotation; }

    /**
     * Cast laser with bounce against mirrors, transmission through glass, and stop on blocking solids.
     * Returns the list of points for the polyline of the ray and records per-segment colors internally.
     */
    public ArrayList<Vector2> cast(ArrayList<Mirror> mirrors, ArrayList<Glass> glasses, ArrayList<Solid> solids, float time) {
        ArrayList<Vector2> points = new ArrayList<>();
        // ensure segmentColors storage
        if (segmentColors == null) segmentColors = new ArrayList<>();
        segmentColors.clear();

        Vector2 origin = new Vector2(position);
        points.add(origin.cpy());

        Vector2 dir = new Vector2((float)Math.cos(Math.toRadians(rotation)),
                                  (float)Math.sin(Math.toRadians(rotation))).nor();

        // current color of the beam
        Color currentColor = new Color(Color.RED);

        int bounces = 0;
        float remainingLength = maxLength;
        final float EPS_SKIP = 1e-4f; // small distance to skip self-intersections

        while (remainingLength > 0.01f) {
            Vector2 closestHit = null;
            Mirror hitMirror = null;
            Glass hitGlass = null;
            Solid hitSolid = null;
            float closestDist = Float.MAX_VALUE;

            // Find closest mirror intersection
            if (mirrors != null) for (Mirror m : mirrors) {
                Vector2 hit = intersectLine(origin, dir, m.start, m.end);
                if (hit != null) {
                    float dist = hit.dst2(origin);
                    if (dist < (EPS_SKIP * EPS_SKIP)) continue;
                    if (dist < closestDist) {
                        closestDist = dist;
                        closestHit = hit;
                        hitMirror = m;
                        hitGlass = null;
                    }
                }
            }

            // Find closest glass intersection (transmissive). It competes with mirrors.
            if (glasses != null) for (Glass g : glasses) {
                Vector2 hit = intersectLine(origin, dir, g.start, g.end);
                if (hit != null) {
                    float dist = hit.dst2(origin);
                    if (dist < (EPS_SKIP * EPS_SKIP)) continue;
                    if (dist < closestDist) {
                        closestDist = dist;
                        closestHit = hit;
                        hitMirror = null;
                        hitGlass = g;
                        hitSolid = null;
                    }
                }
            }

            // Find closest solid intersection (stop the beam). Use the rectangle edges.
            if (solids != null) for (Solid s : solids) {
                if (s == null || !s.isBlocking()) continue;
                if (s.getCollisionBounds() == null) continue;
                com.badlogic.gdx.math.Rectangle r = s.getCollisionBounds();
                // define rectangle corners
                Vector2 a = new Vector2(r.x, r.y);
                Vector2 b = new Vector2(r.x + r.width, r.y);
                Vector2 c = new Vector2(r.x + r.width, r.y + r.height);
                Vector2 d = new Vector2(r.x, r.y + r.height);
                Vector2[] segA = new Vector2[]{a, b, c, d};
                Vector2[] segB = new Vector2[]{b, c, d, a};
                for (int si = 0; si < 4; si++) {
                    Vector2 hit = intersectLine(origin, dir, segA[si], segB[si]);
                    if (hit != null) {
                        float dist = hit.dst2(origin);
                        if (dist < (EPS_SKIP * EPS_SKIP)) continue;
                        // If the solid is a Box with a color set, only block the beam when
                        // the box color matches the current beam color. If the box has no
                        // color (null) behave as before and block.
                        boolean acceptAsBlocking = true;
                        if (s instanceof com.jjmc.chromashift.environment.interactable.Box) {
                            com.jjmc.chromashift.environment.interactable.Box bx = (com.jjmc.chromashift.environment.interactable.Box) s;
                            com.badlogic.gdx.graphics.Color bcol = bx.getColor();
                            if (bcol != null) {
                                // compare colors with a small epsilon
                                float eps = 0.03f;
                                if (Math.abs(bcol.r - currentColor.r) > eps || Math.abs(bcol.g - currentColor.g) > eps || Math.abs(bcol.b - currentColor.b) > eps) {
                                    acceptAsBlocking = false; // colors differ -> beam passes through
                                }
                            }
                        }
                        if (!acceptAsBlocking) {
                            // ignore this solid (beam passes through)
                            continue;
                        }

                        if (dist < closestDist) {
                            closestDist = dist;
                            closestHit = hit;
                            hitMirror = null;
                            hitGlass = null;
                            hitSolid = s;
                        }
                    }
                }
            }

            if (closestHit != null) {
                float hitDist = (float)Math.sqrt(closestDist);
                // if the hit is beyond remaining length, draw final segment and exit
                if (hitDist > remainingLength) {
                    points.add(new Vector2(origin).add(new Vector2(dir).scl(remainingLength)));
                    // final segment uses currentColor
                    segmentColors.add(new Color(currentColor));
                    break;
                }

                // add the hit point and record the color for the segment we just traced
                points.add(closestHit.cpy());
                segmentColors.add(new Color(currentColor));

                if (hitSolid != null) {
                    // Stop the beam on solid
                    break;
                } else if (hitMirror != null) {
                    // reflect direction
                    Vector2 normal = hitMirror.getNormal();
                    float dot = dir.x * normal.x + dir.y * normal.y;
                    dir.x = dir.x - 2f * dot * normal.x;
                    dir.y = dir.y - 2f * dot * normal.y;
                    dir.nor();

                    remainingLength -= hitDist;

                    // move origin slightly along the reflected direction to avoid hitting the same mirror due to numerical precision
                    origin.set(closestHit.x + dir.x * EPS_SKIP * 2f, closestHit.y + dir.y * EPS_SKIP * 2f);

                    bounces++;
                    if (bounces > maxBounces) break;
                } else if (hitGlass != null) {
                    // transmit through glass: optionally tint the laser if the glass requests it
                    if (hitGlass.doesTintLaser()) {
                        // use the dynamic tint at this point and time
                        Color gcol = hitGlass.getTintAt(closestHit, time);
                        float blend = hitGlass.getTintStrength();
                        // lerp the currentColor toward gcol by blend
                        currentColor.lerp(gcol, Math.max(0f, Math.min(1f, blend)));
                        currentColor.a = 1f;
                    }

                    remainingLength -= hitDist;

                    // move origin slightly forward along the same direction to avoid re-hitting the same glass edge
                    origin.set(closestHit.x + dir.x * EPS_SKIP * 2f, closestHit.y + dir.y * EPS_SKIP * 2f);
                    // do not increment bounce count for transmission
                }
            } else {
                // no hit, go full remaining length
                points.add(new Vector2(origin).add(new Vector2(dir).scl(remainingLength)));
                segmentColors.add(new Color(currentColor));
                break;
            }
        }

        return points;
    }

    /** Draw laser points */
    public void draw(ShapeRenderer sr, ArrayList<Vector2> points) {
        if (points == null || points.size() < 2) return;
        // if segmentColors exists and matches segments, use it; otherwise fallback to red
        boolean useColors = (segmentColors != null && segmentColors.size() == points.size() - 1);
        for (int i = 0; i < points.size() - 1; i++) {
            if (useColors) sr.setColor(segmentColors.get(i));
            else sr.setColor(Color.RED);
            sr.rectLine(points.get(i), points.get(i+1), 3f);
        }
    }

    /** Returns the last cached points computed by the most recent cast(). */
    public ArrayList<Vector2> getCachedPoints() {
        return cachedPoints;
    }

    // --- Interactable implementation ---
    @Override
    public Rectangle getBounds() {
        // If not used as an emitter, create a small bounds around the position center
        if (bounds == null) {
            return new Rectangle(position.x - 16f, position.y - 16f, 32f, 32f);
        }
        return bounds;
    }

    @Override
    public void update(float delta) {
        // Update cached beam if we have environment refs
        cachedPoints = cast(mirrors, glasses, solids, System.currentTimeMillis() / 1000f);
    }

    @Override
    public void render(SpriteBatch batch) {
        // Draw beam using SpriteBatch so it's visible without debug mode
        if (cachedPoints != null && cachedPoints.size() >= 2) {
            ensurePixel(batch);
            java.util.ArrayList<Color> cols = segmentColors;
            Color prev = batch.getColor();
            float pr = prev.r, pg = prev.g, pb = prev.b, pa = prev.a;
            try {
                batch.flush();
                for (int i = 0; i < cachedPoints.size() - 1; i++) {
                    Vector2 a = cachedPoints.get(i);
                    Vector2 b = cachedPoints.get(i + 1);
                    float dx = b.x - a.x;
                    float dy = b.y - a.y;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                    Color sc = (cols != null && i < cols.size()) ? cols.get(i) : Color.RED;
                    // beam thickness: outer 6px, inner 4px (inner is semi-transparent)
                    float outerTh = 6f;
                    float innerTh = 4f;
                    // center-based draw
                    float cx = (a.x + b.x) * 0.5f;
                    float cy = (a.y + b.y) * 0.5f;
                    float x = cx - len / 2f;
                    float outerY = cy - outerTh / 2f;
                    // outer colored border (solid)
                    batch.setColor(sc.r, sc.g, sc.b, 1f);
                    batch.draw(PIXEL, x, outerY, len / 2f, outerTh / 2f, len, outerTh, 1f, 1f, angle, 0, 0, 1, 1, false, false);
                    // inner translucent fill
                    float innerY = cy - innerTh / 2f;
                    float innerAlpha = 0.25f; // slightly transparent inner fill
                    batch.setColor(sc.r, sc.g, sc.b, innerAlpha);
                    batch.draw(PIXEL, x, innerY, len / 2f, innerTh / 2f, len, innerTh, 1f, 1f, angle, 0, 0, 1, 1, false, false);
                }
                batch.flush();
            } finally {
                batch.setColor(pr, pg, pb, pa);
            }

            // Draw the laser base using texture, rotated by rotation (default faces right)
        if (Assets.manager.isLoaded(TEX_PATH, Texture.class)) {
            Texture tex = Assets.manager.get(TEX_PATH, Texture.class);
            float x = bounds.x;
            float y = bounds.y;
            float w = bounds.width;
            float h = bounds.height;
            // draw centered rotation
            batch.draw(tex,
                    x, y,
                    w / 2f, h / 2f, // origin
                    w, h,
                    1f, 1f,
                    rotation,
                    0, 0,
                    tex.getWidth(), tex.getHeight(),
                    false, false);
        }
        }
    }

    // Provide access to last computed segment colors for external renderers
    public java.util.ArrayList<Color> getLastSegmentColors() {
        return segmentColors;
    }

    // PIXEL for batch drawing (lazy)
    private static com.badlogic.gdx.graphics.Texture PIXEL;
    private static void ensurePixel(SpriteBatch batch) {
        if (PIXEL == null) {
            com.badlogic.gdx.graphics.Pixmap pm = new com.badlogic.gdx.graphics.Pixmap(1, 1, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            pm.setColor(com.badlogic.gdx.graphics.Color.WHITE);
            pm.fill();
            PIXEL = new com.badlogic.gdx.graphics.Texture(pm);
            pm.dispose();
        }
    }

    @Override
    public void debugDraw(ShapeRenderer shape) {
        // Draw emitter bounds for visualization
        Rectangle b = getBounds();
        shape.setColor(Color.GRAY);
        shape.rect(b.x, b.y, b.width, b.height);
        // Draw beam
        if (cachedPoints != null && !cachedPoints.isEmpty()) {
            draw(shape, cachedPoints);
        }
    }

    @Override
    public void interact() {
        // Rotate by rotateStep on player interaction
        setRotation(((Math.round(rotation / 90f) * 90f) + rotateStep) % 360f);
    }

    @Override
    public void checkInteraction(Rectangle playerHitbox) {
        playerNearby = playerHitbox != null && playerHitbox.overlaps(getBounds());
    }

    @Override
    public boolean canInteract() {
        return playerNearby;
    }

    // Environment wiring
    public void setMirrors(java.util.ArrayList<Mirror> ms) { this.mirrors = (ms != null) ? ms : new ArrayList<>(); }
    public void setGlasses(java.util.ArrayList<Glass> gs) { this.glasses = (gs != null) ? gs : new ArrayList<>(); }
    public void setSolids(java.util.ArrayList<Solid> ss) { this.solids = (ss != null) ? ss : new ArrayList<>(); }
    public void setRotationStep(float step) { this.rotateStep = (step == 0f ? 90f : step); }

    /** 2D line intersection helper (ray vs segment) */
    private Vector2 intersectLine(Vector2 rayOrigin, Vector2 rayDir, Vector2 segA, Vector2 segB) {
        // Solve r_o + t*r = s_a + u*s  where r is rayDir and s = segB-segA
        float rOx = rayOrigin.x;
        float rOy = rayOrigin.y;
        float rDx = rayDir.x;
        float rDy = rayDir.y;

        float sAx = segA.x;
        float sAy = segA.y;
        float sBx = segB.x;
        float sBy = segB.y;

        float sDx = sBx - sAx;
        float sDy = sBy - sAy;

        // 2D cross product
        float denom = rDx * sDy - rDy * sDx; // cross(r, s)
        final float EPS = 1e-6f;
        if (Math.abs(denom) < EPS) {
            // parallel (or nearly parallel) - no reliable intersection
            return null;
        }

        // vector from ray origin to segment start
        float wx = sAx - rOx;
        float wy = sAy - rOy;

        // t = cross(w, s) / denom
        float t = (wx * sDy - wy * sDx) / denom;
        // u = cross(w, r) / denom
        float u = (wx * rDy - wy * rDx) / denom;

        // ray forward (t >= 0) and segment between 0..1 (u in [0,1])
        if (t < 0f) return null;
        if (u < 0f || u > 1f) return null;

        // since rayDir is expected to be normalized in cast(), t is the distance along ray
        return new Vector2(rOx + rDx * t, rOy + rDy * t);
    }
}

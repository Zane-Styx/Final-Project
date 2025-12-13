package com.chromashift.helper;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Rectangle;

public final class VisibilityCuller {
    private static Camera camera;
    private static boolean enabled = true; // gameplay only; editor disables

    private VisibilityCuller() {}

    public static void setCamera(Camera cam) { camera = cam; }
    public static void setEnabled(boolean e) { enabled = e; }
    public static boolean isEnabled() { return enabled; }

    public static boolean isVisible(Rectangle r, float margin) {
        if (!enabled) return true; // culling disabled (e.g., editor)
        if (camera == null || r == null) return true; // visible when no camera set
        float cx = camera.position.x;
        float cy = camera.position.y;
        float hw = camera.viewportWidth * 0.5f;
        float hh = camera.viewportHeight * 0.5f;
        float left = cx - hw - margin;
        float right = cx + hw + margin;
        float bottom = cy - hh - margin;
        float top = cy + hh + margin;
        return r.x + r.width >= left && r.x <= right && r.y + r.height >= bottom && r.y <= top;
    }
}

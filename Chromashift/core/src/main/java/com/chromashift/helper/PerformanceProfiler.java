package com.chromashift.helper;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PerformanceProfiler {
    private static boolean enabled = false;
    private static final Map<String, Long> startTimes = new LinkedHashMap<>();
    private static final Map<String, Long> lastDurations = new LinkedHashMap<>();

    private PerformanceProfiler() {}

    public static void setEnabled(boolean e) { enabled = e; }
    public static boolean isEnabled() { return enabled; }

    public static void start(String label) {
        if (!enabled) return;
        startTimes.put(label, System.nanoTime());
    }

    public static void stop(String label) {
        if (!enabled) return;
        Long t = startTimes.remove(label);
        if (t != null) {
            long dur = System.nanoTime() - t;
            lastDurations.put(label, dur);
        }
    }

    public static Map<String, Long> getLastDurations() { return lastDurations; }

    public static void renderOverlay(SpriteBatch batch, BitmapFont font, float x, float y) {
        if (!enabled) return;
        float dy = 0f;
        for (Map.Entry<String, Long> e : lastDurations.entrySet()) {
            float ms = e.getValue() / 1_000_000f;
            font.draw(batch, e.getKey() + ": " + String.format("%.3f ms", ms), x, y - dy);
            dy += 14f;
        }
    }
}

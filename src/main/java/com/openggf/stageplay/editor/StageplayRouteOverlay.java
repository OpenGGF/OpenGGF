package com.openggf.stageplay.editor;

import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.route.StageplayRoutePoint;
import com.openggf.stageplay.route.StageplayRouteResult;

import java.util.List;
import java.util.Objects;

/**
 * Transient authoring view of observed route samples. This model is deliberately outside the
 * document, runtime renderer and export protocol. Painting reads values only; it never simulates.
 * Both frontends use the same geometry and camera transform, in their own top-left UI units.
 */
public final class StageplayRouteOverlay {
    public static final int GROUND = 0x61E5ED, AIR = 0xFFD077, FAILURE = 0xFF7887;
    public static final int INK = 0x101A2B, WHITE = 0xFFFFFF;

    public interface Painter {
        void line(float x1, float y1, float x2, float y2, int rgb, float width);
    }

    private final StageplayRouteResult result;
    private final boolean applied;
    private final String title;
    private final String reason;

    public StageplayRouteOverlay(StageplayRouteResult result, boolean applied) {
        this.result = Objects.requireNonNull(result);
        if (applied && !result.success()) throw new IllegalArgumentException("Cannot apply a failed route overlay");
        this.applied = applied;
        title = applied ? "Route applied" : result.success() ? "Route proposed" : "Route incomplete";
        reason = switch (result.status()) {
            case VERIFIED -> applied ? "Compare the path during playback." : "Native replay checked. Review, then Apply.";
            case VISUAL_TERRAIN -> "Visual terrain only. Native object AI stays off.";
            case BUDGET_EXCEEDED -> "Search limit reached. Try a closer goal or increase the search budget. Reachability is still unknown.";
            case SEARCH_EXHAUSTED -> "No route found in this search. " + readable(result.message());
            case UNSUPPORTED -> "This route is not supported. " + readable(result.message());
            case CANCELLED -> "Route planning cancelled.";
            case VERIFICATION_FAILED -> "Playback could not confirm this route. " + readable(result.message());
            case STALE_INPUT -> "Scene or assets changed. Plan the route again.";
        };
    }

    public StageplayRouteResult result() { return result; }
    public List<StageplayRoutePoint> path() { return result.path(); }
    public boolean applied() { return applied; }
    public String title() { return title; }
    public String statusText() {
        return result.success() ? title + "." : switch (result.status()) {
            case BUDGET_EXCEEDED -> "Route search limit reached.";
            case CANCELLED -> "Route planning cancelled.";
            case STALE_INPUT -> "Route needs replanning.";
            default -> "Route could not be completed.";
        };
    }

    /** Protocol diagnostics remain intact in the result; chrome presents ordinary words. */
    private static String readable(String message) {
        var matcher = java.util.regex.Pattern.compile("\\b[A-Z][A-Z_]{2,}\\b").matcher(message);
        return matcher.replaceAll(match -> match.group().replace('_', ' ').toLowerCase(java.util.Locale.ROOT));
    }
    public String reason() { return reason; }

    public static float screenX(double world, StageplayTimeline.CameraPose camera,
                                int filmWidth, float left, float width) {
        return left + (float) ((world - camera.x()) * camera.zoom() * width / filmWidth);
    }

    public static float screenY(double world, StageplayTimeline.CameraPose camera,
                                int filmHeight, float top, float height) {
        return top + (float) ((world - camera.y()) * camera.zoom() * height / filmHeight);
    }

    /** The frontend clips to this film rectangle. Widths/markers stay legible at any scene zoom. */
    public void paint(Painter painter, StageplayTimeline.CameraPose camera, int filmWidth, int filmHeight,
                      float left, float top, float width, float height, double actualX, double actualY) {
        var path = path();
        for (int i = 1; i < path.size(); i++) {
            var a = path.get(i - 1); var b = path.get(i);
            // A missing observation is a break, never permission to invent the intervening route.
            if (b.frame() - a.frame() > 1 || b.frame() < a.frame()) continue;
            float ax = screenX(a.x(), camera, filmWidth, left, width);
            float ay = screenY(a.y(), camera, filmHeight, top, height);
            float bx = screenX(b.x(), camera, filmWidth, left, width);
            float by = screenY(b.y(), camera, filmHeight, top, height);
            stroke(painter, ax, ay, bx, by, a.grounded() && b.grounded() ? GROUND : AIR, 2);
            if (a.grounded() && !b.grounded()) {
                stroke(painter, ax - 4, ay + 4, ax, ay - 2, AIR, 2);
                stroke(painter, ax, ay - 2, ax + 4, ay + 4, AIR, 2);
            }
        }
        if (!path.isEmpty()) {
            var start = path.getFirst(); var stop = path.getLast();
            float sx = screenX(start.x(), camera, filmWidth, left, width);
            float sy = screenY(start.y(), camera, filmHeight, top, height);
            stroke(painter, sx - 4, sy - 4, sx + 4, sy - 4, GROUND, 2);
            stroke(painter, sx + 4, sy - 4, sx + 4, sy + 4, GROUND, 2);
            stroke(painter, sx + 4, sy + 4, sx - 4, sy + 4, GROUND, 2);
            stroke(painter, sx - 4, sy + 4, sx - 4, sy - 4, GROUND, 2);
            if (!result.success()) {
                float x = screenX(stop.x(), camera, filmWidth, left, width);
                float y = screenY(stop.y(), camera, filmHeight, top, height);
                stroke(painter, x - 5, y - 5, x + 5, y + 5, FAILURE, 2);
                stroke(painter, x - 5, y + 5, x + 5, y - 5, FAILURE, 2);
            }
        }
        // The goal is a separate marker. Never connect it to an unsuccessful/unfinished path.
        float x = screenX(result.request().targetX(), camera, filmWidth, left, width);
        float y = screenY(result.request().targetY(), camera, filmHeight, top, height);
        int goal = result.success() ? GROUND : FAILURE;
        stroke(painter, x, y - 6, x + 6, y, goal, 2);
        stroke(painter, x + 6, y, x, y + 6, goal, 2);
        stroke(painter, x, y + 6, x - 6, y, goal, 2);
        stroke(painter, x - 6, y, x, y - 6, goal, 2);
        if (applied && Double.isFinite(actualX) && Double.isFinite(actualY)) {
            float ax = screenX(actualX, camera, filmWidth, left, width);
            float ay = screenY(actualY, camera, filmHeight, top, height);
            stroke(painter, ax - 3, ay, ax + 3, ay, WHITE, 2);
            stroke(painter, ax, ay - 3, ax, ay + 3, WHITE, 2);
        }
    }

    private static void stroke(Painter painter, float x1, float y1, float x2, float y2, int rgb, float width) {
        painter.line(x1, y1, x2, y2, INK, width + 2);
        painter.line(x1, y1, x2, y2, rgb, width);
    }
}

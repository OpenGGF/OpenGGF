package com.openggf.level.objects.authored;

import com.openggf.level.objects.ObjectSpawn;
import java.util.HashMap;
import java.util.List;

/** Shared host-key factory and placement validation; independent of each game's numeric object IDs. */
public final class AuthoredGizmos {
    private AuthoredGizmos() { }
    public static final List<String> KEYS = List.of("openggf:camera-zone", "openggf:music-zone",
            "openggf:warp", "openggf:stageplay-trigger");

    public static boolean handlesKey(String key) { return key != null && KEYS.contains(key); }
    public static AuthoredGizmoInstance create(ObjectSpawn spawn) { return new AuthoredGizmoInstance(spawn); }

    /** Eager host admission makes regions work even when their origin is outside native streaming windows. */
    public static void install(List<ObjectSpawn> objects, java.util.function.Consumer<com.openggf.level.objects.ObjectInstance> admit) {
        if (objects.stream().noneMatch(spawn -> handlesKey(spawn.objectKey()))) return;
        validatePlacements(objects);
        objects.stream().filter(spawn -> handlesKey(spawn.objectKey())).map(AuthoredGizmos::create).forEach(admit);
    }

    /** Complete placement-table validation, invoked before preview/export/runtime admission. */
    public static void validatePlacements(List<ObjectSpawn> objects) {
        var links = new HashMap<String, java.util.ArrayList<ObjectSpawn>>();
        for (ObjectSpawn spawn : objects) {
            if (handlesKey(spawn.objectKey()) && spawn.authoredProperties() == null)
                throw new IllegalArgumentException("Authored gizmo properties are required: " + spawn.objectKey());
            if (spawn.authoredProperties() instanceof AuthoredObjectProperties.Warp warp) {
                if (spawn.layoutIndex() < 0) throw new IllegalArgumentException("Warp endpoints require stable placement IDs");
                links.computeIfAbsent(warp.link(), ignored -> new java.util.ArrayList<>()).add(spawn);
            }
        }
        links.forEach((link, pair) -> {
            if (pair.size() != 2) throw new IllegalArgumentException("Warp link '" + link + "' needs exactly two endpoints");
            ObjectSpawn first = pair.get(0), second = pair.get(1);
            var a = (AuthoredObjectProperties.Warp) first.authoredProperties();
            var b = (AuthoredObjectProperties.Warp) second.authoredProperties();
            if (a.twoWay() != b.twoWay()) throw new IllegalArgumentException("Warp endpoints disagree about two-way mode: " + link);
            if (first.layoutIndex() == second.layoutIndex() || first.x() == second.x() && first.y() == second.y())
                throw new IllegalArgumentException("Warp endpoints must have distinct positions and placement IDs: " + link);
            requireExit(first, a); requireExit(second, b);
        });
    }

    /** Region geometry and transport arrivals must fit the authored canvas, independently of native encoding bounds. */
    public static void validateBounds(List<ObjectSpawn> objects, int worldWidth, int worldHeight) {
        for (ObjectSpawn spawn : objects) {
            var properties = spawn.authoredProperties();
            if (properties == null) continue; // Native stock placement behavior is unchanged.
            if (worldWidth < 1 || worldHeight < 1 || spawn.x() < 0 || spawn.y() < 0
                    || (long) spawn.x() + properties.width() > worldWidth
                    || (long) spawn.y() + properties.height() > worldHeight)
                throw new IllegalArgumentException("Authored gizmo region must fit inside the level canvas: " + spawn.objectKey());
            if (properties instanceof AuthoredObjectProperties.Warp warp && exitX(spawn, warp) >= worldWidth)
                throw new IllegalArgumentException("Warp exit needs a free pixel inside the level canvas: " + warp.link());
        }
    }

    static int exitX(ObjectSpawn destination, AuthoredObjectProperties.Warp properties) {
        // Arrive just beyond the receiver's right edge, never inside its trigger. This remains
        // safe when the destination object has not yet been streamed in and in either update order.
        return destination.x() + properties.width();
    }

    private static void requireExit(ObjectSpawn spawn, AuthoredObjectProperties.Warp properties) {
        if (exitX(spawn, properties) > 0xffff)
            throw new IllegalArgumentException("Warp exit needs a free world pixel to its right");
    }
}

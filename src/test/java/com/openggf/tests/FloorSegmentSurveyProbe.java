package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.level.LevelManager;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Opt-in diagnostic: lists each act's longest continuous, pit-free floor paths, the
 * candidates for a walled arena or any stretch a mod needs to be walkable end to end.
 *
 * <p>Every 8px column of the act is scanned top to bottom for floor surfaces (the engine's
 * floor sensor, foreground path); surfaces in neighbouring columns within 24px of height are
 * linked into one path, so a path never crosses a pit, a wall or a step taller than 24px.
 * Loops and one-way tops count as floor. Paths are printed longest first with their x span
 * and height range. A path is a candidate, not proof: confirm it in a capture, since solid
 * objects (bridges, platforms) are not terrain and a steep but linked slope may still stop
 * a player.
 *
 * <p>Inputs (system properties): {@code openggf.floorsurvey.out} (output file; the probe is
 * skipped when unset), {@code openggf.floorsurvey.game} ({@code s1}, {@code s2}, {@code s3k};
 * default {@code s2}), {@code openggf.floorsurvey.acts} (comma-separated 0-based
 * {@code zone:act} pairs; default {@code 0:0}), {@code openggf.floorsurvey.top} (paths per
 * act, default 8). The usual ROM path properties select the ROM.
 *
 * <pre>
 * mvn -Dmse=off -Dsonic2.rom.path=/abs/s2.gen -Dtest=FloorSegmentSurveyProbe \
 *     -Dopenggf.floorsurvey.out=/abs/task-dir/floors.txt -Dopenggf.floorsurvey.acts=0:0,1:1 test
 * </pre>
 *
 * <p>Originating task: Sonic Survivors arena selection (2026-10-06). Comparison-only: it
 * reads loaded levels and never changes engine state.
 */
@RequiresRom(SonicGame.SONIC_2)
class FloorSegmentSurveyProbe {

    @Test
    void surveyFloorPaths() throws Exception {
        String out = System.getProperty("openggf.floorsurvey.out");
        Assumptions.assumeTrue(out != null && !out.isBlank(),
                "set -Dopenggf.floorsurvey.out=<output file> to write a floor survey");
        SonicGame game = switch (System.getProperty("openggf.floorsurvey.game", "s2")) {
            case "s1" -> SonicGame.SONIC_1;
            case "s3k" -> SonicGame.SONIC_3K;
            default -> SonicGame.SONIC_2;
        };
        int top = Integer.decode(System.getProperty("openggf.floorsurvey.top", "8"));
        var sb = new StringBuilder();
        for (String pair : System.getProperty("openggf.floorsurvey.acts", "0:0").split(",")) {
            String[] za = pair.trim().split(":");
            int zone = Integer.decode(za[0]), act = Integer.decode(za[1]);
            SharedLevel shared = SharedLevel.load(game, zone, act);
            try {
                HeadlessTestFixture.builder().withSharedLevel(shared).build();
                var level = GameServices.level().getCurrentLevel();
                sb.append("== zone ").append(zone).append(" act ").append(act)
                        .append(" x ").append(level.getMinX()).append('-').append(level.getMaxX())
                        .append(" y ").append(level.getMinY()).append('-').append(level.getMaxY()).append('\n');
                sb.append(paths(GameServices.level(), level.getMinX(), level.getMaxX() + 320,
                        level.getMinY(), level.getMaxY() + 224, top));
            } finally {
                shared.dispose();
            }
        }
        Files.writeString(Path.of(out), sb.toString());
        org.junit.jupiter.api.Assertions.assertTrue(sb.toString().contains("path x "),
                "the survey found no floor; check the acts and ROM");
    }

    /** Links per-column floor surfaces into paths; returns the {@code top} longest. */
    static String paths(LevelManager levelManager, int minX, int maxX, int minY, int maxY, int top) {
        // Each open path: {startX, lastY, minY, maxY, lastX}.
        List<int[]> open = new ArrayList<>();
        List<int[]> done = new ArrayList<>();
        for (int x = minX; x < maxX; x += 8) {
            TreeSet<Integer> surfaces = new TreeSet<>();
            for (int y = Math.max(0, minY); y < maxY; y += 16) {
                var r = ObjectTerrainUtils.checkFloorDist(levelManager, x, y);
                if (r.foundSurface() && r.distance() >= 0 && r.distance() < 16) surfaces.add(y + r.distance());
            }
            List<int[]> next = new ArrayList<>();
            Set<Integer> used = new HashSet<>();
            for (int[] path : open) {
                Integer best = null;
                for (int sy : surfaces) {
                    if (used.contains(sy) || Math.abs(sy - path[1]) > 24) continue;
                    if (best == null || Math.abs(sy - path[1]) < Math.abs(best - path[1])) best = sy;
                }
                if (best == null) {
                    done.add(path);
                    continue;
                }
                used.add(best);
                path[1] = best;
                path[2] = Math.min(path[2], best);
                path[3] = Math.max(path[3], best);
                path[4] = x;
                next.add(path);
            }
            for (int sy : surfaces) if (!used.contains(sy)) next.add(new int[]{x, sy, sy, sy, x});
            open = next;
        }
        done.addAll(open);
        done.sort((a, b) -> Integer.compare(b[4] - b[0], a[4] - a[0]));
        var sb = new StringBuilder();
        for (int i = 0; i < Math.min(top, done.size()); i++) {
            int[] path = done.get(i);
            sb.append("  path x ").append(path[0]).append('-').append(path[4]).append(" (")
                    .append(path[4] - path[0]).append("px) floor y ").append(path[2]).append('-').append(path[3])
                    .append('\n');
        }
        return sb.toString();
    }
}

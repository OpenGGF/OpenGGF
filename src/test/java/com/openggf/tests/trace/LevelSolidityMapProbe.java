package com.openggf.tests.trace;

import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opt-in diagnostic: writes an ASCII map of a level's foreground solidity so a
 * route can be authored against the real terrain before any ROM capture.
 *
 * <p>Purpose: answer "where are the walls, ledges and one-way platforms" when
 * authoring a BK2 route (glide targets, climbable faces, monitor perches)
 * without guessing from probe captures. Each cell is one sample of the
 * engine's terrain sensors at a grid point: {@code #} inside solid (floor,
 * wall or ceiling sensor reports a negative distance), {@code >} a right wall
 * within sixteen pixels to the right, {@code <} a left wall within sixteen
 * pixels to the left, {@code .} open. Tile tops register on every layer; a
 * one-way platform shows as {@code #} without wall marks. Rows below the
 * playable band may show background-layer artefacts.
 *
 * <p>Inputs (system properties): {@code openggf.solidity.map} (output file;
 * the probe is skipped when unset), {@code openggf.solidity.game} ({@code s1},
 * {@code s2}, {@code s3k}; default {@code s2}), {@code openggf.solidity.zone}
 * and {@code openggf.solidity.act} (0-based; default 0/0),
 * {@code openggf.solidity.x0}/{@code x1}/{@code y0}/{@code y1} (hex or decimal
 * bounds; default 0x40-0x1440 by 0x180-0x400), {@code openggf.solidity.step}
 * (column step in pixels, default 16; rows step 8). The usual ROM path
 * properties select the ROM.
 *
 * <pre>
 * mvn -Dmse=off -Dsonic2.rom.path=/abs/s2.gen -Dtest=LevelSolidityMapProbe \
 *     -Dopenggf.solidity.map=/abs/task-dir/ehz1-solidity.txt test
 * </pre>
 *
 * <p>Originating task: the first Knuckles in Sonic 2 trace fixture
 * (EHZ1 route authoring, 2026-09-14). Comparison-only: it reads the loaded
 * level and never changes engine state.
 */
@RequiresRom(SonicGame.SONIC_2)
class LevelSolidityMapProbe {

    @Test
    void writeSolidityMap() throws Exception {
        String out = System.getProperty("openggf.solidity.map");
        Assumptions.assumeTrue(out != null && !out.isBlank(),
                "set -Dopenggf.solidity.map=<output file> to write a solidity map");
        SonicGame game = switch (System.getProperty("openggf.solidity.game", "s2")) {
            case "s1" -> SonicGame.SONIC_1;
            case "s3k" -> SonicGame.SONIC_3K;
            default -> SonicGame.SONIC_2;
        };
        int zone = Integer.decode(System.getProperty("openggf.solidity.zone", "0"));
        int act = Integer.decode(System.getProperty("openggf.solidity.act", "0"));
        int x0 = Integer.decode(System.getProperty("openggf.solidity.x0", "0x40"));
        int x1 = Integer.decode(System.getProperty("openggf.solidity.x1", "0x1440"));
        int y0 = Integer.decode(System.getProperty("openggf.solidity.y0", "0x180"));
        int y1 = Integer.decode(System.getProperty("openggf.solidity.y1", "0x400"));
        int step = Integer.decode(System.getProperty("openggf.solidity.step", "16"));

        SharedLevel level = SharedLevel.load(game, zone, act);
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("     ");
            for (int x = x0; x < x1; x += step) {
                sb.append(x % 256 == 0 ? Integer.toHexString(x / 256) : (x % 128 == 0 ? "+" : " "));
            }
            sb.append('\n');
            for (int y = y0; y < y1; y += 8) {
                sb.append(String.format("%04X ", y));
                for (int x = x0; x < x1; x += step) {
                    sb.append(cell(x, y));
                }
                sb.append('\n');
            }
            Files.writeString(Path.of(out), sb.toString());
        } finally {
            level.dispose();
        }
    }

    private static char cell(int x, int y) {
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, y);
        TerrainCheckResult right = ObjectTerrainUtils.checkRightWallDist(x, y);
        TerrainCheckResult left = ObjectTerrainUtils.checkLeftWallDist(x, y);
        TerrainCheckResult ceiling = ObjectTerrainUtils.checkCeilingDist(x, y, 0);
        if (inside(floor) || inside(right) || inside(left) || inside(ceiling)) {
            return '#';
        }
        if (near(right)) {
            return '>';
        }
        if (near(left)) {
            return '<';
        }
        return '.';
    }

    private static boolean inside(TerrainCheckResult r) {
        return r.distance() != TerrainCheckResult.NO_COLLISION && r.distance() < 0;
    }

    private static boolean near(TerrainCheckResult r) {
        return r.distance() != TerrainCheckResult.NO_COLLISION && r.distance() >= 0 && r.distance() < 16;
    }
}

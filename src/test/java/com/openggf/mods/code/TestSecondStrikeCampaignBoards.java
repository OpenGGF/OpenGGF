package com.openggf.mods.code;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy;
import com.openggf.game.sonic3k.difficulty.S3kDifficultyRules;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageDataLoader;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageGrid;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageRingConverter;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageRomOffsets;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Native board admission and loop rewards; intentionally not a full-clear oracle. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeCampaignBoards {
    @TempDir static Path temporary;
    private static URLClassLoader creator;
    private static BlueSphereLayoutPolicy policy;
    private static Rom rom;
    private static byte[] superBoards;

    @BeforeAll static void setup() throws Exception {
        creator = SecondStrikeSourceFixture.compile(temporary.resolve("boards"),
                SecondStrikeSourceFixture.MAIN.resolve("secondstrike/campaign/CampaignBlueSphereBoards.java"));
        policy = (BlueSphereLayoutPolicy) creator.loadClass("secondstrike.campaign.CampaignBlueSphereBoards")
                .getConstructor().newInstance();
        rom = new Rom();
        assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
        superBoards = new Sonic3kSpecialStageDataLoader(rom).getCompressedLayoutSet(0);
    }

    @AfterAll static void cleanup() throws Exception {
        if (creator != null) creator.close();
        if (rom != null) rom.close();
    }

    @Test void allFourteenHaveDistinctBoundedNativeApproachRecipes() throws Exception {
        int authored = 0;
        for (int stage = 0; stage < 16; stage++) {
            byte[] original = board(stage);
            byte[] saved = original.clone();
            byte[] changed = policy.transformLayout(stage, original);
            assertArrayEquals(saved, original, "source board " + stage);
            assertNotSame(original, changed);
            assertArrayEquals(changed, S3kDifficultyRules.blueSphereLayout(original, changed), "admission " + stage);
            assertArrayEquals(Arrays.copyOfRange(original, 1024, 1032), Arrays.copyOfRange(changed, 1024, 1032));
            int edits = 0;
            for (int cell = 0; cell < 1024; cell++) {
                if (original[cell] != changed[cell]) {
                    assertEquals(0, original[cell], "original cell " + stage + ":" + cell);
                    assertTrue(changed[cell] == 1 || changed[cell] == 3, "native red or bumper approach");
                    edits++;
                }
            }
            if (stage == 7 || stage == 15) {
                assertEquals(0, edits, "non-campaign native slot " + stage);
            } else {
                authored++;
                assertTrue(edits >= 2 && edits <= 12, "authored edits " + stage + ": " + edits);
            }
        }
        assertEquals(14, authored);
        assertEquals(3, policy.transformLayout(2, board(2))[26 * 32 + 22],
                "this approach must not join the blue loop to the native red wall network");
    }

    @Test void nativeRectangularBlueLoopRewardsSurviveEveryAuthoredGate() throws Exception {
        int checked = 0;
        for (int stage = 0; stage < 16; stage++) {
            if (stage == 7 || stage == 15) continue;
            byte[] original = board(stage);
            byte[] changed = policy.transformLayout(stage, original);
            for (int y = 0; y < 30; y++) for (int x = 0; x < 30; x++) {
                for (int width = 3; width <= 8 && x + width <= 32; width++) {
                    for (int height = 3; height <= 8 && y + height <= 32; height++) {
                        if (!blueRectangle(original, x, y, width, height)) continue;
                        byte[] stockReward = diagnosticConvert(original, "stock", stage, x, y, width, height);
                        byte[] authoredReward = diagnosticConvert(changed, "authored", stage, x, y, width, height);
                        // A blue sub-rectangle in a larger connected shape is
                        // not necessarily a native enclosing loop. Let the ROM
                        // converter decide on the stock board first.
                        if (stockReward == null) {
                            assertNull(authoredReward, "approach gates must not invent a loop reward");
                            continue;
                        }
                        assertNotNull(authoredReward, "native loop must remain convertible on board " + stage);
                        for (int cell = 0; cell < 1024; cell++) {
                            assertEquals(stockReward[cell] == 4, authoredReward[cell] == 4,
                                    "native ring result " + stage + ":" + cell);
                        }
                        checked++;
                    }
                }
            }
        }
        assertEquals(232, checked, "native convertible rectangles on the fourteen verified ROM boards");
    }

    @Test void unexpectedOriginalCellFailsClosedAndReturnedBoardsDoNotShareState() throws Exception {
        byte[] original = board(0);
        byte[] changed = policy.transformLayout(0, original);
        changed[0] = 0;
        assertEquals(1, policy.transformLayout(0, original)[0]);
        original[8 * 32 + 2] = 2;
        assertThrows(IllegalArgumentException.class, () -> policy.transformLayout(0, original));
        assertThrows(IllegalArgumentException.class, () -> policy.transformLayout(0, new byte[1024]));
    }

    private static boolean blueRectangle(byte[] layout, int x, int y, int width, int height) {
        for (int yy = y; yy < y + height; yy++) for (int xx = x; xx < x + width; xx++) {
            if ((yy == y || yy == y + height - 1 || xx == x || xx == x + width - 1)
                    && layout[yy * 32 + xx] != 2) return false;
        }
        return true;
    }

    private static byte[] diagnosticConvert(byte[] layout, String kind, int stage,
                                            int x, int y, int width, int height) {
        if (Boolean.getBoolean("secondstrike.boards.diagnose")) {
            System.out.println("BOARD_CONVERSION " + kind + " stage=" + stage
                    + " rectangle=" + x + "," + y + "," + width + "," + height);
            System.out.flush();
        }
        return convert(layout, x, y, width, height);
    }

    private static byte[] convert(byte[] layout, int x, int y, int width, int height) {
        var grid = new Sonic3kSpecialStageGrid();
        grid.loadFromLayoutData(layout);
        for (int yy = y; yy < y + height; yy++) for (int xx = x; xx < x + width; xx++) {
            if (yy == y || yy == y + height - 1 || xx == x || xx == x + width - 1) grid.setCell(xx, yy, 1);
        }
        grid.setCell(x, y, 0xA);
        if (!new Sonic3kSpecialStageRingConverter().convert(grid, y * 32 + x).converted) return null;
        byte[] result = new byte[1024];
        for (int i = 0; i < result.length; i++) result[i] = (byte) grid.getCellByIndex(i);
        return result;
    }

    private static byte[] board(int stage) throws Exception {
        return stage < 8 ? rom.readBytes(Sonic3kSpecialStageRomOffsets.LAYOUT_S3_STAGE_1 + stage * 0x408L, 0x408)
                : Arrays.copyOfRange(superBoards, (stage - 8) * 0x408, (stage - 7) * 0x408);
    }
}

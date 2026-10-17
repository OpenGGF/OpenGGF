package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.PatternDesc;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ROM sub_953E/Sub_A206 projection witness, including integer truncation and P2 depth.
 * Native stage+138/148/160 SAT publishes solo Tails at centre (160,162).
 * These component inlets isolate presentation geometry; no trace row supplies state.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSpecialStagePlayerProjection {
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @CsvSource({"0,162,179", "-1,162,178", "-32,151,168", "-88,130,146",
            "-136,108,124", "-256,27,42", "-288,-5,9"})
    void romProjectedPlayersKeepTheirDepthAndIntegerJumpCurve(int height, int mainY, int sidekickY)
            throws Exception {
        // GetScalars2(index E0) reads sine E0 and cosine 20 from the shipped table.
        var rom = GameServices.rom().getRom();
        assertEquals((short) -11585, (short) rom.read16BitAddr(0xA264 + 0xE0 * 2));
        assertEquals(11585, rom.read16BitAddr(0xA264 + 0x20 * 2));
        for (int width : new int[]{320, 800}) {
            for (PlayerCharacter character : PlayerCharacter.values()) {
                var graphics = new RecordingGraphics();
                var renderer = new Sonic3kSpecialStageRenderer(graphics);
                renderer.setSpecialStageViewport(SpecialStageViewport.fromLogicalWidth(width));
                renderer.setPlayerProjectionScalars(Sonic3kSpecialStageDataLoader.create().getScalarTable());
                renderer.setPlayerPatternBase(0x5000);
                renderer.setSonicMappingData(singleTileMapping(), singleTileMapping());
                renderer.setTailsPatternBase(0x6000);
                renderer.setTailsMappingData(singleTileMapping(), singleTileMapping());
                renderer.setArtLoaded(true);
                var manager = new Sonic3kSpecialStageManager();
                manager.getPlayer().initialize(0, 0x1000, 0x1000, false);
                set(manager, "playerCharacter", character);
                set(manager, "tailsEnabled", character == PlayerCharacter.SONIC_AND_TAILS);
                set(manager.getPlayer(), "jumpHeight", (long) height << 16);
                set(manager, "tailsJumpHeight", (long) height << 16);
                renderer.render(manager);
                var expected = new ArrayList<Tile>();
                expected.add(new Tile(0x5000, width / 2, mainY));
                if (character == PlayerCharacter.SONIC_AND_TAILS)
                    expected.add(new Tile(0x6000, width / 2, sidekickY));
                assertEquals(expected, graphics.tiles, character + " width=" + width + " height=" + height);
            }
        }
    }

    private static byte[] singleTileMapping() {
        int count = 12, dplc = count * 4, map = dplc + 4;
        byte[] data = new byte[map + 8];
        for (int i = 0; i < count; i++) {
            word(data, i * 2, map);
            word(data, count * 2 + i * 2, dplc - count * 2);
        }
        word(data, dplc, 1);
        word(data, map, 1);
        return data;
    }
    private static void word(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8); data[offset + 1] = (byte) value;
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }
    private record Tile(int pattern, int x, int y) { }
    private static final class RecordingGraphics extends GraphicsManager {
        final List<Tile> tiles = new ArrayList<>();
        @Override public void beginPatternBatch() { }
        @Override public void flushPatternBatch() { }
        @Override public void renderPatternWithId(int pattern, PatternDesc descriptor, int x, int y) {
            tiles.add(new Tile(pattern, x, y));
        }
    }
}

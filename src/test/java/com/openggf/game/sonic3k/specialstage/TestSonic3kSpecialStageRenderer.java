package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.PlayerCharacter;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.PatternDesc;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic3kSpecialStageRenderer {
    @Test
    void soloTailsMainSpriteAndTailAppendageUsePlayer2PaletteLine() {
        RecordingGraphicsManager graphics = new RecordingGraphicsManager();
        Sonic3kSpecialStageRenderer renderer = new Sonic3kSpecialStageRenderer(graphics);
        Sonic3kSpecialStageManager manager = new Sonic3kSpecialStageManager();

        set(manager, "playerCharacter", PlayerCharacter.TAILS_ALONE);
        set(manager, "tailsTailsMappingFrame", 0);
        manager.getPlayer().initialize(Sonic3kSpecialStageConstants.ANGLE_NORTH, 0x1000, 0x1000, false);

        int playerBase = 0x5000;
        int tailsTailBase = 0x6000;
        renderer.setPlayerPatternBase(playerBase);
        renderer.setTailsTailsPatternBase(tailsTailBase);
        renderer.setSonicMappingData(singleTileMappingData(12), singleTileMappingData(12));
        renderer.setTailsTailsMappingData(singleTileMappingData(15));
        renderer.setArtLoaded(true);

        renderer.render(manager);

        assertEquals(List.of(
                        new RenderCall(playerBase, 1),
                        new RenderCall(tailsTailBase, 1)),
                graphics.calls);
    }

    private static byte[] singleTileMappingData(int frameCount) {
        int dplcHeader = frameCount * 2;
        int dplcFrame = frameCount * 4;
        int mapFrame = dplcFrame + 4;
        byte[] data = new byte[mapFrame + 8];
        writeWord(data, 0, mapFrame);
        writeWord(data, dplcHeader, dplcFrame - dplcHeader);
        writeWord(data, dplcFrame, 1);
        writeWord(data, dplcFrame + 2, 0);
        writeWord(data, mapFrame, 1);
        data[mapFrame + 2] = 0;
        data[mapFrame + 3] = 0;
        writeWord(data, mapFrame + 4, 0);
        writeWord(data, mapFrame + 6, 0);
        return data;
    }

    private static void writeWord(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 8) & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private record RenderCall(int patternId, int paletteIndex) {}

    private static final class RecordingGraphicsManager extends GraphicsManager {
        final List<RenderCall> calls = new ArrayList<>();

        @Override
        public void beginPatternBatch() {
        }

        @Override
        public void flushPatternBatch() {
        }

        @Override
        public void renderPatternWithId(int patternId, PatternDesc desc, int x, int y) {
            calls.add(new RenderCall(patternId, desc.getPaletteIndex()));
        }
    }
}

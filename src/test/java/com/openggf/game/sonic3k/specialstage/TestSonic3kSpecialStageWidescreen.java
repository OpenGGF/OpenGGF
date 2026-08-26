package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.PatternDesc;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Real S3K special-stage command and viewport lifecycle evidence. */
class TestSonic3kSpecialStageWidescreen {
    @Test
    void everyNativeStageCommandMovesTogetherAtTheResolvedOuterOrigin() {
        List<RenderCall> nativeCalls = renderAt(320);
        List<RenderCall> wideCalls = renderAt(400);
        List<RenderCall> ultrawideCalls = renderAt(528);

        assertEquals(nativeCalls.size(), wideCalls.size(), "400px must not add stage art");
        assertEquals(nativeCalls.size(), ultrawideCalls.size(), "528px must not add stage art");
        assertEquals(0, nativeCalls.get(0).x(), "native background starts at native origin");
        int floorLastCommand = 41 * 29 + 40 * 28 - 1;
        assertEquals(312, nativeCalls.get(floorLastCommand).x(),
                "native floor command stream ends at the fixed 320px stage boundary");

        assertShifted(nativeCalls, wideCalls, 40);
        assertShifted(nativeCalls, ultrawideCalls, 104);
    }

    @Test
    void providerPublishesViewportToItsManagerBeforeRendering() {
        Sonic3kSpecialStageManager manager = new Sonic3kSpecialStageManager();
        Sonic3kSpecialStageProvider provider = new Sonic3kSpecialStageProvider(manager);
        SpecialStageViewport viewport = SpecialStageViewport.fromLogicalWidth(528);

        provider.setSpecialStageViewport(viewport);

        assertSame(viewport, provider.getSpecialStageViewport());
        assertSame(viewport, manager.getSpecialStageViewport());
        assertEquals(104, manager.getSpecialStageViewport().outerOriginX());
    }

    private static void assertShifted(List<RenderCall> nativeCalls,
                                      List<RenderCall> shiftedCalls, int origin) {
        for (int i = 0; i < nativeCalls.size(); i++) {
            RenderCall nativeCall = nativeCalls.get(i);
            RenderCall shiftedCall = shiftedCalls.get(i);
            assertEquals(nativeCall.patternId(), shiftedCall.patternId(), "pattern identity at " + i);
            assertEquals(nativeCall.paletteIndex(), shiftedCall.paletteIndex(), "palette at " + i);
            assertEquals(nativeCall.y(), shiftedCall.y(), "native Y at " + i);
            assertEquals(nativeCall.x() + origin, shiftedCall.x(), "outer origin at " + i);
        }
    }

    private static List<RenderCall> renderAt(int logicalWidth) {
        RecordingGraphicsManager graphics = new RecordingGraphicsManager();
        Sonic3kSpecialStageRenderer renderer = new Sonic3kSpecialStageRenderer(graphics);
        renderer.setSpecialStageViewport(SpecialStageViewport.fromLogicalWidth(logicalWidth));
        renderer.setArtLoaded(true);
        renderer.setFloorPatternBase(0x1000);
        renderer.setSpherePatternBase(0x2000);
        renderer.setRingPatternBase(0x3000);
        renderer.setBgPatternBase(0x4000);
        renderer.setGetBlueSpherePatternBase(0x5000);
        renderer.setDigitsPatternBase(0x6000);
        renderer.setIconsPatternBase(0x7000);
        renderer.setPlayerPatternBase(0x8000);
        renderer.setPerspectiveMaps(perspectiveMapWithUniformDepth());
        renderer.setBgMapData(repeatedMap(64 * 32, Sonic3kSpecialStageConstants.ART_TILE_BG + 3));
        renderer.setFloorMapData(repeatedMap(9 * 40 * 28, 0x12));
        renderer.setHudNumberMap(new byte[120]);
        renderer.setHudTemplate(new byte[48]);
        renderer.setSonicMappingData(singleTileMappingData(12), singleTileMappingData(12));
        renderer.setBannerMappingData(singleTileBannerMappingData());

        Sonic3kSpecialStageManager manager = new Sonic3kSpecialStageManager();
        manager.getPlayer().initialize(Sonic3kSpecialStageConstants.ANGLE_NORTH,
                0x1000, 0x1000, false);
        manager.getGrid().setCellByIndex(0x2C8, Sonic3kSpecialStageConstants.CELL_BLUE);
        manager.getBanner().initialize();
        set(manager, "playerCharacter", com.openggf.game.PlayerCharacter.SONIC_ALONE);
        set(manager, "tailsEnabled", false);

        renderer.render(manager);
        return graphics.calls;
    }

    private static byte[] repeatedMap(int words, int tile) {
        byte[] data = new byte[words * 2];
        for (int i = 0; i < words; i++) {
            writeWord(data, i * 2, 0xF800 | tile);
        }
        return data;
    }

    private static byte[] singleTileBannerMappingData() {
        byte[] data = new byte[40];
        for (int frame = 0; frame < 4; frame++) {
            int frameOffset = 8 + frame * 8;
            writeWord(data, frame * 2, frameOffset);
            writeWord(data, frameOffset, 1);
            data[frameOffset + 2] = 0;
            data[frameOffset + 3] = 0;
            writeWord(data, frameOffset + 4, 0);
            writeWord(data, frameOffset + 6, 0);
        }
        return data;
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

    private static byte[] perspectiveMapWithUniformDepth() {
        int pointerTableLength = 24 * 4;
        byte[] data = new byte[pointerTableLength + 16 * 15 * 6];
        writeLong(data, 0, 0x00FF0000 + pointerTableLength);
        for (int i = 0; i < 16 * 15; i++) {
            int offset = pointerTableLength + i * 6;
            writeWord(data, offset, 18 << 2);
            writeWord(data, offset + 2, 128 + i);
            writeWord(data, offset + 4, 228);
        }
        return data;
    }

    private static void writeLong(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    private static void writeWord(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    private static void set(Object target, String field, Object value) {
        try {
            var declaredField = target.getClass().getDeclaredField(field);
            declaredField.setAccessible(true);
            declaredField.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private record RenderCall(int patternId, int paletteIndex, int x, int y) {}

    private static final class RecordingGraphicsManager extends GraphicsManager {
        private final List<RenderCall> calls = new ArrayList<>();

        @Override public void beginPatternBatch() {}
        @Override public void flushPatternBatch() {}

        @Override
        public void renderPatternWithId(int patternId, PatternDesc desc, int x, int y) {
            calls.add(new RenderCall(patternId, desc.getPaletteIndex(), x, y));
        }
    }
}


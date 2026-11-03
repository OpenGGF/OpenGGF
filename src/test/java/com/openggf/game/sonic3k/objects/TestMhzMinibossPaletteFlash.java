package com.openggf.game.sonic3k.objects;

import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestMhzMinibossPaletteFlash {
    private static final int[] INDICES = {7, 11, 13, 14};
    private static final int[] NORMAL = {0x224, 0x222, 0xA64, 0x642};
    private static final int[] BRIGHT = {0xAAA, 0xEEE, 0x888, 0x666};

    @Test
    void nativeHitFlashesFourColorsThenRestoresWithoutTouchingOtherColors() throws Exception {
        StubLevel level = new StubLevel();
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        MhzMinibossInstance boss = boss(level, registry);
        boss.onPlayerAttack(null, null);
        assertEquals(5, boss.getState().hitCount);
        for (int tick = 0; tick < 32; tick++) {
            registry.beginFrame();
            boss.update(tick, null);
            registry.resolveInto(level.palettes(), null, null, null);
            int[] expected = (tick & 1) == 0 ? BRIGHT : NORMAL;
            for (int i = 0; i < INDICES.length; i++) {
                assertColorWord(level.getPalette(1), INDICES[i], expected[i]);
                assertEquals(S3kPaletteOwners.MHZ_MINIBOSS,
                        registry.ownerAt(PaletteSurface.NORMAL, 1, INDICES[i]));
            }
            for (int line = 0; line < 4; line++) {
                for (int color = 0; color < 16; color++) {
                    boolean flashed = false;
                    for (int index : INDICES) flashed |= line == 1 && color == index;
                    if (!flashed) assertColorWord(level.getPalette(line), color, 0);
                }
            }
            assertEquals(31 - tick, boss.getState().invulnerabilityTimer);
            assertEquals(tick < 31, boss.getState().invulnerable);
            if (tick < 31) {
                boss.onPlayerAttack(null, null);
                assertEquals(5, boss.getState().hitCount, "flash must reject repeated damage");
            }
        }
        boss.update(32, null);
        for (int i = 0; i < INDICES.length; i++) assertColorWord(level.getPalette(1), INDICES[i], NORMAL[i]);
        boss.onPlayerAttack(null, null);
        assertEquals(4, boss.getState().hitCount, "collision resumes after all 32 updates");
    }

    @Test
    void timerAdvancesWithoutPaletteLevel() throws Exception {
        MhzMinibossInstance boss = boss(null, null);
        boss.onPlayerAttack(null, null);
        for (int tick = 0; tick < 32; tick++) boss.update(tick, null);
        assertEquals(false, boss.getState().invulnerable);
        assertEquals(0, boss.getState().invulnerabilityTimer);
    }

    @Test
    void directPaletteFallbackAlsoEndsOnNativeNormalColors() throws Exception {
        StubLevel level = new StubLevel();
        MhzMinibossInstance boss = boss(level, null);
        boss.onPlayerAttack(null, null);
        for (int tick = 0; tick < 32; tick++) boss.update(tick, null);
        for (int i = 0; i < INDICES.length; i++) assertColorWord(level.getPalette(1), INDICES[i], NORMAL[i]);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"aiz", "hcz-mini", "hcz-end"})
    void otherCustomFlashBossesReadCounterBeforeDecrement(String kind) throws Exception {
        StubLevel level = new StubLevel();
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        com.openggf.level.objects.boss.AbstractBossInstance boss;
        int[] indices;
        int[] normal;
        int[] bright;
        if (kind.equals("aiz")) {
            boss = new AizMinibossInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
            indices = new int[]{7, 10, 11, 14};
            normal = new int[]{0x644, 0x240, 0x020, 0x644};
            bright = new int[]{0x888, 0xAAA, 0xEEE, 0xAAA};
        } else if (kind.equals("hcz-mini")) {
            boss = new HczMinibossInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
            indices = new int[]{4, 7, 9, 10, 11, 13, 14};
            normal = new int[]{0x004, 0, 0x00C, 0x008, 0x020, 0x826, 0x624};
            bright = new int[]{0xAAA, 0xAAA, 0x888, 0xAAA, 0xEEE, 0x888, 0xAAA};
        } else {
            boss = new com.openggf.game.sonic3k.objects.bosses.HczEndBossInstance(
                    new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
            indices = new int[]{10, 11, 14};
            normal = new int[]{0x006, 0x020, 0x624};
            bright = new int[]{0xEEE, 0xEEE, 0xEEE};
        }
        com.openggf.camera.Camera camera = org.mockito.Mockito.mock(com.openggf.camera.Camera.class);
        boss.setServices(new StubObjectServices() {
            @Override public com.openggf.camera.Camera camera() { return camera; }
            @Override public Level currentLevel() { return level; }
            @Override public PaletteOwnershipRegistry paletteOwnershipRegistryOrNull() { return registry; }
        });
        // Idle routine isolates the damage tail while exercising the real public update.
        boss.getState().routine = 0x7E;
        boss.getState().invulnerable = true;
        boss.getState().invulnerabilityTimer = 32;
        for (int tick = 0; tick < 32; tick++) {
            registry.beginFrame();
            boss.update(tick, null);
            registry.resolveInto(level.palettes(), null, null, null);
            int[] expected = (tick & 1) == 0 ? bright : normal;
            for (int i = 0; i < indices.length; i++) assertColorWord(level.getPalette(1), indices[i], expected[i]);
            assertEquals(31 - tick, boss.getState().invulnerabilityTimer);
        }
        assertEquals(false, boss.getState().invulnerable);
    }

    private static MhzMinibossInstance boss(StubLevel level, PaletteOwnershipRegistry registry) throws Exception {
        MhzMinibossInstance boss = new MhzMinibossInstance(new ObjectSpawn(
                0x4200, 0x200, Sonic3kObjectIds.MHZ_MINIBOSS, 0, 0, false, 0));
        com.openggf.camera.Camera camera = org.mockito.Mockito.mock(com.openggf.camera.Camera.class);
        boss.setServices(new StubObjectServices() {
            @Override public com.openggf.camera.Camera camera() { return camera; }
            @Override public Level currentLevel() { return level; }
            @Override public PaletteOwnershipRegistry paletteOwnershipRegistryOrNull() { return registry; }
        });
        Field pending = MhzMinibossInstance.class.getDeclaredField("cameraInitPending");
        pending.setAccessible(true);
        pending.setBoolean(boss, false);
        // Keep routine dispatch idle to isolate the damage tail from arena movement.
        boss.getState().routine = 0x7E;
        return boss;
    }

    private static void assertColorWord(Palette palette, int colorIndex, int segaWord) {
        byte highByte = (byte) ((segaWord >> 8) & 0xFF);
        byte lowByte = (byte) (segaWord & 0xFF);
        int r3 = (lowByte >> 1) & 0x07;
        int g3 = (lowByte >> 5) & 0x07;
        int b3 = (highByte >> 1) & 0x07;
        int expectedR = (r3 * 255 + 3) / 7;
        int expectedG = (g3 * 255 + 3) / 7;
        int expectedB = (b3 * 255 + 3) / 7;
        assertEquals(expectedR, palette.getColor(colorIndex).r & 0xFF);
        assertEquals(expectedG, palette.getColor(colorIndex).g & 0xFF);
        assertEquals(expectedB, palette.getColor(colorIndex).b & 0xFF);
    }

    private static byte[] segaWordBytes(int segaWord) {
        return new byte[] {
                (byte) ((segaWord >> 8) & 0xFF),
                (byte) (segaWord & 0xFF)
        };
    }

    private static final class StubLevel implements Level {
        private final Palette[] palettes = new Palette[] {
                new Palette(), new Palette(), new Palette(), new Palette()
        };

        Palette[] palettes() {
            return palettes;
        }

        @Override public int getPaletteCount() { return palettes.length; }
        @Override public Palette getPalette(int index) { return palettes[index]; }
        @Override public int getPatternCount() { return 0; }
        @Override public Pattern getPattern(int index) { throw new UnsupportedOperationException(); }
        @Override public int getChunkCount() { return 0; }
        @Override public Chunk getChunk(int index) { throw new UnsupportedOperationException(); }
        @Override public int getBlockCount() { return 0; }
        @Override public Block getBlock(int index) { throw new UnsupportedOperationException(); }
        @Override public SolidTile getSolidTile(int index) { throw new UnsupportedOperationException(); }
        @Override public Map getMap() { return null; }
        @Override public List<ObjectSpawn> getObjects() { return List.of(); }
        @Override public List<RingSpawn> getRings() { return List.of(); }
        @Override public RingSpriteSheet getRingSpriteSheet() { return null; }
        @Override public int getMinX() { return 0; }
        @Override public int getMaxX() { return 0; }
        @Override public int getMinY() { return 0; }
        @Override public int getMaxY() { return 0; }
        @Override public int getZoneIndex() { return 0; }
    }
}

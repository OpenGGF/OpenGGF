package slaytherobotnik.art;

import com.openggf.mods.scene.*;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Original fixture pixels exercise the facade; canonical ROM equivalence is covered separately. */
class ArtRetryTest {
    @Test void repairedSpriteIsAdmittedAfter120TicksAndSuccessfulImagesRemainLive() {
        Fixture f = new Fixture();
        Art art = f.art();
        var good = art.rom("ring");
        f.failure = new IllegalArgumentException("corrupt sprite");
        assertNull(art.rom("tornado"));
        int attempts = f.spriteLoads;
        f.failure = null;
        for (int i = 0; i < 100; i++) assertNull(art.rom("tornado"));
        assertEquals(attempts, f.spriteLoads);
        f.ticks = 119;
        assertNull(art.rom("tornado"));
        f.ticks = 120;
        assertNotNull(art.rom("tornado"));
        assertEquals(attempts + 1, f.spriteLoads);
        assertSame(good, art.rom("ring"));
        art.close();
        assertEquals(0xFF123456, good.frame(0).image().pixel(0, 0));
        assertThrows(IllegalStateException.class, () -> art.rom("ring"));
    }

    @Test void partiallyDecodedSlotBatchNeverPublishesAndRepairRetriesWholeBatch() {
        Fixture f = new Fixture();
        Art art = f.art();
        f.tileFailureAt = 3;
        assertNull(art.slotFace(0));
        assertEquals(3, f.tileLoads);
        f.tileFailureAt = -1;
        for (int i = 0; i < 20; i++) assertNull(art.slotFace(0));
        assertEquals(3, f.tileLoads);
        f.ticks = 120;
        assertNotNull(art.slotFace(0));
        assertEquals(11, f.tileLoads);
        for (int i = 0; i < 8; i++) assertNotNull(art.slotFace(i));
        assertEquals(11, f.tileLoads);
    }

    @Test void titleCardAndOverviewFailuresHaveTheSameBoundedRetryAdmission() {
        Fixture f = new Fixture();
        Art art = f.art();
        f.failure = new IllegalStateException("damaged act");
        assertNull(art.titleCard(0, 0));
        assertNull(art.levelOverview(0, 0, 80));
        f.failure = null;
        for (int i = 0; i < 20; i++) {
            assertNull(art.titleCard(0, 0));
            assertNull(art.levelOverview(0, 0, 80));
        }
        assertEquals(1, f.titleLoads);
        assertEquals(1, f.overviewLoads);
        f.ticks = 120;
        assertNotNull(art.titleCard(0, 0));
        assertNotNull(art.levelOverview(0, 0, 80));
    }

    @Test void programmingFaultIsNotMaskedAsMissingArt() {
        Fixture f = new Fixture();
        f.failure = new NullPointerException("decoder programming fault");
        assertThrows(NullPointerException.class, () -> f.art().rom("ring"));
    }

    @Test void failedPaletteAndRomDependencyRetainTheirOriginalKindAndDeadline() throws Exception {
        Fixture f = new Fixture();
        Art art = f.art();
        f.paletteFailureAt = 2;
        assertNull(art.rom("ring"));
        f.paletteFailureAt = -1;
        f.ticks = 120;
        assertNotNull(art.rom("ring"));
        assertEquals(4, f.paletteLoads);
        Fixture broken = new Fixture();
        Art bad = broken.art();
        broken.romFailure = new UnsupportedOperationException("provider unavailable");
        assertNull(bad.rom());
        broken.ticks = 119;
        assertNull(bad.rom("ring"));
        Object dependent = status(bad).getLast();
        assertEquals("UNSUPPORTED", field(dependent, "kind"));
        assertEquals("120", field(dependent, "retryAt"));
        assertTrue(field(dependent, "cause").contains("provider unavailable"));
        broken.romFailure = null;
        broken.ticks = 120;
        assertNotNull(bad.rom("ring"));
    }

    @Test void missingUnsupportedAndInvalidStatusIsBoundedAndNamesActualOwner() throws Exception {
        Fixture f = new Fixture();
        Art art = f.art();
        assertNull(art.rom("unknown"));
        assertEquals("UNSUPPORTED", field(status(art).getFirst(), "kind"));
        f.failure = new IllegalArgumentException("x".repeat(1000));
        assertNull(art.rom("ring"));
        assertEquals("INVALID_REQUEST", field(status(art).getLast(), "kind"));
        for (int i = 0; i < 200; i++) art.rom("unknown:" + i + ":" + "x".repeat(1000));
        assertTrue(status(art).size() <= 64);
        for (Object entry : status(art)) {
            assertTrue(field(entry, "request").length() <= 256);
            assertTrue(field(entry, "cause").length() <= 256);
            assertEquals("fixture-owner", field(entry, "owner"));
        }
        Fixture absent = new Fixture();
        absent.available = false;
        Art missing = absent.art();
        assertNull(missing.rom("ring"));
        assertEquals("OPTIONAL_ROM_MISSING", field(status(missing).getLast(), "kind"));
        absent.available = true;
        absent.ticks = 120;
        assertNotNull(missing.rom("ring"));
    }

    private static List<?> status(Art art) throws Exception {
        return (List<?>) Art.class.getMethod("failures").invoke(art);
    }
    private static String field(Object entry, String name) throws Exception {
        return entry.getClass().getMethod(name).invoke(entry).toString();
    }

    static final class Fixture {
        long ticks;
        boolean available = true;
        RuntimeException failure;
        RuntimeException romFailure;
        int paletteLoads, paletteFailureAt = -1;
        int spriteLoads, tileLoads, titleLoads, overviewLoads;
        int tileFailureAt = -1;
        final SceneImage image = new SceneImage(1, 1, new int[] {0xFF123456});
        final SceneSpriteSet set = new SceneSpriteSet() {
            public int frameCount() { return 32; }
            public SceneSprite frame(int index) { return new SceneSprite(image, 0, 0); }
            public int[] animationFrames(int id) { return new int[] {0}; }
            public int animationDelay(int id) { return 8; }
        };
        final SceneRomArt rom = (SceneRomArt) Proxy.newProxyInstance(SceneRomArt.class.getClassLoader(),
                new Class<?>[] {SceneRomArt.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "gameId" -> "s3k";
                    case "romSha1" -> "cfbf98c36c776677290a872547ac47c53d2761d6";
                    case "palette" -> { paletteLoads++; if (paletteLoads == paletteFailureAt) throw new IllegalArgumentException("partial palette"); yield new int[(int) args[1]]; }
                    case "hasZonePictures", "hasTitleCard" -> true;
                    case "levelBackground", "zoneBackdrop" -> null;
                    case "levelStages" -> List.of(new SceneLevelStage(0, 172, 400, new int[400]));
                    case "sprites" -> { spriteLoads++; if (failure != null) throw failure; yield set; }
                    case "character", "characterAccessory" -> { if (failure != null) throw failure; yield set; }
                    case "titleCard" -> { titleLoads++; if (failure != null) throw failure; yield set; }
                    case "levelOverview", "levelForeground" -> { overviewLoads++; if (failure != null) throw failure; yield image; }
                    case "tiles" -> { tileLoads++; if (tileLoads == tileFailureAt) throw new IllegalArgumentException("partial tiles"); yield image; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        final SceneArt driver = new SceneArt() {
            public SceneImage png(byte[] data) { return image; }
            public SceneRomArt rom() { if (romFailure != null) throw romFailure; return available ? rom : null; }
        };
        final SceneContext ctx = (SceneContext) Proxy.newProxyInstance(SceneContext.class.getClassLoader(),
                new Class<?>[] {SceneContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "art" -> driver;
                    case "ticks" -> ticks;
                    case "ownerModId" -> "fixture-owner";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Art art() { return new Art(ctx, new byte[0], new byte[0], new byte[0]); }
    }
}

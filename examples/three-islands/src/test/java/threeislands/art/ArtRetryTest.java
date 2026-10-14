package threeislands.art;

import com.openggf.mods.scene.*;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Original fixture pixels exercise repair and publication, rather than ROM pixel equivalence. */
class ArtRetryTest {
    @Test void providerStateFaultEscapesAndDocumentedIoFailureStillRetries() {
        Fixture f = new Fixture(); f.failure = new IllegalStateException("fixture provider owner is retired");
        assertThrows(IllegalStateException.class, () -> f.art().sprites("s3k:ring"));
        f.failure = new IllegalStateException("ROM unavailable", new java.io.IOException("fixture I/O"));
        Art art = f.art(); assertNull(art.sprites("s3k:ring"));
        f.failure = null; f.ticks = 120; assertNotNull(art.sprites("s3k:ring"));
    }
    @Test void failedSpriteAndMissingRomAreNotPermanentSuccessCacheEntries() throws Exception {
        Fixture f = new Fixture();
        Art art = f.art();
        f.failure = new IllegalArgumentException("corrupt sprite");
        assertNull(art.sprites("s3k:ring"));
        f.failure = null;
        for (int i = 0; i < 20; i++) assertNull(art.sprites("s3k:ring"));
        assertEquals(1, f.spriteLoads);
        f.ticks = 120;
        assertNotNull(art.sprites("s3k:ring"));
        assertEquals(2, f.spriteLoads);
        Fixture absent = new Fixture();
        absent.available = false;
        Art missing = absent.art();
        assertFalse(missing.has("s3k"));
        absent.available = true;
        absent.ticks = 120;
        assertTrue(missing.has("s3k"));
    }

    @Test void unavailableKitNeverPublishesZeroPaletteOrPoisonedSprite() throws Exception {
        Fixture f = new Fixture();
        f.kitAvailable = false;
        Art art = f.art();
        assertArrayEquals(new int[64], art.kitPalette("s1", 0));
        assertNull(art.sprites("s1:motobug"));
        assertEquals(0, f.spriteLoads, "fallback palette must not become decoded sprite authority");
        f.kitAvailable = true;
        f.ticks = 120;
        assertEquals(0xFF123456, art.kitPalette("s1", 0)[1]);
        assertNotNull(art.sprites("s1:motobug"));
        assertEquals(0xFF123456, f.lastPalette[1]);
    }

    @Test void failedSecondPaletteRangeRetriesCompleteCandidate() throws Exception {
        Fixture f = new Fixture();
        f.paletteFailureAt = 2;
        Art art = f.art();
        assertNull(art.sprites("s3k:ring"));
        f.paletteFailureAt = -1;
        f.ticks = 120;
        assertNotNull(art.sprites("s3k:ring"));
        assertEquals(4, f.paletteLoads);
        assertEquals(0xFF123456, f.lastPalette[1]);
        assertEquals(0xFF123456, f.lastPalette[17]);
    }

    @Test void programmingFaultIsNotMaskedAsMissingArt() {
        Fixture f = new Fixture();
        f.failure = new NullPointerException("decoder programming fault");
        assertThrows(NullPointerException.class, () -> f.art().sprites("s3k:ring"));
    }

    @Test void suppressedInvalidKitKeepsItsCauseAndDeadlineThroughPaletteAndSprite() throws Exception {
        Fixture f = new Fixture();
        Art art = f.art();
        f.kitFailure = new IllegalArgumentException("corrupt kit");
        assertNull(art.kit("s1", 0, 0));
        f.ticks = 119;
        assertNull(art.sprites("s1:motobug"));
        Object dependent = status(art).getLast();
        assertEquals("INVALID_REQUEST", field(dependent, "kind"));
        assertTrue(field(dependent, "cause").contains("corrupt kit"));
        assertEquals("120", field(dependent, "retryAt"));
        f.kitFailure = null;
        f.ticks = 120;
        assertNotNull(art.sprites("s1:motobug"));
    }

    @Test void requestPaletteCacheSharesEquivalentBanksAndCloseKeepsExistingImagesValid() {
        Fixture f = new Fixture();
        Art art = f.art();
        var ship = art.sprites("s3k:ship");
        var image = ship.frame(0).image();
        assertSame(ship, art.sprites("boss:ship"));
        assertEquals(1, f.spriteLoads, "same actual request and copied palette share one bank");
        art.close();
        assertEquals(0xFF123456, image.pixel(0, 0));
        assertThrows(IllegalStateException.class, () -> art.sprites("s3k:ship"));
    }

    @Test void failedLookupStatusIsFiniteAndClassifiesOptionalUnsupportedAndInvalid() throws Exception {
        Fixture f = new Fixture();
        Art art = f.art();
        assertNull(art.sprites("s3k:unknown"));
        assertEquals("UNSUPPORTED", field(status(art).getLast(), "kind"));
        f.failure = new IllegalArgumentException("x".repeat(1000));
        assertNull(art.sprites("s3k:ring"));
        assertEquals("INVALID_REQUEST", field(status(art).getLast(), "kind"));
        for (int i = 0; i < 200; i++) art.sprites("s3k:unknown:" + i + ":" + "x".repeat(1000));
        assertTrue(status(art).size() <= 64);
        for (Object entry : status(art)) {
            assertTrue(field(entry, "request").length() <= 256);
            assertTrue(field(entry, "cause").length() <= 256);
        }
        Fixture absent = new Fixture();
        absent.available = false;
        Art missing = absent.art();
        assertNull(missing.sprites("s3k:ring"));
        assertEquals("OPTIONAL_ROM_MISSING", field(status(missing).getLast(), "kind"));
    }

    private static List<?> status(Art art) throws Exception {
        return (List<?>) Art.class.getMethod("failures").invoke(art);
    }
    private static String field(Object entry, String name) throws Exception {
        return entry.getClass().getMethod(name).invoke(entry).toString();
    }

    static final class Fixture {
        long ticks;
        boolean available = true, kitAvailable = true;
        RuntimeException failure;
        RuntimeException kitFailure;
        int spriteLoads, paletteLoads, paletteFailureAt = -1;
        int[] lastPalette;
        final SceneImage image = new SceneImage(1, 1, new int[] {0xFF123456});
        final SceneSpriteSet set = new SceneSpriteSet() {
            public int frameCount() { return 32; }
            public SceneSprite frame(int index) { return new SceneSprite(image, 0, 0); }
            public int[] animationFrames(int id) { return new int[] {0}; }
            public int animationDelay(int id) { return 8; }
        };
        final SceneLevelKit kit = (SceneLevelKit) Proxy.newProxyInstance(SceneLevelKit.class.getClassLoader(),
                new Class<?>[] {SceneLevelKit.class}, (proxy, method, args) -> {
                    if (method.getName().equals("palette")) { int[] palette = new int[64]; Arrays.fill(palette, 0xFF123456); return palette; }
                    throw new UnsupportedOperationException(method.getName());
                });
        final SceneRomArt rom = (SceneRomArt) Proxy.newProxyInstance(SceneRomArt.class.getClassLoader(),
                new Class<?>[] {SceneRomArt.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "gameId" -> "s3k";
                    case "romSha1" -> "cfbf98c36c776677290a872547ac47c53d2761d6";
                    case "hasLevelKit" -> kitAvailable;
                    case "levelKit" -> { if (kitFailure != null) throw kitFailure; yield kit; }
                    case "character", "characterAccessory" -> set;
                    case "palette" -> { paletteLoads++; if (paletteLoads == paletteFailureAt) throw new IllegalArgumentException("partial palette"); int[] palette = new int[(int) args[1]]; Arrays.fill(palette, 0xFF123456); yield palette; }
                    case "sprites" -> { spriteLoads++; if (failure != null) throw failure; lastPalette = ((int[]) args[1]).clone(); yield set; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Art art() { return new Art(driver, () -> ticks, "fixture-owner"); }
        final SceneArt driver = new SceneArt() {
            public SceneImage png(byte[] data) { return image; }
            public SceneRomArt rom() { return available ? rom : null; }
            public SceneRomArt rom(String game) { return available ? rom : null; }
            public List<String> availableGames() { return available ? List.of("s1", "s2", "s3k") : List.of(); }
        };
    }
}

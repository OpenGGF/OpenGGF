package starpost.art;

import com.openggf.mods.scene.*;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import starpost.festivals.FestivalArt;
import starpost.ruins.Badnik;
import starpost.ruins.RuinsArt;
import starpost.scene.Music;
import starpost.scene.StarpostScene;
import static org.junit.jupiter.api.Assertions.*;

/** Original fixture pixels test failure recovery; canonical ROM pixels remain an engine check. */
class ArtRetryTest {
    @Test void ruinsFailedBankRecoversAndRepeatedMissingMissileIsBounded() throws Exception {
        Fixture f = new Fixture();
        RuinsArt ruins = ruins(f.art(), f);
        f.failure = new IllegalArgumentException("damaged sprite");
        assertNull(ruins.badnik(Badnik.BUZZ_BOMBER, 0));
        int first = f.spriteLoads;
        for (int i = 0; i < 20; i++) assertNull(ruins.badnik(Badnik.BUZZ_BOMBER, 0));
        assertEquals(first, f.spriteLoads);
        f.failure = null; f.ticks = 120;
        assertNotNull(ruins.badnik(Badnik.BUZZ_BOMBER, 0));
        assertEquals(first + 1, f.spriteLoads);
        f.failure = new IllegalArgumentException("missile");
        assertNull(ruins.missile(0));
        int missile = f.spriteLoads;
        for (int i = 0; i < 20; i++) assertNull(ruins.missile(0));
        assertEquals(missile, f.spriteLoads, "null must not decode on every draw");
    }

    @Test void festivalKitAndCompleteSlotBatchRecoverAfterTheOwnerDeadline() throws Exception {
        Fixture f = new Fixture();
        FestivalArt festival = festival(f.art(), f);
        f.kitAvailable = false;
        assertNull(festival.starLight());
        f.kitAvailable = true;
        f.ticks = 119; assertNull(festival.starLight());
        f.ticks = 120; assertNotNull(festival.starLight());
        f.tileFailureAt = f.tileLoads + 3;
        f.alwaysS3TilesFail = true;
        assertNull(festival.slotFace(0));
        int failed = f.tileLoads;
        for (int i = 0; i < 20; i++) assertNull(festival.slotFace(i % 6));
        assertEquals(failed, f.tileLoads);
        f.tileFailureAt = -1; f.alwaysS3TilesFail = false; f.ticks = 240;
        for (int i = 0; i < 6; i++) assertNotNull(festival.slotFace(i));
        assertTrue(festival.casinoNight());
    }

    @Test void programmingFaultsAreNotClassifiedAsMissingPicturesOrMusic() throws Exception {
        Fixture f = new Fixture();
        RuinsArt ruins = ruins(f.art(), f);
        FestivalArt festival = festival(f.art(), f);
        f.failure = new NullPointerException("decoder bug");
        assertThrows(NullPointerException.class, () -> ruins.missile(0));
        assertThrows(NullPointerException.class, festival::bumper);
        f.audioFailure = new NullPointerException("audio bug");
        Music music = new Music(f.context()); music.want("s1", Music.S1_GHZ);
        assertThrows(NullPointerException.class, music::update);
        f.kitFailure = new NullPointerException("scene preparation bug");
        assertThrows(NullPointerException.class, () -> new StarpostScene().enter(f.context()));
    }

    @Test void providerOwnershipStateIsNotExpectedInvalidData() throws Exception {
        Fixture f = new Fixture(); RuinsArt ruins = ruins(f.art(), f);
        f.failure = new IllegalStateException("fixture provider owner is retired");
        assertThrows(IllegalStateException.class, () -> ruins.missile(0));
        f.audioFailure = new IllegalStateException("fixture audio owner is retired");
        Music music = new Music(f.context()); music.want("s1", Music.S1_GHZ);
        assertThrows(IllegalStateException.class, music::update);
    }

    @Test void documentedIoWrapperRetriesAndAuthoredMetadataValidationIsExplicit() throws Exception {
        Fixture f = new Fixture(); RuinsArt ruins = ruins(f.art(), f);
        f.failure = new IllegalStateException("ROM unavailable", new java.io.IOException("fixture I/O"));
        assertNull(ruins.missile(0));
        f.failure = null; f.ticks = 120;
        assertNotNull(ruins.missile(0));
        Fixture invalid = new Fixture(); invalid.blockSize = 128;
        StarpostScene scene = new StarpostScene(); scene.enter(invalid.context());
        assertEquals(AssetFailures.Kind.INVALID_DATA, scene.failures().getFirst().kind());
    }

    @Test void unavailableMusicRetriesWithoutPublishingAPermanentPlayingTrack() {
        Fixture f = new Fixture(); f.musicAvailable = false;
        Music music = new Music(f.context()); music.want("s1", Music.S1_GHZ);
        music.update();
        for (int i = 0; i < 20; i++) music.update();
        assertEquals(1, f.musicLoads);
        f.musicAvailable = true; f.ticks = 119; music.update();
        assertEquals(1, f.musicLoads);
        f.ticks = 120; music.update();
        assertEquals(2, f.musicLoads);
        music.update(); assertEquals(2, f.musicLoads);
    }

    @Test void failedMusicSwitchKeepsThePreviousSongOwnedUntilItIsReleased() {
        Fixture f = new Fixture(); Music music = new Music(f.context());
        music.want("s1", Music.S1_GHZ); music.update();
        f.audioFailure = new IllegalArgumentException("fixture unavailable switch");
        music.want("s1", Music.S1_MZ); music.update();
        music.parkForAct();
        assertEquals(1, f.musicStops, "the prior successfully started song still needs release");
        f.audioFailure = null; f.ticks = 120; music.update();
        assertEquals(3, f.musicLoads, "the failed switch remains retryable");
        music.close(); assertEquals(2, f.musicStops);
    }

    @Test void mechaPaletteIsPublishedOnlyAfterAllThreeRangesLoad() throws Exception {
        Fixture f = new Fixture(); FestivalArt festival = festival(f.art(), f);
        f.paletteFailureAt = f.paletteLoads + 2;
        int before = f.spriteLoads;
        assertNull(festival.mechaSonic());
        assertEquals(before, f.spriteLoads, "partial palette cannot become sprite authority");
        f.paletteFailureAt = -1; f.ticks = 120;
        assertNotNull(festival.mechaSonic());
        assertEquals(before + 1, f.spriteLoads);
        assertEquals(0xFF123456, f.lastPalette[1]);
        assertEquals(0xFF123456, f.lastPalette[17]);
        assertEquals(0xFF123456, f.lastPalette[49]);
    }

    @Test void failedStatusIsBoundedAndClosedArtCannotDecodeAgain() throws Exception {
        Fixture f = new Fixture(); Art art = f.art(); RuinsArt ruins = ruins(art, f);
        f.kitFailure = new IllegalArgumentException("x".repeat(1000));
        for (int i = 0; i < 100; i++) assertNull(ruins.romKit(i, 0));
        List<?> failures = (List<?>) RuinsArt.class.getMethod("failures").invoke(ruins);
        assertTrue(failures.size() <= 64);
        for (Object entry : failures) {
            assertTrue(entry.getClass().getMethod("request").invoke(entry).toString().length() <= 256);
            assertTrue(entry.getClass().getMethod("cause").invoke(entry).toString().length() <= 256);
            assertEquals("starpost-valley", entry.getClass().getMethod("owner").invoke(entry));
        }
        Art.class.getMethod("close").invoke(art);
        int before = f.spriteLoads;
        assertThrows(IllegalStateException.class, () -> ruins.missile(0));
        assertEquals(before, f.spriteLoads);
    }

    private static RuinsArt ruins(Art art, Fixture f) throws Exception {
        try { return RuinsArt.class.getConstructor(Art.class, LongSupplier.class).newInstance(art, (LongSupplier) () -> f.ticks); }
        catch (NoSuchMethodException old) { return new RuinsArt(art); }
    }
    private static FestivalArt festival(Art art, Fixture f) throws Exception {
        try { return FestivalArt.class.getConstructor(Art.class, SceneRomArt.class, LongSupplier.class).newInstance(art, f.s2, (LongSupplier) () -> f.ticks); }
        catch (NoSuchMethodException old) { return new FestivalArt(art, f.s2); }
    }

    static final class Fixture {
        long ticks;
        int spriteLoads, tileLoads, musicLoads, musicStops, paletteLoads, tileFailureAt = -1, paletteFailureAt = -1;
        int[] lastPalette;
        int blockSize = 256;
        boolean kitAvailable = true, musicAvailable = true, alwaysS3TilesFail;
        RuntimeException failure, kitFailure, audioFailure;
        final SceneImage image = picture(256, 256);
        final SceneBackdrop backdrop = new SceneBackdrop(image, List.of(new SceneBackdrop.Band(0, 256, 0, 0)));
        final SceneSpriteSet set = new SceneSpriteSet() {
            public int frameCount() { return 64; }
            public SceneSprite frame(int index) { return new SceneSprite(image, 0, 0); }
            public int[] animationFrames(int id) { return new int[] {0}; }
            public int animationDelay(int id) { return 8; }
        };
        final SceneLevelKit kit = (SceneLevelKit) Proxy.newProxyInstance(SceneLevelKit.class.getClassLoader(), new Class<?>[] {SceneLevelKit.class},
            (p, m, a) -> switch (m.getName()) {
                case "blockSize" -> blockSize;
                case "blockCount" -> 64;
                case "columns", "rows" -> 1;
                case "block" -> 60;
                case "blockImage" -> image;
                case "blockSolidity" -> new byte[256 * 256];
                case "backdrop" -> backdrop;
                case "palette" -> palette(64);
                case "playableArea" -> new int[] {0, 0, 256, 256};
                default -> throw new UnsupportedOperationException(m.getName());
            });
        final SceneRomArt s1 = rom("s1"), s3k = rom("s3k"), s2 = rom("s2");
        Art art() { return new Art(s1, s3k, () -> ticks, "starpost-valley"); }
        SceneRomArt rom(String game) {
            return (SceneRomArt) Proxy.newProxyInstance(SceneRomArt.class.getClassLoader(), new Class<?>[] {SceneRomArt.class},
                (p, m, a) -> switch (m.getName()) {
                    case "gameId" -> game;
                    // Recipe admission is separate from these original test pixels.
                    case "romSha1" -> game.equals("s3k") ? "cfbf98c36c776677290a872547ac47c53d2761d6" : "fixture:" + game;
                    case "levelKit" -> { if (kitFailure != null) throw kitFailure; yield kitAvailable ? kit : null; }
                    case "palette" -> { paletteLoads++; if (paletteLoads == paletteFailureAt) throw new IllegalArgumentException("palette range"); yield palette((int) a[1]); }
                    case "characterPalette" -> palette(16);
                    case "sprites" -> { spriteLoads++; if (failure != null) throw failure; lastPalette = ((int[]) a[1]).clone(); yield set; }
                    case "character", "characterAccessory" -> set;
                    case "hasTitleCard" -> false;
                    case "read" -> { byte[] bytes = new byte[(int) a[1]]; if (bytes.length == 4) bytes[1] = 1; yield bytes; }
                    case "tiles" -> { tileLoads++; if (tileLoads == tileFailureAt || alwaysS3TilesFail && game.equals("s3k")) throw new IllegalArgumentException("tile decode"); yield picture((int) a[3] * 8, (int) a[4] * 8); }
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        }
        SceneContext context() {
            SceneArt source = (SceneArt) Proxy.newProxyInstance(SceneArt.class.getClassLoader(), new Class<?>[] {SceneArt.class},
                (p, m, a) -> m.getName().equals("rom") ? (a == null || a.length == 0 ? s3k : a[0].equals("s1") ? s1 : s2) : null);
            SceneAudio audio = (SceneAudio) Proxy.newProxyInstance(SceneAudio.class.getClassLoader(), new Class<?>[] {SceneAudio.class},
                (p, m, a) -> { if (m.getName().equals("playMusic")) { musicLoads++; if (audioFailure != null) throw audioFailure; return m.getReturnType() == boolean.class ? musicAvailable : null; } if (m.getName().equals("stopMusic")) musicStops++; return null; });
            return (SceneContext) Proxy.newProxyInstance(SceneContext.class.getClassLoader(), new Class<?>[] {SceneContext.class},
                (p, m, a) -> switch (m.getName()) { case "ticks" -> ticks; case "ownerModId" -> "starpost-valley"; case "art" -> source; case "audio" -> audio; default -> throw new UnsupportedOperationException(m.getName()); });
        }
        static int[] palette(int count) { int[] result = new int[count]; Arrays.fill(result, 0xFF123456); return result; }
        static SceneImage picture(int w, int h) { int[] pixels = new int[w * h]; Arrays.fill(pixels, 0xFF123456); return new SceneImage(w, h, pixels); }
    }
}

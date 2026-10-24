package com.openggf.audio;

import com.openggf.game.audio.RomSoundCatalog;

import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

class TestRomSoundCatalog {
    // MusID__First through MusID__End (s2.constants.asm:832-864).
    @ParameterizedTest(name = "S2 native {0} maps to engine {1} ({2})")
    @CsvSource({
            "0x81, 0x92, RESULTS_2P",
            "0x82, 0x81, EMERALD_HILL",
            "0x83, 0x85, MYSTIC_CAVE_2P",
            "0x84, 0x8F, OIL_OCEAN",
            "0x85, 0x82, METROPOLIS",
            "0x86, 0x94, HILL_TOP",
            "0x87, 0x86, AQUATIC_RUIN",
            "0x88, 0x80, CASINO_NIGHT_2P",
            "0x89, 0x83, CASINO_NIGHT",
            "0x8A, 0x87, DEATH_EGG",
            "0x8B, 0x84, MYSTIC_CAVE",
            "0x8C, 0x91, EMERALD_HILL_2P",
            "0x8D, 0x8E, SKY_CHASE",
            "0x8E, 0x8C, CHEMICAL_PLANT",
            "0x8F, 0x90, WING_FORTRESS",
            "0x90, 0x9B, HIDDEN_PALACE",
            "0x91, 0x89, OPTIONS",
            "0x92, 0x88, SPECIAL_STAGE",
            "0x93, 0x8D, BOSS",
            "0x94, 0x8B, FINAL_BOSS",
            "0x95, 0x8A, ENDING",
            "0x96, 0x93, SUPER_SONIC",
            "0x97, 0x99, INVINCIBILITY",
            "0x98, 0x98, EXTRA_LIFE",
            "0x99, 0x96, TITLE",
            "0x9A, 0x97, ACT_CLEAR",
            "0x9B, 0xB8, GAME_OVER",
            "0x9C, 0x9C, CONTINUE",
            "0x9D, 0xBA, GOT_EMERALD",
            "0x9E, 0xBD, CREDITS",
            "0x9F, 0xDC, UNDERWATER"
    })
    void sonic2UsesEveryNativeMusicIdWithoutChangingEngineIds(
            int nativeId, int engineId, String name) {
        var entry = RomSoundCatalog.music("s2", nativeId);
        assertEquals(new RomSoundCatalog.Entry(name, Sonic2Music.valueOf(name).displayName,
                nativeId, engineId), entry);
        assertEquals(nativeId, Sonic2Music.valueOf(name).nativeId);
        assertEquals(engineId, Sonic2Music.valueOf(name).id);
        assertSame(Sonic2Music.valueOf(name), Sonic2Music.fromId(engineId));
        assertEquals(entry.title(), Sonic2Music.titleMap().get(engineId));
        assertEquals(nativeId, RomSoundCatalog.nativeMusicId("s2", engineId));
    }

    @Test
    void sonic1MusicUsesIdentityIdsAndExistingNames() {
        assertEquals(19, RomSoundCatalog.music("s1").size());
        for (var music : Sonic1Music.values()) {
            assertEquals(new RomSoundCatalog.Entry(music.name(), music.displayName,
                    music.id, music.id), RomSoundCatalog.music("s1", music.id));
            assertEquals(music.id, RomSoundCatalog.nativeMusicId("s1", music.id));
        }
    }

    @Test
    void sonic3kCreditsUsesNativeDcAndEngine33WhileRingRemainsSfx33() {
        assertEquals(51, RomSoundCatalog.music("s3k").size());
        for (int nativeId = 1; nativeId <= 0x32; nativeId++) {
            var music = Sonic3kMusic.fromId(nativeId);
            String title = nativeId == 0x2E ? "Mini-Boss (S&K)" : music.displayName;
            assertEquals(new RomSoundCatalog.Entry(music.name(), title,
                    nativeId, nativeId), RomSoundCatalog.music("s3k", nativeId));
            assertEquals(nativeId, RomSoundCatalog.nativeMusicId("s3k", nativeId));
        }
        assertEquals(new RomSoundCatalog.Entry("CREDITS_SK", "Credits (S&K)", 0xDC, 0x33),
                RomSoundCatalog.music("s3k", 0xDC));
        assertEquals(0xDC, RomSoundCatalog.nativeMusicId("s3k", 0x33));
        assertEquals(new RomSoundCatalog.Entry("RING_RIGHT", "Ring (Right)", 0x33, 0x33),
                RomSoundCatalog.sfx("s3k", 0x33));
        assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.music("s3k", 0x33));
        assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.sfx("s3k", 0xDC));
    }

    @Test
    void sonic3SlotDescribesTheArrangementUsedByTheLockedOnDriver() {
        var entry = RomSoundCatalog.music("s3k", 0x2E);
        assertEquals("MINIBOSS_S3", entry.name(), "The stable key identifies the native S3 slot");
        assertEquals("Mini-Boss (S&K)", entry.title(), "The locked-on driver uses its S&K arrangement");
    }

    @Test
    void sonic3DriverVariantsAreNotNativeRequests() {
        for (var music : Sonic3kMusic.values()) {
            if (music.id >= Sonic3kMusic.S3_MUSIC_ID_BASE) {
                assertThrows(IllegalArgumentException.class,
                        () -> RomSoundCatalog.music("s3k", music.id), music.name());
                assertThrows(IllegalArgumentException.class,
                        () -> RomSoundCatalog.nativeMusicId("s3k", music.id), music.name());
                assertThrows(IllegalArgumentException.class,
                        () -> RomSoundCatalog.music("s3k", music.name()), music.name());
                assertThrows(IllegalArgumentException.class,
                        () -> RomSoundCatalog.music("s3k", music.displayName), music.name());
            }
        }
    }

    @Test
    void allSfxKeepNativeIdentityAndEnumNamesIncludingSpecialAndUnusedEntries() {
        assertEquals(49, RomSoundCatalog.sfx("s1").size());
        for (var sfx : Sonic1Sfx.values()) {
            assertEquals(new RomSoundCatalog.Entry(sfx.name(), sfx.displayName, sfx.id, sfx.id),
                    RomSoundCatalog.sfx("s1", sfx.id));
        }
        assertEquals("WATERFALL", RomSoundCatalog.sfx("s1", 0xD0).name());
        assertEquals(81, RomSoundCatalog.sfx("s2").size());
        for (var sfx : Sonic2Sfx.values()) {
            assertEquals(new RomSoundCatalog.Entry(sfx.name(), sfx.displayName, sfx.id, sfx.id),
                    RomSoundCatalog.sfx("s2", sfx.id));
        }
        assertEquals("UNUSED_B8", RomSoundCatalog.sfx("s2", 0xB8).name());
        assertEquals("UNUSED_D1", RomSoundCatalog.sfx("s2", 0xD1).name());
        assertEquals("UNUSED_D2", RomSoundCatalog.sfx("s2", 0xD2).name());
        assertEquals(169, RomSoundCatalog.sfx("s3k").size());
        for (var sfx : Sonic3kSfx.values()) {
            assertEquals(new RomSoundCatalog.Entry(sfx.name(), sfx.displayName, sfx.id, sfx.id),
                    RomSoundCatalog.sfx("s3k", sfx.id));
        }
    }

    @Test
    void musicAndSfxKeepSeparateNamespacesEvenWhenEngineIdsCoincide() {
        assertEquals("GAME_OVER", RomSoundCatalog.music("s2", 0x9B).name());
        assertEquals(0xB8, RomSoundCatalog.music("s2", 0x9B).engineId());
        assertEquals("UNUSED_B8", RomSoundCatalog.sfx("s2", 0xB8).name());
        assertEquals("UNDERWATER", RomSoundCatalog.music("s2", 0x9F).name());
        assertEquals(0xDC, RomSoundCatalog.music("s2", 0x9F).engineId());
        assertEquals("FIRE", RomSoundCatalog.sfx("s2", 0xDC).name());
        assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.music("s2", 0xB8));
        assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.music("s2", 0xDC));
        assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.sfx("s2", 0x9B));
        assertNotEquals(RomSoundCatalog.music("s1", 0x81), RomSoundCatalog.music("s2", 0x81));
    }

    @ParameterizedTest
    @ValueSource(strings = {"s1", "s2", "s3k"})
    void catalogsAreImmutableSortedAndUniqueByNativeIdEngineIdAndStableKey(String game) {
        if (game.equals("s2")) {
            assertEquals(31, RomSoundCatalog.music(game).size());
        }
        for (var entries : List.of(RomSoundCatalog.music(game), RomSoundCatalog.sfx(game))) {
            var names = new HashSet<String>();
            var nativeIds = new HashSet<Integer>();
            var engineIds = new HashSet<Integer>();
            int previousId = -1;
            for (var entry : entries) {
                assertTrue(entry.soundId() > previousId, entry.toString());
                assertTrue(names.add(entry.name()), entry.name());
                assertTrue(nativeIds.add(entry.soundId()), entry.toString());
                assertTrue(engineIds.add(entry.engineId()), entry.toString());
                assertFalse(entry.title().isBlank(), entry.toString());
                previousId = entry.soundId();
            }
            assertThrows(UnsupportedOperationException.class, () -> entries.removeFirst());
            assertThrows(UnsupportedOperationException.class, () -> entries.add(entries.getFirst()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"s1", "s2", "s3k"})
    void musicNamesAcceptEveryStableKeyAndDisplayTitleCaseInsensitively(String game) {
        for (var entry : RomSoundCatalog.music(game)) {
            assertSame(entry, RomSoundCatalog.music(game, entry.name()));
            assertSame(entry, RomSoundCatalog.music(game, entry.name().toLowerCase(Locale.ROOT)));
            assertSame(entry, RomSoundCatalog.music(game, entry.title()));
            assertSame(entry, RomSoundCatalog.music(game, entry.title().toUpperCase(Locale.ROOT)));
        }
        for (String name : new String[]{null, "", "NOT_A_SONG", "JUMP"}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> RomSoundCatalog.music(game, name));
            assertTrue(error.getMessage().contains(game));
            assertTrue(error.getMessage().contains("music"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"s1", "s2", "s3k"})
    void rejectsAllWrongKindSystemAndUnknownNativeBytesWithUsefulErrors(String game) {
        IntPredicate isMusic = switch (game) {
            case "s1" -> id -> id >= 0x81 && id <= 0x93;
            case "s2" -> id -> id >= 0x81 && id <= 0x9F;
            default -> id -> (id >= 1 && id <= 0x32) || id == 0xDC;
        };
        IntPredicate isSfx = switch (game) {
            case "s1" -> id -> id >= 0xA0 && id <= 0xD0;
            case "s2" -> id -> id >= 0xA0 && id <= 0xF0;
            default -> id -> id >= 0x33 && id <= 0xDB;
        };
        for (int id = 0; id <= 0xFF; id++) {
            int nativeId = id;
            if (isMusic.test(nativeId)) {
                assertEquals(nativeId, RomSoundCatalog.music(game, nativeId).soundId());
            } else {
                var error = assertThrows(IllegalArgumentException.class,
                        () -> RomSoundCatalog.music(game, nativeId));
                assertInvalidIdMessage(error, game, "music", nativeId);
            }
            if (isSfx.test(nativeId)) {
                assertEquals(nativeId, RomSoundCatalog.sfx(game, nativeId).soundId());
            } else {
                var error = assertThrows(IllegalArgumentException.class,
                        () -> RomSoundCatalog.sfx(game, nativeId));
                assertInvalidIdMessage(error, game, "SFX", nativeId);
            }
        }
        for (int id : new int[]{-1, 0x100, 0x1000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.music(game, id));
            assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.sfx(game, id));
            assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.nativeMusicId(game, id));
        }
        assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.nativeMusicId(game, 0));
        assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.nativeMusicId(game, 0xFF));
        assertThrows(IllegalArgumentException.class, () -> RomSoundCatalog.nativeMusicId("s2", 0x95));
    }

    @Test
    void unknownGamesFailClearlyAtEveryEntryPoint() {
        for (String game : new String[]{null, "", "s3", "other"}) {
            assertAll(
                    () -> assertUnknownGame(assertThrows(IllegalArgumentException.class,
                            () -> RomSoundCatalog.music(game))),
                    () -> assertUnknownGame(assertThrows(IllegalArgumentException.class,
                            () -> RomSoundCatalog.sfx(game))),
                    () -> assertUnknownGame(assertThrows(IllegalArgumentException.class,
                            () -> RomSoundCatalog.music(game, 0x81))),
                    () -> assertUnknownGame(assertThrows(IllegalArgumentException.class,
                            () -> RomSoundCatalog.sfx(game, 0xA0))),
                    () -> assertUnknownGame(assertThrows(IllegalArgumentException.class,
                            () -> RomSoundCatalog.music(game, "TITLE"))),
                    () -> assertUnknownGame(assertThrows(IllegalArgumentException.class,
                            () -> RomSoundCatalog.nativeMusicId(game, 0x81))));
        }
    }

    private static void assertInvalidIdMessage(
            IllegalArgumentException error, String game, String kind, int id) {
        assertTrue(error.getMessage().contains(game), error.getMessage());
        assertTrue(error.getMessage().contains(kind), error.getMessage());
        assertTrue(error.getMessage().contains(String.format(Locale.ROOT, "$%02X", id)),
                error.getMessage());
    }

    private static void assertUnknownGame(IllegalArgumentException error) {
        assertTrue(error.getMessage().contains("game"), error.getMessage());
    }
}

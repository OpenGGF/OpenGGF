package com.openggf.game.sonic2.competition.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionCheckpoints.*;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic2CompetitionCheckpoints {
    @Test
    void initialSeedsProvideIndependentRespawnPositionsBeforeEitherPlayerHitsAStarpost() {
        var bank = seeded();

        assertEquals(new SavedPlayer(0, 96, 655, 0x03C0, 0x0C0D, (byte) 0,
                0, 0, 0, null), bank.saved(0));
        assertEquals(new SavedPlayer(0, 64, 659, 0x03D0, 0x0C0D, (byte) 0,
                0, 0, 0, null), bank.saved(1));
        assertFalse(bank.canActivate(0, 0));
        assertFalse(bank.canActivate(1, 0x80), "A sign bit alone is not a later checkpoint");
    }

    @Test
    void eachHumanCanActivateTheSamePostAndMaskedOrderRetainsTheFullSubtypeByte() {
        var bank = seeded();
        save(bank, 0, 0x81, null);

        assertEquals(0x81, bank.saved(0).lastStarPost());
        assertFalse(bank.canActivate(0, 1));
        assertTrue(bank.canActivate(1, 0x81), "P1's activation cannot block P2");
        save(bank, 1, 0x81, null);
        assertEquals(0x81, bank.saved(1).lastStarPost());
        assertTrue(bank.canActivate(0, 2), "Compare seven-bit indices, not unsigned subtype bytes");
        save(bank, 0, 2, null);
        SavedPlayer latest = bank.saved(0);
        bank.save(0, 0x81, 999, 999, 0, 0, (byte) 0, 0, 0, 0, null);
        assertEquals(latest, bank.saved(0), "An earlier post cannot overwrite the respawn bank");
        bank.save(0, 0x82, 999, 999, 0, 0, (byte) 0, 0, 0, 0, null);
        assertEquals(latest, bank.saved(0), "Changing only the sign bit cannot reactivate the same post");
        assertEquals(0x81, bank.saved(1).lastStarPost());
    }

    @Test
    void snapshotRestoresEverySavedPlayerAndPrimaryEnvironmentField() {
        var source = seeded();
        var environment = new PrimaryEnvironment(4, 0x720, 0x1234, 0x567,
                0x1357, 0x246, 0x3579, 0x468, 0x579B, 0x68A,
                0x345, 6, 0x80);
        source.save(0, 0x83, 0x2300, 0x456, 0xA3C0, 0x0E0F, (byte) 1,
                153, 6, 12345, environment);
        source.save(1, 2, 0x2200, 0x567, 0x83D0, 0x0C0D, (byte) 0,
                77, 2, 23456, null);
        List<SavedPlayer> snapshot = source.capture();

        source.clear();
        var replacement = new Sonic2CompetitionCheckpoints();
        replacement.restore(snapshot);

        assertEquals(new SavedPlayer(0x83, 0x2300, 0x456, 0xA3C0, 0x0E0F,
                (byte) 1, 153, 6, 12345, new PrimaryEnvironment(4, 0x720,
                0x1234, 0x567, 0x1357, 0x246, 0x3579, 0x468, 0x579B, 0x68A,
                0x345, 6, 0x80)), replacement.saved(0));
        assertEquals(new SavedPlayer(2, 0x2200, 0x567, 0x83D0, 0x0C0D,
                (byte) 0, 77, 2, 23456, null), replacement.saved(1));
        assertTrue(replacement.canActivate(0, 4));
        assertFalse(replacement.canActivate(1, 0x82));
    }

    @Test
    void savedValuesRetainNativeByteAndWordWidthsWithoutGameplayClamping() {
        var bank = seeded();
        var environment = new PrimaryEnvironment(0x104, -1, 0x12345, -2,
                0x10001, 0x10002, 0x10003, 0x10004, 0x10005, 0x10006,
                0x12345, 0x106, 0x180);
        bank.save(0, 0x181, 0x12345, -2, 0x183C0, 0x10E0F, (byte) 0xFF,
                0x1FFFF, 0x1AB, 35999, environment);

        SavedPlayer saved = bank.saved(0);
        assertEquals(0x81, saved.lastStarPost());
        assertEquals(0x2345, saved.x());
        assertEquals(0xFFFE, saved.y());
        assertEquals(0x83C0, saved.artTile());
        assertEquals(0x0E0F, saved.solidBits());
        assertEquals((byte) 0xFF, saved.layer());
        assertEquals(0xFFFF, saved.rings(), "The bank captures RAM, not a HUD ring clamp");
        assertEquals(0xAB, saved.extraLifeFlags());
        assertEquals(new PrimaryEnvironment(4, 0xFFFF, 0x2345, 0xFFFE,
                1, 2, 3, 4, 5, 6, 0x2345, 6, 0x80), saved.primaryEnvironment());
        SavedPlayer before = bank.saved(0);
        assertThrows(IllegalArgumentException.class,
                () -> bank.save(0, 2, 0, 0, 0, 0, (byte) 0, 0, 0, -1, null));
        assertEquals(before, bank.saved(0));
    }

    @Test
    void playerTwoCannotAcquirePrimaryEnvironmentThroughSaveOrRestore() {
        var bank = seeded();
        var environment = new PrimaryEnvironment(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
        List<SavedPlayer> before = bank.capture();

        assertThrows(IllegalArgumentException.class, () -> save(bank, 1, 1, environment));
        assertEquals(before, bank.capture());
        SavedPlayer invalidP2 = new SavedPlayer(1, 2, 3, 4, 5, (byte) 0, 6, 7, 8, environment);
        assertThrows(IllegalArgumentException.class, () -> bank.restore(Arrays.asList(null, invalidP2)));
        assertEquals(before, bank.capture(), "Validate both slots before replacing either bank");
    }

    @Test
    void captureAndRestoreNeverAliasTheMutableContainer() {
        var bank = seeded();
        List<SavedPlayer> captured = bank.capture();
        assertThrows(UnsupportedOperationException.class, () -> captured.set(0, null));
        save(bank, 0, 1, null);
        assertEquals(0, captured.get(0).lastStarPost(), "A captured bank survives later activation");

        var supplied = new ArrayList<>(captured);
        bank.restore(supplied);
        supplied.set(0, null);
        supplied.clear();
        assertEquals(96, bank.saved(0).x());
        assertEquals(64, bank.saved(1).x());
    }

    @Test
    void clearAndPartialSeedSnapshotsRetainWhichSlotsHaveAValidInitialRespawnBank() {
        var bank = new Sonic2CompetitionCheckpoints();
        List<SavedPlayer> empty = bank.capture();
        assertEquals(Arrays.asList(null, null), empty);
        assertThrows(IllegalStateException.class, () -> bank.saved(0));
        assertThrows(IllegalStateException.class, () -> bank.canActivate(1, 1));
        assertThrows(IllegalStateException.class, () -> save(bank, 0, 1, null));
        bank.seed(0, 96, 655, 0x03C0, 0x0C0D, (byte) 0);
        List<SavedPlayer> partiallySeeded = bank.capture();
        assertNull(partiallySeeded.get(1));
        bank.clear();
        bank.restore(partiallySeeded);
        assertEquals(96, bank.saved(0).x());
        assertThrows(IllegalStateException.class, () -> bank.saved(1));
        bank.restore(empty);
        assertThrows(IllegalStateException.class, () -> bank.saved(0));
    }

    @Test
    void malformedSnapshotShapesAndInvalidSlotsCannotChangeExistingBanks() {
        var bank = seeded();
        List<SavedPlayer> before = bank.capture();
        assertThrows(NullPointerException.class, () -> bank.restore(null));
        assertThrows(IllegalArgumentException.class, () -> bank.restore(List.of()));
        assertThrows(IllegalArgumentException.class, () -> bank.restore(List.of(bank.saved(0))));
        assertThrows(IllegalArgumentException.class,
                () -> bank.restore(Arrays.asList(bank.saved(0), bank.saved(1), null)));
        assertThrows(IndexOutOfBoundsException.class, () -> bank.seed(2, 0, 0, 0, 0, (byte) 0));
        assertThrows(IndexOutOfBoundsException.class, () -> bank.saved(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> bank.canActivate(2, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> save(bank, -1, 1, null));
        assertEquals(before, bank.capture());
    }

    private static Sonic2CompetitionCheckpoints seeded() {
        var bank = new Sonic2CompetitionCheckpoints();
        bank.seed(0, 96, 655, 0x03C0, 0x0C0D, (byte) 0);
        bank.seed(1, 64, 659, 0x03D0, 0x0C0D, (byte) 0);
        return bank;
    }

    private static void save(Sonic2CompetitionCheckpoints bank, int slot, int subtype,
                             PrimaryEnvironment environment) {
        bank.save(slot, subtype, 800, 600, slot == 0 ? 0x03C0 : 0x03D0, 0x0C0D,
                (byte) 0, 42, 2, 1234, environment);
    }
}

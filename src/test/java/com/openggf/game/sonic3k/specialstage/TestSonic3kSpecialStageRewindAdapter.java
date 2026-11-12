package com.openggf.game.sonic3k.specialstage;

import com.openggf.game.SpecialStageProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSonic3kSpecialStageRewindAdapter {
    @Test
    void snapshotsOwnBothCharacterPalettesIndependently() {
        var leader = new com.openggf.level.Palette();
        var follower = new com.openggf.level.Palette();
        leader.getColor(4).r = 36; follower.getColor(4).b = 109;
        var snapshot = new Sonic3kSpecialStageSnapshot.PaletteSnapshot(
                new com.openggf.level.Palette[4], null, false, leader, follower);
        leader.getColor(4).r = 0; follower.getColor(4).b = 0;
        assertEquals(36, snapshot.characterPalette().getColor(4).r);
        assertEquals(109, snapshot.tailsPalette().getColor(4).b);
        assertEquals(0, snapshot.characterPalette().getColor(4).b);
        assertEquals(0, snapshot.tailsPalette().getColor(4).r);
    }
    @Test
    void adapterUsesGenericSpecialStageKeyAndKeepsMissingSnapshotDefault() {
        Sonic3kSpecialStageRewindAdapter adapter =
                new Sonic3kSpecialStageRewindAdapter(new Sonic3kSpecialStageManager());

        assertEquals(SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY, adapter.key());
        assertThrows(IllegalStateException.class, adapter::resetForMissingSnapshot);
    }

    @Test
    void captureAndRestoreFailBeforeManagerIsInitialized() {
        Sonic3kSpecialStageRewindAdapter adapter =
                new Sonic3kSpecialStageRewindAdapter(new Sonic3kSpecialStageManager());
        Sonic3kSpecialStageSnapshot emptySnapshot = Sonic3kSpecialStageSnapshot.uninitializedForTest();

        assertThrows(IllegalStateException.class, adapter::capture);
        assertThrows(IllegalStateException.class, () -> adapter.restore(emptySnapshot));
    }
}

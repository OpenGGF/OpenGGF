package com.openggf.game.sonic2.specialstage;

import com.openggf.game.SpecialStageProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSonic2SpecialStageRewindAdapter {
    @Test
    void adapterUsesGenericSpecialStageKeyAndKeepsMissingSnapshotDefault() {
        Sonic2SpecialStageRewindAdapter adapter =
                new Sonic2SpecialStageRewindAdapter(new Sonic2SpecialStageManager());

        assertEquals(SpecialStageProvider.SPECIAL_STAGE_REWIND_KEY, adapter.key());
        assertThrows(IllegalStateException.class, adapter::resetForMissingSnapshot);
    }

    @Test
    void adapterDelegatesCaptureAndRestoreToManager() {
        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager();
        manager.markCompleted(true);
        Sonic2SpecialStageRewindAdapter adapter = new Sonic2SpecialStageRewindAdapter(manager);

        Sonic2SpecialStageSnapshot snapshot = adapter.capture();
        manager.markFailed();

        adapter.restore(snapshot);

        assertEquals(Sonic2SpecialStageManager.ResultState.COMPLETED, manager.getResultState());
    }
}

package com.openggf.game.rewind;

import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.resources.Sonic1PlcService;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ensures native PLC progress belongs to a particular S1/S2 game session. */
class TestS1S2PlcSessionOwnership {

    @Test
    void sequentialSonic1AndSonic2SessionsDoNotSharePlcProgressOrAdapterIdentity() {
        Sonic1GameModule sonic1 = new Sonic1GameModule();
        sonic1.createGame(new Rom());
        Sonic1PlcService s1Service = sonic1.getGameService(Sonic1PlcService.class);
        s1Service.restore(nonEmptySnapshot());
        assertTrue(s1Service.isBusy());
        assertSingleAdapter(sonic1, s1Service);

        Sonic2GameModule sonic2 = new Sonic2GameModule();
        sonic2.createGame(new Rom());
        Sonic2PlcService s2Service = sonic2.getGameService(Sonic2PlcService.class);
        assertFalse(s2Service.isBusy(), "the next session must start with an empty PLC FIFO");
        assertSingleAdapter(sonic2, s2Service);

        s2Service.restore(nonEmptySnapshot());
        assertEquals(nonEmptySnapshot(), s2Service.capture());
        assertEquals(nonEmptySnapshot(), s1Service.capture(),
                "restoring the second session must not mutate the closed first session");
    }

    private static NemesisPlcQueueSnapshot nonEmptySnapshot() {
        return new NemesisPlcQueueSnapshot(null, List.of(
                new NemesisPlcQueueSnapshot.Entry(0x100, 0x20, 3, 3)));
    }

    private static void assertSingleAdapter(GameModule module, RewindSnapshottable<?> service) {
        assertEquals(1, module.rewindAdapters().size());
        assertSame(service, module.rewindAdapters().getFirst());
    }
}

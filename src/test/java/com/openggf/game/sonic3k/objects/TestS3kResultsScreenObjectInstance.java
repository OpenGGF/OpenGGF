package com.openggf.game.sonic3k.objects;

import com.openggf.game.LevelEventProvider;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.events.S3kTransitionEventBridge;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kResultsScreenObjectInstance {

    @Test
    void actOneTransitionSignalWaitsForRomKosCreateGate() {
        TransitionRecordingServices services = new TransitionRecordingServices(0x03);
        S3kResultsScreenObjectInstance results = ObjectConstructionContext.construct(
                services,
                () -> new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_AND_TAILS, 0));
        results.setServices(services);

        assertEquals(0, services.signalCount,
                "Obj_LevelResultsInit only queues Kosinski modules; Events_fg_5 is not set until "
                        + "Obj_LevelResultsCreate sees Kos_modules_left == 0 "
                        + "(docs/skdisasm/sonic3k.asm:62512-62584,62586-62616)");

        for (int i = 0; i < 8; i++) {
            results.update(i, null);
            assertEquals(0, services.signalCount,
                    "Obj_LevelResultsCreate must keep waiting while the ROM Kos module queue is nonzero "
                            + "(docs/skdisasm/sonic3k.asm:62586-62590)");
        }

        results.update(8, null);
        assertEquals(1, services.signalCount,
                "Obj_LevelResultsCreate sets Events_fg_5 only after the art-load gate opens "
                        + "(docs/skdisasm/sonic3k.asm:62610-62616)");
    }

    @Test
    void resultsCreateGateReadyUsesPostDecrementWait() {
        assertFalse(S3kResultsScreenObjectInstance.romResultsCreateGateReady(1));
        assertTrue(S3kResultsScreenObjectInstance.romResultsCreateGateReady(0));
    }

    private static final class TransitionRecordingServices extends TestObjectServices {
        private final int zone;
        private final RecordingBridge bridge = new RecordingBridge();
        private int signalCount;

        private TransitionRecordingServices(int zone) {
            this.zone = zone;
        }

        @Override
        public int romZoneId() {
            return zone;
        }

        @Override
        public LevelEventProvider levelEventProvider() {
            return bridge;
        }

        private final class RecordingBridge implements LevelEventProvider, S3kTransitionEventBridge {
            @Override
            public void initLevel(int zone, int act) {
            }

            @Override
            public void update() {
            }

            @Override
            public void signalActTransition() {
                signalCount++;
            }

            @Override
            public void requestHczPostTransitionCutscene() {
            }

            @Override
            public void requestMgzPostTransitionRelease() {
            }

            @Override
            public void requestCnzPostTransitionRelease(int framesUntilRelease) {
            }
        }
    }
}

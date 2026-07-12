package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.schema.RewindObjectStateBlob;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCnzSpiralTubeMultiSidekick {
    private static final int TUBE_X = 0x13C0;
    private static final int TUBE_Y = 0x02D0;

    @Test
    void mainAndThreeSidekicksCaptureAndAdvanceInNativePrefixOrder() {
        TestablePlayableSprite main = player("main");
        TestablePlayableSprite nativeP2 = player("native-p2");
        TestablePlayableSprite extensionOne = player("extension-1");
        TestablePlayableSprite extensionTwo = player("extension-2");
        CnzSpiralTubeInstance tube = tube(main, List.of(nativeP2, extensionOne, extensionTwo));

        tube.update(0, main);

        for (TestablePlayableSprite player : List.of(main, nativeP2, extensionOne, extensionTwo)) {
            assertTrue(player.isObjectControlled(), player.getCode() + " must capture independently");
            assertTrue(player.isControlLocked());
        }
        assertTrue(tube.isPersistent());

        tube.update(1, main);
        for (TestablePlayableSprite player : List.of(main, nativeP2, extensionOne, extensionTwo)) {
            assertEquals(TUBE_X + 0x30, player.getCentreX() & 0xFFFF,
                    "all four riders must advance through the same first sway sample");
        }
    }

    @Test
    void deathUnloadOmissionAndReorderKeepCleanupWithPlayerIdentity() {
        TestablePlayableSprite main = player("main");
        TestablePlayableSprite nativeP2 = player("native-p2");
        TestablePlayableSprite omitted = player("omitted");
        TestablePlayableSprite retained = player("retained");
        CnzSpiralTubeInstance tube = tube(main, List.of(nativeP2, omitted, retained));
        tube.update(0, main);

        tube.setServices(services(main, List.of(retained, nativeP2)));
        tube.update(1, main);
        assertTrue(omitted.isObjectControlled(), "temporarily omitted active rider must remain carried");
        assertEquals(TUBE_X + 0x30, omitted.getCentreX() & 0xFFFF);

        tube.setServices(services(main, List.of(omitted, retained, nativeP2)));
        omitted.setDead(true);
        tube.update(2, main);
        assertFalse(omitted.isObjectControlled(), "death must clear tube-owned forced control");
        assertFalse(omitted.isControlLocked());
        assertTrue(retained.isObjectControlled(), "other riders must remain independently active");

        tube.onUnload();
        for (TestablePlayableSprite player : List.of(main, nativeP2, retained)) {
            assertFalse(player.isObjectControlled(), "unload must release every owned rider identity");
            assertFalse(player.isControlLocked());
        }
    }

    @Test
    void nonEmptyExtensionStateRewindsThroughPlayerRefs() {
        TestablePlayableSprite oldMain = player("old-main");
        TestablePlayableSprite oldP2 = player("old-p2");
        TestablePlayableSprite oldExtension = player("old-extension");
        CnzSpiralTubeInstance tube = tube(oldMain, List.of(oldP2, oldExtension));
        tube.update(0, oldMain);
        RewindObjectStateBlob snapshot = CompactFieldCapturer.capture(
                tube, rewindContext(oldMain, oldP2, oldExtension));

        TestablePlayableSprite newMain = player("new-main");
        TestablePlayableSprite newP2 = player("new-p2");
        TestablePlayableSprite newExtension = player("new-extension");
        newExtension.setObjectControlled(true);
        newExtension.setControlLocked(true);
        tube.setServices(services(newMain, List.of(newP2, newExtension)));
        CompactFieldCapturer.restore(tube, snapshot, rewindContext(newMain, newP2, newExtension));
        newExtension.setDead(true);
        tube.update(1, newMain);

        assertFalse(newExtension.isObjectControlled(),
                "restored extension state must relink and clean the restored identity");
        assertFalse(newExtension.isControlLocked());
    }

    private static CnzSpiralTubeInstance tube(
            TestablePlayableSprite main,
            List<TestablePlayableSprite> sidekicks) {
        CnzSpiralTubeInstance tube = new CnzSpiralTubeInstance(new ObjectSpawn(
                TUBE_X, TUBE_Y, Sonic3kObjectIds.CNZ_SPIRAL_TUBE, 0, 0, false, 0));
        tube.setServices(services(main, sidekicks));
        return tube;
    }

    private static TestObjectServices services(
            TestablePlayableSprite main,
            List<TestablePlayableSprite> sidekicks) {
        return new TestObjectServices() {
            private final ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, () -> sidekicks);

            @Override
            public ObjectPlayerQuery playerQuery() {
                return query;
            }
        };
    }

    private static TestablePlayableSprite player(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) TUBE_X, (short) TUBE_Y);
        player.setAir(false);
        return player;
    }

    private static RewindCaptureContext rewindContext(
            TestablePlayableSprite main,
            TestablePlayableSprite nativeP2,
            TestablePlayableSprite extension) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerPlayer(main, PlayerRefId.mainPlayer());
        table.registerPlayer(nativeP2, PlayerRefId.sidekick(0));
        table.registerPlayer(extension, PlayerRefId.sidekick(1));
        return RewindCaptureContext.withIdentityTable(table);
    }
}

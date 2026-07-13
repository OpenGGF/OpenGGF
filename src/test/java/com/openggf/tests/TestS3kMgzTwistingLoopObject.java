package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.MGZTwistingLoopObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kMgzTwistingLoopObject {
    private static final int LOOP_X = 0x1200;
    private static final int LOOP_Y = 0x0600;

    @BeforeEach
    void setUp() {
        SessionManager.clear();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void mgzTwistingLoopDirectEntry_advancesDownwardOnFirstActiveFrame() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();

        loop.update(0, player); // capture
        loop.update(1, player); // first active frame

        assertTrue(player.isObjectControlled(), "Loop should still own the player after the first active frame");
        assertTrue(player.isObjectControlAllowsCpu(),
                "MGZ loop uses bits 0-6 object_control, so CPU/touch remain allowed while carried");
        assertTrue(player.isObjectControlSuppressesMovement(),
                "MGZ loop bit 0 should suppress normal movement while the object owns traversal");
        assertFalse(player.isTouchResponseSuppressedByObjectControl(),
                "MGZ loop bits 0-6 should not suppress touch responses");
        assertTrue(player.getCentreY() > LOOP_Y,
                "Captured MGZ loop entry should advance downward on the first active frame");
    }

    @Test
    void mgzTwistingLoopDirectEntry_doesNotReleaseBeforeItsConfiguredThreshold() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x01, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();

        loop.update(0, player); // capture
        for (int frame = 1; frame <= 3; frame++) {
            loop.update(frame, player);
        }

        assertTrue(player.isObjectControlled(),
                "MGZ loop should still be carrying an on-foot entry before its configured release threshold");
    }

    @Test
    void mgzTwistingLoopRollingEntry_keepsRollingRadiiUntilLoopReleases() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x01, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();
        player.move((short) 0, (short) 0x80); // seed y_sub = $8000 without changing pixel Y
        player.setRolling(true);

        loop.update(0, player); // capture
        loop.update(1, player); // first active frame

        assertTrue(player.getRolling(),
                "MGZ loop should keep rolling state while the object is carrying the player");
        assertEquals(14, player.getYRadius(),
                "MGZ loop should keep using the rolling radius for spiral positioning while captured");
        assertTrue(player.getYSubpixelRaw() != 0,
                "MGZ loop should continue carrying subpixel progress while the player is captured");

        for (int frame = 2; frame <= 12 && player.isObjectControlled(); frame++) {
            loop.update(frame, player);
        }

        assertFalse(player.getRolling(),
                "MGZ loop should restore standing state in the shared release path");
        assertEquals(player.getStandYRadius(), player.getYRadius(),
                "MGZ loop should restore the standing radius in the shared release path");
    }

    @Test
    void mgzTwistingLoopNegativeEntrySpeed_keepsNegativeMinimumGroundSpeed() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();
        player.setGSpeed((short) -8);

        loop.update(0, player); // capture
        loop.update(1, player); // first active frame

        assertEquals(-0x0400, player.getGSpeed(),
                "MGZ loop should preserve a negative entry direction when clamping to minimum speed");
        assertEquals(0x0400, player.getYSpeed() & 0xFFFF,
                "MGZ loop should still use the minimum downward carry speed");
    }

    @Test
    void mgzTwistingLoopCapture_keepsEntryWallAngleWhileCarried() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();

        loop.update(0, player); // capture
        loop.update(1, player); // first active frame

        assertTrue((player.getAngle() & 0xFF) == 0x40,
                "MGZ loop should keep the entry wall angle while carrying the player");
    }

    @Test
    void mgzTwistingLoopOnFootRelease_doesNotUseCompensatedHandoff() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x00, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();

        loop.update(0, player); // capture
        loop.update(1, player); // immediate threshold release

        assertFalse(player.isObjectControlled(),
                "Plain MGZ direct entries should release immediately at the configured threshold");
        assertFalse(player.isStickToConvex(),
                "Plain MGZ direct entries should not inherit the compensated convex handoff");
        assertFalse(player.getAir(),
                "Plain MGZ direct entries should remain grounded on release");
    }

    @Test
    void mgzTwistingLoopJumpReleaseKeepsBits0To6ControlUntilReleaseFramesEnd() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite player = createDirectEntryPlayer();

        loop.update(0, player); // capture
        player.setJumpInputPressed(true);
        loop.update(1, player); // jump release starts

        assertTrue(player.isObjectControlled(),
                "Jump release keeps temporary object control while the loop owns launch movement");
        assertTrue(player.isObjectControlAllowsCpu(),
                "Jump release should preserve the MGZ loop bits 0-6 CPU/touch policy");
        assertTrue(player.isObjectControlSuppressesMovement(),
                "Jump release should keep movement suppressed until release frames expire");
        assertFalse(player.isTouchResponseSuppressedByObjectControl(),
                "Bits 0-6 jump release should not suppress touch responses");
        assertFalse(player.isControlLocked(),
                "Jump release should unlock player control even while temporary object control remains");
    }

    @Test
    void mgzTwistingLoopPreservesNativeP2ThenProcessesExtraSidekicks() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite main = createDirectEntryPlayer();
        TestablePlayableSprite nativeP2 = createDirectEntryPlayer("tails", LOOP_X - 1);
        TestablePlayableSprite extraSidekick = createDirectEntryPlayer("knuckles", LOOP_X + 2);
        loop.setServices(new QueryOnlyPlayerServices(main, List.of(nativeP2, extraSidekick)));

        loop.update(0, main);
        loop.update(1, main);

        assertTrue(nativeP2.isObjectControlled(),
                "MGZ loop player2 slot should use only the first native sidekick from ObjectPlayerQuery");
        assertTrue(extraSidekick.isObjectControlled(),
                "MGZ loop should process extension sidekicks after its native player2 slot");
    }

    @Test
    void mgzTwistingLoopUnloadReleasesCapturedExtensionPlayer() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite main = createDirectEntryPlayer("sonic", LOOP_X + 0x100);
        TestablePlayableSprite nativeP2 = createDirectEntryPlayer("tails", LOOP_X + 0x100);
        TestablePlayableSprite extension = createDirectEntryPlayer("knuckles", LOOP_X + 2);
        loop.setServices(new QueryOnlyPlayerServices(main, List.of(nativeP2, extension)));
        loop.update(0, main);
        assertTrue(extension.isObjectControlled());

        loop.onUnload();

        assertFalse(extension.isObjectControlled());
        assertFalse(extension.isObjectMappingFrameControl());
        assertFalse(extension.isControlLocked());
    }

    @Test
    void mgzTwistingLoopUnloadDoesNotClearReplacementControl() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite main = createDirectEntryPlayer("sonic", LOOP_X + 0x100);
        TestablePlayableSprite extension = createDirectEntryPlayer("knuckles", LOOP_X + 2);
        loop.setServices(new QueryOnlyPlayerServices(main, List.of(extension)));
        loop.update(0, main);
        extension.setObjectMappingFrameControl(false);
        extension.setControlLocked(false);
        extension.setAnimationId(5);

        loop.onUnload();

        assertTrue(extension.isObjectControlled(),
                "stale loop cleanup must not release unrelated replacement control");
    }

    @Test
    void mgzTwistingLoopKeepsActiveStateWithActorsWhenNativeP2RosterReorders() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite main = createDirectEntryPlayer("sonic", LOOP_X + 0x100);
        TestablePlayableSprite capturedP2 = createDirectEntryPlayer("tails", LOOP_X + 1);
        TestablePlayableSprite replacementP2 = createDirectEntryPlayer("knuckles", LOOP_X + 0x100);
        List<TestablePlayableSprite> sidekicks = new ArrayList<>(List.of(capturedP2, replacementP2));
        loop.setServices(new QueryOnlyPlayerServices(main, sidekicks));
        loop.update(0, main);
        int capturedY = capturedP2.getCentreY();

        sidekicks.clear();
        sidekicks.add(replacementP2);
        sidekicks.add(capturedP2);
        loop.update(1, main);

        assertTrue(capturedP2.isObjectControlled(), "demoted native P2 must retain its own active loop state");
        assertTrue(capturedP2.getCentreY() > capturedY, "demoted native P2 must continue its own progress");
        assertFalse(replacementP2.isObjectControlled(), "new native P2 must not inherit the prior actor's loop state");
    }

    @Test
    void mgzTwistingLoopKeepsActiveExtensionStateWhenPromotedToNativeP2() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite main = createDirectEntryPlayer("sonic", LOOP_X + 0x100);
        TestablePlayableSprite oldP2 = createDirectEntryPlayer("tails", LOOP_X + 0x100);
        TestablePlayableSprite promoted = createDirectEntryPlayer("knuckles", LOOP_X + 1);
        List<TestablePlayableSprite> sidekicks = new ArrayList<>(List.of(oldP2, promoted));
        loop.setServices(new QueryOnlyPlayerServices(main, sidekicks));
        loop.update(0, main);
        int capturedY = promoted.getCentreY();

        sidekicks.clear();
        sidekicks.add(promoted);
        sidekicks.add(oldP2);
        loop.update(1, main);

        assertTrue(promoted.isObjectControlled(), "promotion must preserve the extension actor's active state");
        assertTrue(promoted.getCentreY() > capturedY, "promoted actor must continue its own loop progress");
    }

    @Test
    void mgzTwistingLoopReleasesOmittedNativeP2AndUnloadStillTargetsDemotedOwner() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite main = createDirectEntryPlayer("sonic", LOOP_X + 0x100);
        TestablePlayableSprite capturedP2 = createDirectEntryPlayer("tails", LOOP_X + 1);
        TestablePlayableSprite replacementP2 = createDirectEntryPlayer("knuckles", LOOP_X + 0x100);
        List<TestablePlayableSprite> sidekicks = new ArrayList<>(List.of(capturedP2, replacementP2));
        loop.setServices(new QueryOnlyPlayerServices(main, sidekicks));
        loop.update(0, main);

        sidekicks.clear();
        sidekicks.add(replacementP2);
        loop.update(1, main);

        assertFalse(capturedP2.isObjectControlled(), "omitted native P2 must be released immediately");
        loop.onUnload();
        assertFalse(replacementP2.isObjectControlled(), "unload must not manufacture ownership for replacement P2");
    }

    @Test
    void mgzTwistingLoopUnloadAfterDemotionReleasesOriginalOwnerOnly() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite main = createDirectEntryPlayer("sonic", LOOP_X + 0x100);
        TestablePlayableSprite capturedP2 = createDirectEntryPlayer("tails", LOOP_X + 1);
        TestablePlayableSprite replacementP2 = createDirectEntryPlayer("knuckles", LOOP_X + 0x100);
        List<TestablePlayableSprite> sidekicks = new ArrayList<>(List.of(capturedP2, replacementP2));
        loop.setServices(new QueryOnlyPlayerServices(main, sidekicks));
        loop.update(0, main);
        sidekicks.clear();
        sidekicks.add(replacementP2);
        sidekicks.add(capturedP2);
        loop.update(1, main);

        loop.onUnload();

        assertFalse(capturedP2.isObjectControlled(), "unload must release the demoted actor that owns loop state");
        assertFalse(replacementP2.isObjectControlled(), "unload must leave the unrelated current P2 untouched");
    }

    @Test
    void mgzTwistingLoopDeathReleasesPromotedExtensionOwner() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite main = createDirectEntryPlayer("sonic", LOOP_X + 0x100);
        TestablePlayableSprite oldP2 = createDirectEntryPlayer("tails", LOOP_X + 0x100);
        TestablePlayableSprite promoted = createDirectEntryPlayer("knuckles", LOOP_X + 1);
        List<TestablePlayableSprite> sidekicks = new ArrayList<>(List.of(oldP2, promoted));
        loop.setServices(new QueryOnlyPlayerServices(main, sidekicks));
        loop.update(0, main);
        sidekicks.clear();
        sidekicks.add(promoted);
        promoted.setDead(true);

        loop.update(1, main);

        assertFalse(promoted.isObjectControlled(), "death cleanup must follow the promoted actor's state");
    }

    @Test
    void mgzTwistingLoopRewindRelinksNativeP2OwnerToReplacementActor() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite capturedMain = createDirectEntryPlayer("sonic", LOOP_X + 0x100);
        TestablePlayableSprite capturedP2 = createDirectEntryPlayer("tails", LOOP_X + 1);
        loop.setServices(new QueryOnlyPlayerServices(capturedMain, new ArrayList<>(List.of(capturedP2))));
        loop.update(0, capturedMain);
        RewindIdentityTable capturedIds = new RewindIdentityTable();
        capturedIds.registerPlayer(capturedMain, PlayerRefId.mainPlayer());
        capturedIds.registerPlayer(capturedP2, PlayerRefId.sidekick(0));
        var snapshot = loop.captureRewindState(RewindCaptureContext.withIdentityTable(capturedIds));

        TestablePlayableSprite replacementMain = createDirectEntryPlayer("sonic", LOOP_X + 0x100);
        TestablePlayableSprite replacementP2 = createDirectEntryPlayer("tails", LOOP_X + 1);
        RewindIdentityTable replacementIds = new RewindIdentityTable();
        replacementIds.registerPlayer(replacementMain, PlayerRefId.mainPlayer());
        replacementIds.registerPlayer(replacementP2, PlayerRefId.sidekick(0));
        loop.setServices(new QueryOnlyPlayerServices(replacementMain, new ArrayList<>(List.of(replacementP2))));
        loop.restoreRewindState(snapshot, RewindCaptureContext.withIdentityTable(replacementIds));
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(replacementP2);
        replacementP2.setObjectMappingFrameControl(true);
        replacementP2.setControlLocked(true);

        loop.onUnload();

        assertFalse(replacementP2.isObjectControlled(), "rewind must relink native P2 ownership to the replacement actor");
        assertTrue(capturedP2.isObjectControlled(), "rewind cleanup must not target the stale captured actor instance");
    }

    @Test
    void mgzTwistingLoopDoesNotPromoteCpuControlledMainIntoNativeP2Slot() {
        MGZTwistingLoopObjectInstance loop = new MGZTwistingLoopObjectInstance(
                new ObjectSpawn(LOOP_X, LOOP_Y, Sonic3kObjectIds.MGZ_TWISTING_LOOP, 0x10, 0, false, 0));
        TestablePlayableSprite updatePlayer = createDirectEntryPlayer("sonic", LOOP_X + 1);
        TestablePlayableSprite cpuMain = createDirectEntryPlayer("tails-main", LOOP_X - 1);
        TestablePlayableSprite nativeP2 = createDirectEntryPlayer("tails", LOOP_X + 2);
        cpuMain.setCpuControlled(true);
        nativeP2.setCpuControlled(true);
        loop.setServices(new QueryOnlyPlayerServices(cpuMain, List.of(nativeP2)));

        loop.update(0, updatePlayer);

        assertFalse(cpuMain.isObjectControlled(),
                "The query's main player must never be consumed as MGZ's native P2 slot, even if CPU-controlled");
        assertTrue(nativeP2.isObjectControlled(),
                "MGZ loop should skip the queried main and use the first queried sidekick as native P2");
    }

    private static TestablePlayableSprite createDirectEntryPlayer() {
        return createDirectEntryPlayer("sonic", LOOP_X + 1);
    }

    private static TestablePlayableSprite createDirectEntryPlayer(String characterCode, int centreX) {
        TestablePlayableSprite player = new TestablePlayableSprite(characterCode, (short) 0, (short) 0);
        player.setCentreX((short) centreX);
        player.setCentreY((short) LOOP_Y);
        player.setAir(false);
        player.setAngle((byte) 0x40);
        player.setGSpeed((short) 0x0800);
        player.setYSpeed((short) 0x0200);
        player.setXSpeed((short) 0);
        player.setObjectControlled(false);
        player.setControlLocked(false);
        player.setOnObject(false);
        player.setRolling(false);
        player.setJumping(false);
        return player;
    }

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
        private final ObjectPlayerQuery playerQuery;

        private QueryOnlyPlayerServices(TestablePlayableSprite main, List<TestablePlayableSprite> sidekicks) {
            this.playerQuery = new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return playerQuery;
        }

        @Override
        public List<com.openggf.game.PlayableEntity> sidekicks() {
            throw new AssertionError("MGZ twisting loop should use ObjectPlayerQuery for native P2 selection");
        }
    }
}

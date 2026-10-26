package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TestHCZHandLauncherObjectInstance {

    @Test
    void landingCheckpointIsConsumedOnFollowingObjectDispatch() {
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = buildLauncher(player);

        while (launcher.getYOffset() > 0x18) {
            launcher.update(0, player);
        }
        assertFalse(player.isObjectControlled(),
                "sub_30CE0 runs before the current SolidObjectTop landing pass");

        launcher.update(1, player);
        assertTrue(player.isObjectControlled(),
                "the following object dispatch consumes the retained standing bit");
    }

    @Test
    void risingHandResolvesTopSolidAfterWritingItsNewY() {
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = buildLauncher(player);
        launcher.setCheckpointBatch(new SolidCheckpointBatch(launcher, Map.of()));

        for (int frame = 0; frame < 5; frame++) {
            int calls = launcher.checkpointYs().size();
            launcher.update(frame, player);
            // HCZHandLauncher_WaitForGrab (sonic3k.asm:65844-65851) subtracts 8.
            // HCZHandLauncher_UpdateSolid (sonic3k.asm:65864-65872) then writes
            // y_pos before calling the top-solid helper.
            assertEquals(calls + 1, launcher.checkpointYs().size(),
                    "every Main pass ends in SolidObjectTop, including a rising pass");
            assertEquals(launcher.getY(), launcher.checkpointYs().getLast(),
                    "SolidObjectTop sees the y_pos written by this pass");
        }
        assertEquals(0x50 - 5 * 8, launcher.getYOffset());
    }

    @Test
    void returningHandResolvesTopSolidWhileNoPlayerIsInRange() {
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = buildLauncher(player);
        launcher.setCheckpointBatch(new SolidCheckpointBatch(launcher, Map.of()));
        while (launcher.getYOffset() > 0x18) {
            launcher.update(0, player);
        }

        player.setCentreX((short) 0x0100);
        int calls = launcher.checkpointYs().size();
        launcher.update(1, player);

        // HCZHandLauncher_RestoreOffset adds 8 and falls into
        // HCZHandLauncher_UpdateSolid (sonic3k.asm:65859-65872).
        assertEquals(0x20, launcher.getYOffset());
        assertEquals(calls + 1, launcher.checkpointYs().size());
        assertEquals(launcher.getY(), launcher.checkpointYs().getLast());
    }

    @Test
    void grabConsumesStandingBitRetainedFromPreviousSolidPass() {
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = buildLauncher(player);
        SolidCheckpointBatch empty = new SolidCheckpointBatch(launcher, Map.of());
        launcher.setCheckpointBatch(empty);
        while (launcher.getYOffset() > 0x20) {
            launcher.update(0, player);
        }

        // The pass that reaches $18 lands the player in its SolidObjectTop.
        launcher.setCheckpointBatch(standingBatch(launcher, player));
        launcher.update(1, player);
        assertEquals(0x18, launcher.getYOffset());
        assertFalse(player.isObjectControlled());

        // HCZHandLauncher_TryGrabPlayer tests status(a0) before this pass's
        // SolidObjectTop runs, so the retained bit grabs even though the
        // later solid pass of the same dispatch reports no contact
        // (sonic3k.asm:65854-65856, 66006-66008).
        launcher.setCheckpointBatch(empty);
        launcher.update(2, player);
        assertTrue(player.isObjectControlled());
    }

    @Test
    void launchReturnSkipsTopSolidAboveGrabOffset() {
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = buildLauncher(player);
        updateUntilGrabbed(launcher, player);

        int frame = 20;
        int callsBeforePass = 0;
        while (player.isObjectControlled() && frame < 200) {
            callsBeforePass = launcher.checkpointYs().size();
            launcher.update(frame++, player);
        }
        assertFalse(player.isObjectControlled(), "the launch must release the held player");
        assertEquals(0x20, launcher.getYOffset());

        // HCZHandLauncher_UpdateAfterLaunch calls SolidObjectTop only while
        // $30 <= $18 (sonic3k.asm:65903-65914); the release pass already
        // moved to $20, so neither it nor the rest of the return resolves.
        assertEquals(callsBeforePass, launcher.checkpointYs().size(),
                "HCZHandLauncher_ReleaseHeldPlayers uses the retained standing bits, not a new solid pass");
        int calls = launcher.checkpointYs().size();
        for (int i = 0; i < 4; i++) {
            launcher.update(frame++, player);
        }
        assertEquals(calls, launcher.checkpointYs().size());
    }

    @Test
    void topSolidUsesLiteralRomD3Height() {
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = buildLauncher(player);

        assertEquals(0x11, launcher.getSolidParams().airHalfHeight());
        assertEquals(0x11, launcher.getSolidParams().groundHalfHeight());
        assertTrue(launcher.rejectsZeroDistanceTopSolidLanding(),
                "SolidObjectTop accepts negative overlap but rejects the exact d0=0 boundary");
        assertFalse(launcher.usesPlatformObjectLandingSnap(),
                "SolidObjectTop keeps its relative landing result instead of PlatformObject's absolute snap");
    }

    @Test
    void grabbedPlayerUsesNativeBitZeroObjectControlPolicy() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0100);
        player.setSubpixelRaw(0xF700, 0);
        ProbeHandLauncher launcher = buildLauncher(player);

        updateUntilGrabbed(launcher, player);

        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertEquals(0xF700, player.getXSubpixelRaw());
    }

    @Test
    void jumpEscapeClearsObjectControlPolicy() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = buildLauncher(player);
        updateUntilGrabbed(launcher, player);

        player.setJumpInputPressed(true);
        launcher.update(20, player);

        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
    }

    @Test
    void automaticLaunchReleaseClearsObjectControlPolicy() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = buildLauncher(player);
        updateUntilGrabbed(launcher, player);

        for (int frame = 20; frame < 180 && player.isObjectControlled(); frame++) {
            launcher.update(frame, player);
        }

        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
    }

    @Test
    void nativeP2QuerySidekickCanBeGrabbedWhenRawSidekickListIsEmpty() {
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x0100, (short) 0x0100);
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = new ProbeHandLauncher(
                new ObjectSpawn(0x0208, 0x0100, 0x3A, 0, 0, false, 0));
        launcher.setServices(new QueryOnlyPlayerServices(main, List.of(sidekick)));
        launcher.setCheckpointBatch(standingBatch(launcher, sidekick));

        updateUntilGrabbed(launcher, main, sidekick);

        assertTrue(sidekick.isObjectControlled(),
                "HCZ hand launcher has only native P1/P2 grab slots, so P2 must come from ObjectPlayerQuery NATIVE_P1_P2");
    }

    @Test
    void nonSpriteQueryMainDoesNotHideNativeP2Sidekick() {
        PlayableEntity nonSpriteMain = mock(PlayableEntity.class);
        TestablePlayableSprite updatePlayer = new TestablePlayableSprite("sonic", (short) 0x0100, (short) 0x0100);
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = new ProbeHandLauncher(
                new ObjectSpawn(0x0208, 0x0100, 0x3A, 0, 0, false, 0));
        launcher.setServices(new QueryOnlyPlayerServices(nonSpriteMain, List.of(sidekick)));
        launcher.setCheckpointBatch(standingBatch(launcher, sidekick));

        updateUntilGrabbed(launcher, updatePlayer, sidekick);

        assertTrue(sidekick.isObjectControlled(),
                "Non-sprite query participants must not consume the native P2 slot");
    }

    @Test
    void extensionSidekicksCaptureWithIndependentGrabState() {
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x0100, (short) 0x0100);
        TestablePlayableSprite nativeP2 = new TestablePlayableSprite("tails", (short) 0x0100, (short) 0x0100);
        TestablePlayableSprite firstExtension = new TestablePlayableSprite("knuckles", (short) 0x0200, (short) 0x0100);
        TestablePlayableSprite secondExtension = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = new ProbeHandLauncher(
                new ObjectSpawn(0x0208, 0x0100, 0x3A, 0, 0, false, 0));
        launcher.setServices(new QueryOnlyPlayerServices(
                main, List.of(nativeP2, firstExtension, secondExtension)));
        launcher.setCheckpointBatch(standingBatch(launcher, firstExtension, secondExtension));

        for (int frame = 0; frame < 16
                && (!firstExtension.isObjectControlled() || !secondExtension.isObjectControlled()); frame++) {
            launcher.update(frame, main);
        }

        assertTrue(firstExtension.isObjectControlled());
        assertTrue(secondExtension.isObjectControlled());
    }

    @Test
    void omittedCapturedExtensionIsReleasedByIdentity() {
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x0100, (short) 0x0100);
        TestablePlayableSprite nativeP2 = new TestablePlayableSprite("tails", (short) 0x0100, (short) 0x0100);
        TestablePlayableSprite extension = new TestablePlayableSprite("knuckles", (short) 0x0200, (short) 0x0100);
        QueryOnlyPlayerServices services = new QueryOnlyPlayerServices(main, List.of(nativeP2, extension));
        ProbeHandLauncher launcher = new ProbeHandLauncher(
                new ObjectSpawn(0x0208, 0x0100, 0x3A, 0, 0, false, 0));
        launcher.setServices(services);
        launcher.setCheckpointBatch(standingBatch(launcher, extension));
        updateUntilGrabbed(launcher, main, extension);

        services.setSidekicks(List.of(nativeP2));
        launcher.update(20, main);

        assertFalse(extension.isObjectControlled());
    }

    @Test
    void capturedExtensionKeepsOwnershipWhenPromotedToNativeP2() {
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x0100, (short) 0x0100);
        TestablePlayableSprite originalP2 = new TestablePlayableSprite("tails", (short) 0x0100, (short) 0x0100);
        TestablePlayableSprite extension = new TestablePlayableSprite("knuckles", (short) 0x0200, (short) 0x0100);
        QueryOnlyPlayerServices services = new QueryOnlyPlayerServices(main, List.of(originalP2, extension));
        ProbeHandLauncher launcher = new ProbeHandLauncher(
                new ObjectSpawn(0x0208, 0x0100, 0x3A, 0, 0, false, 0));
        launcher.setServices(services);
        launcher.setCheckpointBatch(standingBatch(launcher, extension));
        updateUntilGrabbed(launcher, main, extension);

        services.setSidekicks(List.of(extension, originalP2));
        launcher.update(20, main);

        assertTrue(extension.isObjectControlled());
    }

    @Test
    void unloadReleasesEveryCapturedExtension() {
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x0100, (short) 0x0100);
        TestablePlayableSprite nativeP2 = new TestablePlayableSprite("tails", (short) 0x0100, (short) 0x0100);
        TestablePlayableSprite extension = new TestablePlayableSprite("knuckles", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = new ProbeHandLauncher(
                new ObjectSpawn(0x0208, 0x0100, 0x3A, 0, 0, false, 0));
        launcher.setServices(new QueryOnlyPlayerServices(main, List.of(nativeP2, extension)));
        launcher.setCheckpointBatch(standingBatch(launcher, extension));
        updateUntilGrabbed(launcher, main, extension);

        launcher.onUnload();

        assertFalse(extension.isObjectControlled());
    }

    @Test
    void visualDirectionReleasesBothNativeOwnersWithoutLaunchOrRoutineAdvance()
            throws ReflectiveOperationException {
        TestablePlayableSprite main = spy(new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0100));
        TestablePlayableSprite sidekick = spy(new TestablePlayableSprite("tails", (short) 0x0200, (short) 0x0100));
        VisualFixture fixture = capturedVisualFixture(main, List.of(sidekick), main, sidekick);
        ProbeHandLauncher launcher = fixture.launcher();
        LauncherProgress progress = LauncherProgress.read(launcher);
        PlayerMotion mainMotion = PlayerMotion.read(main), sidekickMotion = PlayerMotion.read(sidekick);
        clearInvocations(main, sidekick);

        launcher.releasePlayersForVisualDirection();

        assertVisualRelease(fixture, main, mainMotion);
        assertVisualRelease(fixture, sidekick, sidekickMotion);
        assertEquals(progress, LauncherProgress.read(launcher), "Visual release does not execute the native routine");
        launcher.releasePlayersForVisualDirection();
        assertVisualRelease(fixture, main, mainMotion);
        assertVisualRelease(fixture, sidekick, sidekickMotion);
        assertEquals(progress, LauncherProgress.read(launcher));
    }

    @Test
    void visualDirectionReleasesCapturedExtensionExactlyOnce() throws ReflectiveOperationException {
        TestablePlayableSprite main = spy(new TestablePlayableSprite("sonic", (short) 0x0100, (short) 0x0100));
        TestablePlayableSprite nativeP2 = spy(new TestablePlayableSprite("tails", (short) 0x0100, (short) 0x0100));
        TestablePlayableSprite extension = spy(new TestablePlayableSprite("knuckles", (short) 0x0200, (short) 0x0100));
        VisualFixture fixture = capturedVisualFixture(main, List.of(nativeP2, extension), extension);
        ProbeHandLauncher launcher = fixture.launcher();
        LauncherProgress progress = LauncherProgress.read(launcher);
        PlayerMotion motion = PlayerMotion.read(extension);
        clearInvocations(main, nativeP2, extension);

        launcher.releasePlayersForVisualDirection();
        launcher.releasePlayersForVisualDirection();

        assertVisualRelease(fixture, extension, motion);
        verify(main, never()).applyObjectControlState(ObjectControlState.NONE);
        verify(nativeP2, never()).applyObjectControlState(ObjectControlState.NONE);
        assertEquals(progress, LauncherProgress.read(launcher), "The existing extension cleanup runs without a native dispatch");
    }

    @Test
    void visualDirectionNeverAcquiredLeavesOtherControlsAndRidesUntouched()
            throws ReflectiveOperationException {
        TestablePlayableSprite main = spy(new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0100));
        TestablePlayableSprite sidekick = spy(new TestablePlayableSprite("tails", (short) 0x0200, (short) 0x0100));
        QueryOnlyPlayerServices services = new QueryOnlyPlayerServices(main, List.of(sidekick));
        services.withIsolatedObjectManager();
        ProbeHandLauncher launcher = new ProbeHandLauncher(new ObjectSpawn(0x0208, 0x0100, 0x3A, 0, 0, false, 0));
        launcher.setServices(services);
        ProbeHandLauncher otherOwner = new ProbeHandLauncher(new ObjectSpawn(0x0408, 0x0100, 0x3A, 0, 0, false, 1));
        for (TestablePlayableSprite player : List.of(main, sidekick)) {
            services.objectManager().forceRidingObjectForBootstrap(player, otherOwner);
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        }
        LauncherProgress progress = LauncherProgress.read(launcher);
        PlayerMotion mainMotion = PlayerMotion.read(main), sidekickMotion = PlayerMotion.read(sidekick);
        clearInvocations(main, sidekick);

        launcher.releasePlayersForVisualDirection();
        launcher.releasePlayersForVisualDirection();

        for (TestablePlayableSprite player : List.of(main, sidekick)) {
            assertSame(otherOwner, services.objectManager().getRidingObject(player));
            assertTrue(player.isObjectControlled());
            assertTrue(player.isOnObject());
            verify(player, never()).applyObjectControlState(ObjectControlState.NONE);
        }
        assertEquals(mainMotion, PlayerMotion.read(main));
        assertEquals(sidekickMotion, PlayerMotion.read(sidekick));
        assertEquals(progress, LauncherProgress.read(launcher));
    }

    @Test
    void nativeUnloadStillRetiresOnlyNativeOwnerMetadata() throws ReflectiveOperationException {
        TestablePlayableSprite main = spy(new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0100));
        TestablePlayableSprite sidekick = spy(new TestablePlayableSprite("tails", (short) 0x0200, (short) 0x0100));
        VisualFixture fixture = capturedVisualFixture(main, List.of(sidekick), main, sidekick);
        LauncherProgress progress = LauncherProgress.read(fixture.launcher());
        PlayerMotion mainMotion = PlayerMotion.read(main), sidekickMotion = PlayerMotion.read(sidekick);
        clearInvocations(main, sidekick);

        fixture.launcher().onUnload();

        for (TestablePlayableSprite player : List.of(main, sidekick)) {
            assertTrue(player.isObjectControlled(), "Native deletion must retain the native player control word");
            assertTrue(player.isOnObject());
            assertSame(fixture.launcher(), fixture.objects().getRidingObject(player));
            verify(player, never()).applyObjectControlState(ObjectControlState.NONE);
        }
        assertEquals(mainMotion, PlayerMotion.read(main));
        assertEquals(sidekickMotion, PlayerMotion.read(sidekick));
        assertEquals(progress, LauncherProgress.read(fixture.launcher()));
    }

    private static VisualFixture capturedVisualFixture(TestablePlayableSprite main,
            List<? extends PlayableEntity> sidekicks, TestablePlayableSprite... captured) {
        QueryOnlyPlayerServices services = new QueryOnlyPlayerServices(main, sidekicks);
        services.withIsolatedObjectManager();
        ProbeHandLauncher launcher = new ProbeHandLauncher(new ObjectSpawn(0x0208, 0x0100, 0x3A, 0, 0, false, 0));
        launcher.setServices(services);
        launcher.setCheckpointBatch(standingBatch(launcher, captured));
        // Declare initial top-solid support before the first object dispatch. The
        // existing probe then reaches the real grab routine for each requested owner.
        for (TestablePlayableSprite player : captured) services.objectManager().forceRidingObjectForBootstrap(player, launcher);
        for (int frame = 0; frame < 16
                && java.util.Arrays.stream(captured).anyMatch(player -> !player.isObjectControlled()); frame++) {
            launcher.update(frame, main);
        }
        for (TestablePlayableSprite player : captured) {
            assertTrue(player.isObjectControlled(), "Prerequisite: the actual hand routine acquired the player");
            assertTrue(player.isOnObject());
            assertSame(launcher, services.objectManager().getRidingObject(player));
        }
        assertEquals(0x18, launcher.getYOffset());
        return new VisualFixture(launcher, services.objectManager());
    }

    private static void assertVisualRelease(VisualFixture fixture, TestablePlayableSprite player, PlayerMotion motion) {
        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
        assertFalse(player.isOnObject());
        assertNull(fixture.objects().getRidingObject(player), "Release the exact hand's engine ride");
        assertEquals(motion, PlayerMotion.read(player), "Visual retirement must not apply launch/escape motion or pose");
        verify(player, times(1)).applyObjectControlState(ObjectControlState.NONE);
        verify(player, times(1)).setOnObject(false);
        verify(player, never()).setXSpeed(anyShort());
        verify(player, never()).setYSpeed(anyShort());
        verify(player, never()).setGSpeed(anyShort());
        verify(player, never()).setAir(anyBoolean());
        verify(player, never()).setAnimationId(anyInt());
        verify(player, never()).setForcedInputMask(anyInt());
        verify(player, never()).clearForcedInputMask();
    }

    private record VisualFixture(ProbeHandLauncher launcher, ObjectManager objects) { }

    private record PlayerMotion(int x, int y, int xSub, int ySub, short vx, short vy, short ground,
                                Object direction, int animation, boolean air, boolean rolling) {
        static PlayerMotion read(TestablePlayableSprite player) {
            return new PlayerMotion(player.getCentreX(), player.getCentreY(), player.getXSubpixelRaw(),
                    player.getYSubpixelRaw(), player.getXSpeed(), player.getYSpeed(), player.getGSpeed(),
                    player.getDirection(), player.getAnimationId(), player.getAir(), player.getRolling());
        }
    }

    private record LauncherProgress(Object routine, int timer, int offset, int y, int frame, int priority,
                                    boolean childSpawned, boolean solidActive, int checkpoints) {
        static LauncherProgress read(ProbeHandLauncher launcher) throws ReflectiveOperationException {
            return new LauncherProgress(handField(launcher, "state"), (int) handField(launcher, "timer"),
                    launcher.getYOffset(), launcher.getY(), (int) handField(launcher, "mappingFrame"),
                    launcher.getPriorityBucket(), (boolean) handField(launcher, "childSpawned"),
                    (boolean) handField(launcher, "solidActive"), launcher.checkpointYs().size());
        }
    }

    private static Object handField(ProbeHandLauncher launcher, String name) throws ReflectiveOperationException {
        var field = HCZHandLauncherObjectInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(launcher);
    }

    private static ProbeHandLauncher buildLauncher(TestablePlayableSprite player) {
        ObjectSpawn spawn = new ObjectSpawn(0x0208, 0x0100, 0x3A, 0, 0, false, 0);
        ProbeHandLauncher launcher = new ProbeHandLauncher(spawn);
        launcher.setServices(new TestObjectServices());
        launcher.setCheckpointBatch(standingBatch(launcher, player));
        return launcher;
    }

    private static void updateUntilGrabbed(ProbeHandLauncher launcher, TestablePlayableSprite player) {
        for (int frame = 0; frame < 16 && !player.isObjectControlled(); frame++) {
            launcher.update(frame, player);
        }
        assertTrue(player.isObjectControlled(), "Hand launcher should grab player from standing checkpoint");
    }

    private static void updateUntilGrabbed(
            ProbeHandLauncher launcher,
            TestablePlayableSprite updatePlayer,
            TestablePlayableSprite grabbedPlayer) {
        for (int frame = 0; frame < 16 && !grabbedPlayer.isObjectControlled(); frame++) {
            launcher.update(frame, updatePlayer);
        }
        assertTrue(grabbedPlayer.isObjectControlled(), "Hand launcher should grab native P2 from standing checkpoint");
    }

    private static SolidCheckpointBatch standingBatch(
            HCZHandLauncherObjectInstance launcher,
            TestablePlayableSprite... players) {
        PlayerSolidContactResult result = new PlayerSolidContactResult(
                ContactKind.TOP,
                true,
                false,
                false,
                false,
                PreContactState.ZERO,
                new PostContactState((short) 0, (short) 0, false, true, false),
                0);
        Map<PlayableEntity, PlayerSolidContactResult> contacts = new java.util.IdentityHashMap<>();
        for (TestablePlayableSprite player : players) {
            contacts.put(player, result);
        }
        return new SolidCheckpointBatch(launcher, contacts);
    }

    private static final class ProbeHandLauncher extends HCZHandLauncherObjectInstance {
        private SolidCheckpointBatch retainedBatch = new SolidCheckpointBatch(this, Map.of());
        private SolidCheckpointBatch checkpointBatch = new SolidCheckpointBatch(this, Map.of());
        private final List<Integer> checkpointYs = new java.util.ArrayList<>();

        private ProbeHandLauncher(ObjectSpawn spawn) {
            super(spawn);
        }

        private void setCheckpointBatch(SolidCheckpointBatch checkpointBatch) {
            this.checkpointBatch = checkpointBatch;
        }

        private List<Integer> checkpointYs() {
            return checkpointYs;
        }

        @Override
        protected boolean hasRetainedStandingBit(com.openggf.sprites.playable.AbstractPlayableSprite player) {
            var contact = retainedBatch.perPlayer().get(player);
            return contact != null && contact.standingNow();
        }

        @Override
        protected SolidCheckpointBatch checkpointAll() {
            retainedBatch = checkpointBatch;
            checkpointYs.add(getY());
            return checkpointBatch;
        }
    }

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
        private final PlayableEntity main;
        private List<? extends PlayableEntity> queriedSidekicks;

        private QueryOnlyPlayerServices(PlayableEntity main, List<? extends PlayableEntity> queriedSidekicks) {
            this.main = main;
            this.queriedSidekicks = List.copyOf(queriedSidekicks);
        }

        private void setSidekicks(List<? extends PlayableEntity> sidekicks) {
            this.queriedSidekicks = List.copyOf(sidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> queriedSidekicks);
        }

        @Override
        public List<PlayableEntity> sidekicks() {
            return List.of();
        }
    }
}

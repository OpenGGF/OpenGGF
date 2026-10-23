package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
            // HCZHandLauncher_WaitForGrab subtracts 8 and branches straight to
            // HCZHandLauncher_UpdateSolid, which writes y_pos before
            // SolidObjectTop (sonic3k.asm:65809-65838).
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
        // HCZHandLauncher_UpdateSolid (sonic3k.asm:65824-65838).
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
        // (sonic3k.asm:65819-65821, 65971-65973).
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
        // $30 <= $18 (sonic3k.asm:65868-65879); the release pass already
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
        protected SolidCheckpointBatch checkpointAll() {
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

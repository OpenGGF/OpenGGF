package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SszArrivalControllerObjectInstance;
import com.openggf.game.sonic3k.objects.SszTailsArrivalHelperObjectInstance;
import com.openggf.level.Palette;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Native sub_45866, loc_57D18/CreateNewSprite4 and loc_57DB6; no imported gameplay rows. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszArrivalContinuation {
    @AfterEach
    void reset() {
        GameServices.configuration().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void nativeContinuationChoosesSlotThreeAndStartsOnTheFollowingPass(int width) {
        var fixture = boot(width, "sonic", "tails");
        fixture.stepIdleFrames(109);
        var helper = helper();
        assertNotNull(helper, "loc_57D18 admits Obj_57DCC");
        assertEquals(3, helper.getSlotIndex(), "Player2 and collision reset precede native empty slot3");
        // This headless fixture preloads placements; the independently cold production scene
        // has native controller4. The relevant execution predicate is that helper3 is behind it.
        assertTrue(controller().getSlotIndex() > helper.getSlotIndex());
        var tails = GameServices.sprites().getRegisteredSidekicks().getFirst();
        for (int pass = 0; pass < 12; pass++) {
            assertEquals(0x7F00, tails.getCentreX() & 65535, "no early helper release pass " + pass);
            assertEquals(0, tails.getCentreY() & 65535);
            fixture.stepIdleFrames(1);
        }
        assertEquals(0x100, tails.getCentreX() & 65535);
        assertEquals(0xC4E, tails.getCentreY() & 65535,
                "the twelfth real helper dispatch falls through with the leader release base");
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void arrivalOwnsTheNativeFourPassLightDividerAndLiteralPairs(int width) {
        var fixture = boot(width, "sonic", "tails");
        // First sub_45866 advances cleared index0 to4; timer3 holds it for three further passes.
        fixture.stepIdleFrames(1);
        pair(0x60A, 0x60C);
        fixture.stepIdleFrames(3);
        pair(0x60A, 0x60C);
        fixture.stepIdleFrames(1);
        pair(0x80C, 0x80E);
        fixture.stepIdleFrames(104);
        pair(0x80C, 0x80E); // 109th pass: index20, independently observed D0low20.
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void tailsMainWithConfiguredFollowersDoesNotTakeNativePlayerModeZeroBranch(int width) {
        var fixture = boot(width, "tails", "sonic");
        fixture.stepIdleFrames(109);
        assertNull(helper(), "native Player_mode2 branches around CreateNewSprite4");
        assertEquals("tails", GameServices.sprites().getMainPlayable().characterKey().persisted());
        assertEquals("sonic", GameServices.sprites().getRegisteredSidekicks().getFirst().characterKey().persisted());
    }

    @Test
    void settleKeepsTheControllerThroughTheLastPaletteWriteThenRetires() throws Exception {
        var fixture = boot(320, "sonic", "tails");
        fixture.stepIdleFrames(13); // native divider/index16, timer3.
        var owner = controller();
        owner.onBeamFinished(); // Declared beam completion stimulus, not cold traversal evidence.
        var phase = SszArrivalControllerObjectInstance.class.getDeclaredField("phase");
        phase.setAccessible(true);
        phase.setInt(owner, SszArrivalControllerObjectInstance.PHASE_SETTLE);
        fixture.camera().setMaxY(fixture.camera().getY());
        fixture.camera().setMinY(fixture.camera().getY());
        // This local native phase inlet isolates loc_57DB6's predicate and fallthrough ordering.
        for (int pass = 1; pass <= 24; pass++) {
            fixture.stepIdleFrames(1);
            assertFalse(owner.isDestroyed(), "nonzero cycle survives; zero write is still this dispatch " + pass);
        }
        pair(0x408, 0x40C);
        fixture.stepIdleFrames(1);
        assertTrue(owner.isDestroyed(), "next loc_57DB6 sees both index and active byte zero");
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void actualArrivalBirthAndLightStateRecreateAndReplayEveryRegisteredOwnerTwice(int width) {
        var fixture = boot(width, "sonic", "tails");
        fixture.stepIdleFrames(107);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var objects = GameServices.level().getObjectManager();
        var runtime = GameServices.zoneRuntimeState();
        int lives = GameServices.gameState().getLives();
        var before = registry.capture();
        Runnable advance = () -> {
            fixture.stepIdleFrames(45);
            assertSame(objects, GameServices.level().getObjectManager());
            assertSame(runtime, GameServices.zoneRuntimeState());
            assertEquals(lives, GameServices.gameState().getLives());
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(3, assertNotNullHelper().getSlotIndex());
        };
        advance.run();
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before); same(before, registry.capture());
            advance.run(); same(after, registry.capture());
            registry.restore(before); same(before, registry.capture());
            registry.restore(after); same(after, registry.capture());
        }
    }

    @Test
    void occupiedIdZeroSlotThreeUsesTheNextFreeProbeAndSamePassExecution() throws Exception {
        var fixture = boot(320, "sonic", "tails");
        fixture.stepIdleFrames(108);
        occupyThrough(30);
        fixture.stepIdleFrames(1);
        var helper = assertNotNullHelper();
        assertEquals(31, helper.getSlotIndex(), "live id0 owner at3 still has a nonzero native code header");
        assertEquals(11, delay(helper), "a later slot executes in its actual birth pass");
    }

    @Test
    void theLastProbeThirtyTwoIsAdmitted() throws Exception {
        var fixture = boot(320, "sonic", "tails");
        fixture.stepIdleFrames(108);
        occupyThrough(31);
        fixture.stepIdleFrames(1);
        assertEquals(32, assertNotNullHelper().getSlotIndex(), "DBEQ scans exactly32 headers from Player1");
        assertEquals(11, delay(assertNotNullHelper()));
    }

    @Test
    void occupiedPrefixThroughThirtyTwoCannotFallBackToFreeThirtyThree() {
        var fixture = boot(320, "sonic", "tails");
        fixture.stepIdleFrames(108);
        occupyThrough(32);
        fixture.stepIdleFrames(1);
        assertNull(helper(), "exhausted carried D0 budget does not call AllocateObjectAfterCurrent");
        assertEquals(SszArrivalControllerObjectInstance.PHASE_SWING, controller().phaseForTest());
        assertFalse(fixture.camera().getFrozen(), "native failed-child branch still releases the leader");
    }

    private static int delay(SszTailsArrivalHelperObjectInstance helper) throws Exception {
        var field = SszTailsArrivalHelperObjectInstance.class.getDeclaredField("delay");
        field.setAccessible(true); return field.getInt(helper);
    }
    private static void occupyThrough(int last) {
        var manager = GameServices.level().getObjectManager();
        for (int slot = 3; slot <= last; slot++) {
            if (com.openggf.level.objects.NativeSstSlotAccess.currentOwner(manager, slot) != null) continue;
            boolean managed = com.openggf.level.objects.ObjectSlotLayout.SONIC_3K.isDynamicSlot(slot);
            if (managed && !manager.reserveDynamicSlot(slot)) continue;
            int target = slot;
            var blocker = com.openggf.level.objects.ObjectConstructionContext.with(manager.getObjectServices(), target,
                    () -> new DeclaredNonzeroHeader(new com.openggf.level.objects.ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
            manager.addDynamicObjectAtSlot(blocker, target);
        }
    }
    private static final class DeclaredNonzeroHeader extends com.openggf.level.objects.AbstractObjectInstance
            implements com.openggf.level.objects.RewindRecreatable {
        DeclaredNonzeroHeader(com.openggf.level.objects.ObjectSpawn spawn) { super(spawn, "declared occupied SST"); }
        @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
        @Override public DeclaredNonzeroHeader recreateForRewind(com.openggf.level.objects.RewindRecreateContext context) {
            return new DeclaredNonzeroHeader(context.spawn());
        }
    }

    private static SszTailsArrivalHelperObjectInstance assertNotNullHelper() {
        var value = helper(); assertNotNull(value); return value;
    }
    private static SszTailsArrivalHelperObjectInstance helper() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(SszTailsArrivalHelperObjectInstance.class::isInstance)
                .map(SszTailsArrivalHelperObjectInstance.class::cast).findFirst().orElse(null);
    }
    private static SszArrivalControllerObjectInstance controller() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(SszArrivalControllerObjectInstance.class::isInstance)
                .map(SszArrivalControllerObjectInstance.class::cast).findFirst().orElseThrow();
    }
    private static void pair(int first, int second) {
        var palette = GameServices.level().getCurrentLevel().getPalette(2);
        color(palette.getColor(0xC), first); color(palette.getColor(0xD), second);
    }
    private static void color(Palette.Color actual, int word) {
        var expected = new Palette.Color(); expected.fromSegaFormat(word);
        assertEquals(expected.r & 255, actual.r & 255, "red for native word " + Integer.toHexString(word));
        assertEquals(expected.g & 255, actual.g & 255, "green for native word " + Integer.toHexString(word));
        assertEquals(expected.b & 255, actual.b & 255, "blue for native word " + Integer.toHexString(word));
    }
    private static void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> key + ": " + diff);
        }
    }
    private static HeadlessTestFixture boot(int width, String main, String followers) {
        var config = GameServices.configuration(); config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, followers);
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                java.util.Arrays.stream(WidescreenAspect.values()).filter(a -> a.pixelWidth() == width)
                        .findFirst().orElseThrow().name());
        config.resolveDisplayAspect();
        CrossGameFeatureProvider.getInstance().resetState(); SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).withFreshLevelStartLifecycle().build();
        assertEquals(width, fixture.camera().getWidth() & 65535, "actual supported viewport preset");
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        assertEquals(108, controller().riseRemainingForTest());
        return fixture;
    }
}

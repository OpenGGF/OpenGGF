package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.difficulty.AizTreeExitRecipe;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.badniks.MonkeyDudeBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.MonkeyDudeArmObjectInstance;
import com.openggf.game.sonic3k.objects.badniks.S3kBadnikProjectileInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SkidDustObjectInstance;
import com.openggf.mods.objects.AizHollowTreeFactory;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native tree contract witnesses, deliberately separate from full cold-route qualification. */
@RequiresRom(SonicGame.SONIC_3K)
class TestAizExitLookout {
    private static final ObjectSpawn TREE = new ObjectSpawn(0x2D00, 0x3CC, 3, 0, 0, false, 0, 150);

    @AfterEach void reset() {
        GameServices.configuration().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"sonic,''", "sonic,tails", "tails,''", "knuckles,''"})
    void completedNativeRideAndRealLandingAdmitOneSeparatedLookout(String main, String sidekick) {
        var fixture = boot(main, sidekick);
        var player = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        var tree = create(fixture, AizTreeExitRecipe.EXIT_LOOKOUT);
        completeRide(tree, player);
        assertFalse(player.isObjectControlled());
        assertEquals(0, lookouts(), "no actor during the tree ride or airborne release");
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        int admissionFrames = advanceToLookout(fixture);
        assertFalse(player.getAir(), "the actual ROM exit landing must be reached");
        assertEquals(1, lookouts(), "one native lookout after real control-clear landing");
        var lookout = objects.getActiveObjects().stream().filter(MonkeyDudeBadnikInstance.class::isInstance)
                .map(MonkeyDudeBadnikInstance.class::cast).filter(o -> o.getSpawn().layoutIndex() == -1).findFirst().orElseThrow();
        assertEquals(0x2DE0, lookout.getX()); assertEquals(0x31C, lookout.getY());
        assertTrue(Math.abs(player.getCentreX() - lookout.getX()) > 48
                || Math.abs(player.getCentreY() - lookout.getY()) > 64);
        assertFalse(player.isHurt(), "the admitted native body cannot cause unavoidable birth damage");
        for (var participant : objects.getObjectServices().playerQuery().playersFor(
                com.openggf.level.objects.ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            assertTrue(Math.abs(participant.getCentreX() - lookout.getX()) > 48
                    || Math.abs(participant.getCentreY() - lookout.getY()) > 64);
        }
        for (int tick = 0; tick < 8; tick++) tree.update(241 + tick, player);
        assertEquals(1, lookouts(), "repeated landing updates cannot duplicate the actor");
        // Retire the actual graph before restoring the registered world/player
        // state. Replay the same ordinary right input through real landing.
        objects.removeDynamicObject(tree);
        finishRequestedRetirement(fixture);
        registry.restore(before);
        var restored = objects.getActiveObjects().stream().filter(AizHollowTreeObjectInstance.class::isInstance)
                .map(AizHollowTreeObjectInstance.class::cast).filter(o -> !o.isDestroyed()).findFirst().orElseThrow();
        assertEquals(admissionFrames, advanceToLookout(fixture));
        assertEquals(1, lookouts());
        restored.onUnload();
        finishRequestedRetirement(fixture);
        assertEquals(0, lookouts(), "native tree retirement retires its admitted lookout without rewards");
    }

    @Test void bypassAndEarlyFallDoNotEarnTheLookoutAndStockNeverAddsOne() {
        var fixture = boot("sonic", ""); var player = fixture.sprite();
        var tree = create(fixture, AizTreeExitRecipe.EXIT_LOOKOUT);
        player.setCentreX((short) 0x2D80); player.setCentreY((short) 0x33A);
        player.setAir(false); tree.update(0, player);
        assertEquals(0, lookouts(), "walking/flight/glide bypass cannot trigger a ride-completion actor");
        prepare(player); tree.update(1, player);
        assertTrue(player.isObjectControlled());
        player.setGSpeed((short) 0); tree.update(2, player);
        player.setCentreX((short) 0x2D80); player.setCentreY((short) 0x33A); player.setAir(false);
        tree.update(3, player); assertEquals(0, lookouts(), "an early failed ride has no exit ambush");
        var stock = create(fixture, AizTreeExitRecipe.STOCK);
        completeRide(stock, player);
        for (int frame = 0; frame < 12 && player.getAir(); frame++) fixture.stepFrame(false, false, false, true, false);
        stock.update(240, player); assertEquals(0, lookouts());
    }

    @Test void occupiedBirthAnchorSkipsOnceInsteadOfOverlappingOrTrackingP1() {
        var fixture = boot("sonic", ""); var player = fixture.sprite();
        var tree = create(fixture, AizTreeExitRecipe.EXIT_LOOKOUT);
        completeRide(tree, player);
        // This is an explicit occupied-admission state, not route evidence.
        player.setCentreX((short) 0x2DE0); player.setCentreY((short) 0x33A); player.setAir(false);
        tree.update(240, player); assertEquals(0, lookouts());
        player.setCentreX((short) 0x2D80);
        tree.update(241, player); assertEquals(0, lookouts(), "the one skipped opportunity never follows a player");
    }

    @Test void occupiedNativeP2BirthAnchorSkipsOnce() {
        var fixture = boot("sonic", "tails"); var player = fixture.sprite();
        var tree = create(fixture, AizTreeExitRecipe.EXIT_LOOKOUT);
        completeRide(tree, player);
        var p2 = assertInstanceOf(AbstractPlayableSprite.class,
                GameServices.level().getObjectManager().getObjectServices().playerQuery().nativeP2OrNull(),
                "native team fixture must register P2");
        p2.setCentreX((short) 0x2DE0); p2.setCentreY((short) 0x31C);
        player.setCentreX((short) 0x2D80); player.setCentreY((short) 0x33A); player.setAir(false);
        tree.update(240, player); assertEquals(0, lookouts());
        p2.setCentreX((short) 0x2C00);
        tree.update(241, player); assertEquals(0, lookouts(), "no delayed tracking spawn after occupied P2 leaves");
    }

    @Test void lateLowSpeedFallDoesNotCountAsTheNativeUpperExit() {
        var fixture = boot("sonic", ""); var player = fixture.sprite();
        var tree = create(fixture, AizTreeExitRecipe.EXIT_LOOKOUT);
        prepare(player); tree.update(0, player);
        int tick = 1;
        for (; tick < 240 && progress(tree) < 0x400; tick++) tree.update(tick, player);
        assertTrue(player.isObjectControlled());
        assertTrue(progress(tree) >= 0x400, "late-fall precondition must reach non-default ride progress");
        player.setGSpeed((short) 0); tree.update(tick, player);
        tree.update(tick + 1, player);
        assertFalse(player.isObjectControlled());
        player.setCentreX((short) 0x2D80); player.setCentreY((short) 0x33A); player.setAir(false);
        tree.update(tick + 2, player);
        assertEquals(0, lookouts(), "a late failed ride did not take the native negative-Y upper exit");
    }

    @ParameterizedTest
    @CsvSource({"47,0", "48,0", "49,1"})
    void nativeBodyRespectsTheConservativeP2BirthBoundary(int offset, int expected) throws java.io.IOException {
        var fixture = boot("sonic", "tails");
        var player = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        var tree = create(fixture, AizTreeExitRecipe.EXIT_LOOKOUT);
        completeRide(tree, player);
        var p2 = assertInstanceOf(AbstractPlayableSprite.class,
                objects.getObjectServices().playerQuery().nativeP2OrNull());
        prepareBoundaryLanding(player, p2, offset);
        var renderer = objects.getObjectServices().renderManager().getRenderer(Sonic3kObjectArtKeys.MONKEY_DUDE);
        assertNotNull(renderer);
        assertTrue(renderer.isReady(), "the actual ROM renderer must be ready at the admission attempt");
        var touch = new com.openggf.level.objects.TouchResponseTable(
                com.openggf.data.RomByteReader.fromRom(com.openggf.tests.TestEnvironment.currentRom()),
                com.openggf.game.sonic3k.constants.Sonic3kConstants.TOUCH_SIZES_ADDR,
                com.openggf.game.sonic3k.constants.Sonic3kConstants.TOUCH_SIZES_COUNT);
        assertEquals(8, touch.getWidthRadius(0x0B));
        assertEquals(8, touch.getHeightRadius(0x0B));
        // Normal native player half-width8 + body half-width8 is well inside
        // the 48px admission span; the body never turns or shifts its origin.
        // Positioning this boundary is a local mechanism witness, not a route.
        tree.update(240, player);
        assertEquals(expected, lookouts());
        if (expected == 0) {
            p2.setCentreX((short) 0x2C00);
            objects.update(GameServices.camera().getX(), player, java.util.List.of(p2), 241, false);
            assertEquals(0, lookouts(), "occupied birth is skipped once, never deferred or tracked");
            return;
        }
        var lookout = objects.getActiveObjects().stream().filter(MonkeyDudeBadnikInstance.class::isInstance)
                .map(MonkeyDudeBadnikInstance.class::cast).filter(o -> o.getSpawn().layoutIndex() == -1)
                .findFirst().orElseThrow();
        assertEquals(0x2DE0, lookout.getCollisionX(), "construction retains the original left-facing origin");
        // MonkeyDude_Wait (loc_85652: Animate_Raw/Obj_Wait) and MonkeyDude_Active
        // (sonic3k.asm:182825-182880) never write body x_pos or render_flags; only
        // the arm root targets P1. The body keeps its placement origin while P1 is
        // to the right, so 48 remains a conservative, not a tight, P2 birth span.
        boolean published = false;
        for (int tick = 241; tick <= 246; tick++) {
            objects.snapshotTouchResponseState();
            objects.runTouchResponsesForPlayer(p2, tick, true);
            assertFalse(p2.isHurt(), "P2 must be separated in the native player-slot touch pass");
            objects.update(GameServices.camera().getX(), player, java.util.List.of(p2), tick, false);
            objects.runTouchResponsesForPlayer(p2, tick, false);
            assertFalse(p2.isHurt(), "P2 must also be separated after native body publication");
            assertEquals(0x2DE0, lookout.getCollisionX(), "the native body never turns toward P1");
            published |= lookout.getCollisionFlags() == 0x0B;
        }
        assertTrue(published, "real release/init/wait dispatches must publish the native body collision");
        assertEquals(0x2DE0 + 49, p2.getCentreX());
        assertEquals(0x31C, p2.getCentreY());
        assertEquals(0x2DE0, lookout.getCollisionX());
        assertTrue(lookout.isOnScreenForTouch(), "the harmless49px result must not come from offscreen culling");
        assertFalse(lookout.isDestroyed());
    }

    @ParameterizedTest
    @CsvSource({"47,false", "0,true"})
    void ordinaryNativeBodyKeepsItsPlacementOriginAndHurtsOnlyAnOverlappingP2(int offset, boolean hurt) {
        var fixture = boot("sonic", "tails");
        var player = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        var p2 = assertInstanceOf(AbstractPlayableSprite.class,
                objects.getObjectServices().playerQuery().nativeP2OrNull());
        prepareBoundaryLanding(player, p2, offset);
        var body = new MonkeyDudeBadnikInstance(new ObjectSpawn(0x2DE0, 0x31C,
                com.openggf.game.sonic3k.constants.Sonic3kObjectIds.MONKEY_DUDE, 0x10, 0, false, 0x31C));
        objects.addDynamicObject(body);
        for (int tick = 0; tick < 6 && !p2.isHurt(); tick++) {
            objects.update(GameServices.camera().getX(), player, java.util.List.of(p2), tick, false);
            objects.runTouchResponsesForPlayer(p2, tick, false);
        }
        // MonkeyDude_Wait/MonkeyDude_Active never write x_pos or render_flags.
        assertEquals(0x2DE0, body.getCollisionX(), "the native body keeps its placement origin");
        assertEquals(hurt, p2.isHurt(), "native body contact is harmful during its AI wait only on real overlap");
        assertFalse(body.isDestroyed(), "non-attacking P2 must hurt rather than defeat the native body");
    }

    private static void prepareBoundaryLanding(AbstractPlayableSprite player, AbstractPlayableSprite p2, int offset) {
        ObjectControlState.none().applyTo(player);
        player.setControlLocked(false); player.setRolling(false);
        // The cold native rider stands at x=$2E40 on its real exit ledge.
        // Query that sloped surface instead of assuming the old $33A Y.
        // P1 remains to the right, where the arm root targets it; the body
        // keeps its placement origin and P2 supplies the contact boundary.
        player.setCentreX((short) 0x2E40);
        var services = GameServices.level().getObjectManager().getObjectServices();
        var landing = services.findFloorSurface(0x2E40, 0x350, 32, 32,
                com.openggf.level.objects.TerrainSurface.Layer.FOREGROUND,
                com.openggf.level.objects.TerrainSurface.Path.PRIMARY);
        assertTrue(landing.foundSurface(), "the actual ROM upper-exit landing must exist");
        player.setCentreY((short) (landing.surfaceY() - player.getYRadius()));
        player.setAir(false); player.setXSpeed((short) 0); player.setGSpeed((short) 0);
        var floor = services.floorSurface(
                player.getCentreX(), player.getCentreY() + player.getYRadius(),
                com.openggf.level.objects.TerrainSurface.Layer.FOREGROUND,
                com.openggf.level.objects.TerrainSurface.Path.PRIMARY);
        assertTrue(floor.foundSurface());
        assertTrue(Math.abs(floor.distance()) <= 1, "P1 must stand on the actual native exit landing");
        ObjectControlState.none().applyTo(p2);
        p2.setControlLocked(false); p2.setRolling(false);
        p2.setAnimationId(com.openggf.game.sonic3k.constants.Sonic3kAnimationIds.WAIT.id());
        p2.setCentreX((short) (0x2DE0 + offset)); p2.setCentreY((short) 0x31C);
        p2.setAir(true); p2.setXSpeed((short) 0); p2.setYSpeed((short) 0);
        p2.setInvulnerableFrames(0); p2.setInvincibleFrames(0); p2.setHurt(false);
        assertFalse(p2.isTouchResponseSuppressedByObjectControl());
        GameServices.camera().updatePosition(true);
    }

    @Test void progressedBornLookoutRelinksAndContinuesOnceAfterDestructiveWorldRestore() {
        var fixture = boot("sonic", "tails");
        var player = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var tree = create(fixture, AizTreeExitRecipe.EXIT_LOOKOUT);
        completeRide(tree, player);
        advanceToLookout(fixture);
        var born = nativeLookout();
        assertSame(born, readField(tree, "exitLookout"));
        assertEquals(Boolean.TRUE, readField(tree, "exitLookoutAttempted"));
        // Ordinary left input brakes the actual rider after its real landing.
        // Let native release/init/arm animation progress before the keyframe.
        for (int frame = 0; frame < 8; frame++) fixture.stepFrame(false, false, true, false, false);
        assertSame(born, nativeLookout());
        assertEquals(Boolean.TRUE, readField(born, "armRootActivated"));
        assertEquals(Boolean.FALSE, readField(born, "initPending"));
        assertNotEquals(0, readField(born, "animIndex"), "capture a progressed native arm/body animation");
        var registry = fixture.gameplayMode().getRewindRegistry();
        var snapshot = registry.capture();
        var captured = objects.rewindSnapshottable().capture();
        var expected = new java.util.ArrayList<BornFrame>();
        for (int frame = 0; frame < 20; frame++) {
            fixture.stepFrame(false, false, true, false, false);
            expected.add(bornFrame(fixture));
            assertEquals(1, lookouts(), "the native owner cannot birth a second lookout");
        }
        int bornSlot = born.getSlotIndex();
        java.util.List.copyOf(objects.getActiveObjects()).forEach(objects::removeDynamicObject);
        assertNull(NativeSstSlotAccess.currentOwner(objects, bornSlot),
                "this destructive restore setup removes the actual old physical owner");
        registry.restore(snapshot);
        var restored = objects.getActiveObjects().stream()
                .filter(AizHollowTreeObjectInstance.class::isInstance)
                .map(AizHollowTreeObjectInstance.class::cast)
                .filter(candidate -> !candidate.isDestroyed()).findFirst().orElseThrow();
        var restoredBorn = nativeLookout();
        assertNotSame(tree, restored);
        assertNotSame(born, restoredBorn);
        assertSame(restoredBorn, readField(restored, "exitLookout"), "reference must close over the recreated native child");
        assertEquals(AizTreeExitRecipe.EXIT_LOOKOUT, readField(restored, "exitRecipe"));
        assertEquals(Boolean.TRUE, readField(restored, "exitLookoutAttempted"));
        assertEquals(born.getSpawn(), restoredBorn.getSpawn(), "retain the original tree-relative native identity");
        assertEquals(captured.dynamicObjects(), objects.rewindSnapshottable().capture().dynamicObjects(),
                "restore every captured native field, primitive arm array and object reference");
        assertArrayEquals(captured.usedSlotsBits(), objects.rewindSnapshottable().capture().usedSlotsBits());
        for (int frame = 0; frame < expected.size(); frame++) {
            fixture.stepFrame(false, false, true, false, false);
            assertEquals(expected.get(frame), bornFrame(fixture), "complete native graph/player replay frame " + frame);
            assertEquals(1, lookouts());
        }
        restored.onUnload();
        assertFalse(restoredBorn.isDestroyed(), "withdrawal must first publish at the body's own dispatch");
        finishRequestedRetirement(fixture);
        assertTrue(restoredBorn.isDestroyed());
        assertEquals(0, lookouts(), "restored owner retirement uses the rebound child reference");
    }

    @ParameterizedTest
    @CsvSource({"0", "1"})
    void actualSlotExhaustionOrOneFreeSlotKeepsOneRestorableAdmissionDecision(int freeSlots) {
        var fixture = boot("sonic", "tails");
        var player = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var tree = create(fixture, AizTreeExitRecipe.EXIT_LOOKOUT);
        completeRide(tree, player);
        var p2 = assertInstanceOf(AbstractPlayableSprite.class,
                objects.getObjectServices().playerQuery().nativeP2OrNull());
        prepareBoundaryLanding(player, p2, 49);
        var renderer = objects.getObjectServices().renderManager().getRenderer(Sonic3kObjectArtKeys.MONKEY_DUDE);
        assertNotNull(renderer); assertTrue(renderer.isReady());
        var fillers = new java.util.ArrayList<com.openggf.level.objects.AbstractObjectInstance>();
        int capacity = objects.getObjectSlotCapacity();
        while (capacity - objects.getActiveObjectSlotCount() > freeSlots) {
            assertTrue(fillers.size() < capacity, "fill actual managed SST slots with a bounded fixture graph");
            fillers.add(objects.createDynamicObject(SlotFiller::new));
        }
        int before = objects.getActiveObjectSlotCount();
        int score = GameServices.gameState().getScore(), lives = GameServices.gameState().getLives();
        tree.update(240, player);
        assertEquals(freeSlots, lookouts(), "one free native slot admits one body; zero admits none");
        assertEquals(before + freeSlots, objects.getActiveObjectSlotCount());
        assertEquals(Boolean.TRUE, readField(tree, "exitLookoutAttempted"));
        if (freeSlots == 0) assertNull(readField(tree, "exitLookout"));
        else assertSame(nativeLookout(), readField(tree, "exitLookout"));
        fillers.forEach(objects::removeDynamicObject);
        assertTrue(objects.hasFreeDynamicSlot());
        tree.update(241, player);
        assertEquals(freeSlots, lookouts(), "new capacity cannot retry the semantic release opportunity");
        // Fillers are gone before capture. Restore only the actual native world,
        // including the failed-attempt flag or successfully allocated child.
        var registry = fixture.gameplayMode().getRewindRegistry();
        var snapshot = registry.capture();
        objects.removeDynamicObject(tree);
        finishRequestedRetirement(fixture);
        assertEquals(0, lookouts());
        registry.restore(snapshot);
        var restored = objects.getActiveObjects().stream()
                .filter(AizHollowTreeObjectInstance.class::isInstance)
                .map(AizHollowTreeObjectInstance.class::cast)
                .filter(candidate -> !candidate.isDestroyed()).findFirst().orElseThrow();
        assertNotSame(tree, restored);
        assertEquals(Boolean.TRUE, readField(restored, "exitLookoutAttempted"));
        assertEquals(freeSlots, lookouts());
        if (freeSlots == 0) assertNull(readField(restored, "exitLookout"));
        else assertSame(nativeLookout(), readField(restored, "exitLookout"));
        restored.update(242, player);
        assertEquals(freeSlots, lookouts());
        restored.onUnload();
        finishRequestedRetirement(fixture);
        assertEquals(0, lookouts());
        assertEquals(0, player.getRingCount());
        assertEquals(score, GameServices.gameState().getScore());
        assertEquals(lives, GameServices.gameState().getLives());
    }

    @Test void laterTreeSlotClosesItsFiveArmsBeforeSkidDustReusesTheBodySlot() {
        var scene = retirementScene(false);
        var objects = GameServices.level().getObjectManager();
        int score = GameServices.gameState().getScore(), lives = GameServices.gameState().getLives();
        int rings = scene.fixture().sprite().getRingCount();
        requestFromLaterTreeSlot(scene);
        scene.tree().onUnload(); // repeated withdrawal must not advance native retirement
        assertRequestedGraph(scene.body(), scene.arms());
        stepObjects(scene.fixture(), scene.nextFrame() + 1);
        assertPublishedGraph(scene.body(), scene.arms());
        scene.tree().onUnload(); // a published body must not acquire a second request
        assertEquals(Boolean.FALSE, readField(scene.body(), "ownedRetirementRequested"));
        stepObjects(scene.fixture(), scene.nextFrame() + 2);
        assertClosedGraph(objects, scene.body(), scene.arms());

        // Use the real effect producer and normal allocator after the graph is
        // closed. A stale physical parent3 link would see this actual dust owner.
        SkidDustObjectInstance.spawn(scene.fixture().sprite());
        assertInstanceOf(SkidDustObjectInstance.class, NativeSstSlotAccess.currentOwner(objects, 4));
        assertDoesNotThrow(() -> stepObjects(scene.fixture(), scene.nextFrame() + 3));
        assertTrue(lookoutArms(objects).isEmpty(), "no orphan routine may read the reused body slot");
        assertEquals(score, GameServices.gameState().getScore());
        assertEquals(lives, GameServices.gameState().getLives());
        assertEquals(rings, scene.fixture().sprite().getRingCount());
    }

    @Test void requestedAndPublishedGraphRetirementRecreateAndReplayWithoutDeletingTheCoconut() {
        var scene = retirementScene(true);
        var fixture = scene.fixture();
        var objects = GameServices.level().getObjectManager();
        var registry = fixture.gameplayMode().getRewindRegistry();
        int coconutSlot = scene.coconut().getSlotIndex();
        int coconutX = scene.coconut().getX();
        requestFromLaterTreeSlot(scene);
        var requested = registry.capture();
        stepObjects(fixture, scene.nextFrame() + 1);
        assertPublishedGraph(scene.body(), scene.arms());
        var published = registry.capture();
        stepObjects(fixture, scene.nextFrame() + 2);
        assertClosedGraph(objects, scene.body(), scene.arms());
        var coconut = assertInstanceOf(S3kBadnikProjectileInstance.class,
                NativeSstSlotAccess.currentOwner(objects, coconutSlot));
        assertFalse(coconut.isDestroyed(), "the detached native projectile has its own lifetime");
        assertNotEquals(coconutX, coconut.getX(), "ordinary native projectile motion must continue");
        var after = registry.capture();

        // Recreate the complete registered world, including the request latch,
        // physical arm links and already committed independent projectile.
        java.util.List.copyOf(objects.getActiveObjects()).forEach(objects::removeDynamicObject);
        registry.restore(requested);
        sameWorld(requested, registry.capture());
        var recreated = nativeLookout();
        assertNotSame(scene.body(), recreated);
        assertSame(recreated, NativeSstSlotAccess.currentOwner(objects, 4));
        assertRequestedGraph(recreated, lookoutArms(objects));
        stepObjects(fixture, scene.nextFrame() + 1);
        sameWorld(published, registry.capture());
        stepObjects(fixture, scene.nextFrame() + 2);
        sameWorld(after, registry.capture());

        java.util.List.copyOf(objects.getActiveObjects()).forEach(objects::removeDynamicObject);
        registry.restore(published);
        sameWorld(published, registry.capture());
        assertPublishedGraph(nativeLookout(), lookoutArms(objects));
        stepObjects(fixture, scene.nextFrame() + 2);
        sameWorld(after, registry.capture());
        assertInstanceOf(S3kBadnikProjectileInstance.class,
                NativeSstSlotAccess.currentOwner(objects, coconutSlot));
    }

    private record RetirementScene(HeadlessTestFixture fixture, AizHollowTreeObjectInstance tree,
                                   MonkeyDudeBadnikInstance body,
                                   java.util.List<MonkeyDudeArmObjectInstance> arms,
                                   S3kBadnikProjectileInstance coconut, int nextFrame) { }

    private static RetirementScene retirementScene(boolean waitForCoconut) {
        var fixture = boot("sonic", "");
        var objects = GameServices.level().getObjectManager();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        // Isolate the local mechanism in the actual ROM-loaded world. Existing
        // placed actors are retired before this declared component setup; this
        // is not a cold-route or native-entry qualification.
        for (var object : java.util.List.copyOf(objects.getActiveObjects())) {
            if (object instanceof AbstractObjectInstance actor
                    && actor.getSlotIndex() >= 4 && actor.getSlotIndex() < 98) actor.setDestroyed(true);
        }
        stepObjects(fixture, 0);
        var tree = objects.createDynamicObjectAtSlot(
                () -> (AizHollowTreeObjectInstance) AizHollowTreeFactory.create(TREE, AizTreeExitRecipe.EXIT_LOOKOUT), 25);
        assertNotNull(tree);
        completeRide(tree, fixture.sprite());
        // The direct component ride creates its native reveal control at the
        // first free slot before any normal manager pass. Exclude that exact
        // fixture-owned control before admission, not an unknown later actor
        // or an already admitted lookout; reveal animation is outside this
        // local graph-retirement witness.
        var reveal = assertInstanceOf(AizHollowTreeObjectInstance.AizTreeRevealControlObjectInstance.class,
                NativeSstSlotAccess.currentOwner(objects, 4));
        assertEquals(TREE.x(), reveal.getX());
        assertEquals(TREE.y(), reveal.getY());
        objects.removeDynamicObject(reveal);
        assertNull(NativeSstSlotAccess.currentOwner(objects, 4), "admission must start with the declared free body slot");
        advanceToLookout(fixture);
        var body = nativeLookout();
        var slot4 = NativeSstSlotAccess.currentOwner(objects, 4);
        assertEquals(4, body.getSlotIndex(), () -> "the actual first-free allocation must remain below tree25; SST4="
                + (slot4 == null ? "empty" : slot4.getClass().getName() + " spawn=" + slot4.getSpawn()));
        assertEquals(25, tree.getSlotIndex());
        GameServices.camera().setX((short) 0x2D80);
        GameServices.camera().setY((short) 0x2C0);
        int tick = 500;
        for (int count = 0; count < 4 && lookoutArms(objects).size() < 5; count++) stepObjects(fixture, tick++);
        var arms = lookoutArms(objects);
        assertEquals(5, arms.size(), "native body INIT must create all five actual arm owners");
        assertTrue(arms.stream().allMatch(arm -> arm.getSlotIndex() > 4 && arm.getSlotIndex() < 25));
        S3kBadnikProjectileInstance coconut = null;
        if (waitForCoconut) {
            var root = arms.getFirst();
            assertTrue(fixture.sprite().getCentreX() <= root.getX()
                            && root.getX() - fixture.sprite().getCentreX() < 0x80,
                    "ordinary native left-facing attack prerequisite");
            for (int count = 0; count < 160 && coconut == null; count++) {
                stepObjects(fixture, tick++);
                coconut = objects.activeObjectsOfType(S3kBadnikProjectileInstance.class).stream()
                        .filter(actor -> !actor.isDestroyed()).findFirst().orElse(null);
            }
            assertNotNull(coconut, "native arm/hand routines must commit the real detached coconut");
        }
        return new RetirementScene(fixture, tree, body, arms, coconut, tick);
    }

    private static void requestFromLaterTreeSlot(RetirementScene scene) {
        // Coarse-back=$2D80: original tree $2D00 is out, but body $2DE0
        // remains in its own native window. The actual manager executes body4
        // and arms before retiring tree25 and invoking its onUnload callback.
        GameServices.camera().setX((short) 0x2E00);
        stepObjects(scene.fixture(), scene.nextFrame());
        var objects = GameServices.level().getObjectManager();
        assertTrue(objects.getActiveObjects().stream().noneMatch(actor -> actor == scene.tree()));
        assertSame(scene.body(), NativeSstSlotAccess.currentOwner(objects, 4));
        assertRequestedGraph(scene.body(), scene.arms());
    }

    private static void assertRequestedGraph(MonkeyDudeBadnikInstance body,
                                             java.util.List<MonkeyDudeArmObjectInstance> arms) {
        assertFalse(body.isDestroyed());
        assertEquals(Boolean.TRUE, readField(body, "ownedRetirementRequested"));
        assertEquals(Boolean.FALSE, readField(body, "nativeRetirementPending"));
        assertEquals(5, arms.size());
        assertTrue(arms.stream().noneMatch(AbstractObjectInstance::isDestroyed));
        assertTrue(arms.stream().allMatch(arm -> Boolean.FALSE.equals(readField(arm, "nativeRetirementPending"))));
    }

    private static void assertPublishedGraph(MonkeyDudeBadnikInstance body,
                                             java.util.List<MonkeyDudeArmObjectInstance> arms) {
        assertFalse(body.isDestroyed(), "publish status7 for a whole own-slot pass before clearing");
        assertEquals(Boolean.FALSE, readField(body, "ownedRetirementRequested"));
        assertEquals(Boolean.TRUE, readField(body, "nativeRetirementPending"));
        assertEquals(5, arms.size());
        assertTrue(arms.stream().noneMatch(AbstractObjectInstance::isDestroyed));
        assertTrue(arms.stream().allMatch(arm -> Boolean.TRUE.equals(readField(arm, "nativeRetirementPending"))));
    }

    private static void assertClosedGraph(ObjectManager objects, MonkeyDudeBadnikInstance body,
                                          java.util.List<MonkeyDudeArmObjectInstance> arms) {
        assertTrue(body.isDestroyed());
        assertTrue(arms.stream().allMatch(AbstractObjectInstance::isDestroyed));
        assertTrue(lookoutArms(objects).isEmpty());
        assertNull(NativeSstSlotAccess.currentOwner(objects, 4));
    }

    private static java.util.List<MonkeyDudeArmObjectInstance> lookoutArms(ObjectManager objects) {
        return objects.activeObjectsOfType(MonkeyDudeArmObjectInstance.class).stream()
                .filter(actor -> !actor.isDestroyed() && actor.getSpawn().x() == 0x2DE0
                        && actor.getSpawn().y() == 0x31C)
                .sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
    }

    private static void stepObjects(HeadlessTestFixture fixture, int frame) {
        var objects = GameServices.level().getObjectManager();
        var nativeP2 = objects.getObjectServices().playerQuery().nativeP2OrNull();
        objects.update(GameServices.camera().getX(), fixture.sprite(),
                nativeP2 == null ? java.util.List.of() : java.util.List.of(nativeP2), frame, false);
    }

    private static void finishRequestedRetirement(HeadlessTestFixture fixture) {
        if (lookouts() == 0) return;
        var body = nativeLookout();
        assertFalse(body.isDestroyed());
        assertEquals(Boolean.TRUE, readField(body, "ownedRetirementRequested"));
        stepObjects(fixture, 1000);
        assertFalse(body.isDestroyed());
        assertEquals(Boolean.TRUE, readField(body, "nativeRetirementPending"));
        stepObjects(fixture, 1001);
        assertTrue(body.isDestroyed());
        assertEquals(0, lookouts());
        assertTrue(lookoutArms(GameServices.level().getObjectManager()).isEmpty());
    }

    private static void sameWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> key + ": " + differences);
        }
    }

    private static final class SlotFiller extends com.openggf.level.objects.AbstractObjectInstance {
        SlotFiller() { super(new ObjectSpawn(0x2D00, 0x3CC, 0, 0, 0, false, 0), "A5 slot fixture"); }
        @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }

    private record BornFrame(java.util.List<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry> graph,
                             java.util.List<Integer> main, java.util.List<Integer> nativeP2) { }

    private static BornFrame bornFrame(HeadlessTestFixture fixture) {
        var objects = GameServices.level().getObjectManager();
        var p2 = assertInstanceOf(AbstractPlayableSprite.class, objects.getObjectServices().playerQuery().nativeP2OrNull());
        return new BornFrame(objects.rewindSnapshottable().capture().dynamicObjects(),
                participantFrame(fixture.sprite()), participantFrame(p2));
    }

    private static java.util.List<Integer> participantFrame(AbstractPlayableSprite player) {
        return java.util.List.of((int) player.getCentreX(), (int) player.getCentreY(),
                (int) player.getXSpeed(), (int) player.getYSpeed(), (int) player.getGSpeed(),
                player.getAir() ? 1 : 0, player.isHurt() ? 1 : 0, player.getDead() ? 1 : 0,
                player.isObjectControlled() ? 1 : 0, player.getAnimationId());
    }

    private static MonkeyDudeBadnikInstance nativeLookout() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(MonkeyDudeBadnikInstance.class::isInstance).map(MonkeyDudeBadnikInstance.class::cast)
                .filter(actor -> actor.getSpawn().layoutIndex() == -1 && !actor.isDestroyed()).findFirst().orElseThrow();
    }

    private static Object readField(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Test void missingNativeArtAtTheSingleAdmissionAttemptSkipsWithoutRetry() {
        var fixture = boot("sonic", ""); var player = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        var tree = create(fixture, AizTreeExitRecipe.EXIT_LOOKOUT);
        completeRide(tree, player);
        var unavailable = spy(objects.getObjectServices());
        doReturn(null).when(unavailable).renderManager();
        tree.setServices(unavailable);
        player.setCentreX((short) 0x2D80); player.setCentreY((short) 0x33A); player.setAir(false);
        tree.update(240, player); assertEquals(0, lookouts());
        tree.setServices(objects.getObjectServices());
        tree.update(241, player); assertEquals(0, lookouts(), "late art cannot create a surprise second attempt");
    }

    private static int advanceToLookout(HeadlessTestFixture fixture) {
        for (int frame = 1; frame <= 40; frame++) {
            fixture.stepFrame(false, false, false, true, false);
            if (lookouts() > 0) return frame;
        }
        var player = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        var renderer = objects.getObjectServices().renderManager().getRenderer(Sonic3kObjectArtKeys.MONKEY_DUDE);
        fail("native exit admission not reached: p=" + player.getCentreX() + "," + player.getCentreY()
                + " air=" + player.getAir() + " controlled=" + player.isObjectControlled()
                + " art=" + (renderer != null && renderer.isReady()));
        return -1;
    }

    private static int progress(AizHollowTreeObjectInstance tree) {
        try {
            var rides = tree.getClass().getDeclaredField("nativeRideStates"); rides.setAccessible(true);
            Object main = ((Object[]) rides.get(tree))[0];
            var progress = main.getClass().getDeclaredField("progress"); progress.setAccessible(true);
            return (progress.getInt(main) >>> 16) & 0xFFFF;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static HeadlessTestFixture boot(String main, String sidekick) {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekick);
        SessionManager.clear();
        return HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro()
                .startPosition((short) 0x2CF1, (short) 0x456).startPositionIsCentre().build();
    }

    private static AizHollowTreeObjectInstance create(HeadlessTestFixture fixture, AizTreeExitRecipe recipe) {
        var objects = GameServices.level().getObjectManager();
        for (var object : objects.getActiveObjects()) if (object instanceof AizHollowTreeObjectInstance original) original.setDestroyed(true);
        var tree = (AizHollowTreeObjectInstance) AizHollowTreeFactory.create(TREE, recipe);
        tree.setServices(objects.getObjectServices()); objects.addDynamicObject(tree);
        return tree;
    }

    private static void completeRide(AizHollowTreeObjectInstance tree, AbstractPlayableSprite player) {
        prepare(player); tree.update(0, player);
        assertTrue(player.isObjectControlled(), "actual native capture precondition");
        for (int tick = 1; tick < 240 && player.isObjectControlled(); tick++) tree.update(tick, player);
        assertFalse(player.isObjectControlled(), "native completed upper ride must release");
        assertTrue(player.getAir(), "release precedes the real exit landing");
    }

    private static void prepare(AbstractPlayableSprite player) {
        ObjectControlState.none().applyTo(player); player.setControlLocked(false);
        player.setObjectMappingFrameControl(false); player.setCentreX((short) 0x2CF1);
        player.setCentreY((short) 0x456); player.setXSpeed((short) 0x600);
        player.setGSpeed((short) 0x600); player.setAir(false);
    }

    private static long lookouts() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(MonkeyDudeBadnikInstance.class::isInstance)
                .filter(o -> o.getSpawn().layoutIndex() == -1 && !o.isDestroyed()).count();
    }
}

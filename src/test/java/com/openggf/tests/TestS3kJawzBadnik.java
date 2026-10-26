package com.openggf.tests;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.HczHarmfulExplosionObjectInstance;
import com.openggf.game.sonic3k.objects.badniks.JawzBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestS3kJawzBadnik {

    @AfterEach
    void clearSession() { SessionManager.clear(); }

    @Test
    void collisionAttributesBeginWhenTheRestoredEntryInitializes() {
        var fixture = touchFixture();
        var jawz = onlyJawz(fixture.manager());
        assertEquals(0, jawz.getCollisionFlags(), "Obj_WaitOffscreen has no ObjDat collision byte");
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 0, false);
        fixture.manager().refreshPostCameraRenderState();
        assertEquals(0, jawz.getCollisionFlags());

        GameServices.camera().setY((short) 400);
        fixture.manager().refreshPostCameraRenderState();
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 1, false);
        assertEquals(0, jawz.getCollisionFlags(), "restoring Obj_Jawz does not run SetUp_ObjAttributes");
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 2, false);
        assertEquals(0xD7, jawz.getCollisionFlags(), "SetUp_ObjAttributes installs the live collision byte");
    }

    @ParameterizedTest(name = "P2={0}: only Jawz_Main publishes to the next player pass")
    @ValueSource(booleans = {false, true})
    void productionPreviousListWaitsForFirstMainPublication(boolean p2) {
        var fixture = touchFixture();
        var target = p2 ? fixture.p2() : fixture.main();
        for (int frame = 0; frame < 3; frame++) {
            fixture.manager().snapshotTouchResponseState(true);
            fixture.manager().runTouchResponsesForPlayer(target, frame, true);
            assertFalse(target.isHurt(), "a waiting native SST cannot hurt a player below the camera");
            assertEquals(0, onlyJawz(fixture.manager()).getCollisionProperty());
            fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), frame, false);
            fixture.manager().refreshPostCameraRenderState();
        }
        GameServices.camera().setY((short) 400);
        fixture.manager().refreshPostCameraRenderState();
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 3, false);
        fixture.manager().snapshotTouchResponseState(true);
        fixture.manager().runTouchResponsesForPlayer(target, 4, true);
        assertFalse(target.isHurt());
        assertEquals(0, onlyJawz(fixture.manager()).getCollisionProperty());
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 4, false);
        assertEquals(0xD7, onlyJawz(fixture.manager()).getCollisionFlags());
        fixture.manager().snapshotTouchResponseState(true);
        fixture.manager().runTouchResponsesForPlayer(target, 5, true);
        assertEquals(0, onlyJawz(fixture.manager()).getCollisionProperty(),
                "Obj_Jawz returns via Set_VelocityXTrackSonic without publishing the initialized SST");
        assertFalse(target.isHurt());

        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 5, false);
        var jawz = onlyJawz(fixture.manager());
        assertEquals(158, jawz.getX(), "the first Jawz_Main moves before publishing");
        fixture.manager().snapshotTouchResponseState(true);
        fixture.manager().runTouchResponsesForPlayer(target, 6, true);
        assertEquals(p2 ? 2 : 1, onlyJawz(fixture.manager()).getCollisionProperty());
        assertFalse(target.isHurt(), "Touch_Special queues the object response for Jawz_Main");
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 6, false);
        assertTrue(jawz.isDestroyed(), "the next Main consumes the vulnerable player's property");
        assertFalse(jawz.publishesTouchResponseListEntryThisFrame(),
                "the harmful-child/delete branch does not reach Sprite_CheckDeleteTouch");
        var explosion = fixture.manager().getActiveObjects().stream()
                .filter(HczHarmfulExplosionObjectInstance.class::isInstance).findFirst().orElseThrow();
        assertEquals(156, explosion.getX(), "the contact-response Main also moves before replacing Jawz");
    }

    @Test
    void registeredRecreationPreservesUnpublishedAndFirstPublishedPhases() {
        var fixture = touchFixture();
        fixture.manager().setRewindInPlaceRestoreEnabledForTest(false);
        var registry = new RewindRegistry();
        registry.register(fixture.manager().rewindSnapshottable());
        int index = 0;
        for (var player : List.of(fixture.main(), fixture.p2())) {
            String key = "player-" + index++;
            registry.register(new RewindSnapshottable<PerObjectRewindSnapshot>() {
                public String key() { return key; }
                public PerObjectRewindSnapshot capture() { return player.captureRewindState(); }
                public void restore(PerObjectRewindSnapshot state) { player.restoreRewindState(state); }
            });
        }
        var waiting = registry.capture();
        GameServices.camera().setY((short) 400);
        fixture.manager().refreshPostCameraRenderState();
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 1, false);
        var pending = registry.capture();
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 2, false);
        var initializedUnpublished = registry.capture();
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 3, false);
        var firstPublished = registry.capture();

        for (int replay = 0; replay < 2; replay++) {
            var oldJawz = onlyJawz(fixture.manager());
            fixture.manager().snapshotTouchResponseState(true);
            fixture.manager().runTouchResponsesForPlayer(fixture.p2(), 4, true);
            assertEquals(2, oldJawz.getCollisionProperty());
            fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 4, false);
            assertTrue(oldJawz.isDestroyed(), "restore must recreate a deleted graph, not reuse live state");
            registry.restore(waiting);
            assertNotSame(oldJawz, onlyJawz(fixture.manager()));
            assertEquals(0, onlyJawz(fixture.manager()).getCollisionFlags());
            fixture.manager().refreshPostCameraRenderState();
            fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 1, false);
            assertEquals(0, onlyJawz(fixture.manager()).getCollisionFlags());
            fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 2, false);
            assertEquals(0xD7, onlyJawz(fixture.manager()).getCollisionFlags());
            registry.restore(pending);
            assertEquals(0, onlyJawz(fixture.manager()).getCollisionFlags());
            fixture.manager().snapshotTouchResponseState(true);
            fixture.manager().runTouchResponsesForPlayer(fixture.p2(), 2, true);
            assertFalse(fixture.p2().isHurt());
            fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 2, false);
            fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 3, false);
            var beforeUnpublishedRestore = onlyJawz(fixture.manager());
            fixture.manager().snapshotTouchResponseState(true);
            fixture.manager().runTouchResponsesForPlayer(fixture.p2(), 4, true);
            fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 4, false);
            assertTrue(beforeUnpublishedRestore.isDestroyed());
            registry.restore(initializedUnpublished);
            var restoredUnpublished = onlyJawz(fixture.manager());
            assertNotSame(beforeUnpublishedRestore, restoredUnpublished);
            assertEquals(0xD7, restoredUnpublished.getCollisionFlags());
            fixture.manager().snapshotTouchResponseState(true);
            fixture.manager().runTouchResponsesForPlayer(fixture.p2(), 3, true);
            assertFalse(fixture.p2().isHurt());
            assertEquals(0, restoredUnpublished.getCollisionProperty(),
                    "fresh reconstruction must not publish an initialized but unpublished SST");
            assertFalse(restoredUnpublished.publishesTouchResponseListEntryThisFrame());
            fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 3, false);
            assertEquals(158, restoredUnpublished.getX());
            fixture.manager().snapshotTouchResponseState(true);
            fixture.manager().runTouchResponsesForPlayer(fixture.p2(), 4, true);
            assertEquals(2, restoredUnpublished.getCollisionProperty());
            fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 4, false);
            assertTrue(restoredUnpublished.isDestroyed());

            registry.restore(firstPublished);
            var restoredPublished = onlyJawz(fixture.manager());
            assertNotSame(restoredUnpublished, restoredPublished);
            assertEquals(158, restoredPublished.getX());
            assertTrue(restoredPublished.publishesTouchResponseListEntryThisFrame());
            fixture.manager().snapshotTouchResponseState(true);
            fixture.manager().runTouchResponsesForPlayer(fixture.p2(), 4, true);
            assertEquals(2, restoredPublished.getCollisionProperty(),
                    "the restored previous-list pointer must resolve to the recreated first-Main SST");
            assertFalse(fixture.p2().isHurt());
            // Restore the same published boundary again before the next destructive replay.
            registry.restore(firstPublished);
        }
    }

    @Test
    void attackingPlayerDefeatDoesNotPublishJawzAgain() {
        var fixture = touchFixture();
        fixture.main().setAnimationId(2);
        GameServices.camera().setY((short) 400);
        fixture.manager().refreshPostCameraRenderState();
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 1, false);
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 2, false);
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 3, false);
        var jawz = onlyJawz(fixture.manager());
        fixture.manager().snapshotTouchResponseState(true);
        fixture.manager().runTouchResponsesForPlayer(fixture.main(), 4, true);
        assertEquals(1, jawz.getCollisionProperty());
        fixture.manager().update(0, fixture.main(), List.of(fixture.p2()), 4, false);
        assertTrue(jawz.isDestroyed());
        assertEquals(156, jawz.getX());
        assertFalse(jawz.publishesTouchResponseListEntryThisFrame(),
                "Jawz_Defeated draws the replacement without publishing the old Jawz SST");
        assertTrue(fixture.manager().getActiveObjects().stream()
                .noneMatch(HczHarmfulExplosionObjectInstance.class::isInstance));
    }

    private record TouchFixture(ObjectManager manager, TestablePlayableSprite main, TestablePlayableSprite p2) {}

    private static TouchFixture touchFixture() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var main = new TestablePlayableSprite("sonic", (short) 160, (short) 500);
        var p2 = new TestablePlayableSprite("tails", (short) 160, (short) 500);
        p2.setCpuControlled(true);
        GameServices.camera().setFocusedSprite(main);
        GameServices.camera().setX((short) 0);
        GameServices.camera().setY((short) 0);
        for (var player : List.of(main, p2)) {
            player.setRingCount(10);
            GameServices.sprites().addSprite(player, player.getCode());
        }
        ObjectManager[] holder = new ObjectManager[1];
        var services = new TestObjectServices() {
            public ObjectManager objectManager() { return holder[0]; }
            public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> main, () -> List.of(p2)); }
            public com.openggf.camera.Camera camera() { return GameServices.camera(); }
        };
        services.withDebugOverlay(mock(com.openggf.debug.DebugOverlayManager.class));
        services.withGameState(GameServices.gameState());
        var table = mock(TouchResponseTable.class);
        when(table.getWidthRadius(0x17)).thenReturn(8);
        when(table.getHeightRadius(0x17)).thenReturn(8);
        var manager = new ObjectManager(List.of(), null, -1, null, table, null, GameServices.camera(), services);
        holder[0] = manager;
        manager.reset(0);
        manager.createDynamicObject(() -> new JawzBadnikInstance(
                new ObjectSpawn(160, 500, Sonic3kObjectIds.JAWZ, 0, 0, false, 0)));
        return new TouchFixture(manager, main, p2);
    }

    private static JawzBadnikInstance onlyJawz(ObjectManager manager) {
        return manager.getActiveObjects().stream().filter(JawzBadnikInstance.class::isInstance)
                .map(JawzBadnikInstance.class::cast).findFirst().orElseThrow();
    }

    @Test
    public void jawzInitializesVelocityTowardPlayerOnFirstVisibleFrame() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);

        JawzBadnikInstance jawz = new JawzBadnikInstance(
                new ObjectSpawn(160, 100, Sonic3kObjectIds.JAWZ, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 80);

        jawz.update(0, player); // Obj_WaitOffscreen placeholder dispatch
        assertEquals(160, jawz.getX(), "Jawz should not move on the initialization frame");

        jawz.refreshPostCameraRenderState(); // Render_Sprites sets render_flags bit 7
        jawz.update(1, player); // Obj_WaitOffscreen restores the saved entry point
        assertEquals(160, jawz.getX(), "Jawz initialization should not move the object");

        jawz.update(2, player); // Obj_Jawz initializes velocity
        assertEquals(160, jawz.getX(), "Jawz velocity initialization should not move the object");

        jawz.update(3, player);
        assertEquals(158, jawz.getX(), "Jawz should move toward the player on the next frame");
        assertEquals(1, readMappingFrame(jawz), "Jawz should advance to the second animation frame after moving");
    }

    @Test
    public void jawzTracksRightWhenPlayerIsToTheRight() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);

        JawzBadnikInstance jawz = new JawzBadnikInstance(
                new ObjectSpawn(160, 100, Sonic3kObjectIds.JAWZ, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 240);

        jawz.update(0, player);
        jawz.refreshPostCameraRenderState();
        jawz.update(1, player);
        jawz.update(2, player);
        jawz.update(3, player);

        assertEquals(162, jawz.getX(), "Jawz should move right when the player is on the right");
    }

    @Test
    public void jawzResumesSetupWhenAnOffscreenPlaceholderBecomesVisible() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);

        JawzBadnikInstance jawz = new JawzBadnikInstance(
                new ObjectSpawn(0x400, 100, Sonic3kObjectIds.JAWZ, 0, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x500);

        jawz.update(0, player);
        assertEquals(0x400, jawz.getX(), "The offscreen placeholder must remain stationary");

        AbstractObjectInstance.updateCameraBounds(0x380, 0, 0x4BF, 223, 0);
        jawz.refreshPostCameraRenderState();
        jawz.update(1, player);
        assertEquals(0x400, jawz.getX(),
                "Restoring the saved Obj_Jawz entry consumes its own dispatch");

        jawz.update(2, player);
        assertEquals(0x400, jawz.getX(),
                "The restored Obj_Jawz entry initializes velocity without moving");

        jawz.update(3, player);
        assertEquals(0x402, jawz.getX(),
                "The resumed Jawz routine must execute MoveSprite2 on the following dispatch");
    }

    @Test
    public void jawzUsesNativeSpecialPropertyTouchAccumulation() {
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 160, (short) 100);
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 160, (short) 100);
        JawzBadnikInstance jawz = new JawzBadnikInstance(
                new ObjectSpawn(160, 100, Sonic3kObjectIds.JAWZ, 0, 0, false, 0));
        jawz.setServices(new TestObjectServices() {
            @Override
            public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> sonic, () -> java.util.List.of(tails));
            }
        });
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);
        jawz.update(0, sonic);
        jawz.refreshPostCameraRenderState();
        jawz.update(1, sonic);
        jawz.update(2, sonic); // SetUp_ObjAttributes owns the collision byte.
        TouchResponseResult result = new TouchResponseResult(
                0x17, 8, 8, TouchCategory.SPECIAL, 0);

        jawz.onTouchResponse(sonic, result, 1);
        assertEquals(1, jawz.getCollisionProperty());
        jawz.onTouchResponse(tails, result, 1);

        assertEquals(3, jawz.getCollisionProperty(),
                "Touch_Special adds one for P1 and two for P2, selecting P2 when both overlap");
        assertEquals(0xD7, jawz.getCollisionFlags());
        assertEquals(TouchCategoryDecodeMode.S3K_SPECIAL_PROPERTY,
                jawz.getTouchResponseProfile().categoryDecodeMode());
        assertTrue(jawz.requiresContinuousTouchCallbacks());
    }

    @Test
    public void vulnerablePlayerContactReplacesJawzWithHarmfulHczExplosion() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 319, 223, 0);
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 160, (short) 100);
        ObjectManager[] managerRef = new ObjectManager[1];
        TestObjectServices services = new TestObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return managerRef[0];
            }

            @Override
            public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> sonic, List::of);
            }
        };
        ObjectManager manager = new ObjectManager(
                List.of(), null, -1, null, null, null, null, services);
        managerRef[0] = manager;
        JawzBadnikInstance jawz = manager.createDynamicObject(() -> new JawzBadnikInstance(
                new ObjectSpawn(160, 100, Sonic3kObjectIds.JAWZ, 0, 0, false, 0)));

        jawz.update(0, sonic);
        jawz.refreshPostCameraRenderState();
        jawz.update(1, sonic);
        jawz.update(2, sonic);
        jawz.onTouchResponse(sonic,
                new TouchResponseResult(0x17, 8, 8, TouchCategory.SPECIAL, 0), 3);
        jawz.update(3, sonic);

        assertTrue(jawz.isDestroyed());
        List<HczHarmfulExplosionObjectInstance> explosions = manager.getActiveObjects().stream()
                .filter(HczHarmfulExplosionObjectInstance.class::isInstance)
                .map(HczHarmfulExplosionObjectInstance.class::cast)
                .toList();
        assertEquals(1, explosions.size(),
                "ROM creates one HCZEndBoss_ExplosionChild before deleting Jawz");
        assertEquals(jawz.getX(), explosions.getFirst().getX(),
                "Jawz moves before checking collision_property and creating the child");
        assertEquals(jawz.getY(), explosions.getFirst().getY());
        assertEquals(jawz.getSlotIndex() + 1, explosions.getFirst().getSlotIndex(),
                "CreateChild1_Normal allocates the next free SST slot after Jawz");
    }

    private static int readMappingFrame(JawzBadnikInstance jawz) {
        try {
            Field field = jawz.getClass().getSuperclass().getDeclaredField("mappingFrame");
            field.setAccessible(true);
            return field.getInt(jawz);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read Jawz mapping frame", e);
        }
    }
}

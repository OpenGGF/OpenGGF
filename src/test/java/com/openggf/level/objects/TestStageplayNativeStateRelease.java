package com.openggf.level.objects;

import com.openggf.game.sonic1.objects.Sonic1JunctionObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1TeleporterObjectInstance;
import com.openggf.game.sonic3k.objects.Aiz2EndEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.CnzCannonInstance;
import com.openggf.game.sonic3k.objects.HPZSSEntryControlObjectInstance;
import com.openggf.game.sonic3k.objects.IczTeleporterInstance;
import com.openggf.game.sonic3k.objects.badniks.MegaChopperBadnikInstance;
import com.openggf.level.objects.ObjectVisualFacing.Basis;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayNativeStateRelease {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(100, 100, 1, 0, 0, false, 100);
    private static Field field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }

    @Test void playerReleaseDoesNotCancelS1JunctionOrTransportProgress() throws Exception {
        for (var object : List.of(new Sonic1JunctionObjectInstance(SPAWN), new Sonic1TeleporterObjectInstance(SPAWN))) {
            var owner = mock(AbstractPlayableSprite.class);
            field(object, "controlledPlayer").set(object, owner);
            var routine = field(object, "routine");
            Object running = routine.getType().getEnumConstants()[1]; routine.set(object, running);
            object.releasePlayersForVisualDirection();
            assertSame(running, routine.get(object));
            assertNull(field(object, "controlledPlayer").get(object));
            verify(owner).setControlLocked(false);
        }
    }

    @Test void cannonAndHpzControllerReleaseOwnershipWithoutRewindingTheirNativePhase() throws Exception {
        var owner = mock(AbstractPlayableSprite.class);
        var services = mock(ObjectServices.class, RETURNS_DEEP_STUBS);
        var cannon = new CnzCannonInstance(SPAWN); cannon.setServices(services);
        field(cannon, "capturedPlayer").set(cannon, owner);
        field(cannon, "state").set(cannon, 2);
        cannon.releasePlayersForVisualDirection();
        assertEquals(2, field(cannon, "state").get(cannon));
        var hpz = new HPZSSEntryControlObjectInstance(SPAWN); hpz.setServices(mock(ObjectServices.class));
        field(hpz, "playersLocked").set(hpz, true);
        ((PlayerControlOwners) field(hpz, "directedControlPlayers").get(hpz)).add(owner);
        hpz.releasePlayersForVisualDirection();
        assertEquals(true, field(hpz, "playersLocked").get(hpz));
    }

    @Test void capsulePreservesEndingProgressWhileDroppingOnlyItsOwnership() throws Exception {
        var owner = mock(AbstractPlayableSprite.class);
        var capsule = new Aiz2EndEggCapsuleInstance(100, 100);
        capsule.setServices(mock(ObjectServices.class));
        field(capsule, "nativeP2EndingPoseOwner").set(capsule, owner);
        field(capsule, "tailsEndingPoseApplied").set(capsule, true);
        field(capsule, "tailsEndingPoseObjectControlLocked").set(capsule, true);
        field(capsule, "endingPoseControlsReleased").set(capsule, false);
        capsule.releasePlayersForVisualDirection();
        assertEquals(true, field(capsule, "tailsEndingPoseApplied").get(capsule));
        assertEquals(false, field(capsule, "endingPoseControlsReleased").get(capsule));
        assertNull(field(capsule, "nativeP2EndingPoseOwner").get(capsule));
    }

    @Test void realIczAcquisitionReleasesThroughManagerDirectionRemovalAndClear() throws Exception {
        for (String action : List.of("direct", "remove", "clear")) {
            var fixture = new NativeFixture();
            var direction = ObjectDirectionAccess.forManager(fixture.objects);
            var pad = fixture.objects.createDynamicObject(() -> new IczTeleporterInstance(SPAWN));
            assertNotNull(pad);
            pad.update(0, fixture.player);
            assertTrue(pad.initialized());
            assertNotNull(pad.beamForTest());
            verify(fixture.player).applyObjectControlState(com.openggf.sprites.playable.ObjectControlState
                    .NATIVE_BITS_0_TO_6_CPU_ALLOWED_MOVEMENT_SUPPRESSED);
            var beam = pad.beamForTest();
            var nativeBefore = nativeProgress(pad);
            var beamBefore = nativeProgress(beam);
            clearInvocations(fixture.player, fixture.unrelated);

            // The pad and expanding beam have fixed geometry, without a body-facing mirror.
            act(direction, pad, action, Basis.NON_DIRECTIONAL, Basis.RIGHT);

            verify(fixture.player).applyObjectControlState(
                    com.openggf.sprites.playable.ObjectControlState.NONE);
            verifyNoInteractions(fixture.unrelated);
            assertEquals(nativeBefore, nativeProgress(pad));
            assertSame(beam, pad.beamForTest(), "visual retirement does not finish the native beam");
            assertEquals(beamBefore, nativeProgress(beam));
            if (action.equals("direct")) direction.releaseToFree(pad);
            clearInvocations(fixture.player);
            pad.releasePlayersForVisualDirection();
            verifyNoInteractions(fixture.player);
        }
    }

    @Test void realMegaAcquisitionReleasesThroughManagerWithoutInvokingNativeUnload() throws Exception {
        for (String action : List.of("direct", "remove", "clear")) {
            var fixture = new NativeFixture();
            var direction = ObjectDirectionAccess.forManager(fixture.objects);
            var chopper = fixture.objects.createDynamicObject(() -> new MegaChopperBadnikInstance(SPAWN));
            assertNotNull(chopper);
            // Reuse the owning fixture's existing native offscreen-gate inlet.
            var gate = MegaChopperBadnikInstance.class.getDeclaredMethod("testReleaseOffscreenWait");
            gate.setAccessible(true);
            gate.invoke(chopper);
            chopper.onTouchResponse(fixture.player,
                    new TouchResponseResult(0x17, 0x20, 0x20, TouchCategory.SPECIAL), 0);
            chopper.update(0, fixture.player);
            when(fixture.player.isLeftPressed()).thenReturn(true);
            chopper.update(1, fixture.player);
            verify(fixture.player).setControlLocked(true);
            verify(fixture.player).setForcedInputMask(AbstractPlayableSprite.INPUT_LEFT);
            var nativeBefore = nativeProgress(chopper);
            clearInvocations(fixture.player, fixture.unrelated);

            act(direction, chopper, action, chopper.nativeFacing(), chopper.nativeFacing());

            verify(fixture.player).setForcedInputMask(0);
            verify(fixture.player).setControlLocked(false);
            verify(fixture.player, never()).clearForcedInputMask();
            verifyNoInteractions(fixture.unrelated);
            assertEquals(nativeBefore, nativeProgress(chopper));
            assertSame(fixture.player, field(chopper, "capturedPlayer").get(chopper));
            assertEquals(true, field(chopper, "mainControlsLocked").get(chopper),
                    "onUnload would clear the native latch and captured player");
            if (action.equals("direct")) {
                direction.releaseToFree(chopper);
                fixture.player.setControlLocked(true);
                when(fixture.player.isLeftPressed()).thenReturn(false);
                when(fixture.player.isRightPressed()).thenReturn(true);
                chopper.update(2, fixture.player);
                verify(fixture.player).setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
                clearInvocations(fixture.player);
                var facing = chopper.nativeFacing();
                direction.direct(chopper, heldTemplate(chopper, facing),
                        new StageplayObjectDirection.Direction(100, 100, facing, true, 0, -1));
                verify(fixture.player).setForcedInputMask(0);
                verify(fixture.player, never()).setControlLocked(anyBoolean());
            }
        }
    }

    private static void act(StageplayObjectDirection direction, ObjectInstance object, String action,
                            Basis nativeFacing, Basis requestedFacing) {
        switch (action) {
            case "remove" -> direction.removeDynamic(object);
            case "clear" -> direction.clearObjects();
            default -> direction.direct(object, heldTemplate(object, nativeFacing),
                    new StageplayObjectDirection.Direction(100, 100, requestedFacing, true, 0, -1));
        }
    }

    private static StageplayObjectDirection.VisualTemplate heldTemplate(ObjectInstance object, Basis facing) {
        return new StageplayObjectDirection.VisualTemplate(object.getX(), object.getY(), facing,
                List.of(new StageplayObjectDirection.VisualPart("root/0", 4, false, 0, 0, facing, 0,
                        List.of(new StageplayObjectDirection.VisualPose(0, List.of(), List.of())))),
                List.of(), List.of(), List.of());
    }

    private static java.util.Map<String, Object> nativeProgress(Object target) throws Exception {
        var values = new java.util.LinkedHashMap<String, Object>();
        for (Class<?> type = target.getClass(); type != AbstractObjectInstance.class; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (field.getType().isPrimitive() || field.getType().isEnum()) {
                    values.put(type.getSimpleName() + "." + field.getName(), field.get(target));
                } else if (field.get(target) instanceof com.openggf.game.rewind.RewindStateful<?> holder) {
                    values.put(type.getSimpleName() + "." + field.getName(), holder.captureRewindStateValue());
                }
            }
        }
        return values;
    }

    private static final class NativeFixture {
        final com.openggf.graphics.GraphicsManager graphics = new com.openggf.graphics.GraphicsManager();
        final ObjectServices services = mock(ObjectServices.class);
        final AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        final AbstractPlayableSprite unrelated = mock(AbstractPlayableSprite.class);
        final ObjectManager objects;

        NativeFixture() {
            graphics.setHeadlessMode(true);
            var module = mock(com.openggf.game.GameModule.class);
            when(module.getGameId()).thenReturn(com.openggf.game.GameId.S3K);
            var worldSession = new com.openggf.game.session.WorldSession(module);
            when(services.worldSession()).thenReturn(worldSession);
            when(services.graphicsManager()).thenReturn(graphics);
            when(services.solidExecutionRegistry()).thenReturn(com.openggf.game.solid.SolidExecutionRegistry.inert());
            var camera = mock(com.openggf.camera.Camera.class);
            when(camera.getFocusedSprite()).thenReturn(player);
            when(services.camera()).thenReturn(camera);
            var sprites = mock(com.openggf.sprites.managers.SpriteManager.class);
            when(sprites.getMainPlayable()).thenReturn(player);
            when(services.spriteManager()).thenReturn(sprites);
            when(services.playerQuery()).thenReturn(new ObjectPlayerQuery(() -> player, List::of));
            when(player.getCode()).thenReturn("sonic");
            when(player.getCentreX()).thenReturn((short) 100);
            when(player.getCentreY()).thenReturn((short) 100);
            when(player.getRingCount()).thenReturn(10);
            objects = new ObjectManager(List.of(), null, 0, null, null, graphics, camera, services);
            when(services.objectManager()).thenReturn(objects);
        }
    }
}

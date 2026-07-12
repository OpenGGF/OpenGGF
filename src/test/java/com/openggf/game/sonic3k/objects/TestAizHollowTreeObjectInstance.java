package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.schema.RewindObjectStateBlob;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestAizHollowTreeObjectInstance {

    @Test
    void captureSetsObjectControlBitsSixAndOneWithoutSuppressingMovement() {
        AizHollowTreeObjectInstance tree = new AizHollowTreeObjectInstance(new ObjectSpawn(
                0x2D00, 0x03CC, Sonic3kObjectIds.AIZ_HOLLOW_TREE, 0, 0, false, 0));
        tree.setServices(new TestObjectServices().withCamera(new Camera()));
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .build()
                .sprite();

        player.setCentreX((short) 0x2CF1);
        player.setCentreY((short) 0x0456);
        player.setXSpeed((short) 0x0AAC);
        player.setGSpeed((short) 0x0AE3);
        player.setAir(false);
        clearFixtureIntroControl(player);

        tree.update(0, player);

        assertTrue(player.isObjectControlled(),
                "Obj_AIZHollowTree sets object_control bits 6+1 while riding: " + tree.traceDebugDetails());
        assertTrue(player.isObjectControlAllowsCpu(),
                "Bits 6+1 are not ROM bit 7, so CPU/touch dispatch must not be suppressed: "
                        + tree.traceDebugDetails());
        assertFalse(player.isObjectControlSuppressesMovement(),
                "Obj_AIZHollowTree does not set object_control bit 0");
        assertTrue(player.isSuppressGroundWallCollision(),
                "object_control bit 6 makes Sonic_WalkSpeed skip CalcRoomInFront");
    }

    @Test
    void fallOffTreeClearsAllObjectControlState() {
        AizHollowTreeObjectInstance tree = new AizHollowTreeObjectInstance(new ObjectSpawn(
                0x2D00, 0x03CC, Sonic3kObjectIds.AIZ_HOLLOW_TREE, 0, 0, false, 0));
        tree.setServices(new TestObjectServices().withCamera(new Camera()));
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .build()
                .sprite();

        player.setCentreX((short) 0x2CF1);
        player.setCentreY((short) 0x0456);
        player.setXSpeed((short) 0x0AAC);
        player.setGSpeed((short) 0x0AE3);
        player.setAir(false);
        clearFixtureIntroControl(player);

        tree.update(0, player);
        assertTrue(player.isObjectControlled(), "Expected hollow tree capture precondition");

        player.setGSpeed((short) 0);
        tree.update(1, player);

        assertFalse(player.isObjectControlled(),
                "Hollow-tree fall-off should clear object-control ownership");
        assertFalse(player.isObjectControlAllowsCpu(),
                "Hollow-tree fall-off should clear CPU allowance");
        assertFalse(player.isObjectControlSuppressesMovement(),
                "Hollow-tree fall-off should clear movement suppression");
        assertFalse(player.isTouchResponseSuppressedByObjectControl(),
                "Hollow-tree fall-off should clear touch suppression");
        assertFalse(player.isSuppressGroundWallCollision(),
                "Hollow-tree fall-off should clear bit-6 wall-probe suppression");
    }

    @Test
    void extensionSidekicksCaptureAfterNativeSlotsWithIndependentRideState() {
        AizHollowTreeObjectInstance tree = new AizHollowTreeObjectInstance(new ObjectSpawn(
                0x2D00, 0x03CC, Sonic3kObjectIds.AIZ_HOLLOW_TREE, 0, 0, false, 0));
        Camera camera = new Camera();
        AbstractPlayableSprite main = newPlayer();
        AbstractPlayableSprite nativeP2 = newPlayer();
        AbstractPlayableSprite extension1 = newPlayer();
        AbstractPlayableSprite extension2 = newPlayer();
        camera.setFocusedSprite(main);
        tree.setServices(new TestObjectServices()
                .withCamera(camera)
                .withSidekicks(List.of(nativeP2, extension1, extension2)));
        for (AbstractPlayableSprite player : List.of(main, nativeP2, extension1, extension2)) {
            prepareForCapture(player);
        }

        tree.update(0, main);

        for (AbstractPlayableSprite player : List.of(main, nativeP2, extension1, extension2)) {
            assertTrue(player.isObjectControlled(),
                    "main, native P2, and every extension must own independent tree ride state");
            assertTrue(player.isOnObject());
            assertEquals(Sonic3kObjectIds.AIZ_HOLLOW_TREE, player.getLatchedSolidObjectId());
        }
    }

    @Test
    void unloadReleasesEveryCapturedExtensionWithoutTouchingUnrelatedControl() {
        AizHollowTreeObjectInstance tree = new AizHollowTreeObjectInstance(new ObjectSpawn(
                0x2D00, 0x03CC, Sonic3kObjectIds.AIZ_HOLLOW_TREE, 0, 0, false, 0));
        Camera camera = new Camera();
        AbstractPlayableSprite main = newPlayer();
        AbstractPlayableSprite nativeP2 = newPlayer();
        AbstractPlayableSprite extension = newPlayer();
        AbstractPlayableSprite unrelated = newPlayer();
        camera.setFocusedSprite(main);
        tree.setServices(new TestObjectServices().withCamera(camera)
                .withSidekicks(List.of(nativeP2, extension)));
        prepareForCapture(main);
        prepareForCapture(nativeP2);
        prepareForCapture(extension);
        ObjectControlState.nativeBit7FullControl().applyTo(unrelated);
        tree.update(0, main);

        tree.onUnload();

        for (AbstractPlayableSprite player : List.of(main, nativeP2, extension)) {
            assertFalse(player.isObjectControlled());
            assertFalse(player.isOnObject());
        }
        assertTrue(unrelated.isObjectControlled(),
                "unload must clear only identities actually owned by this tree");
    }

    @Test
    void reorderOmissionDeathAndReplacementKeepRideOwnershipByIdentity() {
        AizHollowTreeObjectInstance tree = new AizHollowTreeObjectInstance(new ObjectSpawn(
                0x2D00, 0x03CC, Sonic3kObjectIds.AIZ_HOLLOW_TREE, 0, 0, false, 0));
        AbstractPlayableSprite main = newPlayer();
        AbstractPlayableSprite first = newPlayer();
        AbstractPlayableSprite second = newPlayer();
        AbstractPlayableSprite omitted = newPlayer();
        AbstractPlayableSprite replacement = newPlayer();
        AbstractPlayableSprite unrelated = newPlayer();
        MutablePlayerServices services = new MutablePlayerServices(main, List.of(first, second, omitted));
        tree.setServices(services);
        for (AbstractPlayableSprite player : List.of(main, first, second, omitted)) {
            prepareForCapture(player);
        }
        prepareOutsideTree(replacement);
        ObjectControlState.nativeBit7FullControl().applyTo(unrelated);
        tree.update(0, main);

        services.setSidekicks(List.of(second, first, replacement));
        second.setDead(true);
        tree.update(1, main);

        assertTrue(first.isObjectControlled(),
                "a rider moved from native P2 to an extension slot must keep its identity-owned state");
        assertFalse(second.isObjectControlled(), "a dead rider must be released even after reorder");
        assertFalse(omitted.isObjectControlled(), "an omitted identity must be released");
        assertFalse(replacement.isObjectControlled(), "replacement identity must not inherit omitted state");
        assertTrue(unrelated.isObjectControlled(), "cleanup must not clear unrelated object control");
    }

    @Test
    void nonEmptyExtensionRideMapRewindsThroughPlayerRefs() {
        AbstractPlayableSprite oldMain = newPlayer();
        AbstractPlayableSprite oldP2 = newPlayer();
        AbstractPlayableSprite oldExtension = newPlayer();
        AizHollowTreeObjectInstance tree = new AizHollowTreeObjectInstance(new ObjectSpawn(
                0x2D00, 0x03CC, Sonic3kObjectIds.AIZ_HOLLOW_TREE, 0, 0, false, 0));
        tree.setServices(new MutablePlayerServices(oldMain, List.of(oldP2, oldExtension)));
        for (AbstractPlayableSprite player : List.of(oldMain, oldP2, oldExtension)) {
            prepareForCapture(player);
        }
        tree.update(0, oldMain);
        RewindObjectStateBlob snapshot = CompactFieldCapturer.capture(
                tree, rewindContext(oldMain, oldP2, oldExtension));

        AbstractPlayableSprite newMain = newPlayer();
        AbstractPlayableSprite newP2 = newPlayer();
        AbstractPlayableSprite newExtension = newPlayer();
        tree.setServices(new MutablePlayerServices(newMain, List.of(newP2, newExtension)));
        CompactFieldCapturer.restore(tree, snapshot, rewindContext(newMain, newP2, newExtension));
        ObjectControlState.nativeBits0To6CpuAllowedMovementActive().applyTo(newExtension);
        newExtension.setOnObject(true);
        newExtension.setLatchedSolidObject(Sonic3kObjectIds.AIZ_HOLLOW_TREE, tree);
        newExtension.setDead(true);

        tree.update(1, newMain);

        assertFalse(newExtension.isObjectControlled(),
                "restored extension map key must resolve to the new PlayerRef identity");
        assertFalse(newExtension.isOnObject());
    }

    @Test
    void treeRevealControlUsesPlayerCentreYForRomYPos() throws Exception {
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .build()
                .sprite();
        player.setCentreY((short) 0x0456);

        Object revealControl = newTreeRevealControl();
        setTimer2EWord(revealControl, 1);
        AizHollowTreeObjectInstance.setTreeRevealCounter(9);

        ((com.openggf.level.objects.AbstractObjectInstance) revealControl).update(0, player);

        assertEquals(9, AizHollowTreeObjectInstance.getTreeRevealCounter(),
                "Obj_AIZ1TreeRevealControl compares against ROM Player_1+y_pos, "
                        + "which maps to player centre Y, not top-left sprite bounds");
    }

    private static void clearFixtureIntroControl(AbstractPlayableSprite player) {
        ObjectControlState.none().applyTo(player);
        player.setControlLocked(false);
        player.setObjectMappingFrameControl(false);
    }

    private static AbstractPlayableSprite newPlayer() {
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .build()
                .sprite();
    }

    private static void prepareForCapture(AbstractPlayableSprite player) {
        player.setCentreX((short) 0x2CF1);
        player.setCentreY((short) 0x0456);
        player.setXSpeed((short) 0x0AAC);
        player.setGSpeed((short) 0x0AE3);
        player.setAir(false);
        clearFixtureIntroControl(player);
    }

    private static void prepareOutsideTree(AbstractPlayableSprite player) {
        prepareForCapture(player);
        player.setCentreX((short) 0x2C00);
    }

    private static RewindCaptureContext rewindContext(
            AbstractPlayableSprite main,
            AbstractPlayableSprite nativeP2,
            AbstractPlayableSprite extension) {
        RewindIdentityTable identities = new RewindIdentityTable();
        identities.registerPlayer(main, PlayerRefId.mainPlayer());
        identities.registerPlayer(nativeP2, PlayerRefId.sidekick(0));
        identities.registerPlayer(extension, PlayerRefId.sidekick(1));
        return RewindCaptureContext.withIdentityTable(identities);
    }

    private static final class MutablePlayerServices extends TestObjectServices {
        private final AbstractPlayableSprite main;
        private List<? extends PlayableEntity> sidekicks;
        private final Camera camera = new Camera();

        private MutablePlayerServices(
                AbstractPlayableSprite main, List<? extends PlayableEntity> sidekicks) {
            this.main = main;
            this.sidekicks = sidekicks;
            camera.setFocusedSprite(main);
        }

        private void setSidekicks(List<? extends PlayableEntity> sidekicks) {
            this.sidekicks = sidekicks;
        }

        @Override
        public Camera camera() {
            return camera;
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }
    }

    private static Object newTreeRevealControl() throws Exception {
        Class<?> controlClass = Class.forName(AizHollowTreeObjectInstance.class.getName()
                + "$AizTreeRevealControlObjectInstance");
        Constructor<?> constructor = controlClass.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(0x2D00, 0x03CC);
    }

    private static void setTimer2EWord(Object revealControl, int value) throws Exception {
        Field field = revealControl.getClass().getDeclaredField("timer2EWord");
        field.setAccessible(true);
        field.setInt(revealControl, value);
    }
}

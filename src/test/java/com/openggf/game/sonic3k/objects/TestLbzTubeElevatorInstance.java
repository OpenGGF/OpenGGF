package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.schema.RewindObjectStateBlob;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.managers.PlayableSpriteAnimation;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLbzTubeElevatorInstance {

    @Test
    void registryRoutesS3klSlot10ToLbzTubeElevator() {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_LBZ);

        ObjectInstance elevator = registry.create(new ObjectSpawn(0x1200, 0x0600, 0x10, 0, 0, false, 0));

        assertFalse(elevator instanceof PlaceholderObjectInstance,
                "S3KL slot $10 is Obj_LBZTubeElevator and must not remain a placeholder");
        assertEquals("LBZTubeElevator", elevator.getName());
    }

    @Test
    void groundedPlayerInsideTubeIsCapturedAndSnappedToRomOffset() {
        ObjectInstance elevator = elevator(0x1200, 0x0600, 0);
        TestablePlayableSprite player = playerAt(0x1200, 0x0600);
        int expectedY = 0x0600 + 0x18 - player.getYRadius();

        elevator.update(0, player);

        assertTrue(player.isObjectControlled(),
                "LBZTubeElevator_CheckPlayer writes object_control=$83 on capture");
        assertFalse(player.isObjectControlAllowsCpu(),
                "object_control=$83 is ROM bit-7 full control, not CPU-assisted object control");
        assertTrue(player.isObjectControlSuppressesMovement());
        assertTrue(player.isTouchResponseSuppressedByObjectControl());
        assertFalse(player.isControlLocked(),
                "object_control=$83 does not write the separate Ctrl_1_locked byte");
        assertEquals(0x1200, player.getCentreX() & 0xFFFF);
        assertEquals(expectedY, player.getCentreY() & 0xFFFF,
                "capture snaps y_pos to elevator y_pos+$18-y_radius");
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertFalse(player.getAir(), "capture clears Status_InAir");
        assertFalse(player.isJumping(), "capture clears jumping");
    }

    @Test
    void freshSolidLandingIsCapturedInTheSameObjectSlot() {
        LbzTubeElevatorInstance elevator = (LbzTubeElevatorInstance) elevator(0x1200, 0x0600, 0);
        TestablePlayableSprite player = playerAt(0x1205, 0x0601);
        player.setAir(false);
        player.setOnObject(true);

        elevator.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);

        assertTrue(player.isObjectControlled());
        assertEquals(0x1200, player.getCentreX() & 0xFFFF,
                "Action's SolidObjectFull_Offset landing is consumed by CheckPlayer in the same ROM slot");
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getGSpeed());
    }

    @Test
    void capturePreservesNativePreviousAnimationAndTimer() {
        ObjectInstance elevator = elevator(0x1200, 0x0600, 0);
        TestablePlayableSprite player = playerAt(0x1200, 0x0600);
        player.setAnimationId(0);
        player.setAnimationFrameIndex(3);
        player.setAnimationTick(5);
        player.getAnimationManager().restoreRewindState(new PlayableSpriteAnimation.RewindState(0, 0));

        elevator.update(0, player);

        assertEquals(0, player.getAnimationManager().captureRewindState().lastAnimationId(),
                "move.b #0,anim does not alter prev_anim when it was already Walk");
        assertTrue(player.isObjectMappingFrameControl(),
                "object_control bit 1 suppresses the next Animate dispatch immediately after capture");
        assertEquals(3, player.getAnimationFrameIndex());
        assertEquals(5, player.getAnimationTick());
    }

    @Test
    void firstPlayerInsideDoesNotCaptureAirborneSecondPlayerWithoutStandingBit() {
        LbzTubeElevatorInstance elevator = (LbzTubeElevatorInstance) elevator(0x1200, 0x0600, 0);
        TestablePlayableSprite tails = playerAt(0x1200, 0x0600);
        tails.setAir(true);
        elevator.setServices(new TestObjectServices().withSidekicks(List.of(tails)));

        elevator.update(0, playerAt(0x1200, 0x0600));

        assertFalse(tails.isObjectControlled(),
                "P2 fast capture requires both P1 phase and this object's p2_standing_bit");
    }

    @Test
    void openWaitingElevatorRetainsSolidObjectFullOffsetRoutineFamily() {
        ObjectInstance elevator = elevator(0x1200, 0x0600, 0);
        SolidObjectProvider solid = (SolidObjectProvider) elevator;

        assertFalse(solid.isTopSolidOnly(),
                "SolidObjectFull_Offset changes the vertical extents but retains full-solid classification");
    }

    @Test
    void parentLayerUsesRomPriority80SoCapturedPlayerCanBeLayeredBetweenTubeParts() {
        ObjectInstance elevator = elevator(0x1200, 0x0600, 0);

        assertEquals(1, elevator.getPriorityBucket(),
                "Obj_LBZTubeElevator parent writes priority=$80; the overlay child owns the $280 layer");
    }

    @Test
    void bobPhaseStoresTheRomAdjustedAngleAcrossSkippedBand() throws Exception {
        ObjectInstance elevator = elevator(0x1200, 0x0600, 0);
        setField(elevator, "bobAngle", 0xAE);

        elevator.update(0, playerAt(0x1300, 0x0600));

        assertEquals(0xD0, field(elevator, "bobAngle").getInt(elevator),
                "LBZTubeElevator writes the $B0+$20 adjustment back to 1(a4)");
    }

    @Test
    void startSpinSamplesBobAngleBeforeIncrement() throws Exception {
        ObjectInstance elevator = elevator(0x1200, 0x0600, 0);
        setField(elevator, "state", 2);
        setField(elevator, "bobAngle", 0x14);

        elevator.update(0, playerAt(0x1300, 0x0600));

        assertEquals(0x16, field(elevator, "bobAngle").getInt(elevator));
        assertEquals(0x05FF, elevator.getY() & 0xFFFF,
                "loc_29EE2 uses sine($14) before storing the next $16 phase");
    }

    @Test
    void pathSetupRetainsOriginalBobAnchorForTransitionDispatch() throws Exception {
        ObjectInstance elevator = elevator(0x0F60, 0x0578, 0x10);
        setField(elevator, "state", 2);
        setField(elevator, "spinSpeed", 0x180);

        elevator.update(0, playerAt(0x1000, 0x0578));

        assertEquals(0x0578, elevator.getY() & 0xFFFF,
                "AutoTunnel_GetPath's temporary first waypoint is overwritten by loc_29EE2 using saved $46");
    }

    @Test
    void bobWordWritePreservesObjectSubpixel() throws Exception {
        ObjectInstance elevator = elevator(0x0F60, 0x0578, 0);
        setField(elevator, "fixedY", ((long) 0x0578 << 16) | 0xABCDL);

        elevator.update(0, playerAt(0x1000, 0x0578));

        assertEquals(0xABCDL, field(elevator, "fixedY").getLong(elevator) & 0xFFFFL,
                "move.w y_pos updates from the bob and path routines must retain the low subpixel word");
    }

    @Test
    void overlayChildUsesRomPriority280Layer() throws Exception {
        ObjectInstance elevator = elevator(0x1200, 0x0600, 0);

        elevator.update(0, playerAt(0x1300, 0x0600));

        Object overlayChild = field(elevator, "overlayChild").get(elevator);
        assertTrue(overlayChild instanceof ObjectInstance);
        assertEquals(5, ((ObjectInstance) overlayChild).getPriorityBucket(),
                "loc_29DEC child writes priority=$280 and must render as a separate object layer");
    }

    @Test
    void waitExitStaysOpenWhileReleasedPlayerIsStillStandingOnElevator() throws Exception {
        ObjectInstance elevator = elevator(0x1200, 0x0600, 0);
        TestablePlayableSprite player = playerAt(0x1200, 0x0600);

        setField(elevator, "state", 8);
        Object p1State = field(elevator, "p1").get(elevator);
        setField(p1State, "phase", 2);

        player.setOnObject(true);
        elevator.update(0, player);
        elevator.update(0, player);

        SolidObjectParams params = ((SolidObjectProvider) elevator).getSolidParams();
        assertEquals(8, params.airHalfHeight(),
                "LBZTubeElevator_WaitExit retains SolidObjectFull_Offset's d2=$08 extent");
        assertEquals(8, params.groundHalfHeight(),
                "continued riding uses the same d2=$08 surface distance");
        assertEquals(0x20, params.offsetY(),
                "SolidObjectFull_Offset adds d3=$20 to the elevator anchor Y");
    }

    @Test
    void releaseClearsObjectMappingFrameControl() throws Exception {
        ObjectInstance elevator = elevator(0x1200, 0x0600, 0);
        TestablePlayableSprite player = playerAt(0x1200, 0x0600);

        setField(elevator, "state", 8);
        setField(elevator, "angle", 8);
        Object p1State = field(elevator, "p1").get(elevator);
        setField(p1State, "phase", 2);
        player.setObjectMappingFrameControl(true);

        elevator.update(0, player);

        assertFalse(player.isObjectMappingFrameControl(),
                "LBZTubeElevator_CheckPlayer clears object_control at WaitExit; "
                        + "the engine must also release object-owned player mapping frames");
        assertEquals(0x57, player.getMappingFrame(),
                "loc_2A1EC still publishes the current tube frame after releasing object_control");
    }

    @Test
    void closedSubtypeDoesNotCapturePlayers() {
        ObjectInstance elevator = elevator(0x1200, 0x0600, 0x40);
        TestablePlayableSprite player = playerAt(0x1200, 0x0600);

        elevator.update(0, player);

        assertFalse(player.isObjectControlled(),
                "subtype bit 6 selects Obj_LBZTubeElevatorClosed and never enters the capture path");
    }

    @Test
    void closedDestinationElevatorHidesWhenPlayerIsRidingActiveTubeElevator() {
        ObjectInstance activeElevator = elevator(0x1200, 0x0600, 0x10);
        ObjectInstance closedDestination = elevator(0x1360, 0x0878, 0x50);
        TestablePlayableSprite player = playerAt(0x1360, 0x0878);
        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectServices services = new ObjectManagerOnlyServices(objectManager);
        setServices(closedDestination, services);

        player.setOnObject(true);
        player.setObjectControlled(true);
        when(objectManager.getRidingObject(player)).thenReturn(activeElevator);

        closedDestination.update(0, player);

        assertEquals(0x7FF0, closedDestination.getX() & 0xFFFF,
                "Obj_LBZTubeElevatorClosed moves off-screen when Player_1.interact is Obj_LBZTubeElevatorActive");
    }

    @Test
    void mainAndThreeSidekicksEnterAndRideWithIndependentState() {
        TestablePlayableSprite main = playerAt(0x1200, 0x0600);
        TestablePlayableSprite nativeP2 = playerAt(0x1200, 0x0600);
        TestablePlayableSprite extensionOne = playerAt(0x1200, 0x0600);
        TestablePlayableSprite extensionTwo = playerAt(0x1200, 0x0600);
        AbstractObjectInstance elevator = configuredElevator(main,
                List.of(nativeP2, extensionOne, extensionTwo));

        elevator.update(0, main);
        elevator.update(1, main);
        for (int frame = 2; frame < 205; frame++) {
            elevator.update(frame, main);
        }

        assertTrue(elevator.getX() != 0x1200,
                "the fixture must reach shared path motion, not only the entry frame");
        for (TestablePlayableSprite player : List.of(main, nativeP2, extensionOne, extensionTwo)) {
            assertTrue(player.isObjectControlled(),
                    "every policy participant must retain independent tube ownership");
            assertEquals(elevator.getX(), player.getCentreX() & 0xFFFF,
                    "every captured player must follow the shared native elevator position");
        }
        assertFalse(((SolidObjectProvider) elevator).isTopSolidOnly(),
                "captured extension players must participate in the shared start-spin transition");
    }

    @Test
    void nativePlayerOneThenPlayerTwoCaptureSemanticsRemainAuthoritative() {
        TestablePlayableSprite main = playerAt(0x1300, 0x0600);
        TestablePlayableSprite nativeP2 = playerAt(0x1200, 0x0600);
        AbstractObjectInstance elevator = configuredElevator(main, List.of(nativeP2));

        elevator.update(0, main);

        assertFalse(main.isObjectControlled(),
                "P2 entering alone must not retroactively force the already-processed native P1 slot");
        assertTrue(nativeP2.isObjectControlled());
    }

    @Test
    void closedDestinationAlsoSuppressesForExtensionRider() {
        ObjectInstance activeElevator = elevator(0x1200, 0x0600, 0x10);
        AbstractObjectInstance closedDestination = (AbstractObjectInstance) elevator(0x1360, 0x0878, 0x50);
        TestablePlayableSprite main = playerAt(0x1460, 0x0878);
        TestablePlayableSprite nativeP2 = playerAt(0x1460, 0x0878);
        TestablePlayableSprite extension = playerAt(0x1360, 0x0878);
        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, () -> List.of(nativeP2, extension));
        closedDestination.setServices(new TestObjectServices() {
            @Override public ObjectManager objectManager() { return objectManager; }
            @Override public ObjectPlayerQuery playerQuery() { return query; }
        });
        extension.setOnObject(true);
        extension.setObjectControlled(true);
        when(objectManager.getRidingObject(extension)).thenReturn(activeElevator);

        closedDestination.update(0, main);

        assertEquals(0x7FF0, closedDestination.getX() & 0xFFFF,
                "closed destinations must not collide with third and later riders");
    }

    @Test
    void waitExitReleasesAllExtensionsAndUnloadCleansForcedControl() throws Exception {
        TestablePlayableSprite main = playerAt(0x1200, 0x0600);
        TestablePlayableSprite nativeP2 = playerAt(0x1200, 0x0600);
        TestablePlayableSprite extensionOne = playerAt(0x1200, 0x0600);
        TestablePlayableSprite extensionTwo = playerAt(0x1200, 0x0600);
        AbstractObjectInstance elevator = configuredElevator(main,
                List.of(nativeP2, extensionOne, extensionTwo));
        elevator.update(0, main);
        assertTrue(extensionOne.isObjectControlled());
        assertTrue(extensionTwo.isObjectControlled());

        setField(elevator, "state", 8);
        elevator.update(1, main);

        for (TestablePlayableSprite player : List.of(main, nativeP2, extensionOne, extensionTwo)) {
            assertFalse(player.isObjectControlled(), "WaitExit must release every captured participant");
            assertFalse(player.isControlLocked());
            assertFalse(player.isObjectMappingFrameControl());
        }

        // Recapture in a fresh elevator and prove unload does not strand any owner.
        elevator = configuredElevator(main, List.of(nativeP2, extensionOne, extensionTwo));
        elevator.update(2, main);
        elevator.onUnload();
        for (TestablePlayableSprite player : List.of(main, nativeP2, extensionOne, extensionTwo)) {
            assertFalse(player.isObjectControlled(), "unload must clear forced control for every owner");
            assertFalse(player.isControlLocked());
        }
    }

    @Test
    void deadExtensionReleasesWithoutDisturbingOtherRiders() {
        TestablePlayableSprite main = playerAt(0x1200, 0x0600);
        TestablePlayableSprite nativeP2 = playerAt(0x1200, 0x0600);
        TestablePlayableSprite extension = playerAt(0x1200, 0x0600);
        AbstractObjectInstance elevator = configuredElevator(main, List.of(nativeP2, extension));
        elevator.update(0, main);
        assertTrue(extension.isObjectControlled(), "precondition: extension must be captured before death");
        extension.setDead(true);

        elevator.update(1, main);

        assertFalse(extension.isObjectControlled());
        assertFalse(extension.isControlLocked());
        assertTrue(main.isObjectControlled());
        assertTrue(nativeP2.isObjectControlled());
    }

    @Test
    void deadExtensionJoiningOnSecondUpdateIsNotForcedIntoOccupiedTube() {
        TestablePlayableSprite main = playerAt(0x1200, 0x0600);
        TestablePlayableSprite nativeP2 = playerAt(0x1200, 0x0600);
        AbstractObjectInstance elevator = configuredElevator(main, List.of(nativeP2));
        elevator.update(0, main);
        assertTrue(main.isObjectControlled(), "precondition: native rider must occupy the tube first");

        TestablePlayableSprite deadExtension = playerAt(0x1800, 0x0600);
        deadExtension.setDead(true);
        elevator.setServices(playerServices(main, List.of(nativeP2, deadExtension)));
        elevator.update(1, main);

        assertFalse(deadExtension.isObjectControlled(),
                "forced-follow must not recapture a dead extension rider merely because another player is inside");
        assertFalse(deadExtension.isControlLocked());
        assertEquals(0x1800, deadExtension.getCentreX() & 0xFFFF,
                "an invalid extension must not be snapped into the moving tube");
    }

    @Test
    void rosterOmissionAndReorderKeepTubeStateWithPlayerIdentity() {
        TestablePlayableSprite main = playerAt(0x1200, 0x0600);
        TestablePlayableSprite originalP2 = playerAt(0x1200, 0x0600);
        TestablePlayableSprite omitted = playerAt(0x1200, 0x0600);
        TestablePlayableSprite retained = playerAt(0x1200, 0x0600);
        AbstractObjectInstance elevator = configuredElevator(main,
                List.of(originalP2, omitted, retained));
        elevator.update(0, main);
        assertTrue(omitted.isObjectControlled());
        assertTrue(retained.isObjectControlled());

        elevator.setServices(playerServices(main, List.of(retained, originalP2)));
        elevator.update(1, main);
        assertTrue(omitted.isObjectControlled(),
                "temporary omission must retain cleanup and carry ownership");

        elevator.setServices(playerServices(main, List.of(omitted, retained, originalP2)));
        elevator.update(2, main);
        assertTrue(omitted.isObjectControlled(), "rejoining player must recover its own tube state");
        assertTrue(originalP2.isObjectControlled(), "native-to-extension reorder must not discard state");
        assertTrue(retained.isObjectControlled(), "extension-to-native reorder must not transfer state");
    }

    @Test
    void releasedExtensionStateCannotForceCaptureAPlayerOutsideTheEntry() throws Exception {
        TestablePlayableSprite main = playerAt(0x1200, 0x0600);
        TestablePlayableSprite nativeP2 = playerAt(0x1200, 0x0600);
        TestablePlayableSprite released = playerAt(0x1200, 0x0600);
        AbstractObjectInstance elevator = configuredElevator(main, List.of(nativeP2, released));
        elevator.update(0, main);
        setField(elevator, "state", 8);
        elevator.update(1, main);

        TestablePlayableSprite newcomer = playerAt(0x1800, 0x0600);
        elevator.setServices(playerServices(main, List.of(nativeP2, released, newcomer)));
        elevator.update(2, main);

        assertFalse(newcomer.isObjectControlled(),
                "phase-4 history must not act like an occupied tube and capture remote extension players");
    }

    @Test
    void nonEmptyExtensionStateRewindsThroughPlayerRefs() throws Exception {
        TestablePlayableSprite oldMain = playerAt(0x1200, 0x0600);
        TestablePlayableSprite oldP2 = playerAt(0x1200, 0x0600);
        TestablePlayableSprite oldExtension = playerAt(0x1200, 0x0600);
        AbstractObjectInstance elevator = configuredElevator(oldMain, List.of(oldP2, oldExtension));
        elevator.update(0, oldMain);
        RewindObjectStateBlob snapshot = CompactFieldCapturer.capture(
                elevator, rewindContext(oldMain, oldP2, oldExtension));

        TestablePlayableSprite newMain = playerAt(0x1200, 0x0600);
        TestablePlayableSprite newP2 = playerAt(0x1200, 0x0600);
        TestablePlayableSprite newExtension = playerAt(0x1200, 0x0600);
        newExtension.setObjectControlled(true);
        newExtension.setControlLocked(true);
        newExtension.setObjectMappingFrameControl(true);
        elevator.setServices(playerServices(newMain, List.of(newP2, newExtension)));
        CompactFieldCapturer.restore(
                elevator, snapshot, rewindContext(newMain, newP2, newExtension));
        setField(elevator, "state", 8);
        elevator.update(1, newMain);

        assertFalse(newExtension.isObjectControlled(),
                "restored extension ownership must relink through PlayerRefId and release the restored player");
        assertFalse(newExtension.isControlLocked());
        assertFalse(newExtension.isObjectMappingFrameControl());
    }

    private static ObjectInstance elevator(int x, int y, int subtype) {
        Sonic3kObjectRegistry registry = new ZoneForTestRegistry(Sonic3kZoneIds.ZONE_LBZ);
        ObjectInstance elevator = registry.create(new ObjectSpawn(x, y, 0x10, subtype, 0, false, 0));
        if (elevator instanceof com.openggf.level.objects.AbstractObjectInstance object) {
            object.setServices(new TestObjectServices());
        }
        return elevator;
    }

    private static AbstractObjectInstance configuredElevator(
            TestablePlayableSprite main,
            List<TestablePlayableSprite> sidekicks) {
        AbstractObjectInstance elevator = (AbstractObjectInstance) elevator(0x1200, 0x0600, 0);
        elevator.setServices(playerServices(main, sidekicks));
        return elevator;
    }

    private static TestObjectServices playerServices(
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

    private static TestablePlayableSprite playerAt(int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) x, (short) y);
        player.setAir(false);
        player.setObjectControlled(false);
        player.setControlLocked(false);
        player.setXSpeed((short) 0x0200);
        player.setYSpeed((short) 0x0100);
        player.setGSpeed((short) 0x0300);
        player.setJumping(true);
        return player;
    }

    private static void setServices(ObjectInstance instance, ObjectServices services) {
        if (instance instanceof com.openggf.level.objects.AbstractObjectInstance object) {
            object.setServices(services);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = field(target, name);
        field.set(target, value);
    }

    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class ZoneForTestRegistry extends Sonic3kObjectRegistry {
        private final int zoneId;

        private ZoneForTestRegistry(int zoneId) {
            this.zoneId = zoneId;
        }

        @Override
        protected int currentRomZoneId() {
            return zoneId;
        }
    }

    private static final class ObjectManagerOnlyServices extends TestObjectServices {
        private final ObjectManager objectManager;

        private ObjectManagerOnlyServices(ObjectManager objectManager) {
            this.objectManager = objectManager;
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }
    }
}

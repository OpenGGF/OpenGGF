package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.GameRng;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.runtime.CnzZoneRuntimeState;
import com.openggf.game.rules.GameRules;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TestS3kSignpostInstance {

    @Test
    void sparkleYOffsetConsumesRomRandomWord() {
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 0x00001234L);

        assertEquals(6, S3kSignpostInstance.romSparkleYOffset(rng));
        assertEquals(0xEA56EA54L, rng.getSeed(),
                "Obj_SignpostSparkle calls Random_Number once before masking d0 with $1F "
                        + "(docs/skdisasm/sonic3k.asm:176294-176300)");
    }

    @Test
    void sparkleCadenceUsesGlobalVIntPhaseRatherThanAllocationAge() {
        assertFalse(S3kSignpostInstance.isRomSparkleFrame(11023));
        assertTrue(S3kSignpostInstance.isRomSparkleFrame(11024));
        assertFalse(S3kSignpostInstance.isRomSparkleFrame(11025));
        assertTrue(S3kSignpostInstance.isRomSparkleFrame(11028));
    }

    @Test
    void bumpFromBelowRequiresRomAnimationTwoAndUpwardVelocity() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setAir(true);
        player.setYSpeed((short) -0x100);
        player.setAnimationId(Sonic3kAnimationIds.SPRING);

        assertFalse(S3kSignpostInstance.hasRomBumpPose(player),
                "Obj_EndSign sub_83A70 rejects non-#2 animations before applying the upward hit "
                        + "(docs/skdisasm/sonic3k.asm:176372-176387)");

        player.setAnimationId(Sonic3kAnimationIds.ROLL);

        assertTrue(S3kSignpostInstance.hasRomBumpPose(player),
                "Obj_EndSign sub_83A70 accepts animation #2 with upward y_vel "
                        + "(docs/skdisasm/sonic3k.asm:176372-176387)");

        player.setYSpeed((short) 0);

        assertFalse(S3kSignpostInstance.hasRomBumpPose(player),
                "Obj_EndSign sub_83A70 rejects non-upward y_vel "
                        + "(docs/skdisasm/sonic3k.asm:176376-176377)");
    }

    @Test
    void sameFrameNativeP2BumpCanOverwriteNativeP1Velocity() {
        TestablePlayableSprite sonic = eligibleBumpPlayer("sonic", 0x32B0, 0x045D);
        TestablePlayableSprite tails = eligibleBumpPlayer("tails", 0x329A, 0x045D);
        int signpostX = 0x329F;
        int signpostY = 0x045D;

        int finalVelocity = 0;
        for (TestablePlayableSprite player : List.of(sonic, tails)) {
            assertTrue(S3kSignpostInstance.isRomBumpCandidate(signpostX, signpostY, player));
            finalVelocity = S3kSignpostInstance.romBumpXVelocity(signpostX, player.getCentreX());
        }

        assertEquals(0x0050, finalVelocity,
                "EndSign_CheckPlayerHit calls sub_83A70 for Sonic and then Tails after one cooldown check, "
                        + "so a same-frame native P2 hit overwrites the signpost x_vel "
                        + "(docs/skdisasm/sonic3k.asm:176342-176365, 176372-176387)");
    }

    @Test
    void bumpRangeWordsEncodeOffsetAndWidth() {
        TestablePlayableSprite player = eligibleBumpPlayer("sonic", 0, 0);

        player.setCentreX((short) 0x120);
        assertFalse(S3kSignpostInstance.isRomBumpCandidate(0x100, 0, player),
                "EndSign_Range's second word is width $40 from x-$20, so x+$20 is exclusive");
        player.setCentreX((short) 0x11F);
        player.setCentreY((short) 0x18);
        assertFalse(S3kSignpostInstance.isRomBumpCandidate(0x100, 0, player),
                "EndSign_Range's fourth word is height $30 from y-$18, so y+$18 is exclusive");
        player.setCentreY((short) 0x17);
        assertTrue(S3kSignpostInstance.isRomBumpCandidate(0x100, 0, player),
                "Check_PlayerInRange adds each width to its negative origin "
                        + "(docs/skdisasm/sonic3k.asm:176410-176411,179994-180025)");
    }

    @Test
    void landedTimerAdvancesOnlyAfterSignedNegativePostDecrement() {
        assertFalse(S3kSignpostInstance.romPostLandTimerExpired(0),
                "Obj_EndSignLanded uses subq.w then bmi.s; timer 1 -> 0 must keep waiting "
                        + "(docs/skdisasm/sonic3k.asm:176198-176208)");
        assertTrue(S3kSignpostInstance.romPostLandTimerExpired(0xFFFF),
                "Obj_EndSignLanded advances only when the post-decrement word is negative "
                        + "(docs/skdisasm/sonic3k.asm:176198-176208)");
    }

    @Test
    void fallingDispatchAppliesBumpBeforeGravityAndMovement() {
        assertEquals(-0x1F4, S3kSignpostInstance.romVelocityAfterGravity(-0x200),
                "Obj_EndSignFall checks the player hit before adding $0C gravity "
                        + "(docs/skdisasm/sonic3k.asm:176149-176160)");
        assertFalse(S3kSignpostInstance.romBumpCheckAvailableAfterCooldownEntry(1),
                "a nonzero $20 cooldown decrements and returns even when it becomes zero "
                        + "(docs/skdisasm/sonic3k.asm:176347-176405)");
        assertTrue(S3kSignpostInstance.romBumpCheckAvailableAfterCooldownEntry(0));
    }

    @Test
    void mainEndingPoseDoesNotLockCtrl1LogicalInputHistory() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setGameRulesForTest(GameRules.SONIC_3K);
        player.setControlLocked(false);
        player.setSpindash(true);
        player.setPushing(true);
        player.setLogicalInputState(false, false, false, false, false);
        player.endOfTick();

        S3kSignpostInstance.applyMainPlayerEndingPose(player);

        assertTrue(player.isObjectControlSuppressesMovement(),
                "Set_PlayerEndingPose writes object_control=$81 to freeze movement "
                        + "(docs/skdisasm/sonic3k.asm:181977-181988)");
        assertFalse(player.isControlLocked(),
                "Set_PlayerEndingPose does not set Ctrl_1_locked; Obj_EndSignLanded only sets Ctrl_2_locked "
                        + "(docs/skdisasm/sonic3k.asm:176198-176218, 181977-181988)");
        assertFalse(player.getSpindash(),
                "Set_PlayerEndingPose clears spin_dash_flag before control is eventually restored");
        assertFalse(player.getPushing(),
                "Set_PlayerEndingPose clears Status_Push");

        player.setLogicalInputState(false, false, false, true, false);
        player.endOfTick();

        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, player.getInputHistory(0),
                "Sonic_RecordPos must keep storing live Ctrl_1_logical for Tails' delayed follow input "
                        + "(docs/skdisasm/sonic3k.asm:21541-21545, 22119-22136)");
    }

    @Test
    void signpostCtrl2LockDoesNotApplyEndingPoseToSidekick() {
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        SidekickCpuController cpu = new SidekickCpuController(tails, sonic);
        tails.setXSpeed((short) 0x0060);
        tails.setYSpeed((short) -0x0100);
        tails.setGSpeed((short) 0x0060);
        tails.setAnimationId(Sonic3kAnimationIds.WALK);

        S3kSignpostInstance.applySidekickInputLock(tails);

        assertTrue(tails.isControlLocked(),
                "Obj_EndSignLanded writes Ctrl_2_locked before results");
        assertTrue(cpu.isController2SignedLocked(),
                "Ctrl_2_locked=$FF skips Tails_CPU_Control from the next player slot");
        assertEquals(0x0060, tails.getXSpeed());
        assertEquals(-0x0100, tails.getYSpeed());
        assertEquals(0x0060, tails.getGSpeed());
        assertEquals(Sonic3kAnimationIds.WALK.id(), tails.getAnimationId());
        assertFalse(tails.isObjectControlled(),
                "Set_PlayerEndingPose targets Player_1 here; Check_TailsEndPose runs later "
                        + "(docs/skdisasm/sonic3k.asm:176229-176238,181914-181943)");
    }

    @Test
    void laterCheckTailsEndPoseClearsLockAndStopsSidekick() {
        TestablePlayableSprite tails = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        SidekickCpuController cpu = new SidekickCpuController(tails, sonic);
        tails.setControlLocked(true);
        cpu.setController2SignedLocked(true);
        tails.setXSpeed((short) 0x0054);
        tails.setYSpeed((short) 0);
        tails.setGSpeed((short) 0x0054);

        S3kSignpostInstance.applySidekickEndingPose(tails);

        assertFalse(tails.isControlLocked());
        assertFalse(cpu.isController2SignedLocked());
        assertTrue(tails.isObjectControlled());
        assertEquals(0, tails.getXSpeed());
        assertEquals(0, tails.getYSpeed());
        assertEquals(0, tails.getGSpeed());
        assertEquals(Sonic3kAnimationIds.VICTORY.id(), tails.getAnimationId());
    }

    @Test
    void afterStateKeepsSignpostAliveInsideRomRangeBeyondGenericScreenMargin() throws Exception {
        Camera camera = new Camera();
        camera.setX((short) 0x31C0);
        camera.setY((short) 0x0400);
        AbstractObjectInstance.updateCameraBounds(0x31C0, 0x0400, 0x3300, 0x04E0, 0);
        S3kSignpostInstance signpost = new S3kSignpostInstance(0x32C0, 0);
        signpost.setServices(new TestObjectServices().withCamera(camera));
        setPrivateField(signpost, "state", enumConstant(signpost, "State", "AFTER"));
        setPrivateField(signpost, "worldY", 0x0390);

        signpost.update(0, null);

        assertFalse(signpost.isDestroyed(),
                "Obj_EndSignAfter keeps the signpost alive while "
                        + "y_pos-Camera_Y_pos+$80 is within $200, even when the generic "
                        + "64px object-screen margin would reject it "
                        + "(docs/skdisasm/sonic3k.asm:176244-176277)");
    }

    @Test
    void afterRangeUsesRomCoarseBackCameraWord() {
        assertTrue(S3kSignpostInstance.isWithinRomAfterRange(0x3140, 0x0400, 0x31C0, 0x0400),
                "Obj_EndSignAfter subtracts Camera_X_pos_coarse_back, which is "
                        + "(Camera_X_pos-$80)&$FF80, so the signpost remains alive at "
                        + "the ROM back edge (docs/skdisasm/sonic3k.asm:176262-176266,37550-37553)");
    }

    @Test
    void afterStateKeepsSignpostAliveOutsideRomRangeWhileResultsAreActive() throws Exception {
        Camera camera = new Camera();
        camera.setX((short) 0x3600);
        camera.setY((short) 0x0400);
        GameStateManager gameState = new GameStateManager();
        gameState.setEndOfLevelActive(true);

        S3kSignpostInstance signpost = new S3kSignpostInstance(0x32C0, 0);
        signpost.setServices(new TestObjectServices()
                .withCamera(camera)
                .withGameState(gameState));
        setPrivateField(signpost, "state", enumConstant(signpost, "State", "AFTER"));
        setPrivateField(signpost, "worldY", 0x0390);

        signpost.update(0, null);

        assertFalse(signpost.isDestroyed(),
                "Obj_EndSignAfter must not delete the signpost while Obj_LevelResults is active; "
                        + "CNZ moves the camera during the post-miniboss transition, but the signpost "
                        + "remains visible through the results screen");
    }

    @Test
    void resultsStateUsesCameraFocusedPlayerWhenUpdatePlayerIsNull() throws Exception {
        Camera camera = new Camera();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setAir(false);
        camera.setFocusedSprite(player);

        List<ObjectInstance> spawned = new ArrayList<>();
        ObjectManager objectManager = mock(ObjectManager.class);
        doAnswer(invocation -> {
            spawned.add(invocation.getArgument(0));
            return null;
        }).when(objectManager).addDynamicObject(any());

        ZoneRuntimeRegistry registry = new ZoneRuntimeRegistry();
        registry.install(new CnzZoneRuntimeState(0, PlayerCharacter.SONIC_AND_TAILS, new Sonic3kCNZEvents()));

        S3kSignpostInstance signpost = new S3kSignpostInstance(0x32C0, 0);
        signpost.setServices(new SignpostResultsServices(camera, objectManager, registry));
        setPrivateField(signpost, "state", enumConstant(signpost, "State", "RESULTS"));

        signpost.update(0, null);

        assertTrue(spawned.stream().anyMatch(S3kResultsScreenObjectInstance.class::isInstance),
                "Obj_EndSignLanded must still allocate Obj_LevelResults when the active "
                        + "player is only available through the runtime player query/camera focus; "
                        + "CNZ spawns the signpost from the event path after the boss "
                        + "(docs/skdisasm/sonic3k.asm:176208-176218)");
        verify(objectManager).addDynamicObject(any(S3kResultsScreenObjectInstance.class));
        verify(objectManager, never()).addDynamicObjectAfterCurrent(any());
    }

    private static TestablePlayableSprite eligibleBumpPlayer(String code, int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0, (short) 0);
        player.setCentreX((short) x);
        player.setCentreY((short) y);
        player.setYSpeed((short) -0x100);
        player.setAnimationId(Sonic3kAnimationIds.ROLL);
        return player;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Object instance, String enumSimpleName, String constantName) {
        for (Class<?> nested : instance.getClass().getDeclaredClasses()) {
            if (nested.getSimpleName().equals(enumSimpleName)) {
                return Enum.valueOf((Class<? extends Enum>) nested.asSubclass(Enum.class), constantName);
            }
        }
        throw new AssertionError("Missing nested enum " + enumSimpleName);
    }

    private static void setPrivateField(Object instance, String fieldName, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static final class SignpostResultsServices extends TestObjectServices {
        private final Camera camera;
        private final ObjectManager objectManager;
        private final ZoneRuntimeRegistry registry;

        private SignpostResultsServices(Camera camera, ObjectManager objectManager, ZoneRuntimeRegistry registry) {
            this.camera = camera;
            this.objectManager = objectManager;
            this.registry = registry;
        }

        @Override
        public Camera camera() {
            return camera;
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }

        @Override
        public ZoneRuntimeRegistry zoneRuntimeRegistry() {
            return registry;
        }
    }
}

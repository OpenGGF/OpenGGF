package com.openggf.game.sonic3k.objects;

import com.openggf.game.session.SessionManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAizEndBossInstance {
    private Camera camera;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        camera = GameServices.camera();
        camera.resetState();
        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(false);
        constructionContext().remove();
        SessionManager.clear();
    }

    @Test
    void initLocksCameraAndLoadsPaletteIntoOwnershipRegistryLineOne() throws Exception {
        camera.setX((short) 0x48A0);

        byte[] paletteLine = new byte[32];
        paletteLine[0] = 0x0E;
        paletteLine[1] = (byte) 0xEE;
        paletteLine[2] = 0x08;
        paletteLine[3] = (byte) 0x88;

        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        services.withRom(new FixedReadRom(paletteLine));
        services.registry.beginFrame();

        AizEndBossInstance boss = buildBoss(services);
        boss.update(0, null);
        services.registry.resolveInto(services.level.palettes(), null, null, null);

        assertEquals(S3kPaletteOwners.AIZ_END_BOSS, services.registry.ownerAt(PaletteSurface.NORMAL, 1, 0));
        assertEquals(S3kPaletteOwners.AIZ_END_BOSS, services.registry.ownerAt(PaletteSurface.NORMAL, 1, 1));
        assertColorWord(services.level.getPalette(1), 0, 0x0EEE);
        assertColorWord(services.level.getPalette(1), 1, 0x0888);
        assertEquals(0x4880, camera.getMinX() & 0xFFFF);
        assertEquals(0x4880, camera.getMaxX() & 0xFFFF);
    }

    @Test
    void retreatSubmergeAnimationRunsRepositionCallback() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);
        invokeNoArg(boss, "beginReSubmerge");

        for (int frame = 0; frame < 45; frame++) {
            boss.update(frame, null);
        }

        assertEquals(12, boss.getState().routine);
    }

    @Test
    void retreatSubmergeCancelsHitFlashWithoutRestoringCollisionBehindWaterfall() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);
        invokeNoArg(boss, "onEmergeComplete");
        boss.update(0, null);
        boss.onPlayerAttack(null, null);

        assertTrue(boss.getState().invulnerable);
        assertEquals(0, boss.getCollisionFlags());

        invokeNoArg(boss, "beginReSubmerge");

        assertFalse(boss.getState().invulnerable,
                "ROM sub_69C94 clears the hit-flash timer/status when Robotnik submerges");
        assertEquals(0, boss.getState().invulnerabilityTimer);
        for (int frame = 0; frame < 0x20; frame++) {
            boss.update(frame, null);
            assertEquals(0, boss.getCollisionFlags(),
                    "Robotnik must remain non-interactive throughout the waterfall phase");
        }
    }

    @Test
    void fireSignalTriggersBurnBridgeVariant() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);

        AizCollapsingLogBridgeObjectInstance bridge = new AizCollapsingLogBridgeObjectInstance(
                new ObjectSpawn(0, 0, 0x2C, 0x80, 0, false, 0));
        bridge.setServices(new TestObjectServices().withCamera(camera));

        invokeNoArg(boss, "onFireTimerExpired");
        bridge.update(0, null);

        assertTrue(readBoolean(bridge, "segmentsSpawned"));
        assertEquals(4, bridge.getPriorityBucket(),
                "AIZCollapsingLogBridge_InitFire writes priority $200");
        assertFalse(bridge.isHighPriority(),
                "render_flags = 4 must not promote the bridge above every terrain pixel");
        assertEquals(0b0111, bridge.getTileOcclusionPaletteMask(),
                "fire bridge must remain above the palette-3 waterfall but below palette 0-2 terrain");
    }

    @Test
    void retreatDoesNotClearBridgeBurnLatchBeforeBridgeConsumesIt() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);

        AizCollapsingLogBridgeObjectInstance bridge = new AizCollapsingLogBridgeObjectInstance(
                new ObjectSpawn(0, 0, 0x2C, 0x80, 0, false, 0));
        bridge.setServices(new TestObjectServices().withCamera(camera));

        invokeNoArg(boss, "onFireTimerExpired");
        invokeNoArg(boss, "beginRetreat");
        bridge.update(0, null);

        assertTrue(readBoolean(bridge, "segmentsSpawned"));
    }

    @Test
    void cameraScrollPhaseAdvancesArenaBoundsWithRomParity() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);
        camera.setX((short) 0x4880);
        camera.setMinX((short) 0x4880);
        camera.setMaxX((short) 0x4880);

        invokeNoArg(boss, "updateCameraScroll");

        assertEquals(0x4882, camera.getMinX() & 0xFFFF);
        assertEquals(0x4882, camera.getMaxX() & 0xFFFF);

        camera.setMinX((short) 0x48E0);
        camera.setMaxX((short) 0x48E0);
        invokeNoArg(boss, "updateCameraScroll");

        assertEquals(0x48E0, camera.getMinX() & 0xFFFF);
        assertEquals(0x48E2, camera.getMaxX() & 0xFFFF);
    }

    @Test
    void revealedRawAnimationUsesRomCallbackTimingBeforeHover() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);
        invokeNoArg(boss, "onEmergeComplete");

        for (int frame = 0; frame < 19; frame++) {
            invokeNoArg(boss, "updateRevealed");
        }
        assertEquals(4, boss.getState().routine,
                "byte_69DB3 should not hit its $F4 callback before the 20th revealed update");
        assertEquals(0, boss.getMappingFrame(),
                "byte_69DB3's final visible entry is mapping frame 0 for one update");

        invokeNoArg(boss, "updateRevealed");

        assertEquals(6, boss.getState().routine,
                "byte_69DB3 $F4 callback should enter loc_6933A hover on the 20th update");
    }

    @Test
    void cameraScrollPhaseLetsCameraFollowIntoExpandedRightBound() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x49A8);
        player.setCentreY((short) 0x0100);
        camera.setFocusedSprite(player);
        camera.setX((short) 0x48E0);
        camera.setY((short) 0x00A0);
        camera.setMinX((short) 0x48E0);
        camera.setMaxX((short) 0x48E0);
        camera.setMinY((short) 0);
        camera.setMaxY((short) 0x1000);

        invokeNoArg(boss, "updateCameraScroll");
        camera.updatePosition();

        assertEquals(0x48E2, camera.getX() & 0xFFFF);
    }

    @Test
    void hoverSetupDoesNotChangeBossRenderFacing() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);

        invokeNoArg(boss, "doMainInit");
        invokeNoArg(boss, "beginHover");

        assertTrue(boss.isFacingRight());
    }

    @Test
    void repositionSelectorUsesRomRawMaskNotLowTwoBits() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 0xA1AFBE1BL);
        services.withRng(rng);

        AizEndBossInstance boss = buildBoss(services);
        boss.getState().x = 0x48A0;
        boss.getState().y = 0x01BD;
        boss.getState().xFixed = 0x48A00000;
        boss.getState().yFixed = 0x01BD0000;

        invokeNoArg(boss, "selectRandomPosition");

        assertEquals(0x08, boss.getAngle(),
                "Obj_AIZEndBoss loc_69A66 masks Random_Number with #$C; "
                        + "raw A1AF5778 should select target index $8, not low-bit index 0 "
                        + "(sonic3k.asm:138748-138756)");
        assertEquals(((0x4A40 - 0x48A0) << 8) / 0x80, boss.getState().xVel,
                "Target index $8 should travel to _unkFA84+$160 = $4A40 over $80 frames");
    }

    @Test
    void repositionVelocityUsesFullLongwordPositionSubpixels() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        GameRng rng = new GameRng(GameRng.Flavour.S3K, 0xA1AFBE1BL);
        services.withRng(rng);

        AizEndBossInstance boss = buildBoss(services);
        boss.getState().x = 0x48A0;
        boss.getState().y = 0x01BD;
        boss.getState().xFixed = 0x48A00000;
        boss.getState().yFixed = 0x01BD8000;

        invokeNoArg(boss, "selectRandomPosition");

        assertEquals(-0x003B, boss.getState().yVel,
                "loc_69A66 subtracts the full longword y_pos before doubling the delta; "
                        + "$01BD.8000 to target $01A0 should produce y_vel=-$3B "
                        + "(sonic3k.asm:138764-138771)");
    }

    @Test
    void hoveredKillingHitRetainsOldDispatchUntilItsNativeCheckHitTail() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        AizEndBossInstance boss = buildBoss(services);
        invokeNoArg(boss, "beginHover");
        boss.getState().hitCount = 1;
        int beforeY = boss.getState().yFixed;

        boss.onPlayerAttack(null, null);

        assertEquals(6, boss.getState().routine,
                "Touch_Enemy changes HP/status, not the hovering code owner or its routine");
        assertEquals(0, services.gameState().getScore(),
                "BossDefeated_StopTimer publishes score only at AIZEndBoss_CheckHitOrDefeat");
        boss.update(0, null);
        assertEquals(beforeY + 0xB000, boss.getState().yFixed,
                "AIZEndBoss_Hover performs Swing $C0-$10 and MoveSprite2 before its CheckHit tail");
        assertEquals(0x3F, readInt(boss, "waitTimer"),
                "Hover's tail starts Wait_FadeToLevelMusic without dispatching that new owner");
        assertEquals(1000, services.gameState().getScore());
        boss.update(1, null);
        assertEquals(0x3E, readInt(boss, "waitTimer"),
                "the following Wait_FadeToLevelMusic dispatch performs the first predecrement");
    }

    @Test
    void attackWaitKillingHitFallsThroughItsExistingObjWaitAfterNativeCheckHit() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        AizEndBossInstance boss = buildBoss(services);
        invokeNoArg(boss, "beginHover");
        // AIZEndBoss_StartAttackWait changes routine to8 and $2E to$3F.
        boss.getState().routine = 8;
        Field wait = AizEndBossInstance.class.getDeclaredField("waitTimer");
        wait.setAccessible(true);
        wait.setInt(boss, 0x3F);
        boss.getState().hitCount = 1;

        boss.onPlayerAttack(null, null);

        assertEquals(8, boss.getState().routine,
                "Touch_Enemy retains the AttackWait dispatcher until its native hit check");
        boss.update(0, null);
        assertEquals(0x3E, readInt(boss, "waitTimer"),
                "AIZEndBoss_AttackWait checks hit, then tail-jumps Obj_Wait after code replacement");
        assertEquals(1000, services.gameState().getScore());
        boss.update(1, null);
        assertEquals(0x3D, readInt(boss, "waitTimer"));
    }

    @Test
    void defeatCapsuleHandoffWaitUsesObjWaitPredecrementSemantics() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        AizEndBossInstance boss = buildBoss(services);
        // Collision is clear until AIZEndBoss_Revealed, so a killing hit is
        // native-reachable only in Revealed/Hover/AttackWait (sonic3k.asm:138109-138114).
        invokeNoArg(boss, "beginHover");
        boss.getState().hitCount = 1;

        boss.onPlayerAttack(null, null);
        boss.update(0, null);
        assertEquals(0x3F, readInt(boss, "waitTimer"),
                "Hover's CheckHit tail installs Wait_FadeToLevelMusic with $2E=$3F before any defeat tick");

        for (int frame = 0; frame < 0x38; frame++) {
            boss.update(frame, null);
            assertTrue(!readBoolean(boss, "eggCapsuleSignal"),
                    "The ship/explosion handoff remains in Wait_Draw before the independent Obj_Wait begins");
        }

        for (int frame = 0; frame < 0x7F; frame++) {
            boss.update(frame, null);
            assertTrue(!readBoolean(boss, "eggCapsuleSignal"),
                    "Obj_Wait branches only after --$2E is negative, so $2E=$7F "
                            + "must not spawn the capsule while it is still non-negative "
                            + "(sonic3k.asm:177944-177952)");
        }

        boss.update(0x7F, null);

        assertTrue(readBoolean(boss, "eggCapsuleSignal"),
                "The AIZ capsule callback should run on the negative transition "
                        + "after 128 Obj_Wait entries from $2E=$7F");
    }

    @Test
    void waitingLayoutBossRetainsItsOriginalSlotUntilTheArenaIsReached() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        var manager = new com.openggf.level.objects.ObjectManager(List.of(),
                new Sonic3kObjectRegistry(), 0, null, null,
                GameServices.graphics(), camera, services);
        services.withDirectObjectManager(manager);
        camera.setX((short) 0x4680);
        camera.setY((short) 0x015A);
        AizEndBossInstance boss = buildBoss(services);
        manager.addDynamicObjectAtSlot(boss, 14);

        manager.update(camera.getX(), null, List.of(), 1);
        camera.setX((short) 0x44C0);
        manager.update(camera.getX(), null, List.of(), 2);

        assertFalse(boss.isDestroyed(),
                "Obj_AIZEndBossWait returns while Camera_X_pos is below $4880; it has no range tail");
        assertTrue(manager.getActiveObjects().contains(boss));
        assertEquals(14, boss.getSlotIndex(), "later controls must retain the original Obj_Load allocation");
        camera.setX((short) 0x4880);
        manager.update(camera.getX(), null, List.of(), 3);
        assertEquals(14, boss.getSlotIndex());
        assertTrue(services.gameState().getCurrentBossId() != 0);
    }

    @Test
    void shipEscapeSignalWaitsForStartDefeatAfterTheFadeToLevelMusicWait() throws Exception {
        // loc_69C36 (pinned 044fa46 sonic3k.asm:139005-139011) installs Wait_FadeToLevelMusic with
        // $34 = loc_69482 and only clears $38 bit 6. Bit 4, which Obj_RobotnikShip's routine 4
        // (loc_67DD8) waits for before its escape, is set by loc_69482 (:138300-138305) when the
        // $3F countdown underflows.
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        AizEndBossInstance boss = buildBoss(services);
        invokeNoArg(boss, "beginHover");
        boss.getState().hitCount = 1;
        boss.onPlayerAttack(null, null);
        boss.update(0, null);
        assertEquals(0x3F, readInt(boss, "waitTimer"), "precondition: Wait_FadeToLevelMusic armed with $2E=$3F");
        assertFalse(boss.isDefeatStarted(), "the defeat callback does not set $38 bit 4");

        for (int pass = 0; pass < 0x3F; pass++) {
            boss.update(pass, null);
            assertFalse(boss.isDefeatStarted(), "bit 4 stays clear while $2E counts down, pass " + pass);
        }
        boss.update(0x3F, null);
        assertTrue(boss.isDefeatStarted(), "the pass where $2E underflows runs loc_69482: bset #4,$38(a0)");
    }

    @Test
    void startDefeatArmsTheRomDrawBridgeOwner() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        AizEndBossInstance boss = buildBoss(services);

        invokeNoArg(boss, "startDefeat");

        assertTrue(Aiz2BossEndSequenceState.isBridgeDropTriggered(),
                "AIZEndBoss_StartDefeat writes _unkFAA3 before the capsule wait");
    }

    @Test
    void capsuleCreationRetainsRomLayoutOwnersAndTheBossSlot() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        var manager = new com.openggf.level.objects.ObjectManager(
                List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                GameServices.graphics(), camera, services);
        services.withDirectObjectManager(manager);
        AizEndBossInstance boss = buildBoss(services);
        S3kCutsceneButtonObjectInstance button = new S3kCutsceneButtonObjectInstance(
                new ObjectSpawn(0x4B18, 0x0189, 0x83, 0, 0, false, 0));
        AizDrawBridgeObjectInstance bridge = new AizDrawBridgeObjectInstance(
                new ObjectSpawn(0x4B48, 0x0218, 0x32, 0, 2, false, 0));
        manager.addDynamicObjectAtSlot(button, 4);
        manager.addDynamicObjectAtSlot(bridge, 8);
        manager.addDynamicObjectAtSlot(boss, 14);
        Aiz2BossEndSequenceState.triggerBridgeDrop();

        invokeNoArg(boss, "spawnEggCapsuleAndFinish");
        button.update(0, null);
        bridge.update(0, null);

        assertFalse(button.isDestroyed(), "the ROM-loaded button must keep its identity and SST slot");
        assertFalse(bridge.isDestroyed(), "the ROM-loaded drawbridge must keep its identity and SST slot");
        assertEquals(4, button.getSlotIndex());
        assertEquals(8, bridge.getSlotIndex());
        assertEquals(14, boss.getSlotIndex());
        assertTrue(Aiz2BossEndSequenceState.isBridgeDropTriggered(),
                "creating the capsule must not clear _unkFAA3");
        assertTrue(manager.getActiveObjects().stream()
                        .noneMatch(Aiz2BossEndSequenceController.class::isInstance),
                "post-defeat dispatch replaces the boss routine, not its SST owner");
    }

    @Test
    void postDefeatContinuationRewindsIntoARecreatedBossAndReplaysForward() throws Exception {
        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());
        AizEndBossInstance original = buildBoss(services);
        Field active = AizEndBossInstance.class.getDeclaredField("postDefeatSequenceActive");
        active.setAccessible(true);
        active.setBoolean(original, true);
        Aiz2BossEndSequenceController originalController = continuationController(original);
        var checkpoint = new Aiz2BossEndSequenceController.SequenceSnapshot(
                0x4880, 0x015A, true, true, false, true, true, false,
                false, true, 0x100, 0);
        originalController.restoreSequenceState(checkpoint);
        var context = com.openggf.game.rewind.schema.RewindCaptureContext.withIdentityTable(
                new com.openggf.game.rewind.identity.RewindIdentityTable());
        var snapshot = com.openggf.game.rewind.schema.CompactFieldCapturer
                .captureDefaultObjectSubclassScalars(original, context);
        AizEndBossInstance restored = buildBoss(services);
        com.openggf.game.rewind.schema.CompactFieldCapturer
                .restoreDefaultObjectSubclassScalars(restored, snapshot, context);
        Aiz2BossEndSequenceController restoredController = continuationController(restored);

        assertTrue(readBoolean(restored, "postDefeatSequenceActive"));
        assertEquals(checkpoint, restoredController.captureSequenceState(),
                "the continuation is value state, not a reference to an unregistered object");
        org.junit.jupiter.api.Assertions.assertNotSame(originalController, restoredController);
        original.update(42, new TestablePlayableSprite("sonic", (short) 0x4A78, (short) 0x0200));
        restored.update(42, new TestablePlayableSprite("sonic", (short) 0x4A78, (short) 0x0200));
        assertEquals(originalController.captureSequenceState(), restoredController.captureSequenceState(),
                "forward replay must resume the same camera-release and transition phase");
    }

    private static Aiz2BossEndSequenceController continuationController(AizEndBossInstance boss)
            throws Exception {
        Field sequence = AizEndBossInstance.class.getDeclaredField("postDefeatSequence");
        sequence.setAccessible(true);
        Object holder = sequence.get(boss);
        Field controller = holder.getClass().getDeclaredField("controller");
        controller.setAccessible(true);
        return (Aiz2BossEndSequenceController) controller.get(holder);
    }

    private static AizEndBossInstance buildBoss(ObjectServices services) throws Exception {
        if (services instanceof TestObjectServices testServices && testServices.configuration() == null) {
            testServices.withConfiguration(SonicConfigurationService.getInstance());
        }
        ThreadLocal<ObjectServices> context = constructionContext();
        context.set(services);
        try {
            AizEndBossInstance boss = new AizEndBossInstance(
                    new ObjectSpawn(0x48E0, 0x015A, 0x92, 0, 0, false, 0));
            boss.setServices(services);
            return boss;
        } finally {
            context.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<ObjectServices> constructionContext() throws Exception {
        Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
        field.setAccessible(true);
        return (ThreadLocal<ObjectServices>) field.get(null);
    }

    private static void invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static int readInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static boolean readBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static void assertColorWord(Palette palette, int colorIndex, int segaWord) {
        byte highByte = (byte) ((segaWord >> 8) & 0xFF);
        byte lowByte = (byte) (segaWord & 0xFF);
        int r3 = (lowByte >> 1) & 0x07;
        int g3 = (lowByte >> 5) & 0x07;
        int b3 = (highByte >> 1) & 0x07;
        int expectedR = (r3 * 255 + 3) / 7;
        int expectedG = (g3 * 255 + 3) / 7;
        int expectedB = (b3 * 255 + 3) / 7;
        assertEquals(expectedR, palette.getColor(colorIndex).r & 0xFF);
        assertEquals(expectedG, palette.getColor(colorIndex).g & 0xFF);
        assertEquals(expectedB, palette.getColor(colorIndex).b & 0xFF);
    }

    private static final class RecordingServices extends TestObjectServices {
        private final StubLevel level = new StubLevel();
        private final PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();

        @Override
        public Level currentLevel() {
            return level;
        }

        @Override
        public PaletteOwnershipRegistry paletteOwnershipRegistryOrNull() {
            return registry;
        }
    }

    private static final class FixedReadRom extends Rom {
        private final byte[] bytes;

        private FixedReadRom(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public byte[] readBytes(long offset, int count) {
            return bytes;
        }
    }

    private static final class StubLevel implements Level {
        private final Palette[] palettes = new Palette[] {
                new Palette(), new Palette(), new Palette(), new Palette()
        };

        Palette[] palettes() {
            return palettes;
        }

        @Override public int getPaletteCount() { return palettes.length; }
        @Override public Palette getPalette(int index) { return palettes[index]; }
        @Override public int getPatternCount() { return 0; }
        @Override public Pattern getPattern(int index) { throw new UnsupportedOperationException(); }
        @Override public int getChunkCount() { return 0; }
        @Override public Chunk getChunk(int index) { throw new UnsupportedOperationException(); }
        @Override public int getBlockCount() { return 0; }
        @Override public Block getBlock(int index) { throw new UnsupportedOperationException(); }
        @Override public SolidTile getSolidTile(int index) { throw new UnsupportedOperationException(); }
        @Override public Map getMap() { return null; }
        @Override public List<ObjectSpawn> getObjects() { return List.of(); }
        @Override public List<RingSpawn> getRings() { return List.of(); }
        @Override public RingSpriteSheet getRingSpriteSheet() { return null; }
        @Override public int getMinX() { return 0; }
        @Override public int getMaxX() { return 0; }
        @Override public int getMinY() { return 0; }
        @Override public int getMaxY() { return 0; }
        @Override public int getZoneIndex() { return 0; }
    }
}

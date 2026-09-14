package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.parallel.Isolated
class TestFbzMinibossChildren {
    @Test
    void circularChainCarriesParentFractionsThroughAllFiveLinks() throws Exception {
        var arm = FbzMinibossArmChild.forTest(
                new FbzMinibossInstance(new ObjectSpawn(0x1000, 0x700, 0xAA, 0, 0, false, 0)), 0);
        setInt(arm, "xFixed", 0x100080);
        setInt(arm, "yFixed", 0x70080);
        int expectedX = 0x100080;
        int expectedY = 0x70080;
        var links = arm.createLinksForTest();
        for (int i = 0; i < links.length; i++) {
            var link = links[i];
            setState(link, "STAGGER");
            setInt(link, "stagger", 10);
            setInt(link, "angle", 0x20);
            link.update(0, null);
            // GetSineCosine($20) returns $B5 for both words; native shifts
            // by four (ordinary links) or three (the terminal).
            int displacement = 0xB5 * (i == 4 ? 32 : 16);
            expectedX += displacement;
            expectedY += displacement;
            assertEquals(expectedX, getInt(link, "xFixed"));
            assertEquals(expectedY, getInt(link, "yFixed"));
            assertEquals(expectedX >> 8, link.getX());
            assertEquals(expectedY >> 8, link.getY());
        }
    }

    @Test
    void outwardAttackWaitsWhilePlayerIsJumping() throws Exception {
        var boss = new FbzMinibossInstance(new ObjectSpawn(0x1000, 0x700, 0xAA, 0, 0, false, 0));
        var arm = FbzMinibossArmChild.forTest(boss, 0);
        setState(arm, "PATROL");
        setInt(arm, "timer", 0);
        var player = mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) 0xF00);
        when(player.isJumping()).thenReturn(true);
        arm.update(0, player);
        assertEquals("PATROL", stateName(arm), "loc_6F2E4 tests Player_1+$40 before aiming");
        assertFalse(boss.rootBit(FbzMinibossInstance.ROOT_OUTWARD_BUSY));

        when(player.isJumping()).thenReturn(false);
        setInt(arm, "timer", 0);
        arm.update(1, player);
        assertEquals("OUTWARD_ARMED", stateName(arm));
        assertTrue(boss.rootBit(FbzMinibossInstance.ROOT_OUTWARD_BUSY));
    }

    @ParameterizedTest
    @CsvSource({"0,-67,-65,false", "0,-66,-64,true", "0,-65,-64,true",
            "1,67,65,false", "1,66,64,true", "1,65,64,true"})
    void normalAttackAlignmentClampsAtOrAcrossTheUnsignedTarget(
            int side, int initialAngle, int expectedAngle, boolean reached) throws Exception {
        FbzMinibossArmChild arm  =  FbzMinibossArmChild.forTest(
                boss(new QueryServices(null, List.of())), side);
        FbzMinibossChainLink terminal  =  arm.createLinksForTest()[4];
        setState(terminal, "NORMAL_ALIGN");
        setInt(terminal, "angle", initialAngle);
        terminal.update(0, null);
        assertEquals(expectedAngle, getInt(terminal, "angle"));
        assertEquals(reached ? "NORMAL_WAIT_PARENT" : "NORMAL_ALIGN", stateName(terminal));
        assertEquals(reached, arm.controlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE),
                "loc_6F5B8 publishes completion only after sub_6F830 clamps the terminal link");
    }

    @ParameterizedTest
    @CsvSource({"0,-67,-65", "0,-66,-64", "0,-65,-64",
            "1,67,65", "1,66,64", "1,65,64"})
    void normalArmUsesTheSameUnsignedClampAsItsLinks(int side, int before, int expected) throws Exception {
        var arm = FbzMinibossArmChild.forTest(boss(new QueryServices(null, List.of())), side);
        setState(arm, "NORMAL_SWING");
        setInt(arm, "angle", before);
        arm.update(0, null);
        assertEquals(expected, getInt(arm, "angle"));
        if (expected == (side == 0 ? -64 : 64)) {
            arm.update(1, null);
            assertEquals(expected, getInt(arm, "angle"),
                    "the clamped arm holds until its terminal link completes");
        }
    }

    @ParameterizedTest
    @CsvSource({"0,-99,-97,false", "0,-98,-96,true", "0,-97,-96,true",
            "1,99,97,false", "1,98,96,true", "1,97,96,true"})
    void recycleClampsEveryLinkButOnlyTheTerminalPublishesArmReadiness(
            int side, int before, int expected, boolean reached) throws Exception {
        for (int index : new int[] {0, 4}) {
            var arm = FbzMinibossArmChild.forTest(boss(new QueryServices(null, List.of())), side);
            var link = arm.createLinksForTest()[index];
            setState(link, "RECYCLE_OR_RESET");
            setInt(link, "angle", before);
            link.update(0, null);
            assertEquals(expected, getInt(link, "angle"));
            assertEquals(reached && index == 4, arm.controlBit(FbzMinibossArmChild.ARM_PATROL_READY));
        }
    }

    @ParameterizedTest
    @CsvSource({"0,-125,-127,false", "0,-126,-128,true", "0,-127,-128,true",
            "1,125,127,false", "1,126,-128,false", "1,127,-128,true", "1,-128,-128,true"})
    void outwardAlignmentPreservesTheNativeAsymmetricEqualityBranch(
            int side, int before, int expected, boolean reached) throws Exception {
        var arm = FbzMinibossArmChild.forTest(boss(new QueryServices(null, List.of())), side);
        var terminal = arm.createLinksForTest()[4];
        setState(terminal, "OUTWARD_ALIGN");
        setInt(terminal, "angle", before);
        terminal.update(0, null);
        assertEquals(expected, getInt(terminal, "angle"));
        assertEquals(reached ? "OUTWARD_WAIT_PARENT" : "OUTWARD_ALIGN", stateName(terminal));
        assertEquals(reached, arm.controlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE));
    }

    @Test
    void normalAttackHoldReleasesTheChainBeforeRecycleCanRestartTheArm() throws Exception {
        var boss = boss(new QueryServices(null, List.of()));
        var arm = FbzMinibossArmChild.forTest(boss, 0);
        var links = arm.createLinksForTest();
        var terminal = links[4];
        setState(arm, "NORMAL_HOLD");
        setInt(arm, "angle", -64);
        setInt(arm, "timer", 1);
        arm.setControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE);
        setState(terminal, "NORMAL_FAN");
        setInt(terminal, "angle", 0x3A);
        setInt(links[3], "angleStep", 2);

        arm.update(0, null);
        terminal.update(0, null);
        assertEquals(-64, getInt(arm, "angle"), "Obj_Wait never advances the arm angle");
        assertEquals("RECYCLE_WAIT", stateName(terminal));
        assertTrue(boss.rootBit(FbzMinibossInstance.ROOT_ARM_RETURNED));
        assertFalse(arm.controlBit(FbzMinibossArmChild.ARM_PATROL_READY),
                "fan impact publishes root state, not arm recycle readiness");

        arm.update(1, null);
        assertEquals("WAIT_CHAIN_RETURN", stateName(arm));
        assertEquals(-64, getInt(arm, "angle"));
        assertFalse(arm.controlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE));
        assertFalse(boss.rootBit(FbzMinibossInstance.ROOT_ARM_RETURNED));
        terminal.update(1, null);
        assertEquals("RECYCLE_OR_RESET", stateName(terminal));
        for (int frame = 2; frame < 54; frame++) {
            arm.update(frame, null);
            assertEquals("WAIT_CHAIN_RETURN", stateName(arm));
            terminal.update(frame, null);
        }
        assertEquals(-96, getInt(terminal, "angle"));
        assertTrue(arm.controlBit(FbzMinibossArmChild.ARM_PATROL_READY));
        arm.update(54, null);
        assertEquals("PATROL", stateName(arm));
    }

    private static Object[] states(Object object) {
        return Arrays.stream(object.getClass().getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("State"))
                .findFirst().orElseThrow().getEnumConstants();
    }

    private static void setState(Object object, String name) throws Exception {
        Object[] values = states(object);
        for (int index = 0; index < values.length; index++) {
            if (values[index].toString().equals(name)) {
                setInt(object, "stateOrdinal", index);
                return;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static String stateName(Object object) throws Exception {
        return states(object)[getInt(object, "stateOrdinal")].toString();
    }

    private static void setInt(Object object, String name, int value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(object, value);
    }

    private static int getInt(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(object);
    }

    @Test
    void endingPoseRetainsExistingPlungerSupportButCannotCreateANewContact() throws Exception {
        com.openggf.tests.TestEnvironment.configureGameModuleFixture(
                com.openggf.tests.rules.SonicGame.SONIC_3K);
        try {
            var camera = new com.openggf.camera.Camera();
            camera.setX((short) 0x2E20);
            camera.setY((short) 0x0540);
            var holder = new com.openggf.level.objects.ObjectManager[1];
            var player = new PlungerContactPlayer();
            var query = new ObjectPlayerQuery(() -> player, List::of);
            var services = new TestObjectServices() {
                @Override public com.openggf.level.objects.ObjectManager objectManager() { return holder[0]; }
                @Override public com.openggf.camera.Camera camera() { return camera; }
                @Override public ObjectPlayerQuery playerQuery() { return query; }
            };
            var manager = new com.openggf.level.objects.ObjectManager(List.of(), new Sonic3kObjectRegistry(),
                    0, null, null, com.openggf.graphics.GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.reset(0x2E20);
            var boss = boss(services);
            var defeated = FbzMinibossInstance.class.getDeclaredField("defeated");
            defeated.setAccessible(true);
            defeated.setBoolean(boss, true);
            var plunger = manager.createDynamicObject(() -> new FbzMinibossPlungerChild(boss));
            player.setCentreX((short) 0x2E30);
            player.setCentreY((short) 0x0560);
            manager.update(0x2E20, player, List.of(), 0, false, true, false);

            player.setCentreX((short) plunger.getX());
            player.setCentreY((short) (plunger.getY() - 8 - 19 + 1));
            player.setAir(true);
            player.setYSpeed((short) 0x100);
            manager.update(0x2E20, player, List.of(), 1, false, true, false);
            assertTrue(manager.isRidingObject(player, plunger));
            assertTrue(manager.hasObjectStandingBit(player, plunger));
            assertFalse(player.getAir());
            int standingY = player.getCentreY();

            S3kSignpostInstance.applyMainPlayerEndingPose(player);
            manager.update(0x2E20, player, List.of(), 2, false, true, false);
            assertTrue(manager.isRidingObject(player, plunger), "the native standing branch precedes bit-7 rejection");
            assertTrue(manager.hasObjectStandingBit(player, plunger));
            assertTrue(player.isOnObject());
            assertFalse(player.getAir());
            assertEquals(standingY, player.getCentreY());

            var newcomer = new PlungerContactPlayer();
            newcomer.setCentreX((short) plunger.getX());
            newcomer.setCentreY((short) (plunger.getY() - 8 - 19 + 1));
            newcomer.setAir(true);
            S3kSignpostInstance.applyMainPlayerEndingPose(newcomer);
            manager.update(0x2E20, newcomer, List.of(), 3, false, true, false);
            assertFalse(manager.isRidingObject(newcomer, plunger));
            assertFalse(manager.hasObjectStandingBit(newcomer, plunger));
            assertTrue(newcomer.getAir(), "a bit-7 player cannot acquire new plunger support");
        } finally {
            com.openggf.game.session.SessionManager.clear();
            com.openggf.game.GameModuleRegistry.reset();
            com.openggf.level.objects.AbstractObjectInstance.resetCameraBoundsForTests();
        }
    }

    @ParameterizedTest
    @CsvSource({"-32,true,false", "27,true,false", "31,true,false", "32,false,false",
            "-32,true,true", "27,true,true", "31,true,true", "32,false,true"})
    void bodyLandingReadsNativeWidthRatherThanCollisionPadding(int offsetX, boolean lands, boolean prison) {
        com.openggf.tests.TestEnvironment.configureGameModuleFixture(
                com.openggf.tests.rules.SonicGame.SONIC_3K);
        try {
            var camera = new com.openggf.camera.Camera();
            camera.setX((short) 0x2E20);
            camera.setY((short) 0x0540);
            com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(
                    0x2E20, 0x0540, 0x2F60, 0x0620, 0);
            var player = new PlungerContactPlayer();
            var services = new TestObjectServices().withCamera(camera);
            var manager = new com.openggf.level.objects.ObjectManager(List.of(), new Sonic3kObjectRegistry(),
                    0, null, null, com.openggf.graphics.GraphicsManager.getInstance(), camera, services);
            manager.reset(0x2E20);
            com.openggf.level.objects.AbstractObjectInstance body = prison
                    ? manager.createDynamicObject(() -> new FbzMinibossPrisonChild(boss(services)))
                    : manager.createDynamicObject(() -> boss(services));
            // Exercise an established boss slot after its first render cycle,
            // as the ordinary ObjectManager frame-start snapshot does.
            body.snapshotPreUpdatePosition();
            player.setRolling(true);
            player.applyRollingRadii(false);
            player.setCentreX((short) (body.getX() + offsetX));
            player.setCentreY((short) (body.getY() - 0x20 - player.getYRadius() - 4));
            player.setAir(true);
            player.setYSpeed((short) 0x338);

            manager.processImmediateInlineSolidCheckpoint(body, player, List.of());

            assertEquals(lands, manager.isRidingObject(player, body));
            assertEquals(lands, manager.hasObjectStandingBit(player, body));
            assertEquals(!lands, player.getAir());
            assertEquals(0x20, body.getBalanceWidthPixels());
            assertEquals(0x0006, ((com.openggf.level.objects.RomObjectCodePointerProvider) body).romObjectCodePointerHighWord());
            if (lands) {
                assertFalse(player.getRolling());
                assertEquals(0, player.getYSpeed());
                // A later CPU jump consumes an established standing bit, not
                // the same-frame landing preservation path.
                manager.updateSolidContacts(player);
                player.setRolling(true);
                player.applyRollingRadii(false);
                player.setCentreX((short) (body.getX() - 0x1B));
                player.setCentreY((short) (body.getY() - 0x20 - player.getYRadius() + 5));
                int jumpX = player.getCentreX();
                int jumpY = player.getCentreY();
                manager.clearRidingObjectForJump(player);
                player.setAir(true);
                player.setOnObject(false);
                player.setXSpeed((short) 0xC);
                player.setYSpeed((short) -0x680);
                manager.processImmediateInlineSolidCheckpoint(body, player, List.of());
                assertEquals(jumpX, player.getCentreX(), "the stale standing bit prevents a fresh side push");
                assertEquals(jumpY, player.getCentreY());
                assertEquals(0xC, player.getXSpeed());
                assertEquals(-0x680, player.getYSpeed());
                assertFalse(manager.hasObjectStandingBit(player, body));
            }
        } finally {
            com.openggf.game.session.SessionManager.clear();
            com.openggf.game.GameModuleRegistry.reset();
            com.openggf.level.objects.AbstractObjectInstance.resetCameraBoundsForTests();
        }
    }

    private static final class PlungerContactPlayer extends com.openggf.sprites.playable.Sonic {
        private PlungerContactPlayer() {
            super("FBZ_PLUNGER_CONTACT", (short) 0, (short) 0);
            setGameRulesForTest(com.openggf.game.rules.GameRules.SONIC_3K);
            setWidth(20);
            setHeight(38);
        }
    }

    @Test
    void initialTableHasSevenExactStableRolesAndTwoIndependentFiveLinkArms() {
        assertArrayEquals(new String[] {
                "cover-left", "cover-right", "cover-centre", "plunger", "aimer", "arm-left", "arm-right"
        }, FbzMinibossInstance.initialRoleNames());
        assertEquals(18, FbzMinibossInstance.fullPersistentGraphSlots());
        assertEquals(19, FbzMinibossInstance.peakGraphSlots());
        assertEquals(5, FbzMinibossArmChild.LINK_COUNT);
    }

    @Test
    void plungerGivesAllPlayersSolidContactButOnlyNativeP1PublishesStartBit() {
        PlayableEntity p1 = mock(PlayableEntity.class);
        PlayableEntity p2 = mock(PlayableEntity.class);
        PlayableEntity extra = mock(PlayableEntity.class);
        QueryServices services = new QueryServices(p1, List.of(p2, extra));
        FbzMinibossInstance boss = boss(services);
        FbzMinibossPlungerChild plunger = new FbzMinibossPlungerChild(boss);
        plunger.setServices(services);

        plunger.onStandingContact(p2, true);
        plunger.onStandingContact(extra, true);
        plunger.update(0, p1);
        assertFalse(boss.isPlungerStarted());
        plunger.onStandingContact(p1, true);
        plunger.update(1, p1);
        assertTrue(boss.isPlungerStarted());
        plunger.onStandingContact(p1, false);
        plunger.update(2, p1);
        assertFalse(boss.isPlungerStarted(), "P1's cleared standing bit is visible immediately");
    }

    @Test
    void bodyAndPlungerUseS3kSolidObjectFullInclusiveFreshContactEdge() {
        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossPlungerChild plunger = new FbzMinibossPlungerChild(boss);

        assertTrue(boss.usesInclusiveRightEdge(),
                "sub_6F786 calls SolidObjectFull, whose SolidObject_cont cmp/bhi accepts relX == width*2");
        assertTrue(plunger.usesInclusiveRightEdge(),
                "sub_6F796 calls SolidObjectFull, whose SolidObject_cont cmp/bhi accepts relX == width*2");
    }

    @Test
    void aimerUsesClosestNativePairWithP1TieAndLungeAlwaysCapturesP1() {
        PlayableEntity p1 = mock(PlayableEntity.class);
        PlayableEntity p2 = mock(PlayableEntity.class);
        PlayableEntity extra = mock(PlayableEntity.class);
        when(p1.getCentreX()).thenReturn((short) 0x2E00);
        when(p2.getCentreX()).thenReturn((short) 0x2E20);
        when(extra.getCentreX()).thenReturn((short) 0x2E10);
        QueryServices services = new QueryServices(p1, List.of(p2, extra));
        FbzMinibossAimerChild aimer = new FbzMinibossAimerChild(boss(services));
        aimer.setServices(services);
        assertSame(p1, aimer.closestNativePlayer(0x2E10));
        assertSame(p1, aimer.captureOutwardLungeTarget());
    }

    @Test
    void interpolationUsesFiveEqualRomSegmentsAndTerminalClosesCycle() {
        assertArrayEquals(new int[] {20, 40, 60, 80, 100},
                FbzMinibossChainLink.interpolateFive(0, 100));
        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossArmChild arm = FbzMinibossArmChild.forTest(boss, 0);
        FbzMinibossChainLink[] links = arm.createLinksForTest();
        assertEquals(5, links.length);
        assertSame(arm, links[0].previous());
        for (int i = 1; i < links.length; i++) assertSame(links[i - 1], links[i].previous());
        assertSame(links[0], arm.next());
        assertSame(arm, links[4].next());
    }

    @Test
    void coverAndAimerActivationAreSetupOnlyAndUseExactWaitCounts() {
        assertArrayEquals(new int[] {33, 33, 65}, FbzMinibossCoverChild.waitUpdates());
        assertEquals(65, FbzMinibossAimerChild.activationWaitUpdates());

        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossCoverChild cover = new FbzMinibossCoverChild(boss, 0, -0x10, -8);
        int startX = cover.getX();
        boss.activateFromNativeP1Plunger();
        cover.update(0, null);
        for (int frame = 1; frame <= 32; frame++) cover.update(frame, null);
        assertEquals(startX - 8, cover.getX());
        cover.update(33, null);
        assertEquals(startX - 9, cover.getX(), "MoveSprite2 runs before the 33rd --timer expiry");
        cover.update(34, null);
        assertEquals(startX - 9, cover.getX());
    }

    @Test
    void renderPrioritiesChangeAtTheExactChainDeploymentCallback() {
        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossCoverChild cover = new FbzMinibossCoverChild(boss, 0, -0x10, -8);
        FbzMinibossPlungerChild plunger = new FbzMinibossPlungerChild(boss);
        FbzMinibossAimerChild aimer = new FbzMinibossAimerChild(boss);
        FbzMinibossArmChild arm = FbzMinibossArmChild.forTest(boss, 0);
        FbzMinibossChainLink[] links = arm.createLinksForTest();

        assertEquals(2, cover.getPriorityBucket());
        assertEquals(5, plunger.getPriorityBucket());
        assertEquals(2, aimer.getPriorityBucket());
        assertEquals(6, arm.getPriorityBucket());
        assertEquals(5, links[0].getPriorityBucket());
        assertEquals(4, links[4].getPriorityBucket());

        for (FbzMinibossChainLink link : links) link.update(0, null);
        arm.setControlBit(FbzMinibossArmChild.ARM_TERMINAL_EDGE);
        for (FbzMinibossChainLink link : links) link.update(1, null);
        for (int frame = 0; frame < 16; frame++) {
            for (FbzMinibossChainLink link : links) link.update(frame + 2, null);
        }
        assertEquals(3, links[0].getPriorityBucket());
        assertEquals(1, links[4].getPriorityBucket());
    }

    @Test
    void coverAndAimerDeleteOnTheDefeatReleaseConversionPass() {
        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossCoverChild cover = new FbzMinibossCoverChild(boss, 0, -0x10, -8);
        FbzMinibossAimerChild aimer = new FbzMinibossAimerChild(boss);

        boss.setRootBit(FbzMinibossInstance.ROOT_DEFEAT_RELEASE);
        cover.update(0, null);
        aimer.update(0, null);

        assertTrue(cover.isDestroyed(), "Child_Draw_Sprite2 deletes the cover in the conversion pass");
        assertTrue(aimer.isDestroyed(), "Child_Draw_Sprite2 deletes the aimer in the conversion pass");
    }

    @Test
    void rootAndPlungerReuseSolidParamsAndHotStateSwitchesDoNotAllocateEnumArrays() throws IOException {
        FbzMinibossInstance boss = boss(new QueryServices(null, List.of()));
        FbzMinibossPlungerChild plunger = new FbzMinibossPlungerChild(boss);
        assertSame(boss.getSolidParams(), boss.getSolidParams());
        assertSame(plunger.getSolidParams(), plunger.getSolidParams());

        Path packageDir = Path.of("src/main/java/com/openggf/game/sonic3k/objects");
        for (String source : List.of("FbzMinibossInstance.java", "FbzMinibossArmChild.java",
                "FbzMinibossChainLink.java", "FbzMinibossAimerChild.java")) {
            String text = Files.readString(packageDir.resolve(source));
            assertFalse(text.contains(".values()["), source + " must not allocate enum arrays in update/render");
        }
    }

    private static FbzMinibossInstance boss(TestObjectServices services) {
        FbzMinibossInstance boss = new FbzMinibossInstance(new ObjectSpawn(0x2F00, 0x5E0, 0xAA, 0, 0, true, 3));
        boss.setServices(services);
        return boss;
    }

    private static final class QueryServices extends TestObjectServices {
        private final ObjectPlayerQuery query;
        QueryServices(PlayableEntity p1, List<? extends PlayableEntity> sidekicks) {
            query = new ObjectPlayerQuery(() -> p1, () -> sidekicks);
        }
        @Override public ObjectPlayerQuery playerQuery() { return query; }
    }
}

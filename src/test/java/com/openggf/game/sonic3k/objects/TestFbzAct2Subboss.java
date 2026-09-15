package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Locked-on oracle for Obj_FBZ2Subboss (sonic3k.asm:148033-148695). */
class TestFbzAct2Subboss {
    @Test
    void visibleChildrenRetainNativeArtPriorityIndependentlyOfSatBucket() {
        var root = boss();
        var children = java.util.List.of(
                new Fbz2SubbossCornerChild(root, 0),
                new Fbz2SubbossCornerChild(root, 2),
                new Fbz2SubbossSolidSideChild(root, 0),
                new Fbz2SubbossLaserChild(root),
                new Fbz2SubbossMachineChild(root),
                new Fbz2SubbossCharacterChild(root, com.openggf.game.PlayerCharacter.SONIC_AND_TAILS),
                new Fbz2SubbossCharacterChild(root, com.openggf.game.PlayerCharacter.KNUCKLES));
        for (var child : children) {
            assertTrue(child.isHighPriority(), child.getClass().getSimpleName()
                    + " must draw above high-priority terrain, as art_tile bit 15 requires");
            assertTrue(child.usesCustomOutOfRangeCheck(), "attached children own their native lifetime");
            assertFalse(child.isCustomOutOfRange(0x2900), "room displays must survive the camera approach");
        }
        assertEquals(1, children.getFirst().getPriorityBucket());
        assertEquals(5, children.get(4).getPriorityBucket());
    }

    @Test void defeatOwnsBothPhysicalArchivesUntilReadyAndRestoresTheirPendingClaims() throws Exception {
        var queue = mock(com.openggf.game.sonic3k.resources.S3kKosModuleQueue.class);
        var coordinator = mock(com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.class);
        var timing = mock(com.openggf.game.timing.HardwareTimingService.class);
        var journal = mock(com.openggf.level.resources.KosinskiModuleQueue.class);
        var rom = mock(com.openggf.data.Rom.class);
        var kind = com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE;
        var cloud = new com.openggf.game.timing.HardwareWorkHandle(kind, 7, "cloud-fingerprint");
        var pillar = new com.openggf.game.timing.HardwareWorkHandle(kind, 8, "pillar-fingerprint");
        when(coordinator.moduleQueue()).thenReturn(queue);
        when(rom.readBytes(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq(32)))
                .thenReturn(new byte[32]);
        var entries = com.openggf.game.sonic3k.Sonic3kPlcLoader.fbz2SubbossDefeatKosmEntries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var handle = i == 0 ? cloud : pillar;
            when(journal.enqueue(rom, entry.sourceAddress(), entry.destinationVramBytes())).thenReturn(true);
            when(queue.queue(rom, entry.sourceAddress(), entry.destinationVramBytes() / 32)).thenReturn(handle);
            when(timing.pendingHandle(kind, handle.ordinal())).thenReturn(java.util.Optional.of(handle));
        }
        var boss = boss();
        boss.setServices(new com.openggf.level.objects.TestObjectServices() {
            @Override public com.openggf.data.Rom rom() { return rom; }
            @Override public com.openggf.level.resources.KosinskiModuleQueue kosinskiModuleQueue() { return journal; }
            @Override public com.openggf.game.RuntimeArtCoordinator runtimeArtCoordinator() { return coordinator; }
            @Override public com.openggf.game.timing.HardwareTimingService hardwareTiming() { return timing; }
        });
        boss.update(0, null);
        for (int i = 0; i < 7; i++) boss.completeLaserCycleForTest();
        var ordered = org.mockito.Mockito.inOrder(queue);
        for (var entry : entries) ordered.verify(queue).queue(rom, entry.sourceAddress(), entry.destinationVramBytes() / 32);
        var pending = boss.captureRewindState();
        boss.update(1, null);
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.never()).claim(org.mockito.ArgumentMatchers.any());
        when(queue.isReady(cloud)).thenReturn(true);
        boss.update(2, null);
        org.mockito.Mockito.verify(queue).claim(cloud);
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.never()).claim(pillar);
        when(queue.isReady(pillar)).thenReturn(true);
        boss.update(3, null);
        boss.update(4, null);
        org.mockito.Mockito.verify(queue).claim(pillar);
        boss.restoreRewindState(pending); // the timing owner restores the same pending handles independently
        boss.update(5, null);
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.times(2)).claim(cloud);
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.times(2)).claim(pillar);
        for (var entry : entries) org.mockito.Mockito.verify(queue).queue(rom, entry.sourceAddress(), entry.destinationVramBytes() / 32);
    }

    @Test
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void activationPublishesEasedDeathPlaneToTheNextSidekickSlot() {
        var fixture = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(4, 1).build();
        var camera = com.openggf.game.GameServices.camera();
        camera.setX((short) 0x2900);
        camera.setY((short) 0x5F0);
        camera.setMaxY((short) 0xB00);
        camera.setMaxYTarget((short) 0xB00);
        var tails = new com.openggf.sprites.playable.Tails("tails_bounds", (short) 0, (short) 0);
        tails.setCpuControlled(true);
        var cpu = new com.openggf.sprites.playable.SidekickCpuController(tails, fixture.sprite());
        tails.setCpuController(cpu);
        cpu.setLevelBounds(0, 0x3000, 0xB00);
        com.openggf.game.GameServices.sprites().addSprite(tails, "tails");
        var manager = com.openggf.game.GameServices.level().getObjectManager();
        var boss = manager.createDynamicObject(TestFbzAct2Subboss::boss);

        boss.update(0, fixture.sprite());
        assertEquals("WAIT_P1", boss.phaseName());
        assertEquals(0xB00, cpu.getMaxYBound(-1), "target publication waits for actual camera easing");
        var runtime = com.openggf.game.GameServices.zoneRuntimeRegistryOrNull()
                .currentAs(com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState.class).orElseThrow();
        byte[] pendingPublication = runtime.captureBytes();
        camera.updateBoundaryEasing();
        var events = com.openggf.game.GameServices.module().getLevelEventProvider();
        events.updateAfterCameraBoundaryEasing();

        assertEquals(0x5EE, camera.getMaxY());
        assertEquals(camera.getMaxY(), cpu.getMaxYBound(-1),
                "next Tails_Check_Screen_Boundaries must observe the live eased Camera_max_Y_pos");
        cpu.setLevelBounds(0, 0x3000, 0xB00);
        events.updateAfterCameraBoundaryEasing();
        assertEquals(0xB00, cpu.getMaxYBound(-1), "publication request is consumed exactly once");
        runtime.restoreBytes(pendingPublication);
        events.updateAfterCameraBoundaryEasing();
        assertEquals(camera.getMaxY(), cpu.getMaxYBound(-1),
                "rewinding the pending event republishes the live boundary at the same phase");
    }

    @Test void nativeBoundsTriggerAndSevenCycleCounterAreExact() {
        assertArrayEquals(new int[] {0x560, 0x660, 0x2900, 0x2C00},
                Fbz2SubbossInstance.activationBounds());
        assertEquals(0x18, Fbz2SubbossInstance.triggerDistanceExclusive());
        assertTrue(Fbz2SubbossInstance.cameraInActivationRange(0x2900,0x560));
        assertTrue(Fbz2SubbossInstance.cameraInActivationRange(0x2C00,0x660));
        assertFalse(Fbz2SubbossInstance.cameraInActivationRange(0x28FF,0x560));
        assertFalse(Fbz2SubbossInstance.cameraInActivationRange(0x2C01,0x660));
        Fbz2SubbossInstance boss = boss();
        assertEquals(7, boss.cyclesRemaining());
        for (int i = 0; i < 6; i++) {
            boss.completeLaserCycleForTest();
            assertFalse(boss.isDefeated());
        }
        boss.completeLaserCycleForTest();
        assertTrue(boss.isDefeated());
    }

    @Test void nativeRootDeleteUsesFixedCoarseBackWindowIndependentOfViewportWidth() {
        int cameraX=0x2B00;
        int coarseBack=(cameraX-0x80)&0xFF80;
        assertTrue(Fbz2SubbossInstance.nativeSpriteCheckDeleteXKeepsAlive(coarseBack+0x280,cameraX));
        assertFalse(Fbz2SubbossInstance.nativeSpriteCheckDeleteXKeepsAlive(coarseBack+0x300,cameraX));
        assertFalse(Fbz2SubbossInstance.nativeSpriteCheckDeleteXKeepsAlive(coarseBack-0x80,cameraX),
                "unsigned wrap deletes objects behind the camera");
    }

    @Test void dropChargeAndDefeatWaitWordsUseNativeUnderflowDurations() {
        assertEquals(56, Fbz2SubbossInstance.dropUpdates());
        assertEquals(64, Fbz2SubbossInstance.preLaserWaitUpdates());
        assertEquals(128, Fbz2SubbossInstance.cycleWaitUpdates());
        assertEquals(96, Fbz2SubbossInstance.defeatWaitUpdates());

        Fbz2SubbossInstance boss = boss();
        PlayableEntity p1 = mock(PlayableEntity.class);
        when(p1.getCentreX()).thenReturn((short) 0x2B40);
        boss.update(-1, p1);
        boss.update(0, p1);
        assertEquals("DROP", boss.phaseName());
        for (int i = 0; i < 55; i++) boss.update(i + 1, p1);
        assertEquals(0x5F0 + 27, boss.getY());
        boss.update(56, p1);
        assertEquals(0x5F0 + 28, boss.getY());
        assertEquals("PRE_LASER_WAIT", boss.phaseName());
        for (int i = 0; i < 63; i++) boss.update(57 + i, p1);
        assertEquals("PRE_LASER_WAIT", boss.phaseName());
        boss.update(120, p1);
        assertEquals("ACTIVE", boss.phaseName());
    }

    @Test void laserReadyStopsMovementAndRetainsItsBitUntilNextCycleStarts() {
        Fbz2SubbossInstance boss = boss();
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) 0x2B40);
        boss.update(-1, player);
        boss.update(0, player);
        for (int i = 1; i <= 120; i++) boss.update(i, player);
        assertEquals("ACTIVE", boss.phaseName());
        boss.update(121, player);
        int x = boss.getX();
        boss.setControlBit(Fbz2SubbossInstance.CONTROL_LASER_READY);
        boss.update(0x40, player);
        assertAll(
                () -> assertEquals(x, boss.getX(), "loc_6FEFA returns without MoveSprite2"),
                () -> assertTrue(boss.controlBit(Fbz2SubbossInstance.CONTROL_LASER_READY),
                        "loc_6FE3A owns the clear at the next cycle"));
        assertEquals("CYCLE_WAIT", boss.phaseName());
        assertEquals(0x7F, boss.waitWordForTest());
        for (int i = 0; i < 128; i++) boss.update(0x41 + i, player);
        assertEquals("ACTIVE", boss.phaseName());
        assertFalse(boss.controlBit(Fbz2SubbossInstance.CONTROL_LASER_READY));
        assertEquals(x, boss.getX(), "the cycle-start callback creates the laser without moving");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {-1, 0, 1})
    void nextLaserCycleAimsAtCurrentPlayerBeforePeriodicAim(int side) {
        Fbz2SubbossInstance boss = boss();
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) (boss.getX() + (side > 0 ? -1 : 1)));
        boss.update(-1, player);
        boss.update(0, player);
        for (int i = 1; i <= 120; i++) boss.update(i, player);
        assertEquals("ACTIVE", boss.phaseName());
        boss.setControlBit(Fbz2SubbossInstance.CONTROL_LASER_READY);
        boss.update(121, player);
        int x = boss.getX();
        when(player.getCentreX()).thenReturn((short) (x + side));
        for (int i = 0; i < 128; i++) boss.update(122 + i, player);
        assertEquals("ACTIVE", boss.phaseName());
        assertEquals(x, boss.getX(), "loc_6FE3A aims without moving");
        boss.update(250, player); // not the periodic VInt-low-five-bits aim
        assertEquals(x + (side > 0 ? 1 : -1), boss.getX(),
                "loc_6FE28 falls through sub_6FE54 and uses current P1, including equality");
    }

    @Test void onlySixNonfinalCyclesMoveTheLeftAnchors() {
        Fbz2SubbossInstance boss = boss();
        Fbz2SubbossCornerChild upperLeft = Fbz2SubbossCornerChild.forTest(boss, 0);
        Fbz2SubbossCornerChild upperRight = Fbz2SubbossCornerChild.forTest(boss, 2);
        int leftStartX = upperLeft.getX();
        int startX = upperRight.getX();
        for (int cycle = 0; cycle < 6; cycle++) {
            boss.completeLaserCycleForTest();
            upperLeft.update(-1, null);
            upperRight.update(-1, null);
            assertEquals(leftStartX + cycle * 32, upperLeft.getX(),
                    "loc_6FF70 installs movement without calling MoveSprite2");
            for (int frame = 0; frame < 32; frame++) upperLeft.update(frame, null);
            for (int frame = 0; frame < 32; frame++) upperRight.update(frame, null);
        }
        assertEquals(leftStartX + 6 * 32, upperLeft.getX());
        assertEquals(startX, upperRight.getX());
        boss.completeLaserCycleForTest();
        for (int frame = 0; frame < 32; frame++) upperLeft.update(frame, null);
        for (int frame = 0; frame < 32; frame++) upperRight.update(frame, null);
        assertEquals(leftStartX + 6 * 32, upperLeft.getX(), "defeat does not publish bit 3");
        assertEquals(startX, upperRight.getX());
    }

    @Test void movingCornerInstallsThenExecutesExactlyThirtyTwoMovementPasses() {
        Fbz2SubbossInstance boss = boss();
        Fbz2SubbossCornerChild left = Fbz2SubbossCornerChild.forTest(boss, 0);
        int startX = left.getX();
        boss.completeLaserCycleForTest();

        left.update(0, null);
        assertEquals(startX, left.getX());
        assertTrue(boss.controlBit(Fbz2SubbossInstance.CONTROL_MOVE_RIGHT));
        for (int pass = 1; pass <= 31; pass++) {
            left.update(pass, null);
            assertEquals(startX + pass, left.getX());
            assertTrue(boss.controlBit(Fbz2SubbossInstance.CONTROL_MOVE_RIGHT));
        }
        left.update(32, null);
        assertEquals(startX + 32, left.getX());
        assertFalse(boss.controlBit(Fbz2SubbossInstance.CONTROL_MOVE_RIGHT));
    }

    @Test void ordinaryAttacksStartFlashButNeverConsumeALaserCycle() {
        Fbz2SubbossInstance boss = boss();
        PlayableEntity player = mock(PlayableEntity.class);
        boss.update(-1, player); // native setup callback is setup-only
        boss.onPlayerAttack(player);
        assertEquals(7, boss.cyclesRemaining());
        assertEquals(0, boss.getCollisionFlags());
        boss.update(0, player);
        assertEquals(31, boss.hitFlashUpdatesRemaining());
        assertEquals(0x7E, boss.getCollisionProperty());
        assertEquals(7, boss.cyclesRemaining());
    }

    @Test void machinePilotAndMaskRemainAtTheirInitializationCoordinatesWhenRootMoves() {
        Fbz2SubbossInstance root = boss();
        Fbz2SubbossMachineChild machine = new Fbz2SubbossMachineChild(root);
        Fbz2SubbossCharacterChild pilot = new Fbz2SubbossCharacterChild(root,
                com.openggf.game.PlayerCharacter.SONIC_ALONE);
        Fbz2SubbossSpriteMaskChild mask = new Fbz2SubbossSpriteMaskChild(root);
        int machineX = machine.getX(), machineY = machine.getY();
        int pilotX = pilot.getX(), pilotY = pilot.getY();
        int maskX = mask.getX(), maskY = mask.getY();

        root.offsetNativePositionWordsPreserveSubpixel(0x40, 0x20);
        machine.update(1, null);
        pilot.update(1, null);
        mask.update(1, null);

        assertEquals(machineX, machine.getX());
        assertEquals(machineY, machine.getY());
        assertEquals(pilotX, pilot.getX());
        assertEquals(pilotY, pilot.getY());
        assertEquals(maskX, mask.getX());
        assertEquals(maskY, mask.getY());
    }

    @Test void nativeInitialChildTableUsesStableRolesAndSubtypes() {
        assertArrayEquals(new int[] {0, 2, 4, 6}, Fbz2SubbossCornerChild.nativeSubtypes());
        assertArrayEquals(new int[] {0, 2}, Fbz2SubbossSolidSideChild.nativeSubtypes());
        assertEquals(0x49, Fbz2SubbossSpriteMaskChild.nativeSubtype());
        assertEquals(4, Fbz2SubbossSpriteMaskChild.mappingFrame());
        assertEquals(0x80, Fbz2SubbossSpriteMaskChild.nativePriority());
        assertEquals(0xAC, Fbz2SubbossLaserChild.activeCollisionFlags());
    }

    @Test void laserRawScriptsMatchEveryNativeCallbackAndCollisionPass() {
        Fbz2SubbossInstance root = boss();
        Fbz2SubbossLaserChild laser = new Fbz2SubbossLaserChild(root);

        laser.update(0, null);
        assertEquals(5, laser.frameForTest(), "loc_70192 is a setup-only dispatch");
        laser.update(1, null);
        assertEquals(0xA, laser.frameForTest());
        for (int call = 2; call <= 206; call++) laser.update(call, null);
        assertFalse(root.controlBit(Fbz2SubbossInstance.CONTROL_LASER_READY));
        laser.update(207, null);
        assertTrue(root.controlBit(Fbz2SubbossInstance.CONTROL_LASER_READY),
                "zero-delay wrap $20 publishes root $38 bit 1");
        for (int call = 208; call <= 270; call++) laser.update(call, null);
        assertEquals("CHARGE", laser.phaseNameForTest());
        laser.update(271, null);
        assertEquals("BEAM", laser.phaseNameForTest());
        assertEquals(6, laser.frameForTest());
        assertEquals(root.getY() + 8 + 0x3C, laser.getY());

        int collisionPasses = 0;
        for (int beam = 1; beam <= 64; beam++) {
            laser.update(271 + beam, null);
            if (laser.getCollisionFlags() == 0xAC) collisionPasses++;
            assertEquals(beam >= 6 && (beam & 1) == 0 ? 0xAC : 0,
                    laser.getCollisionFlags(), "collision is sampled before the raw frame advances");
        }
        assertEquals(30, collisionPasses);
        assertEquals("RETRACT", laser.phaseNameForTest());
        laser.update(336, null);
        assertEquals(7, laser.frameForTest());
        assertEquals("WAIT_DELETE", laser.phaseNameForTest(),
                "the already-negative Obj_Wait counter truncates byte_70412 after one frame");
        for (int wait = 1; wait < 32; wait++) laser.update(336 + wait, null);
        assertFalse(laser.isDestroyed());
        laser.update(368, null);
        assertFalse(laser.isDestroyed(), "Go_Delete_Sprite schedules deletion for the following object pass");
        laser.update(369, null);
        assertTrue(laser.isDestroyed());
    }

    private static Fbz2SubbossInstance boss() {
        return new Fbz2SubbossInstance(new ObjectSpawn(0x2B40, 0x5F0, 0xAB, 0, 0, true, 417));
    }
}

package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TestCnzCylinderInstance {

    @Test
    void firstUpdateContinuesAfterRomInitFallthroughMotionPass() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnWithSubtype(0xF1));
        cylinder.setServices(new TestObjectServices());

        cylinder.update(1, null);

        int expected = 0x1BDF + (TrigLookupTable.sinHex(0x03) >> 3);
        assertEquals(expected, cylinder.getX());
    }

    @Test
    void standingContactCaptureRestoresDefaultRadiiAndClearsRolling() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawn());
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1BC6);
        player.setCentreY((short) 0x07AC);
        player.setRolling(true);
        player.applyRollingRadii(false);
        int defaultYRadius = player.getStandYRadius();

        cylinder.onSolidContact(player, new SolidContact(true, false, false, true, false), 4310);
        cylinder.update(4311, player);

        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertFalse(player.isTouchResponseSuppressedByObjectControl());
        assertFalse(player.isControlLocked());
        assertFalse(player.getRolling());
        assertFalse(player.getAir());
        assertEquals(9, player.getXRadius());
        assertEquals(defaultYRadius, player.getYRadius());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
    }

    @Test
    void standingContactCapturesImmediatelyAfterRecentObjectControlRelease() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(
                spawnAtWithSubtype(0x147E, 0x0AE0, 0x42));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1495);
        player.setCentreY((short) 0x0AAC);
        player.setXSpeed((short) -0x0400);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) -0x0400);
        player.setAir(false);
        player.releaseFromObjectControl(18154);

        cylinder.onSolidContact(player, new SolidContact(true, false, false, true, false), 18154);
        cylinder.update(18155, player);

        assertTrue(player.isObjectControlled(),
                "CNZ f18155: sub_324C0 captures from the standing bit without "
                        + "an engine recapture cooldown (sonic3k.asm:67985-68005)");
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
    }

    @Test
    void captureDoesNotLatchStaleLogicalInputWhileHeld() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawn());
        cylinder.setServices(new TestObjectServices());
        FeatureSetTestPlayableSprite player = new FeatureSetTestPlayableSprite();
        player.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);
        player.setCentreX((short) 0x1BC6);
        player.setCentreY((short) 0x07AC);
        player.setLogicalInputState(false, false, false, true, false);

        cylinder.onSolidContact(player, new SolidContact(true, false, false, true, false), 4310);
        cylinder.update(4311, player);
        player.setLogicalInputState(false, false, false, false, false);
        player.recordFollowerHistoryForTick();

        assertFalse(player.isControlLocked());
        assertEquals(0, player.getInputHistory(0) & 0xFFFF);
    }

    @Test
    void jumpReleaseAppliesRomHoldPositionBeforeLaunch() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawn());
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1BC6);
        player.setCentreY((short) 0x07AC);

        cylinder.onSolidContact(player, new SolidContact(true, false, false, true, false), 4310);
        cylinder.update(4311, player);
        int preReleaseY = player.getCentreY();

        player.setJumpInputPressed(true);
        player.setLogicalInputState(false, false, false, false, true);
        cylinder.update(4312, player);

        int thresholdByte = ((TrigLookupTable.sinHex(0x80) + 0x100) >> 2) & 0xFF;
        int distanceWord = (25 << 8) | thresholdByte;
        int expectedOffset = (TrigLookupTable.cosHex(0x80) * distanceWord) >> 16;
        assertFalse(player.isObjectControlled());
        assertTrue(player.getAir());
        assertTrue(player.getRolling());
        assertEquals(0x1BDF + expectedOffset, player.getCentreX());
        assertEquals(preReleaseY, player.getCentreY());
        assertEquals(7, player.getXRadius());
        assertEquals(14, player.getYRadius());
        assertTrue(player.getYSpeed() < 0);
    }

    @Test
    void jumpReleaseKeepsHeldStandingYAndClearsObjectSupport() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x19D0, 0x0160, 0x42));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCpuControlled(true);
        player.setCentreX((short) (cylinder.getX() - 0x09));
        player.setCentreY((short) 0x0130);

        cylinder.onSolidContact(player, new SolidContact(true, true, false, true, false), 10965);
        cylinder.update(10966, player);
        int heldStandingRadius = player.getStandYRadius();
        player.applyRollingRadii(false);
        player.setOnObject(true);
        player.setLatchedSolidObject(0x47, cylinder);

        player.setJumpInputPressed(true);
        player.setLogicalInputState(false, false, false, false, true);
        cylinder.update(10967, player);

        assertEquals(cylinder.getY() - 0x21 - heldStandingRadius, player.getCentreY());
        assertFalse(player.isOnObject());
        assertEquals(0, player.getLatchedSolidObjectId());
        assertFalse(player.isObjectControlled());
        assertTrue(player.getAir());
        assertTrue(player.getRolling());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getGSpeed());
        assertTrue(player.getYSpeed() < 0);
        assertFalse(cylinder.isSolidFor(player));

        cylinder.snapshotPreUpdatePosition();

        assertTrue(cylinder.isSolidFor(player));
    }

    @Test
    void heldCpuSidekickDoesNotReleaseFromLiveJumpHeldWithoutCtrl2LogicalPress() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x17EA, 0x0B3C, 0x41));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCpuControlled(true);
        player.setCentreX((short) 0x17FB);
        player.setCentreY((short) 0x0B10);

        cylinder.onSolidContact(player, new SolidContact(true, true, false, true, false), 19295);
        cylinder.update(19295, player);

        player.setJumpInputPressed(true);
        player.setLogicalInputState(false, false, false, false, false, false);
        cylinder.update(19296, player);

        assertTrue(player.isObjectControlled(),
                "CNZ f19296: Obj_CNZCylinder passes Ctrl_2_logical in d5 to sub_324C0 "
                        + "and loc_325B6 tests only the low-byte A/B/C press bits "
                        + "(sonic3k.asm:67656-67672,68059-68064)");
        assertFalse(player.getAir());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertTrue(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
    }

    @Test
    void holdPreservesPlayerXSubpixelLikeWordXPosWrites() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawn());
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1BC6);
        player.setCentreY((short) 0x07AC);
        player.setSubpixelRaw(0x1200, 0x9200);

        cylinder.onSolidContact(player, new SolidContact(true, false, false, true, false), 4310);
        cylinder.update(4311, player);
        cylinder.update(4312, player);

        assertEquals(0x1200, player.getXSubpixelRaw());
    }

    @Test
    void holdUsesRomCombinedDistanceWordForXOffset() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawn());
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1BE5);
        player.setCentreY((short) 0x07AC);

        cylinder.onSolidContact(player, new SolidContact(true, false, false, true, false), 4310);
        cylinder.update(4311, player);
        for (int frame = 4312; frame <= 4376; frame++) {
            cylinder.update(frame, player);
        }

        int twistAngle = 0x80;
        int thresholdByte = ((TrigLookupTable.sinHex(twistAngle) + 0x100) >> 2) & 0xFF;
        int distanceWord = (6 << 8) | thresholdByte;
        int expectedOffset = (TrigLookupTable.cosHex(twistAngle) * distanceWord) >> 16;
        assertEquals(0x1BDF + expectedOffset, player.getCentreX());
    }

    @Test
    void horizontalFirstCaptureAndHeldStepUseFrameEntryAnchorForDeferredNonCpuSolidContact() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnWithSubtype(0x42));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        int frameEntryX = cylinder.getX();
        player.setCentreX((short) (frameEntryX - 0x22));
        player.setCentreY((short) 0x07AC);

        cylinder.snapshotPreUpdatePosition();
        cylinder.onSolidContact(player, new SolidContact(true, true, false, true, false), 4310);
        cylinder.update(4311, player);
        cylinder.update(4312, player);

        int twistAngle = 0x80;
        int thresholdByte = ((TrigLookupTable.sinHex(twistAngle) + 0x100) >> 2) & 0xFF;
        int distanceWord = (0x22 << 8) | thresholdByte;
        int expectedOffset = (TrigLookupTable.cosHex(twistAngle) * distanceWord) >> 16;
        assertEquals(cylinder.getPreUpdateX() + expectedOffset, player.getCentreX());
    }

    @Test
    void horizontalWidePositiveStepRightSideCaptureStoresFrameEntryDistanceForNonCpuRider() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x2060, 0x01A0, 0x52));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x2074);
        player.setCentreY((short) 0x016C);

        setCylinderCenter(cylinder, 0x206C, 0x01A0);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x206E, 0x01A0);
        Object slot = playerOneSlot(cylinder);

        invokeCaptureSlot(cylinder, slot, player, true);

        assertEquals(0x08, (int) getSlotField(slot, "horizontalDistance"));
        assertNotEquals(0x06, (int) getSlotField(slot, "horizontalDistance"));

        setCylinderCenter(cylinder, 0x206E, 0x01A0);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x206F, 0x01A0);
        invokeHoldSlot(cylinder, slot);

        assertEquals(0x2076, player.getCentreX());
        assertNotEquals(0x2074, player.getCentreX());
    }

    @Test
    void horizontalNarrowNegativeStepLeftSideCaptureStoresFrameEntryDistanceForNonCpuRider() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x2370, 0x0460, 0x41));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x2357);
        player.setCentreY((short) 0x042C);

        setCylinderCenter(cylinder, 0x236C, 0x0460);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x236B, 0x0460);
        Object slot = playerOneSlot(cylinder);

        invokeCaptureSlot(cylinder, slot, player, true);

        assertEquals(0x15, (int) getSlotField(slot, "horizontalDistance"));
        assertNotEquals(0x14, (int) getSlotField(slot, "horizontalDistance"));

        setCylinderCenter(cylinder, 0x236B, 0x0460);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x236B, 0x0460);
        invokeHoldSlot(cylinder, slot);

        assertEquals(0x2355, player.getCentreX());
        assertNotEquals(0x2356, player.getCentreX());
    }

    @Test
    void horizontalWideHeldPostPeakStepUsesFrameEntryAnchorForNonCpuRider() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x19E0, 0x0160, 0x42));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) (cylinder.getX() - 0x0C));
        player.setCentreY((short) 0x012C);

        cylinder.snapshotPreUpdatePosition();
        cylinder.onSolidContact(player, new SolidContact(true, true, false, true, false), 1);
        cylinder.update(1, player);

        int holdFrames = 0;
        for (int frame = 2; frame < 120; frame++) {
            cylinder.snapshotPreUpdatePosition();
            int preUpdateX = cylinder.getPreUpdateX();
            int twistAngle = (0x80 + (holdFrames * 2)) & 0xFF;
            cylinder.update(frame, player);
            int currentX = cylinder.getX();
            if (currentX < preUpdateX) {
                int expectedOffset = heldOffset(0x0C, twistAngle);
                assertEquals(preUpdateX + expectedOffset, player.getCentreX());
                assertNotEquals(currentX + expectedOffset, player.getCentreX());
                return;
            }
            holdFrames++;
        }

        fail("Expected subtype $42 to reach a negative horizontal step");
    }

    @Test
    void horizontalWideHeldPostPeakStepUsesFrameEntryAnchorForCpuSidekickRider() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x19E0, 0x0160, 0x42));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCpuControlled(true);
        player.setCentreX((short) (cylinder.getX() - 0x17));
        player.setCentreY((short) 0x0130);

        cylinder.snapshotPreUpdatePosition();
        cylinder.onSolidContact(player, new SolidContact(true, true, false, true, false), 1);
        cylinder.update(1, player);

        int holdFrames = 0;
        for (int frame = 2; frame < 120; frame++) {
            cylinder.snapshotPreUpdatePosition();
            int preUpdateX = cylinder.getPreUpdateX();
            int twistAngle = (0x80 + (holdFrames * 2)) & 0xFF;
            cylinder.update(frame, player);
            int currentX = cylinder.getX();
            if (currentX < preUpdateX) {
                int expectedOffset = heldOffset(0x17, twistAngle);
                assertEquals(preUpdateX + expectedOffset, player.getCentreX());
                assertNotEquals(currentX + expectedOffset, player.getCentreX());
                return;
            }
            holdFrames++;
        }

        fail("Expected subtype $42 to reach a negative horizontal step");
    }

    @Test
    void horizontalNarrowHeldPostPeakStepUsesFrameEntryAnchorForNonCpuRider() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x1BDF, 0x07E0, 0x41));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) (cylinder.getX() - 0x18));
        player.setCentreY((short) 0x07AC);

        cylinder.snapshotPreUpdatePosition();
        cylinder.onSolidContact(player, new SolidContact(true, true, false, true, false), 1);
        cylinder.update(1, player);

        int holdFrames = 0;
        for (int frame = 2; frame < 120; frame++) {
            cylinder.snapshotPreUpdatePosition();
            int preUpdateX = cylinder.getPreUpdateX();
            int twistAngle = (0x80 + (holdFrames * 2)) & 0xFF;
            cylinder.update(frame, player);
            int currentX = cylinder.getX();
            if (currentX < preUpdateX) {
                int expectedOffset = heldOffset(0x18, twistAngle);
                assertEquals(preUpdateX + expectedOffset, player.getCentreX());
                assertNotEquals(currentX + expectedOffset, player.getCentreX());
                return;
            }
            holdFrames++;
        }

        fail("Expected subtype $41 to reach a negative horizontal step");
    }

    @Test
    void horizontalNarrowHeldPositiveStepUsesFrameEntryAnchorForCpuSidekickRider() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x1BA0, 0x07E0, 0x41));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCpuControlled(true);
        player.setCentreX((short) 0x1B9A);
        player.setCentreY((short) 0x07B0);

        setCylinderCenter(cylinder, 0x1BA0, 0x07E0);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x1BA1, 0x07E0);
        Object slot = playerTwoSlot(cylinder);
        setSlotField(slot, "active", true);
        setSlotField(slot, "twistAngle", 0x8E);
        setSlotField(slot, "horizontalDistance", 0x06);
        setSlotField(slot, "priorityThresholdSource", 0x60);
        setSlotField(slot, "player", player);

        invokeHoldSlot(cylinder, slot);

        int expectedOffset = heldOffset(0x06, 0x8E);
        assertEquals(0x1BA0 + expectedOffset, player.getCentreX());
        assertNotEquals(0x1BA1 + expectedOffset, player.getCentreX());
    }

    @Test
    void circularHeldPositiveStepUsesFrameEntryAnchorForNonCpuRider() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x1B90, 0x0120, 0x4B));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1B7E);
        player.setCentreY((short) 0x00EC);

        setCylinderCenter(cylinder, 0x1B93, 0x0120);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x1B94, 0x0120);
        Object slot = playerOneSlot(cylinder);
        setSlotField(slot, "active", true);
        setSlotField(slot, "twistAngle", 0x80);
        setSlotField(slot, "horizontalDistance", 0x15);
        setSlotField(slot, "priorityThresholdSource", 0x60);
        setSlotField(slot, "player", player);

        invokeHoldSlot(cylinder, slot);

        int expectedOffset = heldOffset(0x15, 0x80);
        assertEquals(0x1B93 + expectedOffset, player.getCentreX());
        assertNotEquals(0x1B94 + expectedOffset, player.getCentreX());
    }

    @Test
    void circularFirstCapturePositiveStepUsesFrameEntryDistanceForNonCpuRider() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x1CE0, 0x0120, 0x4C));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1D14);
        player.setCentreY((short) 0x00EC);

        setCylinderCenter(cylinder, 0x1CFE, 0x0120);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x1CFF, 0x0120);
        Object slot = playerOneSlot(cylinder);

        invokeCaptureSlot(cylinder, slot, player, true);

        assertEquals(0x16, (int) getSlotField(slot, "horizontalDistance"));
        assertNotEquals(0x15, (int) getSlotField(slot, "horizontalDistance"));
        assertEquals(0x00, (int) getSlotField(slot, "twistAngle"));
    }

    @Test
    void circularVerticalObjectControlledSolidContactUsesFrameEntrySupportAnchorForNonCpuRider() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x1CE0, 0x0120, 0x4C));
        TestPlayableSprite player = new TestPlayableSprite();
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);

        setCylinderCenter(cylinder, 0x1D00, 0x0120);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x1D00, 0x0121);

        assertTrue(cylinder.usesPreUpdatePositionForSolidContact(player));
    }

    @Test
    void verticalOscillatorNewSideContactUsesFrameEntryAnchorForNonCpuRider() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x15C0, 0x04E0, 0x45));
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x15E9);
        player.setCentreY((short) 0x052C);
        player.setXSpeed((short) 0xFE9A);
        player.setGSpeed((short) 0xFE9A);
        player.setAir(false);

        setCylinderCenter(cylinder, 0x15C0, 0x04FD);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x15C0, 0x04FE);

        assertTrue(cylinder.usesPreUpdatePositionForSolidContact(player),
                "CNZ f6678: subtype $45 must classify SolidObject_cont side contact from "
                        + "the frame-entry y_pos to avoid loc_1E056 zeroing x_vel/ground_vel "
                        + "one frame early (sonic3k.asm:67656-67672, 67843-67851, 41473-41495)");
    }

    @Test
    void verticalOscillatorHeldRiderUsesFrameEntryYAnchorOnUpStep() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x2920, 0x0458, 0x46));
        TestPlayableSprite player = new TestPlayableSprite();
        player.setAir(false);

        setCylinderCenter(cylinder, 0x2920, 0x041D);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x2920, 0x041C);
        Object slot = playerOneSlot(cylinder);
        setSlotField(slot, "active", true);
        setSlotField(slot, "twistAngle", 0x00);
        setSlotField(slot, "horizontalDistance", 0x09);
        setSlotField(slot, "priorityThresholdSource", 0x60);
        setSlotField(slot, "player", player);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);

        invokeHoldSlot(cylinder, slot);

        assertTrue(cylinder.usesPreUpdatePositionForSolidContact(player));
        assertEquals(0x041D - 0x21 - player.getYRadius(), player.getCentreY(),
                "CNZ f13049: loc_3238C's upward step is already visible to the split engine, "
                        + "but ROM sub_324C0/SolidObjectFull carries the held rider from the "
                        + "frame-entry y_pos(a0) for this object pass (sonic3k.asm:67656-67672, "
                        + "67865-67874, 41016-41040, 41667-41679)");
        assertNotEquals(0x041C - 0x21 - player.getYRadius(), player.getCentreY());
    }

    @Test
    void verticalOscillatorCpuSidekickNewContactUsesFrameEntryYAnchorOnUpStep() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x2920, 0x0458, 0x46));
        TestPlayableSprite tails = new TestPlayableSprite();
        tails.setCpuControlled(true);
        tails.setAir(true);
        tails.setYSpeed((short) 0x0320);
        tails.setCentreX((short) 0x2921);
        tails.setCentreY((short) 0x03E3);

        setCylinderCenter(cylinder, 0x2920, 0x0416);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x2920, 0x0415);

        assertTrue(cylinder.usesPreUpdatePositionForSolidContact(tails),
                "CNZ f13060: P2 SolidObjectFull must classify the first top contact from "
                        + "the ROM-visible frame-entry y_pos=$0416, not the split engine's "
                        + "already-stepped y_pos=$0415 (sonic3k.asm:67656-67672, "
                        + "67865-67874, 41006-41016, 41394-41440)");
    }

    @Test
    void horizontalOscillatorCpuSidekickNewSideContactUsesFrameEntryXAnchor() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x1415, 0x0AE0, 0x42));
        TestPlayableSprite tails = new TestPlayableSprite();
        tails.setCpuControlled(true);
        tails.setAir(true);
        tails.setCentreX((short) 0x143B);
        tails.setCentreY((short) 0x0B09);

        setCylinderCenter(cylinder, 0x1415, 0x0AE0);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x1414, 0x0AE0);

        assertTrue(cylinder.usesPreUpdatePositionForSolidContact(tails),
                "CNZ f18259: P2 free side contact on a horizontal CNZCylinder must "
                        + "classify/separate from the ROM-visible frame-entry x_pos=$1415, "
                        + "not the split engine's already-stepped x_pos=$1414 "
                        + "(sonic3k.asm:67656-67672, 41006-41010, 41394-41407, 41488-41495)");
    }

    @Test
    void verticalOscillatorCpuCapturedRiderUsesFrameEntryYAnchorOnUpStep() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x2920, 0x0458, 0x46));
        TestPlayableSprite tails = new TestPlayableSprite();
        tails.setCpuControlled(true);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(tails);

        setCylinderCenter(cylinder, 0x2920, 0x0414);
        cylinder.snapshotPreUpdatePosition();
        setCylinderCenter(cylinder, 0x2920, 0x0413);

        assertTrue(cylinder.usesPreUpdatePositionForSolidContact(tails),
                "CNZ f13062: after P2 sub_324C0 capture, the following SolidObjectFull "
                        + "checkpoint must still use the ROM-visible frame-entry y_pos=$0414 "
                        + "for this vertical oscillator up step (sonic3k.asm:67656-67672, "
                        + "41006-41016, 67985-68005)");
    }

    @Test
    void sineVerticalJumpReleaseUsesStoredRomYVelocityNotPositionDelta() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x2920, 0x0458, 0x46));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x2916);
        player.setCentreY((short) 0x0396);
        Object slot = playerOneSlot(cylinder);
        setSlotField(slot, "active", true);
        setSlotField(slot, "player", player);
        setPrivateField(cylinder, "currentYVelocity", -0x0200);

        invokeReleaseSlot(cylinder, slot, 13116, true, (short) 0x0396);

        assertEquals((short) -0x0680, player.getYSpeed(),
                "CNZ f13116: loc_3238C writes y_pos directly and does not update "
                        + "y_vel(a0), so loc_325B6 must add -$680 to the stored "
                        + "ROM y_vel=$0000 rather than to the engine's synthetic "
                        + "position delta (sonic3k.asm:67865-67872, 68059-68068)");
    }

    @Test
    void mode0VerticalControllerUsesCurrentHeldInputWhileStandingOnCylinder() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x28A0, 0x04E0, 0x20));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x28B5);
        player.setCentreY((short) 0x04AC);

        player.setDirectionalInputPressed(false, false, false, false);
        cylinder.onSolidContact(player, new SolidContact(true, true, false, true, false), 12552);
        for (int frame = 12553; frame <= 12588; frame++) {
            boolean down = frame >= 12554 && frame <= 12579;
            boolean up = frame >= 12582;
            player.setDirectionalInputPressed(up, down, false, false);
            cylinder.snapshotPreUpdatePosition();
            cylinder.update(frame, player);
            cylinder.onSolidContact(player, new SolidContact(true, true, false, true, false), frame);
        }

        assertEquals(0x04FD, getPrivateIntField(cylinder, "centerY"),
                "CNZ f12588: loc_32254 reads current Ctrl_held_logical after MoveSprite2, "
                        + "so the first UP frame must affect mode-0 deceleration "
                        + "(sonic3k.asm:67736-67752, 67772-67782)");
        assertEquals(0xFD50, getPrivateIntField(cylinder, "mode0Velocity") & 0xFFFF);
    }

    @Test
    void mode0VerticalControllerTreatsZeroVelocityAsNonNegativeOnUpReturn() throws Exception {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnAtWithSubtype(0x28A0, 0x04E0, 0x20));
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setDirectionalInputPressed(true, false, false, false);

        setPrivateField(cylinder, "centerY", 0x056D);
        setPrivateField(cylinder, "mode0Velocity", 0x0020);
        setPrivateField(cylinder, "mode0YSubpixel", 0x00);
        setPrivateField(cylinder, "standingMask", 0x01);
        setPrivateField(cylinder, "standingMaskCache", 0x01);
        setSlotField(playerOneSlot(cylinder), "player", player);

        for (int frame = 12802; frame <= 12808; frame++) {
            invokeMode0VerticalController(cylinder);
        }

        assertEquals(0x0569, getPrivateIntField(cylinder, "centerY"),
                "CNZ f12808: loc_322AC branches with BPL after the -$20 step, "
                        + "so y_vel == 0 must take loc_322D2's -$10 path before "
                        + "later UP-held acceleration resumes (sonic3k.asm:67772-67782)");
        assertEquals(0xFE70, getPrivateIntField(cylinder, "mode0Velocity") & 0xFFFF);
    }

    @Test
    void externalAirLaunchDuringHeldSlotPreservesVelocityAfterHeldXWrite() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawn());
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1BC6);
        player.setCentreY((short) 0x07AC);
        player.setSubpixelRaw(0x3400, 0x5600);

        cylinder.onSolidContact(player, new SolidContact(true, false, false, true, false), 4310);
        cylinder.update(4311, player);

        player.setCentreX((short) 0x1C20);
        player.setSubpixelRaw(0x3400, 0x5600);
        player.setYSpeed((short) -0x700);
        player.setXSpeed((short) 0x0123);
        player.setGSpeed((short) 0x0456);
        player.setAir(true);
        player.setOnObject(false);
        ObjectControlState.none().applyTo(player);

        cylinder.update(4312, player);

        assertEquals(0x1BDF + heldOffset(25, 0x80), player.getCentreX());
        assertEquals(0x3400, player.getXSubpixelRaw());
        assertEquals((short) -0x700, player.getYSpeed());
        assertEquals((short) 0x0123, player.getXSpeed());
        assertEquals((short) 0x0456, player.getGSpeed());
        assertTrue(player.getAir());
        assertFalse(player.isOnObject());
        assertFalse(player.isObjectControlled());
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(0x1BDF, 0x07E0, 0x47, 0, 0, false, 0);
    }

    private static ObjectSpawn spawnWithSubtype(int subtype) {
        return new ObjectSpawn(0x1BDF, 0x07E0, 0x47, subtype, 0, false, 0);
    }

    private static ObjectSpawn spawnAtWithSubtype(int x, int y, int subtype) {
        return new ObjectSpawn(x, y, 0x47, subtype, 0, false, 0);
    }

    private static int heldOffset(int horizontalDistance, int twistAngle) {
        int thresholdByte = ((TrigLookupTable.sinHex(twistAngle) + 0x100) >> 2) & 0xFF;
        int distanceWord = (horizontalDistance << 8) | thresholdByte;
        return (TrigLookupTable.cosHex(twistAngle) * distanceWord) >> 16;
    }

    private static void setCylinderCenter(CnzCylinderInstance cylinder, int x, int y) throws Exception {
        setPrivateField(cylinder, "centerX", x);
        setPrivateField(cylinder, "centerY", y);
        var updateDynamicSpawn = CnzCylinderInstance.class.getSuperclass()
                .getDeclaredMethod("updateDynamicSpawn", int.class, int.class);
        updateDynamicSpawn.setAccessible(true);
        updateDynamicSpawn.invoke(cylinder, x, y);
    }

    private static Object playerOneSlot(CnzCylinderInstance cylinder) throws Exception {
        var field = CnzCylinderInstance.class.getDeclaredField("playerOneSlot");
        field.setAccessible(true);
        return field.get(cylinder);
    }

    private static Object playerTwoSlot(CnzCylinderInstance cylinder) throws Exception {
        var field = CnzCylinderInstance.class.getDeclaredField("playerTwoSlot");
        field.setAccessible(true);
        return field.get(cylinder);
    }

    private static void setSlotField(Object slot, String name, Object value) throws Exception {
        var field = slot.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(slot, value);
    }

    private static Object getSlotField(Object slot, String name) throws Exception {
        var field = slot.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(slot);
    }

    private static void setPrivateField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int getPrivateIntField(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void invokeMode0VerticalController(CnzCylinderInstance cylinder) throws Exception {
        var method = CnzCylinderInstance.class.getDeclaredMethod("updateMode0VerticalController");
        method.setAccessible(true);
        method.invoke(cylinder);
    }

    private static void invokeHoldSlot(CnzCylinderInstance cylinder, Object slot) throws Exception {
        var holdSlot = CnzCylinderInstance.class.getDeclaredMethod("holdSlot", slot.getClass());
        holdSlot.setAccessible(true);
        holdSlot.invoke(cylinder, slot);
    }

    private static void invokeCaptureSlot(CnzCylinderInstance cylinder, Object slot,
                                          AbstractPlayableSprite player, boolean latchedContact) throws Exception {
        var captureSlot = CnzCylinderInstance.class.getDeclaredMethod(
                "captureSlot", slot.getClass(), AbstractPlayableSprite.class, boolean.class);
        captureSlot.setAccessible(true);
        captureSlot.invoke(cylinder, slot, player, latchedContact);
    }

    private static void invokeReleaseSlot(CnzCylinderInstance cylinder, Object slot,
                                          int frameCounter, boolean jumpedOff, short jumpReleaseY) throws Exception {
        var releaseSlot = CnzCylinderInstance.class.getDeclaredMethod(
                "releaseSlot", slot.getClass(), int.class, boolean.class, short.class);
        releaseSlot.setAccessible(true);
        releaseSlot.invoke(cylinder, slot, frameCounter, jumpedOff, jumpReleaseY);
    }

    private static final class FeatureSetTestPlayableSprite extends TestPlayableSprite {
        void setPhysicsFeatureSetForTest(PhysicsFeatureSet featureSet) {
            setPhysicsFeatureSet(featureSet);
        }
    }
}

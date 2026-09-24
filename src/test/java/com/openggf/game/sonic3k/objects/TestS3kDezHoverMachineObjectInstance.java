package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.NativePositionOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kDezHoverMachineObjectInstance {
    @Test
    void parentAlternatesItsTwoMachineMappingsEveryObjectPass() {
        S3kDezHoverMachineObjectInstance machine = new S3kDezHoverMachineObjectInstance(spawn(0x100));
        machine.setServices(new TestObjectServices());

        machine.update(0, null);
        assertEquals(1, machine.mappingFrameForTest());
        machine.update(1, null);
        assertEquals(0, machine.mappingFrameForTest());
    }

    @Test
    void fieldStartsThirtyTwoPixelsRightAndUsesTwoStepOrbitAngle() {
        S3kDezHoverMachineFieldObjectInstance field =
                new S3kDezHoverMachineFieldObjectInstance(spawn(0x120));

        assertEquals(0x100, field.baseXForTest());
        assertEquals(4, field.getPriorityBucket());
        field.update(0, null);

        assertEquals(2, field.angleForTest());
        assertEquals(0x11F, field.getX(), "cos($02)>>3 is 31 pixels");
        assertEquals(4, field.getPriorityBucket(), "priority uses the pre-increment angle sign");
    }

    @Test
    void fieldOwnsTheRomRoutinePage() {
        assertEquals(4, new S3kDezHoverMachineFieldObjectInstance(spawn(0x120))
                .romObjectCodePointerHighWord());
    }

    @Test
    void fieldLiftsAndStartsTheNativeTumbleForAPlayerInsideItsSineArch() {
        S3kDezHoverMachineFieldObjectInstance field =
                new S3kDezHoverMachineFieldObjectInstance(spawn(0x120));
        field.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        NativePositionOps.writeXPosPreserveSubpixel(player, 0x120);
        NativePositionOps.writeYPosPreserveSubpixel(player, 0x180);

        field.liftForTest(player);

        assertEquals(0x17A, player.getCentreY(),
                "not.w/add.w and neg.w before asr.w make the $60 wave correction 6 pixels");
        assertEquals(0, player.getYSpeed());
        assertEquals(1, player.getGSpeed());
        assertEquals(1, player.getFlipAngle());
        assertEquals(0x7F, player.getFlipsRemaining());
        assertEquals(8, player.getFlipSpeed());
    }

    @Test
    void fieldKeepsNegativeRelativeYInTheRomBorrowPath() {
        S3kDezHoverMachineFieldObjectInstance field =
                new S3kDezHoverMachineFieldObjectInstance(spawn(0x120));
        field.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        NativePositionOps.writeXPosPreserveSubpixel(player, 0x120);
        NativePositionOps.writeYPosPreserveSubpixel(player, 0x170);

        field.liftForTest(player);

        assertEquals(0x16B, player.getCentreY(),
                "the borrow path adds relative Y back before the signed divide");
    }

    private static ObjectSpawn spawn(int x) {
        return new ObjectSpawn(x, 0x180, 0x5E, 0, 0, false, 0);
    }
}

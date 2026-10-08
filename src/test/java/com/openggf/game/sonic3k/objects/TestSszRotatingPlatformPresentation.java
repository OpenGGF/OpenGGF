package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszRotatingPlatformPresentation {
    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"LEFT", "RIGHT"})
    void everyAngleUsesTheRomPoseFlipsAndMappingGeometry(Direction facing) throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.sprite().tickStatus();
        var sprite = fixture.sprite();
        var rom = TestEnvironment.objectServices().rom();
        sprite.setDirection(facing);
        sprite.setRenderFlips(true, true);
        for (int angle = 0; angle < 256; angle++) {
            int row = ((((angle + 10) & 255) * 3) >> 5) & 0xFFFE;
            byte[] pose = rom.readBytes(SszCarriedPlayerPose.TABLE_ADDR + row, 2);
            int flags = pose[0] & 255, frame = pose[1] & 255;
            SszCarriedPlayerPose.apply(rom, sprite, angle);
            assertEquals(facing, sprite.getDirection(), "loc_460A6 retains Status_Facing");
            assertEquals((flags & 1) != 0, sprite.getRenderHFlip());
            assertEquals((flags & 2) != 0, sprite.getRenderVFlip());
            assertEquals(frame, sprite.getMappingFrame());
            var drawn = SpritePresentationRenderer.prepare(GameServices.graphics(), 0, 0, sprite::draw);
            assertFalse(drawn.tiles().isEmpty());
            int base = Sonic3kConstants.MAP_SONIC_ADDR;
            int address = base + (rom.read16BitAddr(base + frame * 2) & 0xFFFF);
            int count = rom.read16BitAddr(address) & 0xFFFF;
            byte[] pieces = rom.readBytes(address + 2, count * 6);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int expectedTiles = 0;
            for (int i = 0; i < pieces.length; i += 6) {
                int x = (short) (((pieces[i + 4] & 255) << 8) | (pieces[i + 5] & 255));
                int y = pieces[i];
                int width = (((pieces[i + 1] & 15) >> 2) + 1) * 8;
                int height = ((pieces[i + 1] & 3) + 1) * 8;
                minX = Math.min(minX, (flags & 1) != 0 ? -x - width : x);
                minY = Math.min(minY, (flags & 2) != 0 ? -y - height : y);
                expectedTiles += width * height / 64;
            }
            assertEquals(expectedTiles, drawn.tiles().size());
            assertEquals(sprite.getCentreX() + minX, drawn.tiles().stream().mapToDouble(t -> t.x()).min().orElseThrow());
            assertEquals(sprite.getCentreY() + minY, drawn.tiles().stream().mapToDouble(t -> t.y()).min().orElseThrow());
        }
        var saved = fixture.gameplayMode().getRewindRegistry().capture();
        var before = SpritePresentationRenderer.prepare(GameServices.graphics(), 0, 0, sprite::draw);
        sprite.setRenderFlips(false, true);
        fixture.gameplayMode().getRewindRegistry().restore(saved);
        assertEquals(before.tiles(), SpritePresentationRenderer.prepare(GameServices.graphics(), 0, 0, sprite::draw).tiles());
    }

    @ParameterizedTest
    @CsvSource({"false,LEFT", "false,RIGHT", "true,LEFT", "true,RIGHT"})
    void postAndCarrierApplyPoseFlipsWhileHoldingThePlayer(boolean useCarrier, Direction facing) throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.sprite().tickStatus();
        var services = TestEnvironment.objectServices();
        var manager = services.objectManager();
        GameServices.camera().setX((short) 0x100);
        GameServices.camera().setY((short) 0xB80);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        AbstractObjectInstance object = useCarrier
                ? new SszRotatingPlatformCarrierObjectInstance(new ObjectSpawn(0x200, 0xC00, 0, 0, 0, false, 0))
                : new SszRotatingPlatformObjectInstance(new ObjectSpawn(0x200, 0xC00, 0x76, 0, 0, false, 0));
        object.setServices(services); manager.addDynamicObject(object);
        var sprite = fixture.sprite();
        sprite.setDirection(facing); sprite.setRenderFlips(true, true);
        NativePositionOps.writeXPosPreserveSubpixel(sprite, 0x200);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, object.getY() - (useCarrier ? 0x11 : 0x21) - 20);
        manager.forceRidingObjectForBootstrap(sprite, object);
        object.update(0, sprite);
        for (int pass = 1; pass <= 128; pass++) {
            object.update(pass, sprite);
            int angle = useCarrier ? ((SszRotatingPlatformCarrierObjectInstance) object).angleForTest(0)
                    : ((SszRotatingPlatformObjectInstance) object).angleForTest(0);
            assertEquals((pass * 2) & 255, angle, "one complete native rotation");
            byte[] pose = services.rom().readBytes(SszCarriedPlayerPose.TABLE_ADDR
                    + (((((angle + 10) & 255) * 3) >> 5) & 0xFFFE), 2);
            assertEquals((pose[0] & 1) != 0, sprite.getRenderHFlip());
            assertEquals((pose[0] & 2) != 0, sprite.getRenderVFlip());
            assertEquals(pose[1] & 255, sprite.getMappingFrame());
            assertEquals(facing, sprite.getDirection());
            int radius = useCarrier ? ((SszRotatingPlatformCarrierObjectInstance) object).radiusForTest(0) : 0;
            assertEquals(0x200 + ((TrigLookupTable.cosHex(angle) * radius) >> 8), sprite.getCentreX());
        }
    }
}

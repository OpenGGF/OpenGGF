package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboPresentation {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void movingBodyGunAndFlameShareNativeAnchorsAndFlipsAfterRecreation(int flags) throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2);
        var services = TestEnvironment.objectServices();
        var manager = services.objectManager();
        GameServices.camera().setX((short) 0x100);
        GameServices.camera().setY((short) 0xB80);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        // loc_915F6's releaser rises and falls before entering the hover loop.
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 4, flags, false, 0));
        robo.setServices(services); manager.addDynamicObject(robo);
        EggRoboGunArmChildInstance gun = null;
        EggRoboJetFlameChildInstance flame = null;
        boolean moved = false;
        for (int pass = 0; pass < 220; pass++) {
            robo.update(pass, null);
            if (manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).isEmpty()) continue;
            gun = manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).getFirst();
            flame = manager.activeObjectsOfType(EggRoboJetFlameChildInstance.class).getFirst();
            gun.update(pass, null); flame.update(pass, null);
            boolean flip = flags != 0;
            assertEquals((robo.getX() + (flip ? 0x1C : -0x1C)) & 0xFFFF, gun.getX(), "sub_91930 X");
            assertEquals((robo.getX() + (flip ? 0xC : -0xC)) & 0xFFFF, flame.getX());
            assertEquals(robo.getY() + 0x1C, flame.getY(), "Refresh_ChildPositionAdjusted uses live Y");
            if (robo.stateForTest().equals("RISING") || robo.stateForTest().equals("FALLING")) {
                assertEquals(robo.getY() - 4, gun.getY(), "zero $32 falls back to live Y during launch");
            }
            var body = draw(robo);
            // CPU presentation emits whole tiles, while renderer FrameBounds
            // excludes transparent edge pixels. Use ROM mapping geometry here.
            var minimum = mappingMinimum(robo.mappingFrame, flip);
            assertFalse(body.tiles().isEmpty());
            assertEquals(robo.getX() + minimum[0],
                    body.tiles().stream().mapToDouble(t -> t.x()).min().orElseThrow(), "body draws at its live native X");
            assertEquals(robo.getY() + minimum[1], body.tiles().stream().mapToDouble(t -> t.y()).min().orElseThrow());
            assertTrue(draw(gun).tiles().stream().allMatch(t -> t.hFlip() == flip));
            assertTrue(draw(flame).tiles().stream().allMatch(t -> t.hFlip() == flip));
            moved |= robo.getX() != 0x200 && robo.getY() != 0xC00;
        }
        assertTrue(moved, "exercise actual horizontal and vertical movement");
        assertNotNull(gun); assertNotNull(flame);
        var gunFrame = draw(gun); var bodyFrame = draw(robo); var flameFrame = draw(flame);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();
        robo.setDestroyed(true); gun.setDestroyed(true); flame.setDestroyed(true);
        registry.restore(saved);
        assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey("object-manager",
                saved.get("object-manager"), registry.capture().get("object-manager")).isEmpty(),
                "recreation preserves live position metadata as well as visible art");
        assertEquals(bodyFrame, draw(manager.activeObjectsOfType(EggRoboBadnikInstance.class).getFirst()));
        assertEquals(gunFrame, draw(manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).getFirst()));
        assertEquals(flameFrame, draw(manager.activeObjectsOfType(EggRoboJetFlameChildInstance.class).getFirst()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void gunTurnsWithTheBodyAndFiresFromTheMirroredMuzzle(int initialFlags) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var services = TestEnvironment.objectServices();
        var manager = services.objectManager();
        ((SszZoneRuntimeState) services.zoneRuntimeState()).markEggRoboFlyByPassed(0);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 2, initialFlags, false, 0));
        robo.setServices(services); manager.addDynamicObject(robo);
        var player = fixture.sprite();
        for (int pass = 0; pass < 2; pass++) {
            int targetX = pass == initialFlags ? 0x180 : 0x280;
            com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(player, targetX);
            com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(player, 0xB90);
            robo.update(pass, player);
            var gun = manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).getFirst();
            gun.update(pass, player);
            boolean flip = targetX > robo.getX();
            assertEquals(robo.getX() + (flip ? 0x1C : -0x1C), gun.getX());
            assertTrue(draw(gun).tiles().stream().allMatch(t -> t.hFlip() == flip));
            assertTrue(draw(robo).tiles().stream().allMatch(t -> t.hFlip() == flip));
        }
        com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(player, robo.getY());
        robo.update(2, player);
        var gun = manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).getFirst();
        gun.update(2, player);
        var shot = manager.activeObjectsOfType(EggRoboShotInstance.class).getFirst();
        boolean flip = !robo.badnikFacingLeft();
        assertEquals(gun.getX() + (flip ? -0xB : 0xB), shot.getX(), "CreateChild10_NormalAdjusted muzzle offset");
        assertEquals(gun.getY() - 4, shot.getY());
        for (int pass = 0; pass < 32; pass++) shot.update(pass, player);
        assertTrue(shot.armedForTest());
        assertEquals(flip ? 0x800 : -0x800, shot.xVelocityForTest());
        int origin = shot.getX(); shot.update(32, player);
        assertEquals(origin + (flip ? 8 : -8), shot.getX());
    }

    private static SpritePresentation.Frame draw(AbstractObjectInstance object) {
        return SpritePresentationRenderer.prepare(GameServices.graphics(), 0, 0,
                () -> object.appendRenderCommands(new java.util.ArrayList<>()));
    }

    private static int[] mappingMinimum(int frame, boolean flip) throws Exception {
        var rom = TestEnvironment.objectServices().rom();
        int base = 0x184F34; // Map_EggRobo, locked-on ROM.
        int address = base + (rom.read16BitAddr(base + frame * 2) & 0xFFFF);
        int count = rom.read16BitAddr(address) & 0xFFFF;
        byte[] pieces = rom.readBytes(address + 2, count * 6);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (int offset = 0; offset < pieces.length; offset += 6) {
            int x = (short) (((pieces[offset + 4] & 255) << 8) | (pieces[offset + 5] & 255));
            int width = (((pieces[offset + 1] & 15) >> 2) + 1) * 8;
            minX = Math.min(minX, flip ? -x - width : x);
            minY = Math.min(minY, pieces[offset]);
        }
        return new int[] {minX, minY};
    }
}

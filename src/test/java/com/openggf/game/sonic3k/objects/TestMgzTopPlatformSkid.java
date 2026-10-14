package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMgzTopPlatformSkid {
    @Test
    void bothReversalDirectionsPlayRomSkidSoundOnEveryQualifyingPass() throws Exception {
        for (boolean left : new boolean[] {true, false}) {
            RecordingServices services = new RecordingServices();
            MGZTopPlatformObjectInstance platform = platform(services);
            TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x100);
            sonic.setGSpeed((short) (left ? 0x600 : -0x600));
            sonic.setLogicalInputState(false, false, left, !left, false);

            groundMotion(platform, sonic);
            groundMotion(platform, sonic);

            assertEquals(List.of(Sonic3kSfx.SKID.id, Sonic3kSfx.SKID.id), services.sounds,
                    "loc_3559E/loc_35620 call Play_SFX each time the speed threshold holds");
            assertEquals(Sonic3kAnimationIds.SKID.id(), sonic.getAnimationId());
            assertEquals(left ? Direction.RIGHT : Direction.LEFT, sonic.getDirection());
        }
    }

    @Test
    void shippedSpeedThresholdAndAngleGateSuppressNonSkids() throws Exception {
        for (boolean left : new boolean[] {true, false}) {
            RecordingServices services = new RecordingServices();
            MGZTopPlatformObjectInstance platform = platform(services);
            TestablePlayableSprite sonic = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x100);
            sonic.setLogicalInputState(false, false, left, !left, false);
            // FixBugs=0 truncates the low inertia byte before the +/-$400 test.
            sonic.setGSpeed((short) (left ? 0x400 : -0x300));
            groundMotion(platform, sonic);
            assertTrue(services.sounds.isEmpty(), "subthreshold deceleration does not skid");
            sonic.setGSpeed((short) (left ? 0x600 : -0x600));
            sonic.setAngle((byte) 0x40);
            groundMotion(platform, sonic);
            assertTrue(services.sounds.isEmpty(), "bne skips skid on non-flat angle result");
        }
    }

    private static MGZTopPlatformObjectInstance platform(RecordingServices services) {
        var platform = new MGZTopPlatformObjectInstance(new ObjectSpawn(0x100, 0x100, 0x5D, 0, 0, false, 0));
        platform.setServices(services);
        return platform;
    }

    private static void groundMotion(MGZTopPlatformObjectInstance platform, AbstractPlayableSprite sprite)
            throws Exception {
        Method method = MGZTopPlatformObjectInstance.class.getDeclaredMethod(
                "runPlayerGroundMotion", AbstractPlayableSprite.class);
        method.setAccessible(true);
        method.invoke(platform, sprite);
    }

    private static final class RecordingServices extends StubObjectServices {
        private final List<Integer> sounds = new ArrayList<>();
        @Override public void playSfx(int soundId) { sounds.add(soundId); }
    }
}

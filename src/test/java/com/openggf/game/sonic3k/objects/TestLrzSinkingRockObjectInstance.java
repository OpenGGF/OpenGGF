package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code Obj_LRZSinkingRock} (sonic3k.asm:87898-87941, ROM {@code $4279E}).
 *
 * <p>Every expectation here is the routine's own arithmetic or a byte of the ROM's
 * {@code SineTable} ({@code Levels/Misc/sine.bin}, read out for the four angles asserted below),
 * not a value taken from a trace or from {@link com.openggf.physics.TrigLookupTable}.
 */
class TestLrzSinkingRockObjectInstance {

    private static final int OBJECT_ID = Sonic3kObjectIds.LBZ_RIDE_GRAPPLE;
    private static final int BASE_X = 0x04F8;
    private static final int BASE_Y = 0x0543;

    /** {@code move.w #$1B,d1 / #$10,d2 / #$11,d3} (sonic3k.asm:87934-87936). */
    @Test
    void solidParamsAreTheRoutineArguments() {
        LrzSinkingRockObjectInstance rock = rock();
        assertEquals(0x1B, rock.getSolidParams().halfWidth(), "d1");
        assertEquals(0x10, rock.getSolidParams().airHalfHeight(), "d2");
        assertEquals(0x11, rock.getSolidParams().groundHalfHeight(), "d3");
    }

    /** {@code move.w y_pos(a0),$46(a0)} (:87904); act 1 keeps {@code mapping_frame} 0 (:87908). */
    @Test
    void initLatchesThePlacedYAndActOneMappingFrame() {
        LrzSinkingRockObjectInstance rock = rock();
        assertEquals(BASE_Y, rock.baseY());
        assertEquals(0, rock.mappingFrame());
        assertEquals(0, rock.angle());
        assertEquals(BASE_Y, rock.getCentreY(), "sin(0) = 0, so the rock starts on its placed Y");
    }

    /**
     * {@code loc_427F8} (:87924-87927): a standing player raises {@code $2E} by one a frame and
     * {@code cmpi.b #$40} stops it there. The standing bit is the one the PREVIOUS frame's
     * {@code SolidObjectFull} left, so the first update after a contact is the first rise.
     */
    @Test
    void standingRaisesTheAngleOneAFrameAndStopsAtFortyHex() {
        LrzSinkingRockObjectInstance rock = rock();
        TestablePlayableSprite player = standingPlayer();
        rock.setServices(services());

        for (int frame = 1; frame <= 0x40; frame++) {
            rock.onSolidContact(player, standingContact(), frame);
            rock.update(frame, player);
            assertEquals(frame, rock.angle(), "angle after " + frame + " standing frames");
        }
        for (int frame = 0x41; frame <= 0x50; frame++) {
            rock.onSolidContact(player, standingContact(), frame);
            rock.update(frame, player);
            assertEquals(0x40, rock.angle(), "the angle is clamped at $40");
        }
    }

    /**
     * {@code loc_427E2} (:87919-87922): with no standing bit the angle counts back down and
     * {@code tst.b} stops it at zero rather than wrapping to {@code $FF}.
     */
    @Test
    void releasingLowersTheAngleBackToZeroWithoutWrapping() {
        LrzSinkingRockObjectInstance rock = rock();
        TestablePlayableSprite player = standingPlayer();
        rock.setServices(services());

        for (int frame = 1; frame <= 10; frame++) {
            rock.onSolidContact(player, standingContact(), frame);
            rock.update(frame, player);
        }
        assertEquals(10, rock.angle());

        for (int frame = 11; frame <= 20; frame++) {
            rock.update(frame, player);
            assertEquals(20 - frame, rock.angle(), "angle after " + (frame - 10) + " idle frames");
        }
        for (int frame = 21; frame <= 25; frame++) {
            rock.update(frame, player);
            assertEquals(0, rock.angle(), "the angle is clamped at zero");
        }
    }

    /**
     * {@code loc_42804} (:87929-87933): {@code GetSineCosine} then {@code asr.w #3} then a word add
     * onto {@code $46(a0)}. The four sine values are ROM bytes: {@code sine.bin} holds
     * {@code $0000}, {@code $0061}, {@code $00B5} and {@code $0100} at angles 0, {@code $10},
     * {@code $20} and {@code $40}, which the arithmetic shift turns into 0, 12, 22 and 32 pixels.
     */
    @Test
    void yPositionIsTheBasePlusTheRomSineOverEight() {
        int[][] cases = {{0x00, 0}, {0x10, 12}, {0x20, 22}, {0x40, 32}};
        for (int[] testCase : cases) {
            LrzSinkingRockObjectInstance rock = rock();
            TestablePlayableSprite player = standingPlayer();
            rock.setServices(services());
            for (int frame = 1; frame <= testCase[0]; frame++) {
                rock.onSolidContact(player, standingContact(), frame);
                rock.update(frame, player);
            }
            assertEquals(testCase[0], rock.angle());
            assertEquals(BASE_Y + testCase[1], rock.getCentreY(),
                    "angle $" + Integer.toHexString(testCase[0]));
        }
    }

    /** {@code move.w #$280,priority(a0)} and a clear priority bit (:87900, :87903). */
    @Test
    void renderStateIsTheInitWrites() {
        LrzSinkingRockObjectInstance rock = rock();
        assertEquals(0x10, rock.getOnScreenHalfWidth(), "width_pixels");
        assertEquals(0x10, rock.getOnScreenHalfHeight(), "height_pixels");
        org.junit.jupiter.api.Assertions.assertFalse(rock.isHighPriority(),
                "make_art_tile($0D3,2,0) leaves the priority bit clear");
    }

    private static LrzSinkingRockObjectInstance rock() {
        return new LrzSinkingRockObjectInstance(
                new ObjectSpawn(BASE_X, BASE_Y, OBJECT_ID, 0, 0, false, 0));
    }

    private static TestablePlayableSprite standingPlayer() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic",
                (short) BASE_X, (short) (BASE_Y - 0x11));
        player.setAirForTest(false);
        return player;
    }

    private static SolidContact standingContact() {
        return new SolidContact(true, false, false, true, false);
    }

    private static TestObjectServices services() {
        return new TestObjectServices().withIsolatedObjectManager();
    }
}

package slaytherobotnik.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The motion the event pictures port from the ROM: the hurt knockback, the invulnerability
 * blink, the ring spill, the Egg Robo's hover and leap, and the Super Emeralds' palette lines.
 */
class EventMotionTest {
    @Test
    void hurtKnockbackRisesFortyFivePixelsAndLandsInFortyFourFrames() {
        // HurtCharacter: x_vel -$200, y_vel -$400; the hurt routine adds $30 a frame.
        EventActors.Fling hero = new EventActors.Fling(100, 0, EventActors.HURT_X_VEL, EventActors.HURT_Y_VEL,
                EventActors.HURT_GRAVITY);
        float peak = 0;
        int frames = 0;
        do {
            hero.step();
            frames++;
            peak = Math.min(peak, hero.py());
        } while (!(hero.yVel > 0 && hero.py() >= 0) && frames < 200);
        // Moving before the gravity is added: down again on the 44th frame, 44.7 pixels up at most.
        assertEquals(44, frames, "landing frame");
        assertEquals(-44.7f, peak, 0.1f, "peak height");
        assertEquals(100 - 2 * frames, hero.px(), 0.01f, "two pixels back a frame");
    }

    @Test
    void invulnerableHeroShowsFourFramesInEight() {
        int shown = 0;
        for (int timer = 120; timer > 0; timer--) {
            if (EventActors.blinkVisible(timer)) {
                shown++;
            }
        }
        assertEquals(60, shown);
        assertTrue(EventActors.blinkVisible(4));
        assertFalse(EventActors.blinkVisible(3));
        assertTrue(EventActors.blinkVisible(0), "drawn once the timer runs out");
    }

    @Test
    void ringsSpillInMirroredPairsFromAngle88() {
        int[][] v = ScrapyardPicture.spill(8);
        // GetSineCosine($88) is about (-$32, -$FB); shifted left once.
        assertEquals(-0x64, v[0][0], 1);
        assertEquals(-0x1F6, v[0][1], 1);
        for (int i = 0; i < 8; i += 2) {
            assertEquals(-v[i][0], v[i + 1][0], "pair " + i + " mirrors");
            assertEquals(v[i][1], v[i + 1][1]);
            assertTrue(v[i][1] < 0, "every ring leaves upwards");
        }
        assertTrue(Math.abs(v[6][0]) > Math.abs(v[0][0]), "later pairs fly wider");
        assertEquals(6, ScrapyardPicture.ringsShown(30));
        assertEquals(8, ScrapyardPicture.ringsShown(60));
        assertEquals(2, ScrapyardPicture.ringsShown(5));
    }

    @Test
    void eggRoboHoversSixteenPixelsEachWayEvery128Frames() {
        CollectorPicture robot = new CollectorPicture(null);
        float low = 0;
        float high = 0;
        for (int frame = 1; frame <= 256; frame++) {
            robot.move();
            low = Math.min(low, robot.height());
            high = Math.max(high, robot.height());
            if (frame == 128 || frame == 256) {
                assertEquals(0, robot.height(), 0.01f, "back at rest after a full swing, frame " + frame);
            }
        }
        assertEquals(16, high, 0.6f);
        assertEquals(-16, low, 0.6f);
    }

    @Test
    void eggRoboLeapsForItsPrizeAndSettlesBack() {
        CollectorPicture robot = new CollectorPicture(null);
        CollectorPicture still = new CollectorPicture(null);
        robot.grab();
        float highest = 0;
        for (int frame = 0; frame < 400; frame++) {
            robot.move();
            still.move();
            highest = Math.min(highest, robot.height() - still.height());
        }
        // loc_9164E to -$200 at $10 a frame (33 pixels), then loc_9167E slowing at $20 a frame
        // (15 more before it turns): 48 pixels up.
        assertEquals(-48, highest, 0.5f);
        assertEquals(still.height(), robot.height(), 0.01f, "it sinks back over its heap");
    }

    @Test
    void superEmeraldsUseWord90816PaletteLines() {
        int[] lines = {2, 0, 2, 0, 0, 1, 3};
        for (int subtype = 0; subtype < 7; subtype++) {
            assertEquals(lines[subtype], MuralPicture.lineOf(subtype), "subtype " + subtype);
        }
    }
}

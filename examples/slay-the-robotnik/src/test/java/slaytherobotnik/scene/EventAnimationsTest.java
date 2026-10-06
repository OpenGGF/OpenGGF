package slaytherobotnik.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The ROM routines the Giant Ring, Monitor Row, Ring Shrine and Strange Mushrooms pictures
 * run, checked against the scripts and tables as the disassembly lists them.
 */
class EventAnimationsTest {
    /** AniRaw_SSEntryRing and AniRaw_SSEntryFlash (sonic3k.asm). */
    private static final byte[] RING = bytes(4, 0, 0, 1, 2, 3, 4, 5, 6, 7, 0xF8, 0x0C, 6, 0x0A, 9, 8, 0x0B, 0xFC);
    private static final byte[] FLASH = bytes(0, 0, 0, 1, 2, 3 | 0x40, 3, 2, 1, 0, 0xF4);

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    /** Ani_Monitor ("Anim - Monitor.asm"): eleven offsets, then the scripts in order. */
    private static byte[] aniMonitor() {
        int[][] scripts = {
                {1, 0, 1, 0xFF},
                {1, 0, 2, 2, 1, 2, 2, 0xFF},
                {1, 0, 3, 3, 1, 3, 3, 0xFF},
                {1, 0, 4, 4, 1, 4, 4, 0xFF},
                {1, 0, 5, 5, 1, 5, 5, 0xFF},
                {1, 0, 6, 6, 1, 6, 6, 0xFF},
                {1, 0, 7, 7, 1, 7, 7, 0xFF},
                {1, 0, 8, 8, 1, 8, 8, 0xFF},
                {1, 0, 9, 9, 1, 9, 9, 0xFF},
                {1, 0, 0x0A, 0x0A, 1, 0x0A, 0x0A, 0xFF},
                {2, 0, 1, 0x0B, 0xFE, 1}};
        List<Integer> out = new ArrayList<>();
        int offset = scripts.length * 2;
        for (int[] script : scripts) {
            out.add(offset >> 8);
            out.add(offset & 0xFF);
            offset += script.length;
        }
        for (int[] script : scripts) {
            for (int b : script) {
                out.add(b);
            }
        }
        return bytes(out.stream().mapToInt(Integer::intValue).toArray());
    }

    private static List<Integer> frames(SpriteAnim anim, int ticks) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            anim.tick();
            out.add(anim.frame());
        }
        return out;
    }

    @Test
    void monitorsFlickerTheirIconWithStaticTwoFramesInSix() {
        SpriteAnim rings = new SpriteAnim(aniMonitor(), 3);
        assertEquals(List.of(0, 0, 4, 4, 4, 4, 1, 1, 4, 4, 4, 4, 0, 0), frames(rings, 14));
        SpriteAnim nothing = new SpriteAnim(aniMonitor(), 0);
        assertEquals(List.of(0, 0, 1, 1, 0, 0, 1, 1), frames(nothing, 8), "the static monitor never shows an icon");
    }

    @Test
    void aBrokenMonitorFlickersOnceThenStaysBroken() {
        SpriteAnim anim = new SpriteAnim(aniMonitor(), 1);
        frames(anim, 5);
        anim.set(0x0A);
        List<Integer> shown = frames(anim, 40);
        assertEquals(List.of(0, 0, 0, 1, 1, 1, 0x0B, 0x0B, 0x0B), shown.subList(0, 9));
        assertTrue(shown.subList(6, 40).stream().allMatch(f -> f == 0x0B), shown.toString());
    }

    @Test
    void theGiantRingFormsThenTurnsTheWayTheRomScriptRuns() {
        RawAnim ring = new RawAnim(RING);
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < 45 + 7 * 8; i++) {
            ring.tick();
            shown.add(ring.frame());
        }
        // Frames 0-7 at five frames each (0 twice), then $A, 9, 8, $B at seven each, round and round.
        for (int f = 0; f < 8; f++) {
            assertEquals(f, (int) shown.get(f * 5 + 2), "forming frame " + f);
        }
        int turning = 40;
        int[] order = {0x0A, 9, 8, 0x0B, 0x0A, 9, 8, 0x0B};
        for (int i = 0; i < order.length; i++) {
            assertEquals(order[i], (int) shown.get(turning + i * 7 + 3), "turning step " + i + " in " + shown);
        }
    }

    @Test
    void aJumperTouchesTheRingVanishesInTheFlashAndEntersThirtyTwoFramesLater() {
        for (int speed : new int[] {0x680, 0x600}) {
            GiantRingEntry entry = new GiantRingEntry(RING, FLASH, 82, 62);
            for (int i = 0; i < 60; i++) {
                entry.tick(); // let the ring form
            }
            entry.jump(22, 105, speed);
            int touched = -1;
            int ringGone = -1;
            int flashDone = -1;
            int entered = -1;
            List<Integer> flash = new ArrayList<>();
            for (int frame = 0; frame < 200 && entered < 0; frame++) {
                entry.tick();
                if (entry.touchedNow()) {
                    touched = frame;
                }
                if (entry.flashFrame() >= 0) {
                    flash.add(entry.flashFrame() | (entry.flashFlipped() ? 0x40 : 0));
                }
                if (ringGone < 0 && touched >= 0 && entry.ringFrame() < 0) {
                    ringGone = frame;
                }
                if (flashDone < 0 && entry.phase() == GiantRingEntry.WAITING) {
                    flashDone = frame;
                }
                if (entry.enteredNow()) {
                    entered = frame;
                }
            }
            assertTrue(touched > 0 && touched < 40, "speed " + speed + " touched at " + touched);
            assertEquals(List.of(0, 0, 1, 2, 0x43, 0x43, 0x42, 0x41, 0x40), flash,
                    "AniRaw_SSEntryFlash, one frame each");
            // SSEntryFlash_Main: the ring goes when the flash changes frame with anim_frame at 3 - the
            // third frame after the touch, as the flash shows Map_SSEntryFlash frame 2.
            assertEquals(touched + 3, ringGone, "the ring goes at the flash's anim_frame 3");
            assertEquals(flashDone + 0x20 + 1, entered, "Obj_Wait $20 frames, then sfx_EnterSS");
        }
    }

    @Test
    void aJumpOrDriftBeforeTheRingHasFormedStillEndsUpInside() {
        GiantRingEntry jumped = new GiantRingEntry(RING, FLASH, 82, 62);
        jumped.jump(22, 105, 0x600); // straight away, while the ring is still forming
        GiantRingEntry drifted = new GiantRingEntry(RING, FLASH, 63, 40);
        drifted.drift(63, 116, 30);
        boolean jumpedIn = false;
        boolean driftedIn = false;
        for (int frame = 0; frame < 300; frame++) {
            jumped.tick();
            drifted.tick();
            jumpedIn |= jumped.touchedNow();
            driftedIn |= drifted.touchedNow();
        }
        assertTrue(jumpedIn, "the hero jumps again until the ring has formed");
        assertTrue(driftedIn, "the card waits at the centre until the ring has formed");
    }

    @Test
    void aMonitorIconRisesThirtyTwoFramesThenGivesItsPowerAndStaysThirty() {
        MonitorContents contents = new MonitorContents();
        int effect = -1;
        int gone = -1;
        List<Integer> explosion = new ArrayList<>();
        for (int frame = 1; frame <= 120 && gone < 0; frame++) {
            contents.tick();
            explosion.add(contents.explosionFrame());
            if (contents.effectNow()) {
                effect = frame;
                // sum of (-$300 + $18k) for k = 0..31, in pixels
                assertEquals(-(32 * 0x300 - 0x18 * 31 * 32 / 2) / 256f, contents.iconOffset(), 0.001f);
            }
            if (contents.gone()) {
                gone = frame;
            }
        }
        assertEquals(33, effect, "the power-up comes when the icon stops rising");
        assertEquals(effect + 30, gone, "anim_frame_timer 30-1, then the icon goes");
        assertEquals(List.of(0, 0, 0, 1), explosion.subList(0, 4), "Map_Explosion frame 0 for its first 4 frames");
        assertEquals(1, (int) explosion.get(3 + 7));
        assertEquals(2, (int) explosion.get(3 + 8));
        assertEquals(4, (int) explosion.get(3 + 31));
        assertEquals(-1, (int) explosion.get(3 + 32), "gone once frame 4 has shown for 8 frames");
    }

    /** GetSineCosine's table as the ROM rounds it: sin(a) * 256 for 256 steps and a quarter more. */
    private static int[] sine() {
        int[] table = new int[0x140];
        for (int i = 0; i < table.length; i++) {
            table[i] = (int) Math.round(Math.sin(i * Math.PI * 2 / 256) * 256);
        }
        return table;
    }

    @Test
    void lostRingsFanOutInMirroredPairsAndSlowDownForTheSecondFan() {
        int[] sine = sine();
        SpilledRings rings = new SpilledRings(sine, 60, 100, 40);
        assertEquals(32, rings.count(), "never more than 32");
        for (int i = 0; i < 32; i += 2) {
            assertEquals(-rings.xVel(i), rings.xVel(i + 1), "ring " + i + " has a mirror twin");
            assertEquals(rings.yVel(i), rings.yVel(i + 1));
        }
        // The first fan: angle $88 upwards by $10, speed shifted left by 2.
        assertEquals((short) (sine[0x88] << 2), rings.xVel(0));
        assertEquals((short) (sine[0x88 + 0x40] << 2), rings.yVel(0));
        assertEquals((short) (sine[0x98] << 2), rings.xVel(2));
        // The second fan starts again at $88, at half the speed.
        assertEquals((short) (sine[0x88] << 1), rings.xVel(16));
        assertEquals((short) (sine[0x88 + 0x40] << 1), rings.yVel(16));
    }

    @Test
    void lostRingsBounceOnTheFloorAndAreGoneAfterTheSpillCounterRunsOut() {
        SpilledRings rings = new SpilledRings(sine(), 60, 100, 32);
        int[] floor = new int[126];
        java.util.Arrays.fill(floor, 120);
        boolean bounced = false;
        int ticks = 0;
        while (rings.active() && ticks < 400) {
            int before = rings.yVel(0);
            rings.tick(floor);
            ticks++;
            if (before > 0 && rings.yVel(0) < 0) {
                bounced = true;
                assertEquals(-(before + 0x18 - ((before + 0x18) >> 2)), rings.yVel(0),
                        "a bounce keeps 3/4 of the speed");
            }
            for (int i = 0; i < rings.count(); i++) {
                // The floor is only checked one frame in eight, so a fast ring may sink in a little first.
                assertTrue(rings.y(i) < 120 + 40, "ring " + i + " fell through the floor at " + rings.y(i));
            }
        }
        assertTrue(bounced, "ring 0 came down and bounced");
        assertEquals(0xFF, ticks, "Ring_spill_anim_counter starts at $FF");
    }

    @Test
    void attractedRingsAllReachTheHeroAndSparkleAway() {
        AttractedRings rings = new AttractedRings(12, 84, 70, 24, 105, 3);
        int collected = 0;
        int ticks = 0;
        while (!rings.done() && ticks < 600) {
            rings.tick();
            collected += rings.collectedNow();
            ticks++;
        }
        assertEquals(12, collected);
        assertTrue(ticks < 180, "took " + ticks + " frames");
    }

    @Test
    void mushroomCapsLaunchOnTheirFirstSpringFrameAtTheRomSpeeds() {
        // Ani_MHZMushroomCap ("Anim - Mushroom Cap.asm").
        byte[] ani = bytes(0, 4, 0, 7, 7, 0, 0xFF, 0, 1, 1, 1, 1, 1, 3, 0, 3, 0, 3, 0, 3, 0, 3, 0, 2, 0, 2, 0,
                2, 0, 2, 0, 0xFC, 0xFD, 0, 0xFD, 0);
        byte[] positions = new byte[0x6C];
        MushroomCap cap = new MushroomCap(ani, positions, 0);
        cap.tick();
        assertEquals(0, cap.frame());
        assertEquals(0x12, cap.surface());
        cap.spring();
        int launch = -1;
        for (int frame = 1; frame <= 40; frame++) {
            cap.tick();
            if (frame <= 5) {
                assertEquals(1, cap.frame(), "squashed for five frames");
                assertEquals(8, cap.surface());
            }
            if (launch < 0 && cap.launching()) {
                launch = frame;
            }
        }
        assertEquals(6, launch);
        assertFalse(cap.launching(), "back at rest once the animation ends");
        assertEquals(-(0x660 + 0x20), MushroomCap.launchSpeed(0x500));
        assertEquals(-(0x760 + 0x20), MushroomCap.launchSpeed(0x660));
        assertEquals(-(0x860 + 0x20), MushroomCap.launchSpeed(0x760));
        assertEquals(0, MushroomCap.counter(44));
        assertEquals(2, MushroomCap.counter(45));
    }
}

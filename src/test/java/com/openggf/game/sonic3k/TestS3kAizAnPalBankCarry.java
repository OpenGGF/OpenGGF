package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Palette;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnPal_AIZ1 and AnPal_AIZ2 share one RAM bank: {@code Palette_cycle_counter0},
 * {@code Palette_cycle_counter1} and {@code Palette_cycle_counters}
 * (sonic3k.constants.asm:599-615). Level's loc_60DE clears it
 * (sonic3k.asm:7684); the seamless AIZ1-&gt;AIZ2 reload never reaches loc_60DE, so
 * AnPal_AIZ2 continues from AnPal_AIZ1's values. LevelLoop calls Animate_Palette
 * (7923) before the DeformBgLayer/ScreenEvents work that performs the reload
 * (7929-7930), so the reload iteration's call is still AnPal_AIZ1's.
 *
 * <p>Native witness (aiz_completerun, native-v1 RAM/CRAM): at the act change counter1
 * is 4, counter0 $1620 and counters+$02 $2A; the first AIZ2 call is the next main-loop
 * iteration, where channel B (counters+$08/+$04) starts; channel A first runs on the
 * fifth AIZ2 call and reads the inherited counters+$02 (entry 7).
 *
 * <p>Expectations here are derived from the ROM routines and tables only (normal-mode
 * AnPal_AIZ1 from a cleared bank, sonic3k.asm:3237-3265; AnPal_AIZ2, 3263-3300), and
 * observed through the level palette, so the same checks run against any source.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAizAnPalBankCarry {
    /** AIZ1 calls before the act change: runs 1..7 at calls 1, 9, ..., 49. */
    private static final int AIZ1_CALLS = 51;
    // After 51 calls from a cleared bank: counter1 = 7 - (50 % 8) = 5, counter0 = 7 * 8,
    // counters+$02 = 7 * 6 = $2A (trickle entry 7), water entry (counter0 >> 3) & 3 = 3.
    private static final int CARRIED_COUNTER1 = 5;
    private static final int CARRIED_TRICKLE_ENTRY = 7;
    private static final int CARRIED_WATER_ENTRY = 3;

    private static HeadlessTestFixture fixture;

    @Test
    void seamlessReloadCarriesTheAnPalBankIntoAiz2() throws Exception {
        var tables = aiz1History(AIZ1_CALLS);
        reloadToAiz2();
        assertAiz2FollowsTheCarriedBank(tables, CARRIED_COUNTER1);
    }

    @Test
    void reloadInsideALevelLoopIterationKeepsThatIterationsCallForAiz1() throws Exception {
        // 50 calls, then the reload iteration's Animate_Palette is AIZ1 call 51.
        var tables = aiz1History(AIZ1_CALLS - 1);
        var level = GameServices.level();
        level.setFrameCounter(level.getFrameCounter() + 1); // LevelLoop's addq ran
        reloadToAiz2();
        int[] before = channels();
        animator().update(); // the rest of the reload iteration (registry frame already begun)
        assertArrayEquals(before, channels(),
                "the reload iteration ran AnPal_AIZ1, not a first AnPal_AIZ2 call");
        assertAiz2FollowsTheCarriedBank(tables, CARRIED_COUNTER1);
    }

    @Test
    void fullLevelLoadClearsTheBank() throws Exception {
        var tables = aiz1History(AIZ1_CALLS);
        GameServices.level().loadZoneAndAct(0, 1);
        GameServices.level().consumePendingInitialProcessSpritesPass();
        skipLevelFade();
        step();
        // Cleared counter1 expires on the first call: both channels run entry 0.
        assertColours(tables.trickle(0), line(2, 4), line(2, 8), line(3, 11), "trickle entry 0");
        assertWater(tables.water(0), "water entry 0");
        assertColours(new int[] {tables.torch(0)}, line(3, 1), "torch entry 0");
    }

    @Test
    void rewindRestoresTheCarriedBankTwiceWithForwardReplay() throws Exception {
        var tables = aiz1History(AIZ1_CALLS);
        reloadToAiz2();
        levelLoopPaletteCall();
        levelLoopPaletteCall();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        List<int[]> forward = record(8);
        assertFalse(Arrays.equals(forward.get(2), forward.get(3)),
                "channel A runs inside the window (AIZ2 call 6)");
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved);
            List<int[]> replay = record(8);
            for (int i = 0; i < forward.size(); i++) {
                assertArrayEquals(forward.get(i), replay.get(i), "cycle " + cycle + " frame " + i);
            }
        }
        assertColours(tables.trickle(CARRIED_TRICKLE_ENTRY), line(2, 4), line(2, 8), line(3, 11),
                "the window holds the carried entry until the next channel-A run (AIZ2 call 12)");
    }

    private Tables aiz1History(int calls) throws Exception {
        // A gameplay position past AIZ1_Resize's Camera_X $1000 edge.
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro()
                .startPosition((short) 0x1EA6, (short) 0x490).startPositionIsCentre().build();
        assertTrue((GameServices.camera().getX() & 0xFFFF) >= 0x1000,
                "AIZ1_Resize has cleared AIZ1_palette_cycle_flag: the normal AnPal_AIZ1 branch runs");
        Tables tables = Tables.load();
        GameServices.level().consumePendingInitialProcessSpritesPass();
        skipLevelFade();
        for (int call = 1; call <= calls; call++) {
            step();
            if (call == 48) {
                // Run 6 (call 41) read counter0 = 5 * 8 (entry 1) and counters+$02 = 5 * 6 (entry 5).
                assertColours(tables.waterfall(1), line(2, 11), line(2, 12), line(2, 13), line(2, 14),
                        "AnPal_AIZ1 run 6 waterfall entry");
                assertColours(tables.aiz1Secondary(5), line(3, 12), line(3, 13), line(3, 14),
                        "AnPal_AIZ1 run 6 counters+$02 entry");
            }
            if (call == 49) {
                // Run 7 reads counter0 = 6 * 8 (entry 2) and counters+$02 = 6 * 6 (entry 6).
                assertColours(tables.waterfall(2), line(2, 11), line(2, 12), line(2, 13), line(2, 14),
                        "AnPal_AIZ1 run 7 waterfall entry");
                assertColours(tables.aiz1Secondary(6), line(3, 12), line(3, 13), line(3, 14),
                        "AnPal_AIZ1 run 7 counters+$02 entry");
            }
        }
        return tables;
    }

    private static void assertAiz2FollowsTheCarriedBank(Tables tables, int carriedCounter1) {
        int[] channelA = channelA();
        for (int call = 1; call <= carriedCounter1; call++) {
            levelLoopPaletteCall();
            assertArrayEquals(channelA, channelA(), "inherited counter1 is still counting at AIZ2 call " + call);
            if (call == 1) {
                assertColours(new int[] {tables.torch(0)}, line(3, 1),
                        "channel B starts on the first AIZ2 call (counters+$08 was zero)");
            }
        }
        levelLoopPaletteCall();
        assertColours(tables.trickle(CARRIED_TRICKLE_ENTRY), line(2, 4), line(2, 8), line(3, 11),
                "the first channel-A run reads the inherited counters+$02");
        assertWater(tables.water(CARRIED_WATER_ENTRY), "and the inherited counter0");
    }

    private static void reloadToAiz2() throws Exception {
        GameServices.level().executeActTransition(SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(0, 1)
                .preserveMusic(true)
                .preserveLevelGamestate(true)
                .showInLevelTitleCard(false)
                .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.PRESERVE_CURRENT)
                .build());
        assertEquals(1, GameServices.level().getCurrentAct());
        assertTrue((GameServices.camera().getX() & 0xFFFF) < 0x3800, "AnPal_PalAIZ2_2/_4 tables");
    }

    private static void skipLevelFade() {
        for (int i = 0; i < 40 && animator().paletteFadeTimerRunning(); i++) {
            step();
        }
        assertFalse(animator().paletteFadeTimerRunning());
    }

    private static void step() {
        fixture.stepIdleFrames(1);
    }

    /**
     * One LevelLoop iteration's palette work through the production entry
     * (LevelFrameRuntimeUpdater -> Sonic3kLevelAnimationManager.update). After the
     * synthetic AIZ reload the carried AIZ1 objects are not shifted as AIZ1BGE_Finish
     * shifts them, so the object pass is not run here.
     */
    private static void levelLoopPaletteCall() {
        var level = GameServices.level();
        level.setFrameCounter(level.getFrameCounter() + 1);
        var registry = GameServices.paletteOwnershipRegistryOrNull();
        if (registry != null) {
            registry.beginFrame();
        }
        animator().update();
    }

    private static List<int[]> record(int frames) {
        List<int[]> rows = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            levelLoopPaletteCall();
            rows.add(channels());
        }
        return rows;
    }

    /** Channel A destinations: line 3 colours 4/8/14, line 4 colours 11-15. */
    private static int[] channelA() {
        return concat(line(2, 4), line(2, 8), line(2, 14), line(3, 11), line(3, 12), line(3, 13),
                line(3, 14), line(3, 15));
    }

    private static int[] channels() {
        return concat(channelA(), line(3, 1));
    }

    private static void assertWater(int[] words, String message) {
        assertColours(words, line(3, 12), line(3, 13), line(3, 14), line(3, 15), message);
    }

    private static void assertColours(int[] segaWords, Object... rest) {
        String message = (String) rest[rest.length - 1];
        for (int i = 0; i < segaWords.length; i++) {
            Palette.Color expected = new Palette.Color();
            expected.fromSegaFormat(segaWords[i]);
            assertArrayEquals(rgb(expected), (int[]) rest[i], message + " colour " + i);
        }
    }

    private static int[] line(int line, int colour) {
        return rgb(GameServices.level().getCurrentLevel().getPalette(line).getColor(colour));
    }

    private static int[] rgb(Palette.Color c) {
        return new int[] {c.r & 0xFF, c.g & 0xFF, c.b & 0xFF};
    }

    private static int[] concat(int[]... parts) {
        return Arrays.stream(parts).flatMapToInt(Arrays::stream).toArray();
    }

    private static Sonic3kLevelAnimationManager animator() {
        return (Sonic3kLevelAnimationManager) GameServices.level().getAnimatedPatternManager();
    }

    /** ROM tables read through the ROM pipeline. */
    private record Tables(byte[] waterfall, byte[] secondary, byte[] water, byte[] trickle, byte[] torch) {
        static Tables load() throws Exception {
            RomByteReader rom = RomByteReader.fromRom(GameServices.rom().getRom());
            return new Tables(
                    rom.slice(Sonic3kConstants.ANPAL_AIZ1_1_ADDR, Sonic3kConstants.ANPAL_AIZ1_1_SIZE),
                    rom.slice(Sonic3kConstants.ANPAL_AIZ1_2_ADDR, Sonic3kConstants.ANPAL_AIZ1_2_SIZE),
                    rom.slice(Sonic3kConstants.ANPAL_AIZ2_1_ADDR, Sonic3kConstants.ANPAL_AIZ2_1_SIZE),
                    rom.slice(Sonic3kConstants.ANPAL_AIZ2_2_ADDR, Sonic3kConstants.ANPAL_AIZ2_2_SIZE),
                    rom.slice(Sonic3kConstants.ANPAL_AIZ2_4_ADDR, Sonic3kConstants.ANPAL_AIZ2_4_SIZE));
        }
        int[] waterfall(int entry) { return words(waterfall, entry * 8, 4); }
        int[] aiz1Secondary(int entry) { return words(secondary, entry * 6, 3); }
        int[] water(int entry) { return words(water, entry * 8, 4); }
        int[] trickle(int entry) { return words(trickle, entry * 6, 3); }
        int torch(int entry) { return words(torch, entry * 2, 1)[0]; }
        private static int[] words(byte[] data, int offset, int count) {
            int[] out = new int[count];
            for (int i = 0; i < count; i++) {
                out[i] = ((data[offset + 2 * i] & 0xFF) << 8) | (data[offset + 2 * i + 1] & 0xFF);
            }
            return out;
        }
    }
}

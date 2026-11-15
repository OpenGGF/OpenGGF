package com.openggf.game.soniccd.special;

import com.openggf.game.soniccd.CdDiscDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native check of special-stage gameplay over the retail Special Stage 1 demo reached from the
 * stage-select menu (run {@code ss1-demo}). The demo's input is the disc's own
 * ({@code Stage1Demo}, scddisasm/src/Special Stage/Main.asm:4776-4782), so the stage reproduces native play with no
 * recorded input; the native log is read from the special stage's own PRG RAM each frame
 * (Sonic's slot at $3C000, UFO slots at $3D000, {@code timerFrames} at $3E317; Sub.asm:87-188)
 * and the HUD digits from native VRAM. Over the demo Sonic jumps, rotates, bounces, breaks all
 * six UFOs (two give speed shoes), the timer stops at the last one and the stage ends when the
 * last explosion clears ({@code CheckUFOPresence}, Sub.asm:311-317, temporary mode).
 */
class TestCdSpecialStageGameplayNative {
    private static CdSpecialStageAssets assets;
    private static Map<Integer, Map<String, String>> frames;
    private static List<Integer> updates;
    private static int stage;

    @BeforeAll
    static void loadDisc() throws Exception {
        try (CdDiscDataSource disc = CdSpecialNative.disc()) {
            assets = CdSpecialStageAssets.load(disc);
        }
    }

    static java.util.stream.Stream<CdSpecialNative.Case> cases() { return CdSpecialNative.cases(); }

    private static void use(CdSpecialNative.Case c) throws Exception {
        Path run = CdSpecialNative.run(c.run());
        frames = CdSpecialNative.frames(run);
        updates = CdSpecialNative.updates(frames, c.fromFrame());
        stage = c.stage();
    }

    private static int bytes(Map<String, String> row, String column, int from, int count) {
        return (int) Long.parseLong(row.get(column).substring(from * 2, (from + count) * 2), 16);
    }

    /** Native Sonic fields after an update, in the order compared. */
    private static int[] nativeSonic(Map<String, String> r) {
        return new int[] {
                Integer.parseInt(r.get("s_routine")), Integer.parseInt(r.get("s_flags")),
                CdSpecialNative.hex(r, "s_x"), CdSpecialNative.hex(r, "s_y"), CdSpecialNative.hex(r, "s_z"),
                bytes(r, "s_raw50", 4, 4), bytes(r, "s_raw10", 4, 2), bytes(r, "s_raw20", 12, 4),
                bytes(r, "s_raw40", 0, 4), bytes(r, "s_raw60", 2, 2), bytes(r, "s_raw60", 0, 1),
                Integer.parseInt(r.get("s_anim")), Integer.parseInt(r.get("s_frame"))};
    }

    private static final String[] FIELDS = {"routine", "flags", "x", "y", "z", "yaw", "speed", "spriteY",
            "spriteYVel", "shoes", "tilt", "anim", "animFrame"};

    private static int[] ours(CdSpecialStageSonic s) {
        return new int[] {s.routine, s.flags & 0xFF, s.x, s.y, s.z, s.yaw, s.speed & 0xFFFF, s.spriteY,
                s.spriteYVel, s.shoes & 0xFFFF, s.tilt & 0xFF, s.anim, s.animation.frame()};
    }

    /**
     * Per update n: the held and tapped bytes its objects read, and the byte its end checks see.
     * The main CPU reads the controller in each pass's first V-blank (and in every fade V-blank
     * before the loop); update n reads the value latched in pass n - 1 and its end checks the one
     * latched in pass n. Demo cases replace the reads after the fade with disc demo bytes.
     */
    private static int[][] inputs(CdSpecialNative.Case c) throws Exception {
        int count = updates.size();
        int[] read = new int[count + 1]; // read[k]: value latched in pass k (k = 0: last fade read)
        byte[] demo = c.demo() ? assets.demo(c.stage()) : null;
        Path run = CdSpecialNative.run(c.run());
        int beforeFade = c.demo() ? 0 : CdSpecialNative.pad(run, updates.get(1) - 2);
        read[0] = c.demo() ? 0 : CdSpecialNative.pad(run, updates.get(1) - 1);
        for (int k = 1; k <= count; k++) {
            if (c.demo()) read[k] = k - 1 < demo.length ? demo[k - 1] & 0xFF : 0;
            else read[k] = k < count ? CdSpecialNative.pad(run, updates.get(k)) : 0;
        }
        int[][] out = new int[count][3];
        for (int n = 1; n < count; n++) {
            int hold = read[n - 1];
            int previous = n >= 2 ? read[n - 2] : beforeFade;
            out[n] = new int[] {hold, hold & ~previous & 0xFF, read[n]};
        }
        return out;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void sonicUfosAndTimerEqualNativeOnEveryUpdateOfTheDemo(CdSpecialNative.Case c) throws Exception {
        use(c);
        CdSpecialStage special = new CdSpecialStage(assets, stage, c.flags(), 0);
        int[][] in = inputs(c);
        special.update(0, 0); // update 0, before the main loop (Main.asm:146)
        special.finishUpdate(0);
        boolean ended = false;
        int compared = 0;
        for (int n = 1; n < updates.size(); n++) {
            special.update(in[n][0], in[n][1]);
            ended = special.finishUpdate(in[n][2]);
            Map<String, String> row = frames.get(updates.get(n));
            String at = "update " + n + " (native frame " + updates.get(n) + ") ";
            int[] expected = nativeSonic(row);
            int[] actual = ours(special.sonic());
            for (int i = 0; i < FIELDS.length; i++) {
                assertEquals(Integer.toHexString(expected[i]), Integer.toHexString(actual[i]), at + "Sonic " + FIELDS[i]);
            }
            assertEquals(Integer.parseInt(row.get("timerFrames")), special.timerFrames, at + "timerFrames");
            String[] slots = row.get("ufos").split("/");
            List<CdSpecialStageUfos.Ufo> ufos = special.ufos().ufos();
            for (int k = 0; k < slots.length; k++) {
                int id = Integer.parseInt(slots[k].substring(0, 2), 16);
                CdSpecialStageUfos.Ufo ufo = ufos.get(k);
                assertEquals(id != 0, ufo.alive(), at + "UFO " + (k + 1) + " present");
                if (id == 0) continue;
                assertEquals(Integer.parseInt(slots[k].substring(2, 4), 16), ufo.routine(), at + "UFO " + (k + 1) + " routine");
                assertEquals(slots[k].substring(4, 12), String.format("%08X", ufo.x()), at + "UFO " + (k + 1) + " x");
                assertEquals(slots[k].substring(12, 20), String.format("%08X", ufo.y()), at + "UFO " + (k + 1) + " y");
            }
            int timeUfoId = Integer.parseInt(row.get("timeufo").substring(0, 2), 16);
            CdSpecialStageUfos.Ufo timeUfo = special.ufos().timeUfo();
            assertEquals(timeUfoId != 0, timeUfo != null && timeUfo.alive(), at + "time UFO present");
            if (timeUfoId != 0) {
                assertEquals(row.get("timeufo").substring(4, 12), String.format("%08X", timeUfo.x()), at + "time UFO x");
                assertEquals(row.get("timeufo").substring(12, 20), String.format("%08X", timeUfo.y()), at + "time UFO y");
            }
            compared++;
            if (ended) break;
        }
        assertEquals(updates.size() - 1, compared, c.ends() ? "ends on the native final update" : "every recorded update");
        if (c.ends()) {
            assertTrue(ended, "the stage ends on its own (SS1: last explosion cleared; SS6: a Start byte; SS8: time)");
            assertEquals(c.ufosAtEnd(), special.ufoCount(), "UFOs left");
            assertEquals(c.ringsAtEnd(), special.rings(), "rings from the ring UFOs");
        }
    }

    /**
     * HUD values drawn natively (timer, rings, UFO count) change in the same pass as the update
     * that changed them: each native change lies within the three frames of that update's pass.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void hudValuesChangeInTheSamePassAsNative(CdSpecialNative.Case c) throws Exception {
        use(c);
        CdSpecialStage special = new CdSpecialStage(assets, stage, c.flags(), 0);
        int[][] in = inputs(c);
        special.update(0, 0);
        special.finishUpdate(0);
        List<int[]> ourChanges = new ArrayList<>();
        int[] last = {special.timer(), special.rings(), special.ufoCount()};
        for (int n = 1; n < updates.size(); n++) {
            special.update(in[n][0], in[n][1]);
            boolean ended = special.finishUpdate(in[n][2]);
            int[] now = {special.timer(), special.rings(), special.ufoCount()};
            if (!java.util.Arrays.equals(now, last)) ourChanges.add(new int[] {updates.get(n), now[0], now[1], now[2]});
            last = now;
            if (ended) break;
        }
        List<int[]> nativeChanges = new ArrayList<>();
        int[] seen = null;
        for (int f = updates.get(1) + 1; frames.containsKey(f); f++) {
            Map<String, String> r = frames.get(f);
            int[] hud = {Integer.parseInt(r.get("hud_timer")), Integer.parseInt(r.get("hud_rings")), Integer.parseInt(r.get("hud_ufos"))};
            if (hud[0] < 0 || hud[1] < 0 || hud[2] < 0) break;
            if (seen != null && !java.util.Arrays.equals(hud, seen)) nativeChanges.add(new int[] {f, hud[0], hud[1], hud[2]});
            seen = hud;
        }
        assertTrue(nativeChanges.size() >= 15, "native HUD changes " + nativeChanges.size());
        for (int i = 0; i < nativeChanges.size(); i++) {
            int[] n = nativeChanges.get(i), o = ourChanges.get(i);
            String at = "HUD change " + i + " at native frame " + n[0];
            assertEquals(n[1], o[1], at + " timer");
            assertEquals(n[2], o[2], at + " rings");
            assertEquals(n[3], o[3], at + " UFOs");
            assertTrue(n[0] >= o[0] && n[0] < o[0] + CdSpecialStageSession.FRAMES_PER_UPDATE,
                    at + " drawn " + (n[0] - o[0]) + " frames after our update");
        }
    }

    /** Negative control: the gameplay comparison rejects input one update late. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void inputOneUpdateLateIsRejected(CdSpecialNative.Case c) throws Exception {
        use(c);
        CdSpecialStage special = new CdSpecialStage(assets, stage, c.flags(), 0);
        int[][] in = inputs(c);
        special.update(0, 0);
        int firstMismatch = -1;
        for (int n = 1; n < updates.size() && firstMismatch < 0; n++) {
            int[] late = n >= 2 ? in[n - 1] : new int[3];
            special.update(late[0], late[1]);
            int[] expected = nativeSonic(frames.get(updates.get(n)));
            int[] actual = ours(special.sonic());
            if (!java.util.Arrays.equals(expected, actual)) firstMismatch = n;
        }
        assertNotEquals(-1, firstMismatch, "a one-update input delay must diverge from native");
    }
}

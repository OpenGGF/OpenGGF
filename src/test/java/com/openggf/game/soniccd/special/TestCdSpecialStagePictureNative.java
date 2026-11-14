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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native check of the special stage picture state: on every native dump frame of the stage loop,
 * the palette equals native CRAM, both planes equal native plane memory, the tile store equals
 * native VRAM (Sonic's art buffer included), the per-line scroll equals the native scroll table and
 * the sprite table equals the native one entry by entry along its links (the zero long that ends
 * the list included; the stale tile and X words behind it are not compared). The native VRAM
 * addresses read here ({@code StageVDPRegs}, scddisasm/src/Special Stage/Main.asm:3496-3515) locate the reference only.
 */
class TestCdSpecialStagePictureNative {
    private static CdSpecialStageAssets assets;

    @BeforeAll
    static void loadDisc() throws Exception {
        try (CdDiscDataSource disc = CdSpecialNative.disc()) {
            assets = CdSpecialStageAssets.load(disc);
        }
    }

    static java.util.stream.Stream<CdSpecialNative.Case> cases() { return CdSpecialNative.cases(); }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void paletteBothPlanesTilesAndScrollEqualNative(CdSpecialNative.Case c) throws Exception {
        Path run = CdSpecialNative.run(c.run());
        Map<Integer, Map<String, String>> frames = CdSpecialNative.frames(run);
        List<Integer> updates = CdSpecialNative.updates(frames, c.fromFrame());
        List<Integer> dumps = new ArrayList<>(CdSpecialNative.dumpFrames(run, "vram"));
        dumps.removeIf(f -> f < updates.get(1) || f > updates.getLast());
        assertTrue(dumps.size() >= 15, "dumps " + dumps);

        CdSpecialStageSession session = new CdSpecialStageSession(assets, c.entry());
        int first = CdSpecialStageSession.FADE_IN_FRAMES + 1;
        List<String> failures = new ArrayList<>();
        for (int k = 1; !session.ended() && updates.get(1) + k - first <= updates.getLast() + 2; k++) {
            int nativeFrame = updates.get(1) + k - first;
            session.frame(c.demo() ? 0 : CdSpecialNative.pad(run, nativeFrame));
            if (!dumps.contains(nativeFrame)) continue;
            byte[] vram = CdSpecialNative.dump(run, "vram", nativeFrame);
            byte[] cram = CdSpecialNative.dump(run, "cram", nativeFrame);
            CdSpecialStageScreen screen = session.screen();
            int palette = 0, a = 0, b = 0, tiles = 0, scroll = 0, sprites = 0;
            for (int i = 0; i < 64; i++) if (CdSpecialNative.word(cram, i * 2) != screen.cram[i]) palette++;
            for (int i = 0; i < 64 * 32; i++) {
                if (CdSpecialNative.word(vram, 0xC000 + i * 2) != screen.planeA[i]) a++;
                if (CdSpecialNative.word(vram, 0xE000 + i * 2) != screen.planeB[i]) b++;
            }
            for (int at = 0; at < 0x10000; at++) {
                boolean tileArea = at < 0xC000 || (at >= 0xD680 && at < 0xE000) || at >= 0xF000;
                if (tileArea && vram[at] != screen.tiles[at]) tiles++;
            }
            boolean line = c.stage() == 0 || c.stage() == 4 || c.stage() == 5;
            for (int l = 0; l < CdSpecialStageScreen.HEIGHT; l++) {
                int at = 0xD000 + (line ? l * 4 : (l >> 3) * 32);
                if (CdSpecialNative.word(vram, at) != (screen.scrollA[l] & 0xFFFF)
                        || CdSpecialNative.word(vram, at + 2) != (screen.scrollB[l] & 0xFFFF)) scroll++;
            }
            StringBuilder spriteDiff = new StringBuilder();
            List<CdSpecialSprites.Entry> table = screen.spriteTable;
            for (int i = 0; i <= table.size() && i < 80; i++) {
                int at = 0xD400 + i * 8;
                int ny = CdSpecialNative.word(vram, at), ns = vram[at + 2] & 0xFF, nl = vram[at + 3] & 0x7F;
                if (i == table.size()) {
                    if (ny != 0 || ns != 0 || nl != 0) { sprites++; spriteDiff.append(" end@" + i); }
                    break;
                }
                CdSpecialSprites.Entry e = table.get(i);
                int nt = CdSpecialNative.word(vram, at + 4), nx = CdSpecialNative.word(vram, at + 6);
                if (ny != e.y() || ns != e.size() || nl != e.link() || nt != e.tile() || nx != e.x()) {
                    sprites++;
                    if (spriteDiff.length() < 400) spriteDiff.append(String.format(" spr%d=%04X,%02X,%02X,%04X,%04X/%04X,%02X,%02X,%04X,%04X",
                            i, ny, ns, nl, nt, nx, e.y(), e.size(), e.link(), e.tile(), e.x()));
                }
            }
            if (palette + a + b + tiles + scroll + sprites > 0) {
                failures.add("frame " + nativeFrame + ": palette " + palette + ", plane A " + a + ", plane B " + b
                        + ", tile bytes " + tiles + ", scroll lines " + scroll + ", sprites " + sprites + "/" + table.size()
                        + spriteDiff + firstDiffs(vram, cram, screen));
            }
        }
        assertEquals(List.of(), failures);
    }

    /**
     * The fade from white ({@code PrepFadeFromWhite}, {@code FadeFromWhite}, scddisasm/src/Special Stage/Main.asm:2474-2565):
     * the sequence of distinct palette states native's palette RAM passes through (colours $10-$13,
     * logged per frame in {@code ss8-terrain}) equals the session's 25 fade frames. Native spends 41
     * frames on them because 16 of its V-blanks find the main CPU still mid-step and take the lag
     * path ({@code VInt_Lag}, scddisasm/src/Special Stage/Main.asm:999-1007); those frames repeat a state and are not modelled.
     */
    @org.junit.jupiter.api.Test
    void fadeFromWhiteStepsEqualNative() throws Exception {
        Path run = CdSpecialNative.run("ss8-terrain");
        Map<Integer, Map<String, String>> frames = CdSpecialNative.frames(run);
        List<Integer> updates = CdSpecialNative.updates(frames, 2500);
        List<String> nativeSteps = new ArrayList<>();
        for (int f = updates.get(0); f < updates.get(1); f++) {
            String pal = frames.get(f).get("m_pal0");
            if (nativeSteps.isEmpty() || !nativeSteps.getLast().equals(pal)) nativeSteps.add(pal);
        }
        CdSpecialStageSession session = new CdSpecialStageSession(assets, new CdSpecialStageEntry(7, CdSpecialStageFlags.SECRET, 0));
        List<String> ours = new ArrayList<>();
        ours.add(String.format("%04X%04X%04X%04X", session.screen().palette[16], session.screen().palette[17],
                session.screen().palette[18], session.screen().palette[19]));
        for (int k = 0; k < CdSpecialStageSession.FADE_IN_FRAMES; k++) {
            session.frame(0);
            String pal = String.format("%04X%04X%04X%04X", session.screen().palette[16], session.screen().palette[17],
                    session.screen().palette[18], session.screen().palette[19]);
            if (!ours.getLast().equals(pal)) ours.add(pal);
        }
        assertEquals(nativeSteps, ours);
    }

    private static String firstDiffs(byte[] vram, byte[] cram, CdSpecialStageScreen screen) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 64 && out.length() < 200; i++) {
            if (CdSpecialNative.word(cram, i * 2) != screen.cram[i]) out.append(String.format(" cram[%d]=%04X/%04X", i, CdSpecialNative.word(cram, i * 2), screen.cram[i]));
        }
        for (int i = 0; i < 64 * 32 && out.length() < 400; i++) {
            if (CdSpecialNative.word(vram, 0xC000 + i * 2) != screen.planeA[i]) out.append(String.format(" A[%d,%d]=%04X/%04X", i % 64, i / 64, CdSpecialNative.word(vram, 0xC000 + i * 2), screen.planeA[i]));
        }
        for (int i = 0; i < 64 * 32 && out.length() < 600; i++) {
            if (CdSpecialNative.word(vram, 0xE000 + i * 2) != screen.planeB[i]) out.append(String.format(" B[%d,%d]=%04X/%04X", i % 64, i / 64, CdSpecialNative.word(vram, 0xE000 + i * 2), screen.planeB[i]));
        }
        for (int at = 0; at < 0xC000 && out.length() < 800; at += 32) {
            if (!java.util.Arrays.equals(vram, at, at + 32, screen.tiles, at, at + 32)) out.append(String.format(" tile %03X", at >> 5));
        }
        for (int l = 0; l < 224 && out.length() < 1000; l += 8) {
            out.append(String.format(" s%d=%04X,%04X/%04X,%04X", l, CdSpecialNative.word(vram, 0xD000 + l * 4),
                    CdSpecialNative.word(vram, 0xD002 + l * 4), screen.scrollA[l] & 0xFFFF, screen.scrollB[l] & 0xFFFF));
        }
        return out.toString();
    }
}

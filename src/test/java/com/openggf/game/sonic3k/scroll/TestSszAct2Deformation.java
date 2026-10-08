package com.openggf.game.sonic3k.scroll;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszAct2Deformation {
    private SszZoneRuntimeState state() {
        var state=new SszZoneRuntimeState(1,PlayerCharacter.KNUCKLES);
        state.setForegroundRoutine(4); state.setSpecialVIntRoutine(4); return state;
    }
    private int[] heights() throws Exception {
        var rom=TestEnvironment.objectServices().romReader();
        int[] heights=new int[37];
        for(int i=0;i<heights.length;i++)heights[i]=rom.readU16BE(0x58C80+i*2);
        assertEquals(0x7FFF,heights[36]); return heights;
    }
    private record NativeSample(int frame, int x, int y, int foreground, int special,
                                int oldDrift, int cloudOffset, int swing, String sha256) { }

    static java.util.stream.Stream<NativeSample> nativeSamples() {
        return java.util.stream.Stream.of(
                new NativeSample(412502, 0, 1601, 0, 0, 23073120, 0, 0,
                        "7079007d51b3b49ba865aa77958b622730aa056079ae0daedffbdd90c39dd465"),
                new NativeSample(412571, 0, 1065, 0, 0, 23355744, 0, 0,
                        "53f279df3221edbaf6fd1baf7c2e466c7f7772b76794e3714dae990d1f562b3c"),
                new NativeSample(412801, 0, 1024, 4, 8, 24297824, 39903, -7,
                        "f6f6b77df7b2a07cbb5582681ee88dfac395e817d28e2bfe8efb2a614dc149ae"),
                new NativeSample(413101, 0, 1024, 4, 8, 25526624, 124803, 0,
                        "dd8af990a8effe64aeb8038c3f3b711048b0e95e0fcb3bc40c0822321de10068"),
                new NativeSample(413501, 161, 1024, 4, 8, 27165024, 238003, 5,
                        "c2da84dc652ac9f9fc23d41db18c3060d3ec379e0b070c0e3ae7812dc227dc1c"),
                new NativeSample(414001, 256, 1024, 4, 8, 29213024, 379503, -1,
                        "d710a16123546bb1317133e30f9f0a07985f342ac3e8211182a467afdabe2b27"));
    }

    /**
     * Read-only BizHawk2.11 movie observations, documented in the SSZ plan's
     * 2026-09-23 native follow-up. This tests the isolated arithmetic, never boots
     * gameplay or imports player state. sub_58D3E reads the drift BEFORE adding
     * $1000, so each sample supplies the observed accumulator minus that increment.
     * Hashes cover native RAM's written table words and entire H_scroll_buffer.
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "native checkpoint {index}")
    @org.junit.jupiter.params.provider.MethodSource("nativeSamples")
    void nativeIntermediateTableAndEveryScanlineMatch(NativeSample sample) throws Exception {
        var state = state();
        state.setForegroundRoutine(sample.foreground());
        state.setSpecialVIntRoutine(sample.special());
        state.setCloudDrift(sample.oldDrift());
        state.setCloudOffsetFixed(sample.cloudOffset());
        state.setEventsBgWord(0, sample.swing());
        SszAct2Deformation.parameters(state, sample.x(), sample.y());
        int[] output = new int[224];
        SszAct2Deformation.compose(state, output, sample.y(), heights());
        var bytes = new java.io.ByteArrayOutputStream();
        var words = new java.io.DataOutputStream(bytes);
        for (int offset = 4; offset < 0x198; offset += 2) {
            if (offset != 0x4E) words.writeShort(state.act2ScrollWord(offset));
        }
        for (int packed : output) words.writeInt(packed);
        String actual = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        assertEquals(sample.sha256(), actual, "native movie frame " + sample.frame());
    }

    /** Declared native stage18 oracle; RAM table + all224 scanlines, no engine gameplay bootstrap. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "2129920,2176,708ce052d64025568c9e310782cef8c519cea11919c42f637c561142448cffad",
        "4294934528,2181,6df66ec3a5ef3743ab9278d30ee04f8f17d009abfb4ad4ea7481467a5790dee5",
        "2147516416,2176,f381af43862c79202f8f33ba2630c2f6cfc1051ecc7736231d61950b4b4351be",
        "2129920,1824,0273c2e1b35214561a87ba453901383b0f4bd7886c8306f28a888ee5e885fce7"
    })
    void islandRampMatchesNativeSignedAndClippedSamples(long seed, int cameraY, String expected) throws Exception {
        var state = state(); state.setCloudDrift((int) seed); state.setBackgroundCameraX(0x5E);
        state.setAct2ScrollWord(4, 0x1234); state.setAct2ScrollWord(0x114, 77);
        SszAct2Deformation.islandParameters(state);
        int[] bands = new int[10];
        for (int i = 0; i < bands.length; i++) bands[i] = TestEnvironment.objectServices().romReader().readU16BE(0x58CCA + i * 2);
        int[] output = new int[224]; SszAct2Deformation.composeIsland(state, output, cameraY, bands);
        var bytes = new java.io.ByteArrayOutputStream(); var words = new java.io.DataOutputStream(bytes);
        for (int offset = 4; offset < 0x198; offset += 2) words.writeShort(state.act2ScrollWord(offset));
        for (int packed : output) words.writeInt(packed);
        assertEquals(expected, java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())));
        assertEquals((int) seed - 0x10000, state.cloudDrift());
    }

    @Test void islandRampUsesOldAccumulatorAndRomPerLineBand() throws Exception {
        var state = state(); state.setForegroundRoutine(0x18);
        state.setCloudDrift(0x208000); state.setBackgroundCameraX(0x5E);
        state.setAct2ScrollWord(4, 0x1234); state.setAct2ScrollWord(0x114, 77);
        SszAct2Deformation.islandParameters(state);
        assertEquals(0x1F8000, state.cloudDrift());
        assertEquals(0x1234, state.act2ScrollWord(4), "loc_58F52 preserves the first band");
        for (int line = 0; line < 128; line++) assertEquals(1 + line / 4, state.act2ScrollWord(0x14 + line * 2));
        int[] negated = {-4, -1, -5, -2, -6, -1, -3};
        for (int i = 0; i < negated.length; i++) assertEquals(negated[i], state.act2ScrollWord(6 + i * 2));
        int[] bands = new int[10];
        for (int i = 0; i < bands.length; i++) bands[i] = TestEnvironment.objectServices().romReader().readU16BE(0x58CCA + i * 2);
        assertEquals(0x8080, bands[8]);
        int[] output = new int[224];
        SszAct2Deformation.composeIsland(state, output, 0x880, bands);
        for (int line = 0; line < 224; line++) {
            assertEquals(line < 128 ? -(1 + line / 4) : -77, (short) (output[line] >>> 16));
            assertEquals(-0x5E, (short) output[line]);
        }
        SszAct2Deformation.composeIsland(state, output, 0x885, bands);
        assertEquals(-2, (short) (output[0] >>> 16), "clipped dynamic band advances by skipped scanlines");
        assertEquals(-77, (short) (output[123] >>> 16));
    }

    @Test void longDriftUsesOldAccumulatorAndExactForegroundScatter() {
        var state=state(); state.setCloudDrift(0x12345000);
        SszAct2Deformation.parameters(state,0x1000,0x420);
        assertEquals(0x12346000,state.cloudDrift());
        assertEquals(0,state.act2ScrollWord(4));
        assertEquals(4724,state.act2ScrollWord(6));
        assertEquals(9512,state.act2ScrollWord(0xC));
        assertEquals(14300,state.act2ScrollWord(8));
        assertEquals(19089,state.act2ScrollWord(0x18));
        for(int offset=0x2A;offset<0x3C;offset+=2)assertEquals(0x1000,state.act2ScrollWord(offset));
    }
    @Test void postDefeatForegroundRemapUsesOrderedWordWrites() {
        var state = state();
        state.setForegroundRoutine(8);
        state.setCloudDrift(0x12345000);
        SszAct2Deformation.parameters(state, 0x1000, 0x420);
        // loc_58E3A's offsets are relative to HScroll_table+$004. Values come
        // from the preceding signed 16.16 scatter, including word wrap.
        int[] offsets = {0x2A, 0x2C, 0x2E, 0x30, 0x32, 0x34, 0x36, 0x38};
        int[] expected = {-22506, -17717, -12929, -8141, -12929, -27294, 28665, 19089};
        for (int i = 0; i < offsets.length; i++) {
            assertEquals(expected[i], state.act2ScrollWord(offsets[i]),
                    "HScroll_table+$" + Integer.toHexString(offsets[i]));
        }
    }

    @Test void wavesPreserveSignAndForwardCopyAliasingAtNativeEdge() {
        for(int sign:new int[]{1,-1}) {
            var state=state(); state.setEventsBgWord(0,4*sign);
            SszAct2Deformation.parameters(state,0,0x420);
            assertEquals(0x100,state.backgroundCameraY());
            assertEquals(2*sign,state.act2ScrollWord(0x50));
            assertEquals(-2*sign,state.act2ScrollWord(0x14E));
            assertEquals(4*sign,state.act2ScrollWord(0x150));
            assertEquals(-4*sign,state.act2ScrollWord(0x16E));
            assertEquals(264-sign,state.act2ScrollWord(0x170));
            assertEquals(528-sign,state.act2ScrollWord(0x194),"overlapping last-two source reads see first destination writes");
            short[] nativeColumns=SszAct2Deformation.columns(state,320);
            short[] wide=SszAct2Deformation.columns(state,800);
            assertArrayEquals(nativeColumns,Arrays.copyOf(wide,20));
            for(int i=20;i<wide.length;i++)assertEquals(nativeColumns[19],wide[i]);
        }
    }
    @Test void romBandsAndBackgroundWindowCoverEveryScanline() throws Exception {
        var state=state(); state.setCloudDrift(0x10000); state.setEventsBgWord(0,4);
        SszAct2Deformation.parameters(state,0,0x370);
        int[] output=new int[224]; Arrays.fill(output,0xDEADBEEF);
        SszAct2Deformation.compose(state,output,0x370,heights());
        assertEquals(0,output[15]>>16);
        assertEquals(-1,output[16]>>16,"word_58C80 first $380-line band ends here");
        int bgY=state.backgroundCameraY();
        for(int line=0;line<224;line++) {
            assertNotEquals(0xDEADBEEF,output[line]);
            int worldY=bgY+line;
            int expected=worldY>=0x100&&worldY<0x180
                    ?state.act2ScrollWord(0x50+(worldY-0x100)*2)-94:-94;
            assertEquals((short)expected,(short)output[line],"BG line "+line);
        }
    }
    @Test void capturedTableReplaysDriftAndGatedColumnsWithoutBorrowingAct1State() throws Exception {
        var state=state(); state.setEventsBgWord(0,-3);
        SszAct2Deformation.parameters(state,64,0x431);
        byte[] saved=state.captureBytes();
        SszAct2Deformation.parameters(state,80,0x432);
        int[] expected=new int[224]; SszAct2Deformation.compose(state,expected,0x432,heights());
        short[] columns=SszAct2Deformation.columns(state,800);
        state.restoreBytes(saved); SszAct2Deformation.parameters(state,80,0x432);
        int[] actual=new int[224]; SszAct2Deformation.compose(state,actual,0x432,heights());
        assertArrayEquals(expected,actual); assertArrayEquals(columns,SszAct2Deformation.columns(state,800));
        state.setSpecialVIntRoutine(0); assertNull(SszAct2Deformation.columns(state,320));
    }
}

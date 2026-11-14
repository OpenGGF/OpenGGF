package com.openggf.game.soniccd.title;

import com.openggf.game.soniccd.device.CdStampGraphics;
import com.openggf.game.soniccd.device.CdStampRenderRequest;
import com.openggf.game.soniccd.device.CdStampRenderState;

import java.util.Arrays;

/**
 * The title's cloud layer: the sub-CPU program {@code Title Screen/Sub.asm} as game logic. It keeps
 * the camera of the graphics operation, builds the perspective trace table ({@code GenGfxTraceTbl})
 * and renders the stamp map into the 256x96 image through the carried stamp-graphics device. The
 * image is the 4bpp tile layout the main CPU copies to VRAM ({@code IMGWTILE} columns of
 * {@code IMGHTILE} tiles, column-major).
 */
public final class CdTitleClouds {
    /** {@code _Common.inc}: TRACETBL $1C800, IMGBUFFER $1D000, STAMPMAP $20000, 256x96 image. */
    private static final int TRACE_TABLE = 0x1C800, IMAGE_BUFFER = 0x1D000, STAMP_MAP = 0x20000;
    public static final int IMAGE_WIDTH = 256, IMAGE_HEIGHT = 96;
    public static final int IMAGE_BYTES = (IMAGE_WIDTH / 8) * (IMAGE_HEIGHT / 8) * 32;
    /** {@code InitGfxOperation} (scddisasm/src/Title Screen/Sub.asm:270-280): 32x32 stamps, 4096x4096 map, repeated; image registers. */
    private static final CdStampRenderRequest REQUEST = CdStampRenderRequest.fromRegisterWords(
            0b111, 0, STAMP_MAP / 4, IMAGE_HEIGHT / 8 - 1, IMAGE_WIDTH);

    private final byte[] wordRam = new byte[CdStampGraphics.WORD_RAM_BYTES];
    private final int[] sine;
    // gfxVars (Sub.asm:15-49): InitGfxParams (scddisasm/src/Title Screen/Sub.asm:159-166) and InitGfxOperation (scddisasm/src/Title Screen/Sub.asm:278-279).
    private int camX = 0x480, camY = 0x480, camZ = 0x140, pitch = 0x44, yaw = 0x100;
    private static final int FOV = 0x80, CENTER = -0x40;
    private int renders;

    public CdTitleClouds(CdTitleAssets assets) {
        sine = assets.sineTable;
        // Program start (Sub.asm:96-112): clear Word RAM, fill $30000-$37FFF with $01080108, LoadCloudsData.
        for (int i = 0x30000; i < 0x38000; i += 4) {
            wordRam[i] = 0x01; wordRam[i + 1] = 0x08; wordRam[i + 2] = 0x01; wordRam[i + 3] = 0x08;
        }
        // LoadCloudsData (scddisasm/src/Title Screen/Sub.asm:664-673): stamps at Word RAM $200, the stamp map at STAMPMAP.
        System.arraycopy(assets.cloudStamps, 0, wordRam, 0x200, assets.cloudStamps.length);
        System.arraycopy(assets.cloudStampMap, 0, wordRam, STAMP_MAP, assets.cloudStampMap.length);
    }

    /**
     * One {@code MainLoop} graphics command (Sub.asm:137-153): GetGfxSines, RunGfxOperation (trace
     * table and the stamp render), then ControlClouds with player 2's held buttons. Returns the image.
     */
    public byte[] render(int p2Held) {
        writeTraceTable();
        CdStampRenderState state = CdStampRenderState.fromStartRegisterWords(REQUEST, TRACE_TABLE / 4,
                IMAGE_BUFFER / 4, 0, IMAGE_HEIGHT);
        CdStampGraphics.executeLines(wordRam, state, IMAGE_HEIGHT);
        controlClouds(p2Held);
        renders++;
        return Arrays.copyOfRange(wordRam, IMAGE_BUFFER, IMAGE_BUFFER + IMAGE_BYTES);
    }

    public int renders() { return renders; }
    public int cameraY() { return camY; }

    /** ControlClouds (scddisasm/src/Title Screen/Sub.asm:173-229); player 2 only reaches the sub CPU after the clouds cheat. */
    private void controlClouds(int p2) {
        if ((p2 & 0x40) != 0) yaw = (yaw - 4) & 0x1FF;      // A
        if ((p2 & 0x20) != 0) yaw = (yaw + 4) & 0x1FF;      // C
        camY = (camY + ((p2 & 0x10) != 0 ? 4 : 2)) & 0xFFFF; // B scrolls fast
        if ((p2 & 0x01) != 0) { pitch -= 2; if (pitch < 0) pitch = 0; }               // subq; bcc; clr
        if ((p2 & 0x02) != 0) { pitch += 2; if (pitch >= 0x50) pitch = 0x50; }
        if ((p2 & 0x04) != 0) { camZ += 8; if (camZ >= 0x7FF) camZ = 0x7FF; }
        if ((p2 & 0x08) != 0) { camZ -= 8; if (camZ < 0x80) camZ = 0x80; }
    }

    /** GetSine/GetCosine (Sub.asm:496-517). */
    private int sin(int value) {
        value &= 0x1FF;
        int index = value;
        if ((value & 0x80) != 0) index = ~index;
        int result = (short) sine[index & 0x7F];
        return (value & 0x100) != 0 ? (short) -result : result;
    }

    private int cos(int value) { return sin(value + 0x80); }

    /** GenGfxTraceTbl (scddisasm/src/Title Screen/Sub.asm:334-487), with 68000 word/long widths and DIVS overflow kept. */
    private void writeTraceTable() {
        int pitchSin = sin(pitch), pitchCos = cos(pitch), yawSin = sin(yaw), yawCos = cos(yaw);
        int d0 = (short) (camX << 3), d1 = (short) (camY << 3);
        int pcYs = (short) ((pitchCos * yawSin) >> 5);
        int pcYc = (short) ((pitchCos * yawCos) >> 5);
        int fovPitchSin = (short) (FOV * pitchSin);                 // muls.w keeps the low word for the next muls.w
        int psYsFov = (fovPitchSin * yawSin) >> 5;
        int psYcFov = (fovPitchSin * yawCos) >> 5;
        int pcFov = FOV * pitchCos;
        int a1 = (-128 * yawCos) << 3, a2 = (-128 * yawSin) << 3;
        int a3 = (127 * yawCos) << 3, a4 = (127 * yawSin) << 3;
        int fovCenter = (short) (FOV + CENTER);
        int centerX = (short) ((fovCenter * (short) ((pitchSin * yawSin) >> 5)) >> 8);
        int centerY = (short) ((fovCenter * (short) ((pitchSin * yawCos) >> 5)) >> 8);
        int out = TRACE_TABLE;
        int line = -3;
        for (int i = 0; i < IMAGE_HEIGHT; i++, line--) {
            int z = ((short) line * pitchSin + pcFov) >> 5;
            if (z == 0) z = 1;
            int divisor = (short) z;
            int xStart = (short) (project(a3 - pcYs * line + psYsFov, divisor) + d0 - centerX);
            int yStart = (short) (project(a4 + pcYc * line - psYcFov, divisor) + d1 + centerY);
            int xEnd = (short) (project(a1 - pcYs * line + psYsFov, divisor) + d0 - centerX);
            int yEnd = (short) (project(a2 + pcYc * line - psYcFov, divisor) + d1 + centerY);
            out = word(out, xStart);
            out = word(out, yStart);
            out = word(out, xEnd - xStart);
            out = word(out, yEnd - yStart);
        }
    }

    /** {@code asr.l #8; muls.w camZ; divs.w z}: the low word, or the dividend's low word on overflow. */
    private int project(int numerator, int divisor) {
        int product = (short) (numerator >> 8) * camZ;
        if (divisor == 0) throw new ArithmeticException("Trace-table Z reached zero");
        int quotient = product / divisor;
        return quotient < Short.MIN_VALUE || quotient > Short.MAX_VALUE ? (short) product : quotient;
    }

    private int word(int at, int value) {
        wordRam[at] = (byte) (value >> 8);
        wordRam[at + 1] = (byte) value;
        return at + 2;
    }
}

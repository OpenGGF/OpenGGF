package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aPlayerAssets;

import java.util.Objects;

/**
 * Scalar CalcSine/CalcAngle operations over the supplied R11A player's immutable asset bytes.
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Functions (Misc).asm:69-219. These routines have no FixBugs alternative;
 * their shipped byte/word arithmetic is retained, without libm or shared lookup tables.
 * This class neither authenticates media nor owns player state or engine integration.
 */
public final class CdPlayerMath {
    /** Signed native d0.w sine and d1.w cosine values, without floating-point normalization. */
    public record SineCosine(int sine, int cosine) { }

    private final CdR11aPlayerAssets assets;

    /** Production callers supply the authenticated loader's player assets; there is no default table. */
    public CdPlayerMath(CdR11aPlayerAssets assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    /**
     * CalcSine (Functions (Misc).asm:69-76): only the low angle byte is consumed.
     * The cosine read uses words 64..319, including the asset's actual 64-word extension.
     *
     * @param angle native angle register contents; upper bits are ignored
     * @return the signed sine and cosine words read from the supplied assets
     */
    public SineCosine calcSine(int angle) {
        int index = angle & 0xFF;
        int cosine = assets.sineWord(index + 0x40);
        return new SineCosine(assets.sineWord(index), cosine);
    }

    /**
     * CalcAngle (Functions (Misc).asm:119-172), retaining the returned ANGLE WORD.
     * Inputs are narrowed to signed low words before the zero/sign/magnitude decisions.
     * The result is an unsigned native word, not an automatically projected byte;
     * for example a small negative Y with positive X can return {@code 0x0100}.
     *
     * @param xWord native d1 register contents; only the low signed word is consumed
     * @param yWord native d2 register contents; only the low signed word is consumed
     * @return native d0.w as a nonnegative int, including 0x0100 when applicable
     */
    public int calcAngle(int xWord, int yWord) {
        int x = (short) xWord;
        int y = (short) yWord;
        // CalcAngle/.AngleZero (119-127,169-172): test the original low words.
        if (x == 0 && y == 0) { return 0x40; }

        // .NotNeg1/.NotNeg2 (129-140): NEG.W $8000 stays $8000, then
        // CMP.W/BCC compares unsigned magnitudes. These ints retain zero-extended words.
        int magnitudeX = x < 0 ? (-x) & 0xFFFF : x;
        int magnitudeY = y < 0 ? (-y) & 0xFFFF : y;
        int angle;
        if (magnitudeY >= magnitudeX) {
            // .YGreater (147-151): LSL.L, DIVU.W, then SUB.B from $40.
            // Equal nonzero magnitudes address byte 256, not byte 255 or zero.
            int quotient = (magnitudeX << 8) / magnitudeY;
            angle = (0x40 - assets.arcTanByte(quotient)) & 0xFF;
        } else {
            // CalcAngle (141-145): zero-extended MOVE.B from the actual asset table.
            int quotient = (magnitudeY << 8) / magnitudeX;
            angle = assets.arcTanByte(quotient);
        }

        // .CheckQuadrant/.GotHalf (153-163): each NEG/ADD is word-width.
        if (x < 0) {
            angle = (-angle) & 0xFFFF;
            angle = (angle + 0x80) & 0xFFFF;
        }
        if (y < 0) {
            angle = (-angle) & 0xFFFF;
            angle = (angle + 0x100) & 0xFFFF;
        }
        return angle;
    }

    /** Explicit low-byte projection for a caller whose native destination is a byte. */
    public int calcAngleByte(int xWord, int yWord) {
        return calcAngle(xWord, yWord) & 0xFF;
    }
}

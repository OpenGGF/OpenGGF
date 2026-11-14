package com.openggf.game.soniccd.special;

/**
 * The special stage title card ({@code ObjTitleCardBar} and {@code ObjTitleCardText},
 * Special Stage/Objects/Title Card/Main.asm). The bar's timer releases Sonic: at 50 updates
 * left it starts his pose, when it leaves the screen it hands him the ground routine, and
 * three updates later it marks the stage active.
 */
final class CdSpecialStageTitleCard {
    int barRoutine;
    int barY;
    int barTimer;
    boolean barVisible = true;
    boolean barAlive = true;
    int textRoutine;
    int textX;
    int textTimer;
    boolean textAlive = true;
    CdSpecialSprites.Sprite barSprite;
    CdSpecialSprites.Sprite textSprite;

    /** {@code ObjTitleCardBar} (Title Card/Main.asm:82-155); runs before Sonic in the update. */
    void updateBar(CdSpecialStage stage) {
        if (!barAlive) return;
        switch (barRoutine) {
            case 0 -> { barY = -96 + 128; barSprite.set(0); barRoutine = 1; moveDown(); }
            case 1 -> moveDown();
            case 2 -> {
                barTimer = (barTimer - 1) & 0xFFFF;
                if (barTimer == 0) barRoutine = 3;
                if (barTimer == 50) stage.sonic().routine = CdSpecialStageSonic.START_2;
            }
            case 3 -> {
                barY = (barY - 32) & 0xFFFF;
                if ((short) barY < 0) {
                    barTimer = 3;
                    barVisible = false;
                    barSprite.flags |= CdSpecialSprites.NO_DRAW;
                    barRoutine = 4;
                    stage.sonic().routine = CdSpecialStageSonic.GROUND;
                }
            }
            default -> {
                barTimer = (barTimer - 1) & 0xFFFF;
                if (barTimer == 0) {
                    barAlive = false;
                    stage.stageInactive = false;
                }
            }
        }
    }

    private void moveDown() {
        barY = (barY + 32) & 0xFFFF;
        if (Integer.compareUnsigned(barY, 112 + 128) >= 0) {
            barY = 112 + 128;
            barTimer = 80;
            barRoutine = 2;
        }
    }

    /** {@code ObjTitleCardText} (Title Card/Main.asm:11-71). */
    void updateText() {
        if (!textAlive) return;
        switch (textRoutine) {
            case 0 -> { textX = 328 + 128; textSprite.set(0); textRoutine = 1; moveLeft(); }
            case 1 -> moveLeft();
            case 2 -> { textTimer = (textTimer - 1) & 0xFFFF; if (textTimer == 0) textRoutine = 3; }
            default -> {
                textX = (textX + 32) & 0xFFFF;
                if (Integer.compareUnsigned(textX, 336 + 128) > 0) textAlive = false;
            }
        }
    }

    private void moveLeft() {
        textX = (textX - 32) & 0xFFFF;
        if (Integer.compareUnsigned(textX, 184 + 128) <= 0) {
            textX = 184 + 128;
            textTimer = 80;
            textRoutine = 2;
        }
    }
}

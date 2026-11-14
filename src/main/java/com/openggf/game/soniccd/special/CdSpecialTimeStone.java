package com.openggf.game.soniccd.special;

/**
 * The Time Stone that drops into Sonic's hands after the last UFO ({@code ObjTimeStone},
 * Special Stage/Objects/Time Stone/Main.asm:7-77): waits 30 updates, falls 4 pixels per update
 * from screen row -16 to 208, hands Sonic his holding pose and 60 updates later marks the stone
 * retrieved. The sparkles that accompany it are presentation only.
 */
final class CdSpecialTimeStone {
    boolean present;
    int routine;
    int timer;
    int spriteY;
    CdSpecialSprites.Sprite sprite;
    CdSpecialSprites.Sprite sparkle1;
    CdSpecialSprites.Sprite sparkle2;
    boolean sparkles;
    boolean sparklesDelete;
    private int sparkleRoutine;

    /** {@code ObjSonic_TimeStone2} spawns the stone and both sparkles (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:360-362). */
    void spawn() {
        present = true;
        routine = 0;
        sparkles = true;
        sparklesDelete = false;
        sparkleRoutine = 0;
    }

    /** {@code ObjTimeStone}'s draw, then {@code ObjSparkle1}/{@code 2} (Time Stone/Main.asm:7-150). */
    void draw(CdSpecialSprites sprites) {
        if (present) {
            sprite.x = 129 + 128;
            sprite.y = spriteY;
            sprites.draw(sprite);
        }
        if (!sparkles) return;
        if (sparkleRoutine == 0) { sparkle1.set(1); sparkle2.set(2); sparkleRoutine = 1; }
        sparkle1.x = sprite.x;
        sparkle1.y = (short) (spriteY - 16);
        sprites.draw(sparkle1);
        sparkle2.x = sprite.x;
        sparkle2.y = (short) (spriteY - 32);
        sprites.draw(sparkle2);
        if (sparklesDelete) sparkles = false;
    }

    void update(CdSpecialStage stage) {
        if (!present) return;
        switch (routine) {
            case 0 -> {
                spriteY = -16 + 128;
                sprite.set(0);
                timer = 30;
                routine = 1;
                waitFall();
            }
            case 1 -> waitFall();
            case 2 -> {
                spriteY = (spriteY + 4) & 0xFFFF;
                if (Integer.compareUnsigned(spriteY, 208 + 128) >= 0) {
                    routine = 3;
                    sparklesDelete = true;
                    timer = 60;
                    stage.sonic().routine = CdSpecialStageSonic.TIME_STONE_11;
                    stage.playSound(CdSpecialSound.TIME_STONE);
                }
            }
            default -> {
                timer = (timer - 1) & 0xFFFF;
                if (timer == 0) stage.gotTimeStone = true;
            }
        }
    }

    private void waitFall() {
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) routine = 2;
    }
}

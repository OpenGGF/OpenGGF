package com.openggf.game.soniccd.special;

/**
 * Sonic in the special stage ({@code ObjSonic}, Special Stage/Objects/Sonic/Main.asm).
 * USA arithmetic only: every {@code REGION<>EUROPE} branch is the one modelled; the European
 * constants (scaled speeds, gravity, rotation and the 60/50 move factor, Sonic/Main.asm:197-255,
 * 1053-1191) are not built.
 *
 * <p>Positions are 16.16 longs as the object holds them (X, Y in the 4096-pixel stamp map, Z the
 * camera height); the sprite's screen Y and its velocity are also 16.16. The routine numbers
 * are the object's own ({@code ObjSonic_Index}, scddisasm/src/Special Stage/Objects/Sonic/Main.asm:27-51).
 */
public final class CdSpecialStageSonic {
    // Routines (ObjSonic_Index, Sonic/Main.asm:27-51).
    static final int INIT = 0, GROUND = 1, JUMP = 2, FLOAT = 3, BUMPED = 4, SINK = 5, SINK2 = 6, HURT = 7,
            TIME_STONE_1 = 8, TIME_STONE_11 = 18, BOOSTED = 0x13, START_1 = 0x14, START_2 = 0x15,
            START_3 = 0x16, START_4 = 0x17;
    // oFlags bits used by ObjSonic.
    static final int FLAG_NO_ANIMATE = 1 << 1, FLAG_NO_DRAW = 1 << 2, FLAG_NO_MOVE = 1 << 3,
            FLAG_GROUND_ANIM = 1 << 4, FLAG_BUMP = 1 << 5, FLAG_FLOAT = 1 << 6, FLAG_AIR = 1 << 7;
    /** Sprite Y on the ground: 216 + 128 (Sonic/Main.asm:141, 226-229). */
    static final int GROUND_SPRITE_Y = 216 + 128;
    static final int GROUND_Z = 0x160;

    private final CdSpecialStage stage;
    int routine;
    int flags;
    int x;
    int y;
    int z;
    int spriteX;
    int spriteY;
    int spriteYVel;
    int xVel;
    int yVel;
    int speed;
    int bumpTime;
    int topSpeed;
    int pitch;
    int yaw;
    int tilt;
    int timer;
    int shoes;
    int anim;
    int stampCenter, stampTopLeft, stampTopRight, stampBottomRight, stampBottomLeft, stampOrientation;
    int ufoCollision;
    final CdSpecialAnimation animation = new CdSpecialAnimation();

    CdSpecialStageSonic(CdSpecialStage stage) {
        this.stage = stage;
    }

    /** {@code ObjSonic} (Sonic/Main.asm:55-131): one object update. */
    void update() {
        int routineAtEntry = routine;
        handleSpeed();
        flags &= ~(FLAG_BUMP | FLAG_NO_DRAW);
        topSpeed = 0xA00;
        runRoutine(routineAtEntry);
        if ((flags & FLAG_NO_MOVE) == 0) {
            if ((flags & (FLAG_AIR | FLAG_FLOAT)) == 0 && !stage.stageInactive) {
                getStamps();
                stampCollide();
            }
            if ((flags & FLAG_BUMP) == 0) move();
            // .CheckLeftBound / .CheckTopBound / .CheckBottomBound (Sonic/Main.asm:90-108).
            x = clampLow(x, 0x340);
        }
        x = clampHigh(x, 0x1000 - 0x340);
        y = clampLow(y, 0x340);
        y = clampHigh(y, 0x1000 - 0x340);
        // .SetPosition (Sonic/Main.asm:110-116).
        stage.view().setCamera(x >> 16, y >> 16, z >> 16, pitch, yaw >> 16);
        if (routine != HURT && routine != BOOSTED) {
            tilt();
            animate();
        }
        stage.drawSonic(); // DrawObject, ObjSonic_LoadArt (Sonic/Main.asm:126-128)
        ufoCollision = 0;
    }

    /** {@code cmpi.w #limit,oX; bcc} then {@code move.w #limit,oX}: the high word only. */
    private static int clampLow(int position, int limit) {
        return (position >>> 16) < limit ? (limit << 16) | (position & 0xFFFF) : position;
    }

    private static int clampHigh(int position, int limit) {
        return (position >>> 16) >= limit ? (limit << 16) | (position & 0xFFFF) : position;
    }

    private void runRoutine(int r) {
        switch (r) {
            case INIT -> init();
            case GROUND -> { rotate(); checkJump(); checkWin(); }
            case JUMP -> jump();
            case FLOAT -> floatUp();
            case BUMPED -> bumped();
            case SINK -> { spriteY += 4 << 16; if ((spriteY >> 16) >= 320 + 128) routine = SINK2; }
            case SINK2, START_1, START_4 -> { }
            case HURT -> { if (decTimer() == 0) { routine = GROUND; animation.set(this, 0); } }
            case BOOSTED -> boosted();
            case START_2 -> { animation.set(this, 0x2C); routine = START_3; timer = 5; }
            case START_3 -> { if (decTimer() == 0) { animation.set(this, 0xA); routine = START_4; } }
            default -> timeStone(r);
        }
    }

    /** {@code subq.b #1,oPlayerTimer}: the byte timer, returning its new value. */
    private int decTimer() {
        timer = (timer - 1) & 0xFF;
        return timer;
    }

    /** {@code ObjSonic_Init} then {@code ObjSonic_Start1} (Sonic/Main.asm:137-155). */
    private void init() {
        spriteX = 128 + 128;
        spriteY = GROUND_SPRITE_Y << 16;
        animation.set(this, 9);
        routine = START_1;
        speed = 0;
        int[] start = stage.assets().startPosition(stage.stageId());
        x = start[0] << 16;
        y = start[1] << 16;
        yaw = start[2] << 16;
        z = GROUND_Z << 16;
        pitch = 0x80;
    }

    /** {@code ObjSonic_Jump} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:197-242), USA gravity $A000. */
    private void jump() {
        spriteY += spriteYVel;
        spriteYVel += 0xA000;
        if (stage.jumpTimer != 0) {
            spriteYVel += 0xA000;
            stage.jumpTimer--;
            if ((stage.playerHold & 0x70) != 0) spriteYVel -= 0xA000;
        }
        land(FLAG_AIR);
        rotate();
        z = ((((GROUND_SPRITE_Y - (spriteY >> 16)) << 2) + GROUND_Z) & 0xFFFF) << 16 | (z & 0xFFFF);
    }

    /** {@code ObjSonic_Float} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:248-274), USA gravity $2000. */
    private void floatUp() {
        spriteY += spriteYVel;
        spriteYVel += 0x2000;
        land(FLAG_FLOAT);
        rotate();
        z = ((((GROUND_SPRITE_Y - (spriteY >> 16)) << 2) + GROUND_Z) & 0xFFFF) << 16 | (z & 0xFFFF);
    }

    /** {@code .CheckLand} (Sonic/Main.asm:225-232, 258-264): unsigned compare of the screen row. */
    private void land(int airFlag) {
        if (Integer.compareUnsigned((spriteY >>> 16), GROUND_SPRITE_Y) >= 0) {
            routine = GROUND;
            spriteY = GROUND_SPRITE_Y << 16;
            spriteYVel = 0;
            flags &= ~airFlag;
            z = (GROUND_Z << 16) | (z & 0xFFFF);
        }
    }

    /** {@code ObjSonic_Bumped} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:280-298). */
    private void bumped() {
        bumpTime = (bumpTime - 1) & 0xFFFF;
        if (bumpTime == 0) {
            routine = GROUND;
            xVel = 0;
            yVel = 0;
        } else {
            x += xVel;
            y += yVel;
        }
        rotate();
        checkJump();
        checkWin();
    }

    /** {@code ObjSonic_Boosted} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:508-528). */
    private void boosted() {
        bumpTime = (bumpTime - 1) & 0xFFFF;
        if (bumpTime == 0) {
            routine = GROUND;
            xVel = 0;
            yVel = 0;
            animation.set(this, 0);
        } else {
            x += xVel;
            y += yVel;
        }
        rotate();
        checkJump();
    }

    /** {@code ObjSonic_TimeStone1}-{@code 11} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:337-502). */
    private void timeStone(int r) {
        switch (r) {
            case 8 -> {
                if (Integer.compareUnsigned(spriteY >>> 16, GROUND_SPRITE_Y) >= 0) {
                    timer = 60;
                    animation.set(this, 0xA);
                    routine = 9;
                    stage.stageInactive = true;
                    speed = 0;
                } else {
                    rotate();
                    checkJump();
                }
            }
            case 9 -> {
                if (decTimer() == 0) {
                    stage.spawnTimeStone();
                    routine = 0xA;
                    timer = 6;
                }
            }
            case 0xA, 0xB, 0xC, 0xD, 0xE, 0xF, 0x10 -> {
                // TimeStone3-9 each turn left 8 per update for a timed step, then set the next pose.
                stage.scrollFlags |= 1 << 2;
                yaw = ((((yaw >> 16) - 8) & 0x1FF) << 16) | (yaw & 0xFFFF);
                if (decTimer() == 0) {
                    int[] next = TIME_STONE_STEPS[r - 0xA];
                    routine = next[0];
                    if (next[1] != 0) timer = next[1];
                    animation.set(this, next[2]);
                }
            }
            case 0x11 -> { }
            case 0x12 -> animation.set(this, 0x2B);
            default -> throw new IllegalStateException("Sonic routine " + r);
        }
    }

    // Next routine, timer and animation of TimeStone3-9 (Sonic/Main.asm:372-490).
    private static final int[][] TIME_STONE_STEPS = {
            {0xB, 4, 0x24}, {0xC, 5, 0x25}, {0xD, 4, 0x26}, {0xE, 5, 0x27},
            {0xF, 4, 0x28}, {0x10, 5, 0x29}, {0x11, 0, 0x2A}};

    /** {@code ObjSonic_CheckWin} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:559-576). */
    private void checkWin() {
        if (stage.ufoCount() != 0) return;
        routine = TIME_STONE_1;
        stage.deleteTimeUfo();
    }

    /** {@code ObjSonic_GetStamps} (scddisasm/src/Special Stage/Sub.asm:1407-1446). */
    private void getStamps() {
        CdStampFloor floor = stage.floor();
        int px = x >> 16, py = y >> 16;
        int centre = floor.stampAt(px, py);
        stampCenter = centre & 0xFF;
        stampOrientation = ((centre >>> 16) >>> 12) & 0xF;
        stampTopLeft = floor.stampAt(px - 8, py - 8) & 0xFF;
        stampTopRight = floor.stampAt(px + 8, py - 8) & 0xFF;
        stampBottomRight = floor.stampAt(px + 8, py + 8) & 0xFF;
        stampBottomLeft = floor.stampAt(px - 8, py + 8) & 0xFF;
    }

    /** {@code ObjSonic_StampCollide} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:582-617). */
    private void stampCollide() {
        checkBumper();
        if (stage.ufoCount() == 0) return;
        int type = stampCenter < 10 ? stampCenter : 0;
        switch (type) {
            case 2 -> { // ObjSonic_Fan (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:621-634)
                routine = FLOAT;
                spriteYVel = -0x40000;
                flags |= FLAG_FLOAT;
                stage.playSound(CdSpecialSound.FAN);
                stage.deleteSplash();
            }
            case 3 -> { // ObjSonic_Water (Sonic/Main.asm:638-648)
                if (!stage.timeStopped) {
                    stage.spawnSplash();
                    if (stage.flags().timeAttack()) topSpeed = 0x500;
                }
            }
            case 4 -> { // ObjSonic_Rough (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:652-670)
                topSpeed = 0x500;
                if (Integer.compareUnsigned(speed, 0x100) >= 0) {
                    stage.spawnDust();
                    stage.playSound(CdSpecialSound.DUST);
                }
            }
            case 5 -> { // ObjSonic_Spring (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:674-687)
                routine = JUMP;
                spriteYVel = -0x100000;
                flags |= FLAG_AIR;
                stage.playSound(CdSpecialSound.SPRING);
                stage.deleteSplash();
            }
            case 6 -> hazard();
            case 7 -> boost(0x18, 0x14, BOOSTED, CdSpecialSound.BIG_BOOSTER, true);
            case 8 -> boost(0x10, 8, BUMPED, CdSpecialSound.SMALL_BOOSTER, false);
            default -> { }
        }
    }

    /** {@code ObjSonic_Hazard} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:691-745): hurt, halve rings, drop up to seven. */
    private void hazard() {
        if (routine == BUMPED || routine == HURT) return;
        timer = 46;
        routine = HURT;
        animation.set(this, 0xD);
        int rings = stage.rings;
        int kept = rings >>> 1;
        stage.rings = kept;
        int lost = rings - kept;
        if (lost != 0) stage.dropRings(Math.min(lost, 7));
    }

    // Boost direction by stamp orientation & $E (.Orientations, Sonic/Main.asm:763-771, 823-831):
    // 0 up, 1 left, 2 down, 3 right.
    private static final int[] BOOST_DIRECTION = {0, 1, 2, 3, 0, 3, 2, 1};

    /** {@code ObjSonic_BigBooster} / {@code ObjSonic_SmallBooster} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:749-867). */
    private void boost(int magnitude, int time, int nextRoutine, CdSpecialSound sound, boolean boostAnim) {
        stage.playSound(sound);
        if (boostAnim) animation.set(this, 0xE);
        int dx = 0, dy = 0;
        switch (BOOST_DIRECTION[(stampOrientation & 0xE) >> 1]) {
            case 0 -> dy = -magnitude;
            case 1 -> dx = -magnitude;
            case 2 -> dy = magnitude;
            default -> dx = magnitude;
        }
        // move.w writes the velocity's high word; the fraction is kept.
        xVel = (dx << 16) | (xVel & 0xFFFF);
        yVel = (dy << 16) | (yVel & 0xFFFF);
        bumpTime = time;
        routine = nextRoutine;
    }

    // ObjSonic_CheckBumper .Index (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:907-923): direction for each corner set.
    private static final int DOWN_RIGHT = 1, DOWN_LEFT = 2, DOWN = 3, UP_LEFT = 4, UP_RIGHT = 5, LEFT = 6, RIGHT = 7, UP = 8;
    private static final int[] BUMPER_DIRECTION = {0, DOWN_RIGHT, DOWN_LEFT, DOWN, UP_LEFT, UP_RIGHT, LEFT, DOWN_LEFT,
            UP_RIGHT, RIGHT, UP_LEFT, DOWN_RIGHT, UP, UP_RIGHT, UP_LEFT, UP_LEFT};

    /** {@code ObjSonic_CheckBumper} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:871-993). */
    private void checkBumper() {
        int d1 = ((speed & 0xFFFF) << 8) + 0x20000;
        int d2 = -d1;
        int corners = (stampTopLeft == 1 ? 1 : 0) | (stampTopRight == 1 ? 2 : 0)
                | (stampBottomRight == 1 ? 4 : 0) | (stampBottomLeft == 1 ? 8 : 0);
        switch (BUMPER_DIRECTION[corners]) {
            case 0 -> { return; }
            case DOWN_RIGHT -> { xVel = d1; yVel = d1; }
            case DOWN_LEFT -> { xVel = d2; yVel = d1; }
            case DOWN -> { xVel = 0; yVel = d1; }
            case UP_LEFT -> { xVel = d2; yVel = d2; }
            case UP_RIGHT -> { xVel = d1; yVel = d2; }
            case LEFT -> { xVel = d2; yVel = 0; }
            case RIGHT -> { xVel = d1; yVel = 0; }
            default -> { xVel = 0; yVel = d2; }
        }
        bumpTime = 0x10;
        routine = BUMPED;
        flags |= FLAG_BUMP;
        stage.playSound(CdSpecialSound.BUMPER);
        animation.set(this, 0);
    }

    /** {@code ObjSonic_CheckJump} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:1020-1043), USA jump velocity -$80000. */
    private void checkJump() {
        if (stage.stageInactive || (stage.playerTap & 0x70) == 0) return;
        routine = JUMP;
        spriteYVel = -0x80000;
        stage.jumpTimer = 20;
        flags |= FLAG_AIR;
        bumpTime = 0;
        stage.playSound(CdSpecialSound.JUMP);
        stage.deleteSplash();
    }

    /** {@code ObjSonic_Rotate} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:1049-1073): 6 angle steps per update (USA). */
    private void rotate() {
        if (stage.stageInactive) return;
        if ((stage.playerHold & 0x08) != 0) {
            yaw = (yaw - 0x60000) & 0x1FFFFFF;
            stage.scrollFlags |= 1 << 3;
        }
        if ((stage.playerHold & 0x04) != 0) {
            yaw = (yaw + 0x60000) & 0x1FFFFFF;
            stage.scrollFlags |= 1 << 2;
        }
    }

    /** {@code ObjSonic_HandleSpeed} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:1106-1160), USA values. */
    private void handleSpeed() {
        if (stage.stageInactive) return;
        if ((stage.playerHold & 0xF) == 2) {
            speed = (speed - 0x40) & 0xFFFF;
            if ((short) speed < 0x200) speed = 0x200;
            return;
        }
        int top;
        if (shoes != 0) {
            shoes--;
            top = 0xE00;
        } else if (routine == HURT) {
            top = 0x200;
        } else {
            top = topSpeed;
        }
        speed = (speed + 0x20) & 0xFFFF;
        if (Integer.compareUnsigned(top, speed) < 0) speed = top;
    }

    /** {@code ObjSonic_Move} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:1166-1192). */
    private void move() {
        short[] sine = stage.sineTable();
        int angle = ((yaw >> 16) + 0x180) & 0x1FF;
        x = (x + CdSpecialMath.muls(CdSpecialMath.cosine(sine, angle), speed)) & 0xFFFFFFF;
        y = (y + CdSpecialMath.muls(CdSpecialMath.sine(sine, angle), speed)) & 0xFFFFFFF;
    }

    /**
     * {@code ObjSonic_Tilt} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:1259-1299). FixBugs = 0: the shipped untilt snaps
     * straight to 5 because both branches fall into {@code .UntiltLeft}'s loop.
     */
    private void tilt() {
        if ((stage.playerHold & 0x04) != 0) {
            tilt = (tilt - 1) & 0xFF;
            if ((byte) tilt < 0) tilt = 0;
            return;
        }
        if ((stage.playerHold & 0x08) != 0) {
            tilt = (tilt + 1) & 0xFF;
            if (tilt >= 0xA) tilt = 9;
            return;
        }
        if (tilt >= 5) {
            tilt = (tilt - 1) & 0xFF;
            if (tilt >= 5) {
                untiltLeft();
                return;
            }
            tilt = 5;
            return;
        }
        untiltLeft();
    }

    private void untiltLeft() {
        do {
            tilt = (tilt + 1) & 0xFF;
        } while (tilt <= 5);
        tilt = 5;
    }

    /** {@code ObjSonic_Animate} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:1305-1361). */
    private void animate() {
        if (stage.stageInactive) return;
        int next;
        boolean groundMove = false;
        boolean other = true;
        if ((flags & FLAG_AIR) != 0) {
            next = 6;
        } else if ((flags & FLAG_FLOAT) != 0) {
            next = 0xB;
        } else if (speed == 0) {
            next = 0xA;
        } else {
            byte[] walk = stage.walkAnimations();
            int row = (tilt * 2) & 0x1C;
            other = false;
            groundMove = true;
            if (Integer.compareUnsigned(speed, 0x300) < 0) next = walk[row + 3];
            else if (Integer.compareUnsigned(speed, 0x540) < 0) next = walk[row + 2];
            else if (Integer.compareUnsigned(speed, 0x780) < 0) next = walk[row + 1];
            else if (Integer.compareUnsigned(speed, 0xB00) < 0) next = walk[row];
            else { next = 1; groundMove = false; }
        }
        next &= 0xFF;
        if (groundMove) {
            // .GroundMoveAnim: the first ground animation restarts, later ones keep their frame.
            boolean wasGround = (flags & FLAG_GROUND_ANIM) != 0;
            flags |= FLAG_GROUND_ANIM;
            if (next == anim) return;
            if (wasGround) animation.change(this, next);
            else animation.set(this, next);
            return;
        }
        if (other) flags &= ~FLAG_GROUND_ANIM; // .OtherAnim; sprinting skips it
        if (next != anim) animation.set(this, next);
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int yaw() { return yaw; }
    public int pitch() { return pitch; }
    public int speed() { return speed; }
    public int routine() { return routine; }
    public int flags() { return flags; }
    public int spriteY() { return spriteY; }
    public int anim() { return anim; }
}

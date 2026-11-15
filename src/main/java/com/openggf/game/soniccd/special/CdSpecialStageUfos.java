package com.openggf.game.soniccd.special;

import java.util.ArrayList;
import java.util.List;

/**
 * The stage's UFOs and the time UFO (Special Stage/Objects/UFO/Main.asm). Each follows a looping
 * path of timed straight legs read from the disc; Sonic breaks one by touching it while in the
 * air, which awards its item (rings that double each time, or speed shoes), and the last one
 * stops the timer. The time UFO appears below 20 seconds and adds 30.
 */
public final class CdSpecialStageUfos {
    /** One UFO object slot. Positions are 16.16. */
    public static final class Ufo {
        final boolean timeUfo;
        int routine;
        int item;
        short[] path;
        int pathIndex;
        int x;
        int y;
        int z;
        int xVel;
        int yVel;
        int pathTime;
        int timer;
        int explodeDirection;
        int drawDelay;
        boolean alive = true;
        int ufoAnim;
        CdSpecialSprites.Sprite sprite;
        CdSpecialSprites.Sprite shadow;
        boolean shadowAlive = true;
        boolean shadowDelete;
        boolean shadowStarted;
        boolean deleteAfterRun;
        boolean cleared;
        int shadowAnim;

        Ufo(boolean timeUfo) { this.timeUfo = timeUfo; }

        public int x() { return x; }
        public int y() { return y; }
        public int routine() { return routine; }
        public boolean alive() { return alive; }
        public int item() { return item; }
    }

    private final CdSpecialStage stage;
    private final List<Ufo> ufos = new ArrayList<>(6);
    private Ufo timeUfo;

    CdSpecialStageUfos(CdSpecialStage stage) {
        this.stage = stage;
    }

    /** {@code SpawnUFOs} (scddisasm/src/Special Stage/Objects/UFO/Main.asm:429-473). */
    void spawn() {
        int n = stage.stageId() + 1;
        int count = CdSpecialStageCatalogue.scalar("UFOCount_SS" + n);
        stage.ufoCount = count;
        for (int i = 1; i <= count; i++) {
            Ufo ufo = new Ufo(false);
            load(ufo, stage.assets().raw("UFOPath_SS" + n + "_" + i));
            loadSprites(ufo);
            ufos.add(ufo);
        }
    }

    private void loadSprites(Ufo ufo) {
        ufo.sprite = new CdSpecialSprites.Sprite(ufo.timeUfo ? 0x8440 : 0xE440,
                ufo.item == 0 || ufo.timeUfo ? stage.ufoRingScripts() : stage.ufoShoeScripts());
        ufo.shadow = new CdSpecialSprites.Sprite(0xE6DC, stage.shadowScripts());
    }

    private static void load(Ufo ufo, byte[] block) {
        ufo.item = block[0] & 0xFF;
        ufo.path = CdSpecialStageAssets.words(java.util.Arrays.copyOfRange(block, 2, block.length));
        ufo.pathIndex = 0;
    }

    /** {@code SpawnTimeUFO} (scddisasm/src/Special Stage/Objects/UFO/Main.asm:479-500), called from each timer tick. */
    void spawnTimeUfo() {
        if (Integer.compareUnsigned(stage.timer, 20) >= 0) return;
        if (timeUfo != null) return;
        timeUfo = new Ufo(true);
        load(timeUfo, stage.assets().raw("TimeUFOPath"));
        loadSprites(timeUfo);
    }

    /** Runs the UFO slots, then the time UFO, in slot order (Sub.asm:133-143). */
    void update() {
        for (Ufo ufo : ufos) if (ufo.alive) run(ufo);
        if (timeUfo != null && timeUfo.alive) {
            run(timeUfo);
            // A deletion flagged earlier this update clears the slot after its run (Sub.asm:682-686).
            if (timeUfo.deleteAfterRun) { timeUfo.alive = false; timeUfo.cleared = true; }
        }
    }

    private void run(Ufo ufo) {
        switch (ufo.routine) {
            case 0 -> {
                // ObjUFO_Init / ObjTimeUFO_Init (UFO/Main.asm:49-61, 175-192).
                followPath(ufo);
                ufo.z = ((stage.sonic().z >> 16) - 0x140) << 16;
                if (!ufo.timeUfo) { ufo.ufoAnim = 0; ufo.sprite.set(0); }
                ufo.drawDelay = 2;
                ufo.routine = 1;
                if (ufo.timeUfo) stage.playSound(CdSpecialSound.TIME_UFO);
                main(ufo);
            }
            case 1 -> main(ufo);
            default -> explode(ufo);
        }
        if (!ufo.alive) return;
        // Z follows Sonic's (UFO/Main.asm:29-30, 155-156).
        ufo.z = ((((stage.sonic().z >> 16) - 0x140) & 0xFFFF) << 16) | (ufo.z & 0xFFFF);
        if (ufo.drawDelay != 0) {
            ufo.drawDelay--;
            ufo.sprite.flags |= CdSpecialSprites.NO_DRAW;
        }
    }

    /**
     * {@code ObjUFO_Draw} (scddisasm/src/Special Stage/Objects/UFO/Main.asm:379-408): queue in the Z buffer by distance from Sonic and
     * pick the animation by distance / 16, capped at $500.
     */
    private void drawUfo(CdSpecialSprites.Sprite sprite, Ufo ufo, boolean shadow) {
        CdSpecialStageSonic sonic = stage.sonic();
        long d = CdSpecialSprites.distance(stage.distanceTable(), sonic.x >> 16, sonic.y >> 16, ufo.x >> 16, ufo.y >> 16);
        stage.sprites().queue3D(sprite, d);
        if (d >= 0x500) d = 0x500;
        int frame = stage.ufoDistanceFrames()[(int) (d >> 4)] & 0xFF;
        int current = shadow ? ufo.shadowAnim : ufo.ufoAnim;
        if (frame == current) return;
        if (shadow) ufo.shadowAnim = frame; else ufo.ufoAnim = frame;
        sprite.change(frame);
    }

    private void place(CdSpecialSprites.Sprite sprite, int x, int y, int z) {
        CdSpecialStageSonic sonic = stage.sonic();
        CdSpecialSprites.project(sprite, stage.view(), sonic.x >> 16, sonic.y >> 16, x, y, z);
    }

    /**
     * The UFO shadows (priority 3, Sub.asm:146-155): {@code ObjUFOShadow} (Shadow/Main.asm:59-99)
     * follows its UFO, hands it last update's on-screen result, then checks its own and draws.
     */
    void updateShadows() {
        List<Ufo> all = new ArrayList<>(ufos);
        all.add(null); // the unused slot
        all.add(timeUfo);
        for (Ufo ufo : all) {
            if (ufo == null || !ufo.shadowAlive) continue;
            CdSpecialSprites.Sprite s = ufo.shadow;
            if (!ufo.shadowStarted) {
                ufo.shadowStarted = true;
                ufo.shadowAnim = 0;
                s.set(0);
            }
            // A cleared parent slot reads as zero (the time UFO deleted by ObjSonic_CheckWin).
            int x = ufo.cleared ? 0 : ufo.x >> 16, y = ufo.cleared ? 0 : ufo.y >> 16;
            ufo.sprite.flags |= CdSpecialSprites.NO_DRAW;
            if ((s.flags & CdSpecialSprites.NO_DRAW) == 0) ufo.sprite.flags &= ~CdSpecialSprites.NO_DRAW;
            int z = stage.sonic().z >> 16;
            drawUfo(s, ufo, true);
            place(s, x, y, z);
            // ObjUFO_ChkOnScreen (scddisasm/src/Special Stage/Objects/UFO/Main.asm:356-373).
            s.flags &= ~CdSpecialSprites.NO_DRAW;
            if (Integer.compareUnsigned(s.x & 0xFFFF, 384 + 128) >= 0 || (short) s.y < 128 + 128 || (short) s.y >= 320 + 128) {
                s.flags |= CdSpecialSprites.NO_DRAW;
            }
            stage.sprites().draw(s);
            if (ufo.shadowDelete) ufo.shadowAlive = false;
        }
    }

    /** {@code ObjUFO_Main} / {@code ObjTimeUFO_Main} (scddisasm/src/Special Stage/Objects/UFO/Main.asm:65-106, 196-268). */
    private void main(Ufo ufo) {
        ufo.x += ufo.xVel;
        ufo.y += ufo.yVel;
        ufo.pathTime = (ufo.pathTime - 1) & 0xFFFF;
        if (ufo.pathTime == 0) followPath(ufo);
        drawUfo(ufo.sprite, ufo, false);
        place(ufo.sprite, ufo.x >> 16, ufo.y >> 16, ufo.z >> 16);
        if (!touchesSonic(ufo)) return;
        if (ufo.timeUfo) {
            if (stage.timeStopped) return;
            ufo.routine = 2;
            ufo.shadowDelete = true;
            ufo.timer = 60;
            ufo.explodeDirection = stage.random() & 1;
            ufo.sprite.set(0);
            stage.timer += 30;
            stage.effects().spawnItem(ufo.sprite.x, ufo.sprite.y, 3);
            return;
        }
        if (stage.ufoCount() < 2) stage.timeStopped = true;
        stage.decrementUfoCount();
        ufo.routine = 2;
        ufo.shadowDelete = true;
        ufo.timer = 60;
        ufo.explodeDirection = stage.random() & 1;
        ufo.sprite.set(0);
        stage.effects().spawnItem(ufo.sprite.x, ufo.sprite.y, ufo.item);
        switch (ufo.item) {
            case 1 -> { // .SpeedShoes
                stage.sonic().shoes = 200;
                stage.ufoRingBonus = 20;
            }
            default -> { // .Rings (also the dummy and hand entries, UFO/Main.asm:248-252)
                int bonus = stage.ufoRingBonus;
                stage.ufoRingBonus = (bonus * 2) & 0xFFFF;
                stage.addRings(bonus);
            }
        }
    }

    /** {@code ObjUFO_Explode} (UFO/Main.asm:110-142, 277-309): 60 updates, then gone. */
    private void explode(Ufo ufo) {
        ufo.sprite.x = (short) (ufo.sprite.x - 4 + (ufo.explodeDirection == 0 ? 8 : 0));
        ufo.sprite.y = (short) (ufo.sprite.y + 1);
        ufo.sprite.flags &= ~CdSpecialSprites.NO_DRAW;
        ufo.timer = (ufo.timer - 1) & 0xFFFF;
        if (ufo.timer == 0) ufo.alive = false;
        if ((ufo.timer & 1) == 0) stage.effects().spawnExplosion(ufo.sprite.x, ufo.sprite.y);
    }

    /**
     * {@code ObjUFO_CheckPlayerCol} (scddisasm/src/Special Stage/Sub.asm:1356-1398): a 32 x 24 box around the UFO against a
     * 32 x 32 box around Sonic, Sonic's Z within [$210, $270), one UFO per update.
     */
    private boolean touchesSonic(Ufo ufo) {
        CdSpecialStageSonic sonic = stage.sonic();
        if (sonic.ufoCollision != 0) return false;
        int ux = (short) (ufo.x >> 16), uy = (short) (ufo.y >> 16);
        int sx = (short) (sonic.x >> 16), sy = (short) (sonic.y >> 16);
        int left = (short) (ux - 16), right = (short) (left + 32);
        int sLeft = (short) (sx - 16), sRight = (short) (sLeft + 32);
        if (sLeft > right || sRight < left) return false;
        int top = (short) (uy - 12), bottom = (short) (top + 24);
        int sTop = (short) (sy - 16), sBottom = (short) (sTop + 32);
        if (sTop > bottom || sBottom < top) return false;
        int sz = (sonic.z >>> 16) & 0xFFFF;
        if (sz < 0x210 || sz >= 0x270) return false;
        sonic.ufoCollision = ufo.timeUfo ? 3 : 2;
        return true;
    }

    /** {@code ObjUFO_FollowPath} (scddisasm/src/Special Stage/Objects/UFO/Main.asm:315-350): set the leg's start and velocity. */
    private void followPath(Ufo ufo) {
        int time = ufo.path[ufo.pathIndex];
        if (time < 0) {
            ufo.pathIndex = 0;
            time = ufo.path[0];
        }
        ufo.pathTime = time & 0xFFFF;
        int startX = ufo.path[ufo.pathIndex + 1], startY = ufo.path[ufo.pathIndex + 2];
        int targetX = ufo.path[ufo.pathIndex + 3], targetY = ufo.path[ufo.pathIndex + 4];
        ufo.x = ((startX & 0xFFFF) << 16) | (ufo.x & 0xFFFF);
        ufo.y = ((startY & 0xFFFF) << 16) | (ufo.y & 0xFFFF);
        ufo.xVel = leg(targetX - startX, time);
        ufo.yVel = leg(targetY - startY, time);
        ufo.pathIndex += 5;
    }

    private static int leg(int distance, int time) {
        int quotient = (short) CdSpecialMath.divs(((short) distance) << 4, time);
        return quotient << 16 >> 4;
    }

    /** {@code ObjSonic_CheckWin .Delete} (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:564-576): the time UFO and its shadow. */
    void deleteTimeUfo() {
        if (timeUfo != null) {
            timeUfo.deleteAfterRun = true;
            timeUfo.shadowDelete = true;
        }
    }

    /** {@code CheckUFOPresence} (scddisasm/src/Special Stage/Sub.asm:377-392): every UFO slot is empty. */
    boolean noneLeft() {
        for (Ufo ufo : ufos) if (ufo.alive) return false;
        return true;
    }

    public List<Ufo> ufos() { return List.copyOf(ufos); }
    public Ufo timeUfo() { return timeUfo; }
}

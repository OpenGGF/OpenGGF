package com.openggf.game.soniccd.special;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The special stage's game state and its per-update logic: the sub-CPU program's
 * {@code MainLoop} body (Sub.asm:255-368), with the objects, timer and floor it owns. The
 * main-CPU program's frame loop that paces these updates is {@link CdSpecialStageSession}.
 * One owner per state: the values the two programs exchange through the communication
 * registers (stage ID, rings, timer, UFO count, Time Stones) are plain fields here.
 */
public final class CdSpecialStage {
    private final CdSpecialStageAssets assets;
    private final int stageId;
    private final CdSpecialStageFlags flags;
    private final short[] sineTable;
    private final byte[] walkAnimations;
    private final CdStampFloor floor;
    private final CdSpecialStageView view = new CdSpecialStageView();
    private final CdStampTrace trace = new CdStampTrace();
    private final byte[] image = new byte[CdStampFloor.IMAGE_WIDTH * CdStampFloor.IMAGE_HEIGHT];
    private final CdSpecialStageSonic sonic;
    private final CdSpecialStageTitleCard titleCard = new CdSpecialStageTitleCard();
    private final CdSpecialStageUfos ufos;
    private final List<CdSpecialSound> sounds = new ArrayList<>(3);
    private final CdSpecialSplash splash;
    private final CdSpecialSprites sprites;
    private final CdSpecialEffects effects;
    private final CdSpecialAnimationScripts ufoRingScripts, ufoShoeScripts, shadowScripts;
    private final short[] distanceTable;
    private final byte[] ufoDistanceFrames;
    private final CdSpecialSprites.Sprite sonicShadow;
    private boolean sonicShadowStarted;
    /** {@code subSonicArtBuf}: the $300 bytes {@code ObjSonic_LoadArt} copies (scddisasm/src/Special Stage/Objects/Sonic/Main.asm:1376-1392). */
    final byte[] sonicArt = new byte[0x300];
    private final CdSpecialTimeStone timeStone = new CdSpecialTimeStone();
    private final CdSpecialAnimationScripts splashScripts;
    private int rngSeed;
    private int timeStones;

    // Sub-CPU variables (Sub.asm:162-188).
    boolean stageOver;
    boolean gotTimeStone;
    int timerSpeedUp;
    int timerFrames;
    boolean stageInactive;
    boolean timeStopped;
    int ufoRingBonus;
    int jumpTimer;
    int playerHold;
    int playerTap;
    int scrollFlags;
    // Communication values (_Global Variables.inc:9-17, _Common.inc:42).
    int rings;
    int timer;
    int ufoCount;
    boolean exited;
    boolean manualExit;
    private int updates;

    /**
     * Program start (Sub.asm:200-251): clear state, load the floor, spawn Sonic, his shadow,
     * the title card and the UFOs, set the stamp animation, the timer counter (60 / 3), the UFO
     * ring bonus (20), mark the stage inactive and, outside Time Attack, the 100-second timer.
     */
    public CdSpecialStage(CdSpecialStageAssets assets, int stageId, CdSpecialStageFlags flags, int timeStones) throws IOException {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.stageId = stageId;
        this.timeStones = timeStones & 0x7F;
        this.sineTable = assets.sineTable();
        this.walkAnimations = assets.walkAnimations();
        this.floor = assets.floor(stageId);
        this.view.setCamera(0, 0, 0, 0, 0);
        this.sonic = new CdSpecialStageSonic(this);
        this.sonic.animation.bind(assets.animationScripts("MapSpr_Sonic"));
        this.splashScripts = assets.animationScripts("MapSpr_Splash");
        this.splash = new CdSpecialSplash(splashScripts);
        this.sprites = new CdSpecialSprites(assets.subProgram());
        this.effects = new CdSpecialEffects(this, assets);
        this.ufoRingScripts = assets.animationScripts("MapSpr_UFORings");
        this.ufoShoeScripts = assets.animationScripts("MapSpr_UFOShoes");
        this.shadowScripts = assets.animationScripts("MapSpr_Shadow");
        this.distanceTable = assets.wordTable("DistanceTable");
        this.ufoDistanceFrames = assets.raw("UFODistanceFrames");
        this.sonicShadow = new CdSpecialSprites.Sprite(0xE6DC, shadowScripts);
        titleCard.barSprite = new CdSpecialSprites.Sprite(0x8516, assets.animationScripts("MapSpr_TitleCardBar"));
        titleCard.textSprite = new CdSpecialSprites.Sprite(0x8516, assets.animationScripts("MapSpr_TitleCardText"));
        CdSpecialAnimationScripts stoneScripts = assets.animationScripts("MapSpr_TimeStone");
        timeStone.sprite = new CdSpecialSprites.Sprite(0xE424, stoneScripts);
        timeStone.sparkle1 = new CdSpecialSprites.Sprite(0xE424, stoneScripts);
        timeStone.sparkle2 = new CdSpecialSprites.Sprite(0xE424, stoneScripts);
        this.ufos = new CdSpecialStageUfos(this);
        timer = 0;
        rings = 0;
        ufoCount = 6;
        ufos.spawn();
        timerFrames = 60 / 3;
        ufoRingBonus = 20;
        stageInactive = true;
        if (!flags.timeAttack()) timer = 100;
    }

    /**
     * One update ({@code MainLoop} from {@code .NotPaused}, Sub.asm:276-303): animate stamps,
     * take the view's sines, render the floor from the previous update's camera, run the objects
     * with the controller value the main CPU last latched, and update the timer. The end checks
     * follow in {@link #finishUpdate}.
     */
    public void update(int ctrlHold, int ctrlTap) {
        if (exited) return;
        sounds.clear();
        floor.animate();
        view.takeSines(sineTable);
        trace.generate(view);
        floor.render(trace, image);
        runObjects(ctrlHold & 0xFF, ctrlTap & 0xFF);
        updateTimer();
        updates++;
    }

    /**
     * The update's end checks (Sub.asm:311-330), made after the graphics wait and Word RAM
     * hand-back. By then the main CPU has read the controller for the next update, so the
     * temporary-mode Start check sees that newer value: native Special Stage 6's attract demo
     * ends on the update whose following controller read carries the demo's Start byte.
     *
     * @return whether the stage has ended ({@code .Exit} / {@code .StageBeaten})
     */
    public boolean finishUpdate(int ctrlHold) {
        if (exited) return true;
        if (ufos.noneLeft() && (flags.temporary() || flags.timeAttack())) stageOver = true;
        if (flags.temporary() && (ctrlHold & 0x80) != 0) {
            manualExit = true;
            return exit(false);
        }
        if (stageOver) return exit(false);
        if (gotTimeStone) return exit(true);
        return false;
    }

    /**
     * {@code RunObjects} (scddisasm/src/Special Stage/Sub.asm:626-660): priority-1 objects (splash, title card bar and
     * text, Time Stone, sparkles, dust), then Sonic and his shadow, then priority-2 objects
     * (item, lost rings, explosions, UFOs, time UFO), then 3D drawing and the UFO shadows.
     * {@code ObjSonic} reads the controller value for this update (Sonic/Main.asm:55-59).
     */
    private void runObjects(int hold, int tap) {
        sprites.begin();
        splash.update(this, splashScripts);
        // The slot named ttlCardBarObj is spawned with ID $A, ObjTitleCardText, and ttlCardTextObj
        // with $B, ObjTitleCardBar (Sub.asm:95-96, 236-237, 1171-1172): the text runs first.
        if (titleCard.textAlive) {
            titleCard.updateText();
            titleCard.textSprite.x = (short) titleCard.textX;
            titleCard.textSprite.y = 112 + 128;
            sprites.draw(titleCard.textSprite);
        }
        if (titleCard.barAlive) {
            titleCard.updateBar(this);
            titleCard.barSprite.x = 116 + 128;
            titleCard.barSprite.y = (short) titleCard.barY;
            sprites.draw(titleCard.barSprite);
        }
        timeStone.update(this);
        timeStone.draw(sprites);
        effects.runDust(sprites);
        playerHold = hold;
        playerTap = tap;
        sonic.update();
        runSonicShadow();
        effects.runPriority2(sprites);
        ufos.update();
        sprites.draw3D();
        ufos.updateShadows();
    }

    /** {@code ObjSonic} draws and loads his art (Sonic/Main.asm:126-128, 1376-1392). */
    void drawSonic() {
        CdSpecialSprites.Sprite s = sonic.animation.sprite;
        s.x = 128 + 128;
        s.y = (short) (sonic.spriteY >> 16);
        s.flags = sonic.flags & (CdSpecialSprites.NO_ANIMATE | CdSpecialSprites.NO_DRAW);
        sprites.draw(s);
        byte[] program = assets.subProgram();
        int table = CdSpecialStageCatalogue.block("SonicArt").offset();
        int at = table + s.spriteFlag * 4;
        int pointer = ((program[at + 1] & 0xFF) << 16) | ((program[at + 2] & 0xFF) << 8) | (program[at + 3] & 0xFF);
        System.arraycopy(program, pointer - 0x10000, sonicArt, 0, sonicArt.length);
    }

    /** {@code ObjSonicShadow} (Shadow/Main.asm:9-50): follows Sonic, projected like a UFO. */
    private void runSonicShadow() {
        if (!sonicShadowStarted) { sonicShadow.set(5); sonicShadowStarted = true; }
        CdSpecialSprites.project(sonicShadow, view, sonic.x >> 16, sonic.y >> 16, sonic.x >> 16, sonic.y >> 16, sonic.z >> 16);
        sprites.draw(sonicShadow);
    }

    /** {@code UpdateTimer} (scddisasm/src/Special Stage/Sub.asm:1720-1789). */
    private void updateTimer() {
        if (flags.timeAttack()) {
            if (timeStopped || stageInactive) return;
            int frames = (timer & 0xFF) + 3;
            int seconds = (timer >>> 8) & 0xFF;
            int minutes = (timer >>> 16) & 0xFF;
            if (frames >= 60) {
                frames -= 60;
                seconds++;
                if (seconds >= 60) {
                    seconds -= 60;
                    minutes++;
                    if (minutes >= 10) {
                        timer = (9 << 16) | (59 << 8) | 59;
                        stageOver = true;
                        return;
                    }
                }
            }
            timer = (timer & 0xFF000000) | (minutes << 16) | (seconds << 8) | frames;
            return;
        }
        timerFrames = (timerFrames - 1) & 0xFF;
        if (timerFrames == 0) {
            timerFrames = 60 / 3;
            tick();
        }
        if (timerSpeedUp != 0) {
            timerSpeedUp--;
            tick();
        }
    }

    /** {@code UpdateTimer .Tick} (scddisasm/src/Special Stage/Sub.asm:1740-1759). */
    private void tick() {
        if (timeStopped || stageInactive) return;
        timer--;
        if (timer < 0) {
            timer = 0;
            timerSpeedUp = 0;
            stageOver = true;
        }
        ufos.spawnTimeUfo();
        if (Integer.compareUnsigned(timer, 0xF) < 0) sounds.add(CdSpecialSound.TIME_WARNING);
    }

    /**
     * {@code .StageBeaten} / {@code .Exit} (Sub.asm:332-356): a beaten stage adds its Time Stone;
     * the next stage is the next one without a stone, wrapping within the seven, or simply the
     * next one once all seven are held.
     */
    private boolean exit(boolean beaten) {
        if (beaten) timeStones |= 1 << stageId;
        exited = true;
        return true;
    }

    /** {@code .GetNextStage} (Sub.asm:341-356). */
    int nextStageId() {
        int next = stageId;
        while (true) {
            next = (next + 1) & 0xFF;
            if (next >= 7) next = 0;
            if (timeStones == 0x7F || (timeStones & (1 << next)) == 0) return next;
        }
    }

    // ---- services used by the objects ----

    void playSound(CdSpecialSound sound) {
        if (sounds.size() < 3) sounds.add(sound);
    }

    void spawnSplash() { splash.spawn(); }
    void deleteSplash() { splash.markDeleted(); }
    /** {@code FindDustObjSlot}; {@code move.b #7,(a1)} (Sonic/Main.asm:661-663). */
    void spawnDust() {
        CdSpecialEffects.Slot slot = effects.freeDust();
        if (slot != null) { slot.present = true; slot.routine = 0; }
    }

    /** {@code Random} (scddisasm/src/Special Stage/Sub.asm:2302-2321). */
    int random() {
        int seed = rngSeed == 0 ? 0x2A6D365A : rngSeed;
        int m = seed * 41;
        int low = (m + (m >>> 16)) & 0xFFFF;
        rngSeed = (low << 16) | (m & 0xFFFF);
        return (seed & 0xFFFF0000) | low;
    }
    void dropRings(int count) { effects.dropRings(count); }
    void spawnTimeStone() { timeStone.spawn(); }
    void deleteTimeUfo() { ufos.deleteTimeUfo(); }

    /** {@code AddRings} (scddisasm/src/Special Stage/Sub.asm:1707-1714). */
    void addRings(int count) {
        rings = Math.min(rings + count, 999);
    }

    /** {@code DecUFOCount} (scddisasm/src/Special Stage/Sub.asm:1692-1698). */
    void decrementUfoCount() {
        ufoCount = (ufoCount - 1) & 0xFF;
    }

    // ---- accessors ----

    public CdSpecialStageAssets assets() { return assets; }
    CdSpecialSprites sprites() { return sprites; }
    java.util.List<CdSpecialSprites.Entry> spriteEntries() { return sprites.entries; }
    CdSpecialEffects effects() { return effects; }
    CdSpecialAnimationScripts ufoRingScripts() { return ufoRingScripts; }
    CdSpecialAnimationScripts ufoShoeScripts() { return ufoShoeScripts; }
    CdSpecialAnimationScripts shadowScripts() { return shadowScripts; }
    short[] distanceTable() { return distanceTable; }
    byte[] ufoDistanceFrames() { return ufoDistanceFrames; }
    CdSpecialSprites.Sprite sonicSprite() { return sonic.animation.sprite; }
    public int stageId() { return stageId; }
    public CdSpecialStageFlags flags() { return flags; }
    public CdStampFloor floor() { return floor; }
    public CdSpecialStageView view() { return view; }
    public CdStampTrace trace() { return trace; }
    /** The floor rendered by the latest update (row-major 256 x 96 colour indices). */
    public byte[] image() { return image; }
    public CdSpecialStageSonic sonic() { return sonic; }
    public CdSpecialStageUfos ufos() { return ufos; }
    short[] sineTable() { return sineTable; }
    byte[] walkAnimations() { return walkAnimations; }
    public int ufoCount() { return ufoCount; }
    public int rings() { return rings; }
    public int timer() { return timer; }
    public int timeStones() { return timeStones; }
    public boolean stageInactive() { return stageInactive; }
    public int updates() { return updates; }
    public List<CdSpecialSound> sounds() { return List.copyOf(sounds); }
    public CdSpecialStageTitleCard titleCard() { return titleCard; }
}

package com.openggf.game.soniccd;

import com.openggf.game.LevelEventProvider;

/**
 * Level events: {@code RunLevelEvents} (Level/_Events.asm) through {@link CdStageEvents}, after
 * the camera scroll, and the V-INT time-warp timer.
 */
final class CdLevelEventProvider implements LevelEventProvider {
    private final java.util.function.Supplier<com.openggf.game.soniccd.timetravel.CdTimeTravel> timeTravel;

    CdLevelEventProvider(java.util.function.Supplier<com.openggf.game.soniccd.timetravel.CdTimeTravel> timeTravel) {
        this.timeTravel = timeTravel;
    }

    @Override public void initLevel(int zone, int act) { }
    @Override public void update() { }

    /**
     * V-INT precedes the frame's object pass: VInt_Finish (Level/Main.asm:704-709) calls RunBoredTimer
     * (Level/Main.asm:985-993) and RunTimeWarp (Level/Main.asm:971-978).
     */
    @Override public void updatePrePhysics() {
        runBoredTimer();
        runTimeWarp();
    }

    /**
     * A lag frame still takes V-INT: the stage's lag routine (Tidal Tempest's {@code VBlankLag} moves the water,
     * r4/main.asm:994-1022), then VInt_Finish (Level/Main.asm:704-709), which advances the time-warp timer.
     */
    @Override public void advanceVblankOnlyState() {
        runLagVBlankWater();
        runBoredTimer();
        runTimeWarp();
    }

    /** {@code RunBoredTimer}: the boredom timer counts every unpaused V-INT, lag V-INTs included. */
    private static void runBoredTimer() {
        var camera = com.openggf.game.GameServices.cameraOrNull();
        var player = camera == null ? null : camera.getFocusedSprite();
        var stage = com.openggf.game.soniccd.player.CdPlayerStage.of(player);
        if (stage != null) stage.boredom().vIntTick();
    }

    /**
     * {@code ObjSonic_LevelBound .Bottom}: a giving-up player past the bottom boundary takes the death routine
     * directly ({@code move.b #6,oRoutine(a0)}) instead of {@code KillPlayer}, so its speeds, animation and
     * sound stay as the give-up left them (Level/_Objects/Sonic/Main.asm:1704-1707).
     */
    @Override
    public boolean interceptPitDeath(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        if (!com.openggf.game.soniccd.player.CdBoredom.givingUp(player)) return false;
        player.setDead(true);
        return true;
    }

    /** The lag V-INT's water call, VBlankLag (r4/main.asm:994-1022; the jsr at :1010), in the zones whose program has it. */
    private static void runLagVBlankWater() {
        var levels = com.openggf.game.GameServices.levelOrNull();
        if (levels == null || !(levels.getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel level)) {
            return;
        }
        if (com.openggf.game.soniccd.zones.CdZones.fragment(level.payload().stage().zone()).lagVBlankUpdatesWater()) {
            levels.advanceDynamicWaterLevel();
        }
    }

    /**
     * After the object pass, in the main loop's order: the {@code levelRestart} test
     * ({@code Level_MainLoop}, Level/Main.asm:405-408), which is the program flow's stage exit for a ready
     * time warp and the time owner's sub-CPU audio commands; then {@code RunLevelEvents}, which the USA
     * {@code LevelScroll} runs before its camera scroll (Palmtree Panic/Scroll (Act 1 Present).asm:189-192).
     */
    @Override
    public void updateAfterObjectsBeforeCamera() {
        com.openggf.game.soniccd.program.CdStageExit.afterObjectPass(com.openggf.game.GameServices.module(),
                com.openggf.game.GameServices.levelOrNull(), com.openggf.game.GameServices.gameStateOrNull(),
                id -> com.openggf.game.GameServices.audio().playMusic(id));
        runLevelEvents();
    }

    /**
     * {@code LevelScroll}'s {@code RunLevelEvents}, its first call (Palmtree Panic/Scroll (Act 1 Present).asm:177-192):
     * nothing while {@code scrollLock} is set, else the zone's routine (act 3's boss start and arena, objects/boss),
     * then the bottom-boundary handling every stage shares (Level/_Events.asm:7-53). The frame runs it after the
     * object pass; {@link CdFirstMainLoopIteration} runs it in the load's two {@code LevelScroll} calls.
     */
    static void runLevelEvents() {
        var camera = com.openggf.game.GameServices.cameraOrNull();
        var levels = com.openggf.game.GameServices.levelOrNull();
        if (camera == null || levels == null
                || !(levels.getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel level)) {
            return;
        }
        if (level.flags().scrollLock) return;
        com.openggf.game.soniccd.objects.boss.CdBossLevelEvents.run(level, camera, camera.getFocusedSprite(),
                levels.getObjectManager());
        CdStageEvents.run(level.payload(), camera, camera.getFocusedSprite());
    }

    private void runTimeWarp() {
        var tt = timeTravel.get();
        var camera = com.openggf.game.GameServices.cameraOrNull();
        var player = camera == null ? null : camera.getFocusedSprite();
        if (tt != null) tt.vIntTick(player != null && com.openggf.game.soniccd.timetravel.CdTimeWarp.charging(player));
    }
    void resetState() { }
}

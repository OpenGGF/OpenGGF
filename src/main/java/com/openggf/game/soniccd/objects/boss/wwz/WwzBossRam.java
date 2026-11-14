package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.player.SonicCdPhysicsProvider;
import com.openggf.game.soniccd.zones.wwz.WwzPlayerProfile;
import com.openggf.game.soniccd.zones.wwz.WwzStageSignals;
import com.openggf.game.soniccd.zones.wwz.WwzStageState;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;

/**
 * The Wacky Workbench act 3 level RAM the boss objects own (Level/_Definitions.inc:314-316; Main CPU Variables.inc:185;
 * the r6 names in Quartz Quadrant/R5 Compatibility.asm:174-175 and Wacky Workbench/R6 Compatibility.asm:53):
 * <ul>
 *   <li>{@code bossFlags} ({@code boss_flags}): low nibble 6 once Eggman has started; bit 4 the arena (the level events'
 *       bounds, Level/_Events.asm:179-224); bit 5 the boss palette colours (palette_cycle_c.asm:17-19); bit 6 a
 *       request for the floor-art switch and bit 7 the switch made (stage_gfx_animate_3c.asm:10-27), which also stops
 *       the player's sparks (objects/r6/player.asm:402-403); any bit stops the floor bounce (:467-468).</li>
 *   <li>{@code bossFight} ({@code boss_started}): 6 once Eggman starts; bits 5, 6, 7 electrify the bottom, middle and
 *       top floor's conductors (ConductorCheckPlayerDamage, objects/r6/boss.asm:1554-1584).</li>
 *   <li>{@code bossMusic} ({@code boss_music}): set by {@code SubCpuCommand} for command $67 (common/sub_cpu.asm:7-12).</li>
 *   <li>the player's {@code oFlags} bit 7, which {@code BossHurtPlayer} sets on the death animation (objects/r6/boss.asm:
 *       1366-1381) and the defeat and the conductors test.</li>
 * </ul>
 * One instance per stage load (the art provider's per-load state, cleared by the load). It answers the zone player's
 * {@link WwzStageSignals} once a boss object binds it to the stage's {@link WwzPlayerProfile}; the floor flash and spark
 * tile signals stay the presentation's. Every write is copied into each live boss object's scalar mirror, so the
 * generic rewind capture of any of them restores it ({@link WwzBossPiece}).
 */
public final class WwzBossRam implements WwzStageSignals {
    private int bossFlags;
    private int bossFight;
    private boolean bossMusic;
    private boolean playerKilled;
    private WwzPlayerProfile bound;
    private LevelManager levels;

    public int flags() { return bossFlags; }

    public int fight() { return bossFight; }

    public boolean music() { return bossMusic; }

    boolean playerKilled() { return playerKilled; }

    void setFlags(ObjectManager objects, int value) {
        bossFlags = value & 0xFF;
        publish(objects);
    }

    void setFight(ObjectManager objects, int value) {
        bossFight = value & 0xFF;
        publish(objects);
    }

    void setMusic(ObjectManager objects) {
        bossMusic = true;
        publish(objects);
    }

    void setPlayerKilled(ObjectManager objects) {
        playerKilled = true;
        publish(objects);
    }

    /** {@code bset}/{@code bclr} on {@code boss_flags}; returns the old bit. */
    boolean flagBit(ObjectManager objects, int bit, boolean set) {
        boolean was = (bossFlags & (1 << bit)) != 0;
        setFlags(objects, set ? bossFlags | (1 << bit) : bossFlags & ~(1 << bit));
        return was;
    }

    private void publish(ObjectManager objects) {
        if (objects == null) return;
        for (var object : objects.getActiveObjects()) {
            if (object instanceof WwzBossPiece piece) piece.mirror(this);
        }
    }

    /** A restored boss object's mirror (the generic rewind capture). */
    void restore(int flags, int fight, boolean music, boolean killed) {
        bossFlags = flags & 0xFF;
        bossFight = fight & 0xFF;
        bossMusic = music;
        playerKilled = killed;
    }

    /** Binds this RAM to the stage's player profile ({@link WwzPlayerProfile#setSignals}); idempotent. */
    void bind(com.openggf.game.GameModule module, CdLevel level, LevelManager levels) {
        this.levels = levels;
        if (level == null || module == null || !(module.getPhysicsProvider() instanceof SonicCdPhysicsProvider p)) return;
        if (p.stage().profile(level) instanceof WwzPlayerProfile profile && profile != bound) {
            profile.setSignals(this);
            bound = profile;
        }
    }

    // ---- WwzStageSignals ------------------------------------------------------------------------------

    @Override public int bossFlags() { return bossFlags; }

    /** The animated tiles' {@code bclr #6}/{@code bset #7} (r6/stage_gfx_animate_3c.asm:11, 27). */
    @Override public void setBossFlags(int value) {
        tilesWriteFrame = levels == null ? -1 : levels.getFrameCounter();
        flagsBeforeTilesWrite = bossFlags;
        setFlags(levels == null ? null : levels.getObjectManager(), value);
    }

    private int tilesWriteFrame = -1;
    private int flagsBeforeTilesWrite;

    /**
     * {@code boss_flags} as native's post-frame RAM shows it: native runs {@code AnimateStageGfx} in the next V-INT
     * (common/main.asm:892), before that frame's objects, while the engine runs the animated tiles at the end of the
     * frame; on the frame the tiles wrote, the value the objects left. Comparison only.
     */
    @Override public int bossFlagsAfterObjects() {
        return levels != null && tilesWriteFrame == levels.getFrameCounter() ? flagsBeforeTilesWrite : bossFlags;
    }

    @Override public boolean bossMusic() { return bossMusic; }

    @Override public int floorFlashStep() { return WwzStageState.floorFlashStep(levels); }

    @Override public int sparkTileFrame() { return WwzStageState.sparkTileFrame(levels); }
}

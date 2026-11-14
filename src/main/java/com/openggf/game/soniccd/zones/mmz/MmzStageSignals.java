package com.openggf.game.soniccd.zones.mmz;

import com.openggf.game.GameModule;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.player.CdPlayerStage;
import com.openggf.game.soniccd.player.SonicCdPhysicsProvider;

/**
 * The Metallic Madness state the zone's objects write and the player side reads. One instance per stage
 * load (it belongs to {@link MmzPlayerProfile}, which {@code CdPlayerStage} rebuilds with the level), so it
 * starts as {@code InitStage} leaves the variables.
 *
 * <p>Writers (objects, scddisasm {@code src/objects/r8}) and readers (player lane):
 * <ul>
 *   <li>{@link #stageLayer()} / {@code stage_layer} (the {@code layer} byte, Main CPU Variables.inc:183): the
 *       Revolve Door flips it ({@code eori.b #1,stage_layer}, revolve_door.asm:165). Read by
 *       {@code r8/player_object_collide.asm:105,280} (Player_TouchResponse's layer gate and HurtPlayer's ring
 *       drop), {@code common/block_collide.asm:115,149}, {@code objects/powerup.asm:52,100} and
 *       {@code objects/monitor_time.asm:123,130,208}.</li>
 *   <li>{@link #shrunk()} / {@code shrunk_player} ({@code miniSonic}): the Laser flips it
 *       ({@code eori.b #1,shrunk_player}, laser.asm:228). Read by the player's radii, rolls, jumps and
 *       animation set ({@code objects/player.asm:143,638,1411,1671,1756,2170,2580,2665,2719}) and by
 *       {@code common/solid_object.asm:85,88}. Saved by checkpoints and warps
 *       ({@code objects/checkpoint.asm:29,33}, {@code objects/player.asm:527,530}).</li>
 *   <li>{@link #revolveHold()} / the player's {@code obj.var_2c} bit 0: set while a Revolve Door carries the player
 *       (revolve_door.asm:134-138, cleared :163). Read by the player routine as a movement lock.</li>
 *   <li>{@link #discHold()} / the player's {@code obj.var_38}: set to 1 while the player rides a Spin Disc
 *       (spin_disc.asm:79-83) and cleared on leaving it or by the solid routine
 *       ({@code common/solid_object.asm:29}). Read by {@code common/player_ground_collide.asm:102,239,305,371}
 *       and {@code objects/player.asm:1886} ({@code PlayerCheckFall} returns while it is set); cleared by
 *       {@code objects/player.asm:1750} (jump).</li>
 *   <li>Act 3's boss bytes (boss lane, {@code objects/boss/mmz}): {@link #bossFlags()} / {@code boss_flags}
 *       ({@code bossFlags}, set to 8 by the Hotaru intro and Eggman, hotaru.asm:445-447, boss.asm:290; read by
 *       {@code r8/events.asm:66-80}), {@link #bossStarted()} / {@code boss_started} ({@code bossFight}: 8, then bit 7 once
 *       the door opens, boss.asm:291, 344, 1906; read by {@code r8/player_object_collide.asm:364-370},
 *       {@code objects/r8/boss_door.asm:54}, {@code amy_rose.asm:37} and {@code CycleBossPalette}) and
 *       {@link #bossDoorFlag()} / {@code boss_door_flag} ({@code bossMusic+1}; set by the Hotaru intro, hotaru.asm:436,
 *       cleared by the boss door, boss_door.asm:56; read by {@code r8/stage_gfx_animate_3c.asm:46} and
 *       {@code _3d.asm:59}, {@link MmzAnimatedTiles}). {@code InitStage} leaves them clear.</li>
 * </ul>
 */
public final class MmzStageSignals {
    private int stageLayer;
    private boolean shrunk;
    private int discHold;
    private boolean revolveHold;
    private final java.util.Map<Integer, Integer> objectStates = new java.util.HashMap<>();
    private int bossFlags;
    private int bossStarted;
    private boolean bossDoorFlag;

    /** {@code stage_layer}: 0 foreground, 1 the other layer. */
    public int stageLayer() { return stageLayer & 0xFF; }

    /** {@code eori.b #1,stage_layer}. */
    public void flipStageLayer() { stageLayer ^= 1; }

    /** {@code miniSonic} / {@code shrunk_player}. */
    public boolean shrunk() { return shrunk; }

    /** {@code eori.b #1,shrunk_player}. */
    public void flipShrunk() { shrunk = !shrunk; }

    /** {@code move.b respawn_shrunk/warp_shrunk,shrunk_player}: a checkpoint restart or a warp arrival. */
    public void setShrunk(boolean value) { shrunk = value; }

    /** The player's {@code obj.var_38}. */
    public int discHold() { return discHold & 0xFF; }

    public void setDiscHold(int value) { discHold = value & 0xFF; }

    /** The player's {@code obj.var_2c} bit 0, set and cleared by the Revolve Door. */
    public boolean revolveHold() { return revolveHold; }

    public void setRevolveHold(boolean hold) { revolveHold = hold; }

    /**
     * The Scarab's {@code object_states} byte for a layout entry and time zone (key {@code entry << 2 | timeZone}):
     * bit 0 the beetle is gone, bit 1 the monitor is gone (objects/r8/scarab.asm:21-35, 60-193). Clear at stage load.
     */
    public int objectState(int key) { return objectStates.getOrDefault(key, 0); }

    public void setObjectState(int key, int bits) { objectStates.put(key, bits & 0xFF); }

    /** {@code boss_flags}. */
    public int bossFlags() { return bossFlags & 0xFF; }

    public void setBossFlags(int value) { bossFlags = value & 0xFF; }

    /** {@code boss_started} ({@code bossFight}). */
    public int bossStarted() { return bossStarted & 0xFF; }

    public void setBossStarted(int value) { bossStarted = value & 0xFF; }

    /** {@code boss_door_flag}. */
    public boolean bossDoorFlag() { return bossDoorFlag; }

    public void setBossDoorFlag(boolean value) { bossDoorFlag = value; }

    /** Rewind copy of every signal. */
    public record Snapshot(int stageLayer, boolean shrunk, int discHold, boolean revolveHold,
                           java.util.Map<Integer, Integer> objectStates, int bossFlags, int bossStarted,
                           boolean bossDoorFlag) { }

    public Snapshot capture() {
        return new Snapshot(stageLayer, shrunk, discHold, revolveHold, java.util.Map.copyOf(objectStates),
                bossFlags, bossStarted, bossDoorFlag);
    }

    public void restore(Snapshot s) {
        stageLayer = s.stageLayer();
        shrunk = s.shrunk();
        discHold = s.discHold();
        revolveHold = s.revolveHold();
        objectStates.clear();
        objectStates.putAll(s.objectStates());
        bossFlags = s.bossFlags();
        bossStarted = s.bossStarted();
        bossDoorFlag = s.bossDoorFlag();
    }

    /** The loaded Metallic Madness stage's signals, or {@code null} outside one. */
    public static MmzStageSignals of(GameModule module, CdLevel level) {
        if (module == null || level == null
                || !(module.getPhysicsProvider() instanceof SonicCdPhysicsProvider provider)) {
            return null;
        }
        CdPlayerStage stage = provider.stage();
        return stage.profile(level) instanceof MmzPlayerProfile profile ? profile.signals() : null;
    }
}

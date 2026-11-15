package com.openggf.game.soniccd.player;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * One zone's part of Sonic CD's player routine. The shared CD player code
 * ({@code ObjSonic_*}, scddisasm src/Level/_Objects/Sonic/Main.asm) tests {@code zone},
 * {@code zoneAct} and {@code timeZone} in a handful of places, and every stage file
 * carries its own copy of the block-check and object-collision routines; each such
 * test or copy is one method here. A zone fragment returns its profile from
 * {@code CdZoneFragment.playerProfile}; {@link CdPlayerStage} calls it, so neither the
 * shared engine nor the CD module names a zone. Every default is the behaviour of a
 * zone the ROM's test does not select.
 *
 * <p>Methods marked <em>not called yet</em> are typed attach points for lanes that own
 * the behaviour; each names the native call site it belongs at.
 */
public interface CdZonePlayerProfile {
    /** A zone with none of the zone-tested player behaviour. */
    CdZonePlayerProfile STANDARD = new CdZonePlayerProfile() { };

    /** A zone whose only zone-tested behaviour is {@code ObjSonic_SpecialChunks} with {@code chunks}. */
    static CdZonePlayerProfile withSpecialChunks(CdSpecialChunks chunks) {
        return new CdZonePlayerProfile() {
            @Override
            public CdSpecialChunks specialChunks() {
                return chunks;
            }
        };
    }

    // ---- ObjSonic_Handle3DRamp, ObjSonic_MoveAir, ObjSonic_ExtCamera --------------

    /**
     * {@code ObjSonic_Handle3DRamp}: whether the raw layout byte the player is in is a
     * 3D-ramp chunk on this stage. The ROM's gate ({@code timeZone == 1},
     * {@code zoneAct == 0}, Main.asm:783-786) and {@code .ChunkList} (Main.asm:825-831)
     * both belong to the profile.
     */
    default boolean isPseudo3dRampChunk(int rawChunk) {
        return false;
    }

    /**
     * {@code ObjSonic_MoveAir}: the stage's launch window in which the routine returns
     * before steering and drag (Main.asm:1566-1576).
     */
    default boolean locksAirMovementAt(int centreX) {
        return false;
    }

    /**
     * {@code ObjSonic_ExtCamera} (scddisasm/src/Level/_Objects/Sonic/Main.asm:231-283) runs on this stage. Wacky Workbench's
     * copies open with a bare {@code rts} ({@code Level/Wacky Workbench/Objects/Sonic/
     * Main.asm:591-592}, {@code objects/r6/player.asm:530-531}), so its focus column
     * stays at the centre {@code LevelSizeLoad} sets.
     */
    default boolean extendedCamera() {
        return true;
    }

    /**
     * {@code ObjSonic_ExtCamera}: on a 3D ramp at this X the speed pan returns to the
     * centre ({@code cmpi.w #$1B00,oX(a0)}, Main.asm:239-244).
     */
    default boolean rampCentresFocusAt(int centreX) {
        return false;
    }

    // ---- ObjSonic_SpecialChunks -----------------------------------------------------

    /**
     * {@code ObjSonic_SpecialChunks}: the stage's loop and roll-tunnel chunks with the
     * zone's variant, or null when the routine's zone gate skips this zone
     * (Main.asm:2500-2508 runs it only in Palmtree Panic, Tidal Tempest, Quartz
     * Quadrant and Stardust Speedway).
     */
    default CdSpecialChunks specialChunks() {
        return null;
    }

    // ---- GetLevelBlock's loop chunks (Collision Floor.asm:483-485, 507-650) -----------

    /**
     * The chunk the player's probe reads for loop chunk {@code loopChunk} (bit 7 clear). The
     * default is {@code .NotMMZ2}: on the loop path the next chunk, $29 becoming $51
     * (Collision Floor.asm:643-650); Stardust Speedway and Metallic Madness have their own
     * branches that also read the stage {@code layer}.
     */
    default int playerLoopChunk(com.openggf.game.soniccd.level.CdLevel level, int loopChunk, boolean onLoopPath) {
        return com.openggf.game.soniccd.level.CdLevel.PlayerLoopChunkRule.STANDARD.chunk(loopChunk, onLoopPath);
    }

    /**
     * {@code GetLevelBlock}'s writes when the player's probe reads a plain chunk (Collision Floor.asm:487-499):
     * none for most zones.
     */
    default void playerPlainChunk(com.openggf.game.soniccd.level.CdLevel level) {
    }

    // ---- Per-stage routines and gates ---------------------------------------------

    /**
     * {@code ObjSonic_SpecialCol} / {@code PlayerCheckBlock}, called straight after the
     * movement mode on the normal-control path (Main.asm:322-324). A bare {@code rts}
     * in Palmtree Panic ({@code Player Special Collision.asm:7-8}), Tidal Tempest and
     * Metallic Madness act 1; Collision Chaos, Quartz Quadrant, Stardust Speedway and
     * Metallic Madness acts 2-3 assemble their own ({@code src/rN/player_block_check.asm}).
     */
    default void blockCheck(AbstractPlayableSprite player) {
    }

    /**
     * The gate before {@code Player_ObjCollide} (Main.asm:342-348) together with the
     * zone's own entry tests in {@code src/rN/player_object_collide.asm} (Collision Chaos
     * and Wacky Workbench test {@code var_2c} bit 6; Metallic Madness bit 0). False skips
     * the player's touch pass for the frame.
     */
    default boolean objectCollisionEnabled(AbstractPlayableSprite player) {
        return true;
    }

    /**
     * The touch routine tests {@code timeWarp} beside {@code invincible} on both paths: an enemy is damaged
     * ({@code Player_TouchEnemy}) and a hazard does not hurt ({@code Player_TouchHazard}) while the warp stars
     * run (Level/Palmtree Panic/Player Object Collision.asm:130-134, 211-216; the r-dialect copies' sub_206FA6
     * and sub_207070, e.g. r5/player_object_collide.asm:125-130, 208-213). Every zone's copy has both tests.
     */
    default boolean touchesAsInvincible(AbstractPlayableSprite player) {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(player.currentGameModule());
        return tt != null && tt.starsActive();
    }

    /**
     * {@code ObjSonic_TimeWarp}'s slot after {@code ObjSonic_Handle3DRamp} in every
     * movement mode (Main.asm:573-668): the standard routine,
     * {@link com.openggf.game.soniccd.timetravel.CdTimeWarp#run}, with this zone's
     * {@link #stopsTimeWarpAt} test. A zone with its own player routine (Wacky Workbench)
     * calls the same standard routine from it.
     */
    default void timeWarp(AbstractPlayableSprite player) {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(player.currentGameModule());
        if (tt == null) return;
        com.openggf.game.soniccd.timetravel.CdTimeWarp.run(tt, player,
                stopsTimeWarpAt(tt.timeZone(), player.getCentreX() & 0xFFFF),
                com.openggf.game.soniccd.objects.common.CdTimeStars::new);
    }

    /**
     * The controlled branch of {@code ObjSonic_Main} ({@code btst #0,oPlayerCtrl}, scddisasm/src/Level/_Objects/Sonic/Main.asm:300-309):
     * every zone but Metallic Madness calls {@code ObjSonic_TimeWarp} there.
     */
    default void whileObjectControlled(AbstractPlayableSprite player) {
        timeWarp(player);
    }

    /** The start of the player's routine every frame: zone state the player's routines read. */
    default void beforePlayerRoutine(AbstractPlayableSprite player) {
    }

    /**
     * The zone test that opens {@code ObjSonic_TimeWarp}: true stops the warp
     * ({@code .StopTimeWarp}). Only Palmtree Panic act 1 has one: in the past and the
     * future, before its first 3D ramp ({@code cmpi.w #$900,oX(a0); bcs.w .StopTimeWarp},
     * Main.asm:574-583).
     */
    default boolean stopsTimeWarpAt(com.openggf.game.soniccd.timetravel.CdTimeZone timeZone, int centreX) {
        return false;
    }

    /**
     * {@code ObjSonic_ChkFlipper} (scddisasm/src/Level/_Objects/Sonic/Main.asm:3112-3158), reached from {@code ObjSonic_CheckJump}
     * while the player stands on an object (Main.asm:1798): Collision Chaos's pinball flipper
     * launch. True when the flipper set the jump's velocity; the jump then skips its headroom
     * test and standard velocity.
     */
    default boolean flipperLaunch(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * The player stands on a pinball flipper ({@code btst #3,oFlags}; {@code cmpi.b
     * #$1E,oID(a1)}): the standing balance test writes Walk and resets the screen instead
     * (Main.asm:884-888).
     */
    /**
     * Rewind: a copy of the stage signals the zone's objects write and this player reads (its typed inputs),
     * or {@code null} for a zone with none. They start cleared at each stage load with the profile.
     */
    default Object captureSignals(com.openggf.game.soniccd.level.CdLevel level) {
        return null;
    }

    /** Rewind: puts back a {@link #captureSignals} copy taken on the same stage. */
    default void restoreSignals(com.openggf.game.soniccd.level.CdLevel level, Object snapshot) {
    }

    default boolean standingOnFlipper(AbstractPlayableSprite player) {
        return false;
    }

    /** {@code MoveObject}'s flipper bias for a player standing on one (src/common/object.asm:65-96); 0 otherwise. */
    default int standingObjectMoveXBias(AbstractPlayableSprite player) {
        return 0;
    }

    /**
     * {@code ObjSonic_Water}'s zone test ({@code cmpi.b #2,zone}, scddisasm/src/Level/_Objects/Sonic/Main.asm:462-467;
     * {@code PlayerCheckWater}, src/objects/player.asm:431-437): the zone's player has water.
     * Then the shared water owner's entry and exit keep the underwater status while the
     * player falls above the level ({@code PlayerCheckWaterSurface}, player.asm:461-464),
     * {@link CdPlayerWater} owns the air countdown, and {@link #waterCurrents} run after
     * the scroll.
     */
    default boolean hasWater() {
        return false;
    }

    /**
     * The water check's position gate: {@code cmpi.b #1,act; cmpi.w #$C8,obj.x(a0); bcs}
     * returns at once left of $C8 in Tidal Tempest act 2 (player.asm:439-443).
     */
    default boolean runsWaterCheckAt(int centreX) {
        return true;
    }

    /** {@code CheckWaterCurrents}' records for this stage (src/r4/main.asm:776-871); empty for none. */
    default java.util.List<CdWaterCurrent> waterCurrents() {
        return java.util.List.of();
    }

    /**
     * {@code PlayerCheckWaterSpawnBubbles}: a splash object $0B at the player's X
     * (player.asm:476-482). True when the zone created it.
     */
    default boolean spawnWaterSplash(AbstractPlayableSprite player) {
        return false;
    }

    /** The zone's $21 bubbles for the air countdown, or null when it has none. */
    default CdPlayerWater.BubbleFactory airBubbles() {
        return null;
    }

    /**
     * <em>Not called yet.</em> The {@code miniSonic} flag's branches in
     * {@code ObjSonic_MdGround}, {@code ObjSonic_StartRoll} and {@code ObjSonic_Animate}
     * (Main.asm:680-684, 1751-1757, 2726-2727, 2951-): the shrunken player's radii and
     * animation set, set by objects such as the spinning discs. True while it applies.
     */
    default boolean miniPlayer(AbstractPlayableSprite player) {
        return false;
    }

    /**
     * The player's {@code layer} byte where the stage's objects compare it with their own {@code oLayer} (the layout's
     * secondary subtype): Metallic Madness's monitors and time posts ({@code cmpi.b #6,zone; tst.b layer}, Monitor and
     * Time Post/Main.asm:105-110, 143-153, 242-259, 308-313). -1 for a stage whose objects never test it.
     */
    default int playerLayerForObjects() {
        return -1;
    }

    /**
     * {@code LoadCheckpoint} and {@code LoadTimeWarpData} restore {@code miniSonic} from their saved copy only in
     * Metallic Madness ({@code cmpi.b #6,zone; bne}, common/respawn_load.asm:23-35, 71-83); elsewhere a no-op.
     */
    default void restoreMiniPlayer(boolean mini) {
    }

    // ---- Wacky Workbench's own player (Level/Wacky Workbench/Objects/Sonic/Main.asm,
    //      src/objects/r6/player.asm): hooks whose defaults are the common player --------

    /**
     * The checks {@code ObjSonic_Main} makes straight after the movement mode on the
     * normal-control path. The common player calls only {@link #blockCheck}
     * (Main.asm:322-324); Wacky Workbench's calls its floor, spark, beam, bar and pole
     * checks there instead ({@code Level/Wacky Workbench/Objects/Sonic/Main.asm:673-677}).
     */
    default void afterMovementMode(AbstractPlayableSprite player) {
        blockCheck(player);
    }

    /** What a stage-owned air state did this frame; see {@link #airState}. */
    enum AirState {
        /** No stage-owned state: the air mode runs its normal movement. */
        NONE,
        /** The state moved the player; only the air level collision follows. */
        HELD,
        /** As {@link #HELD}, with the {@code ObjSonic_TimeWarp} slot between them. */
        HELD_THEN_TIME_WARP
    }

    /**
     * The opening of {@code ObjSonic_MdAir}/{@code ObjSonic_MdJump}, before
     * {@code ObjSonic_Handle3DRamp}. The common player has no such state (Main.asm:713-780).
     */
    default AirState airState(AbstractPlayableSprite player) {
        return AirState.NONE;
    }

    /** {@code ObjSonic_MdAir}'s falling walk reset leaves this animation alone (scddisasm/src/Level/_Objects/Sonic/Main.asm:713-721 has none). */
    default boolean holdsFallingAirAnimation(AbstractPlayableSprite player) {
        return false;
    }

    /** {@code ObjSonic_JumpHeight .NotJump} caps the rise at -$FC0 (scddisasm/src/Level/_Objects/Sonic/Main.asm:1889-1895). */
    default boolean capsUpwardVelocity() {
        return true;
    }

    /**
     * {@code ObjSonic_StartRoll}'s already-rolling branch and $200 floor for a slow
     * rightward roll (Main.asm:1745-1747, 1765-1774).
     */
    default boolean rollEntrySpeedFloor() {
        return true;
    }

    /** {@code ObjSonic_MoveGround .BalanceGround} stops a charge (scddisasm/src/Level/_Objects/Sonic/Main.asm:907-911). */
    default boolean balanceCancelsTimedCharge() {
        return true;
    }

    /** {@code ObjSonic_MoveRoll .Spindash}'s step, {@code move.w #75,d0} (scddisasm/src/Level/_Objects/Sonic/Main.asm:1372) unless overridden. */
    default int timedSpindashStep(int standardStep) {
        return standardStep;
    }

    /**
     * {@code HurtPlayer}'s clears on the path that hurts rather than kills. The common
     * routine reaches {@code .ClearCharge: clr.b oPlayerCharge(a0)} from the shield and
     * ring tests (HurtPlayer, {@code Level/Palmtree Panic/Player Object Collision.asm:229-241};
     * every zone's copy has it, e.g. {@code src/r8/player_object_collide.asm:266-285}); the
     * kill path does not. A zone with its own routine overrides this.
     */
    default void beforeHurt(AbstractPlayableSprite player) {
        if (player != null && player.getMovementManager()
                instanceof com.openggf.sprites.managers.PlayableSpriteMovement movement) {
            movement.cancelTimedCharge();
        }
    }
}

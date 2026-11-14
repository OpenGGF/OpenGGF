package com.openggf.game.soniccd.player;

import com.openggf.game.internal.PlayerStageRoutines;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.soniccd.SonicCdGame;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.zones.CdZones;
import com.openggf.level.LevelManager;
import com.openggf.physics.Direction;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Sonic CD's stage routines on the shared player owner: the zone-independent bodies of
 * {@code ObjSonic_Handle3DRamp}, {@code ObjSonic_SpecialChunks} and the per-stage call
 * sites in {@code ObjSonic_Main} (scddisasm src/Level/_Objects/Sonic/Main.asm), with
 * every zone test answered by the loaded stage's {@link CdZonePlayerProfile}. The
 * profile comes from the stage zone's fragment and is rebuilt when the level changes.
 */
public final class CdPlayerStage implements PlayerStageRoutines {
    /** Layout RAM rows are $80 bytes; the foreground holds the first $40 (Main.asm:788-795). */
    private static final int LAYOUT_FOREGROUND_COLUMNS = 0x40;
    /** {@code cmpi.w #-$800,oYVel(a0)} (Main.asm:812-813). */
    private static final int RAMP_LAUNCH_Y_SPEED = -0x800;
    /** {@code move.w #$600,oXVel(a0)} (Main.asm:815). */
    private static final short RAMP_LAUNCH_X_SPEED = 0x600;

    private final Supplier<SonicCdGame> game;
    private final CdPlayerWater water = new CdPlayerWater();
    private final CdBoredom boredom = new CdBoredom();
    private CdLevel profiledLevel;
    private CdZonePlayerProfile profile = CdZonePlayerProfile.STANDARD;

    public CdPlayerStage(Supplier<SonicCdGame> game) {
        this.game = Objects.requireNonNull(game, "game");
    }

    /**
     * {@code ObjSonic_Handle3DRamp} (scddisasm/src/Level/_Objects/Sonic/Main.asm:782-822): set the bit on a listed chunk;
     * otherwise clear it, and if it was set while rising faster than $800, launch at
     * $600 in the facing direction.
     */
    @Override
    public boolean updatePseudo3dRamp(AbstractPlayableSprite player, boolean wasOnRamp) {
        CdLevel level = level(player);
        if (level == null) return false;
        if (profile(level).isPseudo3dRampChunk(rawChunkAt(level, player))) {
            return true;                                              // bset #1,oPlayerCtrl
        }
        if (wasOnRamp && player.getYSpeed() < RAMP_LAUNCH_Y_SPEED) {  // .NotFound
            player.setXSpeed(player.getDirection() == Direction.LEFT
                    ? (short) -RAMP_LAUNCH_X_SPEED : RAMP_LAUNCH_X_SPEED);
        }
        return false;
    }

    @Override
    public void afterPseudo3dRampCheck(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (level != null) profile(level).timeWarp(player);
    }

    @Override
    public boolean freezesAirMovement(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level != null && profile(level).locksAirMovementAt(player.getCentreX() & 0xFFFF);
    }

    @Override
    public boolean runsSpeedPannedFocus(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level == null || profile(level).extendedCamera();
    }

    @Override
    public boolean pseudo3dRampCentresFocus(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level != null && profile(level).rampCentresFocusAt(player.getCentreX() & 0xFFFF);
    }

    @Override
    public void afterMovementMode(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (level != null) profile(level).afterMovementMode(player);
    }

    @Override
    public boolean runsStageAirState(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (level == null) return false;
        return switch (profile(level).airState(player)) {
            case NONE -> false;
            case HELD -> true;
            case HELD_THEN_TIME_WARP -> {
                afterPseudo3dRampCheck(player);                     // bsr.w ObjSonic_TimeWarp
                yield true;
            }
        };
    }

    /**
     * {@code ObjSonic_MdAir}'s walk reset is skipped while a water current holds the player
     * ({@code tst.b water_current_flag; bne}, src/objects/player.asm:674-675) as well as for
     * the zone's own held poses.
     */
    @Override
    public boolean holdsFallingAirAnimation(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level != null && (profile(level).holdsFallingAirAnimation(player)
                || profile(level).hasWater() && water.currentActive());
    }

    @Override
    public boolean capsUpwardVelocity(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level == null || profile(level).capsUpwardVelocity();
    }

    @Override
    public boolean rollEntrySpeedFloor(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level == null || profile(level).rollEntrySpeedFloor();
    }

    @Override
    public boolean balanceCancelsTimedCharge(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level == null || profile(level).balanceCancelsTimedCharge();
    }

    @Override
    public int timedSpindashStep(AbstractPlayableSprite player, int standardStep) {
        CdLevel level = level(player);
        return level == null ? standardStep : profile(level).timedSpindashStep(standardStep);
    }

    @Override
    public void beforeHurt(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (level != null) profile(level).beforeHurt(player);
    }

    /**
     * {@code Player_ObjCollide}'s gate also skips a giving-up player ({@code cmpi.b #$2B,oAnim(a0); beq.s .NoObjCol},
     * Main.asm:342-348).
     */
    @Override
    public boolean objectCollisionEnabled(AbstractPlayableSprite player) {
        if (CdBoredom.givingUp(player)) return false;
        CdLevel level = level(player);
        return level == null || profile(level).objectCollisionEnabled(player);
    }

    /** {@code ObjSonic_MdGround}'s opening: the boredom check and the give-up (scddisasm/src/Level/_Objects/Sonic/Main.asm:675-692). */
    @Override
    public GroundMode stageGroundMode(AbstractPlayableSprite player) {
        return level(player) == null ? GroundMode.NORMAL : boredom.groundMode(player, miniPlayer(player));
    }

    /** The give-up's death keeps the jump's X speed: {@code ObjSonic_LevelBound .Bottom} skips {@code KillPlayer} (scddisasm/src/Level/_Objects/Sonic/Main.asm:1704-1707). */
    @Override
    public boolean deathKeepsXSpeed(AbstractPlayableSprite player) {
        return CdBoredom.givingUp(player);
    }

    /**
     * The $40-type branch of the touch routine where the stage has layers ({@code tst.b stage_layer} against
     * {@code obj.subtype_2(a1)}, r8/player_object_collide.asm:104-116): an object on the other layer gets no response.
     */
    @Override
    public boolean admitsSpecialTouch(AbstractPlayableSprite player, com.openggf.level.objects.ObjectInstance object) {
        return !(object instanceof com.openggf.game.soniccd.objects.common.CdObject cd) || cd.onPlayerLayerForTouch();
    }

    /** The boredom timer ({@code VInt_Finish}'s {@code RunBoredTimer}). */
    public CdBoredom boredom() {
        return boredom;
    }

    @Override
    public boolean touchesAsInvincible(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level != null && profile(level).touchesAsInvincible(player);
    }

    @Override
    public void beforePlayerRoutine(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (level != null) profile(level).beforePlayerRoutine(player);
    }

    /** {@code btst #0,oPlayerCtrl}'s branch (Main.asm:300-309): the zone's time-warp call. */
    @Override
    public void whileObjectControlled(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (level != null) profile(level).whileObjectControlled(player);
    }

    /** {@code ObjSonic_Hurt}'s {@code jsr ObjSonic_SpecialCol} (scddisasm/src/Level/_Objects/Sonic/Main.asm:2360): the zone's block check. */
    @Override
    public void afterHurtGravity(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (level != null) profile(level).blockCheck(player);
    }

    /** {@code PlayerCheckFlipper} (src/objects/player.asm:2933-2970), called by the jump (player.asm:1715-1718). */
    @Override
    public boolean jumpLaunchedByStandingObject(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level != null && profile(level).flipperLaunch(player);
    }

    /**
     * {@code MoveObject} (src/common/object.asm:65-96): an object standing on a pinball flipper
     * moves by its X speed less $100 ($100 more on a flipped flipper); the speed is unchanged.
     */
    @Override
    public int standingObjectMoveXBias(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level == null ? 0 : profile(level).standingObjectMoveXBias(player);
    }

    /** {@code .CheckBalance}'s flipper branch (Main.asm:884-888; player.asm:832-835). */
    @Override
    public boolean standingObjectHoldsWalk(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level != null && profile(level).standingOnFlipper(player);
    }

    // ---- Water: PlayerCheckWater (src/objects/player.asm:431-482) and CdPlayerWater --------

    @Override
    public boolean runsWaterCheck(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (level == null) return true;
        CdZonePlayerProfile zone = profile(level);
        return !zone.hasWater() || zone.runsWaterCheckAt(player.getCentreX() & 0xFFFF);
    }

    @Override
    public boolean waterExitWaitsWhileFalling(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level != null && profile(level).hasWater();
    }

    @Override
    public void onWaterEntry(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (level != null && profile(level).hasWater()) water.onWaterEntry(player);
    }

    @Override
    public boolean spawnsWaterSplash(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level != null && profile(level).hasWater() && profile(level).spawnWaterSplash(player);
    }

    @Override
    public boolean ownsDrowningCountdown(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level != null && profile(level).hasWater();
    }

    /** The bubbles slot after the player's slot: the air countdown ({@link CdPlayerWater#runCountdown}). */
    public void afterPlayerSlot(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (level == null || !profile(level).hasWater()) return;
        CdPlayerWater.BubbleFactory bubbles = profile(level).airBubbles();
        LevelManager levels = player.currentLevelManagerIfAvailable();
        water.runCountdown(player, levels == null ? 0 : levels.getFrameCounter(),
                bubbles != null ? bubbles : (l, spawn, angle, number) -> false);
    }

    /** The level loop after the scroll ({@code StageMainLoopDraw}, src/r4/main.asm:401-402): {@code CheckWaterCurrents}. */
    public void afterScroll(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (player == null || level == null) return;
        applyControlLock(player, level);
        if (!profile(level).hasWater()) return;
        var flags = player.currentGameModule()
                .getGameService(com.openggf.game.soniccd.objects.shared.SharedSwitchFlags.class);
        water.checkCurrents(player, profile(level).waterCurrents(), i -> flags == null ? 0 : flags.get(i));
    }

    /** The player this stage holds under {@code ctrlLocked}. */
    private AbstractPlayableSprite lockedPlayer;

    /**
     * {@code ObjSonic_Main} ({@code tst.b ctrlLocked.w; bne.s .CtrlLock}, Level/_Objects/Sonic/Main.asm:
     * 296-298): while {@code ctrlLocked} is set the player keeps {@code playerCtrlHold} (the signpost's and big
     * ring's $808, Right; 0 to stand still) instead of the pad. Run after the object pass, so a lock an object sets
     * in one level frame drives the player's next frame, as the player's slot runs first.
     */
    private void applyControlLock(AbstractPlayableSprite player, CdLevel level) {
        var flags = level.flags();
        if (flags.ctrlLocked) {
            int hold = (flags.playerCtrlHold >> 8) & 0xFF;
            int mask = hold & 0x0F;                                        // up, down, left, right
            if ((hold & 0x70) != 0) mask |= AbstractPlayableSprite.INPUT_JUMP;   // A, B or C
            player.setControlLocked(true);
            player.setForcedInputMask(mask);
            lockedPlayer = player;
        } else if (lockedPlayer == player) {
            player.setControlLocked(false);
            player.clearForcedInputMask();
            player.clearLogicalInputState();
            lockedPlayer = null;
        }
    }

    /** {@code water_current_flag}: the shared animation keeps the previous pose instead of Walk. */
    public boolean waterCurrentActive() {
        return water.currentActive();
    }

    /** The stage-wide water state (rewind adapter, the zone's bubble objects). */
    public CdPlayerWater water() {
        return water;
    }

    /**
     * InitStage: the drown timer, the bubbles slot and the current flag start clear (r4/main.asm:212), and so does all
     * of the movement manager's player state (its transient state). Every part of it is RAM the stage load clears: the
     * player object, with {@code oFlags} bit 7 ({@link CdPlayerObjectFlags}), {@code oPlayerCtrl} with the pole angle
     * and hang timer, the charge timer, look state and balance latches ({@code .ClearObjects}, Level/Main.asm:186-192);
     * Wacky Workbench's {@code wwzBeamMode}, inside the {@code flowerPosBuf} block ({@code .ClearFlowers},
     * Level/Main.asm:177-183; Main CPU Variables.inc:148, 187); and the held and previous pad state ({@code playerCtrl},
     * {@code p1CtrlData}, Level/Main.asm:279-281). Without it a stage loaded after another in one session started with
     * the last stage's beam mode, hang timer and held buttons.
     */
    public void resetForLevel() {
        water.resetForLevel();
        boredom.reset();
        if (com.openggf.game.GameServices.sprites().getMainPlayable() instanceof AbstractPlayableSprite player
                && player.getMovementManager() instanceof PlayableSpriteMovement movement) {
            movement.resetTransientState();
        }
    }

    /**
     * {@code ObjSonic_SpecialChunks}, the last call of {@code ObjSonic_Main} after the
     * object pass (Main.asm:350, 2500-2637). The hurt and death routines and debug
     * mode never reach it.
     */
    public void applySpecialChunks(AbstractPlayableSprite player) {
        if (player == null || player.isHurt() || player.getDead() || player.isDebugMode()) return;
        CdLevel level = level(player);
        if (level == null) return;
        CdSpecialChunks chunks = profile(level).specialChunks();
        if (chunks == null) return;
        if (chunks.apply(player, rawChunkAt(level, player))
                && player.getMovementManager() instanceof PlayableSpriteMovement movement) {
            movement.startRollFromStageChunk();                     // jmp ObjSonic_StartRoll
        }
    }

    /**
     * {@code savedMiniSonic} ({@code respawn_shrunk}, Main CPU Variables.inc:144): outside the variables
     * {@code InitStage} clears ({@code .ClearVariables1} starts at {@code flowerPosBuf}, common/main.asm:183-190),
     * so it outlives the stage reload a checkpoint restart is.
     */
    private boolean savedMiniPlayer;

    /** The Sonic CD stage routines of {@code player}'s game, or {@code null} in another game. */
    public static CdPlayerStage of(AbstractPlayableSprite player) {
        var module = player == null ? null : player.currentGameModule();
        return module != null && module.getPhysicsProvider() instanceof SonicCdPhysicsProvider provider ? provider.stage() : null;
    }

    /** {@code tst.b miniSonic} for the loaded stage. */
    public boolean miniPlayer(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        return level != null && profile(level).miniPlayer(player);
    }

    /**
     * {@code ObjCheckpoint_SaveData}'s {@code move.b shrunk_player,respawn_shrunk}; every stage file but Tidal
     * Tempest's assembles it ({@code if STAGE_ZONE<>2}, objects/checkpoint.asm:23-33).
     */
    public void saveCheckpointMiniPlayer(AbstractPlayableSprite player) {
        CdLevel level = level(player);
        if (level != null && !profile(level).hasWater()) savedMiniPlayer = miniPlayer(player);
    }

    /** {@code LoadCheckpoint}'s {@code move.b respawn_shrunk,shrunk_player} (common/respawn_load.asm:71-83). */
    public void restoreCheckpointMiniPlayer(AbstractPlayableSprite player) {
        restoreMiniPlayer(player, savedMiniPlayer);
    }

    /**
     * {@code LoadCheckpoint}/{@code LoadTimeWarpData}'s {@code cmpi.b #6,zone; bne; move.b ...,shrunk_player}
     * (common/respawn_load.asm:23-35, 71-83): the zone's profile takes the saved flag.
     */
    public void restoreMiniPlayer(AbstractPlayableSprite player, boolean mini) {
        CdLevel level = level(player);
        if (level != null) profile(level).restoreMiniPlayer(mini);
    }

    /** A zone's stage signals captured with the stage they belong to, {@code savedMiniSonic} and {@code boredTimer}. */
    public record SignalsSnapshot(CdLevel level, Object signals, boolean savedMiniPlayer, int boredTimer) { }

    /**
     * Rewind for the zone's stage signals ({@code MmzStageSignals}, {@code SszStageLayer}): captured with their
     * stage and restored only into that stage; a stage load clears them by building a new profile.
     */
    public RewindSnapshottable<SignalsSnapshot> signalsRewindAdapter() {
        return signalsRewind;
    }

    private final RewindSnapshottable<SignalsSnapshot> signalsRewind = new RewindSnapshottable<>() {
            @Override public String key() { return "soniccd.zonesignals"; }

            @Override
            public SignalsSnapshot capture() {
                SonicCdGame current = game.get();
                CdLevel level = current == null ? null : current.currentLevel();
                return new SignalsSnapshot(level, level == null ? null : profile(level).captureSignals(level),
                        savedMiniPlayer, boredom.timer());
            }

            @Override
            public void restore(SignalsSnapshot snapshot) {
                SonicCdGame current = game.get();
                CdLevel level = current == null ? null : current.currentLevel();
                savedMiniPlayer = snapshot.savedMiniPlayer();
                boredom.restore(snapshot.boredTimer());
                if (level != null && level == snapshot.level() && snapshot.signals() != null) {
                    profile(level).restoreSignals(level, snapshot.signals());
                }
            }

            @Override public void resetForMissingSnapshot() { }
        };

    public CdZonePlayerProfile profile(CdLevel level) {
        if (level != profiledLevel) {
            SonicCdGame current = game.get();
            var image = current != null && current.currentLevel() == level ? current.currentImage() : null;
            profile = image == null ? CdZonePlayerProfile.STANDARD
                    : CdZones.fragment(level.payload().stage().zone()).playerProfile(level.payload(), image);
            profiledLevel = level;
            CdZonePlayerProfile zone = profile;
            level.setPlayerLoopChunkRule(new CdLevel.PlayerLoopChunkRule() {
                @Override public int chunk(int chunk, boolean path) { return zone.playerLoopChunk(level, chunk, path); }
                @Override public void plainChunk() { zone.playerPlainChunk(level); }
            });
        }
        return profile;
    }

    /**
     * The layout byte at the player's centre as the routines read it: row
     * {@code (oY >> 1) & $380}, column {@code oX high byte & $7F}, loop bit kept
     * (Main.asm:788-795, 2511-2518). Columns past the foreground read 0 here: no stage's
     * foreground reaches them and the player cannot stand there.
     */
    static int rawChunkAt(CdLevel level, AbstractPlayableSprite player) {
        int row = ((player.getCentreY() & 0xFFFF) >> 8) & 7;
        int column = ((player.getCentreX() & 0xFFFF) >> 8) & 0x7F;
        if (column >= LAYOUT_FOREGROUND_COLUMNS || column >= level.getMap().getWidth()
                || row >= level.getMap().getHeight()) {
            return 0;
        }
        int id = level.getMap().getValue(0, column, row) & 0x7F;
        return level.hasLoopFlag(column, row) ? id | 0x80 : id;
    }

    private static CdLevel level(AbstractPlayableSprite player) {
        LevelManager levels = player.currentLevelManagerIfAvailable();
        return levels != null && levels.getCurrentLevel() instanceof CdLevel level ? level : null;
    }

    /** Whether {@code player} is on a 3D ramp ({@code oPlayerCtrl} bit 1): the PPZ scroll's input. */
    public static boolean onPseudo3dRamp(AbstractPlayableSprite player) {
        return player != null && player.getMovementManager() instanceof PlayableSpriteMovement movement
                && movement.isOnPseudo3dRamp();
    }
}

package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.boss.BossExplosionObjectInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * ROM {@code Obj_SSZHPZTeleporter} ($79, sonic3k.asm:90929-91243) in Hidden Palace
 * ({@code $1601}), the Super Emerald sanctuary ({@code $1701}) and Sky Sanctuary ({@code $A00},
 * {@code $A01}).
 *
 * <p>Sky Sanctuary takes the {@code loc_455BA} fall-through, so it keeps
 * {@code make_art_tile(ArtTile_SSZMisc+$88,0,0)} and mapping frame 0, and its lift is
 * {@code (subtype & $3F) * $10} rather than HPZ's whole subtype ({@code loc_45744}). The launch
 * opens the Y wrap ({@code min_Y -$100}, {@code max_Y}/{@code target_max_Y} {@code $1000}), clears
 * {@code Events_bg+$05} so {@code sub_575EA}'s bounds machine runs again, and locks scrolling
 * ({@code loc_4577E}, {@code loc_45790}). A negative subtype is a pad gated on a boss-defeat flag:
 * {@code $AA} on {@code Events_bg+$00} (GHZ) and {@code $F6} on {@code +$02} (MTZ). It starts
 * {@code $20} px sunk and inert, and once the flag is negative rises 1 px every fourth
 * {@code Level_frame_counter} tick. The pad at {@code ($1A40,$670)} is not a teleporter at all but
 * the Mecha Sonic spawner ({@code loc_45A66}).
 *
 * <p>Subtype 0 is a receiving pad. A positive subtype transports a standing Player 1 upward
 * by {@code subtype * $10} pixels: {@code loc_45660} spawns {@code Obj_TeleporterBeam}; while the
 * beam's progress byte is below 8 nothing happens, at 8 the player rolls and
 * {@code Events_bg+$04} makes the receiving pads intangible, until {@code $18} the player drifts
 * up and to the pad centre, then {@code loc_457BE} lifts player and camera {@code $10} per frame
 * and {@code loc_4581C} settles the player with {@code Gradual_SwingOffset}. In HPZ every
 * teleporter is forced to subtype {@code $4A} for Knuckles, and a {@code loc_45B94} route
 * helper is allocated beside it.
 *
 * <p>In HPZ the idle routine owns {@code Normal_palette_line_4} colours 1-2 while on screen
 * ({@code $040C/$0408}) and gates {@code AnPal_HPZ} through {@code Palette_cycle_counters+$00};
 * {@code sub_45866} cycles those colours from {@code word_4670C} while a transport is active.
 */
public final class SSZHPZTeleporterObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, SlopedSolidProvider, TeleporterBeamOwner {
    private static final int HPZ_MAPPING_FRAME = 0xA;
    private static final int HPZ_PALETTE_LINE = 0;
    /** {@code byte_466E8}. */
    private static final byte[] HPZ_SLOPE = {
            9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0xB, 0xD, 0xF, 0x11,
            0x11, 0x11, 0x11, 0x11, 0x11, 0xF, 0xD, 0xB,
            9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9
    };
    private static final SolidObjectParams SOLID =
            SolidObjectParams.of(0x23, 0x10, 0x10);
    /** {@code word_4670C}: (colour 2, colour 1) pairs written by {@code sub_45866}. */
    private static final int[] LIGHT_CYCLE = {
            0x408, 0x40C, 0x60A, 0x60C, 0x80C, 0x80E, 0xA0E, 0xC0E, 0xC0E, 0xE0E,
            0xE0E, 0xE0E, 0xC0E, 0xE0E, 0xA0E, 0xC0E, 0x80C, 0x80E, 0x60A, 0x60C
    };
    private static final String LIGHT_OWNER = "s3k.hpz.teleporterLight";
    private static final String SSZ_LIGHT_OWNER = "s3k.ssz.teleporterLight";
    private static final int KNUCKLES_SUBTYPE = 0x4A;
    /** SSZ never reaches {@code loc_455B2}, so {@code mapping_frame} stays at the SST's zero. */
    private static final int SSZ_MAPPING_FRAME = 0;
    /** {@code make_art_tile(ArtTile_SSZMisc+$88,0,0)} in the init: palette line 0. */
    private static final int SSZ_PALETTE_LINE = 0;
    /** {@code move.w #$20,y_vel(a0)}: a gated pad starts this far below its placement Y. */
    private static final int GATED_SINK = 0x20;
    /** {@code loc_455BA}: {@code x_pos >= $1A00 && y_pos < $680} is the Mecha Sonic spawner. */
    private static final int SPAWNER_MIN_X = 0x1A00;
    private static final int SPAWNER_MAX_Y = 0x680;

    public static final int STATE_IDLE = 0;
    public static final int STATE_CHARGING = 1;
    public static final int STATE_RISING = 2;
    public static final int STATE_SETTLING = 3;
    /** {@code loc_45AD6}, {@code loc_45B1C} and {@code loc_45B8A}: the Sonic/Tails altar ending. */
    public static final int STATE_ALTAR_WAIT = 4;
    public static final int STATE_ALTAR_BEAM = 5;
    public static final int STATE_ALTAR_DONE = 6;
    /** {@code loc_45A66}: the SSZ Mecha Sonic spawner pad. Never solid, never launches. */
    public static final int STATE_MECHA_SPAWNER = 7;
    /** {@code move.w #$20,$32(a0)} in {@code loc_45AB0}. */
    public static final int MECHA_PAD_EXPLOSION_FRAMES = 0x20;

    private int x;
    private int y;
    private int subtype;
    /** {@code $16(a0)}: the placement Y the idle routine re-derives {@code y_pos} from. */
    private int baseY;
    /** {@code y_vel(a0)} ({@code $1A}): how far a gated pad is still sunk. */
    private int sinkOffset;
    private boolean initialized;
    private int state;
    /** {@code $38(a0)} word: non-zero while a beam owns this teleporter. */
    private int beamFlag;
    /** {@code $39(a0)} light timer and {@code $3A(a0)} light index. */
    private int lightTimer;
    private int lightIndex;
    /** {@code $2D(a0)} rise frames remaining. */
    private int riseRemaining;
    /** {@code $2E}/{@code $32}/{@code $36}: {@code Gradual_SwingOffset} speed, offset and direction. */
    private int swingSpeed;
    private int swingOffset;
    private boolean swingReversed;
    /** {@code $3E(a0)}: player Y at the top of the rise. */
    private int settleBaseY;
    private TeleporterBeamObjectInstance beam;
    /** {@code routine(a0)}: {@code st} by the ending helper's {@code loc_45C60}. */
    private boolean altarActivated;
    /** {@code loc_457A2} has cleared Player_1 {@code Status_OnObj} during this charge. */
    private boolean playerOnObjCleared;
    /** {@code $2E(a0)} on the Mecha Sonic spawner: {@code st} once the boss has been allocated. */
    private boolean mechaSpawned;
    /** {@code $30(a0)}: the boss slot, which {@code loc_45AB0} reads back every frame. */
    private SszMechaSonicObjectInstance mechaBoss;
    /** {@code $32(a0)}: the {@code $20}-frame countdown to {@code Delete_Current_Sprite}. */
    private int mechaDeleteTimer;

    private record RewindExtra(int subtype, boolean initialized, int state, int beamFlag,
                               int lightTimer, int lightIndex, int riseRemaining, int swingSpeed,
                               int swingOffset, boolean swingReversed, int settleBaseY,
                               ObjectRefId beamId, boolean altarActivated,
                               boolean playerOnObjCleared, int baseY, int sinkOffset,
                               boolean mechaSpawned, ObjectRefId mechaBossId,
                               int mechaDeleteTimer)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SSZHPZTeleporterObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZHPZTeleporter");
        x = spawn.x();
        y = spawn.y();
        subtype = spawn.subtype() & 0xFF;
    }

    @Override
    public SSZHPZTeleporterObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SSZHPZTeleporterObjectInstance(ctx.spawn());
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        if (!initialized) {
            initialize();
        }
        switch (state) {
            case STATE_CHARGING -> updateCharging(player);
            case STATE_RISING -> updateRising(player);
            case STATE_SETTLING -> updateSettling(player);
            case STATE_ALTAR_WAIT -> updateAltarWait();
            case STATE_ALTAR_BEAM -> updateAltarBeam();
            case STATE_ALTAR_DONE -> advanceLight();
            case STATE_MECHA_SPAWNER -> updateMechaSpawner();
            default -> updateIdle(player);
        }
    }

    /** {@code Obj_SSZHPZTeleporter} init, HPZ branch {@code loc_45574}-{@code loc_455B2}. */
    private void initialize() {
        initialized = true;
        // move.w y_pos(a0),$16(a0): the idle routine rebuilds y_pos from this every frame.
        baseY = y;
        applyGatedSink();
        if (isSkySanctuary()) {
            // loc_45574 falls through to loc_455BA for every zone but $16 and $1701.
            if (x >= SPAWNER_MIN_X && y < SPAWNER_MAX_Y) {
                state = STATE_MECHA_SPAWNER;
            }
            return;
        }
        if (!isHiddenPalace()) {
            return;
        }
        spawnChild(() -> new HpzTeleporterRouteHelperObjectInstance(
                new ObjectSpawn(x, y, 0, 0, 0, false, 0), this));
        HpzZoneRuntimeState hpz = hpzState();
        if (hpz != null && hpz.playerCharacter() == PlayerCharacter.KNUCKLES) {
            subtype = KNUCKLES_SUBTYPE;
        }
    }

    /**
     * Init {@code loc_4554E}-{@code loc_4556A}: a negative subtype is a pad gated on a boss flag.
     * {@code add.b d0,d0} moves subtype bit 6 into the sign, which selects {@code Events_bg+$02}
     * (the MTZ recreation) over {@code Events_bg+$00} (the GHZ one). While that flag is not yet
     * negative the pad starts {@code $20} px low and stays inert.
     *
     * <p>Only Sky Sanctuary places a negative {@code $79} subtype: HPZ act 2 carries {@code $00}
     * and {@code $4A}, the {@code $1701} arena none, so this branch never reads another zone's
     * {@code Events_bg}.
     */
    private void applyGatedSink() {
        if ((subtype & 0x80) == 0) {
            return;
        }
        int flagOffset = (subtype & 0x40) != 0 ? 2 : 0;
        SszZoneRuntimeState ssz = sszState();
        int flag = ssz == null ? 0 : ssz.eventsBgByte(flagOffset);
        if (flag >= 0) {
            sinkOffset = GATED_SINK;
        }
    }

    /** {@code loc_455CC}. */
    private void updateIdle(PlayableEntity player) {
        HpzZoneRuntimeState hpz = hpzState();
        if (hpz != null && isHiddenPalace()) {
            if (isOnScreen()) {
                if (beamFlag == 0) {
                    // move.l #$40C0408,(Normal_palette_line_4+$2).w / st (Palette_cycle_counters+$00).w
                    writeLineFourColours(0x040C, 0x0408);
                    hpz.setPaletteCycleSuppressed(true);
                }
            } else if (beamFlag == 0) {
                hpz.setPaletteCycleSuppressed(false);
            }
        }
        // loc_455F8: move.w $16(a0),d0 / add.w $1A(a0),d0 / move.w d0,y_pos(a0).
        y = (baseY + sinkOffset) & 0xFFFF;
        advanceLight();
        if (subtype == 0 && transportActive(hpz)) {
            return;
        }
        // loc_45616: a sunk pad is not solid at all (tst.w $1A(a0) / bne.s loc_4562C).
        PlayerSolidContactResult contact = sinkOffset != 0 ? null : resolveSolid(player);
        if (subtype == 0) {
            return;
        }
        if (subtype >= 0x80 && !advanceGatedPad()) {
            return;
        }
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        if (contact == null || !contact.standingNow()) {
            return;
        }
        int dx = (sprite.getCentreX() & 0xFFFF) - x + 0xC;
        if ((dx & 0xFFFF) >= 0x18 || sprite.isObjectControlled() || sprite.isHurt()
                || sprite.getDead() || sprite.isDebugMode()) {
            return;
        }
        beam = spawnChild(() -> new TeleporterBeamObjectInstance(
                new ObjectSpawn(x, y, 0, 0, 0, false, 0), this));
        if (beam == null) {
            return;
        }
        sprite.setXSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setYSpeed((short) -1);
        sprite.setSpindash(false);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
        sprite.setAnimationId(Sonic3kAnimationIds.WAIT);
        state = STATE_CHARGING;
        playerOnObjCleared = false;
        beamFlag = -0x100;
        services().playSfx(Sonic3kSfx.CHARGING.id);
    }

    /**
     * {@code tst.b (Events_bg+$04).w} at {@code loc_455F8}: while the arrival script owns the
     * player every subtype-0 receiving pad is intangible.
     */
    private boolean transportActive(HpzZoneRuntimeState hpz) {
        SszZoneRuntimeState ssz = sszState();
        if (ssz != null) {
            return ssz.eventsBgByte(0x04) != 0;
        }
        return hpz != null && hpz.teleporterTransportActive();
    }

    /**
     * {@code loc_4562C}-{@code loc_45660} for a negative subtype: the pad does nothing until its
     * boss flag byte goes negative, then rises one pixel every fourth {@code Level_frame_counter}
     * tick until it is flush, and only then behaves as an ordinary launch pad.
     *
     * @return true when the pad has surfaced and the launch check should run this frame
     */
    private boolean advanceGatedPad() {
        SszZoneRuntimeState ssz = sszState();
        int flagOffset = (subtype & 0x40) != 0 ? 2 : 0;
        int flag = ssz == null ? 0 : ssz.eventsBgByte(flagOffset);
        if (flag >= 0) {
            return false;
        }
        if (sinkOffset == 0) {
            return true;
        }
        if ((services().levelManager().getFrameCounter() & 3) == 0) {
            sinkOffset--;
        }
        return false;
    }

    /** {@code loc_456F4}. */
    private void updateCharging(PlayableEntity player) {
        resolveTransportSolid(player);
        if (!(player instanceof AbstractPlayableSprite sprite) || beam == null) {
            return;
        }
        int progress = beam.progress();
        int frameCounter = services().levelManager().getFrameCounter();
        if (progress < 8) {
            return;
        }
        if (progress == 8) {
            sprite.setOnObject(false);
            playerOnObjCleared = true;
            sprite.setRollingFlagPreserveRadii(true);
            sprite.setAnimationId(Sonic3kAnimationIds.ROLL);
            HpzZoneRuntimeState hpz = hpzState();
            if (hpz != null) {
                hpz.setTeleporterTransportActive(true);
            }
            return;
        }
        if (progress < 0x18) {
            if ((frameCounter & 1) == 0) {
                return;
            }
            NativePositionOps.addYPosPreserveSubpixel(sprite, -1);
            if ((frameCounter & 2) == 0) {
                return;
            }
            int playerX = sprite.getCentreX() & 0xFFFF;
            if (playerX != x) {
                NativePositionOps.addXPosPreserveSubpixel(sprite, playerX < x ? 1 : -1);
            }
            return;
        }
        // loc_45744: HPZ keeps the full subtype as the rise count.
        state = STATE_RISING;
        riseRemaining = isHiddenPalace() ? subtype : subtype & 0x3F;
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
        sprite.setAnimationId(Sonic3kAnimationIds.WALK);
        sprite.setMappingFrame(0);
        // object_control = 3: bit 1 skips Animate_Sonic, so mapping_frame stays 0 through the rise
        // and prev_anim keeps the charge's roll.
        sprite.setObjectMappingFrameControl(true);
        if (isHiddenPalace()) {
            services().camera().setMinX((short) 0xAA0);
        } else {
            // loc_4577E: the launch opens the whole Y wrap for the flight.
            var camera = services().camera();
            camera.setMinY((short) -0x100);
            camera.setMaxY((short) 0x1000);
            camera.setMaxYTarget((short) 0x1000);
        }
        // loc_45790: clr.b (Events_bg+$05).w releases sub_575EA's frozen bounds.
        SszZoneRuntimeState ssz = sszState();
        if (ssz != null) {
            ssz.setEventsBgByte(0x05, 0);
        }
        services().playSfx(Sonic3kSfx.TRANSPORTER.id);
        services().camera().setScrollLocked(true);
    }

    /**
     * {@code loc_45A66}-{@code loc_45AB0}: the pad at {@code ($1A40,$670)} is not a teleporter at
     * all. It raises its own art priority and waits for {@code Camera_Y == Camera_max_Y} to
     * allocate {@code Obj_SSZEndBoss} into {@code _unkFAA4}, then explodes once the boss has
     * moved past it. It calls no solid routine and never launches the player.
     *
     * <p>Slice 7 of the bring-up plan owns {@code Obj_SSZEndBoss}; until that class exists this
     * pad draws and does nothing else, which is recorded as a gap rather than faked.
     */
    private void updateMechaSpawner() {
        // loc_45A72: once $32(a0) is armed nothing else runs; the pad counts down and deletes.
        if (mechaDeleteTimer != 0) {
            mechaDeleteTimer--;
            if (mechaDeleteTimer == 0) {
                ObjectLifetimeOps.deleteNoRespawn(this);
            }
            return;
        }
        if (!mechaSpawned) {
            // loc_45A84: the allocation waits for the final arena's lock to have eased the
            // camera all the way down, which is Camera_Y_pos == Camera_max_Y_pos, not a
            // position test on the player.
            var camera = services().camera();
            if (camera == null
                    || (camera.getY() & 0xFFFF) != (camera.getMaxY() & 0xFFFF)) {
                return;
            }
            // jsr (AllocateObject).l -- the plain one. The boss takes the lowest free slot,
            // which may be below this pad's, so it may not run in the same frame. A failed
            // allocation writes nothing and the gate is simply retried next frame.
            SszMechaSonicObjectInstance boss = spawnFreeChild(() ->
                    new SszMechaSonicObjectInstance(
                            new ObjectSpawn(x, y, 0, 0, 0, false, 0)));
            if (boss == null) {
                return;
            }
            mechaBoss = boss;
            mechaSpawned = true;
            SszZoneRuntimeState ssz = sszState();
            if (ssz != null) {
                // move.w a1,(_unkFAA4).w: the slot SSZ1's background event at :116134 reads,
                // and the one sub_5750C carries when the launch comes.
                ssz.setCarriedObjectSlot(boss.getSlotIndex() & 0xFFFF);
            }
            return;
        }
        // loc_45AB0: movea.w $30(a0),a1 -- the pad reads the boss slot back every frame and
        // explodes as soon as the boss's x_pos is no longer above its own, which is the
        // moment routine 4's run to the left has carried Mecha Sonic past it.
        if (mechaBoss == null || mechaBoss.isDestroyed()) {
            return;
        }
        if ((mechaBoss.getX() & 0xFFFF) > (x & 0xFFFF)) {
            return;
        }
        mechaDeleteTimer = MECHA_PAD_EXPLOSION_FRAMES;
        spawnChild(() -> new BossExplosionObjectInstance(x, y, 0,
                Sonic3kSfx.EXPLODE.id, getPriorityBucket()));
    }

    /** {@code loc_457BE}. */
    private void updateRising(PlayableEntity player) {
        resolveTransportSolid(player);
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        if (riseRemaining != 0) {
            NativePositionOps.addYPosPreserveSubpixel(sprite, -0x10);
            riseRemaining--;
            if (riseRemaining != 0) {
                var camera = services().camera();
                camera.setY((short) (camera.getY() - 0x10));
            }
            return;
        }
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
        // loc_457BE: object_control = 1 re-enables Animate_Sonic; anim 2 equals prev_anim, so the
        // roll script resumes where the charge left it instead of restarting.
        sprite.setObjectMappingFrameControl(false);
        sprite.setAnimationId(Sonic3kAnimationIds.ROLL);
        sprite.setRollingFlagPreserveRadii(true);
        settleBaseY = sprite.getCentreY() & 0xFFFF;
        swingSpeed = 0;
        swingOffset = 0;
        swingReversed = false;
        services().camera().setScrollLocked(false);
        if (beam != null) {
            beam.startContracting();
        }
        state = STATE_SETTLING;
    }

    /** {@code loc_4581C}. */
    private void updateSettling(PlayableEntity player) {
        resolveTransportSolid(player);
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        int offset = gradualSwingOffset(0x20000, 0x800);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, (offset + settleBaseY) & 0xFFFF);
        if (swingSpeed < 0) {
            return;
        }
        ObjectControlState.none().applyTo(sprite);
        sprite.setAnimationId(Sonic3kAnimationIds.WALK);
        // Next frame Player_1 finds no floor under the settled position and sets Status_InAir,
        // after which SolidObjectTopSloped2_1P clears the standing bit (loc_1E338). Drop the
        // retained ride now so the grounded player pass does not treat the pad as support.
        var objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.releaseRidingObject(sprite, this);
        }
        playerOnObjCleared = false;
        HpzZoneRuntimeState hpz = hpzState();
        if (hpz != null) {
            hpz.setTeleporterTransportActive(false);
        }
        state = STATE_IDLE;
    }

    /**
     * {@code loc_45BDA}: the Sonic/Tails route helper replaces this teleporter's code with
     * {@code loc_45AD6}. From here on it is no longer solid and only runs its light cycle.
     */
    void convertToAltarEnding() {
        state = STATE_ALTAR_WAIT;
    }

    /** {@code st routine(a1)} from {@code loc_45C60}. */
    void activateAltarBeam() {
        altarActivated = true;
    }

    /** {@code loc_45AD6}. */
    private void updateAltarWait() {
        advanceLight();
        if (!altarActivated) {
            return;
        }
        beam = spawnChild(() -> new TeleporterBeamObjectInstance(
                new ObjectSpawn(x, y, 0, 0, 0, false, 0), this));
        if (beam == null) {
            return;
        }
        state = STATE_ALTAR_BEAM;
        beamFlag = -0x100;
        services().playSfx(Sonic3kSfx.CHARGING.id);
    }

    /** {@code loc_45B1C}: carry the Knuckles object ({@code _unkFAA4}) up into the beam. */
    private void updateAltarBeam() {
        advanceLight();
        if (beam == null) {
            return;
        }
        CutsceneKnucklesHpzInstance knuckles = HpzKnucklesCutsceneSupport.knuckles(services());
        int progress = beam.progress();
        // cmpi.b #8,$46(a2) / blt.w: the spawn phase toggles the byte between 0 and -1.
        if (progress < 8) {
            return;
        }
        if (progress == 8) {
            HpzZoneRuntimeState hpz = hpzState();
            if (hpz != null) {
                hpz.setKnucklesCutsceneFlag(HpzKnucklesCutsceneSupport.FLAG_BEAM_READY);
            }
            return;
        }
        if (progress >= 0x18) {
            if (knuckles != null) {
                knuckles.clearCodePointer();
            }
            services().playSfx(Sonic3kSfx.TRANSPORTER.id);
            state = STATE_ALTAR_DONE;
            return;
        }
        int frameCounter = services().levelManager().getFrameCounter();
        if ((frameCounter & 1) == 0 || knuckles == null) {
            return;
        }
        knuckles.nativeAddY(-1);
        if ((frameCounter & 2) == 0) {
            return;
        }
        int knucklesX = knuckles.getX() & 0xFFFF;
        if (knucklesX != x) {
            knuckles.nativeAddX(knucklesX < x ? 1 : -1);
        }
    }

    /** {@code Gradual_SwingOffset} (sonic3k.asm:92484-92515); returns the offset's high word. */
    int gradualSwingOffset(int speed, int acceleration) {
        int step = acceleration;
        if (swingReversed) {
            step = -step;
            swingOffset += swingSpeed;
            if (swingOffset < 0) {
                swingSpeed -= step;
            } else {
                swingSpeed = speed;
                swingOffset = 0;
                swingReversed = false;
            }
        } else {
            swingOffset += swingSpeed;
            if (swingOffset > 0) {
                swingSpeed -= step;
            } else {
                swingSpeed = -speed;
                swingOffset = 0;
                swingReversed = true;
            }
        }
        return (short) (swingOffset >> 16);
    }

    /** Beam {@code clr.b $38(a1)} on deletion. */
    @Override
    public void onBeamFinished() {
        beamFlag = 0;
        beam = null;
    }

    /** {@code sub_45866}: light cycle every fourth pass while active. */
    private void advanceLight() {
        if (lightTimer != 0) {
            lightTimer--;
            return;
        }
        lightTimer = 3;
        int next = advanceLightCycle(lightIndex, beamFlag != 0);
        if (next < 0) {
            return;
        }
        lightIndex = next;
        if (isHiddenPalace()) {
            writeLineFourColours(LIGHT_CYCLE[lightIndex / 2 + 1], LIGHT_CYCLE[lightIndex / 2]);
        } else if (isSkySanctuary()) {
            writeSszLightColours(services(), SSZ_LIGHT_OWNER, lightIndex);
        }
    }

    /**
     * {@code loc_4588E}'s zone {@code $A} branch:
     * {@code move.l (a1,d0.w),(Normal_palette_line_3+$18).w}, i.e. one longword — two whole
     * {@code word_4670C} entries in table order — into palette line 2 colours {@code $C} and
     * {@code $D}. The HPZ branch below it writes the same table to line 3 colours 1-2 in the
     * opposite order, which is why the two cannot share one write.
     */
    static void writeSszLightColours(ObjectServices services, String owner, int lightIndex) {
        int first = LIGHT_CYCLE[lightIndex / 2];
        int second = LIGHT_CYCLE[lightIndex / 2 + 1];
        byte[] words = {
                (byte) (first >> 8), (byte) first,
                (byte) (second >> 8), (byte) second
        };
        S3kPaletteWriteSupport.applyContiguousPatch(
                services.paletteOwnershipRegistryOrNull(),
                services.currentLevel(),
                services.graphicsManager(),
                owner,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                2, 0xC, words);
    }

    /**
     * {@code sub_45866} exactly: a three-frame divider in {@code $39(a0)} and a byte index in
     * {@code $3A(a0)} that advances by 4 and wraps at {@code $28}. The cycle runs only while the
     * index has left zero or {@code $38(a0)} is non-zero, so an idle pad holds its base colours.
     *
     * @return the new {@code $3A(a0)} index, or -1 when nothing was written this frame
     */
    static int advanceLightCycle(int lightIndex, boolean active) {
        if (lightIndex == 0 && !active) {
            return -1;
        }
        int next = lightIndex + 4;
        return next >= 0x28 ? 0 : next;
    }

    private void writeLineFourColours(int colourOne, int colourTwo) {
        byte[] words = {
                (byte) (colourOne >> 8), (byte) colourOne,
                (byte) (colourTwo >> 8), (byte) colourTwo
        };
        S3kPaletteWriteSupport.applyContiguousPatch(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                LIGHT_OWNER,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                3, 1, words);
    }

    private PlayerSolidContactResult resolveSolid(PlayableEntity player) {
        var solid = services().solidExecution();
        if (solid == null || solid.isInert()) {
            return null;
        }
        var batch = solid.resolveSolidNowAll();
        return player == null ? null : batch.perPlayer().get(player);
    }

    private boolean isHiddenPalace() {
        return services().romZoneId() == Sonic3kZoneIds.ZONE_HPZ && services().currentAct() == 1;
    }

    /** {@code cmpi.b #$A,(Current_zone).w}: the branch {@code sub_45866} and the init both take. */
    private boolean isSkySanctuary() {
        return services().romZoneId() == Sonic3kZoneIds.ZONE_SSZ;
    }

    private SszZoneRuntimeState sszState() {
        if (tryServices() == null) {
            return null;
        }
        var registry = services().zoneRuntimeRegistry();
        return registry == null ? null : S3kRuntimeStates.currentSsz(registry).orElse(null);
    }

    private HpzZoneRuntimeState hpzState() {
        var registry = services().zoneRuntimeRegistry();
        return registry == null ? null : S3kRuntimeStates.currentHpz(registry).orElse(null);
    }

    public int stateForTest() { return state; }
    int subtypeForTest() { return subtype; }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId beamId = context.identityTable()
                .map(table -> table.encodeObject(beam)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                subtype, initialized, state, beamFlag, lightTimer, lightIndex, riseRemaining,
                swingSpeed, swingOffset, swingReversed, settleBaseY, beamId, altarActivated,
                playerOnObjCleared, baseY, sinkOffset, mechaSpawned,
                context.identityTable().map(table -> table.encodeObject(mechaBoss)).orElse(null),
                mechaDeleteTimer));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            subtype = extra.subtype();
            initialized = extra.initialized();
            state = extra.state();
            beamFlag = extra.beamFlag();
            lightTimer = extra.lightTimer();
            lightIndex = extra.lightIndex();
            riseRemaining = extra.riseRemaining();
            swingSpeed = extra.swingSpeed();
            swingOffset = extra.swingOffset();
            swingReversed = extra.swingReversed();
            settleBaseY = extra.settleBaseY();
            altarActivated = extra.altarActivated();
            playerOnObjCleared = extra.playerOnObjCleared();
            baseY = extra.baseY();
            sinkOffset = extra.sinkOffset();
            beam = extra.beamId() == null ? null
                    : (TeleporterBeamObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.beamId(), true);
            mechaSpawned = extra.mechaSpawned();
            mechaDeleteTimer = extra.mechaDeleteTimer();
            mechaBoss = extra.mechaBossId() == null ? null
                    : (SszMechaSonicObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.mechaBossId(), true);
        }
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getOutOfRangeReferenceX() { return x; }
    @Override public int getPriorityBucket() { return 3; }
    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public byte[] getSlopeData() { return HPZ_SLOPE; }
    @Override public boolean isSlopeFlipped() { return false; }
    // SolidObjCheckSloped2 (sonic3k.asm loc_1E534) lands on y_pos - byte_466E8[d0/2] with no
    // baseline term, and loc_1E45A admits only overlaps 1..16 (bhi / cmpi.w #-$10 / blo).
    @Override public int getSlopeBaseline() { return 0; }
    @Override public Integer getDirectTopLandingOverlapLimit() { return 0x11; }
    // SolidObjectTopSloped2_1P's standing path has no object_control test, so the pad keeps its
    // standing bit (and Player_1 stays grounded) after loc_45660 sets object_control = 1.
    @Override public boolean allowsObjectControlledSolidContacts() {
        return state == STATE_IDLE || state == STATE_CHARGING;
    }

    // SolidObjSloped2 only re-seats a rider whose Status_OnObj is set; once loc_457A2 clears it
    // the standing path keeps the ride without writing y_pos, so the charge lift is not undone.
    @Override public boolean preservesObjectManagedRideWhileNotSolidFor(PlayableEntity player) {
        return playerOnObjCleared
                && (state == STATE_CHARGING || state == STATE_RISING || state == STATE_SETTLING)
                && tryServices() != null && player == services().playerQuery().mainPlayerOrNull();
    }

    /**
     * {@code sub_45856} during the transport: the pad keeps its p1 standing bit and Player_1 stays
     * grounded, but {@code Status_OnObj} remains clear (the managed ride hook re-marks it).
     */
    private void resolveTransportSolid(PlayableEntity player) {
        resolveSolid(player);
        if (playerOnObjCleared && player instanceof AbstractPlayableSprite sprite) {
            sprite.setOnObject(false);
        }
    }
    int mappingFrameForTest() { return HPZ_MAPPING_FRAME; }
    int renderPaletteLineForTest() { return HPZ_PALETTE_LINE; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isSkySanctuary()) {
            // Init: make_art_tile(ArtTile_SSZMisc+$88,0,0) over Map_SSZHPZTeleporter, and no
            // loc_455B2, so the mapping frame stays 0.
            PatternSpriteRenderer sszRenderer = getRenderer(Sonic3kObjectArtKeys.SSZ_TELEPORTER);
            if (sszRenderer != null) {
                sszRenderer.drawFrameIndex(
                        SSZ_MAPPING_FRAME, x, y, false, false, SSZ_PALETTE_LINE);
            }
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_ENTRY_TELEPORTER);
        if (renderer != null) {
            renderer.drawFrameIndex(
                    HPZ_MAPPING_FRAME, x, y, false, false, HPZ_PALETTE_LINE);
        }
    }
}

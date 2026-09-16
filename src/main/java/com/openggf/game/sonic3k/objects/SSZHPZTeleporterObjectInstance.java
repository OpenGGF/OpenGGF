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
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * ROM {@code Obj_SSZHPZTeleporter} ($79, sonic3k.asm:90929-91243) in Hidden Palace
 * ({@code $1601}) and the Super Emerald sanctuary ({@code $1701}).
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
        implements RewindRecreatable, SlopedSolidProvider {
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
    private static final int KNUCKLES_SUBTYPE = 0x4A;

    static final int STATE_IDLE = 0;
    static final int STATE_CHARGING = 1;
    static final int STATE_RISING = 2;
    static final int STATE_SETTLING = 3;

    private final int x;
    private final int y;
    private int subtype;
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

    private record RewindExtra(int subtype, boolean initialized, int state, int beamFlag,
                               int lightTimer, int lightIndex, int riseRemaining, int swingSpeed,
                               int swingOffset, boolean swingReversed, int settleBaseY,
                               ObjectRefId beamId)
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
            default -> updateIdle(player);
        }
    }

    /** {@code Obj_SSZHPZTeleporter} init, HPZ branch {@code loc_45574}-{@code loc_455B2}. */
    private void initialize() {
        initialized = true;
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
        advanceLight();
        if (subtype == 0 && hpz != null && hpz.teleporterTransportActive()) {
            return;
        }
        PlayerSolidContactResult contact = resolveSolid(player);
        if (subtype == 0 || !(player instanceof AbstractPlayableSprite sprite)) {
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
        beamFlag = -0x100;
        services().playSfx(Sonic3kSfx.CHARGING.id);
    }

    /** {@code loc_456F4}. */
    private void updateCharging(PlayableEntity player) {
        resolveSolid(player);
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
        if (isHiddenPalace()) {
            services().camera().setMinX((short) 0xAA0);
        }
        services().playSfx(Sonic3kSfx.TRANSPORTER.id);
        services().camera().setScrollLocked(true);
    }

    /** {@code loc_457BE}. */
    private void updateRising(PlayableEntity player) {
        resolveSolid(player);
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
        resolveSolid(player);
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
        HpzZoneRuntimeState hpz = hpzState();
        if (hpz != null) {
            hpz.setTeleporterTransportActive(false);
        }
        state = STATE_IDLE;
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
    void onBeamFinished() {
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
        if (lightIndex == 0 && beamFlag == 0) {
            return;
        }
        lightIndex += 4;
        if (lightIndex >= 0x28) {
            lightIndex = 0;
        }
        if (isHiddenPalace()) {
            writeLineFourColours(LIGHT_CYCLE[lightIndex / 2 + 1], LIGHT_CYCLE[lightIndex / 2]);
        }
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

    private HpzZoneRuntimeState hpzState() {
        var registry = services().zoneRuntimeRegistry();
        return registry == null ? null : S3kRuntimeStates.currentHpz(registry).orElse(null);
    }

    int stateForTest() { return state; }
    int subtypeForTest() { return subtype; }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId beamId = context.identityTable()
                .map(table -> table.encodeObject(beam)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                subtype, initialized, state, beamFlag, lightTimer, lightIndex, riseRemaining,
                swingSpeed, swingOffset, swingReversed, settleBaseY, beamId));
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
            beam = extra.beamId() == null ? null
                    : (TeleporterBeamObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.beamId(), true);
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
    int mappingFrameForTest() { return HPZ_MAPPING_FRAME; }
    int renderPaletteLineForTest() { return HPZ_PALETTE_LINE; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_ENTRY_TELEPORTER);
        if (renderer != null) {
            renderer.drawFrameIndex(
                    HPZ_MAPPING_FRAME, x, y, false, false, HPZ_PALETTE_LINE);
        }
    }
}

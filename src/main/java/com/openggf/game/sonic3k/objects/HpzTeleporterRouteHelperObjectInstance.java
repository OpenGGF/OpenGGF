package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.save.SaveReason;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * ROM {@code loc_45B94} (sonic3k.asm:91514-91728): the helper {@code Obj_SSZHPZTeleporter}
 * allocates beside every Hidden Palace teleporter.
 *
 * <p>For Knuckles it ends the act: once the camera is above Y {@code $240} and Player 1 has
 * reached X {@code $B00}, it fades the music, saves and starts SSZ act 2 ({@code $A01}).
 *
 * <p>For Sonic and Tails ({@code loc_45BC8}) it deletes itself unless its teleporter is the altar
 * teleporter (X {@code >= $1000}). That one gets its light colours and the {@code AnPal_HPZ} gate,
 * becomes {@code loc_45AD6}, and the helper runs the ending {@code loc_45BF4}: camera max X
 * {@code $1600}, then {@code sub_45C8E} holds each player at X {@code $1628} (Player 2 {@code $1638})
 * and, once both have landed, sends Knuckles to the teleporter ({@code _unkFAB8} bit 6) and
 * starts the beam 60 frames later, shaking the camera up-left for {@code $48} frames. Each
 * player then walks left onto the pad holding C, vanishes with {@code sfx_Transporter}, and
 * Player 1's timer ends the act: {@code SaveGame} and {@code StartNewLevel $A00}.
 */
public final class HpzTeleporterRouteHelperObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    static final int KNUCKLES_EXIT_CAMERA_Y = 0x240;
    static final int KNUCKLES_EXIT_PLAYER_X = 0xB00;
    static final int ALTAR_TELEPORTER_MIN_X = 0x1000;
    static final int ENDING_CAMERA_MAX_X = 0x1600;
    static final int HOLD_X = 0x1628;
    static final int PAD_X = 0x15C0;
    private static final String LIGHT_OWNER = "s3k.hpz.teleporterLight";

    /** Genesis pad bits: left {@code $04}, C {@code $20}. */
    private static final int WALK_LEFT_JUMP = AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_JUMP;

    private SSZHPZTeleporterObjectInstance teleporter;
    private boolean exitRequested;
    private boolean ending;
    /** {@code $30}/{@code $31}: per-player stage; {@code -1} is done. */
    private final int[] stage = new int[2];
    /** {@code $32}/{@code $33}: per-player timers. */
    private final int[] playerTimer = new int[2];
    /** {@code subtype(a0)} and {@code $2D(a0)}. */
    private int sequence;
    private int sequenceTimer;
    /** {@code routine(a0)}: camera shake frames. */
    private int shake;

    private record RewindExtra(boolean exitRequested, ObjectRefId teleporterId, boolean ending,
                               int[] stage, int[] playerTimer, int sequence, int sequenceTimer,
                               int shake)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public HpzTeleporterRouteHelperObjectInstance(ObjectSpawn spawn,
                                                  SSZHPZTeleporterObjectInstance teleporter) {
        super(spawn, "HpzTeleporterRouteHelper");
        this.teleporter = teleporter;
    }

    @Override
    public HpzTeleporterRouteHelperObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzTeleporterRouteHelperObjectInstance(ctx.spawn(), null);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        var registry = services().zoneRuntimeRegistry();
        var hpz = registry == null ? null : S3kRuntimeStates.currentHpz(registry).orElse(null);
        if (hpz != null && hpz.playerCharacter() == PlayerCharacter.KNUCKLES) {
            updateKnucklesExit(player);
            return;
        }
        if (ending) {
            updateEnding(hpz);
            return;
        }
        if (teleporter == null || teleporter.getX() < ALTAR_TELEPORTER_MIN_X) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        // loc_45BDA
        S3kPaletteWriteSupport.applyContiguousPatch(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                LIGHT_OWNER,
                S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                3, 1, new byte[]{0x04, 0x0C, 0x04, 0x08});
        if (hpz != null) {
            hpz.setPaletteCycleSuppressed(true);
        }
        teleporter.convertToAltarEnding();
        ending = true;
    }

    private void updateKnucklesExit(PlayableEntity player) {
        if (exitRequested || player == null) {
            return;
        }
        if ((services().camera().getY() & 0xFFFF) >= KNUCKLES_EXIT_CAMERA_Y) {
            return;
        }
        if ((player.getCentreX() & 0xFFFF) < KNUCKLES_EXIT_PLAYER_X) {
            return;
        }
        exitRequested = true;
        // moveq #cmd_FadeOut / SaveGame / move.w #$A01,d0 / StartNewLevel
        services().fadeOutMusic();
        services().requestSessionSave(SaveReason.PROGRESSION_SAVE);
        services().requestZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 1, true);
    }

    /** {@code loc_45BF4}. */
    private void updateEnding(HpzZoneRuntimeState hpz) {
        if (exitRequested) {
            return;
        }
        var camera = services().camera();
        if (ENDING_CAMERA_MAX_X >= (camera.getX() & 0xFFFF)
                && (camera.getMaxX() & 0xFFFF) != ENDING_CAMERA_MAX_X) {
            camera.setMaxX((short) ENDING_CAMERA_MAX_X);
            camera.setMinY((short) 0);
        }
        boolean sonicAndTails = hpz == null || hpz.playerCharacter() == PlayerCharacter.SONIC_AND_TAILS;
        AbstractPlayableSprite p1 = HpzKnucklesCutsceneSupport.player1(services());
        if (sub45C8E(p1, 0, sonicAndTails)) {
            return;
        }
        if (sonicAndTails) {
            sub45C8E(HpzKnucklesCutsceneSupport.player2(services()), 1, true);
        }
        // loc_45C42
        if (sequence == 1) {
            sequenceTimer = (sequenceTimer - 1) & 0xFF;
            if (sequenceTimer == 0) {
                if (hpz != null) {
                    hpz.setKnucklesCutsceneFlag(HpzKnucklesCutsceneSupport.FLAG_ENDING_JUMP);
                }
                sequence = 2;
                sequenceTimer = 60;
            }
        } else if (sequence != 0) {
            sequenceTimer = (sequenceTimer - 1) & 0xFF;
            if (sequenceTimer == 0) {
                if (teleporter != null) {
                    teleporter.activateAltarBeam();
                }
                sequence = 0;
                sequenceTimer = 0;
            }
        }
        // loc_45C72
        if (shake == 0) {
            return;
        }
        camera.setX((short) (camera.getX() - 1));
        if ((shake & 1) != 0) {
            camera.setY((short) (camera.getY() - 1));
        }
        shake--;
    }

    /**
     * {@code sub_45C8E} for one player. Returns true when {@code StartNewLevel} ended the act,
     * which does not return to the caller in the ROM.
     */
    private boolean sub45C8E(AbstractPlayableSprite sprite, int index, boolean sonicAndTails) {
        if (sprite == null || stage[index] < 0) {
            return false;
        }
        switch (stage[index]) {
            case 0 -> {
                int holdX = index == 0 ? HOLD_X : HOLD_X + 0x10;
                if (holdX < (sprite.getCentreX() & 0xFFFF)) {
                    return false;
                }
                NativePositionOps.writeXPosPreserveSubpixel(sprite, holdX);
                sprite.setXSpeed((short) 0);
                sprite.setGSpeed((short) 0);
                lockController(sprite, index);
                stage[index]++;
            }
            case 1 -> {
                if (sprite.getAir()) {
                    return false;
                }
                playerTimer[index] = index == 0 ? 0xA0 : 0xDC;
                stage[index]++;
            }
            case 2 -> {
                if (sonicAndTails && playerTimer[1 - index] == 0) {
                    return false;
                }
                sequence = 1;
                sequenceTimer = 60;
                shake = 0x48;
                services().camera().setScrollLocked(true);
                stage[index]++;
            }
            case 3 -> {
                if (sequence != 0 || sequenceTimer != 0) {
                    return false;
                }
                playerTimer[index] = (playerTimer[index] - 1) & 0xFF;
                if (playerTimer[index] != 0) {
                    return false;
                }
                sprite.setXSpeed((short) -0x100);
                // move.w #$2424,(a2): left and C held and pressed.
                sprite.setForcedInputMask(WALK_LEFT_JUMP);
                sprite.setForcedJumpPress(true);
                stage[index]++;
            }
            case 4 -> {
                if (PAD_X < (sprite.getCentreX() & 0xFFFF)) {
                    // move.w #$2404,(a2): left and C held, left pressed.
                    sprite.setForcedInputMask(WALK_LEFT_JUMP);
                    return false;
                }
                NativePositionOps.writeXPosPreserveSubpixel(sprite, PAD_X);
                NativePositionOps.addYPosPreserveSubpixel(sprite, -0x80);
                sprite.setXSpeed((short) 0);
                sprite.setGSpeed((short) 0);
                // move.b #3,object_control(a1) / clr.b anim(a1) / clr.b mapping_frame(a1)
                ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
                sprite.setAnimationId(0);
                sprite.setObjectMappingFrameControl(true);
                sprite.setMappingFrame(0);
                sprite.setForcedInputMask(0);
                sprite.clearLogicalInputState();
                services().playSfx(Sonic3kSfx.TRANSPORTER.id);
                if (index == 1) {
                    stage[index] = -1;
                    return false;
                }
                playerTimer[index] = sonicAndTails ? 2 * 60 : 60;
                stage[index]++;
            }
            default -> {
                playerTimer[index] = (playerTimer[index] - 1) & 0xFF;
                if (playerTimer[index] != 0) {
                    return false;
                }
                exitRequested = true;
                services().fadeOutMusic();
                services().requestSessionSave(SaveReason.PROGRESSION_SAVE);
                services().requestZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0, true);
                return true;
            }
        }
        return false;
    }

    /** {@code clr.w (a2)} / {@code st (a3)} on {@code Ctrl_1}/{@code Ctrl_2}. */
    private static void lockController(AbstractPlayableSprite sprite, int index) {
        sprite.setControlLocked(true);
        sprite.clearLogicalInputState();
        sprite.setForcedInputMask(0);
        if (index == 1 && sprite.getCpuController() != null) {
            sprite.getCpuController().setController2SignedLocked(true);
            sprite.getCpuController().clearController2LogicalLatch();
        }
    }

    public int stageForTest(int index) { return stage[index]; }
    public boolean endingForTest() { return ending; }
    public boolean exitRequestedForTest() { return exitRequested; }
    public int shakeForTest() { return shake; }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId teleporterId = context.identityTable()
                .map(table -> table.encodeObject(teleporter)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(exitRequested, teleporterId, ending, stage.clone(),
                        playerTimer.clone(), sequence, sequenceTimer, shake));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            exitRequested = extra.exitRequested();
            teleporter = extra.teleporterId() == null ? null
                    : (SSZHPZTeleporterObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.teleporterId(), true);
            ending = extra.ending();
            System.arraycopy(extra.stage(), 0, stage, 0, 2);
            System.arraycopy(extra.playerTimer(), 0, playerTimer, 0, 2);
            sequence = extra.sequence();
            sequenceTimer = extra.sequenceTimer();
            shake = extra.shake();
        }
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}

package com.openggf.game.sonic3k.objects;

import com.openggf.game.CharacterKey;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * SKL $38, Obj_SOZQuicksand ($3FCD4-$40274): four invisible sand regions.
 * Native player slots have independent held bits; the slide also has a byte
 * recapture timer. Extra followers receive independent native-P2 equivalents.
 */
public final class SozQuicksandObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private final int halfExtent;
    private final int variant;
    // Reuse the existing rewind-aware participant storage; no FBZ rules are involved.
    private final FbzParticipantStateTable participants = new FbzParticipantStateTable(2);

    public SozQuicksandObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SOZQuicksand");
        halfExtent = (spawn.subtype() & 0x3F) << 3;
        variant = spawn.subtype() & 0xC0;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity leader) {
        if (leader instanceof AbstractPlayableSprite player) {
            updatePlayer(player);
        }
        var svc = tryServices();
        if (svc != null) {
            for (var participant : svc.playerQuery().playersFor(
                    ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
                if (participant != leader && participant instanceof AbstractPlayableSprite player) {
                    updatePlayer(player);
                }
            }
        }
        coarseXCullViewport(spawn.x());
    }

    private void updatePlayer(AbstractPlayableSprite player) {
        int slot = participants.slot(player);
        int cooldown = participants.get(slot, 1);
        if (variant == 0x40 && cooldown != 0) {
            participants.set(slot, 1, (cooldown - 1) & 0xFF);
            return;
        }
        if (participants.flag(slot, 0)) {
            updateHeld(player, slot);
        } else {
            acquire(player, slot);
        }
        if (participants.flag(slot, 0) && player.isOnObject()) {
            // sub_3FD4E and the other sand owners set Status_OnObj without
            // SolidObject. Preserve that ownership through inline cleanup.
            var manager = services().objectManager();
            if (manager != null) {
                manager.markObjectSupportThisFrame(player);
            }
        }
    }

    private boolean inside(AbstractPlayableSprite player) {
        // Each cmp/bhs is an unsigned WORD range, including at world wrap.
        int x = player.getCentreX() - spawn.x();
        int y = player.getCentreY() - spawn.y();
        return switch (variant) {
            case 0x40 -> inWordRange(x, halfExtent, halfExtent * 2) && inWordRange(y, 0x10, 0x20);
            case 0x80 -> inWordRange(x, 0x20, 0x40) && inWordRange(y, halfExtent, halfExtent * 2);
            case 0xC0 -> inWordRange(x, halfExtent, halfExtent * 2) && inWordRange(y, 0x33, 0x40);
            default -> inWordRange(x, 0x10, 0x20) && inWordRange(y, halfExtent, halfExtent * 2);
        };
    }

    private static boolean inWordRange(int delta, int bias, int length) {
        return ((delta + bias) & 0xFFFF) < length;
    }

    private boolean flippedY() { return (spawn.renderFlags() & 2) != 0; }

    private void acquire(AbstractPlayableSprite p, int slot) {
        if (!inside(p) || p.isOnObject() || p.getDead() || p.isDebugMode()
                || p.isObjectControlled() || (variant != 0xC0 && p.isHurt())) {
            return;
        }
        // loc_3FFF2 admits a grounded player only for an upward waterfall.
        if (!p.getAir() && !(variant == 0x80 && flippedY())) {
            return;
        }
        if (variant == 0x40 && p.getYSpeed() < 0) {
            return;
        }
        if (variant == 0xC0 && p.getYSpeed() < 0) {
            p.setYSpeed((short) (p.getYSpeed() + 0x68)); // loc_401A4, no capture
            return;
        }
        participants.flag(slot, 0, true);
        p.setOnObject(true);
        p.setRollingJump(false);
        p.setDoubleJumpFlag(0);
        p.setJumping(false);
        if (variant == 0x80) {
            p.setAir(true);
            p.clearRollingFlagPreserveRadii();
            p.setGSpeed((short) 1);
            if (p.getFlipAngle() == 0) {
                p.setFlipAngle(1);
                p.setAnimationId(0);
                p.setFlipsRemaining(0x7F);
                p.setFlipSpeed(8);
            }
            return;
        }
        if (variant == 0xC0) {
            p.clearRollingFlagPreserveRadii();
            p.restoreDefaultRadii();
            p.setAnimationId(0);
            if (p.isHurt()) {
                p.setHurt(false);
                p.setInvulnerableFrames(120);
                p.setSpindash(false);
            }
        } else {
            roll(p);
        }
        p.setXSpeed((short) (p.getXSpeed() >> 1));
        if (variant == 0xC0) {
            p.setGSpeed(p.getXSpeed());
        }
        p.setYSpeed((short) (p.getYSpeed() >> 1));
        // All acquisition branches return; held forces begin next object pass.
    }

    private void updateHeld(AbstractPlayableSprite p, int slot) {
        boolean controlledExit = (variant == 0x80 || variant == 0xC0) && p.isObjectControlled();
        boolean routineExit = variant == 0x80 && (p.isHurt() || p.getDead());
        if (!inside(p) || !p.getAir() || controlledExit || routineExit) {
            release(p, slot);
            if (variant == 0x40) {
                p.setYSpeed((short) 0); // loc_3FF2A
            }
            return;
        }
        if (variant == 0x40) {
            if (p.isLogicalJumpPressActive()) {
                p.setYSpeed((short) 0x400);
                participants.set(slot, 1, 0x1E);
                release(p, slot);
                jumpSound();
            } else {
                p.setYSpeed((short) 0);
                p.setXSpeed((short) (p.getXSpeed() >> 1));
                NativePositionOps.addXPos16_16(p, (spawn.renderFlags() & 1) == 0 ? 0xB000 : -0xB000);
            }
            return;
        }
        if (variant == 0x80) {
            p.setFlipsRemaining(0x7F);
            p.setYSpeed(p.getYSpeed() < 0 ? (short) (p.getYSpeed() + 0x68)
                    : (short) (flippedY() ? -0x330 : 0xA8));
            // loc_40084/loc_400BA read Level_frame_counter, never V_int_run_count.
            if ((services().levelManager().getFrameCounter() & 7) == 0) {
                p.setXSpeed((short) (p.getXSpeed() >> 1));
            }
            return;
        }
        if (p.isLogicalJumpPressActive()) {
            if (variant == 0xC0) {
                release(p, slot);
                // loc_401E6 tests native character_id=2, independent of donation.
                p.setYSpeed((short) (CharacterKey.KNUCKLES.equals(p.characterKey()) ? -0x600 : -0x680));
                roll(p);
                p.setDoubleJumpFlag(0);
                p.setJumping(true);
                jumpSound();
                return;
            }
            p.setYSpeed((short) -0x800);
            jumpSound();
        }
        p.setXSpeed((short) (p.getXSpeed() >> 1));
        if (variant == 0xC0) {
            p.setGSpeed(p.getXSpeed());
        }
        p.setYSpeed(p.getYSpeed() < 0 ? (short) (p.getYSpeed() + 0x68)
                : (short) (flippedY() ? -0x198 : 0xA8));
    }

    private static void roll(AbstractPlayableSprite p) {
        // ROM writes literal $07/$0E radii without moving x_pos/y_pos.
        p.setRollingFlagPreserveRadii(true);
        p.applyCustomRadii(7, 14);
        p.setAnimationId(2);
        p.setRollingJump(false);
    }

    private void release(AbstractPlayableSprite player, int slot) {
        participants.flag(slot, 0, false);
        player.setOnObject(false);
    }

    private void jumpSound() {
        var svc = tryServices();
        if (svc != null) {
            svc.playSfx(Sonic3kSfx.JUMP.id);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Native controller never calls a display routine; terrain supplies the art.
    }
}

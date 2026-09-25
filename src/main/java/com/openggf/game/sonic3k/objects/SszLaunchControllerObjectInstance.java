package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.physics.TrigLookupTable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Obj_57E96: the forced jump, nine spiral turns and StartNewLevel $B00. */
public final class SszLaunchControllerObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private int routine;
    private int timer = 30;
    // Native words $30/$32 and $34/$36. During forced jump, $30's two BYTES are P1/P2 timers.
    private int p1Counter;
    private int p2Counter;
    private int p1Jump;
    private int p2Jump;
    private boolean transitionRequested;

    public SszLaunchControllerObjectInstance(ObjectSpawn spawn) { super(spawn, "SSZLaunch"); }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite leader)) return;
        int clock = services().levelManager().getFrameCounter();
        var state = S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElseThrow();
        if (routine <= 4 && (clock & 15) == 0) services().playSfx(Sonic3kSfx.BIG_RUMBLE.id);
        if (routine == 0) {
            timer = (short) (timer - 1);
            if (timer != 0) return;
            p1Counter = 4;
            routine = 4;
        }
        if (routine == 4 || routine == 8) {
            descendCamera(clock);
            int y = services().camera().getY() & 0xFFFF;
            if (routine == 4 && y < 0x600) return;
            forcedJump(leader, false);
            var partner = nativePartner();
            if (partner != null) forcedJump(partner, true);
            if (routine == 4) {
                if (y < 0x60C) return;
                for (int i = 0; i < 10; i++) state.launch().setDelay(i, word(0x577C6 + 2 * i));
                services().playSfx(Sonic3kSfx.COLLAPSE.id);
                routine = 8;
                return;
            }
            if (y < 0x640) return;
            timer = 15;
            p1Counter = 8;
            p2Counter = 0;
            routine = 12;
        }
        if ((short) state.eventsFg4() >= 0) return;
        if (timer != 0) {
            timer = (short) (timer - 1);
            if (timer != 0) return;
            spawnFreeChild(() -> new SszLaunchCrumbleObjectInstance(
                    new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
        }
        int cameraY = services().camera().getY() & 0xFFFF;
        if (cameraY < 0x5C0) {
            spiral(leader, false);
            var partner = nativePartner();
            if (partner != null) spiral(partner, true);
            if (((clock + 8) & 15) == 0) services().playSfx(Sonic3kSfx.DEATH_EGG_RISE_LOUD.id);
        }
        // The DBF executes high-byte times, including the iteration after SUBQ reaches zero.
        int target = 0x500 - 0x70 * (p1Counter >>> 8) - (p1Counter & 255);
        int delta = (cameraY - target) & 0xFFFF;
        if (cameraY >= (target & 0xFFFF))
            services().camera().setY((short) Math.max(0x110, cameraY - Math.min(2, delta)));
    }

    private void descendCamera(int clock) {
        var camera = services().camera();
        int y = camera.getY() & 0xFFFF;
        if (y >= 0x618 ? (clock & 1) != 0 : (clock & 3) != 0) camera.setY((short) (y + 1));
    }

    private void forcedJump(AbstractPlayableSprite sprite, boolean partner) {
        if (sprite.isObjectControlled()) return;
        int count = partner ? p1Counter & 255 : p1Counter >>> 8;
        if ((byte) count >= 0) {
            count = (count - 1) & 255;
            if (partner) p1Counter = (p1Counter & 0xFF00) | count;
            else p1Counter = (p1Counter & 255) | (count << 8);
            if ((byte) count < 0) {
                // sub_58002 owns Ctrl_*_logical while the hardware pad copy is locked.
                sprite.setForcedInputMask(AbstractPlayableSprite.INPUT_JUMP);
                sprite.setForcedJumpPress(true);
                sprite.writeLogicalInputAndCurrentFollowerHistory(AbstractPlayableSprite.INPUT_JUMP, true);
            }
            return;
        }
        if (sprite.getYSpeed() >= 0) {
            sprite.setForcedInputMask(0);
            NativePositionOps.writeYPosPreserveSubpixel(sprite, 0x7FFF);
            // loc_58016: ANDI.B #$FC,render_flags; MOVE.B #3,object_control.
            // The spiral table owns its orientation, independently of status.facing.
            // Bit 1 also stops Animate_* from putting the old facing flip back while
            // the player waits offscreen for the first spiral mapping.
            sprite.setRenderFlips(false, false);
            sprite.setObjectMappingFrameControl(true);
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
            sprite.setAnimationId(Sonic3kAnimationIds.WALK);
        } else {
            int held = AbstractPlayableSprite.INPUT_JUMP | ((sprite.getCentreX() & 0xFFFF) < 0x1A40
                    ? AbstractPlayableSprite.INPUT_RIGHT : AbstractPlayableSprite.INPUT_LEFT);
            sprite.setForcedInputMask(held);
            sprite.suppressNextJumpPress();
            sprite.writeLogicalInputAndCurrentFollowerHistory(held, false);
        }
    }

    private void spiral(AbstractPlayableSprite sprite, boolean partner) {
        int jump = partner ? p2Jump : p1Jump;
        int counter = partner ? p2Counter : p1Counter;
        if (jump != 0) {
            if ((short) jump < 0) {
                NativePositionOps.addXPos16_16(sprite, sprite.getXSpeed() << 8);
                int oldYVelocity = sprite.getYSpeed();
                sprite.setYSpeed((short) (oldYVelocity + 0x38));
                int y = ((sprite.getCentreY() << 16) | sprite.getYSubpixelRaw()) + (oldYVelocity << 8);
                NativePositionOps.writeYPosPreserveSubpixel(sprite, y >> 16);
                sprite.setSubpixelRaw(sprite.getXSubpixelRaw(), y & 0xFFFF);
                if (sprite.getYSpeed() >= 0) {
                    ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
                    sprite.setObjectMappingFrameControl(true);
                    sprite.setMappingFrame(0);
                    sprite.setAnimationId(Sonic3kAnimationIds.WALK);
                    jump = 180;
                }
            } else if (!partner && !transitionRequested) {
                jump = (short) (jump - 1);
                if (jump == 0) {
                    transitionRequested = true;
                    services().fadeOutMusic();
                    services().requestZoneAndAct(11, 0, true);
                }
            }
            if (partner) p2Jump = jump; else p1Jump = jump;
            return;
        }
        if (counter == 0x910) {
            // ST writes the high byte of the word, leaving its zero low byte.
            if (partner) p2Jump = 0xFF00; else p1Jump = 0xFF00;
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
            sprite.setObjectMappingFrameControl(false);
            sprite.setAir(true);
            sprite.setJumping(true);
            sprite.setAnimationId(Sonic3kAnimationIds.ROLL);
            sprite.setRolling(true);
            sprite.applyCustomRadii(7, 14);
            sprite.setGSpeed((short) 0x800);
            sprite.setXSpeed((short) 0x400);
            sprite.setYSpeed((short) -0x680);
            sprite.setSubpixelRaw(0, 0);
            services().playSfx(Sonic3kSfx.JUMP.id);
            return;
        }
        int phase = (counter + 1) & 255;
        int turns = counter >>> 8;
        if (phase >= 0x70) { phase -= 0x70; turns = (turns + 1) & 255; }
        counter = (turns << 8) | phase;
        if (partner) p2Counter = counter; else p1Counter = counter;
        int angle = byteAt(0x587B4 + phase);
        sprite.setHighPriority((byte) (angle + 0x40) >= 0);
        int x = 0x1A40 + ((TrigLookupTable.sinHex(angle) * 0x4C00) >> 16);
        int vertical = (TrigLookupTable.cosHex(angle) * 0x1C00) >> 16;
        if ((byte) angle < 0) vertical = -vertical - 0x38;
        int y = 0x570 + vertical - (byte) byteAt(0x58824 + phase) - 0x70 * turns;
        if (partner || character() == PlayerCharacter.TAILS_ALONE) {
            y += 4;
            sprite.setPriorityBucket((byte) angle < 0 ? 2 : 1);
        }
        NativePositionOps.writeXPosPreserveSubpixel(sprite, x);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, y);
        sprite.setObjectMappingFrameControl(true);
        sprite.setMappingFrame(byteAt(0x587A8 + ((((angle + 10) & 255) * 3) >> 6)));
    }

    private PlayerCharacter character() {
        return S3kRuntimeStates.resolvePlayerCharacter(services().zoneRuntimeRegistry(), services().configuration());
    }
    private AbstractPlayableSprite nativePartner() {
        return character() == PlayerCharacter.SONIC_AND_TAILS
                && services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite p ? p : null;
    }
    private int word(int address) {
        try { return services().romReader().readU16BE(address); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private int byteAt(int address) {
        try { return services().romReader().readU8(address); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
    public int routineForTest() { return routine; }
    public int counterForTest() { return p1Counter; }
}

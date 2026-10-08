package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.graphics.GLCommand;

import java.util.List;

/**
 * ROM object {@code Obj_LRZLavaFall} -- object id {@code $1F} in the {@code SKL} pointer set
 * (sonic3k.asm:88770-88808, ROM {@code $436A0}). The {@code S3KL} set spends the same id on
 * {@code Obj_LBZLoweringGrapple}.
 *
 * <p>The placement itself is an invisible emitter: Init writes no mappings and no {@code art_tile},
 * only {@code $30(a0) = subtype} (:88771-88773). Every frame it compares the low byte of
 * {@code Level_frame_counter} against that value and does nothing while the clock is below it
 * (:88777-88780), so the fall runs for {@code $100 - subtype} frames out of every 256 and is off
 * for the rest. Lava Reef's seven placements carry {@code $50}, {@code $60} and {@code $70}, giving
 * duty cycles of {@code $B0}, {@code $A0} and {@code $90} frames.
 *
 * <p>While it is running, {@code anim_frame_timer} reloads with 5, so a drop is allocated every
 * sixth frame (:88781-88784). {@code $25(a0)} alternates: the ROM increments it, and on the frame
 * it reaches 2 it is reset to 0 and that drop gets the routine that plays {@code sfx_LavaFall}
 * (:88788-88792), so every second drop is the noisy one.
 *
 * <p>Its tail is {@code Delete_Sprite_If_Not_In_Range} (:88807), so the shared camera unload
 * applies.
 */
public final class LrzLavaFallObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code move.b #5,anim_frame_timer(a0)} (sonic3k.asm:88784). */
    private static final int DROP_PERIOD_RELOAD = 5;
    /** {@code cmpi.b #2,$25(a0)} (sonic3k.asm:88789). */
    private static final int SOUND_ALTERNATION = 2;

    /** {@code btst #0,status(a0)} (:88805): the X-flip gives the drops a longer life. */
    private boolean longLived;
    /** ROM {@code $30(a0)}: the clock threshold the fall runs above. */
    private int clockThreshold;
    /** ROM {@code anim_frame_timer(a0)}, zero in a freshly cleared slot. */
    private int dropTimer;
    /** ROM {@code $25(a0)}: which of every two drops carries the sound routine. */
    private int soundAlternator;

    public LrzLavaFallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZLavaFall");
        this.longLived = (spawn.renderFlags() & 0x1) != 0;
        // moveq #0,d0 / move.b subtype(a0),d0 / move.w d0,$30(a0).
        this.clockThreshold = spawn.subtype() & 0xFF;
    }

    /**
     * {@code Obj_LRZLavaFall} is installed from the SKL object pointer table at ROM
     * {@code $000436A0} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzLavaFallObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzLavaFallObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // move.w (Level_frame_counter).w,d0 / andi.w #$FF,d0 / cmp.w $30(a0),d0 / blo
        // (sonic3k.asm:88777-88780): an unsigned compare against the clock's low byte.
        int clock = levelFrameCounterOrFallback(vIntRunCount) & 0xFF;
        if (clock < clockThreshold) {
            return;
        }
        // subq.b #1,anim_frame_timer(a0) / bpl (:88781-88782).
        dropTimer = (byte) (dropTimer - 1);
        if (dropTimer >= 0) {
            return;
        }
        dropTimer = DROP_PERIOD_RELOAD;
        emitDrop();
    }

    /** {@code loc_436C8} through {@code loc_43746} (sonic3k.asm:88785-88806). */
    private void emitDrop() {
        // addq.b #1,$25(a0) / cmpi.b #2,$25(a0) / blo: the second of every two drops is the one
        // given the routine that plays the sound.
        soundAlternator++;
        boolean playsSound = soundAlternator >= SOUND_ALTERNATION;
        if (playsSound) {
            soundAlternator = 0;
        }
        final int x = getCentreX();
        final int y = getCentreY();
        final boolean sound = playsSound;
        final boolean longer = longLived;
        // AllocateObjectAfterCurrent (:88785) scans forward from this object's own slot, which is
        // spawnChild's contract.
        spawnChild(() -> new LrzLavaFallDropInstance(x, y, sound, longer));
    }

    private int levelFrameCounterOrFallback(int fallback) {
        try {
            return services().levelManager() != null
                    ? services().levelManager().getFrameCounter()
                    : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** ROM x_pos/y_pos are object centres. */
    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return getSpawn().y() & 0xFFFF;
    }

    /** ROM {@code $30(a0)}. */
    public int clockThreshold() {
        return clockThreshold;
    }

    /** ROM {@code anim_frame_timer(a0)}. */
    public int dropTimer() {
        return dropTimer;
    }

    /** ROM {@code $25(a0)}. */
    public int soundAlternator() {
        return soundAlternator;
    }

    public boolean isLongLived() {
        return longLived;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Init writes no mappings and no art_tile (sonic3k.asm:88771-88775): the emitter is
        // invisible and only its drops are drawn.
    }
}

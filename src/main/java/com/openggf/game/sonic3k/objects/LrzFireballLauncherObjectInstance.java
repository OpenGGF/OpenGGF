package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM object {@code Obj_LRZFireballLauncher} -- object id {@code $1B} in the {@code SKL} pointer set
 * (sonic3k.asm:88151-88215, ROM {@code $42B4C}; {@code Map_LRZFireballLauncher} at ROM
 * {@code $42CB2}). The {@code S3KL} set spends the same id on {@code Obj_LBZPipePlug}.
 *
 * <p>A wall nozzle that spits a horizontal fireball on a fixed period. Init stores
 * {@code subtype << 2} in {@code $30(a0)} as that period (:88159-88162), so Lava Reef's eleven
 * subtypes give periods of {@code $40} to {@code $E0} frames. {@code $2E(a0)} is the countdown and
 * starts at the zero of a cleared slot, so the first {@code subq.w #1} already goes negative -- but
 * the shot is gated on {@code render_flags} bit 7 (:88168-88169), which only
 * {@code Sprite_OnScreen_Test} sets, so on the object's first frame the counter is reloaded without
 * firing and the first real shot lands {@code $30 + 1} frames later.
 *
 * <p>The shot copies the launcher's mappings and render flags but is given its own
 * {@code make_art_tile(ArtTile_LRZMisc,0,0)} (:88179), so the same map data is drawn on palette
 * line 0 rather than the launcher's line 3 -- the same trap the shooting trigger's shot has.
 * {@code status(a0)} bit 0, the placement's X-flip, mirrors it: the velocity is negated and the
 * spawn offset moves from {@code +8} to {@code -8}. None of the twenty-seven Lava Reef placements
 * sets that bit (their render flags are {@code 0} or {@code 2}, X-flip clear), so the leftward
 * branch is implemented from the ROM rather than from a placement.
 *
 * <p>The routine's tail is {@code Sprite_OnScreen_Test} (:88194), a draw test, and the object has
 * no unload of its own.
 *
 * <p><b>Gap.</b> The shot sets {@code bset #4,shield_reaction(a1)} (:88182), the bit that makes a
 * shield deflect it. The engine can only express that through a canonical
 * {@code TouchResponseProfile} whose remaining fields were not traced, so it is a plain harmful
 * object and a shielded player absorbs it -- the same gap recorded for the shooting trigger's shot.
 */
public final class LrzFireballLauncherObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:88157). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$10,width_pixels(a0)} / {@code #4,height_pixels(a0)} (:88155-88156). */
    private static final int WIDTH_PIXELS = 0x10;
    private static final int HEIGHT_PIXELS = 0x04;
    /** {@code move.b #2,mapping_frame(a0)} (:88158). */
    private static final int MAPPING_FRAME = 2;
    /** {@code addi.w #8,x_pos(a1)} then {@code subi.w #2*8} when mirrored (:88176, :88191). */
    private static final int SPAWN_OFFSET_X = 8;

    /** {@code btst #0,status(a0)} (:88189): the placement's X-flip mirrors the launcher. */
    private boolean mirrored;
    /** ROM {@code $30(a0)}: {@code subtype << 2}, the reload period in frames. */
    private int period;
    /** ROM {@code $2E(a0)}: the countdown, zero in a freshly cleared slot. */
    private int countdown;
    /**
     * ROM {@code render_flags(a0)} bit 7 as the previous frame's render pass left it. Only
     * {@code Sprite_OnScreen_Test} sets it, so a launcher that has just been created reads it
     * clear and skips its first reload's shot however close the camera is.
     */
    private boolean renderedLastFrame;

    public LrzFireballLauncherObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZFireballLauncher");
        this.mirrored = (spawn.renderFlags() & 0x1) != 0;
        // moveq #0,d0 / move.b subtype(a0),d0 / lsl.w #2,d0 / move.w d0,$30(a0).
        this.period = (spawn.subtype() & 0xFF) << 2;
        this.countdown = 0;
    }

    /**
     * {@code Obj_LRZFireballLauncher} is installed from the SKL object pointer table at ROM
     * {@code $00042B4C} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzFireballLauncherObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzFireballLauncherObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        boolean wasRendered = renderedLastFrame;
        renderedLastFrame = isOnScreen();

        // loc_42BF6 (sonic3k.asm:88165-88194): subq.w #1,$2E(a0) / bpl.
        countdown = (short) (countdown - 1);
        if (countdown >= 0) {
            return;
        }
        // move.w $30(a0),$2E(a0) happens whether or not the shot is made.
        countdown = period;
        // tst.b render_flags(a0) / bpl: bit 7, "was drawn last frame", not "is on screen now".
        if (!wasRendered) {
            return;
        }
        fire();
    }

    /** {@code loc_42C1A} (sonic3k.asm:88171-88193). */
    private void fire() {
        int offset = mirrored ? -SPAWN_OFFSET_X : SPAWN_OFFSET_X;
        final int childX = (getCentreX() + offset) & 0xFFFF;
        final int childY = getCentreY();
        final boolean childMirrored = mirrored;
        // AllocateObjectAfterCurrent (:88170) scans forward from this object's own slot, which is
        // spawnChild's contract.
        spawnChild(() -> new LrzFireballObjectInstance(childX, childY, childMirrored));
        try {
            services().playSfx(Sonic3kSfx.LEVEL_PROJECTILE.id);
        } catch (Exception ignored) {
            // Headless replays can omit the audio backend.
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
    public int period() {
        return period;
    }

    /** ROM {@code $2E(a0)}. */
    public int countdown() {
        return countdown;
    }

    public boolean isMirrored() {
        return mirrored;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_LRZMisc,3,0) (sonic3k.asm:88153) leaves the priority bit clear.
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return WIDTH_PIXELS;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HEIGHT_PIXELS;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_FIREBALL_LAUNCHER);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(MAPPING_FRAME, getX(), getY(), false, false);
    }
}

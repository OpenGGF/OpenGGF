package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * The two camera releases the Lava Reef miniboss leaves behind, ROM {@code loc_78AA8}
 * (sonic3k.asm:160505-160545), allocated by {@code loc_787E0} on the frame the drill dies.
 *
 * <p>Both are the same shape -- wait for the camera to reach an x, write
 * {@code Camera_min_X_pos} there and delete -- so one class carries both as a {@link Gate}:
 * <ul>
 *   <li>{@link Gate#WAITER} is the allocated object itself. It does nothing until
 *       {@code End_of_level_flag} is set ({@code tst.b} at :160506), then installs the palette
 *       rotation, <b>allocates its sibling</b> and falls straight into its own test at
 *       {@code loc_78AE6}: {@code Camera_X_pos >= $940} writes {@code Camera_min_X_pos = $940}.</li>
 *   <li>{@link Gate#SIBLING} is {@code loc_78B08}, the one the waiter allocates. At
 *       {@code Camera_X_pos >= $2C0} it writes {@code Camera_min_X_pos = $2C0} <b>and</b> copies
 *       {@code Pal_LRZ2} over {@code Normal_palette_line_2} and {@code Pal_LRZMiniboss3} over
 *       {@code Normal_palette_line_3} and the line after it.</li>
 * </ul>
 *
 * <p><b>The palette write is the visible half, and it is why act 2 looks wrong without this.</b>
 * {@code Load_Level} (sonic3k.asm:38747-38761) copies the level layout and nothing else -- no
 * palette -- so the seamless act change carries the miniboss's own lines into act 2 and they stay
 * there until the player has walked to {@code $2C0}. Measured on a native BizHawk capture of the
 * recorded movie (`~/Videos/OGGF/lrz-bring-up/native-lrz2-bg/run1`, movie frame 416433 = trace row
 * 26450): 893 frames after its own act change the ROM still draws act 2's crystal blocks in the
 * miniboss palette, gold rather than blue.
 *
 * <p>The ROM's names are one-based, so {@code Normal_palette_line_2} is engine palette line
 * <b>1</b> and {@code line_3} is line <b>2</b> (sonic3k.constants.asm:766-770); {@code sub_78B38}
 * copies {@code $40} bytes from {@code line_3}, which is lines 2 and 3 and stops exactly at the
 * end of {@code Normal_palette}.
 *
 * <p><b>Not modelled, and recorded as owed:</b> {@code word_78EAA}, the thirteen-step palette
 * rotation script the waiter installs through {@code Palette_rotation_custom} and
 * {@code Palette_cycle_counter1} before its own test. It fades five colours of
 * {@code Normal_palette_line_3+$02} down over about a second and is a separate mechanism from the
 * {@code AnPal} cycles the engine already runs.
 */
public final class LrzPostDefeatCameraReleaseInstance extends AbstractObjectInstance
        implements RewindRecreatable {

    /** Which of the two releases this slot is. */
    public enum Gate {
        /** {@code loc_78AA8} into {@code loc_78AE6}: waits on the flag, releases at {@code $940}. */
        WAITER,
        /** {@code loc_78B08}: releases at {@code $2C0} and installs act 2's palette. */
        SIBLING
    }

    /** {@code cmpi.w #$940,(Camera_X_pos).w} (sonic3k.asm:160524). */
    public static final int WAITER_CAMERA_X = 0x0940;
    /** {@code cmpi.w #$2C0,(Camera_X_pos).w} (sonic3k.asm:160537). */
    public static final int SIBLING_CAMERA_X = 0x02C0;
    /** {@code Normal_palette_line_2}, one-based: engine line 1. */
    private static final int ACT2_PALETTE_LINE = 1;
    /** {@code Normal_palette_line_3} and the line after it: engine lines 2 and 3. */
    private static final int MINIBOSS3_FIRST_LINE = 2;
    private static final int PALETTE_LINE_BYTES = 0x20;

    private Gate gate;
    /** ROM: the waiter's own {@code tst.b (End_of_level_flag)} has not passed yet. */
    private boolean awaitingEndOfLevel;
    /** The waiter allocates its sibling exactly once. */
    private boolean siblingAllocated;

    public LrzPostDefeatCameraReleaseInstance(Gate gate) {
        super(new ObjectSpawn(0, 0, Sonic3kObjectIds.MANTIS, 0, 0, false, 0),
                gate == Gate.WAITER ? "LRZPostDefeatCameraReleaseWaiter"
                        : "LRZPostDefeatCameraReleaseSibling");
        this.gate = gate;
        // loc_78B08 is only ever allocated after the flag has already passed, so the sibling has
        // no wait of its own.
        this.awaitingEndOfLevel = gate == Gate.WAITER;
        this.siblingAllocated = false;
        // Neither slot is drawn and neither carries render_flags bit 2, so
        // Offset_ObjectsDuringTransition (sonic3k.asm:104166-104181) leaves both alone as the
        // act change rebases the world.
        setRomWorldPositioned(false);
    }

    /** Probe constructor for rewind recreation and reflection-level tests. */
    public LrzPostDefeatCameraReleaseInstance(ObjectSpawn spawn) {
        this(Gate.WAITER);
    }

    @Override
    public LrzPostDefeatCameraReleaseInstance recreateForRewind(RewindRecreateContext ctx) {
        Gate restored = gate;
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzPostDefeatCameraReleaseInstance(restored));
    }

    /** Both slots outlive the act change and neither is a placement. */
    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (awaitingEndOfLevel) {
            // tst.b (End_of_level_flag).w / beq.w locret_78536 (sonic3k.asm:160506-160507).
            if (!services().gameState().isEndOfLevelFlag()) {
                return;
            }
            awaitingEndOfLevel = false;
            if (!siblingAllocated) {
                // jsr (AllocateObject) / move.l #loc_78B08,(a1) (:160517-160519).
                siblingAllocated = true;
                spawnAfterCurrentSibling(
                        () -> new LrzPostDefeatCameraReleaseInstance(Gate.SIBLING));
            }
            // loc_78AE0 runs the rotation script and falls through into loc_78AE6 on this frame.
        }
        int threshold = gate == Gate.WAITER ? WAITER_CAMERA_X : SIBLING_CAMERA_X;
        if ((services().camera().getX() & 0xFFFF) < threshold) {
            return;
        }
        services().camera().setMinX((short) threshold);
        if (gate == Gate.SIBLING) {
            installActTwoPalette();
        }
        // jmp (Delete_Current_Sprite) (sonic3k.asm:160528, :160547): neither slot is a placement
        // and neither comes back.
        com.openggf.level.objects.ObjectLifetimeOps.destroyLatched(this);
    }

    /**
     * {@code loc_78B08}'s two copies (sonic3k.asm:160540-160545) and {@code sub_78B38}
     * (:160548-160555).
     */
    private void installActTwoPalette() {
        try {
            var rom = services().rom();
            byte[] act2 = rom.readBytes(Sonic3kConstants.PAL_LRZ2_ADDR, PALETTE_LINE_BYTES);
            applyLine(ACT2_PALETTE_LINE, act2);
            byte[] miniboss3 = rom.readBytes(
                    Sonic3kConstants.PAL_LRZ_MINIBOSS_3_ADDR, PALETTE_LINE_BYTES * 2);
            for (int line = 0; line < 2; line++) {
                byte[] slice = new byte[PALETTE_LINE_BYTES];
                System.arraycopy(miniboss3, line * PALETTE_LINE_BYTES, slice, 0, PALETTE_LINE_BYTES);
                applyLine(MINIBOSS3_FIRST_LINE + line, slice);
            }
        } catch (Exception failure) {
            // A headless fixture without the ROM still releases the camera, which is the half
            // that changes where the player may walk.
        }
    }

    private void applyLine(int line, byte[] data) {
        S3kPaletteWriteSupport.applyLine(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                S3kPaletteOwners.ZONE_EVENT_PALETTE_LOAD,
                S3kPaletteOwners.PRIORITY_ZONE_EVENT,
                line,
                data,
                true);
    }

    /** ROM: which of the two slots this is. */
    public Gate gate() {
        return gate;
    }

    /** ROM: the waiter's {@code End_of_level_flag} test has not passed yet. */
    public boolean isAwaitingEndOfLevel() {
        return awaitingEndOfLevel;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Neither slot draws: loc_78AA8's chain never calls Draw_Sprite.
    }
}

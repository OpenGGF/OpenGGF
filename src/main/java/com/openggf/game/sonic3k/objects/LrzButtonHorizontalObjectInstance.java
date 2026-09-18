package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
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
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM object {@code Obj_LRZBigDoor}'s companion switch: {@code Obj_LRZButtonHorizontal}, object id
 * {@code $1C} in the {@code SKL} pointer set (sonic3k.asm:88221-88277, {@code Map_LRZButtonHorizontal}
 * at ROM {@code $42D7C}, {@code Map_LRZButtonHorizontal2} at {@code $42D9E}).
 *
 * <p>Unlike the shared {@code $33 Obj_Button}, which is pressed by standing on it, this one is
 * pressed by walking into its side. The ROM reads that from {@code SolidObjectFull}'s own return:
 * {@code SolidObject_cont} sets bit {@code standing_bit + $D} of {@code d6} on every horizontal
 * push (sonic3k.asm:41501-41512), which is bit 16 for Player 1 and bit 17 for Player 2, so the
 * object's {@code swap d6 / andi.w #3,d6} (:88253-88254) tests exactly "either player is against my
 * side this frame".
 *
 * <p>Subtype decode (:88243-88251, :88256-88257):
 * <ul>
 *   <li>bits 0-3 pick the {@code Level_trigger_array} index;</li>
 *   <li>bit 6 moves the written bit from 0 to 7 ({@code moveq #7,d3});</li>
 *   <li>bit 4 makes the press latch: without it the button clears its bit every frame nobody is
 *       touching it.</li>
 * </ul>
 * None of Lava Reef's 21 placements sets bit 4 or bit 6, so every placed button writes bit 0 and
 * releases when the player steps away; the decode is implemented in full because it is the ROM's.
 *
 * <p>{@code sfx_Switch} plays only on the transition, gated on the whole byte being zero
 * ({@code tst.b (a3)} at :88263), not on this button's own bit.
 */
public final class LrzButtonHorizontalObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, RewindRecreatable,
        RomObjectCodePointerProvider {

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:88227). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$10,width_pixels(a0)} / {@code height_pixels(a0)} (:88225-88226). */
    private static final int WIDTH_PIXELS = 0x10;
    private static final int HEIGHT_PIXELS = 0x10;
    /** {@code move.w #$10,d1 / #$F,d2 / #$10,d3} before {@code SolidObjectFull} (:88236-88239). */
    private static final int SOLID_HALF_WIDTH = 0x10;
    private static final int SOLID_HEIGHT_AIR = 0x0F;
    private static final int SOLID_HEIGHT_GROUND = 0x10;

    private static final int FRAME_UNPRESSED = 0;
    private static final int FRAME_PRESSED = 1;

    // All three are decoded from the spawn's subtype and left non-final so the rewind coverage
    // guard sees them as restorable state; recreateForRewind replays the same spawn.
    /** {@code subtype(a0) & $F} (sonic3k.asm:88243-88244). */
    private int triggerIndex;
    /** {@code btst #6,subtype(a0)} then {@code moveq #7,d3} (:88249-88251). */
    private int triggerBit;
    /** {@code btst #4,subtype(a0)} (:88256): the press latches instead of releasing. */
    private boolean latching;

    private final String artKey;

    /** ROM {@code mapping_frame(a0)}. */
    private int mappingFrame;
    /** This frame's {@code d6} high-word bits 0 and 1, as the solid pass left them. */
    private boolean p1SideContact;
    private boolean p2SideContact;

    public LrzButtonHorizontalObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZButtonHorizontal");
        int subtype = spawn.subtype() & 0xFF;
        this.triggerIndex = subtype & 0x0F;
        this.triggerBit = (subtype & 0x40) != 0 ? 7 : 0;
        this.latching = (subtype & 0x10) != 0;
        this.artKey = actIndexOrZero() != 0
                ? Sonic3kObjectArtKeys.LRZ2_BUTTON_HORIZONTAL
                : Sonic3kObjectArtKeys.LRZ_BUTTON_HORIZONTAL;
    }

    private int actIndexOrZero() {
        try {
            return services().currentAct();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * {@code Obj_LRZButtonHorizontal} sits at ROM {@code $00042CD6} (sonic3k.lst); its whole code
     * block lies in one bank, so the high word {@code sub_13EFC} latches is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzButtonHorizontalObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzButtonHorizontalObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        // The engine runs the solid pass after object update, so the flags read here are the ones
        // the ROM's own SolidObjectFull call left in d6 for the frame that has just been drawn -
        // the same one-frame relationship LrzDashElevatorObjectInstance documents for the standing
        // bits. move.b #0,mapping_frame(a0) (sonic3k.asm:88241).
        boolean touched = p1SideContact || p2SideContact;
        p1SideContact = false;
        p2SideContact = false;
        mappingFrame = FRAME_UNPRESSED;

        if (!touched) {
            // btst #4,subtype(a0) / bne.s loc_42D76 (sonic3k.asm:88256-88257).
            if (!latching) {
                Sonic3kLevelTriggerManager.clearBit(triggerIndex, triggerBit);
            }
            return;
        }
        // loc_42D62 (:88262-88272): the sound is gated on the whole byte, not on this bit.
        if (!Sonic3kLevelTriggerManager.testAny(triggerIndex)) {
            playSwitchSfx();
        }
        Sonic3kLevelTriggerManager.setBit(triggerIndex, triggerBit);
        mappingFrame = FRAME_PRESSED;
    }

    private void playSwitchSfx() {
        try {
            services().playSfx(Sonic3kSfx.SWITCH.id);
        } catch (Exception ignored) {
            // Headless replays can omit the audio backend.
        }
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (!contact.touchSide()) {
            return;
        }
        if (player == nativeP2OrNull()) {
            p2SideContact = true;
        } else {
            p1SideContact = true;
        }
    }

    private PlayableEntity nativeP2OrNull() {
        try {
            return services().playerQuery().nativeP2OrNull();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HEIGHT_AIR, SOLID_HEIGHT_GROUND);
    }

    /**
     * {@code loc_1E154} (sonic3k.asm:41608-41616) re-reads {@code width_pixels(a0)} for the
     * landing's own x test, not the {@code d1} the caller passed. Most full-solid callers set
     * {@code d1 = width_pixels + $B}, which is why the shared default reconstructs the width byte
     * by subtracting {@code $B}. This object breaks that idiom: {@code loc_42D16}
     * (sonic3k.asm:88236-88240) passes {@code d1 = $10} while {@code width_pixels} is also
     * {@code $10} (:88225). Reconstructing gives {@code 5}, a landing strip ten pixels wide
     * instead of thirty-two, and the cold act 1 route's own landing -- at
     * {@code x $10B2} against the placement at {@code x $10C2}, which is {@code relX 0}, the
     * first pixel of the span -- fell straight through it.
     */
    @Override
    public int getTopLandingHalfWidth(PlayableEntity playerEntity, int collisionHalfWidth) {
        return WIDTH_PIXELS;
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fullSolid(false);
    }

    /** ROM {@code subtype(a0) & $F}. */
    public int triggerIndex() {
        return triggerIndex;
    }

    /** 0, or 7 when subtype bit 6 is set. */
    public int triggerBit() {
        return triggerBit;
    }

    public boolean isLatching() {
        return latching;
    }

    public int mappingFrame() {
        return mappingFrame;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_LRZMisc,3,0) / make_art_tile(ArtTile_LRZ2Misc,1,0) leave the
        // priority bit clear (sonic3k.asm:88223, :88231).
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
        PatternSpriteRenderer renderer = getRenderer(artKey);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}

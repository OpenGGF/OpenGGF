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
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * ROM object {@code Obj_LRZDoor} - object id {@code $19} in the {@code SKL} pointer set
 * (sonic3k.asm:88015-88063, {@code Map_LRZDoor} at ROM {@code $429DA}).
 *
 * <p>A solid vertical door that rises out of the way once its trigger byte is non-zero. The gate is
 * {@code tst.b (Level_trigger_array,d0.w)} with {@code d0 = subtype & $F} (:88033-88036): the whole
 * byte, not one bit, so any writer of that index opens it - the {@code $1C} horizontal buttons, the
 * shared {@code $33 Obj_Button}, and the {@code $1D} shooting triggers.
 *
 * <p>Opening is a one-way latch. {@code loc_42974} replaces the routine pointer with
 * {@code loc_42994} and falls straight into it, so the first frame the trigger reads non-zero is
 * also the first frame of travel, and once {@code $2E(a0)} reaches {@code $40} the routine becomes
 * {@code loc_429BC}, which only re-runs the solid box. A door that has opened never closes again,
 * even if its trigger is cleared.
 *
 * <p>The travel itself is {@code GetSineCosine($2E) asr #2}, negated and added to the Y the Init
 * saved in {@code $46(a0)} (:88050-88056). {@code sin($40) = $100}, so the door ends exactly 64
 * pixels above its placement over 64 frames.
 *
 * <p>Act 2 re-skins the same mappings: {@code mapping_frame} 1, art tile base {@code $090} instead
 * of {@code ArtTile_LRZMisc}, and {@code height_pixels} {@code $20} instead of {@code $28}
 * (:88025-88029). The height feeds the solid box directly, so the act 2 door is a shorter obstacle.
 */
public final class LrzDoorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code move.w #$200,priority(a0)} (sonic3k.asm:88021). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0200);
    /** {@code move.b #$10,width_pixels(a0)} (:88019). */
    private static final int WIDTH_PIXELS = 0x10;
    /** {@code move.b #$28,height_pixels(a0)} and the act 2 {@code #$20} (:88020, :88028). */
    private static final int HEIGHT_PIXELS_ACT1 = 0x28;
    private static final int HEIGHT_PIXELS_ACT2 = 0x20;
    /** {@code move.w #$1B,d1} before {@code SolidObjectFull} (:88058). */
    private static final int SOLID_HALF_WIDTH = 0x1B;
    /** {@code cmpi.b #$40,$2E(a0)} (:88044): a quarter turn of the sine table. */
    private static final int OPEN_ANGLE = 0x40;
    /** {@code asr.w #2,d0} (:88053). */
    private static final int TRAVEL_SHIFT = 2;

    private static final int STAGE_WAITING = 0;
    private static final int STAGE_OPENING = 1;
    private static final int STAGE_OPEN = 2;

    /**
     * ROM {@code subtype(a0) & $F}: the {@code Level_trigger_array} index. Non-final so the
     * rewind coverage guard sees it as restorable state; the constructor derives it from the
     * spawn, which {@link #recreateForRewind} replays.
     */
    private int triggerIndex;
    /** ROM {@code height_pixels(a0)}, act-dependent. Non-final so rewind sees restorable state. */
    private int heightPixels;
    /** ROM {@code mapping_frame(a0)}: 0 in act 1, 1 in act 2. */
    private int mappingFrame;
    /** ROM {@code $46(a0)}: the placement Y the sine offset is added to. */
    private int baseY;
    /** ROM {@code $2E(a0)}, a byte that counts one sine step a frame while opening. */
    private int openTimer;
    /** Which of the three routine pointers {@code (a0)} holds. */
    private int stage;

    private final String artKey;

    public LrzDoorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZDoor");
        this.triggerIndex = spawn.subtype() & 0x0F;
        this.baseY = spawn.y() & 0xFFFF;

        boolean actTwo = actIndexOrZero() != 0;
        this.heightPixels = actTwo ? HEIGHT_PIXELS_ACT2 : HEIGHT_PIXELS_ACT1;
        this.mappingFrame = actTwo ? 1 : 0;
        this.artKey = actTwo ? Sonic3kObjectArtKeys.LRZ2_DOOR : Sonic3kObjectArtKeys.LRZ_DOOR;
    }

    private int actIndexOrZero() {
        try {
            return services().currentAct();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * {@code Obj_LRZDoor} sits at ROM {@code $0004292A} (sonic3k.lst); its whole code block lies
     * in one bank, so the high word {@code sub_13EFC} latches is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzDoorObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzDoorObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (stage == STAGE_WAITING) {
            // tst.b (a3,d0.w) / beq.s loc_429BC (sonic3k.asm:88036-88037): the whole byte.
            if (!Sonic3kLevelTriggerManager.testAny(triggerIndex)) {
                return;
            }
            stage = STAGE_OPENING;
            playDoorOpenSfx();
            // loc_42974 writes the new routine pointer and falls into loc_42994 the same frame.
        }
        if (stage != STAGE_OPENING) {
            return;
        }
        // addq.b #1,$2E(a0) / cmpi.b #$40 (sonic3k.asm:88041-88045).
        openTimer = (openTimer + 1) & 0xFF;
        if (openTimer == OPEN_ANGLE) {
            stage = STAGE_OPEN;
        }
        // loc_429A6 runs on the same frame whichever way that branch went.
        updateOpenPosition();
    }

    /**
     * {@code loc_429A6} (sonic3k.asm:88050-88056). {@code GetSineCosine} returns the ROM's
     * {@code SineTable} word for the byte angle; {@code asr.w #2} then {@code neg.w} turns
     * {@code sin($40) = $100} into a 64-pixel rise.
     */
    private void updateOpenPosition() {
        int offset = -(TrigLookupTable.sinHex(openTimer) >> TRAVEL_SHIFT);
        updateDynamicSpawn(getCentreX(), (baseY + offset) & 0xFFFF);
    }

    private void playDoorOpenSfx() {
        try {
            services().playSfx(Sonic3kSfx.DOOR_OPEN.id);
        } catch (Exception ignored) {
            // Headless replays can omit the audio backend.
        }
    }

    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return (baseY - (TrigLookupTable.sinHex(openTimer) >> TRAVEL_SHIFT)) & 0xFFFF;
    }

    /** ROM {@code $2E(a0)}. */
    public int openTimer() {
        return openTimer;
    }

    /** True once {@code (a0)} holds {@code loc_429BC} with the travel finished. */
    public boolean isFullyOpen() {
        return stage == STAGE_OPEN;
    }

    public boolean isOpening() {
        return stage == STAGE_OPENING;
    }

    /** ROM {@code subtype(a0) & $F}. */
    public int triggerIndex() {
        return triggerIndex;
    }

    public int mappingFrame() {
        return mappingFrame;
    }

    public int heightPixels() {
        return heightPixels;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // move.w #$1B,d1 / moveq #0,d2 / move.b height_pixels(a0),d2 / move.w d2,d3 / addq.w #1,d3
        // (sonic3k.asm:88058-88062).
        return SolidObjectParams.of(SOLID_HALF_WIDTH, heightPixels, heightPixels + 1);
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        // SolidObjectFull, no extra edge tolerance.
        return SolidRoutineProfile.fullSolid(false);
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_LRZMisc,2,0) and make_art_tile($090,2,0) both leave the priority
        // bit clear (sonic3k.asm:88017, :88027).
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return WIDTH_PIXELS;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return heightPixels;
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

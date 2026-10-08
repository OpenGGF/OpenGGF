package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
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
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM object {@code Obj_LRZBigDoor} - object id {@code $1A} in the {@code SKL} pointer set
 * (sonic3k.asm:88070-88145; {@code Obj_LRZBigDoor} at ROM {@code $00042A18},
 * {@code Map_LRZBigDoor} at {@code $42B24}). One act 1 placement.
 *
 * <p>Nothing triggers this door; proximity does. {@code loc_42A68} (:88089-88097) tests Player 1
 * only, and both tests are worth reading exactly:
 * <ul>
 *   <li>{@code d0 = y_pos(P1) - y_pos(a0) - $40} then {@code cmpi.w #$80,d0 / bhs} - an
 *       <em>unsigned</em> compare, so the band is {@code y_pos(a0)+$40} up to but not including
 *       {@code y_pos(a0)+$C0}, and a player above the door wraps out of it rather than passing;</li>
 *   <li>{@code d0 = x_pos(P1) - x_pos(a0)} then {@code cmpi.w #$50,d0 / blt} - a <em>signed</em>
 *       compare, so the door only opens once the player is at least {@code $50} to its right.</li>
 * </ul>
 *
 * <p>The travel is {@code GetSineCosine($2E) asr #1} added, not subtracted, to the saved
 * {@code $46(a0)} (:88119-88122): this door sinks {@code $80} pixels over its {@code $40} frames,
 * which is exactly the {@code addi.w #$80,y_pos(a0)} the already-open branch applies at Init
 * (:88081). {@code Screen_shake_flag} is held at {@code -1} for the whole descent and cleared on
 * the last frame (:88104, :88115), and {@code sfx_BigRumble} replays every sixteenth
 * {@code Level_frame_counter+1} (:88123-88128).
 */
public final class LrzBigDoorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code move.w #$280,priority(a0)} (sonic3k.asm:88076). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0280);
    /** {@code move.b #$30,width_pixels(a0)} / {@code #$40,height_pixels(a0)} (:88074-88075). */
    private static final int WIDTH_PIXELS = 0x30;
    private static final int HEIGHT_PIXELS = 0x40;
    /** {@code move.w #$3B,d1 / #$40,d2 / #$41,d3} before {@code SolidObjectFull} (:88130-88133). */
    private static final int SOLID_HALF_WIDTH = 0x3B;
    private static final int SOLID_HEIGHT_AIR = 0x40;
    private static final int SOLID_HEIGHT_GROUND = 0x41;
    /** {@code addi.w #-$40,d0} and {@code cmpi.w #$80,d0} (:88092-88093). */
    private static final int TRIGGER_Y_OFFSET = 0x40;
    private static final int TRIGGER_Y_SPAN = 0x80;
    /** {@code cmpi.w #$50,d0} (:88096). */
    private static final int TRIGGER_X_DISTANCE = 0x50;
    /** {@code cmpi.b #$40,$2E(a0)} (:88112). */
    private static final int OPEN_ANGLE = 0x40;
    /** {@code asr.w #1,d0} (:88121). */
    private static final int TRAVEL_SHIFT = 1;
    /** {@code addi.w #$80,y_pos(a0)} on the already-open branch (:88081). */
    private static final int OPEN_DROP = 0x80;
    /** {@code andi.b #$F,d0} on {@code Level_frame_counter+1} (:88124). */
    private static final int RUMBLE_MASK = 0x0F;

    private static final int STAGE_WAITING = 0;
    private static final int STAGE_OPENING = 1;
    private static final int STAGE_OPEN = 2;

    /** ROM {@code $46(a0)}: the placement Y. Non-final so rewind sees restorable state. */
    private int baseY;
    /** ROM {@code $2E(a0)}. */
    private int openTimer;
    /** Which routine pointer {@code (a0)} holds. */
    private int stage;
    /** The Y the already-open branch leaves in {@code y_pos(a0)}. */
    private int currentY;

    public LrzBigDoorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZBigDoor");
        this.baseY = spawn.y() & 0xFFFF;
        this.currentY = baseY;

        // move.w respawn_addr(a0),d0 / btst #0,(a2) (sonic3k.asm:88077-88080).
        var objectServices = tryServices();
        var manager = objectServices == null ? null : objectServices.objectManager();
        // The shared placement owner now preserves the lower respawn-table bits.
        // Use the original placement, not its moving render position or an X-only
        // zone cache: distinct doors may share X, and stage returns carry this byte.
        if (manager != null && manager.isSpawnStateBitSet(spawn, 0)) {
            this.currentY = (baseY + OPEN_DROP) & 0xFFFF;
            this.openTimer = OPEN_ANGLE;
            this.stage = STAGE_OPEN;
            // Obj_LRZBigDoor adds $80 to y_pos before the already-open draw/solid
            // tail. Publish that same position to rendering and collision too.
            updateDynamicSpawn(spawn.x(), currentY);
        }
    }

    /**
     * {@code Obj_LRZBigDoor} sits at ROM {@code $00042A18} (sonic3k.lst); its whole code block lies
     * in one bank, so the high word {@code sub_13EFC} latches is {@code $0004}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0004;
    }

    @Override
    public LrzBigDoorObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzBigDoorObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (stage == STAGE_WAITING) {
            if (!playerInTriggerRegion(playerEntity)) {
                return;
            }
            stage = STAGE_OPENING;
            writeScreenShakeFlag(-1);
            playSfx(Sonic3kSfx.BIG_RUMBLE.id);
            var objectServices = tryServices();
            if (objectServices != null && objectServices.objectManager() != null) {
                // loc_42A68: bset #0,(respawn_addr); no table entry means no write.
                objectServices.objectManager().setSpawnStateBit(spawn, 0);
            }
            // loc_42A68 writes the new routine pointer and falls into loc_42AAE the same frame.
        }
        if (stage != STAGE_OPENING) {
            return;
        }
        // addq.b #1,$2E(a0) / cmpi.b #$40 (sonic3k.asm:88110-88116).
        openTimer = (openTimer + 1) & 0xFF;
        if (openTimer == OPEN_ANGLE) {
            stage = STAGE_OPEN;
            writeScreenShakeFlag(0);
        }
        // loc_42AC6 runs on the same frame whichever way that branch went.
        currentY = (baseY + (TrigLookupTable.sinHex(openTimer) >> TRAVEL_SHIFT)) & 0xFFFF;
        updateDynamicSpawn(getCentreX(), currentY);

        if ((levelFrameCounterLowByte() & RUMBLE_MASK) == 0) {
            playSfx(Sonic3kSfx.BIG_RUMBLE.id);
        }
    }

    /** {@code loc_42A68} (sonic3k.asm:88089-88097). Player 1 only; the sidekick cannot open it. */
    boolean playerInTriggerRegion(PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return false;
        }
        int deltaY = (player.getCentreY() - baseY - TRIGGER_Y_OFFSET) & 0xFFFF;
        if (deltaY >= TRIGGER_Y_SPAN) {
            return false;
        }
        int deltaX = (short) (player.getCentreX() - getCentreX());
        return deltaX >= TRIGGER_X_DISTANCE;
    }

    private void writeScreenShakeFlag(int value) {
        LrzZoneRuntimeState state = lrzStateOrNull();
        if (state != null) {
            state.screenShake().writeFlag(value);
        }
    }

    private LrzZoneRuntimeState lrzStateOrNull() {
        try {
            return S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private int levelFrameCounterLowByte() {
        try {
            return services().levelManager() != null
                    ? services().levelManager().getFrameCounter() & 0xFF
                    : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void playSfx(int id) {
        try {
            services().playSfx(id);
        } catch (Exception ignored) {
            // Headless replays can omit the audio backend.
        }
    }

    public int getCentreX() {
        return getSpawn().x() & 0xFFFF;
    }

    public int getCentreY() {
        return currentY;
    }

    /** ROM {@code $2E(a0)}. */
    public int openTimer() {
        return openTimer;
    }

    public boolean isFullyOpen() {
        return stage == STAGE_OPEN;
    }

    public boolean isOpening() {
        return stage == STAGE_OPENING;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HEIGHT_AIR, SOLID_HEIGHT_GROUND);
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fullSolid(false);
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_LRZMisc,2,0) (sonic3k.asm:88072) leaves the priority bit clear.
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
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_BIG_DOOR);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(0, getX(), getY(), false, false);
    }
}

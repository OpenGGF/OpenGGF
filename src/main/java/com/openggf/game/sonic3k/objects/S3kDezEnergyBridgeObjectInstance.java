package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * SKL {@code $55}, {@code Obj_DEZEnergyBridge} (sonic3k.asm:93909-93990) with its shared
 * subtype decoder {@code sub_47DDE} (:93879-93902). Thirteen act 1 and twelve act 2
 * placements. S3KL {@code $55} is {@code Obj_MGZHeadTrigger}; the two tables share the number.
 *
 * <p><b>It is a top solid that exists only part of the time.</b> The object has two routines.
 * {@code loc_47E62} is the off state: it recomputes the phase every update and, while the phase
 * is not zero, jumps straight to {@code Delete_Sprite_If_Not_In_Range} (:93931-93933) — no
 * display, no solidity, and an ordinary off-screen despawn. When the phase reaches zero it
 * loads the on-duration into {@code $34(a0)} and falls through into {@code loc_47E8C}, the on
 * state, which decrements that counter, runs {@code SolidObjectTop} for both players, and on
 * the update the counter reaches zero pushes every rider off before switching back.
 *
 * <p><b>The whole cycle is one subtype byte, read as three separate fields.</b>
 * {@code sub_47DDE} (:93884-93896): bits 2-3 index {@code word_47DD6} (:93872-93876) for the
 * period mask {@code $7F}, {@code $FF}, {@code $1FF} or {@code $3FF}; bits 4-7 are a phase
 * index multiplied by a sixteenth of the period ({@code (mask + 1) >> 4}); bits 0-1 give the
 * on-duration as {@code ((subtype & 3) + 2) << 5}, so 64, 96, 128 or 160 frames. The clock is
 * {@code Level_frame_counter}, not {@code V_int_run_count}.
 *
 * <p><b>The init decides whether the bridge starts mid-cycle.</b> {@code sub.w d1,d0 / bcc}
 * (:93915-93916) compares the current phase against the on-duration as an unsigned subtraction.
 * A borrow means the phase is still inside the on window, so the object negates the difference
 * into {@code $34(a0)} and enters the on state part-used (:93917-93920); no borrow installs the
 * off state, which then falls straight through and runs on the same update (:93923-93925).
 * Neither path waits a frame.
 *
 * <p><b>Riders are pushed off explicitly.</b> {@code sub_47EE8} (:93977-93984) tests and clears
 * the object's own per-player standing bit and, only if it was set, clears the player's
 * {@code Status_OnObj} and sets {@code Status_InAir}. A player standing on a bridge that
 * switches off starts falling that frame rather than waiting for terrain to notice.
 *
 * <p>The two drawn pieces are 8x8 tiles {@code $40} apart that slide right by {@code $10} a
 * frame and wrap every four ({@code Map_DEZEnergyBridge}, :94088), selected by
 * {@code Level_frame_counter}'s low byte and 3 (:93945-93947). The zap sound plays every eighth
 * frame while the bridge is on screen (:93948-93953); {@code tst.b render_flags(a0) / bpl} is
 * the on-screen bit, so an off-screen bridge is silent.
 *
 * <p>{@code bset #7,status(a0)} at :93913 sets a bit no routine in this object, in
 * {@code SolidObjectTop}, in {@code Sprite_OnScreen_Test} or in
 * {@code Delete_Sprite_If_Not_In_Range} reads back, so it is recorded rather than modelled.
 */
public final class S3kDezEnergyBridgeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable {

    /** {@code word_47DD6} (:93872-93876), indexed by {@code (subtype & $C) >> 1} as words. */
    private static final int[] PERIOD_MASKS = { 0x7F, 0xFF, 0x1FF, 0x3FF };

    /** {@code move.b #$40,width_pixels(a0)} / {@code move.w #9,d3} (:93911, :93940-93941). */
    private static final int SOLID_HALF_WIDTH = 0x40;
    private static final int SOLID_HALF_HEIGHT = 9;

    /** {@code move.w #$300,priority(a0)} (:93882). */
    private static final int PRIORITY_WORD = 0x300;

    /** {@code Map_DEZEnergyBridge} (:94088): four frames of the sliding pair. */
    private static final int MAPPING_FRAME_MASK = 3;
    /** {@code andi.b #7,d0 / bne} (:93950-93951). */
    private static final int ZAP_PERIOD_MASK = 7;

    /** Which routine is installed: {@code loc_47E8C} when true, {@code loc_47E62} when false. */
    private boolean on;
    /** {@code $34(a0)}. */
    private int onFramesLeft;
    private int mappingFrame;
    private boolean initApplied;
    private boolean drawPublished;
    private boolean renderedOnScreen;

    /**
     * The object's own {@code p1_standing_bit} and {@code p2_standing_bit} (status bits 3 and
     * 4), as the last {@code SolidObjectTop} pass left them. Two booleans rather than two
     * player references, so the pair is ordinary captured object state and the release path
     * resolves the sprites from the player query the way the ROM resolves {@code Player_1} and
     * {@code Player_2} from fixed addresses.
     */
    private boolean playerOneStanding;
    private boolean playerTwoStanding;

    public S3kDezEnergyBridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZEnergyBridge");
    }

    /**
     * {@code move.b subtype(a0),d0 / andi.w #$C,d0 / lsr.w #1,d0 /
     * move.w word_47DD6(pc,d0.w),d0} (:93883-93887). The shift by one turns bits 2-3 into a
     * word offset, so the table index is {@code (subtype & $C) >> 2}.
     *
     * <p>The three subtype fields are read from the immutable placement on demand rather than
     * cached, because a stored copy would be object state a rewind has to carry for nothing:
     * spawn recreation reconstructs the spawn, and the spawn is the whole input.
     */
    private int periodMask() {
        return PERIOD_MASKS[(spawn.subtype() & 0x0C) >> 2];
    }

    /**
     * {@code addq.w #1,d0 / lsr.w #4,d0 / andi.w #$F0,d1 / lsr.w #4,d1 / mulu.w d1,d0}
     * (:93888-93892): a sixteenth of the period, times the subtype's high nibble.
     */
    private int phaseOffset() {
        return ((periodMask() + 1) >> 4) * ((spawn.subtype() & 0xF0) >> 4);
    }

    /** {@code andi.b #3,d1 / addq.b #2,d1 / lsl.w #5,d1} (:93894-93896). */
    private int onDuration() {
        return ((spawn.subtype() & 0x03) + 2) << 5;
    }

    // --- per-update behaviour ---

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        drawPublished = false;
        int levelFrameCounter = levelFrameCounter(vIntRunCount);
        if (!initApplied) {
            initApplied = true;
            applyInit(levelFrameCounter);
        }
        if (!on) {
            // loc_47E62 :93930-93933. A non-zero phase leaves the bridge invisible and
            // non-solid; the engine's own off-screen despawn stands in for
            // Delete_Sprite_If_Not_In_Range.
            if (phase(levelFrameCounter) != 0) {
                return;
            }
            // loc_47E76 :93936-93940, which falls through into loc_47E8C on the same update.
            onFramesLeft = onDuration();
            on = true;
        }
        // loc_47E8C :93942-93943.
        onFramesLeft--;
        if (onFramesLeft == 0) {
            releaseRiders();
            on = false;
        }
        // loc_47EBE :93945-93953. Both the switch-off frame and an ordinary on frame reach
        // this: only the SolidObjectTop call is skipped.
        drawPublished = true; // expiry still reaches Sprite_OnScreen_Test
        mappingFrame = levelFrameCounter & MAPPING_FRAME_MASK;
        if ((levelFrameCounter & ZAP_PERIOD_MASK) == 0 && renderedOnScreen) {
            ObjectServices objectServices = tryServices();
            if (objectServices != null) {
                objectServices.playSfx(Sonic3kSfx.ENERGY_ZAP.id);
            }
        }
    }

    /** {@code Obj_DEZEnergyBridge} :93914-93925. */
    private void applyInit(int levelFrameCounter) {
        // sub.w d1,d0 / bcc.s loc_47E5C: an unsigned subtraction, so the borrow is exactly
        // "the phase has not reached the end of the on window yet".
        int remaining = onDuration() - phase(levelFrameCounter);
        if (remaining > 0) {
            on = true;
            onFramesLeft = remaining;
            return;
        }
        on = false;
    }

    /** {@code move.w (Level_frame_counter).w,d0 / add.w $30(a0),d0 / and.w $32(a0),d0}. */
    private int phase(int levelFrameCounter) {
        return (levelFrameCounter + phaseOffset()) & periodMask();
    }

    /** {@code sub_47EE8} :93977-93984, called for Player 1 and then Player 2. */
    private void releaseRiders() {
        if (playerOneStanding) {
            release(mainPlayerOrNull());
            playerOneStanding = false;
        }
        if (playerTwoStanding) {
            release(sidekickOrNull());
            playerTwoStanding = false;
        }
    }

    /** {@code bclr #Status_OnObj,status(a1) / bset #Status_InAir,status(a1)} (:93980-93981). */
    private static void release(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.setOnObject(false);
            sprite.setAir(true);
        }
    }

    // --- solid contract ---

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        // jsr (SolidObjectTop).l (:93942).
        return true;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        // The off routine never reaches the SolidObjectTop call, and neither does the update
        // that switches the bridge off (:93944 branches past it to loc_47EBE).
        return on;
    }

    @Override
    public boolean usesStickyContactBuffer() {
        return false;
    }

    /**
     * {@code loc_1E45A} :42000-42007. The two rejects are {@code sub.w d1,d0 / bhi} and
     * {@code cmpi.w #-$10,d0 / blo}, both unsigned: the first throws out any positive
     * separation and the second throws out everything unsigned-below {@code $FFF0}, which
     * includes {@code d0 == 0}. The accepted window is therefore {@code -$10 <= d0 <= -1} — the
     * player's feet must already be <em>inside</em> the surface by at least one pixel. The
     * engine's {@code distY} is {@code -d0}, so the exact boundary has to be rejected here.
     *
     * <p>This is the frame the act 2 route turns on: at native row 20299 the player's feet are
     * exactly level with the second bridge's surface ({@code y $03C8} against
     * {@code $03E8 - 9 - $13 - 4}) and the ROM leaves them airborne; they land on row 20300,
     * two pixels further in, at {@code $03CB}.
     */
    @Override
    public boolean rejectsZeroDistanceTopSolidLanding() {
        return true;
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.topSolid(usesStickyContactBuffer());
    }

    @Override
    public int getOnScreenHalfWidth() {
        return SOLID_HALF_WIDTH;
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (player == null) {
            return;
        }
        setStandingBit(player, contact.standing());
    }

    @Override
    public void onSolidContactCleared(PlayableEntity player, int frameCounter) {
        if (player != null) {
            setStandingBit(player, false);
        }
    }

    /** {@code bset d6,status(a0)} / {@code bclr d6,status(a0)} with {@code d6} 3 or 4. */
    private void setStandingBit(PlayableEntity player, boolean standing) {
        if (player.isCpuControlled()) {
            playerTwoStanding = standing;
        } else {
            playerOneStanding = standing;
        }
    }

    private PlayableEntity mainPlayerOrNull() {
        if (tryServices() == null || services().playerQuery() == null) {
            return null;
        }
        return services().playerQuery().mainPlayerOrNull();
    }

    /** {@code lea (Player_2).w,a1} (:93973): the second native player, not every sidekick. */
    private PlayableEntity sidekickOrNull() {
        if (tryServices() == null || services().playerQuery() == null) {
            return null;
        }
        List<PlayableEntity> players = services().playerQuery()
                .playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2);
        return players.size() > 1 ? players.get(1) : null;
    }

    // --- rendering ---

    /**
     * {@code make_art_tile(ArtTile_DEZMisc+$B2,1,0)} (:93880) through
     * {@code Map_DEZEnergyBridge}. Nothing is drawn while the bridge is off, because the off
     * routine jumps to {@code Delete_Sprite_If_Not_In_Range} without displaying.
     * The dispatch that installs that off routine still publishes its last sprite.
     */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawPublished) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_ENERGY_BRIDGE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(),
                (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(PRIORITY_WORD);
    }

    @Override public void refreshPostCameraRenderState() {
        if (drawPublished) renderedOnScreen = isWithinRenderSpriteBounds(0x40, 8);
    }
    @Override public int getOnScreenHalfHeight() { return 8; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(getX(), cameraX, coarseXCullRange());
    }
    public boolean drawPublishedForTest() { return drawPublished; }

    // --- helpers ---

    /**
     * {@code Obj_DEZEnergyBridge} reads {@code Level_frame_counter}, not
     * {@code V_int_run_count} (:93930). {@code LevelManager} owns that clock.
     */
    private int levelFrameCounter(int fallbackFrameCounter) {
        return services().levelManager() != null
                ? services().levelManager().getFrameCounter()
                : fallbackFrameCounter;
    }

    /** Test accessors. */
    public boolean isOnForTest() {
        return on;
    }

    public int onFramesLeftForTest() {
        return onFramesLeft;
    }

    public int mappingFrameForTest() {
        return mappingFrame;
    }

    public int periodMaskForTest() {
        return periodMask();
    }

    public int phaseOffsetForTest() {
        return phaseOffset();
    }

    public int onDurationForTest() {
        return onDuration();
    }
}

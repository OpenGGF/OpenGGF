package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code Obj_SSZCutsceneBridge} ({@code $77}, sonic3k.asm:90405-90470): the bridge at
 * {@code ($320,$C88)} that extends when cutscene Knuckles hits the button.
 *
 * <p>Init records the placement X in {@code $12(a0)} and sets {@code $2E = $C0}, the offset added
 * to that base, so the bridge starts {@code $C0} px to the right of its resting place. With
 * {@code Last_star_post_hit} already set it installs {@code loc_4501A} instead and sits at offset
 * zero — the state a death after the cutscene respawns into.
 *
 * <p>{@code loc_44FA2} waits for {@code Events_bg+$08} (which it reads but never clears), then
 * subtracts 2 from the offset each frame, playing {@code sfx_DoorOpen} on the frame the offset is
 * still {@code $68}. When the offset reaches zero it opens the act: {@code Events_bg+$05} is
 * cleared so {@code sub_575EA} takes over the bounds again, the camera limits become
 * {@code 0 … $19A0} and {@code -$100 … $1000}, and the pseudo-starpost is written
 * ({@code Saved_X/Y = $140,$C6C}, {@code Last_star_post_hit = 1}, {@code Save_Level_Data}, then
 * {@code clr.l (Saved_timer).w}). It never touches {@code Scroll_lock}: the arrival controller
 * cleared that at {@code loc_57D3C}.
 *
 * <p>{@code sub_45026} makes it a {@code SolidObjectTop} of {@code $60}/{@code $10}/{@code 9} and
 * positions three child sprites; the engine draws the mapping's own frames.
 */
public final class SszCutsceneBridgeObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, SolidObjectProvider {
    /** {@code move.w #$C0,$2E(a0)}. */
    static final int START_OFFSET = 0xC0;
    /** {@code cmpi.w #$68,d1} before the subtraction. */
    static final int DOOR_SFX_OFFSET = 0x68;
    /** {@code subq.w #2,d1}. */
    private static final int RETRACT_STEP = 2;
    /** {@code sub_45026}: {@code moveq #$60,d1 / #$10,d2 / #9,d3}. */
    private static final SolidObjectParams SOLID = SolidObjectParams.of(0x60, 0x10, 9);
    /** {@code move.w #$180,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);

    /** {@code loc_44FBA}: the bounds the bridge hands back to the act. */
    static final int CAMERA_MIN_X = 0;
    static final int CAMERA_MAX_X = 0x19A0;
    static final int CAMERA_MIN_Y = -0x100;
    static final int CAMERA_MAX_Y = 0x1000;
    /** The pseudo-starpost, shared with {@code loc_65976}. */
    static final int SAVED_X = 0x140;
    static final int SAVED_Y = 0xC6C;

    /** {@code $12(a0)}: the placement X the offset is added to. */
    @RewindTransient(reason = "Constructor-derived from the immutable spawn record; recreateForRewind rebuilds it.")
    private final int baseX;
    @RewindTransient(reason = "Constructor-derived from the immutable spawn record; recreateForRewind rebuilds it.")
    private final int y;
    /** {@code $2E(a0)}. */
    private int offset = START_OFFSET;
    private boolean initialized;
    private boolean extended;

    private record RewindExtra(int offset, boolean initialized, boolean extended)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszCutsceneBridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZCutsceneBridge");
        this.baseX = spawn.x();
        this.y = spawn.y();
    }

    @Override
    public SszCutsceneBridgeObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SszCutsceneBridgeObjectInstance(ctx.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(offset, initialized, extended));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            offset = extra.offset();
            initialized = extra.initialized();
            extended = extra.extended();
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) {
            initialized = true;
            // tst.b (Last_star_post_hit).w / bne.s: a respawn past the cutscene starts at
            // loc_4501A with the offset already consumed.
            if (starPostHit()) {
                offset = 0;
                extended = true;
            }
        }
        if (extended) {
            return;
        }
        SszZoneRuntimeState state = state();
        if (state == null || state.eventsBgWord(0x08) == 0) {
            return;
        }
        if (offset == DOOR_SFX_OFFSET) {
            services().playSfx(Sonic3kSfx.DOOR_OPEN.id);
        }
        offset -= RETRACT_STEP;
        if (offset != 0) {
            return;
        }
        extended = true;
        state.setEventsBgByte(0x05, 0);
        var camera = services().camera();
        camera.setMinX((short) CAMERA_MIN_X);
        camera.setMaxX((short) CAMERA_MAX_X);
        camera.setMinY((short) CAMERA_MIN_Y);
        camera.setMaxY((short) CAMERA_MAX_Y);
        camera.setMaxYTarget((short) CAMERA_MAX_Y);
        SszCheckpointOps.writeCutsceneStarPost(services(), SAVED_X, SAVED_Y, true);
    }

    private boolean starPostHit() {
        var checkpoint = services().checkpointState();
        return checkpoint != null && checkpoint.getStarPostActivationMark() > 0;
    }

    private SszZoneRuntimeState state() {
        return S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
    }

    int offsetForTest() { return offset; }
    boolean extendedForTest() { return extended; }

    @Override public int getX() { return (baseX + offset) & 0xFFFF; }
    @Override public int getY() { return y; }
    @Override public int getOutOfRangeReferenceX() { return baseX; }
    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.topSolid(false);
    }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x60; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_CUTSCENE_BRIDGE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, getX(), y, false, false);
        }
    }
}

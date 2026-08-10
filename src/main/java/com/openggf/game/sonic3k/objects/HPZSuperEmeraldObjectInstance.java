package com.openggf.game.sonic3k.objects;

import com.openggf.game.EmeraldRewardKind;
import com.openggf.game.PlayableEntity;
import com.openggf.game.SpecialStageEntryRequest;
import com.openggf.game.sonic3k.S3kEmeraldProgression;
import com.openggf.game.sonic3k.S3kSanctuaryRuntimeState;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/** ROM {@code Obj_HPZSuperEmerald} (SKL object $B4). */
public final class HPZSuperEmeraldObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, SolidObjectProvider {
    private static final int[][] POSITIONS = {
            {0x1640, 0x368}, {0x15E0, 0x3A0}, {0x16A0, 0x3A0},
            {0x15A0, 0x350}, {0x16E0, 0x350}, {0x1550, 0x390},
            {0x1730, 0x390}
    };
    private static final SolidObjectParams SOLID = new SolidObjectParams(0x1B, 0x10, 0x10);
    private static final int[] COMPLETED_PALETTE_LINES = {2, 0, 2, 0, 0, 1, 3};
    private static final int[] KNUCKLES_COMPLETED_PALETTE_LINES = {2, 1, 2, 1, 0, 0, 3};

    public enum Display { GRAY, COLORED }
    public enum SanctuaryPlayerMode { SONIC_TAILS, KNUCKLES }

    private int subtype;
    private HPZSSEntryControlObjectInstance parentRef;
    private S3kEmeraldProgression progression;
    private S3kSanctuaryRuntimeState runtime;
    private boolean requestPublished;
    private int lastFrameCounter;

    private record RewindExtra(
            S3kSanctuaryRuntimeState.Snapshot runtime,
            boolean requestPublished,
            int lastFrameCounter,
            int subtype,
            ObjectRefId parentId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public HPZSuperEmeraldObjectInstance(ObjectSpawn spawn) {
        this(spawn, null, null);
    }

    public HPZSuperEmeraldObjectInstance(
            ObjectSpawn spawn, S3kEmeraldProgression progression, S3kSanctuaryRuntimeState runtime) {
        super(spawn, "HPZSuperEmerald");
        subtype = Math.min(6, spawn.subtype());
        this.progression = progression;
        this.runtime = runtime;
        if (progression != null && progression.state(subtype) == S3kEmeraldProgression.EmeraldState.ABSENT) {
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    public HPZSuperEmeraldObjectInstance(
            ObjectSpawn spawn, HPZSSEntryControlObjectInstance parent) {
        this(spawn, parent.progressionForChild(), parent.runtimeForChild());
        parentRef = parent;
    }

    @Override
    public HPZSuperEmeraldObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HPZSuperEmeraldObjectInstance(ctx.spawn());
    }

    private void ensureState() {
        if (parentRef != null) {
            progression = parentRef.progressionForChild();
            runtime = parentRef.runtimeForChild();
        }
        if (progression == null) {
            progression = S3kEmeraldProgression.from(services().gameState());
        }
        if (runtime == null) {
            runtime = new S3kSanctuaryRuntimeState(progression, true);
        }
        if (progression.state(subtype) == S3kEmeraldProgression.EmeraldState.ABSENT) {
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        lastFrameCounter = vIntRunCount;
        ensureState();
        if (isDestroyed() || requestPublished) {
            return;
        }
        if (runtime.phase() == S3kSanctuaryRuntimeState.Phase.SELECTING
                && runtime.selectedStage() == subtype) {
            updateSelection();
            return;
        }
        if (isSelectable() && tryServices() != null && services().objectManager() != null
                && services().objectManager().isRidingObject(player, this)) {
            beginSelection(player instanceof AbstractPlayableSprite sprite ? sprite : null);
        }
    }

    public boolean beginSelection() {
        return beginSelection(null);
    }

    boolean beginSelection(AbstractPlayableSprite sprite) {
        ensureState();
        if (!runtime.beginPedestalSelection(subtype)) {
            return false;
        }
        if (sprite != null) {
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
            sprite.setAnimationId(5);
        }
        return true;
    }

    public void updateSelection() {
        ensureState();
        if (!requestPublished && runtime.updatePedestalSelection()) {
            requestPublished = true;
            services().requestSpecialStageEntry(
                    new SpecialStageEntryRequest(subtype, EmeraldRewardKind.SUPER_EMERALD));
        }
    }

    public boolean isSelectable() {
        ensureState();
        return progression.state(subtype) == S3kEmeraldProgression.EmeraldState.GRAY_SUPER;
    }

    public Display display() {
        ensureState();
        if (runtime.forceGrayPedestal(subtype)) {
            return Display.GRAY;
        }
        return (progression.state(subtype) == S3kEmeraldProgression.EmeraldState.SUPER
                || runtime.revealPending(subtype))
                ? Display.COLORED : Display.GRAY;
    }

    public int completedPaletteLine() {
        return COMPLETED_PALETTE_LINES[subtype];
    }

    public int completedPaletteLine(SanctuaryPlayerMode playerMode) {
        return playerMode == SanctuaryPlayerMode.KNUCKLES
                ? KNUCKLES_COMPLETED_PALETTE_LINES[subtype]
                : COMPLETED_PALETTE_LINES[subtype];
    }

    private SanctuaryPlayerMode sanctuaryPlayerMode() {
        if (tryServices() != null) {
            for (PlayableEntity player : services().playerQuery().playersFor(
                    com.openggf.level.objects.ObjectPlayerParticipationPolicy.MAIN_ONLY_NATIVE)) {
                if (player instanceof AbstractPlayableSprite sprite
                        && "knuckles".equalsIgnoreCase(sprite.getCode())) {
                    return SanctuaryPlayerMode.KNUCKLES;
                }
            }
        }
        return SanctuaryPlayerMode.SONIC_TAILS;
    }

    boolean sharesRuntimeWithForTest(HPZSSEntryControlObjectInstance controller) {
        ensureState();
        return parentRef == controller && runtime == controller.runtimeForChild();
    }

    int mappingFrameForTest(int frameCounter) {
        ensureState();
        return progression.state(subtype) == S3kEmeraldProgression.EmeraldState.SUPER
                && !runtime.forceGrayPedestal(subtype) && (frameCounter & 1) != 0
                ? 7 : display() == Display.COLORED ? subtype : 0x1E;
    }

    /**
     * The sanctuary pedestals are created by the {@code $B5} controller
     * ({@code SingleObjLoad}), have no respawn-table entry, and carry no
     * off-screen delete tail — the controller owns them for the whole scene.
     * <p>
     * Without this the shared {@code out_of_range} unload deletes a pedestal
     * whose X falls more than $80 behind the camera and nothing can bring a
     * dynamic child back: pedestal 5 ($1550, the leftmost) dies as soon as the
     * camera reaches $1600 (which the conversion pan and any walk to the right
     * side of the sanctuary both do), so its Super Emerald stage became
     * permanently unreachable. Pedestal 6 ($1730) survives only because the
     * window extends $C0 further ahead of the camera than behind it.
     */
    @Override public boolean isPersistent() { return true; }

    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public boolean isSolidFor(PlayableEntity player) { return !isDestroyed(); }
    @Override public int getX() { return POSITIONS[subtype][0]; }
    @Override public int getY() { return POSITIONS[subtype][1]; }
    @Override public int getOutOfRangeReferenceX() { return getX(); }
    @Override public int getPriorityBucket() {
        ensureState();
        int state = progression.state(subtype).romValue();
        return state == 1 || state == 2 ? 1 : 4;
    }
    @Override public boolean isHighPriority() { return true; }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ensureState();
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parentRef)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(runtime.capture(), requestPublished, lastFrameCounter,
                        subtype, parentId));
    }

    @Override
    public void restoreRewindState(
            PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            if (extra.parentId() != null) {
                parentRef = (HPZSSEntryControlObjectInstance) context.requireIdentityTable()
                        .resolveObject(extra.parentId(), true);
            }
            ensureState();
            runtime.restore(extra.runtime());
            requestPublished = extra.requestPublished();
            lastFrameCounter = extra.lastFrameCounter();
            subtype = extra.subtype();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        boolean colored = display() == Display.COLORED;
        boolean flicker = progression.state(subtype) == S3kEmeraldProgression.EmeraldState.SUPER
                && !runtime.forceGrayPedestal(subtype) && (lastFrameCounter & 1) != 0;
        String key = colored
                ? Sonic3kObjectArtKeys.HPZ_MASTER_EMERALD
                : Sonic3kObjectArtKeys.HPZ_GRAY_EMERALD;
        PatternSpriteRenderer renderer = getRenderer(key);
        if (renderer != null) {
            renderer.drawFrameIndex(flicker ? 7 : colored ? subtype : 0,
                    getX(), getY(), false, false,
                    flicker ? 0 : colored
                            ? completedPaletteLine(sanctuaryPlayerMode()) : 0);
        }
    }
}

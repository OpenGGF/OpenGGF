package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.ObjectRefKind;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.physics.BackgroundPlaneCollisionProvider;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Event-backed FBZ runtime adapter and sole rewind serializer for FBZ event RAM. */
public final class FbzZoneRuntimeState implements S3kZoneRuntimeState {
    private static final int MAGIC = 0x46425A31;
    private static final int VERSION = 8;
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private final Sonic3kFBZEvents events;

    public FbzZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter, Sonic3kFBZEvents events) {
        if (actIndex < 0 || actIndex > 1) throw new IllegalArgumentException("FBZ act: " + actIndex);
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
        this.events = Objects.requireNonNull(events, "events");
        if (events.getAct() != actIndex) throw new IllegalArgumentException("FBZ adapter/handler act mismatch");
    }

    @Override public int zoneIndex() { return Sonic3kZoneIds.ZONE_FBZ; }
    @Override public int actIndex() { return actIndex; }
    @Override public boolean usesPersistentBackgroundVdpPlane() { return actIndex == 0; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return events.getDynamicResizeRoutine(); }
    @Override public boolean isActTransitionFlagActive() { return events.isEventsFg5(); }
    public boolean isBackedBy(Sonic3kFBZEvents candidate) { return events == candidate; }
    public int foregroundLayoutRegion() { return events.getForegroundLayoutRegion(); }
    public boolean foregroundOutdoor() { return events.isForegroundOutdoor(); }
    public boolean backgroundOutdoor() { return events.isBackgroundOutdoor(); }
    public int backgroundRedrawStage() { return events.getBackgroundRedrawStage(); }
    public Sonic3kFBZEvents.RedrawDirection backgroundRedrawDirection() { return events.getBackgroundRedrawDirection(); }
    public int backgroundRedrawProgress() { return events.getBackgroundRedrawProgress(); }
    public int outdoorBobOffset() { return events.getOutdoorBobOffset(); }
    public void setOutdoorBobOffset(int value) { events.setOutdoorBobOffset(value); }
    public int hScrollAccumulator() { return events.getHScrollAccumulator(); }
    public int sampleOutdoorHScrollAccumulator(int frameCounter) {
        return events.sampleOutdoorHScrollAccumulator(frameCounter);
    }
    public int sampleBossHScrollAccumulator(int frameCounter) {
        return events.sampleBossHScrollAccumulator(frameCounter);
    }
    public Sonic3kFBZEvents.MagneticPolarity magneticPolarity() { return events.getMagneticPolarity(); }
    public int magneticTimerPhase() { return events.getMagneticTimerPhase(); }
    public void advanceMagneticPhase(int frameCounter) { events.advanceMagneticPhase(frameCounter); }
    public boolean pendulumOrientationBit(int layoutIndex) { return events.getPendulumOrientationBit(layoutIndex); }
    public void setPendulumOrientationBit(int layoutIndex, boolean value) {
        events.setPendulumOrientationBit(layoutIndex, value);
    }
    public int act2ForegroundStage() { return events.getAct2ForegroundStage(); }
    public int bossBackgroundStage() { return events.getBossBackgroundStage(); }
    public int bossBackgroundOffsetX() { return events.getBossBackgroundOffsetX(); }
    public int bossBackgroundOffsetY() { return events.getBossBackgroundOffsetY(); }
    public boolean bossLoadPositionAdjustmentPending() { return events.isBossLoadPositionAdjustmentPending(); }
    public ObjectRefId cloudRewindId(int index) { return events.getCloudRewindId(index); }
    public List<ObjectRefId> cloudRewindIds() { return events.getCloudRewindIds(); }
    public boolean cloudCleanupTerminal() { return events.isCloudCleanupTerminal(); }
    public Sonic3kFBZEvents.PlaneAssignmentMode planeAssignmentMode() { return events.getPlaneAssignmentMode(); }
    public Sonic3kFBZEvents.CollisionMode collisionMode() { return events.getCollisionMode(); }
    public boolean screenShakeActive() { return events.isScreenShakeActive(); }
    public int screenShakeOffset() { return events.getScreenShakeOffset(); }
    public int screenShakePhase() { return events.getScreenShakePhase(); }

    @Override public BackgroundPlaneCollisionProvider.State backgroundPlaneCollisionStateOrNull() {
        return events.getCollisionMode() == Sonic3kFBZEvents.CollisionMode.FOREGROUND_ONLY
                ? BackgroundPlaneCollisionProvider.State.INACTIVE
                : new BackgroundPlaneCollisionProvider.State(true, events.getCollisionCameraDiffX(), events.getCollisionCameraDiffY());
    }

    @Override public byte[] captureBytes() {
        List<ObjectRefId> clouds = events.getCloudRewindIds();
        byte[] retainedPlane = events.captureRetainedPlaneSnapshot();
        int cloudBytes = clouds.stream().mapToInt(id -> 1 + (id == null ? 0 : 20)).sum();
        ByteBuffer out = ByteBuffer.allocate(200 + retainedPlane.length + cloudBytes);
        out.putInt(MAGIC).putInt(VERSION).putInt(events.getForegroundLayoutRegion());
        out.put(bool(events.isForegroundOutdoor())).put(bool(events.isBackgroundOutdoor()));
        out.putInt(events.getBackgroundRedrawStage()).putInt(events.getBackgroundRedrawDirection().ordinal());
        out.putInt(events.getBackgroundRedrawProgress()).putInt(events.getBackgroundRedrawPosition());
        out.putInt(events.getBackgroundRedrawRowCount()).putInt(events.getBackgroundRedrawVerticalAnchor());
        out.putInt(events.getLastRoundedBackgroundY());
        out.putInt(events.getDeformMode().ordinal());
        out.putInt(events.getPaletteVariant().ordinal()).putInt(events.getPaletteTarget().ordinal());
        out.put(bool(events.isAct1ScreenInitialized())).put(bool(events.isAct1BackgroundInitialized()));
        out.put(bool(events.isOutdoorMotionAllocationAttempted()));
        out.put(bool(events.isOutdoorMotionSpawned()));
        out.putInt(retainedPlane.length).put(retainedPlane);
        out.putInt(events.getOutdoorBobOffset());
        out.putInt(events.getHScrollAccumulator()).put(bool(events.isHScrollAccumulatorSampled()));
        out.putInt(events.getHScrollAccumulatorLastFrame()).putInt(events.getHScrollAccumulatorLastRead());
        out.putInt(events.getMagneticPolarity().ordinal()).putInt(events.getMagneticTimerPhase());
        out.put(bool(events.isMagneticEdgeObserved())).putInt(events.getMagneticLastEdgeFrame());
        for (long word : events.capturePendulumOrientationBits()) out.putLong(word);
        out.putInt(events.getAct2ForegroundStage()).putInt(events.getBossBackgroundStage());
        out.putInt(events.getBossBackgroundOffsetX()).putInt(events.getBossBackgroundOffsetY());
        out.put(bool(events.isBossLoadPositionAdjustmentPending()));
        clouds.forEach(id -> writeObjectId(out, id));
        out.put(bool(events.isCloudCleanupTerminal()));
        out.putInt(events.getPlaneAssignmentMode().ordinal()).putInt(events.getCollisionMode().ordinal());
        out.putInt(events.getCollisionCameraDiffX()).putInt(events.getCollisionCameraDiffY());
        out.put(bool(events.isScreenShakeActive())).putInt(events.getScreenShakeOffset()).putInt(events.getScreenShakePhase());
        out.put(bool(events.isEventsFg5()));
        if (out.hasRemaining()) throw new IllegalStateException("FBZ rewind size mismatch: " + out.remaining());
        return out.array();
    }

    @Override public void restoreBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        Snapshot s;
        try {
            ByteBuffer in = ByteBuffer.wrap(bytes);
            if (in.getInt() != MAGIC) throw new IllegalArgumentException("invalid FBZ rewind magic");
            if (in.getInt() != VERSION) throw new IllegalArgumentException("unsupported FBZ rewind version");
            int layout = in.getInt();
            boolean fgOutdoor = readBool(in, "foreground outdoor"), bgOutdoor = readBool(in, "background outdoor");
            int redrawStage = in.getInt();
            var redraw = enumAt(Sonic3kFBZEvents.RedrawDirection.values(), in.getInt(), "redraw direction");
            int redrawProgress = in.getInt();
            int redrawPosition = in.getInt(), redrawRowCount = in.getInt(), redrawVerticalAnchor = in.getInt();
            int lastRoundedBackgroundY = in.getInt();
            var deformMode = enumAt(Sonic3kFBZEvents.DeformMode.values(), in.getInt(), "deform mode");
            var paletteVariant = enumAt(Sonic3kFBZEvents.PaletteVariant.values(), in.getInt(), "palette variant");
            var paletteTarget = enumAt(Sonic3kFBZEvents.PaletteTarget.values(), in.getInt(), "palette target");
            boolean screenInitialized = readBool(in, "Act 1 screen initialized");
            boolean backgroundInitialized = readBool(in, "Act 1 background initialized");
            boolean motionAllocationAttempted = readBool(in, "outdoor motion allocation attempted");
            boolean motionSpawned = readBool(in, "outdoor motion spawned");
            int retainedPlaneLength = in.getInt();
            if (retainedPlaneLength != 0 && retainedPlaneLength != 64 * 32 * 4) {
                throw new IllegalArgumentException("invalid retained Plane-B byte count: " + retainedPlaneLength);
            }
            byte[] retainedPlane = new byte[retainedPlaneLength];
            in.get(retainedPlane);
            int bob = in.getInt();
            int hScrollAccumulator = in.getInt();
            boolean hScrollSampled = readBool(in, "HScroll accumulator sampled");
            int hScrollLastFrame = in.getInt(), hScrollLastRead = in.getInt();
            var polarity = enumAt(Sonic3kFBZEvents.MagneticPolarity.values(), in.getInt(), "magnetic polarity");
            int magneticPhase = in.getInt();
            boolean magneticEdgeObserved = readBool(in, "magnetic edge observed");
            int magneticLastEdgeFrame = in.getInt();
            long[] pendulumOrientationBits = new long[8];
            for (int i = 0; i < pendulumOrientationBits.length; i++) pendulumOrientationBits[i] = in.getLong();
            int foregroundStage = in.getInt(), bossStage = in.getInt();
            int bossX = in.getInt(), bossY = in.getInt();
            boolean adjustment = readBool(in, "boss adjustment");
            List<ObjectRefId> clouds = new ArrayList<>(Sonic3kConstants.FBZ_CLOUD_REWIND_SLOT_COUNT);
            for (int i = 0; i < Sonic3kConstants.FBZ_CLOUD_REWIND_SLOT_COUNT; i++) clouds.add(readObjectId(in));
            boolean cleanupTerminal = readBool(in, "cloud cleanup terminal");
            var plane = enumAt(Sonic3kFBZEvents.PlaneAssignmentMode.values(), in.getInt(), "plane assignment");
            var collision = enumAt(Sonic3kFBZEvents.CollisionMode.values(), in.getInt(), "collision mode");
            int diffX = in.getInt(), diffY = in.getInt();
            boolean shakeActive = readBool(in, "screen shake active");
            int shakeOffset = in.getInt(), shakePhase = in.getInt();
            boolean eventsFg5 = readBool(in, "Events_fg_5");
            if (in.hasRemaining()) throw new IllegalArgumentException("trailing FBZ rewind bytes: " + in.remaining());
            s = new Snapshot(layout, fgOutdoor, bgOutdoor, redrawStage, redraw,
                    redrawProgress, redrawPosition, redrawRowCount, redrawVerticalAnchor, lastRoundedBackgroundY,
                    deformMode, paletteVariant, paletteTarget,
                    screenInitialized, backgroundInitialized, motionAllocationAttempted, motionSpawned,
                    retainedPlane, bob,
                    hScrollAccumulator, hScrollSampled, hScrollLastFrame, hScrollLastRead,
                    polarity, magneticPhase, magneticEdgeObserved, magneticLastEdgeFrame, pendulumOrientationBits,
                    foregroundStage, bossStage, bossX, bossY, adjustment,
                    Collections.unmodifiableList(clouds), cleanupTerminal, plane, collision, diffX, diffY,
                    shakeActive, shakeOffset, shakePhase, eventsFg5);
        } catch (BufferUnderflowException e) {
            throw new IllegalArgumentException("truncated FBZ rewind state", e);
        }
        Sonic3kFBZEvents probe = new Sonic3kFBZEvents();
        probe.init(actIndex);
        apply(probe, s);
        apply(events, s);
    }

    private static void apply(Sonic3kFBZEvents target, Snapshot s) {
        target.setForegroundLayoutRegion(s.layout());
        target.setForegroundOutdoor(s.fgOutdoor()); target.setBackgroundOutdoor(s.bgOutdoor());
        target.setBackgroundRedraw(s.redrawStage(), s.redraw()); target.setOutdoorBobOffset(s.bob());
        target.restoreAct1EventState(s.redrawProgress(), s.redrawPosition(), s.redrawRowCount(),
                s.redrawVerticalAnchor(), s.lastRoundedBackgroundY(), s.deformMode(), s.paletteVariant(), s.paletteTarget(),
                s.screenInitialized(), s.backgroundInitialized(), s.motionAllocationAttempted(), s.motionSpawned());
        target.restoreRetainedPlaneSnapshot(s.retainedPlane());
        target.restoreHScrollAccumulatorState(s.hScrollAccumulator(), s.hScrollSampled(),
                s.hScrollLastFrame(), s.hScrollLastRead());
        target.restoreMagneticState(s.polarity(), s.magneticPhase(),
                s.magneticEdgeObserved(), s.magneticLastEdgeFrame());
        target.restorePendulumOrientationBits(s.pendulumOrientationBits());
        if (target.getAct() == 1) {
            if (s.cleanupTerminal() && s.clouds().stream().anyMatch(Objects::nonNull)) {
                throw new IllegalArgumentException("terminal FBZ cloud snapshot contains live identities");
            }
            target.setAct2ForegroundStage(s.foregroundStage());
            target.setBossBackgroundState(s.bossStage(), s.bossX(), s.bossY());
            target.setBossLoadPositionAdjustmentPending(s.adjustment());
            target.restoreCloudState(s.clouds(), s.cleanupTerminal());
            target.setPlaneAssignmentMode(s.plane());
            target.setCollisionMode(s.collision(), s.diffX(), s.diffY());
            target.setScreenShakeState(s.shakeActive(), s.shakeOffset(), s.shakePhase());
        } else if (s.foregroundStage() != 0 || s.bossStage() != 0 || s.bossX() != 0 || s.bossY() != 0) {
            throw new IllegalArgumentException("Act 2 FBZ state present in Act 1 snapshot");
        } else if (s.adjustment() || s.cleanupTerminal() || s.clouds().stream().anyMatch(Objects::nonNull)
                || s.plane() != Sonic3kFBZEvents.PlaneAssignmentMode.NORMAL
                || s.collision() != Sonic3kFBZEvents.CollisionMode.FOREGROUND_ONLY
                || s.diffX() != 0 || s.diffY() != 0 || s.shakeActive()
                || s.shakeOffset() != 0 || s.shakePhase() != 0) {
            throw new IllegalArgumentException("Act 2-only FBZ state present in Act 1 snapshot");
        }
        target.setEventsFg5(s.eventsFg5());
    }

    private static byte bool(boolean v) { return (byte) (v ? 1 : 0); }
    private static boolean readBool(ByteBuffer in, String name) {
        int v = in.get() & 0xFF;
        if (v > 1) throw new IllegalArgumentException("invalid FBZ " + name + " flag: " + v);
        return v != 0;
    }
    private static <E> E enumAt(E[] values, int ordinal, String name) {
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("invalid FBZ " + name + ": " + ordinal);
        return values[ordinal];
    }
    private static void writeObjectId(ByteBuffer out, ObjectRefId id) {
        out.put(bool(id != null));
        if (id != null) out.putInt(id.slotIndex()).putInt(id.generation()).putInt(id.spawnId()).putInt(id.dynamicId()).putInt(id.kind().ordinal());
    }
    private static ObjectRefId readObjectId(ByteBuffer in) {
        if (!readBool(in, "cloud id presence")) return null;
        int slot = in.getInt(), generation = in.getInt(), spawn = in.getInt(), dynamic = in.getInt();
        return new ObjectRefId(slot, generation, spawn, dynamic, enumAt(ObjectRefKind.values(), in.getInt(), "cloud id kind"));
    }
    private record Snapshot(int layout, boolean fgOutdoor, boolean bgOutdoor,
                            int redrawStage, Sonic3kFBZEvents.RedrawDirection redraw,
                            int redrawProgress, int redrawPosition, int redrawRowCount, int redrawVerticalAnchor,
                            int lastRoundedBackgroundY,
                            Sonic3kFBZEvents.DeformMode deformMode,
                            Sonic3kFBZEvents.PaletteVariant paletteVariant,
                            Sonic3kFBZEvents.PaletteTarget paletteTarget,
                            boolean screenInitialized, boolean backgroundInitialized,
                            boolean motionAllocationAttempted, boolean motionSpawned,
                            byte[] retainedPlane,
                            int bob,
                            int hScrollAccumulator, boolean hScrollSampled,
                            int hScrollLastFrame, int hScrollLastRead,
                            Sonic3kFBZEvents.MagneticPolarity polarity, int magneticPhase,
                            boolean magneticEdgeObserved, int magneticLastEdgeFrame,
                            long[] pendulumOrientationBits,
                            int foregroundStage, int bossStage, int bossX, int bossY, boolean adjustment,
                            List<ObjectRefId> clouds, boolean cleanupTerminal, Sonic3kFBZEvents.PlaneAssignmentMode plane,
                            Sonic3kFBZEvents.CollisionMode collision, int diffX, int diffY,
                            boolean shakeActive, int shakeOffset, int shakePhase, boolean eventsFg5) { }
}

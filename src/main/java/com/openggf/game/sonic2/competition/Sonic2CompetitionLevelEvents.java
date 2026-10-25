package com.openggf.game.sonic2.competition;

import com.openggf.camera.Camera;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.sonic2.titlecard.TitleCardManager;

import java.util.Objects;

/**
 * Native REV01 competition camera events. The ordinary zone handlers own boss
 * state machines that the ROM's {@code Two_player_mode} branches never enter.
 * CNZ's zone-global {@code SlotMachine} remains owned by the zone feature
 * provider's post-object callback, rather than being dispatched a second time.
 */
public final class Sonic2CompetitionLevelEvents implements LevelEventProvider {
    @FunctionalInterface
    interface CameraMotion {
        void completed(int slot, short oldX, short oldY, boolean frozen);
    }

    private record CameraSample(short x, short y, boolean frozen) { }

    private final Sonic2CompetitionSession session;
    private final TitleCardManager title;
    private final Runnable deformEntry;
    private final CameraMotion cameraMotion;
    private CameraSample primaryCameraSample;
    private int act = -1;
    private short actTwoMaxX;

    public Sonic2CompetitionLevelEvents(Sonic2CompetitionSession session) {
        this(session, null, null);
    }

    Sonic2CompetitionLevelEvents(Sonic2CompetitionSession session, TitleCardManager title, Runnable deformEntry) {
        this(session, title, deformEntry, (slot, oldX, oldY, frozen) -> { });
    }

    Sonic2CompetitionLevelEvents(Sonic2CompetitionSession session, TitleCardManager title,
            Runnable deformEntry, CameraMotion cameraMotion) {
        this.session = Objects.requireNonNull(session, "session");
        this.title = title;
        this.deformEntry = deformEntry;
        this.cameraMotion = Objects.requireNonNull(cameraMotion, "camera motion");
    }

    @Override public void updateFixedInLevelObjectsBeforeDynamicObjects() {
        if (title == null || title.isComplete()) return;
        // Obj34_BackgroundOutInit is in Object_RAM before managed slot 16.
        // It deforms once, on its own first background pass, not every leave wait.
        if (title.isFirstBackgroundLeavePass()) deformEntry.run();
        title.update();
    }

    @Override
    public void initLevel(int logicalZone, int act) {
        clearCameraSample();
        if (act < 0 || act > 1) {
            throw new IllegalArgumentException("Not a native competition act: " + act);
        }
        // Logical engine zone IDs differ from Current_Zone's ROM IDs ($00/$0B/$0C).
        // Each immediate is verified in the shipped REV01 Two_player_mode branch:
        // LevEvents_EHZ2_Routine1 (s2.asm:20409-20425), ROM $E6A2;
        // LevEvents_MCZ2_Routine1 (s2.asm:21426-21442), ROM $F188;
        // LevEvents_CNZ2_Routine1 (s2.asm:21532-21549), ROM $F2C0.
        short maxX = switch (logicalZone) {
            case 0 -> 0x2920;
            case 5 -> 0x2100;
            case 3 -> 0x26A0;
            default -> throw new IllegalArgumentException(
                    "Not a native competition logical zone: " + logicalZone);
        };
        this.act = act;
        actTwoMaxX = maxX;
    }

    @Override public void updateAfterObjectsBeforeCamera() {
        clearCameraSample();
        if (act < 0) return;
        requireQualifiedCameraPath();
        primaryCameraSample = sample(Objects.requireNonNull(session.camera(0), "native P1 camera"));
    }

    @Override public void updateFixedInLevelObjects() {
        var before = primaryCameraSample;
        // Consume before the callback: a failed camera/callback must never
        // publish the previous pass's sample during a later continuation.
        clearCameraSample();
        if (before != null) cameraMotion.completed(0, before.x(), before.y(), before.frozen());
    }

    void clearCameraSample() { primaryCameraSample = null; }

    private static CameraSample sample(Camera camera) {
        return new CameraSample(camera.getX(), camera.getY(), camera.getFrozen());
    }

    private void requireQualifiedCameraPath() {
        // ScrollHoriz (s2.asm:18041-18044) retains its prior diff WORD when
        // Teleport_flag skips scrolling. The canonical integer camera has no
        // qualified native teleport worker; reject before sampling or motion.
        if (session.teleportTimer() != 0) {
            clearCameraSample();
            throw new UnsupportedOperationException("Native teleport camera path is not qualified");
        }
    }

    @Override
    public void update() {
        if (act < 0) return;
        requireQualifiedCameraPath();
        Camera p2 = Objects.requireNonNull(session.camera(1), "native P2 camera");
        // DeformBgLayer (s2.asm:15097-15175) scrolls P1, then P2, before
        // RunDynamicLevelEvents. The canonical world pass already scrolled P1.
        // P2 has its own boundary bank and no dynamic bottom-boundary easing tail.
        var before = sample(p2);
        p2.updatePosition();
        cameraMotion.completed(1, before.x(), before.y(), before.frozen());
        p2.captureRenderCopy();

        // Act 1 returns without boundary writes: LevEvents_EHZ (s2.asm:20390-20394),
        // LevEvents_MCZ (s2.asm:21406-21409), LevEvents_CNZ (s2.asm:21511-21515).
        // In Act 2, Two_player_mode writes both right boundaries every tick and
        // leaves Dynamic_Resize_Routine at zero, never entering a boss routine.
        initializeBoundaryWords();
    }

    /** Level calls DeformBgLayer before admitting the first object pass. */
    void initializeBoundaryWords() {
        if (act == 1) {
            Objects.requireNonNull(session.camera(0), "native P1 camera").setMaxX(actTwoMaxX);
            Objects.requireNonNull(session.camera(1), "native P2 camera").setMaxX(actTwoMaxX);
        }
    }

    @Override
    public void updateAtLevelLoopTail() {
        if (act < 0) return;
        // SetLevelEndType (s2.asm:6128-6147) enables the signpost for every native
        // competition act. CheckLoadSignpostArt (s2.asm:6154-6172) first tests the
        // global Debug_placement_mode, owned by the primary player.
        if (Objects.requireNonNull(session.player(0), "native P1 actor").isDebugMode()) return;
        if (lockSignpostLeftBoundary(0)) {
            // Native P1's successful write returns immediately in 2P mode.
            // It neither visits P2 that tick nor submits the ordinary signpost PLC.
            return;
        }
        // Every failed P1 eligibility test falls through to the independent P2
        // bank: SignpostUpdateTailsBounds (s2.asm:6175-6188), ROM $4C80.
        lockSignpostLeftBoundary(1);
    }

    private boolean lockSignpostLeftBoundary(int slot) {
        Camera camera = Objects.requireNonNull(session.camera(slot), "native camera");
        // SUBI.W wraps before CMP.W/BLT performs a signed comparison.
        short threshold = (short) (camera.getMaxX() - 0x100);
        if (camera.getX() < threshold || session.playerState(slot).isTimerPaused()
                || camera.getMinX() == threshold) {
            return false;
        }
        camera.setMinX(threshold);
        return true;
    }
}

package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.S3kEmeraldProgression;
import com.openggf.game.sonic3k.S3kSanctuaryRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.ArrayList;
import java.util.List;

/**
 * ROM {@code Obj_HPZSSEntryControl} (SKL object $B5).
 *
 * <p>Owns the sanctuary object graph and conversion/exit gates. Durable
 * progression is delegated to {@link S3kEmeraldProgression}.
 */
public final class HPZSSEntryControlObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int CENTRE_X = 0x1640;

    private S3kEmeraldProgression progression;
    private S3kSanctuaryRuntimeState runtime;
    private boolean reentry;
    private boolean childrenSpawned;
    private boolean exitBandArmed;
    private boolean exitRequested;
    private boolean introSignal;
    private boolean introCrystalSpawned;
    private int conversionTimer = -1;
    private boolean conversionCrystalActive;
    private int cameraTargetX = -1;
    private int teleporterReadyTimer = 0x10;
    private boolean playersLocked;

    private record RewindExtra(
            S3kSanctuaryRuntimeState.Snapshot runtime,
            boolean reentry,
            boolean childrenSpawned,
            boolean exitBandArmed,
            boolean exitRequested,
            boolean introSignal,
            boolean introCrystalSpawned,
            int conversionTimer,
            boolean conversionCrystalActive,
            int cameraTargetX,
            int teleporterReadyTimer,
            boolean playersLocked)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public HPZSSEntryControlObjectInstance(ObjectSpawn spawn) {
        this(spawn, null, false);
    }

    public HPZSSEntryControlObjectInstance(
            ObjectSpawn spawn, S3kEmeraldProgression progression, boolean reentry) {
        super(spawn, "HPZSSEntryControl");
        this.progression = progression;
        this.reentry = reentry;
        if (progression != null) {
            runtime = new S3kSanctuaryRuntimeState(progression, reentry);
        }
    }

    @Override
    public HPZSSEntryControlObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HPZSSEntryControlObjectInstance(ctx.spawn());
    }

    private void ensureState() {
        if (progression == null) {
            progression = S3kEmeraldProgression.from(services().gameState());
            reentry = services().sanctuaryReentryStage().isPresent();
        }
        if (runtime == null) {
            runtime = new S3kSanctuaryRuntimeState(progression, reentry);
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        ensureState();
        if (!childrenSpawned) {
            spawnInitialChildren();
        }
        if (!reentry && !playersLocked && player instanceof AbstractPlayableSprite sprite) {
            applyFreshLockToParticipants(sprite);
            playersLocked = true;
        }

        if (runtime.phase() == S3kSanctuaryRuntimeState.Phase.INTRO_WAIT) {
            if (runtime.updateIntro() && !introCrystalSpawned) {
                introCrystalSpawned = true;
                int startY = (services().camera().getY() & 0xFFFF) - 0x80;
                spawnChild(() -> new HPZSanctuaryFallingCrystalObjectInstance(this, 7, startY));
            }
        } else if (runtime.phase() == S3kSanctuaryRuntimeState.Phase.WAITING_FOR_INTRO_SIGNAL
                && introSignal) {
            runtime.signalIntroComplete();
            if (runtime.phase() == S3kSanctuaryRuntimeState.Phase.CONVERTING) {
                conversionTimer = 0x21F;
            }
        } else if (runtime.phase() == S3kSanctuaryRuntimeState.Phase.CONVERTING) {
            updateConversionChoreography();
        }
        if (playersLocked && !conversionCrystalActive
                && runtime.phase() == S3kSanctuaryRuntimeState.Phase.READY
                && player instanceof AbstractPlayableSprite sprite) {
            releaseFreshLockFromParticipants(sprite);
            playersLocked = false;
        }

        boolean ready = --teleporterReadyTimer < 0;
        updateExitBand(player.getCentreX() & 0xFFFF, ready);
    }

    public void signalIntroComplete() {
        introSignal = true;
    }

    void onFallingCrystalMidpoint(int subtype) {
        if (subtype == 7) {
            applyMidpointPlayerMappings();
            return;
        }
        if (runtime != null && runtime.phase() == S3kSanctuaryRuntimeState.Phase.CONVERTING
                && runtime.conversionOrder().stream().skip(
                        runtime.capture().conversionCursor()).findFirst().orElse(-1) == subtype) {
            runtime.convertNext();
            spawnChild(() -> new HPZSuperEmeraldObjectInstance(
                    dynamicSpawn(0, 0, 0xB4, subtype), this));
        }
    }

    void onFallingCrystalAnimationComplete(int subtype) {
        if (subtype == 7) {
            introSignal = true;
            return;
        }
        conversionCrystalActive = false;
        conversionTimer = 0x1F;
    }

    private void applyMidpointPlayerMappings() {
        if (tryServices() == null) return;
        for (PlayableEntity entity : services().playerQuery().playersFor(
                com.openggf.level.objects.ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (entity instanceof AbstractPlayableSprite sprite) {
                String name = sprite.getClass().getSimpleName();
                int mapping = name.contains("Tails") ? 0xAD
                        : name.contains("Knuckles") ? 0x56 : 0xBA;
                sprite.setMappingFrame(mapping);
                sprite.setAnimationId(5);
            }
        }
    }

    boolean introSignalReceivedForTest() {
        return introSignal;
    }

    public void beginConversionForTest() {
        ensureState();
        if (runtime.phase() == S3kSanctuaryRuntimeState.Phase.INTRO_WAIT) {
            runtime.skipIntroForTest();
        }
        runtime.signalIntroComplete();
        conversionTimer = 0x21F;
    }

    /**
     * Advances the controller-owned signed word countdown and returns the
     * converted subtype, or {@code -1} while the branch remains non-negative.
     */
    public int updateConversionTimerForTest() {
        ensureState();
        // loc_90B22/loc_90C2A use subq.w/bpl/bmi. $21F fires on update $220;
        // the between-drop $1F value fires on update $20.
        if (--conversionTimer >= 0) {
            return -1;
        }
        int converted = runtime.convertNext();
        conversionTimer = 0x1F;
        return converted;
    }

    private void updateConversionChoreography() {
        if (conversionCrystalActive) {
            return;
        }
        int cursor = runtime.capture().conversionCursor();
        if (cursor >= runtime.conversionOrder().size()) {
            return;
        }
        int subtype = runtime.conversionOrder().get(cursor);
        if (cameraTargetX < 0) {
            if (--conversionTimer >= 0) {
                return;
            }
            cameraTargetX = conversionCameraTarget(subtype);
        }
        if (!panCameraForTest(services().camera(), cameraTargetX)) {
            return;
        }
        cameraTargetX = -1;
        conversionCrystalActive = true;
        int startY = (services().camera().getY() & 0xFFFF) - 0x80;
        spawnChild(() -> new HPZSanctuaryFallingCrystalObjectInstance(
                this, subtype, startY));
    }

    private static int conversionCameraTarget(int subtype) {
        return switch (subtype) {
            case 0 -> 0x15A0;
            case 1 -> 0x1540;
            case 2 -> 0x1600;
            case 3 -> 0x1520;
            case 4 -> 0x1620;
            case 5 -> 0x1520;
            case 6 -> 0x1620;
            default -> 0x15A0;
        };
    }

    boolean panCameraForTest(Camera camera, int targetX) {
        int current = camera.getX() & 0xFFFF;
        int delta = targetX - current;
        if (Math.abs(delta) < 0x10) {
            camera.setX((short) targetX);
            return true;
        }
        camera.setX((short) (current + (delta > 0 ? 0x10 : -0x10)));
        return false;
    }

    void applyFreshLockForTest(AbstractPlayableSprite player) {
        NativePositionOps.writeXPosPreserveSubpixel(player, 0x1640);
        NativePositionOps.writeYPosPreserveSubpixel(player, 0x3A3);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        // ROM writes $83: retain bit-7 ownership while enabling the low CPU
        // permission bits used by the ceremony animation.
        player.setObjectControlAllowsCpu(true);
        player.setObjectControlSuppressesMovement(true);
        player.setObjectMappingFrameControl(true);
        player.setMappingFrame(0);
        player.setAnimationId(0x1C);
    }

    private void applyFreshLockToParticipants(AbstractPlayableSprite primary) {
        applyFreshLockForTest(primary);
        if (tryServices() == null) return;
        for (PlayableEntity entity : services().playerQuery().playersFor(
                com.openggf.level.objects.ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (entity instanceof AbstractPlayableSprite sprite && sprite != primary) {
                applyFreshLockForTest(sprite);
            }
        }
    }

    void releaseFreshLockForTest(AbstractPlayableSprite player) {
        ObjectControlState.none().applyTo(player);
        player.setObjectMappingFrameControl(false);
    }

    private void releaseFreshLockFromParticipants(AbstractPlayableSprite primary) {
        releaseFreshLockForTest(primary);
        if (tryServices() == null) return;
        for (PlayableEntity entity : services().playerQuery().playersFor(
                com.openggf.level.objects.ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (entity instanceof AbstractPlayableSprite sprite && sprite != primary) {
                releaseFreshLockForTest(sprite);
            }
        }
    }

    private void spawnInitialChildren() {
        childrenSpawned = true;
        spawnChild(() -> new HPZMasterEmeraldObjectInstance(
                dynamicSpawn(0x1640, 0x340, 0xB0, 0)));
        spawnChild(() -> new SSZHPZTeleporterObjectInstance(
                dynamicSpawn(0x1640, 0x3C7, 0x79, 0)));
        for (int subtype : initialPedestalSubtypes()) {
            final int index = subtype;
            spawnChild(() -> new HPZSuperEmeraldObjectInstance(
                    dynamicSpawn(0, 0, 0xB4, index), this));
        }
    }

    S3kEmeraldProgression progressionForChild() {
        ensureState();
        return progression;
    }

    S3kSanctuaryRuntimeState runtimeForChild() {
        ensureState();
        return runtime;
    }

    private static ObjectSpawn dynamicSpawn(int x, int y, int id, int subtype) {
        return new ObjectSpawn(x, y, id, subtype, 0, false, y);
    }

    public List<Integer> initialPedestalSubtypes() {
        ensureState();
        if (reentry) {
            return List.of(0, 1, 2, 3, 4, 5, 6);
        }
        ArrayList<Integer> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            // ROM loc_90A26: d1=2; cmp state,d1 / bhi skips allocation.
            // Therefore state 2/3 falls through and is spawned.
            if (progression.state(i).romValue() >= 2) {
                result.add(i);
            }
        }
        return List.copyOf(result);
    }

    public List<Class<?>> initialChildTypes() {
        ArrayList<Class<?>> types = new ArrayList<>();
        types.add(HPZMasterEmeraldObjectInstance.class);
        types.add(SSZHPZTeleporterObjectInstance.class);
        for (int ignored : initialPedestalSubtypes()) {
            types.add(HPZSuperEmeraldObjectInstance.class);
        }
        return List.copyOf(types);
    }

    public void updateExitBand(int playerCentreX, boolean teleporterReady) {
        ensureState();
        int distance = Math.abs(playerCentreX - CENTRE_X);
        if (distance >= 0x18) {
            exitBandArmed = true;
            return;
        }
        if (distance >= 8 || !exitBandArmed) {
            return;
        }
        exitBandArmed = false;
        if (!exitRequested && teleporterReady && runtime.exitEligible()) {
            exitRequested = true;
            services().requestSanctuaryExit();
        }
    }

    public void attachRuntimeForTest(S3kSanctuaryRuntimeState runtime) {
        this.runtime = runtime;
    }

    @Override public int getX() { return CENTRE_X; }
    @Override public int getY() { return 0x3A3; }
    @Override public int getOutOfRangeReferenceX() { return CENTRE_X; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible controller.
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState() {
        ensureState();
        return super.captureRewindState().withObjectSubclassExtra(
                new RewindExtra(runtime.capture(), reentry, childrenSpawned,
                        exitBandArmed, exitRequested, introSignal, introCrystalSpawned,
                        conversionTimer, conversionCrystalActive, cameraTargetX,
                        teleporterReadyTimer, playersLocked));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot) {
        super.restoreRewindState(snapshot);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            ensureState();
            runtime.restore(extra.runtime());
            reentry = extra.reentry();
            childrenSpawned = extra.childrenSpawned();
            exitBandArmed = extra.exitBandArmed();
            exitRequested = extra.exitRequested();
            introSignal = extra.introSignal();
            introCrystalSpawned = extra.introCrystalSpawned();
            conversionTimer = extra.conversionTimer();
            conversionCrystalActive = extra.conversionCrystalActive();
            cameraTargetX = extra.cameraTargetX();
            teleporterReadyTimer = extra.teleporterReadyTimer();
            playersLocked = extra.playersLocked();
        }
    }
}

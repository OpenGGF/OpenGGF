package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.S3kEmeraldProgression;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.S3kSanctuaryRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.game.save.SaveReason;

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

    private final com.openggf.level.objects.PlayerControlOwners directedControlPlayers =
            new com.openggf.level.objects.PlayerControlOwners();

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
    private int conversionSpawnCursor;
    private int cameraTargetX = -1;
    private boolean playersLocked;
    private boolean freshLockApplied;
    private boolean finalPanStarted;
    private boolean conversionSequenceStarted;
    /** The controller was loaded behind {@code SpecialStage_Results} with {@code HPZ_special_stage_completed} set. */
    private boolean resultsBackdrop;

    private record RewindExtra(
            S3kSanctuaryRuntimeState.Snapshot runtime,
            boolean reentry,
            boolean childrenSpawned,
            boolean exitBandArmed,
            boolean exitRequested,
            boolean introSignal,
            boolean introCrystalSpawned,
            int conversionTimer,
            int conversionSpawnCursor,
            int cameraTargetX,
            boolean playersLocked,
            boolean freshLockApplied,
            boolean finalPanStarted,
            boolean conversionSequenceStarted,
            boolean resultsBackdrop,
            java.util.List<com.openggf.game.rewind.identity.PlayerRefId> ownedPlayers)
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
            reentry = services().sanctuaryReturnContext().isPresent()
                    || services().sanctuaryReentryStage().isPresent();
        }
        if (runtime == null) {
            var returnContext = services().sanctuaryReturnContext();
            var returnStage = services().sanctuaryReentryStage();
            runtime = returnContext.isPresent()
                    ? new S3kSanctuaryRuntimeState(progression, true,
                    returnContext.orElseThrow().stageIndex(),
                    returnContext.orElseThrow().succeeded())
                    : returnStage.isPresent()
                    ? new S3kSanctuaryRuntimeState(progression, true,
                    returnStage.getAsInt(),
                    progression.state(returnStage.getAsInt())
                            == S3kEmeraldProgression.EmeraldState.SUPER)
                    : new S3kSanctuaryRuntimeState(progression, reentry);
        }
    }

    /**
     * Rebuilds this controller as {@code SpecialStage_Results} leaves it: {@code loc_2E226}
     * sets {@code HPZ_special_stage_completed} after {@code Load_Sprites}, so the first
     * {@code Obj_HPZSSEntryControl} pass takes {@code loc_909EA}, allocates all seven
     * pedestals and deletes itself (sonic3k.asm:197888-197899). A cleared stage also left
     * {@code _unkFAC0} = stage|$80 and {@code _unkFAC1} = $FF (sonic3k.asm:63227-63232);
     * the runtime's pedestal-transform and return-transform flags model those two bytes.
     * Must be called before the controller's first update.
     */
    public void hostSpecialStageResults(int stageIndex, boolean succeeded) {
        if (progression == null) {
            progression = S3kEmeraldProgression.from(services().gameState());
        }
        reentry = true;
        resultsBackdrop = true;
        runtime = new S3kSanctuaryRuntimeState(progression, true, stageIndex, succeeded);
    }

    /**
     * Native Level/loc_64DC's fixed-slot fade word aliases this controller's
     * loc_90A10 intro countdown when B5 occupies Dynamic_object_RAM+5 (SST8).
     */
    public void enterLevelLoop() {
        ensureState();
        runtime.enterLevelLoop();
    }

    /**
     * Obj_HPZSSEntryControl loc_90998/loc_909B0 prepares the target bank in
     * the initial Process_Sprites at Level loc_6468, before loc_64DC arms the
     * black reveal. The engine represents that reveal as an RGB overlay on
     * a prepared current bank, so publish the staged destination here.
     * Results hosting has its own Normal/Target palette model and bypasses
     * this publication. childrenSpawned captures the one-shot setup lifetime.
     */
    private void publishEntryPaletteUnderFade() {
        if (tryServices() == null) {
            return;
        }
        var registry = services().paletteOwnershipRegistryOrNull();
        var levelManager = services().levelManager();
        var level = levelManager != null ? levelManager.getCurrentLevel() : null;
        if (registry == null || level == null) {
            return;
        }
        for (int line = 1; line < 4; line++) {
            S3kPaletteWriteSupport.applyLine(
                    registry, level, services().graphicsManager(),
                    S3kPaletteOwners.HPZ_PALETTE_CONTROL,
                    S3kPaletteOwners.PRIORITY_ZONE_EVENT,
                    line, registry.targetSegaData(line, 0, 16));
        }
        S3kPaletteWriteSupport.resolvePendingWritesNow(
                registry, level, services().graphicsManager());
    }

    public boolean isHostingSpecialStageResults() {
        return resultsBackdrop;
    }

    /**
     * loc_2E9D8 {@code clr.b (_unkFAC1).w}: the Hyper message has arrived, releasing the
     * Master Emerald's {@code loc_9071A} palette rotation.
     */
    public void completeResultsReturnTransformation() {
        ensureState();
        runtime.completeReturnTransformation();
    }

    /** {@code _unkFAC1} as the results reveal sees it. */
    public boolean resultsReturnTransformationActive() {
        ensureState();
        return runtime.transformationActive();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        ensureState();
        boolean initialDispatch = !childrenSpawned;
        if (initialDispatch) {
            if (!resultsBackdrop) {
                publishEntryPaletteUnderFade();
            }
            spawnInitialChildren();
        }
        if (resultsBackdrop) {
            // loc_909F4 ends in Delete_Current_Sprite; the engine keeps the controller
            // inert because its children read the shared runtime through it.
            return;
        }
        if (!reentry && !freshLockApplied && player instanceof AbstractPlayableSprite sprite) {
            applyFreshLockToParticipants(sprite);
            playersLocked = true;
            freshLockApplied = true;
        }
        // loc_90A10/sub_90A4C's setup dispatch ends in rts. The next
        // operation, loc_90A72, owns the first intro countdown decrement.
        if (initialDispatch) return;

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
                conversionSequenceStarted = true;
                spawnChild(() -> new HPZSanctuarySmallEmeraldCeremonyObjectInstance(
                        progression));
            }
        } else if (conversionSequenceStarted && playersLocked) {
            updateConversionChoreography();
        }
        if (playersLocked
                && runtime.phase() == S3kSanctuaryRuntimeState.Phase.READY
                && player instanceof AbstractPlayableSprite sprite) {
            if (!conversionSequenceStarted) {
                releaseFreshLockFromParticipants(sprite);
                playersLocked = false;
                return;
            }
        }

        boolean standingOnTeleporter = services().objectManager() != null
                && services().objectManager().activeObjectsOfType(
                        SSZHPZTeleporterObjectInstance.class).stream()
                .anyMatch(teleporter -> services().objectManager()
                        .isRidingObject(player, teleporter));
        updateExitBand(player.getCentreX() & 0xFFFF, standingOnTeleporter);
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
            runtime.beginPedestalReveal(subtype);
            spawnChild(() -> new HPZSuperEmeraldObjectInstance(
                    dynamicSpawn(0, 0, 0xB4, subtype), this));
            if (tryServices() != null) {
                services().requestSessionSave(SaveReason.SPECIAL_STAGE_SAVE);
            }
        }
    }

    void onFallingCrystalAnimationComplete(int subtype) {
        if (subtype == 7) {
            introSignal = true;
        } else if (runtime != null) {
            runtime.completePedestalReveal(subtype);
        }
    }

    private void applyMidpointPlayerMappings() {
        if (tryServices() == null) return;
        for (PlayableEntity entity : services().playerQuery().playersFor(
                com.openggf.level.objects.ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (entity instanceof AbstractPlayableSprite sprite) {
                // ROM reads character_id, not the engine registration code (e.g. tails_p2).
                String code = sprite.characterKey().persisted();
                int mapping = "tails".equalsIgnoreCase(code) ? 0xAD
                        : "knuckles".equalsIgnoreCase(code) ? 0x56 : 0xBA;
                if ("tails".equalsIgnoreCase(code)) {
                    NativePositionOps.writeYPosPreserveSubpixel(
                            sprite, (sprite.getCentreY() & 0xFFFF) + 4);
                }
                sprite.setMappingFrame(mapping);
                sprite.setAnimationId(5);
            }
        }
    }

    private void applyFinalPlayerMappings() {
        if (tryServices() == null) return;
        for (PlayableEntity entity : services().playerQuery().playersFor(
                com.openggf.level.objects.ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (entity instanceof AbstractPlayableSprite sprite) {
                // ROM reads character_id, not the engine registration code (e.g. tails_p2).
                String code = sprite.characterKey().persisted();
                sprite.setRenderFlips(false, false);
                sprite.setMappingFrame("tails".equalsIgnoreCase(code) ? 0xAD
                        : "knuckles".equalsIgnoreCase(code) ? 0x56 : 0xBA);
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
        if (cameraTargetX < 0) {
            if (--conversionTimer >= 0) {
                return;
            }
            services().camera().setScrollLocked(true);
            if (conversionSpawnCursor >= runtime.conversionOrder().size()) {
                finalPanStarted = true;
                cameraTargetX = 0x15A0;
                applyFinalPlayerMappings();
            } else {
                int subtype = runtime.conversionOrder().get(conversionSpawnCursor);
                cameraTargetX = conversionCameraTarget(subtype);
                applyConversionPlayerMappings(subtype);
            }
            // loc_90B32 installs loc_90BD2 and tail-calls sub_90B84 for
            // Player_2; its rts ends this dispatch before the camera pan.
            return;
        }
        if (!panCameraForTest(services().camera(), cameraTargetX)) {
            return;
        }
        cameraTargetX = -1;
        if (finalPanStarted) {
            services().camera().setScrollLocked(false);
            for (PlayableEntity entity : services().playerQuery().playersFor(
                    com.openggf.level.objects.ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
                if (entity instanceof AbstractPlayableSprite sprite) {
                    releaseFreshLockForTest(sprite);
                }
            }
            playersLocked = false;
            return;
        }
        int subtype = runtime.conversionOrder().get(conversionSpawnCursor++);
        int startY = (services().camera().getY() & 0xFFFF) - 0x80;
        spawnChild(() -> new HPZSanctuaryFallingCrystalObjectInstance(
                this, subtype, startY));
        // loc_90BF4 switches the controller to loc_90C2A immediately. This
        // countdown runs concurrently with the falling crystal child.
        conversionTimer = 0x1F;
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

    private void applyConversionPlayerMappings(int subtype) {
        int[] flipAndVariant = conversionPoseForTest(subtype);
        for (PlayableEntity entity : services().playerQuery().playersFor(
                com.openggf.level.objects.ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (entity instanceof AbstractPlayableSprite sprite) {
                // ROM reads character_id, not the engine registration code (e.g. tails_p2).
                String code = sprite.characterKey().persisted();
                int base = flipAndVariant[1] == 0
                        ? ("tails".equalsIgnoreCase(code) ? 0xAD
                        : "knuckles".equalsIgnoreCase(code) ? 0x56 : 0xBA)
                        : ("tails".equalsIgnoreCase(code) ? 0xB0
                        : "knuckles".equalsIgnoreCase(code) ? 0xD6 : 0xC4);
                boolean facesLeft = flipAndVariant[0] != 0;
                sprite.setDirection(facesLeft ? Direction.LEFT : Direction.RIGHT);
                sprite.setRenderFlips(facesLeft, false);
                sprite.setMappingFrame(base);
            }
        }
    }

    static int[] conversionPoseForTest(int subtype) {
        return switch (subtype) {
            case 0 -> new int[]{1, 1};
            case 1 -> new int[]{1, 0};
            case 2 -> new int[]{0, 0};
            case 3 -> new int[]{1, 1};
            case 4 -> new int[]{0, 1};
            case 5 -> new int[]{1, 0};
            default -> new int[]{0, 0};
        };
    }

    boolean panCameraForTest(Camera camera, int targetX) {
        int current = camera.getX() & 0xFFFF;
        int delta = targetX - current;
        if (Math.abs(delta) < 0x10) {
            return true;
        }
        camera.setX((short) (current + (delta > 0 ? 0x10 : -0x10)));
        return false;
    }

    void applyFreshLockForTest(AbstractPlayableSprite player) {
        directedControlPlayers.add(player);
        var superState = player.getSuperStateController();
        if (superState != null && superState.isSuper()) {
            // Player initialization on every ROM level load clears
            // Super_Sonic_Knux_flag before the B5 controller assigns normal
            // player mappings.
            superState.debugDeactivate();
        }
        // sub_90A4C begins literal host-ROM mapping ownership. Keep the
        // native table/timers while borrowing presentation only; configured
        // rules, physics and powered/controller RAM remain the participant's.
        if (tryServices() != null && services().spriteManager() != null
                && player.currentLevelManagerIfAvailable() != null && player.getSpriteRenderer() != null) {
            com.openggf.sprites.managers.SpriteManagerInternalAccess.borrowNativePlayerArt(
                    services().spriteManager(), player);
        }
        NativePositionOps.writeXPosPreserveSubpixel(player, 0x1640);
        NativePositionOps.writeYPosPreserveSubpixel(player, 0x3A3);
        // ROM writes $83. Its sign bit makes Tails_CPU_Control skip the normal
        // body, locking native P2 for the ceremony just like the main player.
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setObjectMappingFrameControl(true);
        player.setHighPriority(true);
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
        directedControlPlayers.remove(player);
        ObjectControlState.none().applyTo(player);
        player.setObjectMappingFrameControl(false);
        // loc_90C16 releases control after the final native pose. The shared
        // RETURN lifetime keeps that pose drawable until Animate actually
        // publishes an ordinary mapping, then returns configured donor art.
        if (tryServices() != null && services().spriteManager() != null) {
            com.openggf.sprites.managers.SpriteManagerInternalAccess.releaseNativePlayerArt(
                    services().spriteManager(), player);
        }
    }

    @Override
    public void releasePlayersForVisualDirection() {
        for (AbstractPlayableSprite player : directedControlPlayers.players())
            releaseFreshLockForTest(player);
        directedControlPlayers.clear();
        playersLocked = false;
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
                dynamicSpawn(0x1640, 0x340, 0xB0, 0), this));
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

    public String ceremonyStateForTest() {
        ensureState();
        return "phase=" + runtime.phase()
                + ",cursor=" + runtime.capture().conversionCursor()
                + ",spawnCursor=" + conversionSpawnCursor
                + ",conversionTimer=" + conversionTimer
                + ",cameraTarget=" + cameraTargetX
                + ",finalPan=" + finalPanStarted
                + ",playersLocked=" + playersLocked;
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
        return captureRewindState(com.openggf.game.rewind.schema.RewindCaptureContext.none());
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot) {
        restoreRewindState(snapshot, com.openggf.game.rewind.schema.RewindCaptureContext.none());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(
            com.openggf.game.rewind.schema.RewindCaptureContext context) {
        ensureState();
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(runtime.capture(), reentry, childrenSpawned,
                        exitBandArmed, exitRequested, introSignal, introCrystalSpawned,
                        conversionTimer, conversionSpawnCursor, cameraTargetX,
                        playersLocked, freshLockApplied, finalPanStarted,
                        conversionSequenceStarted, resultsBackdrop, directedControlPlayers.capture(context)));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot,
            com.openggf.game.rewind.schema.RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
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
            conversionSpawnCursor = extra.conversionSpawnCursor();
            cameraTargetX = extra.cameraTargetX();
            playersLocked = extra.playersLocked();
            freshLockApplied = extra.freshLockApplied();
            finalPanStarted = extra.finalPanStarted();
            conversionSequenceStarted = extra.conversionSequenceStarted();
            resultsBackdrop = extra.resultsBackdrop();
            directedControlPlayers.restore(extra.ownedPlayers(), context);
        }
    }
}

package com.openggf.game.sonic2.objects;
import com.openggf.level.objects.ExplosionObjectInstance;

import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.boss.BossExplosionObjectInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2MCZBossInstance;
import com.openggf.level.objects.AbstractObjectRegistry;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectFactory;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.game.sonic2.objects.badniks.AsteronBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.AquisBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.OctusBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.MasherBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.BuzzerBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.CoconutsBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.FlasherBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.SpinyBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.SpinyOnWallBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.GrabberBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.ChopChopBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.WhispBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.GrounderBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.CrawlBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.CrawltonBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.SpikerBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.SpikerDrillObjectInstance;
import com.openggf.game.sonic2.objects.badniks.SolBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.RexonBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.RexonHeadObjectInstance;
import com.openggf.game.sonic2.objects.badniks.ShellcrackerBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.SlicerBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.NebulaBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.TurtloidBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.BalkiryBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.BalkiryJetObjectInstance;
import com.openggf.game.sonic2.objects.badniks.CluckerBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.WFZStickBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.WFZUnknownBadnikInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2EHZBossInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2CPZBossInstance;
import com.openggf.game.sonic2.objects.bosses.ARZBossArrow;
import com.openggf.game.sonic2.objects.bosses.ARZBossEyes;
import com.openggf.game.sonic2.objects.bosses.ARZBossPillar;
import com.openggf.game.sonic2.objects.bosses.Sonic2ARZBossInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2CNZBossInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2HTZBossInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2DEZEggmanInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2MTZBossInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance;
import com.openggf.game.sonic2.objects.badniks.ShellcrackerClawInstance;
import com.openggf.game.sonic2.objects.badniks.SlicerPincerInstance;
import com.openggf.game.sonic2.objects.badniks.TurtloidJetInstance;
import com.openggf.game.sonic2.objects.badniks.TurtloidRiderInstance;
import com.openggf.game.sonic2.objects.badniks.SolFireballObjectInstance;
import com.openggf.game.sonic2.objects.bosses.HTZBossFlamethrower;
import com.openggf.game.sonic2.objects.bosses.HTZBossLavaBall;
import com.openggf.game.sonic2.objects.bosses.CNZBossElectricBall;
import com.openggf.game.sonic2.objects.bosses.CPZBossContainer;
import com.openggf.game.sonic2.objects.bosses.CPZBossContainerExtend;
import com.openggf.game.sonic2.objects.bosses.CPZBossContainerFloor;
import com.openggf.game.sonic2.objects.bosses.CPZBossDripper;
import com.openggf.game.sonic2.objects.bosses.CPZBossFlame;
import com.openggf.game.sonic2.objects.bosses.CPZBossGunk;
import com.openggf.game.sonic2.objects.bosses.CPZBossPipe;
import com.openggf.game.sonic2.objects.bosses.CPZBossPipePump;
import com.openggf.game.sonic2.objects.bosses.CPZBossPipeSegment;
import com.openggf.game.sonic2.objects.bosses.CPZBossPump;
import com.openggf.game.sonic2.objects.bosses.CPZBossRobotnik;
import com.openggf.game.sonic2.objects.bosses.LavaBubbleObjectInstance;
import com.openggf.game.sonic2.objects.bosses.MCZFallingDebrisInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class Sonic2ObjectRegistry extends AbstractObjectRegistry {
    private static final Logger LOGGER = Logger.getLogger(Sonic2ObjectRegistry.class.getName());
    private static final String BUZZER_FLAME_CHILD_CLASS =
            "com.openggf.game.sonic2.objects.badniks.BuzzerBadnikInstance$BuzzerFlameChild";
    // Batch-inner2 inner-class hazard/solid child binary names.
    private static final String DEZ_ROBOT_ARTICULATED_CHILD_CLASS =
            "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance$ArticulatedChild";
    private static final String DEZ_ROBOT_BOMB_CHILD_CLASS =
            "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance$BombChild";
    private static final String DEZ_ROBOT_HEAD_CHILD_CLASS =
            "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance$HeadChild";
    private static final String DEZ_ROBOT_JET_CHILD_CLASS =
            "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance$JetChild";
    private static final String WFZ_FLOATING_PLATFORM_CLASS =
            "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance$WFZFloatingPlatform";
    private static final String WFZ_LASER_WALL_CLASS =
            "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance$WFZLaserWall";
    private static final String WFZ_PLATFORM_HURT_CLASS =
            "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance$WFZPlatformHurt";
    private static final List<DynamicObjectRewindCodec> DYNAMIC_REWIND_CODECS = List.of(
            // BadnikProjectileInstance now implements RewindRecreatable -> genericRecreate Path 1.
            buzzerFlameCodec(),
            // MonitorContentsObjectInstance now implements RewindRecreatable -> genericRecreate Path 1.
            checkpointDongleCodec(),
            checkpointStarCodec(),
            arzBossArrowCodec(),
            arzBossPillarCodec(),
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    GrounderRockProjectile.class,
                    spawn -> new GrounderRockProjectile(spawn.x(), spawn.y(), 0, null)),
            grounderWallCodec(),
            // HtzFireProjectileObjectInstance now implements RewindRecreatable -> genericRecreate Path 1.
            // NOTE: EHZ boss child codecs (Spike, Wheel, GroundVehicle, Propeller,
            // VehicleTop) intentionally REMOVED. All five are construction-spawned
            // (inside initializeBossState() → spawnChildComponents()), so the
            // activeObjects restore loop re-establishes them when the boss is
            // reconstructed via registry.create(). A codec would add a second copy
            // from the dynamic-objects restore loop, doubling the count (7 → 14).
            // The Propeller is also reloaded from a routine (reloadPropeller during
            // flying-off) but that is the same singleton child; reconstruction still
            // re-establishes the construction instance. MTZBossLaser (fired) and the
            // routine-fired children keep their codecs.
            // See docs/KNOWN_DISCREPANCIES.md and TestBossChildNoDoubleSpawnParity.
            balkiryJetCodec(),
            // ArrowProjectileInstance now implements RewindRecreatable -> genericRecreate Path 1.
            shellcrackerClawCodec(),
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    SlicerPincerInstance.class,
                    spawn -> new SlicerPincerInstance(
                            spawn, null, spawn.x(), spawn.y(), 0, false, 0)),
            // SpikerDrillObjectInstance now implements RewindRecreatable -> genericRecreate Path 1.
            turtloidJetCodec(),
            turtloidRiderCodec(),
            solFireballCodec(),
            // WallTurretShotInstance and VerticalLaserObjectInstance now implement
            // RewindRecreatable -> genericRecreate Path 1.
            // SpikyBlockSpikeInstance now implements RewindRecreatable -> genericRecreate Path 1.
            // BombPrizeObjectInstance now implements RewindRecreatable -> genericRecreate Path 1.
            // RingPrizeObjectInstance now implements RewindRecreatable -> genericRecreate Path 1.
            // SteamPuffObjectInstance now implements RewindRecreatable -> genericRecreate Path 1.
            seesawBallCodec(),
            cpzBossContainerExtendCodec(),
            htzFlamethrowerCodec(),
            htzBossLavaBallCodec(),
            cnzBossElectricBallCodec(),
            // Batch-4 S2 rewind codecs (CPZ boss component chain + OOZ flame).
            // LavaBubbleObjectInstance and MCZFallingDebrisInstance now implement
            // RewindRecreatable -> genericRecreate Path 1.
            // BubbleObjectInstance now implements RewindRecreatable -> genericRecreate Path 1.
            cpzBossContainerCodec(),
            cpzContainerFloorCodec(),
            // CPZBossFallingPart now implements RewindRecreatable -> genericRecreate Path 1.
            cpzBossFlameCodec(),
            cpzBossGunkCodec(),
            cpzBossPipeCodec(),
            cpzBossPipePumpCodec(),
            cpzBossPumpCodec(),
            cpzBossRobotnikCodec(),
            oozBurnerFlameCodec(),
            // Batch-5 S2 rewind codecs (CPZ-boss Dripper/PipeSegment, Rexon head,
            // Egg-prison button, results screen).
            // DestroyedEggPrisonObjectInstance now implements
            // RewindRecreatable -> genericRecreate Path 1.
            cpzBossDripperCodec(),
            cpzBossPipeSegmentCodec(),
            rexonHeadCodec(),
            eggPrisonButtonCodec(),
            // LeafParticleObjectInstance now implements RewindRecreatable -> genericRecreate Path 1.
            // ResultsScreenObjectInstance now implements RewindRecreatable -> genericRecreate Path 1.
            // BossExplosionObjectInstance now implements RewindRecreatable -> genericRecreate Path 1.
            // DEZ Eggman BarrierWall codec deleted: RewindRecreatable generic
            // recreate relinks the parent barrierWall back-reference.
            // MTZ boss laser now implements RewindRecreatable -> genericRecreate Path 1.
            // Batch-inner2 S2 rewind codecs (DEZ Death Egg Robot bomb child +
            // WFZ floating-platform/laser-wall/platform-hurt children).
            //
            // NOTE: ArticulatedChild, HeadChild, JetChild codecs intentionally REMOVED.
            // These three children are construction-spawned (inside initializeBossState() →
            // spawnChildren()), so they are re-established by boss reconstruction during
            // the activeObjects restore loop. Adding a codec would produce a second copy
            // from the dynamic-objects restore loop, doubling the count (10 → 18).
            // ForearmChild has no codec and is correct (also construction-spawned).
            // BombChild is kept because it is fired from an attack routine, not construction.
            // See docs/KNOWN_DISCREPANCIES.md and TestBossChildNoDoubleSpawnParity.
            deathEggRobotBombCodec(),
            wfzFloatingPlatformCodec(),
            wfzLaserWallCodec(),
            wfzPlatformHurtCodec());

    private final Map<Integer, List<String>> namesById = new HashMap<>();
    private final Set<Integer> unknownIds = new HashSet<>();

    public Sonic2ObjectRegistry() {
    }

    @Override
    public ObjectInstance create(ObjectSpawn spawn) {
        ensureLoaded();
        int id = spawn.objectId();
        ObjectFactory factory = factories.get(id);
        if (factory == null) {
            factory = defaultFactory;
            if (!namesById.containsKey(id) && unknownIds.add(id)) {
                LOGGER.info(() -> String.format("Object registry missing id 0x%02X (seen in placement list).", id));
            }
        }
        return factory.create(spawn, this);
    }

    @Override
    public void registerFactory(int objectId, ObjectFactory factory) {
        factories.put(objectId & 0xFF, factory);
    }

    public boolean hasRegisteredFactory(int objectId) {
        ensureLoaded();
        return factories.containsKey(objectId & 0xFF);
    }

    @Override
    public String getPrimaryName(int objectId) {
        ensureLoaded();
        List<String> names = namesById.get(objectId);
        if (names == null || names.isEmpty()) {
            return String.format("Obj%02X", objectId & 0xFF);
        }
        return names.get(0);
    }

    @Override
    public List<String> getAliases(int objectId) {
        ensureLoaded();
        List<String> names = namesById.get(objectId);
        if (names == null) {
            return List.of();
        }
        return Collections.unmodifiableList(names);
    }

    @Override
    public ObjectSlotLayout objectSlotLayout() {
        return ObjectSlotLayout.SONIC_2;
    }

    @Override
    public com.openggf.level.objects.ObjectWindowingStrategy objectWindowingStrategy() {
        return S2ObjectWindowing.INSTANCE;
    }

    @Override
    public List<DynamicObjectRewindCodec> dynamicRewindCodecs() {
        return DYNAMIC_REWIND_CODECS;
    }

    @Override
    public void reportCoverage(List<ObjectSpawn> spawns) {
        ensureLoaded();
        if (spawns == null || spawns.isEmpty()) {
            return;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (ObjectSpawn spawn : spawns) {
            counts.merge(spawn.objectId(), 1, Integer::sum);
        }

        int totalIds = counts.size();
        int missing = 0;
        List<String> missingEntries = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int id = entry.getKey();
            if (!namesById.containsKey(id)) {
                missing++;
                missingEntries.add(String.format("0x%02X (%d)", id, entry.getValue()));
            }
        }

        LOGGER.info(String.format("Object registry coverage: %d unique ids in level, %d missing names.",
                totalIds, missing));
        if (!missingEntries.isEmpty()) {
            LOGGER.info("Missing object ids: " + String.join(", ", missingEntries));
        }
    }

    private static DynamicObjectRewindCodec buzzerFlameCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(BUZZER_FLAME_CHILD_CLASS);
            }

            @Override
            public String className() {
                return BUZZER_FLAME_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                try {
                    var extra = (PerObjectRewindSnapshot.BuzzerFlameRewindExtra)
                            entry.state().objectSubclassExtra();
                    BuzzerBadnikInstance parent = findBuzzerParentForRewind(context, extra.parentSlotIndex());
                    if (parent == null) {
                        return null;
                    }
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(ObjectSpawn.class, BuzzerBadnikInstance.class);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(entry.spawn(), parent);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec checkpointDongleCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof CheckpointDongleInstance;
            }

            @Override
            public String className() {
                return CheckpointDongleInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                CheckpointObjectInstance parent = findCheckpointParentForRewind(context, entry.spawn());
                return parent == null ? null : new CheckpointDongleInstance(parent);
            }
        };
    }

    private static DynamicObjectRewindCodec checkpointStarCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof CheckpointStarInstance;
            }

            @Override
            public String className() {
                return CheckpointStarInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                CheckpointObjectInstance parent = findCheckpointParentForRewind(context, entry.spawn());
                return parent == null ? null : new CheckpointStarInstance(parent, 0);
            }
        };
    }

    private static BuzzerBadnikInstance findBuzzerParentForRewind(
            DynamicObjectRecreateContext context, int parentSlotIndex) {
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (inst instanceof BuzzerBadnikInstance buzzer
                    && buzzer.getSlotIndex() == parentSlotIndex) {
                return buzzer;
            }
        }
        return null;
    }

    private static CheckpointObjectInstance findCheckpointParentForRewind(
            DynamicObjectRecreateContext context, ObjectSpawn childSpawn) {
        if (childSpawn == null) {
            return null;
        }
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (inst instanceof CheckpointObjectInstance checkpoint
                    && checkpoint.getCenterX() == childSpawn.x()
                    && checkpoint.getCenterY() == childSpawn.y()) {
                return checkpoint;
            }
        }
        return null;
    }

    private static <T> T findLiveInstance(DynamicObjectRecreateContext context, Class<T> type) {
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (type.isInstance(inst)) {
                return type.cast(inst);
            }
        }
        return null;
    }

    // ---- Batch-inner2 inner-class hazard/solid child relink codecs ----

    private static DynamicObjectRewindCodec dezRobotArticulatedChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                // Exact-class match: do NOT catch the ForearmChild subclass (it needs its own
                // codec because `isFront` is a final ctor-only field).
                return instance.getClass().getName().equals(DEZ_ROBOT_ARTICULATED_CHILD_CLASS);
            }

            @Override
            public String className() {
                return DEZ_ROBOT_ARTICULATED_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                try {
                    // Spawn-order restore guarantees the placed Death Egg Robot body is already
                    // live; relink it so the recreated articulated part shares the boss lifetime
                    // (mirrors the other parent-relink codecs in this registry).
                    Sonic2DeathEggRobotInstance parent =
                            findLiveInstance(context, Sonic2DeathEggRobotInstance.class);
                    if (parent == null) {
                        return null;
                    }
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            Sonic2DeathEggRobotInstance.class, String.class, int.class, int.class);
                    ctor.setAccessible(true);
                    // priority/frame are placeholder ctor args; the actual non-spawn-derivable
                    // state (frame, priority, currentX/currentY, xFixed/yFixed, falling, xVel,
                    // yVel, fallTimer) is all stored in non-final fields and reapplied by
                    // GenericFieldCapturer after recreate. The part carries its own in-flight
                    // falling trajectory and is NOT re-emitted by the body, so it must be
                    // restored rather than dropped.
                    return (ObjectInstance) ctor.newInstance(parent, "ArticulatedChild", 0, 0);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec deathEggRobotBombCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(DEZ_ROBOT_BOMB_CHILD_CLASS);
            }

            @Override
            public String className() {
                return DEZ_ROBOT_BOMB_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // The Death Egg Robot is the placed/layout-spawned boss, recreated
                // before the dynamic-object restore loop, so it is in getActiveObjects().
                Sonic2DeathEggRobotInstance boss =
                        findLiveInstance(context, Sonic2DeathEggRobotInstance.class);
                if (boss == null) {
                    return null; // boss gone -> drop the orphaned bomb
                }
                try {
                    Class<?> cls = Class.forName(entry.className());
                    // Placeholder ctor args (0,0,0,0): currentX/currentY/xVel/yVel and
                    // the other in-flight scalars are non-final and reapplied by the
                    // generic field capturer after recreate.
                    var ctor = cls.getDeclaredConstructor(
                            Sonic2DeathEggRobotInstance.class, int.class, int.class, int.class, int.class);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(boss, 0, 0, 0, 0);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec dezBossHeadCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(DEZ_ROBOT_HEAD_CHILD_CLASS);
            }

            @Override
            public String className() {
                return DEZ_ROBOT_HEAD_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // Relink the live parent boss body (only one DEZ Death Egg Robot exists at a time).
                Sonic2DeathEggRobotInstance parent =
                        findLiveInstance(context, Sonic2DeathEggRobotInstance.class);
                if (parent == null) {
                    return null;
                }
                try {
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            Sonic2DeathEggRobotInstance.class, int.class);
                    ctor.setAccessible(true);
                    // priority is hardcoded to 4 for the head in spawnChildren(); it is a
                    // non-final field, so GenericFieldCapturer reapplies the captured value
                    // (along with headRoutine/waitTimer/bodyMiscSignaled/glow*) after construct.
                    return (ObjectInstance) ctor.newInstance(parent, 4);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec dezJetChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(DEZ_ROBOT_JET_CHILD_CLASS);
            }

            @Override
            public String className() {
                return DEZ_ROBOT_JET_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                try {
                    // Spawn-order restore guarantees the (only) Death Egg Robot body is
                    // already live; relink its back-reference so the boss keeps driving the
                    // restored jet (jet.setJetRoutine(...) / positionNonAnimatedChildren()).
                    Sonic2DeathEggRobotInstance parent = findLiveInstance(
                            context, Sonic2DeathEggRobotInstance.class);
                    if (parent == null) {
                        return null;
                    }
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            Sonic2DeathEggRobotInstance.class, int.class);
                    ctor.setAccessible(true);
                    // priority 4 = the fixed spawn constant from spawnChildren()
                    ObjectInstance child = (ObjectInstance) ctor.newInstance(parent, 4);
                    // Relink parent.jet to the restored child. Animation scalars
                    // (jetRoutine/jetAnimId/jetFrame/
                    // animIdx/animTimer) and collisionFlags are non-final and reapplied by
                    // GenericFieldCapturer after recreate.
                    var f = Sonic2DeathEggRobotInstance.class.getDeclaredField("jet");
                    f.setAccessible(true);
                    f.set(parent, child);
                    return child;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec wfzFloatingPlatformCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(WFZ_FLOATING_PLATFORM_CLASS);
            }

            @Override
            public String className() {
                return WFZ_FLOATING_PLATFORM_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                try {
                    // Relink the single live (restored) WFZ boss as the platform's parent.
                    Sonic2WFZBossInstance boss = findLiveInstance(context, Sonic2WFZBossInstance.class);
                    if (boss == null) {
                        return null;
                    }
                    // Independent floating platform (SolidObjectProvider) flies its own
                    // descend->horizontal-oscillation trajectory once released and is NOT
                    // re-emitted by the PlatformReleaser, so it must be restored. Spawn x/y are
                    // placeholders; phase/phaseTimer/reverseTimer/xVel/yVel/xFixed/yFixed/baseY
                    // and currentX/currentY are all non-final and reapplied by GenericFieldCapturer
                    // after recreate (hurtChild back-ref is relinked by its own WFZPlatformHurt codec).
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            Sonic2WFZBossInstance.class, int.class, int.class);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(
                            boss, entry.spawn().x(), entry.spawn().y());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec wfzLaserWallCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(WFZ_LASER_WALL_CLASS);
            }

            @Override
            public String className() {
                return WFZ_LASER_WALL_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                try {
                    // Spawn-order restore guarantees the parent boss is already live.
                    Sonic2WFZBossInstance parent = findLiveInstance(
                            context, Sonic2WFZBossInstance.class);
                    if (parent == null) {
                        return null;
                    }
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            Sonic2WFZBossInstance.class, int.class, int.class);
                    ctor.setAccessible(true);
                    ObjectInstance child = (ObjectInstance) ctor.newInstance(
                            parent, entry.spawn().x(), entry.spawn().y());
                    // Relink whichever side back-reference the restored parent is missing
                    // (two walls share this binary name; left vs right by null slot / X side).
                    var leftF = Sonic2WFZBossInstance.class.getDeclaredField("leftWall");
                    var rightF = Sonic2WFZBossInstance.class.getDeclaredField("rightWall");
                    leftF.setAccessible(true);
                    rightF.setAccessible(true);
                    if (leftF.get(parent) == null) {
                        leftF.set(parent, child);
                    } else if (rightF.get(parent) == null) {
                        rightF.set(parent, child);
                    }
                    // Keep the parent's childComponents list consistent.
                    var listF = Sonic2WFZBossInstance.class.getDeclaredField("childComponents");
                    listF.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> comps = (java.util.List<Object>) listF.get(parent);
                    if (comps != null && !comps.contains(child)) {
                        comps.add(child);
                    }
                    return child;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec wfzPlatformHurtCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(WFZ_PLATFORM_HURT_CLASS);
            }

            @Override
            public String className() {
                return WFZ_PLATFORM_HURT_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                try {
                    // Relink the live (already spawn-order-restored) platform parent.
                    ObjectInstance platformParent = findWfzPlatformParentForRewind(
                            context, entry.spawn());
                    if (platformParent == null) {
                        return null;  // platform not yet restored / dropped -> drop child this pass
                    }
                    ObjectInstance boss = findLiveInstance(context, Sonic2WFZBossInstance.class);
                    Class<?> cls = Class.forName(entry.className());
                    Class<?> platformCls = Class.forName(WFZ_FLOATING_PLATFORM_CLASS);
                    var ctor = cls.getDeclaredConstructor(
                            Sonic2WFZBossInstance.class, platformCls);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(boss, platformParent);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    // Match the restored WFZFloatingPlatform parent by position (mirrors
    // findCheckpointParentForRewind). Requires the platform's own codec to have restored
    // it earlier in spawn order. The hurt child sits at platformX, platformY+0x0C.
    private static ObjectInstance findWfzPlatformParentForRewind(
            DynamicObjectRecreateContext context, ObjectSpawn childSpawn) {
        if (childSpawn == null) {
            return null;
        }
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (inst.getClass().getName().equals(WFZ_FLOATING_PLATFORM_CLASS)
                    && inst.getX() == childSpawn.x()
                    && inst.getY() == (childSpawn.y() - 0x0C)) {
                return inst;
            }
        }
        return null;
    }

    private static DynamicObjectRewindCodec arzBossArrowCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == ARZBossArrow.class;
            }

            @Override
            public String className() {
                return ARZBossArrow.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2ARZBossInstance boss = findLiveInstance(context, Sonic2ARZBossInstance.class);
                ARZBossEyes eyes = findLiveInstance(context, ARZBossEyes.class);
                if (boss == null || eyes == null) {
                    return null;
                }
                // RENDER_X_FLIP (0x01) is set on the spawn renderFlags exactly for the
                // right-pillar arrow, and is the same bit initArrow() OR-sets, so this
                // derivation is correct against both pre-init and post-init renderFlags.
                boolean fromRightPillar = (entry.spawn().renderFlags() & 1) != 0;
                return new ARZBossArrow(entry.spawn(), boss, eyes, fromRightPillar);
            }
        };
    }

    private static DynamicObjectRewindCodec arzBossPillarCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof ARZBossPillar;
            }

            @Override
            public String className() {
                return ARZBossPillar.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2ARZBossInstance parent = findArzBossParentForRewind(context);
                return parent == null ? null : new ARZBossPillar(entry.spawn(), parent);
            }
        };
    }

    private static Sonic2ARZBossInstance findArzBossParentForRewind(
            DynamicObjectRecreateContext context) {
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (inst instanceof Sonic2ARZBossInstance boss) {
                return boss;
            }
        }
        return null;
    }

    private static DynamicObjectRewindCodec grounderWallCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof GrounderWallInstance;
            }

            @Override
            public String className() {
                return GrounderWallInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // Relink to a live Grounder parent so the pre-activation gate still
                // works. Parent is only consulted while !activated; once activated
                // (a restored non-final scalar) it is irrelevant. wallIndex 0 is a
                // placeholder: the velocities it derives are overwritten by
                // restoreObjectRewindState (xVelocity/yVelocity are non-final).
                GrounderBadnikInstance parent = findGrounderParentForRewind(context);
                ObjectSpawn s = entry.spawn();
                return new GrounderWallInstance(s.x(), s.y(), 0, parent);
            }
        };
    }

    private static GrounderBadnikInstance findGrounderParentForRewind(
            DynamicObjectRecreateContext context) {
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (inst instanceof GrounderBadnikInstance parent) {
                return parent;
            }
        }
        return null;
    }

    // EHZ boss child codecs (Spike, Wheel, GroundVehicle, Propeller, VehicleTop)
    // and the shared findEhzBossParentForRewind() helper were removed: those
    // children are construction-spawned and re-established by boss reconstruction
    // during the activeObjects restore loop, so a codec would double them. See the
    // DYNAMIC_REWIND_CODECS list comment and TestBossChildNoDoubleSpawnParity.

    private static DynamicObjectRewindCodec balkiryJetCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == BalkiryJetObjectInstance.class;
            }

            @Override
            public String className() {
                return BalkiryJetObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                BalkiryBadnikInstance best = null;
                long bestDistance = Long.MAX_VALUE;
                ObjectSpawn spawn = entry.spawn();
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (!(inst instanceof BalkiryBadnikInstance balkiry)) {
                        continue;
                    }
                    if (spawn == null) {
                        best = balkiry;
                        break;
                    }
                    long dx = balkiry.getX() - spawn.x();
                    long dy = balkiry.getY() - spawn.y();
                    long distance = dx * dx + dy * dy;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = balkiry;
                    }
                }
                if (best == null) {
                    return null;
                }
                return new BalkiryJetObjectInstance(entry.spawn(), best);
            }
        };
    }

    private static DynamicObjectRewindCodec shellcrackerClawCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == ShellcrackerClawInstance.class;
            }

            @Override
            public String className() {
                return ShellcrackerClawInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                ObjectSpawn spawn = entry.spawn();
                ShellcrackerBadnikInstance best = null;
                long bestDistance = Long.MAX_VALUE;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (!(inst instanceof ShellcrackerBadnikInstance body)) {
                        continue;
                    }
                    if (spawn == null) {
                        best = body;
                        break;
                    }
                    long dx = body.getX() - spawn.x();
                    long dy = body.getY() - spawn.y();
                    long distance = dx * dx + dy * dy;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = body;
                    }
                }
                if (best == null) {
                    return null;
                }
                // pieceIndex / facingRight are un-finaled non-spawn scalars; restoreObjectRewindState
                // reapplies the captured values after construction, so placeholders are safe here.
                return new ShellcrackerClawInstance(spawn, best, spawn.x(), spawn.y(), 0, false);
            }
        };
    }

    private static DynamicObjectRewindCodec turtloidJetCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == TurtloidJetInstance.class;
            }

            @Override
            public String className() {
                return TurtloidJetInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                TurtloidBadnikInstance best = null;
                long bestDistance = Long.MAX_VALUE;
                ObjectSpawn spawn = entry.spawn();
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (!(inst instanceof TurtloidBadnikInstance turtloid)) {
                        continue;
                    }
                    if (spawn == null) {
                        best = turtloid;
                        break;
                    }
                    long dx = turtloid.getX() - spawn.x();
                    long dy = turtloid.getY() - spawn.y();
                    long distance = dx * dx + dy * dy;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = turtloid;
                    }
                }
                if (best == null) {
                    return null;
                }
                return new TurtloidJetInstance(entry.spawn(), best);
            }
        };
    }

    private static DynamicObjectRewindCodec turtloidRiderCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == TurtloidRiderInstance.class;
            }

            @Override
            public String className() {
                return TurtloidRiderInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // The rider is spawned at parentBodyPos + (+4, -0x18); find the live
                // placed Turtloid body whose position best matches that derivation.
                TurtloidBadnikInstance best = null;
                long bestDistance = Long.MAX_VALUE;
                ObjectSpawn spawn = entry.spawn();
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (!(inst instanceof TurtloidBadnikInstance turtloid)) {
                        continue;
                    }
                    if (spawn == null) {
                        best = turtloid;
                        break;
                    }
                    long dx = turtloid.getX() - spawn.x();
                    long dy = turtloid.getY() - spawn.y();
                    long distance = dx * dx + dy * dy;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = turtloid;
                    }
                }
                if (best == null) {
                    return null;
                }
                return new TurtloidRiderInstance(entry.spawn(), best);
            }
        };
    }

    private static DynamicObjectRewindCodec solFireballCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == SolFireballObjectInstance.class;
            }

            @Override
            public String className() {
                return SolFireballObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // All four fireballs are spawned with the parent's own ObjectSpawn, so the
                // live Sol's position matches entry.spawn(); fall back to first live Sol.
                SolBadnikInstance best = null;
                long bestDistance = Long.MAX_VALUE;
                ObjectSpawn spawn = entry.spawn();
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (!(inst instanceof SolBadnikInstance sol)) {
                        continue;
                    }
                    if (spawn == null) {
                        best = sol;
                        break;
                    }
                    long dx = sol.getX() - spawn.x();
                    long dy = sol.getY() - spawn.y();
                    long distance = dx * dx + dy * dy;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = sol;
                    }
                }
                if (best == null) {
                    return null;
                }
                // angle is a non-final captured scalar reapplied by restoreObjectRewindState.
                return new SolFireballObjectInstance(entry.spawn(), best, 0);
            }
        };
    }

    // ehzBossGroundVehicleCodec / ehzBossPropellerCodec / ehzBossVehicleTopCodec
    // removed (construction-spawned EHZ boss children — see DYNAMIC_REWIND_CODECS
    // list comment).

    private static DynamicObjectRewindCodec htzFlamethrowerCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof HTZBossFlamethrower;
            }

            @Override
            public String className() {
                return HTZBossFlamethrower.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2HTZBossInstance parent = findHtzBossParentForRewind(context);
                if (parent == null) {
                    return null;
                }
                // currentX/currentY/xVel/flipped/animFrame/animTimer are non-final scalars
                // reapplied by restoreObjectRewindState, so the spawn-time offset math in
                // the ctor is overwritten and placeholder ctor args are safe.
                return new HTZBossFlamethrower(parent, entry.spawn().x(), entry.spawn().y(), false);
            }
        };
    }

    private static DynamicObjectRewindCodec htzBossLavaBallCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == HTZBossLavaBall.class;
            }

            @Override
            public String className() {
                return HTZBossLavaBall.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2HTZBossInstance parent = findHtzBossParentForRewind(context);
                if (parent == null) {
                    return null;
                }
                // spawnX/spawnY/leftBall/fromLeftSide are placeholders: currentX/currentY come
                // from the per-object snapshot, and leftBall/fromLeftSide/xFixed/yFixed/xVel/
                // yVel/animFrame/animTimer are non-final captured scalars reapplied on restore.
                return new HTZBossLavaBall(parent, 0, 0, false, false);
            }
        };
    }

    private static Sonic2HTZBossInstance findHtzBossParentForRewind(
            DynamicObjectRecreateContext context) {
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (inst instanceof Sonic2HTZBossInstance boss) {
                return boss;
            }
        }
        return null;
    }

    private static DynamicObjectRewindCodec cnzBossElectricBallCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CNZBossElectricBall.class;
            }

            @Override
            public String className() {
                return CNZBossElectricBall.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2CNZBossInstance boss = findLiveInstance(context, Sonic2CNZBossInstance.class);
                if (boss == null) {
                    return null;
                }
                return new CNZBossElectricBall(entry.spawn(), boss);
            }
        };
    }

    // ---- Batch-4 CPZ boss-component relink codecs ----

    private static DynamicObjectRewindCodec cpzBossContainerCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CPZBossContainer.class;
            }

            @Override
            public String className() {
                return CPZBossContainer.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2CPZBossInstance boss = findLiveInstance(context, Sonic2CPZBossInstance.class);
                if (boss == null) {
                    return null;
                }
                // mainBoss relinked via ctor; all scalar state (routineSecondary, xVel, timer2,
                // floorSpawned/extendSpawned latches, anim, mappingFrame, ...) is non-final and
                // reapplied by restoreObjectRewindState; animationState is rebuilt in the ctor.
                return new CPZBossContainer(entry.spawn(), boss);
            }
        };
    }

    private static DynamicObjectRewindCodec cpzContainerFloorCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CPZBossContainerFloor.class;
            }

            @Override
            public String className() {
                return CPZBossContainerFloor.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2CPZBossInstance boss = findLiveInstance(context, Sonic2CPZBossInstance.class);
                CPZBossContainer container = findLiveInstance(context, CPZBossContainer.class);
                if (boss == null || container == null) {
                    return null;
                }
                // isFloor2 is now a non-final scalar restored by restoreObjectRewindState, so
                // false here is a placeholder; anim/timer2 (non-final) are likewise overwritten
                // on restore; animationState is rebuilt in the ctor.
                return new CPZBossContainerFloor(entry.spawn(), boss, container, false);
            }
        };
    }

    private static DynamicObjectRewindCodec cpzBossContainerExtendCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof CPZBossContainerExtend;
            }

            @Override
            public String className() {
                return CPZBossContainerExtend.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // Both parents (boss + container) are placed/dynamic objects restored
                // earlier in spawn order, so they are live during recreate. The extend's
                // object-ref fields (mainBoss/container) are final and arrive via the ctor;
                // the scalar/state fields (x, y, renderFlags, anim, mappingFrame) are
                // non-final and reapplied by restoreObjectRewindState.
                Sonic2CPZBossInstance boss = findLiveInstance(context, Sonic2CPZBossInstance.class);
                CPZBossContainer container = findLiveInstance(context, CPZBossContainer.class);
                if (boss == null || container == null) {
                    return null;
                }
                return new CPZBossContainerExtend(entry.spawn(), boss, container);
            }
        };
    }

    private static DynamicObjectRewindCodec seesawBallCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof SeesawBallObjectInstance;
            }

            @Override
            public String className() {
                return SeesawBallObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                SeesawObjectInstance parent = findSeesawParentForRewind(context);
                if (parent == null) {
                    return null; // no live parent -> stay dropped this frame
                }
                // Derive ctor args from the live parent exactly as SeesawObjectInstance
                // .spawnBall() does. initialX/initialY are placeholders; xPos/yPos
                // (non-final) are reapplied by restoreObjectRewindState afterwards, as is
                // storedAngle, so 'flipped' only needs to seed a valid initial value.
                int centerX = parent.getSpawn().x();
                int bottomY = parent.getSpawn().y() + 0x10;
                boolean flipped = parent.isFlippedHorizontal();
                return new SeesawBallObjectInstance(centerX, bottomY, centerX, bottomY, parent, flipped);
            }
        };
    }

    private static SeesawObjectInstance findSeesawParentForRewind(DynamicObjectRecreateContext context) {
        // One Seesaw (subtype 0) spawns exactly one ball; match the live seesaw that is
        // still missing its ball so the restore re-links 1:1.
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (inst instanceof SeesawObjectInstance seesaw
                    && seesaw.getSpawn().subtype() == 0
                    && !seesaw.hasLiveBall()) {
                return seesaw;
            }
        }
        return null;
    }

    private static DynamicObjectRewindCodec cpzBossFlameCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CPZBossFlame.class;
            }

            @Override
            public String className() {
                return CPZBossFlame.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2CPZBossInstance boss = findLiveInstance(context, Sonic2CPZBossInstance.class);
                return boss == null ? null : new CPZBossFlame(entry.spawn(), boss);
            }
        };
    }

    private static DynamicObjectRewindCodec cpzBossGunkCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CPZBossGunk.class;
            }

            @Override
            public String className() {
                return CPZBossGunk.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2CPZBossInstance boss = findLiveInstance(context, Sonic2CPZBossInstance.class);
                if (boss == null) {
                    return null;
                }
                // gunkReady=false is a placeholder; routineSecondary/timer2 (non-final) are
                // overwritten on restore. Droplets share this class and are covered too.
                return new CPZBossGunk(entry.spawn(), boss, false);
            }
        };
    }

    private static DynamicObjectRewindCodec cpzBossPipeCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CPZBossPipe.class;
            }

            @Override
            public String className() {
                return CPZBossPipe.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2CPZBossInstance boss = findLiveInstance(context, Sonic2CPZBossInstance.class);
                if (boss == null) {
                    return null;
                }
                return new CPZBossPipe(entry.spawn(), boss);
            }
        };
    }

    private static DynamicObjectRewindCodec cpzBossPipePumpCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CPZBossPipePump.class;
            }

            @Override
            public String className() {
                return CPZBossPipePump.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2CPZBossInstance boss = findLiveInstance(context, Sonic2CPZBossInstance.class);
                CPZBossPipe pipe = findLiveInstance(context, CPZBossPipe.class);
                return pipe == null ? null : new CPZBossPipePump(entry.spawn(), boss, pipe);
            }
        };
    }

    private static DynamicObjectRewindCodec cpzBossPumpCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CPZBossPump.class;
            }

            @Override
            public String className() {
                return CPZBossPump.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2CPZBossInstance boss = findLiveInstance(context, Sonic2CPZBossInstance.class);
                return boss == null ? null : new CPZBossPump(entry.spawn(), boss);
            }
        };
    }

    private static DynamicObjectRewindCodec cpzBossRobotnikCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CPZBossRobotnik.class;
            }

            @Override
            public String className() {
                return CPZBossRobotnik.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2CPZBossInstance boss = findLiveInstance(context, Sonic2CPZBossInstance.class);
                return boss == null ? null : new CPZBossRobotnik(entry.spawn(), boss);
            }
        };
    }

    private static DynamicObjectRewindCodec oozBurnerFlameCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof OOZBurnerFlameObjectInstance;
            }

            @Override
            public String className() {
                return OOZBurnerFlameObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                OOZPoppingPlatformObjectInstance parent =
                        findOozFlameParentForRewind(context, entry.spawn());
                return parent == null
                        ? null
                        : new OOZBurnerFlameObjectInstance(entry.spawn(), parent);
            }
        };
    }

    private static OOZPoppingPlatformObjectInstance findOozFlameParentForRewind(
            DynamicObjectRecreateContext context, ObjectSpawn childSpawn) {
        if (childSpawn == null) {
            return null;
        }
        // The flame spawns at flameX = platform.getX(), flameY = platform.getHomeY() - 0x10.
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (inst instanceof OOZPoppingPlatformObjectInstance platform
                    && platform.getX() == childSpawn.x()
                    && platform.getHomeY() == childSpawn.y() + 0x10) {
                return platform;
            }
        }
        return null;
    }

    // ---- Batch-5 relink codecs ----

    private static DynamicObjectRewindCodec cpzBossDripperCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CPZBossDripper.class;
            }

            @Override
            public String className() {
                return CPZBossDripper.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2CPZBossInstance boss = findLiveInstance(context, Sonic2CPZBossInstance.class);
                CPZBossPipe pipe = findLiveInstance(context, CPZBossPipe.class);
                // mainBoss + parentPipe relinked via ctor (both stay final object refs).
                // All scalar state (x, y, renderFlags, routineSecondary, anim, mappingFrame,
                // timer, timer4) is non-final and reapplied by restoreObjectRewindState.
                return pipe == null ? null : new CPZBossDripper(entry.spawn(), boss, pipe);
            }
        };
    }

    private static DynamicObjectRewindCodec cpzBossPipeSegmentCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CPZBossPipeSegment.class;
            }

            @Override
            public String className() {
                return CPZBossPipeSegment.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic2CPZBossInstance boss = findLiveInstance(context, Sonic2CPZBossInstance.class);
                CPZBossPipe pipe = findLiveInstance(context, CPZBossPipe.class);
                if (pipe == null) {
                    return null;
                }
                // mainBoss + parentPipe relinked via ctor (both stay final). yOffset is a
                // non-final scalar reapplied by restoreObjectRewindState, so 0 here is a
                // placeholder; x/y/renderFlags/anim/mappingFrame/retracting are non-final and
                // overwritten on restore; animationState is rebuilt in the ctor.
                return new CPZBossPipeSegment(entry.spawn(), boss, pipe, 0);
            }
        };
    }

    private static DynamicObjectRewindCodec rexonHeadCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == RexonHeadObjectInstance.class;
            }

            @Override
            public String className() {
                return RexonHeadObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                RexonBadnikInstance parent = findLiveInstance(context, RexonBadnikInstance.class);
                if (parent == null) {
                    return null;
                }
                // parent relinked via ctor. x/y, headIndex, headNumber, xFlip (un-finaled),
                // state, velocities, oscillation phase/dir/counter, baseX/Y, sub-pixels,
                // timers, projectileTimer, destroyed are reapplied by restoreObjectRewindState.
                // linkedHead (sibling) is an object-ref field relinked generically by identity
                // id. Placeholders below are overwritten on restore.
                return new RexonHeadObjectInstance(
                        entry.spawn(), parent,
                        entry.spawn().x(), entry.spawn().y(),
                        0,      // headIndex placeholder (un-finaled, reapplied)
                        false); // xFlip placeholder (un-finaled, reapplied)
            }
        };
    }

    private static DynamicObjectRewindCodec eggPrisonButtonCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == EggPrisonButtonObjectInstance.class;
            }

            @Override
            public String className() {
                return EggPrisonButtonObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                EggPrisonObjectInstance parent =
                        findLiveInstance(context, EggPrisonObjectInstance.class);
                if (parent == null) {
                    return null;
                }
                // parent relinked via ctor; baseY recomputed from spawn (final, spawn-derivable);
                // currentY/triggered are non-final scalars reapplied by restoreObjectRewindState.
                return new EggPrisonButtonObjectInstance(entry.spawn(), parent);
            }
        };
    }

    @Override
    protected void registerDefaultFactories() {
        namesById.putAll(Sonic2ObjectRegistryData.NAMES_BY_ID);
        // LayerSwitcher (0x03) behavior is handled by ObjectManager.PlaneSwitchers,
        // but the ROM still allocates Obj03 into an SST slot before MarkObjGone3.
        registerFactory(Sonic2ObjectIds.LAYER_SWITCHER,
                (spawn, registry) -> new Sonic2LayerSwitcherObjectInstance(
                        spawn, registry.getPrimaryName(spawn.objectId())));

        registerFactory(Sonic2ObjectIds.SPRING,
                (spawn, registry) -> new SpringObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.SPIKES,
                (spawn, registry) -> new SpikeObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.MONITOR,
                (spawn, registry) -> new MonitorObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.MONITOR_CONTENTS,
                (spawn, registry) -> new MonitorContentsObjectInstance(spawn, null));
        registerFactory(Sonic2ObjectIds.CHECKPOINT,
                (spawn, registry) -> new CheckpointObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // Springboard / Lever Spring (CPZ, ARZ, MCZ)
        registerFactory(Sonic2ObjectIds.SPRINGBOARD,
                (spawn, registry) -> new SpringboardObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // ARZ Leaves Generator
        registerFactory(Sonic2ObjectIds.LEAVES_GENERATOR,
                (spawn, registry) -> new LeavesGeneratorObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // ARZ Bubble Generator (Object 0x24)
        // Subtype bit 7 determines mode: generator (invisible spawner) vs child bubble
        registerFactory(Sonic2ObjectIds.BUBBLES, (spawn, registry) -> {
            if ((spawn.subtype() & 0x80) != 0) {
                // Generator mode - invisible spawner that creates rising bubbles
                return new BubbleGeneratorObjectInstance(spawn, registry.getPrimaryName(spawn.objectId()));
            } else {
                // Child bubble mode - shouldn't be in level data normally
                // but handle it by creating a rising bubble at that position
                return new BubbleObjectInstance(spawn.x(), spawn.y(), spawn.subtype() & 0x07, 0);
            }
        });

        // CPZ Objects
        registerFactory(Sonic2ObjectIds.TIPPING_FLOOR,
                (spawn, registry) -> new TippingFloorObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.SPEED_BOOSTER,
                (spawn, registry) -> new SpeedBoosterObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CPZ_SPIN_TUBE,
                (spawn, registry) -> new CPZSpinTubeObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.BLUE_BALLS,
                (spawn, registry) -> new BlueBallsObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.BREAKABLE_BLOCK,
                (spawn, registry) -> new BreakableBlockObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.PIPE_EXIT_SPRING,
                (spawn, registry) -> new PipeExitSpringObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.BARRIER,
                (spawn, registry) -> new BarrierObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CPZ_STAIRCASE,
                (spawn, registry) -> new CPZStaircaseObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CPZ_PYLON,
                (spawn, registry) -> new CPZPylonObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // CNZ Objects
        registerFactory(Sonic2ObjectIds.BUMPER,
                (spawn, registry) -> new BumperObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.HEX_BUMPER,
                (spawn, registry) -> new HexBumperObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.BONUS_BLOCK,
                (spawn, registry) -> new BonusBlockObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.FLIPPER,
                (spawn, registry) -> new FlipperObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.FORCED_SPIN,
                (spawn, registry) -> new ForcedSpinObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.LAUNCHER_SPRING,
                (spawn, registry) -> new LauncherSpringObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CNZ_CONVEYOR_BELT,
                (spawn, registry) -> new CNZConveyorBeltObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CNZ_RECT_BLOCKS,
                (spawn, registry) -> new CNZRectBlocksObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CNZ_BIG_BLOCK,
                (spawn, registry) -> new CNZBigBlockObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CNZ_ELEVATOR,
                (spawn, registry) -> new ElevatorObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.POINT_POKEY,
                (spawn, registry) -> new PointPokeyObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        ObjectFactory platformFactory = (spawn, registry) -> new PlatformObjectInstance(spawn,
                registry.getPrimaryName(spawn.objectId()));
        registerFactory(Sonic2ObjectIds.BRIDGE,
                (spawn, registry) -> new BridgeObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.BRIDGE_STAKE,
                (spawn, registry) -> new BridgeStakeObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.EHZ_WATERFALL,
                (spawn, registry) -> new EHZWaterfallObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.OOZ_LAUNCHER,
                (spawn, registry) -> new OOZLauncherObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.LAUNCHER_BALL,
                (spawn, registry) -> new LauncherBallObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.FAN,
                (spawn, registry) -> new FanObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.OOZ_POPPING_PLATFORM,
                (spawn, registry) -> new OOZPoppingPlatformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.SPIRAL,
                (spawn, registry) -> new SpiralObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // OOZ Badniks
        registerFactory(Sonic2ObjectIds.OCTUS,
                (spawn, registry) -> new OctusBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.AQUIS,
                (spawn, registry) -> new AquisBadnikInstance(spawn));

        // EHZ Badniks
        registerFactory(Sonic2ObjectIds.MASHER,
                (spawn, registry) -> new MasherBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.BUZZER,
                (spawn, registry) -> new BuzzerBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.COCONUTS,
                (spawn, registry) -> new CoconutsBadnikInstance(spawn));

        // CPZ Badniks
        registerFactory(Sonic2ObjectIds.SPINY,
                (spawn, registry) -> new SpinyBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.SPINY_ON_WALL,
                (spawn, registry) -> new SpinyOnWallBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.GRABBER,
                (spawn, registry) -> new GrabberBadnikInstance(spawn));

        // ARZ Badniks
        registerFactory(Sonic2ObjectIds.CHOP_CHOP,
                (spawn, registry) -> new ChopChopBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.WHISP,
                (spawn, registry) -> new WhispBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.GROUNDER_IN_WALL,
                (spawn, registry) -> new GrounderBadnikInstance(spawn, false));
        registerFactory(Sonic2ObjectIds.GROUNDER_IN_WALL2,
                (spawn, registry) -> new GrounderBadnikInstance(spawn, true));
        // Note: GROUNDER_WALL (0x8F) and GROUNDER_ROCKS (0x90) are spawned dynamically

        // HTZ Fire Shooter (Obj20) - fire source that shoots paired fireballs
        registerFactory(Sonic2ObjectIds.LAVA_BUBBLE,
                (spawn, registry) -> new HtzFireShooterObjectInstance(spawn));

        // HTZ Badniks
        registerFactory(Sonic2ObjectIds.SPIKER,
                (spawn, registry) -> new SpikerBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.SPIKER_DRILL,
                (spawn, registry) -> new SpikerDrillObjectInstance(
                        spawn, spawn.x(), spawn.y(),
                        (spawn.renderFlags() & 0x01) != 0,
                        (spawn.renderFlags() & 0x02) != 0));
        registerFactory(Sonic2ObjectIds.SOL,
                (spawn, registry) -> new SolBadnikInstance(spawn));
        // Rexon (lava snake) - both 0x94 and 0x96 point to same implementation
        registerFactory(Sonic2ObjectIds.REXON,
                (spawn, registry) -> new RexonBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.REXON2,
                (spawn, registry) -> new RexonBadnikInstance(spawn));
        // Note: REXON_HEAD (0x97) is spawned dynamically by RexonBadnikInstance

        // CNZ Badniks
        registerFactory(Sonic2ObjectIds.CRAWL,
                (spawn, registry) -> new CrawlBadnikInstance(spawn));

        // MTZ Badniks
        registerFactory(Sonic2ObjectIds.ASTERON,
                (spawn, registry) -> new AsteronBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.SHELLCRACKER,
                (spawn, registry) -> new ShellcrackerBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.SLICER,
                (spawn, registry) -> new SlicerBadnikInstance(spawn));

        // MCZ Badniks
        registerFactory(Sonic2ObjectIds.CRAWLTON,
                (spawn, registry) -> new CrawltonBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.FLASHER,
                (spawn, registry) -> new FlasherBadnikInstance(spawn));

        // SCZ Badniks
        registerFactory(Sonic2ObjectIds.NEBULA,
                (spawn, registry) -> new NebulaBadnikInstance(spawn));
        registerFactory(Sonic2ObjectIds.TURTLOID,
                (spawn, registry) -> new TurtloidBadnikInstance(spawn));
        // Note: TURTLOID_RIDER (0x9B) and Turtloid jet are spawned dynamically by TurtloidBadnikInstance
        registerFactory(Sonic2ObjectIds.BALKIRY,
                (spawn, registry) -> new BalkiryBadnikInstance(spawn));
        // Note: BALKIRY_JET (0x9C) is spawned dynamically by BalkiryBadnikInstance

        // WFZ Badniks
        registerFactory(Sonic2ObjectIds.CLUCKER_BASE,
                (spawn, registry) -> new CluckerBaseObjectInstance(spawn));
        registerFactory(Sonic2ObjectIds.CLUCKER,
                (spawn, registry) -> new CluckerBadnikInstance(spawn));

        // Level completion objects
        registerFactory(Sonic2ObjectIds.SIGNPOST,
                (spawn, registry) -> new SignpostObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.EGG_PRISON,
                (spawn, registry) -> new EggPrisonObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // CNZ Boss (Object 0x51)
        registerFactory(Sonic2ObjectIds.CNZ_BOSS,
                (spawn, registry) -> new Sonic2CNZBossInstance(spawn));
        // HTZ Boss (Object 0x52)
        registerFactory(Sonic2ObjectIds.HTZ_BOSS,
                (spawn, registry) -> new Sonic2HTZBossInstance(spawn));
        // EHZ Boss (Object 0x56)
        registerFactory(Sonic2ObjectIds.EHZ_BOSS,
                (spawn, registry) -> new Sonic2EHZBossInstance(spawn));
        // MCZ Boss (Object 0x57)
        registerFactory(Sonic2ObjectIds.MCZ_BOSS,
                (spawn, registry) -> new Sonic2MCZBossInstance(spawn));
        // CPZ Boss (Object 0x5D)
        registerFactory(Sonic2ObjectIds.CPZ_BOSS,
                (spawn, registry) -> new Sonic2CPZBossInstance(spawn));
        // ARZ Boss (Object 0x89)
        registerFactory(Sonic2ObjectIds.ARZ_BOSS,
                (spawn, registry) -> new Sonic2ARZBossInstance(spawn));
        // DEZ Mecha Sonic / Silver Sonic (Object 0xAF)
        registerFactory(Sonic2ObjectIds.MECHA_SONIC,
                (spawn, registry) -> new Sonic2MechaSonicInstance(spawn));
        // WFZ Boss (Object 0xC5) - Laser Platform Boss
        registerFactory(Sonic2ObjectIds.WFZ_BOSS,
                (spawn, registry) -> new Sonic2WFZBossInstance(spawn));
        // DEZ Eggman transition object (Object 0xC6)
        registerFactory(Sonic2ObjectIds.DEZ_EGGMAN,
                (spawn, registry) -> new Sonic2DEZEggmanInstance(spawn));
        // DEZ Death Egg Robot (Object 0xC7) - Final Boss
        registerFactory(Sonic2ObjectIds.DEATH_EGG_ROBOT,
                (spawn, registry) -> new Sonic2DeathEggRobotInstance(spawn));
        // Boss Explosion (Object 0x58)
        registerFactory(Sonic2ObjectIds.BOSS_EXPLOSION,
                (spawn, registry) -> new BossExplosionObjectInstance(
                        spawn.x(),
                        spawn.y(),
                        Sonic2Sfx.BOSS_EXPLOSION.id));

        // SwingingPlatform (Object 0x15) - chain-suspended platform in OOZ, ARZ, MCZ
        registerFactory(Sonic2ObjectIds.SWINGING_PLATFORM,
                (spawn, registry) -> new SwingingPlatformObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        registerFactory(Sonic2ObjectIds.GENERIC_PLATFORM_A,
                (spawn, registry) -> new ARZPlatformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.GENERIC_PLATFORM_B,
                (spawn, registry) -> new CPZPlatformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ Twin Stompers (Obj64) - crushing piston pair
        registerFactory(Sonic2ObjectIds.MTZ_TWIN_STOMPERS,
                (spawn, registry) -> new MTZTwinStompersObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // Button (Obj47) - trigger button that activates other objects via ButtonVine_Trigger
        registerFactory(Sonic2ObjectIds.BUTTON,
                (spawn, registry) -> new ButtonObjectInstance(spawn));

        // MTZ Spin Tube (Obj67) - tube transport with sinusoidal entry
        registerFactory(Sonic2ObjectIds.MTZ_SPIN_TUBE,
                (spawn, registry) -> new MTZSpinTubeObjectInstance(spawn));

        // MTZ spring wall - invisible solid wall that bounces player
        registerFactory(Sonic2ObjectIds.MTZ_SPRING_WALL,
                (spawn, registry) -> new MTZSpringWallObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ Nut - screw nut that moves vertically when player pushes it
        registerFactory(Sonic2ObjectIds.NUT,
                (spawn, registry) -> new NutObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ Long Platform (Obj65) - long moving platform with cog child
        registerFactory(Sonic2ObjectIds.MTZ_LONG_PLATFORM,
                (spawn, registry) -> {
                    // Properties index 2 = standalone cog (routine 6 in disassembly)
                    int propsIndex = ((spawn.subtype() >> 2) & 0x1C) >> 2;
                    if (propsIndex == 2) {
                        return new MTZLongPlatformCogInstance(spawn);
                    }
                    return new MTZLongPlatformObjectInstance(spawn);
                });

        // MTZ SpikyBlock (Obj68) - block with rotating spike from MTZ
        registerFactory(Sonic2ObjectIds.SPIKY_BLOCK,
                (spawn, registry) -> new SpikyBlockObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ SteamSpring (Obj42) - steam-powered spring piston from MTZ
        registerFactory(Sonic2ObjectIds.STEAM_SPRING,
                (spawn, registry) -> new SteamSpringObjectInstance(spawn));

        // MTZ Floor Spike (Obj6D) - retractable spike from MTZ
        registerFactory(Sonic2ObjectIds.FLOOR_SPIKE,
                (spawn, registry) -> new FloorSpikeObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ LargeRotPform (Obj6E) - large rotating platform moving in circle
        registerFactory(Sonic2ObjectIds.LARGE_ROT_PFORM,
                (spawn, registry) -> new LargeRotPformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ Cog (Obj70) - giant rotating cog with 8 solid teeth
        registerFactory(Sonic2ObjectIds.COG,
                (spawn, registry) -> new CogObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ/CPZ multi-purpose platform with 12 movement subtypes
        registerFactory(Sonic2ObjectIds.MTZ_PLATFORM,
                (spawn, registry) -> new MTZPlatformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ Conveyor (Obj6C) - small platform on pulleys
        // Subtype bit 7 set = parent spawner (creates children), bit 7 clear = individual platform
        registerFactory(Sonic2ObjectIds.CONVEYOR,
                (spawn, registry) -> ConveyorObjectInstance.createOrSpawnChildren(spawn));

        // MTZ Lava Bubble (Obj71) - animated lava bubble scenery
        registerFactory(Sonic2ObjectIds.MTZ_LAVA_BUBBLE,
                (spawn, registry) -> new MTZLavaBubbleObjectInstance(spawn));

        // CPZ/MCZ horizontal moving platform
        registerFactory(Sonic2ObjectIds.SIDEWAYS_PFORM,
                (spawn, registry) -> new SidewaysPformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        registerFactory(Sonic2ObjectIds.INVISIBLE_BLOCK,
                (spawn, registry) -> new InvisibleBlockObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // ARZ Objects
        registerFactory(Sonic2ObjectIds.FALLING_PILLAR,
                (spawn, registry) -> new FallingPillarObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.RISING_PILLAR,
                (spawn, registry) -> new RisingPillarObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.ARROW_SHOOTER,
                (spawn, registry) -> new ArrowShooterObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // OOZ/MCZ/ARZ Collapsing Platform
        registerFactory(Sonic2ObjectIds.COLLAPSING_PLATFORM,
                (spawn, registry) -> new CollapsingPlatformObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // ARZ Swinging Platform
        registerFactory(Sonic2ObjectIds.SWINGING_PFORM,
                (spawn, registry) -> new SwingingPformObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ/MTZ Rotating Platforms (moving wooden crates)
        registerFactory(Sonic2ObjectIds.MCZ_ROT_PFORMS,
                (spawn, registry) -> new MCZRotPformsObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // ARZ Rotating Platforms
        registerFactory(Sonic2ObjectIds.ARZ_ROT_PFORMS,
                (spawn, registry) -> new ARZRotPformsObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // HTZ/MTZ Lava Marker (invisible hazard collision zone)
        registerFactory(Sonic2ObjectIds.LAVA_MARKER,
                (spawn, registry) -> new LavaMarkerObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // HTZ Rising Lava (invisible solid platform during earthquake)
        registerFactory(Sonic2ObjectIds.RISING_LAVA,
                (spawn, registry) -> new RisingLavaObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // HTZ Seesaw (Object 0x14)
        registerFactory(Sonic2ObjectIds.SEESAW,
                (spawn, registry) -> new SeesawObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // HTZ Zipline Lift (Object 0x16)
        registerFactory(Sonic2ObjectIds.HTZ_LIFT,
                (spawn, registry) -> new HTZLiftObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // HTZ Smashable Ground (Object 0x2F) - breakable rock platform
        registerFactory(Sonic2ObjectIds.SMASHABLE_GROUND,
                (spawn, registry) -> new SmashableGroundObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ Brick / Spike Ball (Object 0x75)
        registerFactory(Sonic2ObjectIds.MCZ_BRICK,
                (spawn, registry) -> new MCZBrickObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ Sliding Spikes (Object 0x76) - spike block that slides out of wall
        registerFactory(Sonic2ObjectIds.SLIDING_SPIKES,
                (spawn, registry) -> new SlidingSpikesObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ Stomper (Object 0x2A) - ceiling crusher
        registerFactory(Sonic2ObjectIds.STOMPER,
                (spawn, registry) -> new StomperObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ VineSwitch (Object 0x7F) - pull switch that triggers ButtonVine
        registerFactory(Sonic2ObjectIds.VINE_SWITCH,
                (spawn, registry) -> new VineSwitchObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ/WFZ MovingVine (Object 0x80) - vine pulley or hook on chain
        registerFactory(Sonic2ObjectIds.MOVING_VINE,
                (spawn, registry) -> new MovingVineObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ Bridge (Object 0x77) - horizontal gate triggered by ButtonVine
        registerFactory(Sonic2ObjectIds.MCZ_BRIDGE,
                (spawn, registry) -> new MCZBridgeObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ Drawbridge (Object 0x81) - rotatable drawbridge triggered by ButtonVine
        registerFactory(Sonic2ObjectIds.MCZ_DRAWBRIDGE,
                (spawn, registry) -> new MCZDrawbridgeObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // SCZ Cloud (ObjB3) - decorative scrolling clouds
        registerFactory(Sonic2ObjectIds.TORNADO,
                (spawn, registry) -> new TornadoObjectInstance(spawn));
        registerFactory(Sonic2ObjectIds.CLOUD,
                (spawn, registry) -> new CloudObjectInstance(spawn));

        // WFZ/SCZ Vertical Propeller (ObjB4) - animated spinning blade hazard
        registerFactory(Sonic2ObjectIds.VPROPELLER,
                (spawn, registry) -> new VPropellerObjectInstance(spawn));

        // WFZ/SCZ Horizontal Propeller (ObjB5) - horizontal spinning blade with upward push
        registerFactory(Sonic2ObjectIds.HPROPELLER,
                (spawn, registry) -> new HPropellerObjectInstance(spawn));

        // WFZ Palette Switcher (Obj8B) - cycling palette switcher
        registerFactory(Sonic2ObjectIds.WFZ_PAL_SWITCHER,
                (spawn, registry) -> new WFZPalSwitcherObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // WFZ Laser (ObjB9) - horizontal laser beam that fires leftward
        registerFactory(Sonic2ObjectIds.LASER,
                (spawn, registry) -> new LaserObjectInstance(spawn));

        // WFZ LateralCannon (ObjBE) - retracting platform
        registerFactory(Sonic2ObjectIds.LATERAL_CANNON,
                (spawn, registry) -> new LateralCannonObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // WFZ Stick (ObjBF) - unused rotating stick badnik in WFZ debug list
        registerFactory(Sonic2ObjectIds.WFZ_STICK,
                (spawn, registry) -> new WFZStickBadnikInstance(spawn));

        // WFZ Unknown (ObjBB) - removed unused object retained in the ROM pointer table
        registerFactory(Sonic2ObjectIds.WFZ_UNKNOWN,
                (spawn, registry) -> new WFZUnknownBadnikInstance(spawn));

        // WFZ WallTurret (ObjB8) - wall-mounted turret that shoots projectiles
        registerFactory(Sonic2ObjectIds.WALL_TURRET,
                (spawn, registry) -> new WallTurretObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // WFZ VerticalLaser (ObjB7) - unused huge vertical laser, also spawned by ObjB6
        registerFactory(Sonic2ObjectIds.VERTICAL_LASER,
                (spawn, registry) -> new VerticalLaserObjectInstance(spawn, spawn.x(), spawn.y()));

        // WFZ SpeedLauncher (ObjC0) - catapult platform that launches player
        registerFactory(Sonic2ObjectIds.SPEED_LAUNCHER,
                (spawn, registry) -> new SpeedLauncherObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // WFZ BreakablePlating (ObjC1) - breakable plating / grab point
        registerFactory(Sonic2ObjectIds.BREAKABLE_PLATING,
                (spawn, registry) -> new BreakablePlatingObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // WFZ Rivet (ObjC2) - rivet at end of WFZ that opens ship when busted
        registerFactory(Sonic2ObjectIds.RIVET,
                (spawn, registry) -> new RivetObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // WFZ Tornado smoke (ObjC3/ObjC4) - ObjC4 points to the same ROM routine
        registerFactory(Sonic2ObjectIds.TORNADO_SMOKE,
                (spawn, registry) -> new TornadoSmokeObjectInstance(spawn));
        registerFactory(Sonic2ObjectIds.TORNADO_SMOKE_2,
                (spawn, registry) -> new TornadoSmokeObjectInstance(spawn));

        // WFZ SmallMetalPform (ObjBD) - ascending/descending belt platform spawner
        registerFactory(Sonic2ObjectIds.SMALL_METAL_PFORM,
                (spawn, registry) -> new SmallMetalPformObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // WFZ TiltingPlatform (ObjB6) - tilting/spinning platform with 4 behavior types
        registerFactory(Sonic2ObjectIds.TILTING_PLATFORM,
                (spawn, registry) -> new TiltingPlatformObjectInstance(spawn));

        // WFZ ShipFire (ObjBC) - flame from Robotnik's ship, flickers and tracks BG scroll
        registerFactory(Sonic2ObjectIds.WFZ_SHIP_FIRE,
                (spawn, registry) -> new WFZShipFireObjectInstance(spawn));

        // WFZ Wheel (ObjBA) - static conveyor belt wheel decoration
        registerFactory(Sonic2ObjectIds.WFZ_WHEEL,
                (spawn, registry) -> new WFZWheelObjectInstance(spawn));

        // WFZ Grab (ObjD9) - invisible hang-on point
        registerFactory(Sonic2ObjectIds.GRAB,
                (spawn, registry) -> new GrabObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));
    }
}

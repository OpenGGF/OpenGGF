package com.openggf.game.sonic1.objects;

import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.Sonic1Level;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.objects.badniks.Sonic1BallHogBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BombBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BombFuseInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BurrobotBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BuzzBomberBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1CaterkillerBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1CaterkillerBodyInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1ChopperBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1CrabmeatBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1JawsBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1MotobugBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BatbrainBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1NewtronBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1OrbinautBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1RollerBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1YadrinBadnikInstance;
import com.openggf.game.sonic1.objects.bosses.FZCylinder;
import com.openggf.game.sonic1.objects.bosses.FZPlasmaBall;
import com.openggf.game.sonic1.objects.bosses.FZPlasmaLauncher;
import com.openggf.game.sonic1.objects.bosses.GHZBossWreckingBall;
import com.openggf.game.sonic1.objects.bosses.SYZBossSpike;
import com.openggf.game.sonic1.objects.bosses.Sonic1BossBlockInstance;
import com.openggf.game.sonic1.objects.bosses.Sonic1BossFireInstance;
import com.openggf.game.sonic1.objects.bosses.Sonic1GHZBossInstance;
import com.openggf.game.sonic1.objects.bosses.Sonic1SLZBossSpikeball;
import com.openggf.game.sonic1.objects.bosses.Sonic1LZBossInstance;
import com.openggf.game.sonic1.objects.bosses.Sonic1MZBossInstance;
import com.openggf.game.sonic1.objects.bosses.Sonic1SLZBossInstance;
import com.openggf.game.sonic1.objects.bosses.Sonic1SYZBossInstance;
import com.openggf.game.sonic1.objects.bosses.Sonic1FZBossInstance;
import com.openggf.game.sonic1.objects.bosses.Sonic1FalseFloorInstance;
import com.openggf.game.sonic1.objects.bosses.Sonic1ScrapEggmanInstance;
import com.openggf.level.objects.boss.BossExplosionObjectInstance;
import com.openggf.level.objects.AbstractObjectRegistry;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Object registry for Sonic the Hedgehog 1.
 * Uses factory-based registration following the Sonic 2 pattern.
 */
public class Sonic1ObjectRegistry extends AbstractObjectRegistry {

    private Map<ObjectSpawn, List<RingSpawn>> ringSpawnMapping = Map.of();

    private static final List<DynamicObjectRewindCodec> DYNAMIC_REWIND_CODECS = List.of(
            bombFuseChildCodec(),
            // Sonic1BuzzBomberMissileInstance and
            // Sonic1BuzzBomberMissileDissolveInstance restore through
            // RewindRecreatable generic recreate; scalar state is reapplied
            // after construction.
            caterkillerBodyCodec(),
            // Sonic1CrabmeatProjectileInstance restores through
            // RewindRecreatable generic recreate; scalar state is reapplied
            // after construction.
            ghzBossWreckingBallCodec(),
            slzBossSpikeballCodec(),
            // NOTE: syzBossSpikeCodec intentionally REMOVED.
            // SYZBossSpike is construction-spawned: Sonic1SYZBossInstance.initializeBossState()
            // calls spawnSpikeChild() which calls spawnFreeChild(). Re-adding a codec would
            // double it on rewind restore (1 → 2). The restore adopts the reconstructed child
            // in place at its exact captured state (ObjectManager step-4 reconciliation).
            // See docs/KNOWN_DISCREPANCIES.md and TestBossChildNoDoubleSpawnParity.
            fzCylinderCodec(),
            fzPlasmaLauncherCodec(),
            fzPlasmaBallCodec(),
            bossBlockCodec(),
            collapsingFloorCodec(),
            falseFloorBlockCodec(),
            orbSpikeCodec(),
            // NOTE: scrapEggmanButtonCodec intentionally REMOVED.
            // ScrapEggmanButton is construction-spawned: Sonic1ScrapEggmanInstance
            // constructor calls spawnDynamicObject(button). Re-adding a codec would
            // double it on rewind restore (1 → 2). The restore adopts the reconstructed
            // child in place at its exact captured state (ObjectManager step-4 reconciliation).
            // See docs/KNOWN_DISCREPANCIES.md and TestBossChildNoDoubleSpawnParity.
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    Sonic1EggPrisonObjectInstance.class,
                    spawn -> new Sonic1EggPrisonObjectInstance(spawn)),
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    Sonic1EndingEmeraldsObjectInstance.class,
                    spawn -> new Sonic1EndingEmeraldsObjectInstance(
                            spawn.x(), spawn.y(), 0, 0)),
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    Sonic1EndingSonicObjectInstance.class,
                    spawn -> new Sonic1EndingSonicObjectInstance(spawn.x(), spawn.y())),
            glassReflectionCodec(),
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    Sonic1ResultsScreenObjectInstance.class,
                    spawn -> new Sonic1ResultsScreenObjectInstance(0, 0, 0)),
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    Sonic1ExplosionItemObjectInstance.class,
                    spawn -> new Sonic1ExplosionItemObjectInstance(spawn.x(), spawn.y(), null, 0)),
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    Sonic1FloatingBlockObjectInstance.class,
                    (spawn, os) -> new Sonic1FloatingBlockObjectInstance(
                            spawn, os != null ? os.romZoneId() : -1)),
            grassFireChildCodec(),
            lamppostTwirlCodec(),
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    Sonic1MonitorPowerUpObjectInstance.class,
                    spawn -> new Sonic1MonitorPowerUpObjectInstance(
                            spawn.x(), spawn.y(), spawn.subtype(), null)),
            ringFlashCodec(),
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    Sonic1RingInstance.class,
                    spawn -> new Sonic1RingInstance(
                            spawn,
                            new RingSpawn(spawn.x(), spawn.y()),
                            spawn.x())),
            seesawBallCodec(),
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    Sonic1SpikedBallChainObjectInstance.class,
                    (spawn, os) -> {
                        int zoneIndex = (os != null) ? os.romZoneId() : -1;
                        return new Sonic1SpikedBallChainObjectInstance(spawn, zoneIndex);
                    }),
            // Sonic1SplashObjectInstance (LZ water splash, object 0x08) is accept-drop:
            // a sub-1-second purely-cosmetic splash re-emitted naturally on water
            // entry/exit. See docs/KNOWN_DISCREPANCIES.md "Batch-3 Rewind: Transient
            // Cosmetic Children Not Rewound".
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    Sonic1StomperDoorObjectInstance.class,
                    (spawn, os) -> {
                        int zoneIndex = (os != null) ? os.romZoneId() : -1;
                        return new Sonic1StomperDoorObjectInstance(spawn, zoneIndex);
                    }),
            // Sonic1TeleporterObjectInstance codec deleted (Phase-2 batch 5):
            // now implements RewindRecreatable -> genericRecreate Path 1.
            // Batch-7 S1 rewind codec (shared boss-defeat explosion).
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    BossExplosionObjectInstance.class,
                    s -> new BossExplosionObjectInstance(s.x(), s.y(), Sonic1Sfx.BOSS_EXPLOSION.id)));

    private static DynamicObjectRewindCodec bombFuseChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic1BombFuseInstance.class;
            }

            @Override
            public String className() {
                return Sonic1BombFuseInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                ObjectSpawn spawn = entry.spawn();
                Sonic1BombBadnikInstance parent =
                        findLiveBombParentForRewind(context, spawn.x(), spawn.y());
                if (parent == null) {
                    return null;
                }
                // Non-final scalars (facingLeft, ceilingBomb, fuseTime/timer, fuseYSpeed)
                // are reapplied by restoreObjectRewindState; pass placeholders here.
                // origY is final but equals spawn.y() (its ctor-time value).
                return new Sonic1BombFuseInstance(
                        spawn.x(), spawn.y(), false, false, 0, 0, parent);
            }
        };
    }

    private static Sonic1BombBadnikInstance findLiveBombParentForRewind(
            DynamicObjectRecreateContext context, int x, int y) {
        Sonic1BombBadnikInstance best = null;
        long bestDist = Long.MAX_VALUE;
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (inst instanceof Sonic1BombBadnikInstance bomb) {
                long dx = bomb.getX() - x;
                long dy = bomb.getY() - y;
                long dist = dx * dx + dy * dy;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = bomb;
                }
            }
        }
        return best;
    }

    private static DynamicObjectRewindCodec glassReflectionCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic1GlassReflectionInstance.class;
            }

            @Override
            public String className() {
                return Sonic1GlassReflectionInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                ObjectSpawn spawn = entry.spawn();
                // Parent glass block is a layout (active) object recreated earlier
                // in the restore loop, so it is live in getActiveObjects(). Match by
                // nearest spawn position (multiple glass blocks may exist).
                Sonic1GlassBlockObjectInstance parent =
                        findLiveGlassBlockParentForRewind(context, spawn.x(), spawn.y());
                if (parent == null) {
                    return null;
                }
                // reflectSubtype / isTall are derived from the parent's spawn (the
                // child receives the SAME spawn as its parent), so recompute them
                // rather than un-finaling. Matches Sonic1GlassBlockObjectInstance:
                //   fullSubtype = spawn.subtype() & 0xFF
                //   isTall      = subtype < 3
                //   reflectSubtype = (fullSubtype + 8) & 0x0F   (addq.b #8 / andi.b #$F)
                int fullSubtype = spawn.subtype() & 0xFF;
                int reflectSubtype = (fullSubtype + 8) & 0x0F;
                boolean isTall = fullSubtype < 3;
                // x / y / baseY / glassDist are non-final and reapplied by
                // restoreObjectRewindState after recreate.
                return new Sonic1GlassReflectionInstance(spawn, parent, reflectSubtype, isTall);
            }
        };
    }

    private static Sonic1GlassBlockObjectInstance findLiveGlassBlockParentForRewind(
            DynamicObjectRecreateContext context, int x, int y) {
        Sonic1GlassBlockObjectInstance best = null;
        long bestDist = Long.MAX_VALUE;
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (inst instanceof Sonic1GlassBlockObjectInstance block) {
                // Block x is stable (= spawn.x()); compare baseY for the y key.
                long dx = block.getX() - x;
                long dy = block.getBaseY() - y;
                long dist = dx * dx + dy * dy;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = block;
                }
            }
        }
        return best;
    }

    private static DynamicObjectRewindCodec caterkillerBodyCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic1CaterkillerBodyInstance.class;
            }

            @Override
            public String className() {
                return Sonic1CaterkillerBodyInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // The head is a layout-spawned badnik recreated earlier in the
                // restore loop, so it is live in getActiveObjects(). It serves as
                // both the parent-chain root (head) and the immediate parentState
                // (the head implements CaterkillerParentState). The per-segment
                // movement/animation scalars are non-final and reapplied by the
                // generic field capturer after recreate. isAnimatedSegment and
                // fragSpeedIndex are passed as placeholders; they were un-finaled so
                // the generic capturer reapplies their captured values.
                Sonic1CaterkillerBadnikInstance head = null;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1CaterkillerBadnikInstance h) {
                        head = h;
                        break;
                    }
                }
                if (head == null) {
                    return null;
                }
                ObjectSpawn spawn = entry.spawn();
                return new Sonic1CaterkillerBodyInstance(
                        head, head, spawn.x(), spawn.y(),
                        false, false, 0, 0);
            }
        };
    }

    private static DynamicObjectRewindCodec ghzBossWreckingBallCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == GHZBossWreckingBall.class;
            }

            @Override
            public String className() {
                return GHZBossWreckingBall.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic1GHZBossInstance parent = null;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1GHZBossInstance boss) {
                        parent = boss;
                        break;
                    }
                }
                if (parent == null) {
                    return null;
                }
                return new GHZBossWreckingBall(parent);
            }
        };
    }

    private static DynamicObjectRewindCodec slzBossSpikeballCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic1SLZBossSpikeball.class;
            }

            @Override
            public String className() {
                return Sonic1SLZBossSpikeball.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic1SLZBossInstance boss = null;
                Sonic1SeesawObjectInstance seesaw = null;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (boss == null && inst instanceof Sonic1SLZBossInstance b) {
                        boss = b;
                    } else if (seesaw == null && inst instanceof Sonic1SeesawObjectInstance s) {
                        seesaw = s;
                    }
                }
                if (boss == null || seesaw == null) {
                    return null;
                }
                ObjectSpawn spawn = entry.spawn();
                return new Sonic1SLZBossSpikeball(boss, seesaw, spawn.x(), spawn.y());
            }
        };
    }

    private static DynamicObjectRewindCodec syzBossSpikeCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == SYZBossSpike.class;
            }

            @Override
            public String className() {
                return SYZBossSpike.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic1SYZBossInstance boss = null;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1SYZBossInstance b) {
                        boss = b;
                        break;
                    }
                }
                if (boss == null) {
                    return null;
                }
                return new SYZBossSpike(boss);
            }
        };
    }

    private static DynamicObjectRewindCodec fzCylinderCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == FZCylinder.class;
            }

            @Override
            public String className() {
                return FZCylinder.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic1FZBossInstance boss = null;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1FZBossInstance b) {
                        boss = b;
                        break;
                    }
                }
                if (boss == null) {
                    return null;
                }
                // subtype (and derived isBottom/baseX/baseY) un-finaled so the generic
                // capturer reapplies the captured per-cylinder values; ctor gets a
                // placeholder subtype.
                return new FZCylinder(boss, 0);
            }
        };
    }

    private static DynamicObjectRewindCodec fzPlasmaLauncherCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == FZPlasmaLauncher.class;
            }

            @Override
            public String className() {
                return FZPlasmaLauncher.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic1FZBossInstance parent = null;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1FZBossInstance fz) {
                        parent = fz;
                        break;
                    }
                }
                if (parent == null) {
                    return null; // parent not yet restored -> drop
                }
                return new FZPlasmaLauncher(parent);
            }
        };
    }

    private static DynamicObjectRewindCodec fzPlasmaBallCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == FZPlasmaBall.class;
            }

            @Override
            public String className() {
                return FZPlasmaBall.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                FZPlasmaLauncher launcher = null;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof FZPlasmaLauncher l) {
                        launcher = l;
                        break;
                    }
                }
                if (launcher == null) {
                    return null;
                }
                ObjectSpawn spawn = entry.spawn();
                // startX/startY from spawn; targetX is a non-final scalar reapplied by
                // restoreObjectRewindState, so a placeholder 0 is fine.
                return new FZPlasmaBall(launcher, spawn.x(), spawn.y(), 0);
            }
        };
    }

    private static DynamicObjectRewindCodec bossBlockCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic1BossBlockInstance.class;
            }

            @Override
            public String className() {
                return Sonic1BossBlockInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                ObjectSpawn spawn = entry.spawn();
                // Recreate via the public column ctor. blockColumn is un-finaled so the
                // generic field capturer reapplies its captured value (-1 for fragments,
                // 0-9 for blocks); spawn.subtype() is a placeholder for the block form.
                Sonic1BossBlockInstance block =
                        new Sonic1BossBlockInstance(spawn.subtype());
                // Relink the grabbingBoss object reference to the live SYZ boss.
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1SYZBossInstance boss) {
                        block.relinkGrabbingBoss(boss);
                        break;
                    }
                }
                return block;
            }
        };
    }

    private static DynamicObjectRewindCodec collapsingFloorCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic1CollapsingFloorObjectInstance.class;
            }

            @Override
            public String className() {
                return Sonic1CollapsingFloorObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // Conceptually exactSpawnCodec: recreate purely from spawn + live zone
                // (the same ROM zone id the production factory uses).
                int zoneIndex = context.objectServices().romZoneId();
                return new Sonic1CollapsingFloorObjectInstance(entry.spawn(), zoneIndex);
            }
        };
    }

    private static final String FALSE_FLOOR_BLOCK_CLASS =
            "com.openggf.game.sonic1.objects.bosses.Sonic1FalseFloorInstance$FalseFloorBlock";

    private static DynamicObjectRewindCodec falseFloorBlockCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(FALSE_FLOOR_BLOCK_CLASS);
            }

            @Override
            public String className() {
                return FALSE_FLOOR_BLOCK_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // Relink the live restored master FalseFloor (persistent/layout-spawned,
                // isPersistent()==true, so live in getActiveObjects() at restore time).
                // currentX/currentY/blockIndex were un-finaled and passed as placeholders
                // (0,0,0); the generic field capturer reapplies their captured values,
                // and broken/goSignal are already non-final captured scalars.
                Sonic1FalseFloorInstance master = null;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1FalseFloorInstance ff) {
                        master = ff;
                        break;
                    }
                }
                if (master == null) {
                    return null;
                }
                try {
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(int.class, int.class, int.class);
                    ctor.setAccessible(true);
                    ObjectInstance block = (ObjectInstance) ctor.newInstance(0, 0, 0);
                    // Re-register into the master's childBlocks so the disintegration
                    // sequence (childBlocks.get(currentFrame).signalBreak()) still resolves.
                    master.reattachChildBlock((Sonic1FalseFloorInstance.FalseFloorBlock) block);
                    return block;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static final String ORB_SPIKE_CLASS =
            "com.openggf.game.sonic1.objects.badniks.Sonic1OrbinautBadnikInstance"
                    + "$OrbSpikeObjectInstance";

    private static DynamicObjectRewindCodec orbSpikeCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(ORB_SPIKE_CLASS);
            }

            @Override
            public String className() {
                return ORB_SPIKE_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // Relink the live Orbinaut parent (a layout/active badnik recreated
                // earlier in the restore loop). The OrbSpikeObjectInstance is a private
                // static nested class, so it is constructed by reflection on the
                // (Sonic1OrbinautBadnikInstance, int) ctor with a placeholder startAngle.
                // All live state (x, y, angle, launched, xVelocity) is non-final and
                // reapplied by restoreObjectRewindState; the parent ref stays final
                // (relinked here); motion is reset from x/xVel each step.
                Sonic1OrbinautBadnikInstance parent = null;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1OrbinautBadnikInstance o) {
                        parent = o;
                        break;
                    }
                }
                if (parent == null) {
                    return null;
                }
                try {
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            Sonic1OrbinautBadnikInstance.class, int.class);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(parent, 0);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec scrapEggmanButtonCodec() {
        final String CLASS_NAME =
                "com.openggf.game.sonic1.objects.bosses.Sonic1ScrapEggmanInstance$ScrapEggmanButton";
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(CLASS_NAME);
            }

            @Override
            public String className() {
                return CLASS_NAME;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                try {
                    // Relink the live parent by scanning active objects.
                    Sonic1ScrapEggmanInstance parent = null;
                    for (ObjectInstance obj : context.objectManager().getActiveObjects()) {
                        if (obj instanceof Sonic1ScrapEggmanInstance segg) {
                            parent = segg;
                            break;
                        }
                    }
                    if (parent == null) {
                        return null;
                    }
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            ObjectSpawn.class, Sonic1ScrapEggmanInstance.class);
                    ctor.setAccessible(true);
                    // buttonPhase/buttonFrame are restored by GenericFieldCapturer
                    // after recreate; buttonX/buttonY are spawn-derived.
                    return (ObjectInstance) ctor.newInstance(entry.spawn(), parent);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec grassFireChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic1GrassFireObjectInstance.class;
            }

            @Override
            public String className() {
                return Sonic1GrassFireObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // Parent platform is a layout-spawned (active) object, recreated before
                // its dynamic fire children, so it is live in getActiveObjects().
                Sonic1LargeGrassyPlatformObjectInstance parent = null;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1LargeGrassyPlatformObjectInstance p) {
                        parent = p;
                        break;
                    }
                }
                if (parent == null) {
                    return null;
                }
                ObjectSpawn spawn = entry.spawn();
                // isWalker is spawn-derivable (subtype 0 = walker, 1 = stationary).
                boolean isWalker = spawn.subtype() == 0;
                // baseY/originX were un-finaled so the generic capturer reapplies the
                // captured values; pass placeholder sinkOffset 0.
                return new Sonic1GrassFireObjectInstance(
                        spawn.x(), spawn.y(), 0, parent.getSlopeData(), parent, isWalker);
            }
        };
    }

    private static DynamicObjectRewindCodec lamppostTwirlCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic1LamppostTwirlInstance.class;
            }

            @Override
            public String className() {
                return Sonic1LamppostTwirlInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // The lamppost is a placed/layout-spawned object, recreated earlier in
                // the restore loop. Pick the nearest one to the twirl's spawn position.
                ObjectSpawn spawn = entry.spawn();
                Sonic1LamppostObjectInstance parent = null;
                long bestDist = Long.MAX_VALUE;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1LamppostObjectInstance lamp) {
                        long dx = lamp.getCenterX() - spawn.x();
                        long dy = lamp.getCenterY() - spawn.y();
                        long dist = dx * dx + dy * dy;
                        if (dist < bestDist) {
                            bestDist = dist;
                            parent = lamp;
                        }
                    }
                }
                if (parent == null) {
                    return null;
                }
                return new Sonic1LamppostTwirlInstance(parent);
            }
        };
    }

    private static DynamicObjectRewindCodec ringFlashCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic1RingFlashObjectInstance.class;
            }

            @Override
            public String className() {
                return Sonic1RingFlashObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                ObjectSpawn spawn = entry.spawn();
                Sonic1GiantRingObjectInstance parent = null;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1GiantRingObjectInstance ring) {
                        parent = ring;
                        break;
                    }
                }
                // hFlip placeholder (false); the live capturer reapplies the real value
                // after recreate since hFlip is un-finaled. Parent may be null if the
                // ring was already deleted at frame 3 (ctor only dereferences it once,
                // gated by !triggerFired).
                return new Sonic1RingFlashObjectInstance(parent, spawn.x(), spawn.y(), false);
            }
        };
    }

    private static DynamicObjectRewindCodec seesawBallCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic1SeesawBallObjectInstance.class;
            }

            @Override
            public String className() {
                return Sonic1SeesawBallObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // The seesaw (object 0x5E) is a layout-spawned active object recreated
                // earlier in the restore loop. Find the nearest one to the ball's
                // captured position. origX/origY are un-finaled and reapplied by the
                // generic field capturer; flipped passed here is a placeholder.
                ObjectSpawn spawn = entry.spawn();
                Sonic1SeesawObjectInstance parent = null;
                long bestDist = Long.MAX_VALUE;
                for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
                    if (inst instanceof Sonic1SeesawObjectInstance s) {
                        long dx = (long) s.getX() - spawn.x();
                        long dy = (long) s.getY() - spawn.y();
                        long dist = dx * dx + dy * dy;
                        if (dist < bestDist) {
                            bestDist = dist;
                            parent = s;
                        }
                    }
                }
                if (parent == null) {
                    return null;
                }
                return new Sonic1SeesawBallObjectInstance(parent, spawn.x(), spawn.y(), false);
            }
        };
    }

    public void setRingSpawnMapping(Map<ObjectSpawn, List<RingSpawn>> mapping) {
        this.ringSpawnMapping = mapping != null ? mapping : Map.of();
    }

    /**
     * Returns the ring spawn mapping for the current level by reading from
     * {@link Sonic1Level} via {@link #currentLevel()}. Falls back to the locally-set
     * mapping (e.g. for tests that construct the registry without a live level).
     */
    private Map<ObjectSpawn, List<RingSpawn>> currentRingSpawnMapping() {
        if (currentLevel() instanceof Sonic1Level s1Level) {
            return s1Level.getRingSpawnMapping();
        }
        return ringSpawnMapping;
    }

    @Override
    public List<DynamicObjectRewindCodec> dynamicRewindCodecs() {
        return DYNAMIC_REWIND_CODECS;
    }

    @Override
    protected void registerDefaultFactories() {
        // ROM parity: each ring layout entry becomes a real ring object that
        // occupies a slot, spawns children via spawnChild (FindFreeObj equivalent),
        // and manages its own sparkle countdown. Rendering/collection by RingManager.
        factories.put(Sonic1ObjectIds.RING,
                (spawn, registry) -> {
                    List<RingSpawn> ringSpawns = currentRingSpawnMapping().get(spawn);
                    if (ringSpawns == null || ringSpawns.isEmpty()) {
                        // Fallback: single ring at spawn position
                        ringSpawns = List.of(new RingSpawn(spawn.x(), spawn.y()));
                    }
                    return new Sonic1RingInstance(spawn, ringSpawns);
                });
        factories.put(Sonic1ObjectIds.BREAKABLE_POLE,
                (spawn, registry) -> new Sonic1PoleThatBreaksObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.FLAPPING_DOOR,
                (spawn, registry) -> new Sonic1FlappingDoorObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SPINNING_LIGHT,
                (spawn, registry) -> new Sonic1SpinningLightObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LAMPPOST,
                (spawn, registry) -> new Sonic1LamppostObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SIGNPOST,
                (spawn, registry) -> new Sonic1SignpostObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.MONITOR,
                (spawn, registry) -> new Sonic1MonitorObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.ANIMALS,
                (spawn, registry) -> new Sonic1AnimalsObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.ROCK,
                (spawn, registry) -> new Sonic1RockObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.BRIDGE,
                (spawn, registry) -> new Sonic1BridgeObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SCENERY,
                (spawn, registry) -> new Sonic1SceneryObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SPIKES,
                (spawn, registry) -> new Sonic1SpikeObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SPRING,
                (spawn, registry) -> new Sonic1SpringObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.GIANT_RING,
                (spawn, registry) -> new Sonic1GiantRingObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.PLATFORM,
                (spawn, registry) -> new Sonic1PlatformObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.COLLAPSING_LEDGE,
                (spawn, registry) -> new Sonic1CollapsingLedgeObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.BREAKABLE_WALL,
                (spawn, registry) -> new Sonic1BreakableWallObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.EDGE_WALLS,
                (spawn, registry) -> new Sonic1EdgeWallObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.CHOPPER,
                (spawn, registry) -> new Sonic1ChopperBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.JAWS,
                (spawn, registry) -> new Sonic1JawsBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.BURROBOT,
                (spawn, registry) -> new Sonic1BurrobotBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.BUZZ_BOMBER,
                (spawn, registry) -> new Sonic1BuzzBomberBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.CRABMEAT,
                (spawn, registry) -> new Sonic1CrabmeatBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.MOTOBUG,
                (spawn, registry) -> new Sonic1MotobugBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.NEWTRON,
                (spawn, registry) -> new Sonic1NewtronBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.CATERKILLER,
                (spawn, registry) -> new Sonic1CaterkillerBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.BATBRAIN,
                (spawn, registry) -> new Sonic1BatbrainBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.YADRIN,
                (spawn, registry) -> new Sonic1YadrinBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.ROLLER,
                (spawn, registry) -> new Sonic1RollerBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.BUMPER,
                (spawn, registry) -> new Sonic1BumperObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.FLOATING_BLOCK,
                (spawn, registry) -> new Sonic1FloatingBlockObjectInstance(spawn,
                        currentRomZoneId()));
        factories.put(Sonic1ObjectIds.HARPOON,
                (spawn, registry) -> new Sonic1HarpoonObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SPIKED_POLE_HELIX,
                (spawn, registry) -> new Sonic1SpikedPoleHelixObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SWINGING_PLATFORM,
                (spawn, registry) -> new Sonic1SwingingPlatformObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.MZ_BRICK,
                (spawn, registry) -> new Sonic1MzBrickObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SMASH_BLOCK,
                (spawn, registry) -> new Sonic1SmashBlockObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.CHAINED_STOMPER,
                (spawn, registry) -> new Sonic1ChainedStomperObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.PUSH_BLOCK,
                (spawn, registry) -> new Sonic1PushBlockObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.BUTTON,
                (spawn, registry) -> new Sonic1ButtonObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.MZ_GLASS_BLOCK,
                (spawn, registry) -> new Sonic1GlassBlockObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LAVA_BALL_MAKER,
                (spawn, registry) -> new Sonic1LavaBallMakerObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LAVA_GEYSER_MAKER,
                (spawn, registry) -> new Sonic1LavaGeyserMakerObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LAVA_GEYSER,
                (spawn, registry) -> new Sonic1LavaGeyserObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LAVA_TAG,
                (spawn, registry) -> new Sonic1LavaTagObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LAVA_WALL,
                (spawn, registry) -> new Sonic1LavaWallObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.MZ_LARGE_GRASSY_PLATFORM,
                (spawn, registry) -> new Sonic1LargeGrassyPlatformObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.BURNING_GRASS,
                (spawn, registry) -> new Sonic1GrassFireObjectInstance(
                        spawn.x(), spawn.y(), 0, null, null, spawn.subtype() == 0));
        factories.put(Sonic1ObjectIds.WATERFALL_SOUND,
                (spawn, registry) -> new Sonic1WaterfallSoundObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.GIANT_RING,
                (spawn, registry) -> new Sonic1GiantRingObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.HIDDEN_BONUS,
                (spawn, registry) -> new Sonic1HiddenBonusObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.COLLAPSING_FLOOR,
                (spawn, registry) -> new Sonic1CollapsingFloorObjectInstance(
                        spawn, currentRomZoneId()));
        factories.put(Sonic1ObjectIds.MOVING_BLOCK,
                (spawn, registry) -> new Sonic1MovingBlockObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SPIKED_BALL_CHAIN,
                (spawn, registry) -> new Sonic1SpikedBallChainObjectInstance(spawn,
                        currentRomZoneId()));
        factories.put(Sonic1ObjectIds.BIG_SPIKED_BALL,
                (spawn, registry) -> new Sonic1BigSpikedBallObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SLZ_ELEVATOR,
                (spawn, registry) -> new Sonic1ElevatorObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SLZ_CIRCLING_PLATFORM,
                (spawn, registry) -> new Sonic1CirclingPlatformObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SLZ_STAIRCASE,
                (spawn, registry) -> new Sonic1StaircaseObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LABYRINTH_BLOCK,
                (spawn, registry) -> new Sonic1LabyrinthBlockObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.GARGOYLE,
                (spawn, registry) -> new Sonic1GargoyleObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LZ_CONVEYOR,
                (spawn, registry) -> new Sonic1LZConveyorObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.BUBBLES,
                (spawn, registry) -> new Sonic1BubblesObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.WATERFALL,
                (spawn, registry) -> new Sonic1WaterfallObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SBZ_SMALL_DOOR,
                (spawn, registry) -> new Sonic1SmallDoorObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SBZ_CONVEYOR_BELT,
                (spawn, registry) -> new Sonic1ConveyorBeltObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SBZ_SPINNING_PLATFORM,
                (spawn, registry) -> new Sonic1SpinPlatformObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SBZ_VANISHING_PLATFORM,
                (spawn, registry) -> new Sonic1VanishingPlatformObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.PYLON,
                (spawn, registry) -> new Sonic1PylonObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.FAN,
                (spawn, registry) -> new Sonic1FanObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SEESAW,
                (spawn, registry) -> new Sonic1SeesawObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.BOMB,
                (spawn, registry) -> new Sonic1BombBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.ORBINAUT,
                (spawn, registry) -> new Sonic1OrbinautBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.FLAMETHROWER,
                (spawn, registry) -> new Sonic1FlamethrowerObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.ELECTROCUTER,
                (spawn, registry) -> new Sonic1ElectrocuterObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SBZ_SPIN_CONVEYOR,
                (spawn, registry) -> new Sonic1SpinConveyorObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SBZ_SAW,
                (spawn, registry) -> new Sonic1SawObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.GIRDER,
                (spawn, registry) -> new Sonic1GirderBlockObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.BALL_HOG,
                (spawn, registry) -> new Sonic1BallHogBadnikInstance(spawn));
        factories.put(Sonic1ObjectIds.SBZ_STOMPER_DOOR,
                (spawn, registry) -> new Sonic1StomperDoorObjectInstance(spawn,
                        currentRomZoneId()));
        factories.put(Sonic1ObjectIds.JUNCTION,
                (spawn, registry) -> new Sonic1JunctionObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.RUNNING_DISC,
                (spawn, registry) -> new Sonic1RunningDiscObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.INVISIBLE_BARRIER,
                (spawn, registry) -> new Sonic1InvisibleBarrierObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.TELEPORTER,
                (spawn, registry) -> new Sonic1TeleporterObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.GHZ_BOSS,
                (spawn, registry) -> new Sonic1GHZBossInstance(spawn));
        factories.put(Sonic1ObjectIds.MZ_BOSS,
                (spawn, registry) -> new Sonic1MZBossInstance(spawn));
        factories.put(Sonic1ObjectIds.SYZ_BOSS,
                (spawn, registry) -> new Sonic1SYZBossInstance(spawn));
        factories.put(Sonic1ObjectIds.LZ_BOSS,
                (spawn, registry) -> new Sonic1LZBossInstance(spawn));
        factories.put(Sonic1ObjectIds.SLZ_BOSS,
                (spawn, registry) -> new Sonic1SLZBossInstance(spawn));
        factories.put(Sonic1ObjectIds.SYZ_BOSS_BLOCK,
                (spawn, registry) -> new Sonic1BossBlockInstance(spawn.subtype()));
        factories.put(Sonic1ObjectIds.BOSS_FIRE,
                (spawn, registry) -> new Sonic1BossFireInstance(spawn));
        factories.put(Sonic1ObjectIds.FZ_BOSS,
                (spawn, registry) -> new Sonic1FZBossInstance(spawn));
        factories.put(Sonic1ObjectIds.END_SONIC,
                (spawn, registry) -> new Sonic1EndingSonicObjectInstance(spawn.x(), spawn.y()));
        factories.put(Sonic1ObjectIds.END_CHAOS,
                (spawn, registry) -> new Sonic1EndingEmeraldsObjectInstance(spawn.x(), spawn.y(), 0, 1));
        factories.put(Sonic1ObjectIds.END_STH,
                (spawn, registry) -> new Sonic1EndingSTHObjectInstance());
        factories.put(Sonic1ObjectIds.EGG_PRISON, (spawn, registry) -> {
            // ROM placement has two entries: subtype 0 (body) and subtype 1 (button).
            // Pri_Main creates sub-objects from Pri_Var; our engine loads each entry separately.
            if (spawn.subtype() == 1) {
                return new Sonic1EggPrisonButtonObjectInstance(spawn);
            }
            return new Sonic1EggPrisonObjectInstance(spawn);
        });
        factories.put(Sonic1ObjectIds.SCRAP_EGGMAN,
                (spawn, registry) -> new Sonic1ScrapEggmanInstance(spawn));
        factories.put(Sonic1ObjectIds.FALSE_FLOOR,
                (spawn, registry) -> new Sonic1FalseFloorInstance(spawn));
    }

    @Override
    public String getPrimaryName(int objectId) {
        return switch (objectId) {
            case Sonic1ObjectIds.SONIC -> "Sonic";
            case Sonic1ObjectIds.BREAKABLE_POLE -> "PoleThatBreaks";
            case Sonic1ObjectIds.FLAPPING_DOOR -> "FlappingDoor";
            case Sonic1ObjectIds.SPINNING_LIGHT -> "SpinningLight";
            case Sonic1ObjectIds.SIGNPOST -> "Signpost";
            case Sonic1ObjectIds.BRIDGE -> "Bridge";
            case Sonic1ObjectIds.HARPOON -> "Harpoon";
            case Sonic1ObjectIds.SPIKED_POLE_HELIX -> "SpikedPoleHelix";
            case Sonic1ObjectIds.SWINGING_PLATFORM -> "SwingingPlatform";
            case Sonic1ObjectIds.PLATFORM -> "Platform";
            case Sonic1ObjectIds.COLLAPSING_LEDGE -> "CollapsingLedge";
            case Sonic1ObjectIds.SCENERY -> "Scenery";
            case Sonic1ObjectIds.BALL_HOG -> "BallHog";
            case Sonic1ObjectIds.CRABMEAT -> "Crabmeat";
            case Sonic1ObjectIds.CANNONBALL -> "Cannonball";
            case Sonic1ObjectIds.BUZZ_BOMBER -> "BuzzBomber";
            case Sonic1ObjectIds.BUZZ_BOMBER_MISSILE -> "BuzzBomberMissile";
            case Sonic1ObjectIds.MISSILE_DISSOLVE -> "MissileDissolve";
            case Sonic1ObjectIds.RING -> "Ring";
            case Sonic1ObjectIds.MONITOR -> "Monitor";
            case Sonic1ObjectIds.POWER_UP -> "PowerUp";
            case Sonic1ObjectIds.EXPLOSION_ITEM -> "ExplosionItem";
            case Sonic1ObjectIds.ANIMALS -> "Animals";
            case Sonic1ObjectIds.CHOPPER -> "Chopper";
            case Sonic1ObjectIds.JAWS -> "Jaws";
            case Sonic1ObjectIds.BURROBOT -> "Burrobot";
            case Sonic1ObjectIds.SPIKES -> "Spikes";
            case Sonic1ObjectIds.ROCK -> "Rock";
            case Sonic1ObjectIds.BREAKABLE_WALL -> "BreakableWall";
            case Sonic1ObjectIds.GHZ_BOSS -> "GHZBoss";
            case Sonic1ObjectIds.MZ_BOSS -> "MZBoss";
            case Sonic1ObjectIds.BOSS_FIRE -> "BossFire";
            case Sonic1ObjectIds.SYZ_BOSS -> "SYZBoss";
            case Sonic1ObjectIds.LZ_BOSS -> "LZBoss";
            case Sonic1ObjectIds.SLZ_BOSS -> "SLZBoss";
            case Sonic1ObjectIds.FZ_BOSS -> "FZBoss";
            case Sonic1ObjectIds.EGGMAN_CYLINDER -> "EggmanCylinder";
            case Sonic1ObjectIds.BOSS_PLASMA -> "BossPlasma";
            case Sonic1ObjectIds.SLZ_BOSS_SPIKEBALL -> "BossSpikeball";
            case Sonic1ObjectIds.SYZ_BOSS_BLOCK -> "BossBlock";
            case Sonic1ObjectIds.EGG_PRISON -> "EggPrison";
            case Sonic1ObjectIds.MOTOBUG -> "Motobug";
            case Sonic1ObjectIds.SPRING -> "Spring";
            case Sonic1ObjectIds.EDGE_WALLS -> "EdgeWalls";
            case Sonic1ObjectIds.MZ_BRICK -> "MzBrick";
            case Sonic1ObjectIds.NEWTRON -> "Newtron";
            case Sonic1ObjectIds.ROLLER -> "Roller";
            case Sonic1ObjectIds.BUMPER -> "Bumper";
            case Sonic1ObjectIds.BOSS_BALL -> "BossBall";
            case Sonic1ObjectIds.WATERFALL_SOUND -> "WaterfallSound";
            case Sonic1ObjectIds.GIANT_RING -> "GiantRing";
            case Sonic1ObjectIds.YADRIN -> "Yadrin";
            case Sonic1ObjectIds.MZ_GLASS_BLOCK -> "MzGlassBlock";
            case Sonic1ObjectIds.SMASH_BLOCK -> "SmashBlock";
            case Sonic1ObjectIds.COLLAPSING_FLOOR -> "CollapsingFloor";
            case Sonic1ObjectIds.MOVING_BLOCK -> "MovingBlock";
            case Sonic1ObjectIds.CHAINED_STOMPER -> "ChainedStomper";
            case Sonic1ObjectIds.PUSH_BLOCK -> "PushBlock";
            case Sonic1ObjectIds.BUTTON -> "Button";
            case Sonic1ObjectIds.LAVA_BALL_MAKER -> "LavaBallMaker";
            case Sonic1ObjectIds.LAVA_BALL -> "LavaBall";
            case Sonic1ObjectIds.LAVA_GEYSER_MAKER -> "LavaGeyserMaker";
            case Sonic1ObjectIds.LAVA_GEYSER -> "LavaGeyser";
            case Sonic1ObjectIds.LAVA_TAG -> "LavaTag";
            case Sonic1ObjectIds.LAVA_WALL -> "LavaWall";
            case Sonic1ObjectIds.MZ_LARGE_GRASSY_PLATFORM -> "MzLargeGrassyPlatform";
            case Sonic1ObjectIds.BURNING_GRASS -> "BurningGrass";
            case Sonic1ObjectIds.BATBRAIN -> "Batbrain";
            case Sonic1ObjectIds.FLOATING_BLOCK -> "FloatingBlock";
            case Sonic1ObjectIds.SPIKED_BALL_CHAIN -> "SpikedBallChain";
            case Sonic1ObjectIds.BIG_SPIKED_BALL -> "BigSpikedBall";
            case Sonic1ObjectIds.SLZ_ELEVATOR -> "Elevator";
            case Sonic1ObjectIds.SLZ_CIRCLING_PLATFORM -> "CirclingPlatform";
            case Sonic1ObjectIds.SLZ_STAIRCASE -> "Staircase";
            case Sonic1ObjectIds.PYLON -> "Pylon";
            case Sonic1ObjectIds.FAN -> "Fan";
            case Sonic1ObjectIds.SEESAW -> "Seesaw";
            case Sonic1ObjectIds.BOMB -> "Bomb";
            case Sonic1ObjectIds.ORBINAUT -> "Orbinaut";
            case Sonic1ObjectIds.LABYRINTH_BLOCK -> "LabyrinthBlock";
            case Sonic1ObjectIds.GARGOYLE -> "Gargoyle";
            case Sonic1ObjectIds.LZ_CONVEYOR -> "LZConveyor";
            case Sonic1ObjectIds.BUBBLES -> "Bubbles";
            case Sonic1ObjectIds.WATERFALL -> "Waterfall";
            case Sonic1ObjectIds.JUNCTION -> "Junction";
            case Sonic1ObjectIds.RUNNING_DISC -> "RunningDisc";
            case Sonic1ObjectIds.SBZ_SMALL_DOOR -> "SmallDoor";
            case Sonic1ObjectIds.SBZ_CONVEYOR_BELT -> "ConveyorBelt";
            case Sonic1ObjectIds.SBZ_SPINNING_PLATFORM -> "SpinPlatform";
            case Sonic1ObjectIds.SBZ_SAW -> "Saw";
            case Sonic1ObjectIds.SBZ_STOMPER_DOOR -> "StomperDoor";
            case Sonic1ObjectIds.SBZ_VANISHING_PLATFORM -> "VanishingPlatform";
            case Sonic1ObjectIds.FLAMETHROWER -> "Flamethrower";
            case Sonic1ObjectIds.ELECTROCUTER -> "Electrocuter";
            case Sonic1ObjectIds.SBZ_SPIN_CONVEYOR -> "SpinConveyor";
            case Sonic1ObjectIds.GIRDER -> "Girder";
            case Sonic1ObjectIds.INVISIBLE_BARRIER -> "InvisibleBarrier";
            case Sonic1ObjectIds.TELEPORTER -> "Teleporter";
            case Sonic1ObjectIds.CATERKILLER -> "Caterkiller";
            case Sonic1ObjectIds.LAMPPOST -> "Lamppost";
            case Sonic1ObjectIds.HIDDEN_BONUS -> "HiddenBonus";
            case Sonic1ObjectIds.END_SONIC -> "EndSonic";
            case Sonic1ObjectIds.END_CHAOS -> "EndChaos";
            case Sonic1ObjectIds.END_STH -> "EndSTH";
            case Sonic1ObjectIds.SCRAP_EGGMAN -> "ScrapEggman";
            case Sonic1ObjectIds.FALSE_FLOOR -> "FalseFloor";
            default -> String.format("S1_Obj_%02X", objectId & 0xFF);
        };
    }
}

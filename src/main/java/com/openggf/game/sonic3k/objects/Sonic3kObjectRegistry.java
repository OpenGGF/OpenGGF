package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic3k.events.Sonic3kMGZEvents;
import com.openggf.game.sonic3k.objects.badniks.BlastoidBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.BatbotBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.BuggernautBabyInstance;
import com.openggf.game.sonic3k.objects.badniks.BuggernautBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.CaterkillerJrBodyInstance;
import com.openggf.game.sonic3k.objects.badniks.ButterdroidBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.CaterkillerJrHeadInstance;
import com.openggf.game.sonic3k.objects.badniks.CluckoidBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.CorkeyBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.DragonflyBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.Flybot767BadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.JawzBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.MadmoleBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.MegaChopperBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.MantisBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.MonkeyDudeBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.MushmeanieBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.BubblesBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.OrbinautBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.PenguinatorBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.PoindexterBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.RibotBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.TunnelbotBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.TurboSpikerBadnikInstance;
import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.badniks.BloominatorBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.RhinobotBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.S3kBadnikProjectileInstance;
import com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.SparkleBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.SnaleBlasterBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.StarPointerBadnikInstance;
import com.openggf.game.sonic3k.objects.bosses.CnzEndBossInstance;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossBlade;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossBladeSplash;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossBladeWaterChute;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossInstance;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossRobotnikShip;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossTurbine;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossWaterColumn;
import com.openggf.game.sonic3k.objects.bosses.IczEndBossInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossArenaHelperInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossHitProxyChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossRobotnikHeadChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossRobotnikShipFlameInstance;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossSpikeChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossVisualChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossWeatherMachineChild;
import com.openggf.game.sonic3k.objects.bosses.MhzEndBossWeatherVisualChild;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.AbstractObjectRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Object registry for Sonic 3 &amp; Knuckles.
 *
 * <p>Route-critical objects, bosses, badniks, and cutscene controllers are
 * registered as concrete factories as they are ported. Remaining unknown or
 * low-priority object IDs still fall back to {@link PlaceholderObjectInstance}
 * for debug rendering. Object names are derived from the SK Set 1 pointer
 * table ({@code Object pointers - SK Set 1.asm}) in the S3K disassembly.
 *
 * <p>S3K uses two zone-set pointer tables (SK Set 1 for S&amp;K zones,
 * S3 Set for S3 zones) which remap some IDs above 110. The names here
 * use SK Set 1 as the canonical source; S3-only remappings share the
 * same underlying object names in most cases.
 */
public class Sonic3kObjectRegistry extends AbstractObjectRegistry {
    private static final List<DynamicObjectRewindCodec> DYNAMIC_REWIND_CODECS = List.of(
            ObjectRewindDynamicCodecs.deferredPlayerBoundCodec(
                    FireShieldObjectInstance.class, com.openggf.level.objects.ShieldObjectInstance.class),
            ObjectRewindDynamicCodecs.deferredPlayerBoundCodec(
                    LightningShieldObjectInstance.class, com.openggf.level.objects.ShieldObjectInstance.class),
            ObjectRewindDynamicCodecs.deferredPlayerBoundCodec(
                    BubbleShieldObjectInstance.class, com.openggf.level.objects.ShieldObjectInstance.class),
            ObjectRewindDynamicCodecs.deferredPlayerBoundCodec(
                    Sonic3kInvincibilityStarsObjectInstance.class,
                    com.openggf.level.objects.InvincibilityStarsObjectInstance.class),
            cnzMinibossChildCodec(CnzMinibossTopInstance.class, CnzMinibossTopInstance::new),
            // CnzMinibossCoilInstance codec deleted (Phase-2 batch 46):
            // compact restore resolves the parent object reference after generic recreate.
            // CnzMinibossSparkInstance codec deleted (Phase-2 batch 47):
            // compact restore resolves the parent object reference after generic recreate.
            // CnzMinibossScrollControlInstance codec deleted (Phase-2 batch 2):
            // now implements RewindRecreatable -> genericRecreate Path 1.

            // --- AIZ2 battleship / boss-endgame dynamic objects ---------------
            // Without these codecs, recreateDynamicObject() returns null for any
            // AIZ2 ship/boss object captured in a rewind keyframe and the object
            // is silently dropped on restore. The differentiating state (e.g.
            // baseSecondaryY, subtype, barrelIndex) lives in scalar fields that
            // were made non-final so the generic field capturer reapplies them
            // after recreate; codecs only need to build a structurally-correct
            // instance, so placeholders are passed where the value is reapplied.

            // Tier 1: AizBossSmallInstance codec deleted (Phase-2 batch 2):
            // now implements RewindRecreatable -> genericRecreate Path 1.

            // Tier 2: AizBattleshipInstance codec deleted (Phase-2 generic recreate):
            // now implements RewindRecreatable -> genericRecreate Path 1.
            // Other non-final differentiators below are still reapplied after recreate.
            // AizBgTreeInstance / Aiz2BossEndSequenceController codecs deleted
            // (Phase-2 batch 15): both use RewindRecreatable generic recreate.

            // Tier 3: relink to the live boss recreated earlier in the restore.
            aizMinibossChildCodec(AizMinibossBodyChild.class, AizMinibossBodyChild::new),
            aizMinibossChildCodec(AizMinibossArmChild.class, AizMinibossArmChild::new),
            aizMinibossChildCodec(AizMinibossNapalmController.class,
                    boss -> new AizMinibossNapalmController(boss, 0)),
            aizMinibossChildCodec(AizMinibossFlameBarrelChild.class,
                    boss -> new AizMinibossFlameBarrelChild(boss, 0, false)),
            aizEndBossChildCodec(AizEndBossShipChild.class, AizEndBossShipChild::new),
            aizEndBossChildCodec(AizEndBossFlameColumnChild.class, AizEndBossFlameColumnChild::new),
            aizEndBossChildCodec(AizEndBossArmChild.class,
                    boss -> new AizEndBossArmChild(boss, 0, 0, 0)),

            // --- AIZ2 transient combat/cosmetic children (now captured+restored) ---
            // These were previously dropped (Tier-4). Held rewind restores the
            // nearest keyframe and re-simulates forward each displayed frame, so a
            // dropped child gets RE-EMITTED from scratch and visibly plays forward.
            // Capturing + recreating them makes the whole scene rewind cleanly. The
            // codec only builds a structurally-correct instance (relinking the live
            // parent/sibling recreated earlier in spawn order); the non-final scalar
            // fields are reapplied afterward by the generic field capturer, so
            // placeholders are passed where a value is captured.

            // Self-contained primitive-only transients now implement RewindRecreatable
            // and use genericRecreate Path 1; no handwritten codecs are needed here.

            // Relink to the live battleship recreated earlier in the restore.
            aizBattleshipChildCodec(AizShipBombInstance.class,
                    (ship, s) -> new AizShipBombInstance(s, ship, 0, s.y())),

            // Relink to the live miniboss; sibling barrel/anchor where needed.
            aizMinibossChildCodec(AizMinibossFlameChild.class,
                    boss -> new AizMinibossFlameChild(boss, 0, 0, 0)),
            aizMinibossBarrelShotCodec(),
            aizSiblingAnchoredCodec(AizMinibossBarrelShotFlareChild.class,
                    AizMinibossBarrelShotChild.class, AizMinibossBarrelShotFlareChild::new),

            // Relink to the live end boss; sibling arm/propeller where needed.
            aizEndBossPropellerCodec(),
            aizEndBossFlameCodec(),
            aizEndBossChildCodec(AizEndBossBombChild.class,
                    boss -> new AizEndBossBombChild(boss, 0, 0, 0)),
            aizEndBossChildCodec(AizEndBossSmokeChild.class,
                    boss -> new AizEndBossSmokeChild(boss, 0, 0, false)),

            // --- Release-slice batch 1: HCZ/MHZ/MGZ/ICZ rewind recreate codecs ---
            // Without these, recreateDynamicObject() returns null for the listed
            // objects captured in a rewind keyframe and they vanish on restore.
            // Self-contained objects use exactSpawnCodec (re-running the ctor from
            // the captured spawn); non-spawn differentiator scalars were made
            // non-final so the generic field capturer reapplies them after recreate.

            // HCZConveyorBeltObjectInstance / MhzPulleyLiftObjectInstance /
            // MhzSwingVineObjectInstance codecs deleted (Phase-2 batch 2): all three
            // now implement RewindRecreatable -> genericRecreate Path 1.

            // Self-contained, non-final differentiators reapplied after recreate.
            // S3kBadnikProjectileInstance codec deleted (Phase-2 batch 15):
            // scalar differentiators are restored after generic recreate.
            // MGZHeadTriggerProjectileInstance codec deleted (Phase-2 batch 13):
            // xVel/hFlip are reapplied after genericRecreate Path 1.
            // S3kSignpostInstance codec deleted (Phase-2 batch 20):
            // nullable spawn + scalar state are handled by RewindRecreatable.
            // S3kAirCountdownObjectInstance codec deleted (Phase-2 batch 14):
            // nullable spawn + scalar state are handled by RewindRecreatable.

            // --- Release-slice batch 2: AIZ/CNZ/MGZ/MHZ/ICZ/SS/badnik recreate codecs ---
            // Transient cosmetic debris/explosion/sparkle children, damaging miniboss
            // children, the ICZ post-boss egg capsule, the SS-entry flash, and two
            // badnik children. Self-contained objects use exactSpawnCodec; non-spawn
            // differentiator scalars were made non-final (reapplied by the generic
            // capturer); parent/sibling-linked objects use relink codecs.

            // Self-contained cosmetic / gameplay debris and effects.
            // AizRockFragmentChild / CnzMinibossDebrisChild / S3kBossExplosionChild
            // codecs deleted (Phase-2 batch 12): all three now implement
            // RewindRecreatable -> genericRecreate Path 1.
            // S3kSignpostSparkleChild codec deleted (Phase-2 batch 14):
            // nullable spawn + scalar state are handled by RewindRecreatable.
            // MhzPollenParticleInstance codec deleted (Phase-2 batch 12):
            // now implements RewindRecreatable -> genericRecreate Path 1.

            // IczEndBossEggCapsuleInstance codec deleted (Phase-2 batch 17):
            // exact spawn coordinates are supplied by RewindRecreatable.

            // Self-contained badnik child (no live parent ref; differentiators captured).
            // CaterkillerJrBodyInstance codec deleted (Phase-2 batch 14):
            // constructor-only segment scalars are restored after generic recreate.

            // MHZ miniboss children (relink to the live boss recreated earlier).
            mhzMinibossChildCodec(MhzMinibossFlameInstance.class, MhzMinibossFlameInstance::new),
            mhzMinibossEscapeShardCodec(),

            // Parent/sibling relink codecs.
            // IczBigSnowPileInstance codec deleted (Phase-2 ICZ snow-pile batch):
            // live Sonic3kICZEvents owner is resolved by RewindRecreatable.
            signpostStubCodec(),
            starPostStarChildCodec(),
            starPostBonusStarChildCodec(),
            ssEntryFlashCodec(),
            buggernautBabyCodec(),

            // --- Release-slice batch 4: HCZ end-boss scene + AIZ boss/intro codecs ---
            // Without these, recreateDynamicObject() returns null for the listed
            // objects captured in a rewind keyframe and they vanish on restore.
            // Self-contained gameplay-critical bosses/cutscenes/capsules use
            // exactSpawnCodec; boss/parent-linked children use relink codecs that
            // resolve the live boss recreated earlier in the restore order. Non-spawn
            // differentiator scalars were made non-final where needed so the generic
            // field capturer reapplies them after recreate.

            // Self-contained gameplay-critical bosses / cutscenes / capsules.
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    AizEndBossInstance.class, s -> new AizEndBossInstance(s)),
            // Aiz2EndEggCapsuleInstance / HczEndBossEggCapsuleInstance codecs
            // deleted (Phase-2 batch 17): exact spawn coordinates are supplied
            // by RewindRecreatable.
            // HczEndBossGeyserCutscene codec deleted (Phase-2 batch 20):
            // exact spawn coordinates are supplied by RewindRecreatable.

            // HCZ end-boss children — relink to the live boss recreated earlier.
            // Turbine is registered BEFORE the water column so the column's
            // sibling relink finds it already live in getActiveObjects().
            hczEndBossChildCodec(HczEndBossRobotnikShip.class, HczEndBossRobotnikShip::new),
            bossChildCodec(HczEndBossTurbine.class, HczEndBossInstance.class,
                    boss -> new HczEndBossTurbine(boss, 0, 0x24)),
            hczEndBossChildCodec(HczEndBossBlade.class,
                    boss -> new HczEndBossBlade(boss, 0, 0, 0)),
            hczEndBossChildCodec(HczEndBossBladeWaterChute.class,
                    boss -> new HczEndBossBladeWaterChute(boss, 0, 0)),
            hczEndBossSplashCodec(),
            hczWaterColumnCodec(),

            // AIZ1 intro biplane cutscene children (parent-relink to the live
            // AizPlaneIntroInstance orchestrator).
            aizPlaneIntroChildCodec(),
            aizPlaneIntroWaveChildCodec(),

            // --- Release-slice batch 5: MHZ end-boss family + CNZ traversal codecs ---
            // Without these, recreateDynamicObject() returns null for the listed
            // objects captured in a rewind keyframe and they vanish on restore.
            // Self-contained objects use exactSpawnCodec (re-running the ctor from
            // the captured spawn); non-spawn differentiator scalars were made
            // non-final where needed so the generic field capturer reapplies them
            // after recreate. MHZ end-boss children parent-relink to the live boss,
            // which is registered FIRST so it is recreated before the relink runs.

            // Self-contained gameplay-critical CNZ traversal/launch objects.
            // CnzBumperObjectInstance / CnzCannonInstance / CnzCylinderInstance codecs
            // deleted (Phase-2 batch 2): all three now implement RewindRecreatable
            // -> genericRecreate Path 1.
            // CNZ lights flash codec deleted in Phase-2 batch 3: restoreAfter is
            // reapplied by the capturer after genericRecreate Path 1.

            // MHZ end boss codec deleted (Phase-2 batch 4):
            // now implements RewindRecreatable -> genericRecreate Path 1.

            // Self-contained MHZ end-boss objects.
            // MhzEndBossEggCapsuleInstance codec deleted (Phase-2 batch 18):
            // exact spawn coordinates are supplied by RewindRecreatable.
            // MhzEndBossPaletteFadeController codec deleted (Phase-2 batch 40):
            // placeholder palette state matches the previous codec; scalar/array
            // state is restored after generic recreate.

            // MHZ end-boss arena helper: role/spikeIndex/spikeTier/alternateSide
            // are un-finaled and reapplied; relinks the long-lived MHZ events owner.
            mhzEndBossArenaHelperCodec(),

            // MHZ end-boss children — relink to the live boss recreated earlier.
            bossChildCodec(MhzEndBossRobotnikHeadChild.class, MhzEndBossInstance.class,
                    MhzEndBossRobotnikHeadChild::new),
            // MhzEndBossRobotnikShipFlameInstance codec deleted (Phase-2 batch 45):
            // compact restore resolves the parent object reference after generic recreate.
            bossChildCodec(MhzEndBossSpikeChild.class, MhzEndBossInstance.class,
                    boss -> new MhzEndBossSpikeChild(boss, 0, 0, 0)),
            bossChildCodec(MhzEndBossVisualChild.class, MhzEndBossInstance.class,
                    boss -> new MhzEndBossVisualChild(boss, 0, 0, false)),
            bossChildCodec(MhzEndBossWeatherMachineChild.class, MhzEndBossInstance.class,
                    boss -> new MhzEndBossWeatherMachineChild(boss)),
            mhzEndBossHitProxyCodec(),
            // MhzEndBossDefeatFragmentChild codec deleted (Phase-2 batch 42):
            // compact restore reapplies parent-derived subtype/xVel after generic recreate.

            // MHZ weather-machine visual children — relink to the live
            // weather-machine child recreated just above (in spawn order).
            mhzEndBossWeatherVisualChildCodec(),

            // --- Release-slice batch 6: LBZ/CNZ/AIZ1 cutscene + MGZ end-act +
            // MHZ ship/door + shield/spark rewind recreate codecs ---------------
            // Without these, recreateDynamicObject() returns null for the listed
            // objects captured in a rewind keyframe and they vanish on restore.
            // Self-contained objects use exactSpawnCodec; non-spawn differentiator
            // scalars were made non-final where needed so the generic field capturer
            // reapplies them after recreate. Parent/sibling-linked objects use relink
            // codecs that resolve the live parent (placed/layout cutscene or event
            // manager) recreated earlier in the restore order.

            // LBZ1 Knuckles cutscene family.
            cutsceneKnucklesLbz1CollapseChildCodec(),
            cutsceneKnucklesLbz1RangeHelperCodec(),
            // CutsceneKnucklesLbz1ThrownBomb codec deleted (Phase-2 batch 15):
            // motion state is restored after generic recreate.

            // AIZ1 intro Knuckles codec deleted (Phase-2 batch 4): parent is
            // self-contained via RewindRecreatable; the rock child still relinks
            // to the live parent in getActiveObjects().
            cutsceneKnucklesAiz1RockChildCodec(),

            // CNZ2 Knuckles cutscene blocking wall: relink to the placed parent.
            cutsceneKnuxCnz2WallCodec(),

            // S3K InstaShield (player-bound; rebinds the live player like its
            // fire/lightning/bubble shield siblings).
            ObjectRewindDynamicCodecs.deferredPlayerBoundCodec(
                    InstaShieldObjectInstance.class,
                    com.openggf.level.objects.ShieldObjectInstance.class),

            // LightningShield spark particle codec deleted (Phase-2 batch 12):
            // self-contained; re-fetches art via RewindRecreatable.

            // MGZ end-of-act capsule / animals / results / collapse floor / boss.
            // Mgz2EndEggCapsuleInstance codec deleted (Phase-2 batch 18):
            // exact spawn coordinates are supplied by RewindRecreatable.
            // Mgz2CapsuleAnimalInstance codec deleted (Phase-2 batch 20):
            // captured scalar state is restored after generic recreate.
            // S3kResultsScreenObjectInstance codec deleted (Phase-2 batch 44):
            // character/act/player-ref state is restored after generic recreate.
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    Mgz2ResultsScreenObjectInstance.class,
                    s -> new Mgz2ResultsScreenObjectInstance(PlayerCharacter.SONIC_AND_TAILS, 0)),
            mgz2CollapseSolidCodec(),
            // MgzDrillingRobotnikInstance codec deleted (Phase-2 batch 6):
            // now implements RewindRecreatable -> genericRecreate Path 1.
            // MgzEndBossInstance codec deleted (Phase-2 batch 5):
            // now implements RewindRecreatable -> genericRecreate Path 1.

            // MHZ1 Knuckles cutscene door (relink to the placed button parent).
            mhz1CutsceneDoorCodec(),

            // MHZ Act 2 ship-sequence controller codec deleted (Phase-2 batch 16):
            // ROM-fixed seeds are supplied by RewindRecreatable generic recreate.

            // --- Release-slice batch 7: Pachinko traps/flippers + boss-defeat
            // signpost flow + song-fade transition + rock debris + egg-prison
            // animal recreate codecs --------------------------------------------
            // Without these, recreateDynamicObject() returns null for the listed
            // objects captured in a rewind keyframe and they vanish on restore.
            // All are self-contained (no live parent/sibling relink). Non-spawn
            // differentiator scalars were made non-final where needed so the
            // generic field capturer reapplies them after recreate.

            // Pachinko bonus-stage capture trap + sloped flipper codecs deleted
            // (Phase-2 batch 2): both now implement RewindRecreatable ->
            // genericRecreate Path 1 (fully spawn-constructible, no parent link).

            // AIZ/LRZ breakable-rock gravity debris codec deleted (Phase-2
            // batch 12): mappingFrame/artKey are reapplied after generic recreate.

            // Boss-defeat signpost flow codec deleted (Phase-2 batch 16):
            // signpostX is spawn-derivable; apparentAct/cleanupAction are restored
            // by the generic field capturer after RewindRecreatable recreate.

            // SongFadeTransitionInstance and EggPrisonAnimalInstance codecs
            // deleted (Phase-2 batch 13): placeholder constructor args are now
            // supplied by RewindRecreatable; scalar state is reapplied afterward.

            // Slot-machine bonus-stage objects now implement RewindRecreatable:
            // they resolve the live S3kSlotStageController from the active
            // Sonic3kBonusStageCoordinator via ObjectServices during generic recreate.

            // --- Batch-inner1: inner-class hazard/solid/cosmetic child codecs -----
            // Static nested children that were dropped on held rewind because no
            // codec matched their JVM binary name. Each is keyed by the binary-name
            // string (mirroring buzzerFlameCodec) or the typed exactSpawnCodec where
            // the class is registry-visible. Parent-relinked children resolve the
            // live parent via getActiveObjects(); self-contained projectiles/effects
            // re-run their ctor from the captured spawn. Non-spawn differentiator
            // scalars were un-finaled so the generic field capturer reapplies them
            // after recreate, so placeholder ctor args are safe.

            // AIZ spiked-log spike hitbox (relink to nearest live spiked log).
            aizSpikedLogSpikeCodec(),
            // AIZ falling-log ridable platform (self-contained; act-derived artKey
            // reapplied by the capturer).
            ObjectRewindDynamicCodecs.exactSpawnCodec(
                    AizFallingLogObjectInstance.FallingLogChild.class,
                    s -> new AizFallingLogObjectInstance.FallingLogChild(
                            s.x(), s.y(), Sonic3kObjectArtKeys.AIZ1_FALLING_LOG)),
            // AIZ tree-reveal control shim codec deleted (Phase-2 batch 21):
            // self-contained nested class now uses genericRecreate Path 1.
            // HCZ water-drop cosmetic child codec deleted in Phase-2 batch 3:
            // self-contained private nested class now uses genericRecreate Path 1.
            // Blastoid/SnaleBlaster/Spiker in-flight projectile codecs deleted in
            // Phase-2 batch 19: self-contained private nested classes now use
            // genericRecreate Path 1.
            // CorkeyShotChild now implements RewindRecreatable -> genericRecreate Path 1;
            // compact restore reapplies the captured shot script after recreate.
            // Dragonfly swinging body segment (relink to live parent / prior sibling).
            dragonflyLinkedBodyChildCodec(),
            // Ribot leg/swing appendage (relink to nearest live Ribot body).
            ribotActiveChildCodec(),
            // Spiker spring-loaded top spike (relink to nearest live Spiker).
            spikerTopSpikeCodec(),
            // Star Pointer orbiting/launched point (relink to nearest live parent).
            starPointerPointCodec(),
            // Orbinaut orbiting orb (relink to nearest live parent).
            orbinautOrbCodec(),
            // Turbo Spiker launched shell (relink to nearest live parent).
            turboSpikerShellChildCodec(),
            // Madmole side-drill codec deleted (Phase-2 batch 43): self-contained;
            // facingLeft is recovered from the spawn render flag during generic recreate.
            // ICZ end-boss fleeing-Robotnik escape ship codec deleted (Phase-2
            // batch 37): self-contained cutscene ship now uses genericRecreate
            // Path 1.

            // --- Batch-inner2: nested-class hazard/solid/cutscene child codecs -----
            // More static/non-static nested children dropped on held rewind because
            // no codec matched their JVM binary name. Parent-relinked children resolve
            // the live parent (placed boss / cutscene / event owner) via
            // getActiveObjects(); self-contained chips re-run their ctor from the
            // captured spawn. Non-spawn differentiator scalars were un-finaled so the
            // generic field capturer reapplies them after recreate.

            // MGZ miniboss ceiling spire codec deleted (Phase-2 batch 21):
            // self-contained nested class now uses genericRecreate Path 1.
            // MGZ miniboss drill arm (one-shot hurt hitbox; relink live boss).
            mgzDrillArmCodec(),
            // Gumball ExitTriggerChild codec deleted (Phase-2 batch 5):
            // now implements RewindRecreatable -> genericRecreate Path 1.
            // MGZ head-trigger stone chip codec deleted (Phase-2 batch 21):
            // self-contained nested class now uses genericRecreate Path 1.
            // MHZ1 cutscene Player-2 stopper (sidekick lock; relink cutscene owner).
            mhz1CutscenePlayerTwoStopperCodec(),
            // MHZ2 cutscene route-switch carrier (cosmetic; relink cutscene parent).
            mhz2KnucklesRouteSwitchChildCodec(),
            // HCZ miniboss rocket touch hitbox (hurt hazard; relink boss + slot).
            hczMinibossRocketTouchCodec(),
            // ICZ ice-spikes hurt child (hurt hazard; relink nearest live spike base).
            iczIceSpikesHurtChildCodec());

    // AIZ2 dynamic objects still intentionally dropped on rewind restore (no codec):
    //   (none remaining) — all AIZ2 battleship/boss transient children are now
    //   captured and recreated on restore so held rewind reverses the scene cleanly.

    @Override
    public ObjectSlotLayout objectSlotLayout() {
        return ObjectSlotLayout.SONIC_3K;
    }

    @Override
    public List<DynamicObjectRewindCodec> dynamicRewindCodecs() {
        return DYNAMIC_REWIND_CODECS;
    }

    private static DynamicObjectRewindCodec cnzMinibossChildCodec(
            Class<? extends AbstractObjectInstance> type,
            Function<ObjectSpawn, ? extends AbstractObjectInstance> factory) {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == type;
            }

            @Override
            public String className() {
                return type.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                CnzMinibossInstance parent = findCnzMinibossParentForRewind(context);
                if (parent == null) {
                    return null;
                }
                AbstractObjectInstance child = factory.apply(entry.spawn());
                if (child instanceof CnzMinibossTopInstance top) {
                    top.attachBossForTest(parent);
                } else if (child instanceof CnzMinibossCoilInstance coil) {
                    coil.attachBossForTest(parent);
                } else if (child instanceof CnzMinibossSparkInstance spark) {
                    spark.attachBossForTest(parent);
                }
                return child;
            }
        };
    }

    private static CnzMinibossInstance findCnzMinibossParentForRewind(
            DynamicObjectRecreateContext context) {
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (inst instanceof CnzMinibossInstance parent) {
                return parent;
            }
        }
        return null;
    }

    /**
     * Codec for an AIZ miniboss child. The live {@link AizMinibossInstance} is
     * layout-spawned and recreated before the dynamic-object restore loop, so it
     * is already present in {@code getActiveObjects()}. The child's
     * {@code parent} is final and passed into the constructor. If the live
     * miniboss is absent the child is dropped (codec returns null) rather than
     * throwing.
     */
    private static DynamicObjectRewindCodec aizMinibossChildCodec(
            Class<? extends AbstractObjectInstance> type,
            Function<AbstractBossInstance, ? extends AbstractObjectInstance> factory) {
        return bossChildCodec(type, AizMinibossInstance.class, factory);
    }

    /**
     * Codec for an AIZ end-boss child. As with the miniboss children, the live
     * {@link AizEndBossInstance} is recreated before the dynamic-object restore
     * loop, so it is found in {@code getActiveObjects()} and passed into the
     * child constructor.
     */
    private static DynamicObjectRewindCodec aizEndBossChildCodec(
            Class<? extends AbstractObjectInstance> type,
            Function<AizEndBossInstance, ? extends AbstractObjectInstance> factory) {
        return bossChildCodec(type, AizEndBossInstance.class, factory);
    }

    /**
     * Codec for an HCZ end-boss child. The live {@link HczEndBossInstance} is the
     * placed/layout-spawned boss recreated before the dynamic-object restore loop,
     * so it is found in {@code getActiveObjects()} and passed into the child
     * constructor. If the boss is absent the child is dropped (codec returns null).
     */
    private static DynamicObjectRewindCodec hczEndBossChildCodec(
            Class<? extends AbstractObjectInstance> type,
            Function<HczEndBossInstance, ? extends AbstractObjectInstance> factory) {
        return bossChildCodec(type, HczEndBossInstance.class, factory);
    }

    /**
     * Codec for the HCZ end-boss blade splash. Relinks the live boss and recovers
     * the splash spawn X from the captured spawn (AbstractBossChild keeps
     * {@code dynamicSpawn.x() == currentX == bladeX}). The animation scalars are
     * non-final and reapplied by the generic field capturer after recreate.
     */
    private static DynamicObjectRewindCodec hczEndBossSplashCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == HczEndBossBladeSplash.class;
            }

            @Override
            public String className() {
                return HczEndBossBladeSplash.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                HczEndBossInstance boss = findLiveBossForRewind(context, HczEndBossInstance.class);
                if (boss == null) {
                    return null; // parent not yet live; drop this restore
                }
                return new HczEndBossBladeSplash(boss, entry.spawn().x());
            }
        };
    }

    /**
     * Codec for the HCZ end-boss water column. It needs both the live
     * {@link HczEndBossInstance} (a placed/active object) and the live
     * {@link HczEndBossTurbine} sibling. The turbine is itself a dynamic child
     * captured earlier in spawn order, and its codec is registered before this one
     * so it is already recreated (and present in {@code getActiveObjects()}) when
     * the column relinks. If either is absent the column is dropped.
     */
    private static DynamicObjectRewindCodec hczWaterColumnCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == HczEndBossWaterColumn.class;
            }

            @Override
            public String className() {
                return HczEndBossWaterColumn.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                HczEndBossInstance boss = findLiveBossForRewind(context, HczEndBossInstance.class);
                HczEndBossTurbine turbine = findLiveInstance(context, HczEndBossTurbine.class);
                if (boss == null || turbine == null) {
                    return null;
                }
                return new HczEndBossWaterColumn(boss, turbine);
            }
        };
    }

    /**
     * Codec for the AIZ1 intro biplane child. The live
     * {@link AizPlaneIntroInstance} orchestrator is resolved via its static
     * accessor (set in its constructor, cleared on destroy), which is more robust
     * than scanning active objects and avoids spawn-order assumptions. The plane
     * child's {@code parent} is final and passed into the constructor. If the
     * intro is over (no live orchestrator) the child is dropped.
     */
    private static DynamicObjectRewindCodec aizPlaneIntroChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == AizIntroPlaneChild.class;
            }

            @Override
            public String className() {
                return AizIntroPlaneChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                AizPlaneIntroInstance parent = AizPlaneIntroInstance.getActiveIntroInstance();
                if (parent == null) {
                    return null;
                }
                return new AizIntroPlaneChild(entry.spawn(), parent);
            }
        };
    }

    /**
     * Codec for the AIZ1 intro wave-splash child. Relinks the single live
     * {@link AizPlaneIntroInstance} orchestrator (passed into the constructor and
     * used live each frame via its scroll speed). If the intro is over the wave is
     * dropped.
     */
    private static DynamicObjectRewindCodec aizPlaneIntroWaveChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == AizIntroWaveChild.class;
            }

            @Override
            public String className() {
                return AizIntroWaveChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                AizPlaneIntroInstance parent = AizPlaneIntroInstance.getActiveIntroInstance();
                if (parent == null) {
                    return null;
                }
                return new AizIntroWaveChild(entry.spawn(), parent);
            }
        };
    }

    private static <B extends AbstractBossInstance> DynamicObjectRewindCodec bossChildCodec(
            Class<? extends AbstractObjectInstance> type,
            Class<B> bossType,
            Function<? super B, ? extends AbstractObjectInstance> factory) {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == type;
            }

            @Override
            public String className() {
                return type.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                B boss = findLiveBossForRewind(context, bossType);
                if (boss == null) {
                    return null;
                }
                return factory.apply(boss);
            }
        };
    }

    private static <B extends AbstractBossInstance> B findLiveBossForRewind(
            DynamicObjectRecreateContext context, Class<B> bossType) {
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (bossType.isInstance(inst)) {
                return bossType.cast(inst);
            }
        }
        return null;
    }

    /**
     * Codec for the AIZ2 battleship's dropped bombs. The live
     * {@link AizBattleshipInstance} is recreated earlier in the restore loop
     * (entries are processed in spawn order, and the ship spawns before its
     * bombs), so it is found in {@code getActiveObjects()} and passed into the
     * bomb constructor. If no live ship is present the bomb is dropped.
     */
    private static DynamicObjectRewindCodec aizBattleshipChildCodec(
            Class<? extends AbstractObjectInstance> type,
            BiFunction<AizBattleshipInstance, ObjectSpawn, ? extends AbstractObjectInstance> factory) {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == type;
            }

            @Override
            public String className() {
                return type.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                AizBattleshipInstance ship = findLiveInstance(context, AizBattleshipInstance.class);
                if (ship == null) {
                    return null;
                }
                return factory.apply(ship, entry.spawn());
            }
        };
    }

    /**
     * Codec for the AIZ end-boss propeller, which needs both the live boss and
     * the live {@link AizEndBossArmChild} it is mounted on. The arm is spawned
     * before the propeller, so it is already present in
     * {@code getActiveObjects()} during the propeller's recreate. If either the
     * boss or the arm is absent the propeller is dropped.
     */
    private static DynamicObjectRewindCodec aizEndBossPropellerCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == AizEndBossPropellerChild.class;
            }

            @Override
            public String className() {
                return AizEndBossPropellerChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                AizEndBossInstance boss = findLiveBossForRewind(context, AizEndBossInstance.class);
                AizEndBossArmChild arm = findLiveInstance(context, AizEndBossArmChild.class);
                if (boss == null || arm == null) {
                    return null;
                }
                return new AizEndBossPropellerChild(boss, arm, 0);
            }
        };
    }

    /**
     * Codec for the AIZ end-boss flame, which needs both the live boss and the
     * live {@link AizEndBossPropellerChild} that emitted it. The propeller is
     * spawned before its flames, so it is present during the flame's recreate.
     * If either is absent the flame is dropped.
     */
    private static DynamicObjectRewindCodec aizEndBossFlameCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == AizEndBossFlameChild.class;
            }

            @Override
            public String className() {
                return AizEndBossFlameChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                AizEndBossInstance boss = findLiveBossForRewind(context, AizEndBossInstance.class);
                AizEndBossPropellerChild propeller =
                        findLiveInstance(context, AizEndBossPropellerChild.class);
                if (boss == null || propeller == null) {
                    return null;
                }
                return new AizEndBossFlameChild(boss, propeller, 0);
            }
        };
    }

    /**
     * Codec for the AIZ miniboss barrel shot, which needs both the live miniboss
     * and the live {@link AizMinibossFlameBarrelChild} that fired it. The barrel
     * is spawned before its shots, so it is present during the shot's recreate.
     * When several barrels are live the shot is relinked to the barrel nearest
     * its captured spawn position; if none is live the shot is dropped.
     */
    private static DynamicObjectRewindCodec aizMinibossBarrelShotCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == AizMinibossBarrelShotChild.class;
            }

            @Override
            public String className() {
                return AizMinibossBarrelShotChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                AizMinibossInstance boss = findLiveBossForRewind(context, AizMinibossInstance.class);
                AizMinibossFlameBarrelChild barrel = findNearestLiveInstance(
                        context, AizMinibossFlameBarrelChild.class, entry.spawn());
                if (boss == null || barrel == null) {
                    return null;
                }
                return new AizMinibossBarrelShotChild(
                        boss, barrel, 0, 0, AizMinibossBarrelShotChild.Mode.SIMPLE);
            }
        };
    }

    /**
     * Codec for a cosmetic child anchored to a live sibling (e.g. the barrel-shot
     * muzzle flare anchored to its barrel shot). The anchor is recreated earlier
     * in spawn order; when several anchors of the type are live the child is
     * relinked to the one nearest its captured spawn position. If no anchor is
     * live the child is dropped rather than recreated with a null anchor.
     */
    private static <A extends AbstractObjectInstance> DynamicObjectRewindCodec aizSiblingAnchoredCodec(
            Class<? extends AbstractObjectInstance> type,
            Class<A> anchorType,
            Function<? super A, ? extends AbstractObjectInstance> factory) {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == type;
            }

            @Override
            public String className() {
                return type.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                A anchor = findNearestLiveInstance(context, anchorType, entry.spawn());
                if (anchor == null) {
                    return null;
                }
                return factory.apply(anchor);
            }
        };
    }

    private static <T> T findLiveInstance(DynamicObjectRecreateContext context, Class<T> type) {
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (type.isInstance(inst)) {
                return type.cast(inst);
            }
        }
        return null;
    }

    /**
     * Finds the live instance of {@code type} whose current position is nearest
     * the captured spawn. Used to relink a cosmetic/combat child to the correct
     * one of several live siblings of the same concrete type. Falls back to the
     * first live instance when the spawn is null.
     */
    private static <T> T findNearestLiveInstance(
            DynamicObjectRecreateContext context, Class<T> type, ObjectSpawn spawn) {
        T best = null;
        long bestDistance = Long.MAX_VALUE;
        for (ObjectInstance inst : context.objectManager().getActiveObjects()) {
            if (!type.isInstance(inst)) {
                continue;
            }
            if (spawn == null) {
                return type.cast(inst);
            }
            long dx = inst.getX() - spawn.x();
            long dy = inst.getY() - spawn.y();
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = type.cast(inst);
            }
        }
        return best;
    }

    // ===================================================================
    // Batch-inner1: inner-class hazard/solid/cosmetic child rewind codecs
    // ===================================================================

    private static final String SPIKER_TOP_SPIKE_CHILD_CLASS =
            "com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance$SpikerTopSpikeChild";
    private static final String RIBOT_ACTIVE_CHILD_CLASS =
            "com.openggf.game.sonic3k.objects.badniks.RibotBadnikInstance$RibotActiveChild";
    private static final String DRAGONFLY_LINKED_BODY_CHILD_CLASS =
            "com.openggf.game.sonic3k.objects.badniks.DragonflyBadnikInstance$LinkedBodyChild";
    private static final String STAR_POINTER_POINT_CLASS =
            "com.openggf.game.sonic3k.objects.badniks.StarPointerBadnikInstance$OrbitingPointInstance";
    private static final String ORBINAUT_ORB_CLASS =
            "com.openggf.game.sonic3k.objects.badniks.OrbinautBadnikInstance$OrbinautOrbInstance";
    private static final String TURBO_SPIKER_SHELL_CHILD_CLASS =
            "com.openggf.game.sonic3k.objects.badniks.TurboSpikerBadnikInstance$TurboSpikerShellChild";

    // Batch-inner2 binary-name keys (private/nested children -> no Class literal).
    private static final String MGZ_DRILL_ARM_CHILD_CLASS =
            "com.openggf.game.sonic3k.objects.MgzMinibossInstance$DrillArmChild";
    private static final String MHZ1_CUTSCENE_P2_STOPPER_CLASS =
            "com.openggf.game.sonic3k.objects.Mhz1CutsceneKnucklesInstance$Mhz1CutscenePlayerTwoStopper";
    private static final String MHZ2_KNUX_ROUTE_SWITCH_CHILD_CLASS =
            "com.openggf.game.sonic3k.objects.CutsceneKnucklesMhz2Instance$Mhz2KnucklesRouteSwitchChild";
    private static final String HCZ_MINIBOSS_ROCKET_TOUCH_CLASS =
            "com.openggf.game.sonic3k.objects.HczMinibossInstance$RocketTouchChild";
    private static final String ICZ_ICE_SPIKES_HURT_CHILD_CLASS =
            "com.openggf.game.sonic3k.objects.IczIceSpikesObjectInstance$SpikeHurtChild";

    private static DynamicObjectRewindCodec aizSpikedLogSpikeCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass()
                        == AizSpikedLogObjectInstance.SpikedLogCollisionChild.class;
            }

            @Override
            public String className() {
                return AizSpikedLogObjectInstance.SpikedLogCollisionChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                // The child spawns at the parent log's spawn (x,y); relink the
                // correct one of several live spiked logs by nearest captured spawn.
                AizSpikedLogObjectInstance parent = findNearestLiveInstance(
                        context, AizSpikedLogObjectInstance.class, entry.spawn());
                if (parent == null) {
                    return null;
                }
                return new AizSpikedLogObjectInstance.SpikedLogCollisionChild(
                        entry.spawn(), parent);
            }
        };
    }

    private static DynamicObjectRewindCodec dragonflyLinkedBodyChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(DRAGONFLY_LINKED_BODY_CHILD_CLASS);
            }

            @Override
            public String className() {
                return DRAGONFLY_LINKED_BODY_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                DragonflyBadnikInstance parent = findNearestLiveInstance(
                        context, DragonflyBadnikInstance.class, entry.spawn());
                if (parent == null) {
                    return null;
                }
                // segmentIndex = subtype >> 1 (subtype = i << 1). Segment 0 anchors
                // to the parent; later segments chain off the previously-recreated
                // sibling (restore is spawn-order, so the earlier sibling is live).
                ObjectSpawn spawn = entry.spawn();
                int subtype = spawn != null ? spawn.subtype() : 0;
                int segmentIndex = subtype >> 1;
                AbstractObjectInstance followAnchor = parent;
                if (segmentIndex > 0) {
                    AbstractObjectInstance sibling = findNearestLiveInstance(
                            context, DragonflyBadnikInstance.LinkedBodyChild.class, spawn);
                    if (sibling != null) {
                        followAnchor = sibling;
                    }
                }
                try {
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            DragonflyBadnikInstance.class, AbstractObjectInstance.class,
                            int.class, int.class);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(
                            parent, followAnchor, subtype, segmentIndex);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec ribotActiveChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(RIBOT_ACTIVE_CHILD_CLASS);
            }

            @Override
            public String className() {
                return RIBOT_ACTIVE_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                try {
                    // Children spawn within +/-0x18 px of the parent body; relink
                    // the nearest live Ribot to the captured child spawn.
                    RibotBadnikInstance parent = findNearestLiveInstance(
                            context, RibotBadnikInstance.class, entry.spawn());
                    if (parent == null) {
                        return null;
                    }
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            ObjectSpawn.class, RibotBadnikInstance.class,
                            int.class, int.class, int.class);
                    ctor.setAccessible(true);
                    // childIndex/originX/originY (now un-final) reapplied by the capturer.
                    return (ObjectInstance) ctor.newInstance(
                            entry.spawn(), parent, 0, entry.spawn().x(), entry.spawn().y());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec spikerTopSpikeCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(SPIKER_TOP_SPIKE_CHILD_CLASS);
            }

            @Override
            public String className() {
                return SPIKER_TOP_SPIKE_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                SpikerBadnikInstance parent = findNearestLiveInstance(
                        context, SpikerBadnikInstance.class, entry.spawn());
                if (parent == null) {
                    return null;
                }
                try {
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(SpikerBadnikInstance.class);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(parent);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec starPointerPointCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(STAR_POINTER_POINT_CLASS);
            }

            @Override
            public String className() {
                return STAR_POINTER_POINT_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                StarPointerBadnikInstance parent = findNearestLiveInstance(
                        context, StarPointerBadnikInstance.class, entry.spawn());
                if (parent == null) {
                    return null;
                }
                try {
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            ObjectSpawn.class, StarPointerBadnikInstance.class, int.class);
                    ctor.setAccessible(true);
                    // index seeds the initial angle only; the captured angle/launched/
                    // xVelocity/break* scalars are reapplied by the capturer.
                    return (ObjectInstance) ctor.newInstance(entry.spawn(), parent, 0);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec orbinautOrbCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(ORBINAUT_ORB_CLASS);
            }

            @Override
            public String className() {
                return ORBINAUT_ORB_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                OrbinautBadnikInstance parent = findNearestLiveInstance(
                        context, OrbinautBadnikInstance.class, entry.spawn());
                if (parent == null) {
                    return null;
                }
                try {
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            ObjectSpawn.class, OrbinautBadnikInstance.class, int.class);
                    ctor.setAccessible(true);
                    // index seeds the initial angle only; the captured angle scalar
                    // is reapplied by the capturer after recreate.
                    return (ObjectInstance) ctor.newInstance(entry.spawn(), parent, 0);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    private static DynamicObjectRewindCodec turboSpikerShellChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(TURBO_SPIKER_SHELL_CHILD_CLASS);
            }

            @Override
            public String className() {
                return TURBO_SPIKER_SHELL_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                try {
                    TurboSpikerBadnikInstance parent = findNearestLiveInstance(
                            context, TurboSpikerBadnikInstance.class, entry.spawn());
                    if (parent == null) {
                        return null;
                    }
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(TurboSpikerBadnikInstance.class);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(parent);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    // ===================================================================
    // Batch-inner2: nested-class hazard/solid/cutscene child rewind codecs
    // ===================================================================

    /**
     * Codec for the MGZ miniboss drill arm (a HURT hitbox, ARM_COLLISION_FLAGS
     * 0x9E). The arms are spawned ONCE at the arena-engage transition, not per
     * frame, so the restored boss never re-emits them (parentReEmits=false) and a
     * dropped arm leaves the miniboss without its drill-arm hitboxes for the rest
     * of the fight. Relinks the live {@link MgzMinibossInstance} (recreated before
     * the dynamic-restore loop) and reconstructs via the private ctor; xOffset/
     * yOffset were un-finaled so the capturer reapplies the left/right
     * differentiator, and currentX/currentY are already non-final.
     */
    private static DynamicObjectRewindCodec mgzDrillArmCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(MGZ_DRILL_ARM_CHILD_CLASS);
            }

            @Override
            public String className() {
                return MGZ_DRILL_ARM_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                MgzMinibossInstance parent = findLiveInstance(context, MgzMinibossInstance.class);
                if (parent == null) {
                    return null;
                }
                try {
                    Class<?> cls = Class.forName(MGZ_DRILL_ARM_CHILD_CLASS);
                    var ctor = cls.getDeclaredConstructor(
                            MgzMinibossInstance.class, int.class, int.class);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(parent, 0, 0);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + MGZ_DRILL_ARM_CHILD_CLASS, e);
                }
            }
        };
    }

    /**
     * Codec for the MHZ1 rival-Knuckles cutscene Player-2 stopper (an invisible
     * helper that locks/ducks native Player 2 during the cutscene). The owner
     * spawns it exactly once behind a captured {@code playerTwoStopperSpawned}
     * latch that survives rewind, so it is NOT re-emitted (parentReEmits=false)
     * and dropping it would lose the sidekick lock. Relinks the live
     * {@link Mhz1CutsceneKnucklesInstance} owner and reconstructs via the private
     * 1-arg ctor; the only mutable scalar ({@code locked}) is captured generically.
     */
    private static DynamicObjectRewindCodec mhz1CutscenePlayerTwoStopperCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(MHZ1_CUTSCENE_P2_STOPPER_CLASS);
            }

            @Override
            public String className() {
                return MHZ1_CUTSCENE_P2_STOPPER_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Mhz1CutsceneKnucklesInstance parent =
                        findLiveInstance(context, Mhz1CutsceneKnucklesInstance.class);
                if (parent == null) {
                    return null;
                }
                try {
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(Mhz1CutsceneKnucklesInstance.class);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(parent);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    /**
     * Codec for the MHZ2 Knuckles leaf-blower cutscene route-switch carrier
     * (cosmetic: only draws the route-switch sprite; the hazard/camera/launch
     * logic lives on the parent). The parent spawns it once behind a non-rewound
     * {@code switchChildSpawned} latch and never re-emits it (parentReEmits=false),
     * so it must be restored for visual parity. Relinks the unique live
     * {@link CutsceneKnucklesMhz2Instance} parent and reconstructs via the private
     * 1-arg ctor; {@code knucklesRoute} was un-finaled so the capturer reapplies
     * the captured value after recreate.
     */
    private static DynamicObjectRewindCodec mhz2KnucklesRouteSwitchChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(MHZ2_KNUX_ROUTE_SWITCH_CHILD_CLASS);
            }

            @Override
            public String className() {
                return MHZ2_KNUX_ROUTE_SWITCH_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                CutsceneKnucklesMhz2Instance parent =
                        findLiveInstance(context, CutsceneKnucklesMhz2Instance.class);
                if (parent == null) {
                    return null;
                }
                try {
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(CutsceneKnucklesMhz2Instance.class);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(parent);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    /**
     * Codec for the HCZ miniboss rocket touch-response child (4 per fight; a HURT
     * hitbox view over the parent's RocketState[]). The boss re-positions the
     * rockets every frame but does NOT re-emit the children: {@code
     * spawnRocketTouchChildren()} is guarded one-shot, so a dropped child is gone
     * for the rest of the fight (parentReEmits=false) — it must be restored.
     * Relinks the single live {@link HczMinibossInstance} (layout-placed,
     * recreated before this loop), reconstructs via the non-static inner ctor
     * (synthetic leading enclosing-instance param), and re-attaches the parent's
     * {@code rocketTouchChildren[rocketIndex]} slot. The three scalar args are
     * un-finaled and reapplied by the capturer.
     */
    private static DynamicObjectRewindCodec hczMinibossRocketTouchCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(HCZ_MINIBOSS_ROCKET_TOUCH_CLASS);
            }

            @Override
            public String className() {
                return HCZ_MINIBOSS_ROCKET_TOUCH_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                try {
                    HczMinibossInstance parent =
                            findLiveInstance(context, HczMinibossInstance.class);
                    if (parent == null) {
                        return null;
                    }
                    ObjectSpawn spawn = entry.spawn();
                    int rocketIndex = spawn.subtype() / 2;
                    int objectId = spawn.objectId();
                    int layoutIndex = spawn.layoutIndex();

                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            HczMinibossInstance.class, int.class, int.class, int.class);
                    ctor.setAccessible(true);
                    ObjectInstance child = (ObjectInstance) ctor.newInstance(
                            parent, rocketIndex, objectId, layoutIndex);

                    var f = HczMinibossInstance.class.getDeclaredField("rocketTouchChildren");
                    f.setAccessible(true);
                    Object arr = f.get(parent);
                    if (arr == null) {
                        arr = java.lang.reflect.Array.newInstance(cls, 4);
                        f.set(parent, arr);
                    }
                    if (rocketIndex >= 0 && rocketIndex < java.lang.reflect.Array.getLength(arr)) {
                        java.lang.reflect.Array.set(arr, rocketIndex, child);
                    }
                    return child;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    /**
     * Codec for the ICZ ice-spikes hurt child (a HURT hitbox, CHILD_COLLISION_FLAGS
     * 0x98). The subtype-0 parent spawns it once behind a captured {@code
     * childSpawned} latch restored to true on rewind, so the parent does not
     * re-emit it (parentReEmits=false) and a dropped child permanently loses a hurt
     * hitbox. Relinks the nearest live {@link IczIceSpikesObjectInstance} (multiple
     * spike bases can coexist) and reconstructs via the private 3-arg ctor; x/y are
     * spawn-derivable so nothing needs un-finaling.
     */
    private static DynamicObjectRewindCodec iczIceSpikesHurtChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass().getName().equals(ICZ_ICE_SPIKES_HURT_CHILD_CLASS);
            }

            @Override
            public String className() {
                return ICZ_ICE_SPIKES_HURT_CHILD_CLASS;
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                IczIceSpikesObjectInstance parent = findNearestLiveInstance(
                        context, IczIceSpikesObjectInstance.class, entry.spawn());
                if (parent == null) {
                    return null;
                }
                try {
                    Class<?> cls = Class.forName(entry.className());
                    var ctor = cls.getDeclaredConstructor(
                            IczIceSpikesObjectInstance.class, int.class, int.class);
                    ctor.setAccessible(true);
                    return (ObjectInstance) ctor.newInstance(
                            parent, entry.spawn().x(), entry.spawn().y());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                            "Failed to recreate dynamic rewind object " + entry.className(), e);
                }
            }
        };
    }

    /**
     * Codec for {@link MhzEndBossArenaHelperInstance}. The helper's only non-spawn
     * constructor argument is the long-lived
     * {@link com.openggf.game.sonic3k.events.Sonic3kMHZEvents} owner, reached
     * via the level event provider. The
     * role-discriminator scalars (role/spikeIndex/spikeTier/alternateSide) are
     * un-finaled and reapplied by the generic field capturer after recreate, so a
     * structurally-valid PILLAR placeholder is built here; position and role-derived
     * render/collision fields are non-final and reapplied too. If the events owner is
     * absent the helper is dropped (codec returns null).
     */
    private static DynamicObjectRewindCodec mhzEndBossArenaHelperCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == MhzEndBossArenaHelperInstance.class;
            }

            @Override
            public String className() {
                return MhzEndBossArenaHelperInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                if (!(context.objectServices().levelEventProvider()
                        instanceof com.openggf.game.sonic3k.Sonic3kLevelEventManager mgr)) {
                    return null;
                }
                com.openggf.game.sonic3k.events.Sonic3kMHZEvents events = mgr.getMhzEvents();
                if (events == null) {
                    return null;
                }
                return MhzEndBossArenaHelperInstance.pillar(events);
            }
        };
    }

    /**
     * Codec for {@link MhzEndBossHitProxyChild}. The proxy's only non-spawn-derivable
     * field is its final {@link MhzEndBossInstance} parent, which is relinked from the
     * live boss in {@code getActiveObjects()} (the boss is recreated earlier in spawn
     * order). The proxy's package-private constructor rebuilds its spawn and position
     * from the live parent via {@code refreshFromParent()}, so the captured spawn is
     * intentionally unused. If the boss is absent the proxy is dropped.
     */
    private static DynamicObjectRewindCodec mhzEndBossHitProxyCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == MhzEndBossHitProxyChild.class;
            }

            @Override
            public String className() {
                return MhzEndBossHitProxyChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                MhzEndBossInstance parent =
                        findLiveBossForRewind(context, MhzEndBossInstance.class);
                if (parent == null) {
                    return null;
                }
                return MhzEndBossHitProxyChild.forRewindRecreate(parent);
            }
        };
    }

    /**
     * Codec for {@link MhzEndBossWeatherVisualChild}. The visual child relinks its live
     * {@link MhzEndBossWeatherMachineChild} parent (recreated just before it in spawn
     * order, found by nearest captured spawn position when several are live) and
     * re-derives its {@code subtype} and {@code spark} discriminator from the captured
     * spawn (the spark bit is encoded into the spawn's {@code rawYWord} at construction).
     * The remaining animation scalars are reapplied by the generic field capturer; x/y
     * are re-derived from the parent each frame. If no parent is live the child is dropped.
     */
    private static DynamicObjectRewindCodec mhzEndBossWeatherVisualChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == MhzEndBossWeatherVisualChild.class;
            }

            @Override
            public String className() {
                return MhzEndBossWeatherVisualChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                MhzEndBossWeatherMachineChild parent = findNearestLiveInstance(
                        context, MhzEndBossWeatherMachineChild.class, entry.spawn());
                if (parent == null) {
                    return null;
                }
                return MhzEndBossWeatherVisualChild.forRewindRecreate(parent, entry.spawn());
            }
        };
    }

    // ---- Batch-6 relink codecs --------------------------------------------

    /**
     * Codec for the LBZ1 Knuckles-cutscene collapse child. Relinks the single live
     * {@link CutsceneKnucklesLbz1Instance} parent (a placed/persistent cutscene
     * singleton re-spawned via the placement path on restore, hence present in
     * getActiveObjects()). subtype is spawn-derivable and passed into the
     * constructor; motion/timer scalars are reapplied by the field capturer.
     */
    private static DynamicObjectRewindCodec cutsceneKnucklesLbz1CollapseChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CutsceneKnucklesLbz1CollapseChild.class;
            }

            @Override
            public String className() {
                return CutsceneKnucklesLbz1CollapseChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                CutsceneKnucklesLbz1Instance parent =
                        findLiveInstance(context, CutsceneKnucklesLbz1Instance.class);
                if (parent == null) {
                    return null;
                }
                return new CutsceneKnucklesLbz1CollapseChild(parent, entry.spawn().subtype());
            }
        };
    }

    /**
     * Codec for the LBZ1 Knuckles-cutscene range helper. Relinks the single live
     * {@link CutsceneKnucklesLbz1Instance} parent (a placed object re-spawned via
     * the placement path on restore, present in getActiveObjects()). Position is
     * spawn-derivable; the parent is passed into the constructor. Dropped if the
     * cutscene parent is no longer live.
     */
    private static DynamicObjectRewindCodec cutsceneKnucklesLbz1RangeHelperCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CutsceneKnucklesLbz1RangeHelper.class;
            }

            @Override
            public String className() {
                return CutsceneKnucklesLbz1RangeHelper.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                CutsceneKnucklesLbz1Instance parent =
                        findLiveInstance(context, CutsceneKnucklesLbz1Instance.class);
                if (parent == null) {
                    return null;
                }
                return new CutsceneKnucklesLbz1RangeHelper(
                        parent, entry.spawn().x(), entry.spawn().y());
            }
        };
    }

    /**
     * Codec for the AIZ1 intro Knuckles rock child. Relinks the live
     * {@link CutsceneKnucklesAiz1Instance} parent (recreated earlier in the
     * dynamic-object restore order via its exactSpawnCodec, so it is present in
     * getActiveObjects()). The child's {@code parent} field is final and passed
     * into the constructor; if no live parent exists the child is dropped.
     */
    private static DynamicObjectRewindCodec cutsceneKnucklesAiz1RockChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CutsceneKnucklesRockChild.class;
            }

            @Override
            public String className() {
                return CutsceneKnucklesRockChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                CutsceneKnucklesAiz1Instance parent =
                        findLiveInstance(context, CutsceneKnucklesAiz1Instance.class);
                if (parent == null) {
                    return null;
                }
                return new CutsceneKnucklesRockChild(entry.spawn(), parent);
            }
        };
    }

    /**
     * Codec for {@link CutsceneKnuxCnz2WallInstance}. The wall's only non-spawn
     * constructor argument is its live {@link CutsceneKnucklesCnz2AInstance}
     * cutscene parent (a placed object re-spawned from placement on restore).
     * Relinks to that live parent so the invisible blocking wall is rebuilt with
     * the correct owner; dropped if the parent is absent.
     */
    private static DynamicObjectRewindCodec cutsceneKnuxCnz2WallCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == CutsceneKnuxCnz2WallInstance.class;
            }

            @Override
            public String className() {
                return CutsceneKnuxCnz2WallInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                CutsceneKnucklesCnz2AInstance parent =
                        findLiveInstance(context, CutsceneKnucklesCnz2AInstance.class);
                if (parent == null) {
                    return null;
                }
                return new CutsceneKnuxCnz2WallInstance(entry.spawn(), parent);
            }
        };
    }

    /**
     * Codec for the MGZ2 level-collapse solids. The solid's scroll/delete suppliers
     * are bound to the live {@link Sonic3kMGZEvents} manager, so this relinks
     * through the event provider and rebuilds the solid via the owner-side
     * {@link Sonic3kMGZEvents#recreateCollapseSolidForRewind(ObjectSpawn)} factory.
     * Dropped if the MGZ event manager is gone (cannot rebind suppliers).
     */
    private static DynamicObjectRewindCodec mgz2CollapseSolidCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Mgz2LevelCollapseSolidInstance.class;
            }

            @Override
            public String className() {
                return Mgz2LevelCollapseSolidInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic3kMGZEvents mgz = findLiveMgzEventsForRewind(context);
                if (mgz == null) {
                    return null;
                }
                return mgz.recreateCollapseSolidForRewind(entry.spawn());
            }
        };
    }

    private static Sonic3kMGZEvents findLiveMgzEventsForRewind(
            DynamicObjectRecreateContext context) {
        if (context.objectServices().levelEventProvider()
                instanceof com.openggf.game.sonic3k.Sonic3kLevelEventManager mgr) {
            return mgr.getMgzEvents();
        }
        return null;
    }

    /**
     * Codec for the MHZ1 Knuckles-cutscene door. Relinks the single live
     * {@link Mhz1CutsceneButtonInstance} parent (a placed object recreated before
     * the dynamic-restore loop, present in getActiveObjects()). The door's
     * {@code parent} field is final and passed into the constructor; the door's
     * slide position/state are reapplied by the field capturer. Dropped if the
     * button parent is absent.
     */
    private static DynamicObjectRewindCodec mhz1CutsceneDoorCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Mhz1CutsceneDoorInstance.class;
            }

            @Override
            public String className() {
                return Mhz1CutsceneDoorInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Mhz1CutsceneButtonInstance parent =
                        findLiveInstance(context, Mhz1CutsceneButtonInstance.class);
                if (parent == null) {
                    return null;
                }
                return new Mhz1CutsceneDoorInstance(parent);
            }
        };
    }

    /**
     * Codec for {@link S3kSignpostStubChild}. The stub's only non-derivable
     * field is its live {@link S3kSignpostInstance} parent. The signpost is
     * spawned (and recreated) before the stub in spawn order, so it is present
     * in {@code getActiveObjects()} during the stub's recreate. The signpost is
     * effectively a singleton, so a plain type scan is unambiguous; if absent
     * the stub is dropped.
     */
    private static DynamicObjectRewindCodec signpostStubCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == S3kSignpostStubChild.class;
            }

            @Override
            public String className() {
                return S3kSignpostStubChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                S3kSignpostInstance parent = findLiveInstance(context, S3kSignpostInstance.class);
                if (parent == null) {
                    return null;
                }
                return new S3kSignpostStubChild(parent);
            }
        };
    }

    /**
     * Codec for {@link Sonic3kStarPostStarChild}. The orbiting star's only
     * non-derivable field is its live {@link Sonic3kStarPostObjectInstance}
     * parent; the star re-derives its orbit center from that parent in the
     * constructor. Several StarPosts may be live, so the parent is relinked by
     * nearest captured spawn position (the dummy spawn stores the parent's
     * center). StarPosts are layout objects recreated before the dynamic-object
     * restore loop, so the parent is present; if absent the star is dropped.
     */
    private static DynamicObjectRewindCodec starPostStarChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic3kStarPostStarChild.class;
            }

            @Override
            public String className() {
                return Sonic3kStarPostStarChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic3kStarPostObjectInstance parent = findNearestLiveInstance(
                        context, Sonic3kStarPostObjectInstance.class, entry.spawn());
                if (parent == null) {
                    return null;
                }
                return new Sonic3kStarPostStarChild(parent);
            }
        };
    }

    /**
     * Codec for {@link Sonic3kStarPostBonusStarChild}. The orbiting bonus star
     * re-derives its orbit center from its live
     * {@link Sonic3kStarPostObjectInstance} parent in the constructor, so the only
     * non-derivable state is (a) that parent link and (b) the enum {@code variant}
     * (the bonus-stage type chosen from the ring count at spawn). Several StarPosts
     * may be live, so the parent is relinked by nearest captured spawn position.
     * {@code variant} is no longer final, so the generic field capturer reapplies it
     * after recreate; a placeholder is passed to the constructor here. StarPosts are
     * layout objects recreated before the dynamic-object restore loop, so the parent
     * is present; if absent the star is dropped.
     */
    private static DynamicObjectRewindCodec starPostBonusStarChildCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic3kStarPostBonusStarChild.class;
            }

            @Override
            public String className() {
                return Sonic3kStarPostBonusStarChild.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic3kStarPostObjectInstance parent = findNearestLiveInstance(
                        context, Sonic3kStarPostObjectInstance.class, entry.spawn());
                if (parent == null) {
                    return null;
                }
                // angleOffset (0) and variant (placeholder) are reapplied by the
                // generic field capturer after recreate; centerX/centerY are
                // re-derived from the relinked parent in the constructor.
                return new Sonic3kStarPostBonusStarChild(parent, 0,
                        Sonic3kStarPostObjectInstance.BonusStarVariant.YELLOW);
            }
        };
    }

    /**
     * Codec for {@link Sonic3kSSEntryFlashObjectInstance}. The flash holds a final
     * {@link Sonic3kSSEntryRingObjectInstance} parent ring; while a flash exists the
     * ring is ENTERED with {@code isPersistent()/shouldStayActiveWhenRemembered()}
     * true and is a layout object recreated earlier in the restore loop, so it is
     * present in {@code getActiveObjects()}. Position is spawn-derivable; the scalar
     * gameplay-control fields (state, animIndex, waitTimer, ringDeleteTriggered) are
     * non-final and reapplied by the generic field capturer. If no live ring is
     * present the flash is dropped.
     */
    private static DynamicObjectRewindCodec ssEntryFlashCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == Sonic3kSSEntryFlashObjectInstance.class;
            }

            @Override
            public String className() {
                return Sonic3kSSEntryFlashObjectInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                Sonic3kSSEntryRingObjectInstance ring =
                        findLiveInstance(context, Sonic3kSSEntryRingObjectInstance.class);
                if (ring == null) {
                    return null;
                }
                ObjectSpawn spawn = entry.spawn();
                return new Sonic3kSSEntryFlashObjectInstance(ring, spawn.x(), spawn.y());
            }
        };
    }

    /**
     * Codec for an MHZ miniboss child relinked to the single live
     * {@link MhzMinibossInstance} (an {@link AbstractBossInstance} recreated before
     * the dynamic-object restore loop). The child's captured scalar state is
     * reapplied by the generic field capturer; a placeholder child index is passed
     * to the constructor. If the live boss is absent the child is dropped.
     */
    private static DynamicObjectRewindCodec mhzMinibossChildCodec(
            Class<? extends AbstractObjectInstance> type,
            BiFunction<MhzMinibossInstance, Integer, ? extends AbstractObjectInstance> factory) {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == type;
            }

            @Override
            public String className() {
                return type.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                MhzMinibossInstance boss = findLiveBossForRewind(context, MhzMinibossInstance.class);
                if (boss == null) {
                    return null;
                }
                return factory.apply(boss, 0);
            }
        };
    }

    /**
     * Codec for {@link MhzMinibossEscapeShardInstance}. The shard's only non-spawn
     * dependency is the live {@link MhzMinibossInstance} parent; position is taken
     * straight from the captured spawn, and all stateful scalar fields are reapplied
     * by the generic field capturer. If the live boss is absent the shard is dropped.
     */
    private static DynamicObjectRewindCodec mhzMinibossEscapeShardCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == MhzMinibossEscapeShardInstance.class;
            }

            @Override
            public String className() {
                return MhzMinibossEscapeShardInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                MhzMinibossInstance parent = findLiveBossForRewind(context, MhzMinibossInstance.class);
                if (parent == null) {
                    return null;
                }
                return new MhzMinibossEscapeShardInstance(
                        entry.spawn().x(), entry.spawn().y(), parent);
            }
        };
    }

    /**
     * Codec for {@link BuggernautBabyInstance}. The baby is relinked to the nearest
     * live {@link BuggernautBadnikInstance} parent by captured spawn position
     * (several buggernauts may be live). The parent is a layout-spawned badnik
     * recreated before the dynamic-object restore loop. The baby's
     * {@code @RewindTransient} parent link is re-supplied here; all other state is
     * reapplied by the generic field capturer. If no live Buggernaut exists the baby
     * is dropped (matching the codebase-wide drop-child-when-parent-absent rule).
     */
    private static DynamicObjectRewindCodec buggernautBabyCodec() {
        return new DynamicObjectRewindCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance.getClass() == BuggernautBabyInstance.class;
            }

            @Override
            public String className() {
                return BuggernautBabyInstance.class.getName();
            }

            @Override
            public ObjectInstance recreate(DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                BuggernautBadnikInstance parent = findNearestLiveInstance(
                        context, BuggernautBadnikInstance.class, entry.spawn());
                if (parent == null) {
                    return null;
                }
                return BuggernautBadnikInstance.recreateBabyForRewind(
                        entry.spawn(), entry.spawn().x(), entry.spawn().y(), parent);
            }
        };
    }

    @Override
    protected void registerDefaultFactories() {
        factories.put(Sonic3kObjectIds.MONITOR,
                (spawn, registry) -> new Sonic3kMonitorObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.PATH_SWAP, (spawn, registry) -> new Sonic3kPathSwapObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.AIZ_HOLLOW_TREE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL && currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                        return new MhzTwistedVineObjectInstance(spawn);
                    }
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new AizHollowTreeObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.COLLAPSING_PLATFORM,
                (spawn, registry) -> new Sonic3kCollapsingPlatformObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.AIZLRZ_ROCK,
                (spawn, registry) -> new AizLrzRockObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.AIZ_RIDE_VINE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL && currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                        return new MhzPulleyLiftObjectInstance(spawn);
                    }
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new AizRideVineObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.SPRING,
                (spawn, registry) -> new Sonic3kSpringObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.SPIKES,
                (spawn, registry) -> new Sonic3kSpikeObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.AIZ1_TREE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL && currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                        return new MhzCurledVineObjectInstance(spawn);
                    }
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new Aiz1TreeObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.AIZ1_ZIPLINE_PEG,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL && currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                        return new MhzStickyVineObjectInstance(spawn);
                    }
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new Aiz1ZiplinePegObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MHZ_SWING_BAR_HORIZONTAL,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL && currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                        return new MhzSwingBarHorizontalObjectInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
        factories.put(Sonic3kObjectIds.AIZ_GIANT_RIDE_VINE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL && currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                        return new MhzSwingBarVerticalObjectInstance(spawn);
                    }
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new AizGiantRideVineObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.LBZ_TUBE_ELEVATOR,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL && currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                        return new MhzSwingVineObjectInstance(spawn);
                    }
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new LbzTubeElevatorInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.BREAKABLE_WALL,
                (spawn, registry) -> new BreakableWallObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.TWISTED_RAMP,
                (spawn, registry) -> new Sonic3kTwistedRampObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.COLLAPSING_BRIDGE,
                (spawn, registry) -> new CollapsingBridgeObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.MHZ_MUSHROOM_PLATFORM,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL && currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                        return new MhzMushroomPlatformObjectInstance(spawn);
                    }
                    if (zoneSet == S3kZoneSet.S3KL && currentRomZoneId() == Sonic3kZoneIds.ZONE_LBZ) {
                        return new LbzMovingPlatformObjectInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
        factories.put(Sonic3kObjectIds.MHZ_MUSHROOM_PARACHUTE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL && currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                        return new MhzMushroomParachuteObjectInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
        factories.put(Sonic3kObjectIds.MHZ_MUSHROOM_CATAPULT,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL && currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                        return new MhzMushroomCatapultObjectInstance(spawn);
                    }
                    if (zoneSet == S3kZoneSet.S3KL && currentRomZoneId() == Sonic3kZoneIds.ZONE_LBZ) {
                        return new LbzExplodingTriggerInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
        factories.put(Sonic3kObjectIds.UPDRAFT,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL) {
                        return new UpdraftObjectInstance(spawn);
                    }
                    if (zoneSet == S3kZoneSet.S3KL && currentRomZoneId() == Sonic3kZoneIds.ZONE_LBZ) {
                        return new LbzTriggerBridgeInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
        factories.put(Sonic3kObjectIds.LBZ_PLAYER_LAUNCHER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new LbzPlayerLauncherInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.LBZ_FLAME_THROWER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new LbzFlameThrowerObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.LBZ_RIDE_GRAPPLE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new LbzRideGrappleInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.LBZ_CUP_ELEVATOR,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new LbzCupElevatorInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.LBZ_CUP_ELEVATOR_POLE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new LbzCupElevatorPoleInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.AUTOMATIC_TUNNEL,
                (spawn, registry) -> new AutomaticTunnelObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.MHZ_MUSHROOM_CAP,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL && currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                        return new MhzMushroomCapObjectInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
        factories.put(Sonic3kObjectIds.LBZ_ROLLING_DRUM,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new LbzRollingDrumInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.AUTO_SPIN,
                (spawn, registry) -> new AutoSpinObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.CORK_FLOOR,
                (spawn, registry) -> new CorkFloorObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.AIZ_FLIPPING_BRIDGE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new AizFlippingBridgeObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.AIZ_DRAW_BRIDGE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new AizDrawBridgeObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.SINKING_MUD,
                (spawn, registry) -> new SinkingMudObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.AIZ_COLLAPSING_LOG_BRIDGE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new AizCollapsingLogBridgeObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.AIZ_FALLING_LOG,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new AizFallingLogObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.AIZ_SPIKED_LOG,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new AizSpikedLogObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.AIZ_DISAPPEARING_FLOOR,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new AizDisappearingFloorObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.INVISIBLE_BLOCK,
                (spawn, registry) -> new Sonic3kInvisibleBlockObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.INVISIBLE_HURT_BLOCK_H,
                (spawn, registry) -> new Sonic3kInvisibleHurtBlockHObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.INVISIBLE_HURT_BLOCK_V,
                (spawn, registry) -> new Sonic3kInvisibleHurtBlockVObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.FLOATING_PLATFORM,
                (spawn, registry) -> new FloatingPlatformObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.MGZ_SWINGING_PLATFORM,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZSwingingPlatformObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZLBZ_SMASHING_PILLAR,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZLBZSmashingPillarObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZLBZ_SMASHING_PILLAR_ALT,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZLBZSmashingPillarObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.LBZ_ALARM,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new LbzAlarmObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZ_TWISTING_LOOP,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZTwistingLoopObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZ_TRIGGER_PLATFORM,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZTriggerPlatformObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZ_HEAD_TRIGGER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZHeadTriggerObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZ_MOVING_SPIKE_PLATFORM,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZMovingSpikePlatformObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZ_SWINGING_SPIKE_BALL,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZSwingingSpikeBallObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZ_DASH_TRIGGER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZDashTriggerObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZ_PULLEY,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZPulleyObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZ_TOP_PLATFORM,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZTopPlatformObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZ_TOP_LAUNCHER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MGZTopLauncherObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.BUMPER,
                (spawn, registry) -> {
                    if (currentRomZoneId() == Sonic3kZoneIds.ZONE_GLOWING_SPHERE) {
                        return new PachinkoBumperObjectInstance(spawn);
                    }
                    if (currentRomZoneId() == Sonic3kZoneIds.ZONE_CNZ) {
                        return new CnzBumperObjectInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), getCurrentZoneSet()));
                });
        factories.put(Sonic3kObjectIds.CNZ_TRIANGLE_BUMPER,
                (spawn, registry) -> currentRomZoneId() == Sonic3kZoneIds.ZONE_CNZ
                        ? new CnzTriangleBumperObjectInstance(spawn)
                        : new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), getCurrentZoneSet())));
        factories.put(Sonic3kObjectIds.BUBBLER,
                (spawn, registry) -> new BubblerObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.ICZ_BREAKABLE_WALL,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczBreakableWallObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.BUTTON,
                (spawn, registry) -> new Sonic3kButtonObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.CUTSCENE_BUTTON,
                (spawn, registry) -> {
                    // ROM: Obj_CutsceneButton dispatches on subtype via off_65C40
                    // (sonic3k.asm:133947): $00 -> loc_65C56, $02 -> loc_65C72,
                    // $04 -> loc_65C78 (CNZ water/geyser/flash), $06 -> loc_65CAC
                    // (CNZ vacuum tubes). The CNZ2 layout (Levels/CNZ/Object Pos/2.bin)
                    // places the first-encounter button at X=$1E00 with subtype $04
                    // and the second-encounter (vacuum-tube) button at X=$4780 with
                    // subtype $06. Route both CNZ2 cutscene button variants here.
                    if (currentRomZoneId() == Sonic3kZoneIds.ZONE_CNZ
                            && (spawn.subtype() == 4 || spawn.subtype() == 6)) {
                        return new Cnz2CutsceneButtonInstance(spawn);
                    }
                    if (currentRomZoneId() == Sonic3kZoneIds.ZONE_HCZ) {
                        return new Hcz2CutsceneButtonInstance(spawn);
                    }
                    return new S3kCutsceneButtonObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.STAR_POST,
                (spawn, registry) -> new Sonic3kStarPostObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.HCZ_TWISTING_LOOP,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZTwistingLoopObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_WATER_SPLASH,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZWaterSplashObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_WATER_DROP,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZWaterDropObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.TENSION_BRIDGE,
                (spawn, registry) -> new TensionBridgeObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.HCZ_WATER_RUSH,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZWaterRushObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_CGZ_FAN,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZCGZFanObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_LARGE_FAN,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZLargeFanObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_HAND_LAUNCHER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZHandLauncherObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_CONVEYOR_BELT,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZConveyorBeltObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_CONVEYOR_SPIKE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZConveyorSpikeObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_BLOCK,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZBlockObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_BALLOON,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzBalloonInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_CANNON,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzCannonInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_RISING_PLATFORM,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzRisingPlatformInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_TRAP_DOOR,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzTrapDoorInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_LIGHT_BULB,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzLightBulbInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_HOVER_FAN,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzHoverFanInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_CYLINDER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzCylinderInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_VACUUM_TUBE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzVacuumTubeInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_GIANT_WHEEL,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzGiantWheelInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_SPIRAL_TUBE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzSpiralTubeInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_BARBER_POLE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzBarberPoleObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_WIRE_CAGE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzWireCageObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_SPINNING_COLUMN,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZSpinningColumnObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_SNAKE_BLOCKS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZSnakeBlocksObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_FREEZER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczFreezerObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_PATH_FOLLOW_PLATFORM,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczPathFollowPlatformObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_CRUSHING_COLUMN,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczCrushingColumnObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_SEGMENT_COLUMN,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczSegmentColumnObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_SWINGING_PLATFORM,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczSwingingPlatformObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_STALAGTITE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczStalagtiteObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_ICE_CUBE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczIceCubeObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_ICE_SPIKES,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczIceSpikesObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_HARMFUL_ICE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczHarmfulIceObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_SNOW_PILE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczSnowPileObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_TENSION_PLATFORM,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczTensionPlatformObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_ICE_BLOCK,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczIceBlockObjectInstance(spawn);
                });
        factories.put(0x3C,
                (spawn, registry) -> new DoorObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.STILL_SPRITE,
                (spawn, registry) -> new StillSpriteInstance(spawn));
        factories.put(Sonic3kObjectIds.ANIMATED_STILL_SPRITE,
                (spawn, registry) -> new AnimatedStillSpriteInstance(spawn));
        factories.put(Sonic3kObjectIds.HCZ_BREAKABLE_BAR,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZBreakableBarObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_WATER_WALL,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HCZWaterWallObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.AIZ_FOREGROUND_PLANT,
                (spawn, registry) -> new AizForegroundPlantInstance(spawn));
        factories.put(Sonic3kObjectIds.HIDDEN_MONITOR,
                (spawn, registry) -> new S3kHiddenMonitorInstance(spawn));
        factories.put(Sonic3kObjectIds.SS_ENTRY_RING,
                (spawn, registry) -> new Sonic3kSSEntryRingObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.GUMBALL_MACHINE,
                (spawn, registry) -> new GumballMachineObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.GUMBALL_TRIANGLE_BUMPER,
                (spawn, registry) -> new GumballTriangleBumperObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.CNZ_WATER_LEVEL_CORK_FLOOR,
                (spawn, registry) -> new CnzWaterLevelCorkFloorInstance(spawn));
        factories.put(Sonic3kObjectIds.CNZ_WATER_LEVEL_BUTTON,
                (spawn, registry) -> new CnzWaterLevelButtonInstance(spawn));
        factories.put(Sonic3kObjectIds.PACHINKO_TRIANGLE_BUMPER,
                (spawn, registry) -> currentRomZoneId() == Sonic3kZoneIds.ZONE_GLOWING_SPHERE
                        ? new PachinkoTriangleBumperObjectInstance(spawn)
                        : new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), getCurrentZoneSet())));
        factories.put(Sonic3kObjectIds.PACHINKO_FLIPPER,
                (spawn, registry) -> currentRomZoneId() == Sonic3kZoneIds.ZONE_GLOWING_SPHERE
                        ? new PachinkoFlipperObjectInstance(spawn)
                        : new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), getCurrentZoneSet())));
        factories.put(Sonic3kObjectIds.PACHINKO_ENERGY_TRAP,
                (spawn, registry) -> currentRomZoneId() == Sonic3kZoneIds.ZONE_GLOWING_SPHERE
                        ? new PachinkoEnergyTrapObjectInstance(spawn)
                        : new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), getCurrentZoneSet())));
        factories.put(Sonic3kObjectIds.PACHINKO_PLATFORM,
                (spawn, registry) -> currentRomZoneId() == Sonic3kZoneIds.ZONE_GLOWING_SPHERE
                        ? new PachinkoPlatformObjectInstance(spawn)
                        : new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), getCurrentZoneSet())));
        factories.put(Sonic3kObjectIds.PACHINKO_ITEM_ORB,
                (spawn, registry) -> currentRomZoneId() == Sonic3kZoneIds.ZONE_GLOWING_SPHERE
                        ? new PachinkoItemOrbObjectInstance(spawn)
                        : new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), getCurrentZoneSet())));
        factories.put(Sonic3kObjectIds.PACHINKO_MAGNET_ORB,
                (spawn, registry) -> currentRomZoneId() == Sonic3kZoneIds.ZONE_GLOWING_SPHERE
                        ? new PachinkoMagnetOrbObjectInstance(spawn)
                        : new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), getCurrentZoneSet())));
        factories.put(Sonic3kObjectIds.GUMBALL_ITEM,
                (spawn, registry) -> new GumballItemObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.BLOOMINATOR,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new MadmoleBadnikInstance(spawn);
                    }
                    return new BloominatorBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.RHINOBOT,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new MushmeanieBadnikInstance(spawn);
                    }
                    return new RhinobotBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MONKEY_DUDE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new DragonflyBadnikInstance(spawn);
                    }
                    return new MonkeyDudeBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CATERKILLER_JR,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new ButterdroidBadnikInstance(spawn);
                    }
                    return new CaterkillerJrHeadInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.JAWZ,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        if (currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                            return new MhzEndBossInstance(spawn);
                        }
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new JawzBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.BLASTOID,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new BlastoidBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.BUGGERNAUT,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new BuggernautBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.TURBO_SPIKER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new TurboSpikerBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MEGA_CHOPPER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MegaChopperBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.POINDEXTER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new PoindexterBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.SPIKER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new SpikerBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.BUBBLES_BADNIK,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new BubblesBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MANTIS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MantisBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.TUNNELBOT,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new TunnelbotBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.AIZ_MINIBOSS_CUTSCENE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new CluckoidBadnikInstance(spawn);
                    }
                    return new AizMinibossCutsceneInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.AIZ_MINIBOSS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        if (currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                            return new MhzMinibossTreeInstance(spawn);
                        }
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new AizMinibossInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.AIZ_END_BOSS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        if (currentRomZoneId() == Sonic3kZoneIds.ZONE_MHZ) {
                            return new MhzMinibossInstance(spawn);
                        }
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new AizEndBossInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_MINIBOSS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HczMinibossInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.HCZ_END_BOSS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new HczEndBossInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_MINIBOSS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzMinibossInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CNZ_END_BOSS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CnzEndBossInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CLAMER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new ClamerObjectInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.SPARKLE,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new SparkleBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.BATBOT,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new BatbotBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.PENGUINATOR,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new PenguinatorBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.STAR_POINTER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new StarPointerBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.SNALE_BLASTER,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new SnaleBlasterBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ORBINAUT,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new OrbinautBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.RIBOT,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new RibotBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.CORKEY,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new CorkeyBadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.FLYBOT_767,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new Flybot767BadnikInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_MINIBOSS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczMinibossInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.ICZ_END_BOSS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new IczEndBossInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZ_MINIBOSS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MgzMinibossInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MGZ_END_BOSS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet != S3kZoneSet.S3KL) {
                        return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                    }
                    return new MgzEndBossInstance(spawn);
                });
        factories.put(Sonic3kObjectIds.MHZ1_CUTSCENE_KNUCKLES,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL) {
                        return new Mhz1CutsceneKnucklesInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
        factories.put(Sonic3kObjectIds.MHZ1_CUTSCENE_BUTTON,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.SKL) {
                        return new Mhz1CutsceneButtonInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
        factories.put(Sonic3kObjectIds.CUTSCENE_KNUCKLES,
                (spawn, registry) -> {
                    // ROM: Obj_CutsceneKnuckles uses subtype as longword index into
                    // CutsceneKnuckles_Index: 0=AIZ1, 4=AIZ2, 8=HCZ2, 12=CNZ2A, 16=CNZ2B, etc.
                    int subtype = spawn.subtype();
                    if (subtype == 8) {
                        return new CutsceneKnucklesHcz2Instance(spawn);
                    }
                    if (subtype == 12) {
                        return new CutsceneKnucklesCnz2AInstance(spawn);
                    }
                    if (subtype == 16) {
                        return new CutsceneKnucklesCnz2BInstance(spawn);
                    }
                    if (subtype == 0x14) {
                        return new CutsceneKnucklesLbz1Instance(spawn);
                    }
                    if (subtype == 0x1C) {
                        return new CutsceneKnucklesMhz1Instance(spawn);
                    }
                    if (subtype == 0x20) {
                        return new CutsceneKnucklesMhz2Instance(spawn);
                    }
                    if (subtype == 0x30) {
                        return new CutsceneKnucklesSkIntroInstance(spawn);
                    }
                    // Default: AIZ2 variant (handles subtypes 0 and 4)
                    return new CutsceneKnucklesAiz2Instance(spawn);
                });
        factories.put(Sonic3kObjectIds.LBZ1_ROBOTNIK,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.S3KL && currentRomZoneId() == Sonic3kZoneIds.ZONE_LBZ) {
                        return new Lbz1RobotnikEventController(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
        factories.put(Sonic3kObjectIds.LBZ_MINIBOSS,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.S3KL && currentRomZoneId() == Sonic3kZoneIds.ZONE_LBZ) {
                        return new LbzMinibossInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
        factories.put(Sonic3kObjectIds.LBZ_MINIBOSS_BOX,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.S3KL && currentRomZoneId() == Sonic3kZoneIds.ZONE_LBZ) {
                        return new LbzMinibossBoxInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
        factories.put(Sonic3kObjectIds.LBZ_MINIBOSS_BOX_KNUX,
                (spawn, registry) -> {
                    S3kZoneSet zoneSet = getCurrentZoneSet();
                    if (zoneSet == S3kZoneSet.S3KL && currentRomZoneId() == Sonic3kZoneIds.ZONE_LBZ) {
                        return new LbzMinibossBoxKnuxInstance(spawn);
                    }
                    return new PlaceholderObjectInstance(spawn, getPrimaryName(spawn.objectId(), zoneSet));
                });
    }

    private S3kZoneSet getCurrentZoneSet() {
        int romZoneId = currentRomZoneId();
        if (romZoneId < 0) {
            return S3kZoneSet.S3KL;
        }
        return S3kZoneSet.forZone(romZoneId);
    }

    /**
     * Returns the object name for the given zone set.
     * For S3KL (zones 0-6), delegates to {@link #getPrimaryName(int)}.
     * For SKL (zones 7-13), uses the SK Set 2 pointer table names.
     */
    public String getPrimaryName(int objectId, S3kZoneSet zoneSet) {
        if (zoneSet == S3kZoneSet.SKL) {
            return getSklName(objectId);
        }
        return getPrimaryName(objectId);
    }

    /**
     * Returns the object name from the SK Set 1 pointer table (S3KL).
     * Names match the disassembly label with the {@code Obj_} prefix stripped.
     * Used for zones 0-6 (AIZ, HCZ, MGZ, CNZ, FBZ, ICZ, LBZ).
     */
    @Override
    public String getPrimaryName(int objectId) {
        return switch (objectId) {
            case 0x00 -> "Ring";
            case 0x01 -> "Monitor";
            case 0x02 -> "PathSwap";
            case 0x03 -> "AIZHollowTree";
            case 0x04 -> "CollapsingPlatform";
            case 0x05 -> "AIZLRZEMZRock";
            case 0x06 -> "AIZRideVine";
            case 0x07 -> "Spring";
            case 0x08 -> "Spikes";
            case 0x09 -> "AIZ1Tree";
            case 0x0A -> "AIZ1ZiplinePeg";
            case 0x0C -> "AIZGiantRideVine";
            case 0x0D -> "BreakableWall";
            case 0x0E -> "TwistedRamp";
            case 0x0F -> "CollapsingBridge";
            case 0x10 -> "LBZTubeElevator";
            case 0x11 -> "LBZMovingPlatform";
            case 0x12 -> "LBZUnusedElevator";
            case 0x13 -> "LBZExplodingTrigger";
            case 0x14 -> "LBZTriggerBridge";
            case 0x15 -> "LBZPlayerLauncher";
            case 0x16 -> "LBZFlameThrower";
            case 0x17 -> "LBZRideGrapple";
            case 0x18 -> "LBZCupElevator";
            case 0x19 -> "LBZCupElevatorPole";
            case 0x1A -> "LBZUnusedTiltingBridge";
            case 0x1B -> "LBZPipePlug";
            case 0x1D -> "LBZUnusedBarPlatform";
            case 0x1E -> "LBZSpinLauncher";
            case 0x1F -> "LBZLoweringGrapple";
            case 0x20 -> "MGZLBZSmashingPillar";
            case 0x21 -> "LBZGateLaser";
            case 0x22 -> "LBZAlarm";
            case 0x23 -> "LBZUnusedForceFall";
            case 0x24 -> "AutomaticTunnel";
            case 0x26 -> "AutoSpin";
            case 0x27 -> "S2LavaMarker";
            case 0x28 -> "InvisibleBlock";
            case 0x29 -> "AIZDisappearingFloor";
            case 0x2A -> "CorkFloor";
            case 0x2B -> "AIZFlippingBridge";
            case 0x2C -> "AIZCollapsingLogBridge";
            case 0x2D -> "AIZFallingLog";
            case 0x2E -> "AIZSpikedLog";
            case 0x2F -> "StillSprite";
            case 0x30 -> "AnimatedStillSprite";
            case 0x31 -> "LBZRollingDrum";
            case 0x32 -> "AIZDrawBridge";
            case 0x33 -> "Button";
            case 0x34 -> "StarPost";
            case 0x35 -> "AIZForegroundPlant";
            case 0x36 -> "HCZBreakableBar";
            case 0x37 -> "HCZWaterRush";
            case 0x38 -> "HCZCGZFan";
            case 0x39 -> "HCZLargeFan";
            case 0x3A -> "HCZHandLauncher";
            case 0x3B -> "HCZWaterWall";
            case 0x3C -> "Door";
            case 0x3D -> "RetractingSpring";
            case 0x3E -> "HCZConveyorBelt";
            case 0x3F -> "HCZConveyorSpike";
            case 0x40 -> "HCZBlock";
            case 0x41 -> "CNZBalloon";
            case 0x42 -> "CNZCannon";
            case 0x43 -> "CNZRisingPlatform";
            case 0x44 -> "CNZTrapDoor";
            case 0x45 -> "CNZLightBulb";
            case 0x46 -> "CNZHoverFan";
            case 0x47 -> "CNZCylinder";
            case 0x48 -> "CNZVacuumTube";
            case 0x49 -> "CNZGiantWheel";
            case 0x4A -> "Bumper";
            case 0x4B -> "CNZTriangleBumpers";
            case 0x4C -> "CNZSpiralTube";
            case 0x4D -> "CNZBarberPoleSprite";
            case 0x4E -> "CNZWireCage";
            case 0x4F -> "SinkingMud";
            case 0x50 -> "MGZTwistingLoop";
            case 0x51 -> "FloatingPlatform";
            case 0x52 -> "MGZSmashingPillar";
            case 0x53 -> "MGZSwingingPlatform";
            case 0x54 -> "Bubbler";
            case 0x55 -> "MGZHeadTrigger";
            case 0x56 -> "MGZMovingSpikePlatform";
            case 0x57 -> "MGZTriggerPlatform";
            case 0x58 -> "MGZSwingingSpikeBall";
            case 0x59 -> "MGZDashTrigger";
            case 0x5A -> "MGZPulley";
            case 0x5B -> "MGZTopPlatform";
            case 0x5C -> "MGZTopLauncher";
            case 0x5D -> "CGZTriangleBumpers";
            case 0x5E -> "CGZBladePlatform";
            case 0x5F -> "2PRetractingSpring";
            case 0x60 -> "BPZElephantBlock";
            case 0x61 -> "BPZBalloon";
            case 0x62 -> "DPZDisolvingSandBar";
            case 0x63 -> "DPZButton";
            case 0x64 -> "2PItem";
            case 0x65 -> "2PGoalMarker";
            case 0x66 -> "EMZDripper";
            case 0x67 -> "HCZSnakeBlocks";
            case 0x68 -> "HCZSpinningColumn";
            case 0x69 -> "HCZTwistingLoop";
            case 0x6A -> "InvisibleHurtBlockH";
            case 0x6B -> "InvisibleHurtBlockV";
            case 0x6C -> "TensionBridge";
            case 0x6D -> "HCZWaterSplash";
            case 0x6E -> "WaterDrop";
            case 0x6F -> "FBZWireCage";
            case 0x70 -> "FBZWireCageStationary";
            case 0x71 -> "FBZFloatingPlatform";
            case 0x72 -> "FBZChainLink";
            case 0x73 -> "FBZMagneticSpikeBall";
            case 0x74 -> "FBZMagneticPlatform";
            case 0x75 -> "FBZSnakePlatform";
            case 0x76 -> "FBZBentPipe";
            case 0x77 -> "FBZRotatingPlatform";
            case 0x78 -> "FBZDEZPlayerLauncher";
            case 0x79 -> "FBZDisappearingPlatform";
            case 0x7A -> "FBZScrewDoor";
            case 0x7B -> "FBZSpinningPole";
            case 0x7C -> "FBZPropeller";
            case 0x7D -> "FBZPiston";
            case 0x7E -> "FBZPlatformBlocks";
            case 0x7F -> "FBZMissileLauncher";
            case 0x80 -> "HiddenMonitor";
            case 0x81 -> "EggCapsule";
            case 0x82 -> "CutsceneKnuckles";
            case 0x83 -> "CutsceneButton";
            case 0x84 -> "AIZPlaneIntro";
            case 0x85 -> "SSEntryRing";
            case 0x86 -> "GumballMachine";
            case 0x87 -> "GumballTriangleBumper";
            case 0x88 -> "CNZWaterLevelCorkFloor";
            case 0x89 -> "CNZWaterLevelButton";
            case 0x8A -> "FBZExitHall";
            case 0x8B -> "SpriteMask";
            case 0x8C -> "Bloominator";
            case 0x8D -> "Rhinobot";
            case 0x8E -> "MonkeyDude";
            case 0x8F -> "CaterKillerJr";
            case 0x90 -> "AIZMinibossCutscene";
            case 0x91 -> "AIZMiniboss";
            case 0x92 -> "AIZEndBoss";
            case 0x93 -> "Jawz";
            case 0x94 -> "Blastoid";
            case 0x95 -> "Buggernaut";
            case 0x96 -> "TurboSpiker";
            case 0x97 -> "MegaChopper";
            case 0x98 -> "Poindexter";
            case 0x99 -> "HCZMiniboss";
            case 0x9A -> "HCZEndBoss";
            case 0x9B -> "BubblesBadnik";
            case 0x9C -> "Spiker";
            case 0x9D -> "Mantis";
            case 0x9E -> "Tunnelbot";
            case 0x9F -> "MGZMiniboss";
            case 0xA0 -> "MGZ2DrillingRobotnik";
            case 0xA1 -> "MGZEndBoss";
            case 0xA2 -> "MGZEndBossKnux";
            case 0xA3 -> "Clamer";
            case 0xA4 -> "Sparkle";
            case 0xA5 -> "Batbot";
            case 0xA6 -> "CNZMiniboss";
            case 0xA7 -> "CNZEndBoss";
            case 0xA8 -> "Blaster";
            case 0xA9 -> "TechnoSqueek";
            case 0xAA -> "FBZMiniboss";
            case 0xAB -> "FBZ2Subboss";
            case 0xAC -> "FBZEndBoss";
            case 0xAD -> "Penguinator";
            case 0xAE -> "StarPointer";
            case 0xAF -> "ICZCrushingColumn";
            case 0xB0 -> "ICZPathFollowPlatform";
            case 0xB1 -> "ICZBreakableWall";
            case 0xB2 -> "ICZFreezer";
            case 0xB3 -> "ICZSegmentColumn";
            case 0xB4 -> "ICZSwingingPlatform";
            case 0xB5 -> "ICZStalagtite";
            case 0xB6 -> "ICZIceCube";
            case 0xB7 -> "ICZIceSpikes";
            case 0xB8 -> "ICZHarmfulIce";
            case 0xB9 -> "ICZSnowPile";
            case 0xBA -> "ICZTensionPlatform";
            case 0xBB -> "ICZIceBlock";
            case 0xBC -> "ICZMiniboss";
            case 0xBD -> "ICZEndBoss";
            case 0xBE -> "SnaleBlaster";
            case 0xBF -> "Ribot";
            case 0xC0 -> "Orbinaut";
            case 0xC1 -> "Corkey";
            case 0xC2 -> "Flybot767";
            case 0xC3 -> "LBZ1Robotnik";
            case 0xC4 -> "LBZMinibossBox";
            case 0xC5 -> "LBZMinibossBoxKnux";
            case 0xC6 -> "LBZ2RobotnikShip";
            case 0xC8 -> "LBZKnuxPillar";
            case 0xC9 -> "LBZMiniboss";
            case 0xCA -> "LBZFinalBoss1";
            case 0xCB -> "LBZEndBoss";
            case 0xCC -> "LBZFinalBoss2";
            case 0xCD -> "LBZFinalBossKnux";
            case 0xCE -> "FBZExitDoor";
            case 0xCF -> "FBZEggPrison";
            case 0xD0 -> "FBZSpringPlunger";
            case 0xE0 -> "FBZWallMissile";
            case 0xE1 -> "FBZMine";
            case 0xE2 -> "FBZElevator";
            case 0xE3 -> "FBZTrapSpring";
            case 0xE4 -> "FBZFlamethrower";
            case 0xE5 -> "FBZSpiderCrane";
            case 0xE6 -> "PachinkoTriangleBumper";
            case 0xE7 -> "PachinkoFlipper";
            case 0xE8 -> "PachinkoEnergyTrap";
            case 0xE9 -> "PachinkoInvisibleUnknown";
            case 0xEA -> "PachinkoPlatform";
            case 0xEB -> "GumballItem";
            case 0xEC -> "PachinkoMagnetOrb";
            case 0xED -> "PachinkoItemOrb";
            case 0xFF -> "FBZMagneticPendulum";
            default -> String.format("S3K_Obj_%02X", objectId & 0xFF);
        };
    }

    /**
     * Returns the object name from the SK Set 2 pointer table (SKL).
     * Used for zones 7-13 (MHZ, SOZ, LRZ, SSZ, DEZ, DDZ).
     * Shared objects (Ring, Monitor, Spring, etc.) return the same name as S3KL.
     */
    private String getSklName(int objectId) {
        return switch (objectId) {
            case 0x00 -> "Ring";
            case 0x01 -> "Monitor";
            case 0x02 -> "PathSwap";
            case 0x03 -> "MHZTwistedVine";
            case 0x04 -> "CollapsingPlatform";
            case 0x05 -> "AIZLRZEMZRock";
            case 0x06 -> "MHZPulleyLift";
            case 0x07 -> "Spring";
            case 0x08 -> "Spikes";
            case 0x09 -> "MHZCurledVine";
            case 0x0A -> "MHZStickyVine";
            case 0x0B -> "MHZSwingBarHorizontal";
            case 0x0C -> "MHZSwingBarVertical";
            case 0x0D -> "BreakableWall";
            case 0x0E -> "TwistedRamp";
            case 0x0F -> "CollapsingBridge";
            case 0x10 -> "MHZSwingVine";
            case 0x11 -> "MHZMushroomPlatform";
            case 0x12 -> "MHZMushroomParachute";
            case 0x13 -> "MHZMushroomCatapult";
            case 0x14 -> "Updraft";
            case 0x15 -> "LRZCorkscrew";
            case 0x16 -> "LRZWallRide";
            case 0x17 -> "LRZSinkingRock";
            case 0x18 -> "LRZFallingSpike";
            case 0x19 -> "LRZDoor";
            case 0x1A -> "LRZBigDoor";
            case 0x1B -> "LRZFireballLauncher";
            case 0x1C -> "LRZButtonHorizontal";
            case 0x1D -> "LRZShootingTrigger";
            case 0x1E -> "LRZDashElevator";
            case 0x1F -> "LRZLavaFall";
            case 0x20 -> "LRZSwingingSpikeBall";
            case 0x21 -> "LRZSmashingSpikePlatform";
            case 0x22 -> "LRZSpikeBall";
            case 0x23 -> "MHZMushroomCap";
            case 0x24 -> "AutomaticTunnel";
            case 0x25 -> "LRZChainedPlatforms";
            case 0x26 -> "AutoSpin";
            case 0x27 -> "S2LavaMarker";
            case 0x28 -> "InvisibleBlock";
            case 0x29 -> "LRZFlameThrower";
            case 0x2A -> "CorkFloor";
            case 0x2B -> "LRZOrbitingSpikeBallH";
            case 0x2C -> "LRZOrbitingSpikeBallV";
            case 0x2D -> "LRZSolidMovingPlatforms";
            case 0x2E -> "LRZSolidRock";
            case 0x2F -> "StillSprite";
            case 0x30 -> "AnimatedStillSprite";
            case 0x31 -> "LRZCollapsingBridge";
            case 0x32 -> "LRZTurbineSprites";
            case 0x33 -> "Button";
            case 0x34 -> "StarPost";
            case 0x35 -> "AIZForegroundPlant";
            case 0x36 -> "HCZBreakableBar";
            case 0x37 -> "LRZSpikeBallLauncher";
            case 0x38 -> "SOZQuicksand";
            case 0x39 -> "SOZSpawningSandBlocks";
            case 0x3A -> "SOZPathSwap";
            case 0x3B -> "SOZLoopFallthrough";
            case 0x3C -> "Door";
            case 0x3D -> "RetractingSpring";
            case 0x3E -> "SOZPushableRock";
            case 0x3F -> "SOZSpringVine";
            case 0x40 -> "SOZRisingSandWall";
            case 0x41 -> "SOZLightSwitch";
            case 0x42 -> "SOZFloatingPillar";
            case 0x43 -> "SOZSwingingPlatform";
            case 0x44 -> "SOZBreakableSandRock";
            case 0x45 -> "SOZPushSwitch";
            case 0x46 -> "SOZDoor";
            case 0x47 -> "SOZSandCork";
            case 0x48 -> "SOZRapelWire";
            case 0x49 -> "SOZSolidSprites";
            case 0x4A -> "DEZFloatingPlatform";
            case 0x4B -> "DEZTiltingBridge";
            case 0x4C -> "DEZHangCarrier";
            case 0x4D -> "DEZTorpedoLauncher";
            case 0x4E -> "DEZLiftPad";
            case 0x4F -> "DEZStaircase";
            case 0x50 -> "DEZConveyorBelt";
            case 0x51 -> "FloatingPlatform";
            case 0x52 -> "DEZLightning";
            case 0x53 -> "DEZConveyorPad";
            case 0x54 -> "Bubbler";
            case 0x55 -> "DEZEnergyBridge";
            case 0x56 -> "DEZEnergyBridgeCurved";
            case 0x57 -> "DEZTunnelLauncher";
            case 0x58 -> "DEZGravitySwitch";
            case 0x59 -> "DEZTeleporter";
            case 0x5A -> "DEZGravityTube";
            case 0x5B -> "DEZGravitySwap";
            case 0x5C -> "DEZGravityHub";
            case 0x5D -> "DEZRetractingSpring";
            case 0x5E -> "DEZHoverMachine";
            case 0x5F -> "DEZGravityRoom";
            case 0x60 -> "DEZBumperWall";
            case 0x61 -> "DEZGravityPuzzle";
            case 0x6A -> "InvisibleHurtBlockH";
            case 0x6B -> "InvisibleHurtBlockV";
            case 0x6C -> "TensionBridge";
            case 0x6D -> "InvisibleShockBlock";
            case 0x6E -> "InvisibleLavaBlock";
            case 0x74 -> "SSZRetractingSpring";
            case 0x75 -> "SSZSwingingCarrier";
            case 0x76 -> "SSZRotatingPlatform";
            case 0x77 -> "SSZCutsceneBridge";
            case 0x78 -> "FBZDEZPlayerLauncher";
            case 0x79 -> "SSZHPZTeleporter";
            case 0x7A -> "SSZElevatorBar";
            case 0x7B -> "SSZCollapsingBridgeDiagonal";
            case 0x7C -> "SSZCollapsingBridge";
            case 0x7D -> "SSZBouncyCloud";
            case 0x7E -> "SSZCollapsingColumn";
            case 0x7F -> "SSZFloatingPlatform";
            case 0x80 -> "HiddenMonitor";
            case 0x81 -> "EggCapsule";
            case 0x82 -> "CutsceneKnuckles";
            case 0x83 -> "CutsceneButton";
            case 0x84 -> "AIZPlaneIntro";
            case 0x85 -> "SSEntryRing";
            case 0x86 -> "GumballMachine";
            case 0x87 -> "GumballTriangleBumper";
            case 0x88 -> "CNZWaterLevelCorkFloor";
            case 0x89 -> "CNZWaterLevelButton";
            case 0x8A -> "FBZExitHall";
            case 0x8B -> "SpriteMask";
            case 0x8C -> "Madmole";
            case 0x8D -> "Mushmeanie";
            case 0x8E -> "Dragonfly";
            case 0x8F -> "Butterdroid";
            case 0x90 -> "Cluckoid";
            case 0x91 -> "MHZMinibossTree";
            case 0x92 -> "MHZMiniboss";
            case 0x93 -> "MHZEndBoss";
            case 0x94 -> "Skorp";
            case 0x95 -> "Sandworm";
            case 0x96 -> "Rockn";
            case 0x97 -> "SOZMiniboss";
            case 0x98 -> "SOZEndBoss";
            case 0x99 -> "Fireworm";
            case 0x9A -> "Iwamodoki";
            case 0x9B -> "Toxomister";
            case 0x9C -> "LRZRockCrusher";
            case 0x9D -> "LRZMiniboss";
            case 0x9E -> "LRZ3Autoscroll";
            case 0xA0 -> "EggRobo";
            case 0xA1 -> "SSZGHZBoss";
            case 0xA2 -> "SSZMTZBoss";
            case 0xA3 -> "SSZEndBoss";
            case 0xA4 -> "Spikebonker";
            case 0xA5 -> "Chainspike";
            case 0xA6 -> "DEZMiniboss";
            case 0xA7 -> "DEZEndBoss";
            case 0xA8 -> "MHZ1CutsceneKnuckles";
            case 0xA9 -> "MHZ1CutsceneButton";
            case 0xAA -> "Hyudoro";
            case 0xAB -> "SOZCapsuleHyudoro";
            case 0xAC -> "SOZCapsule";
            case 0xAD -> "LRZ3Platform";
            case 0xAE -> "LRZ2CutsceneKnuckles";
            case 0xAF -> "SSZCutsceneButton";
            case 0xB0 -> "HPZMasterEmerald";
            case 0xB1 -> "HPZPaletteControl";
            case 0xB2 -> "KnuxFinalBossCrane";
            case 0xB3 -> "StartNewLevel";
            case 0xB4 -> "HPZSuperEmerald";
            case 0xB5 -> "HPZSSEntryControl";
            case 0xB6 -> "DDZEndBoss";
            case 0xB7 -> "DDZAsteroid";
            case 0xB8 -> "DDZMissile";
            default -> String.format("S3K_Obj_%02X", objectId & 0xFF);
        };
    }
}

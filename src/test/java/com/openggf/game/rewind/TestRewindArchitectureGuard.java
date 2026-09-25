package com.openggf.game.rewind;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class TestRewindArchitectureGuard {
    private static final Path MAIN_ROOT = Path.of("src/main/java");

    private static final List<Path> OBJECT_SOURCE_ROOTS = List.of(
            Path.of("src/main/java/com/openggf/level/objects"),
            Path.of("src/main/java/com/openggf/level/rings"),
            Path.of("src/main/java/com/openggf/game/sonic1"),
            Path.of("src/main/java/com/openggf/game/sonic2"),
            Path.of("src/main/java/com/openggf/game/sonic3k")
    );

    private static final Pattern CAPTURE_OVERRIDE = Pattern.compile(
            "\\bpublic\\s+\\S*PerObjectRewindSnapshot\\s+captureRewindState\\s*\\(");
    private static final Pattern RESTORE_OVERRIDE = Pattern.compile(
            "\\bpublic\\s+void\\s+restoreRewindState\\s*\\(");

    private static final Map<String, Integer> OBJECT_REWIND_OVERRIDE_BASELINE = Map.ofEntries(
            // Exact parent id plus a null tombstone for the one-update retired-owner tail;
            // generic strict object refs cannot capture the already-unregistered parent.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ChainspikeBadnikInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ChainspikeBadnikInstance.java#restoreRewindState", 1),
            // Sky Sanctuary arrival and cutscene graph. Each override exists for one of two
            // reasons the generic schema cannot cover: a cross-object SST link the ROM keeps as
            // a word ($3C the beam, $20 the cutscene Knuckles) which must survive as an
            // ObjectRefId, or a Gradual_SwingOffset/raw-animation holder whose RewindStateful
            // value is captured as a typed extra. Restore equality and forward replay are
            // covered by TestS3kSszArrivalHeadless and TestS3kSszKnucklesBridgeHeadless.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszArrivalControllerObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszArrivalControllerObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszTailsArrivalHelperObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszTailsArrivalHelperObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszCutsceneKnucklesSpawnerObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszCutsceneKnucklesSpawnerObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesSszInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesSszInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszDeathEggSmallObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszDeathEggSmallObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszCutsceneBridgeObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszCutsceneBridgeObjectInstance.java#restoreRewindState", 1),
            // Obj_SSZCollapsingColumn's debris keeps the same cross-object SST link: loc_44BF8
            // reads its parent through $2E(a0) every frame, both to hang from it and to report
            // back with subq.b #1,routine(a1), so the reference must survive a restore as an
            // ObjectRefId. Restore equality is covered by TestS3kSszTraversalPlatforms and the
            // per-object round trip in TestEveryObjectRewindRoundTrip.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszCollapsingColumnDebrisObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszCollapsingColumnDebrisObjectInstance.java#restoreRewindState", 1),
            // Obj_SSZGHZBoss and its three child shapes. Same triage, four more cross-object SST
            // links: CreateChild9_TreeList parents each chain link to the link before it rather
            // than to the ship, the ship keeps that list plus the ChildObjDat_7A69E emitter, and
            // Child1_MakeMechaHead's head reads the ship every frame through
            // Refresh_ChildPositionAdjusted. None of them can be rebuilt from the spawn alone, so
            // each captures its references as typed ObjectRefId sidecars. Restore equality is
            // covered by TestS3kSszGhzArenaHeadless and the per-object round trip.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszGhzBossObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszGhzBossObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszGhzBossChainLinkChild.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszGhzBossChainLinkChild.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszGhzBossShieldChild.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszGhzBossShieldChild.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMechaSonicHeadChild.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMechaSonicHeadChild.java#restoreRewindState", 1),
            // Obj_SSZMTZBoss and its two child shapes. The ship carries the 16.16 SSZ_MTZ_boss
            // position/velocity pair, the three arm bytes and the two-level $26/$32 dispatch, none
            // of which is a spawn decode; each orb holds the ship through a typed ObjectRefId
            // sidecar and carries its own three orbit angles; and the laser child keeps the shot's
            // own 16.16 X, its hold counter and the flip it copied off the ship at setup. Restore
            // equality is covered by TestS3kSszMtzArenaHeadless and the per-object round trip.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMtzBossObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMtzBossObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMtzBossOrbChild.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMtzBossOrbChild.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMtzBossLaserChild.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMtzBossLaserChild.java#restoreRewindState", 1),
            // Triaged: Mecha Sonic's act-1 graph is twenty-one routines over a 16.16 position, a
            // $34 callback, a $38 flag byte and a raw-animation cursor addressed by ROM address,
            // none of which the generic capture reaches; and the after-image children hold the
            // boss and the subtype sub_7D236 turns into their offset and priority. Restore
            // equality is covered by TestS3kSszMechaSpawnHeadless and the per-object round trip.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMechaSonicObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMechaSonicObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMechaSonicTrailChild.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMechaSonicTrailChild.java#restoreRewindState", 1),
            // Triaged: loc_7D056 is a three-field countdown object -- the $2E the handover gate
            // pre-decrements, the routine sub_868F8 writes, and whether the results allocation
            // has happened -- none of which the generic capture reaches. Restore equality is
            // covered by TestS3kSszMechaSpawnHeadless.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMechaSonicActEndObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/SszMechaSonicActEndObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/EggRoboGunArmChildInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/EggRoboGunArmChildInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/EggRoboJetFlameChildInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/EggRoboJetFlameChildInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszRotatingPlatformObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszRotatingPlatformObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszSwingingCarrierObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszSwingingCarrierObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszSwingingCarrierArcObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszSwingingCarrierArcObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszSwingingCarrierBarObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszSwingingCarrierBarObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/level/objects/AbstractObjectInstance.java#captureRewindState", 2),
            Map.entry("src/main/java/com/openggf/level/objects/AbstractObjectInstance.java#restoreRewindState", 2),
            // Explosion construction factories cannot be derived from placement or services.
            // The typed extra preserves their exact configuration and all explosion scalars;
            // context-aware overrides retain generic capture for subtype state. Focused
            // recreation tests cover custom child order, once-only effects and S1 subtype state.
            Map.entry("src/main/java/com/openggf/level/objects/ExplosionObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/level/objects/ExplosionObjectInstance.java#restoreRewindState", 1),
            // Shared badnik base keeps the no-arg compatibility overrides and
            // adds context-aware overloads so default badnik compact sidecars can
            // resolve captured player/object references through the restore table.
            Map.entry("src/main/java/com/openggf/level/objects/AbstractBadnikInstance.java#captureRewindState", 2),
            Map.entry("src/main/java/com/openggf/level/objects/AbstractBadnikInstance.java#restoreRewindState", 2),
            Map.entry("src/main/java/com/openggf/level/objects/AbstractMonitorObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/level/objects/AbstractMonitorObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/ARZPlatformObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/ARZPlatformObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/ConveyorObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/ConveyorObjectInstance.java#restoreRewindState", 1),
            // Obj70 compresses eight ROM SST cog teeth into one multi-piece
            // object plus slot-pressure children, so rewind must capture the
            // rotating tooth phase/offsets and whether child slots were spawned.
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/CogObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/CogObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/BadnikProjectileInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/BadnikProjectileInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/BuzzerBadnikInstance.java#captureRewindState", 2),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/BuzzerBadnikInstance.java#restoreRewindState", 2),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/CoconutsBadnikInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/CoconutsBadnikInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/MasherBadnikInstance.java#captureRewindState", 2),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/MasherBadnikInstance.java#restoreRewindState", 2),
            // Obj08 skid dust has transient animation/delete/DPLC-preload state
            // that is not reconstructible from placement data alone.
            Map.entry("src/main/java/com/openggf/level/objects/SkidDustObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/level/objects/SkidDustObjectInstance.java#restoreRewindState", 1),
            // HPZ uses typed sidecars for its shared controller/runtime graph;
            // TestS3kHpzGraphRewind proves fresh recreation and exact relinking.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZMasterEmeraldGlowObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZMasterEmeraldGlowObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZMasterEmeraldObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZMasterEmeraldObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZSSEntryControlObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZSSEntryControlObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZSanctuaryFallingCrystalObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZSanctuaryFallingCrystalObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZSanctuarySmallEmeraldCeremonyObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZSanctuarySmallEmeraldCeremonyObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZSuperEmeraldObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZSuperEmeraldObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZSuperEmeraldReturnEffectObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HPZSuperEmeraldReturnEffectObjectInstance.java#restoreRewindState", 1),
            // DEZ ($B01) gravity swap: the flag it writes is global and already snapshotted,
            // but its own $32 side latch is not derivable from the placement or the player's
            // position -- the ROM's init seeds it once and each crossing consumes it before
            // the Y-band test. Without the sidecar a restore mid-corridor replays the wrong
            // crossing body and sets gravity where the first run cleared it.
            // TestS3kDezGravityObjectsHeadless captures after the write, replays forward.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravitySwitchObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravitySwitchObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravitySwapObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravitySwapObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezTeleporterObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezTeleporterObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravityPuzzleObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravityPuzzleObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravityRoomObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravityRoomObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravityHubObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravityHubObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravityTubeObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kDezGravityTubeObjectInstance.java#restoreRewindState", 1),
            // HPZ ($1601) teleporter graph and Knuckles-fight children keep object links in
            // ObjectRefId sidecars: generic capture lost the teleporter's beam link on replay.
            // TestS3kHpzCompatibilityMatrix and TestS3kHpzKnucklesFightHeadless prove restore
            // and forward replay.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SSZHPZTeleporterObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SSZHPZTeleporterObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/TeleporterBeamObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/TeleporterBeamObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HpzTeleporterRouteHelperObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HpzTeleporterRouteHelperObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/AbstractHpzCutsceneChildObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/AbstractHpzCutsceneChildObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HpzShipSparkOrbiterObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HpzShipSparkOrbiterObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HpzKnucklesDustObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HpzKnucklesDustObjectInstance.java#restoreRewindState", 1),
            // Hyper stars keep player identity in a typed sidecar; the focused
            // player-reference graph test swaps the live main player on restore.
            // DDZ parent3 links travel as ObjectRefId sidecars (same triage as the HPZ cutscene children);
            // TestS3kDdzColdRoutes restores the boss graph mid-fight, at the wrap and during the exit.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/AbstractDdzObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/AbstractDdzObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HyperSonicStarsObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HyperSonicStarsObjectInstance.java#restoreRewindState", 1),
            // 2026-09-18 Lava Reef. Four object-graph links that a restore cannot rebuild from
            // scalars, each travelling as an ObjectRefId sidecar on the same triage precedent as
            // the DDZ and HPZ entries above.
            //  - The rock crusher's eight hit pieces read their parent's position every frame
            //    through Refresh_ChildPosition; without the link a restored piece freezes.
            //  - The Toxomister body watches $44(a0) to know when to breathe again, its cloud
            //    reads the body's facing to sign the puff dispersal, and each puff follows the
            //    cloud. A lost link would make the body breathe a second cloud immediately.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/LrzRockCrusherPieceInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/LrzRockCrusherPieceInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ToxomisterBadnikInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ToxomisterBadnikInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ToxomisterCloudInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ToxomisterCloudInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ToxomisterPuffInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ToxomisterPuffInstance.java#restoreRewindState", 1),
            // 2026-09-18 Lava Reef, the Fireworm. The head owns the list of the four segments it
            // created (ChildObjDat_8FA16); each segment reads the head's status bit 7 to decide
            // whether it is still publishing a hurt region (Child_DrawTouch_Sprite_FlickerMove,
            // sonic3k.asm:178136-178141). A list of live objects is not a scalar and a restore
            // cannot rebuild it, so it travels as ObjectRefId sidecars and each restored segment
            // is re-attached to this head. Without it a restored chain would keep hurting after
            // the head was destroyed.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/FirewormHeadInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/FirewormHeadInstance.java#restoreRewindState", 1),
            // 2026-09-18 Lava Reef, the Fireworm's segment. The same sidecar disposition one level
            // down: loc_8F95C's flame runs Refresh_ChildPositionAdjusted and never moves on its
            // own, so a lost link leaves the restored flame standing still while its segment swims
            // away (a one-pixel-per-frame drift after a restore, which is how this was found).
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/FirewormSegmentInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/FirewormSegmentInstance.java#restoreRewindState", 1)
    );

    private static final Map<String, Integer> OBJECT_REWIND_ANNOTATION_BASELINE = Map.ofEntries(
            // 2026-07-28 develop merge. Each is a structural or derived reference —
            // hardware work handles, cutscene/render scratch, and the queued
            // results-art handle — rebuilt from its owner or the timing ledger on
            // restore, matching the triage precedent of the entries below.
            Map.entry("src/main/java/com/openggf/level/objects/ShieldObjectInstance.java#@RewindTransient", 3),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesAiz1Instance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/StarPointerBadnikInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/SpikebonkerBadnikInstance.java#@RewindTransient", 2),
            // Chainspike's initial-spawn list is diagnostic; child parent links
            // are captured exact ObjectRefIds, never nearest-body guesses.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ChainspikeBadnikInstance.java#@RewindTransient", 2),
            // HPZ Knuckles-fight object links are restored by ObjectRefId sidecars.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/AbstractHpzCutsceneChildObjectInstance.java#@RewindTransient", 1),
            // DDZ parent3 link, restored by the ObjectRefId sidecar in AbstractDdzObjectInstance.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/AbstractDdzObjectInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HpzKnucklesDustObjectInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/HpzShipSparkOrbiterObjectInstance.java#@RewindTransient", 2),
            // Structural parent pointers on inner particle/support child classes: the parent
            // reference is object-graph structure rebuilt when the parent re-spawns its
            // children, not rewindable state. Same triage precedent as the entries above.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/LbzCupElevatorInstance.java#@RewindTransient", 4),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/LbzTubeElevatorInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/IczSnowPileObjectInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/IczTensionPlatformObjectInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/Mhz1CutsceneKnucklesInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/CluckoidBadnikInstance.java#@RewindTransient", 1),
            // BuggernautBaby's parent pointer is live object-graph structure relinked
            // to the nearest live parent on recreate, not rewindable scalar state.
            // Same structural-parent triage precedent as the entries above.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/BuggernautBabyInstance.java#@RewindTransient", 1),
            // 2026-09-18 Lava Reef: the four links triaged with the override entries above, each
            // restored by its own ObjectRefId sidecar rather than left to be rebuilt.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/LrzRockCrusherPieceInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ToxomisterBadnikInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ToxomisterCloudInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/ToxomisterPuffInstance.java#@RewindTransient", 1),
            // 2026-09-18 Lava Reef, the Fireworm. Two dispositions per class, neither rewindable
            // state: `scripts` is a read-only window of ROM bytes (byte_8FA40..byte_8FA56) that
            // reloads itself on demand, and the head's `segments` list is the object-graph link
            // triaged with the override entries above.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/FirewormFlameInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/FirewormHeadInstance.java#@RewindTransient", 2),
            // The segment carries three: the ROM script window, its flame link (ObjectRefId
            // sidecar, triaged with the override entries above) and its head link, which
            // FirewormHeadInstance.restoreRewindState re-attaches from its own sidecar list.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/FirewormSegmentInstance.java#@RewindTransient", 3),
            // Checkpoint/starpost orbit children keep ROM parent pointers as
            // structural live links. Rewind recreates them only when a matching
            // live parent exists, then reapplies captured scalar orbit state.
            Map.entry("src/main/java/com/openggf/game/sonic1/objects/Sonic1LamppostTwirlInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/CheckpointDongleInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/CheckpointStarInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/MTZLongPlatformCogInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/Sonic3kStarPostBonusStarChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/Sonic3kStarPostStarChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/S3kSignpostStubChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossArenaHelperInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossHitProxyChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossRobotnikHeadChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossSpikeChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossVisualChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossWeatherMachineChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossWeatherVisualChild.java#@RewindTransient", 1),
            // Obj50 body/wing links are live object graph structure. The child
            // pointer is rebuilt from allocation and the wing's parent pointer
            // mirrors the ROM SST parent pointer rather than rewindable state.
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/AquisBadnikInstance.java#@RewindTransient", 2),
            // 2026-07-02 triage: LBZ rewind-tail coverage (541d471da) and the CNZ2
            // point-pokey prize objects (e870059b6) annotate structural parent/child
            // links and constructor-derived offsets — object-graph structure rebuilt
            // by the owning reconstruction path, not rewindable scalar state. Every
            // annotation carries an inline reason; same precedent as the entries above.
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/BombPrizeObjectInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/RingPrizeObjectInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesLbz2Instance.java#@RewindTransient", 4),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/Lbz2RobotnikShipInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/LbzKnuxPillarInstance.java#@RewindTransient", 2),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/LbzLoweringGrappleObjectInstance.java#@RewindTransient", 4),
            // Runtime-art queue handles are transient facades rebound from their
            // captured production-submission ordinals after hardware-ledger restore.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/MgzDrillingRobotnikInstance.java#@RewindTransient", 3),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/Lbz1RobotnikEventController.java#@RewindTransient", 3),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzEndBossInstance.java#@RewindTransient", 12),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss1Instance.java#@RewindTransient", 13),
            // Obj11 bridge parent/child slot links are object-graph structure relinked
            // by adoptSegmentForRewind on the segment's recreate path, exactly like the
            // ARZRotPformsObjectInstance and EggPrisonObjectInstance slot children.
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/BridgeObjectInstance.java#@RewindTransient", 2),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/BridgeSegmentObjectInstance.java#@RewindTransient", 1),
            // S2 trace-parity slot models keep parent/child graph links and
            // constructor-derived child roles outside scalar rewind capture.
            // Focused graph tests cover recreation and relinking.
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/ARZRotPformsObjectInstance.java#@RewindTransient", 5),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/EggPrisonObjectInstance.java#@RewindTransient", 4),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/bosses/Sonic2OOZBossInstance.java#@RewindTransient", 1),
            // SOZ quicksand halfExtent/variant are immutable subtype decodes.
            // Spawn recreation reconstructs both; cooldown/ownership remains captured
            // by the participant table and cold-route rewind checks.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SozQuicksandObjectInstance.java#@RewindTransient", 2),
            // Sky Sanctuary arrival and cutscene objects: the annotated fields are the placement
            // X and the release/base Y each object is constructed with. recreateForRewind rebuilds
            // every one from the same ObjectSpawn, so they are immutable spawn decodes rather than
            // uncaptured frame state.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszArrivalControllerObjectInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszCutsceneKnucklesSpawnerObjectInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszTailsArrivalHelperObjectInstance.java#@RewindTransient", 2),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/SszCutsceneBridgeObjectInstance.java#@RewindTransient", 2)
    );

    private static final Set<String> REWIND_REGISTRY_PRODUCTION_ALLOWLIST = Set.of(
            "src/main/java/com/openggf/game/session/GameplayModeContext.java"
    );

    @Test
    void objectRewindOverridesDoNotGrowWithoutExplicitBaselineTriage() throws IOException {
        Map<String, Integer> actual = objectRewindOverrideCounts();

        assertBaselineMatches(
                actual,
                OBJECT_REWIND_OVERRIDE_BASELINE,
                "Object subclasses should prefer generic/schema rewind capture. "
                        + "New per-object capture/restore overrides need explicit triage.");
    }

    @Test
    void objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage() throws IOException {
        Map<String, Integer> actual = objectRewindAnnotationCounts();

        assertBaselineMatches(
                actual,
                OBJECT_REWIND_ANNOTATION_BASELINE,
                "Object packages should prefer central rewind policies/codecs. "
                        + "New @RewindTransient/@RewindDeferred annotations need explicit triage.");
    }

    @Test
    void productionRewindRegistryConstructionStaysGameplayScoped() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources(MAIN_ROOT)) {
            String normalized = normalize(source);
            if (REWIND_REGISTRY_PRODUCTION_ALLOWLIST.contains(normalized)) {
                continue;
            }
            List<String> lines = Files.readAllLines(source);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("new RewindRegistry(")) {
                    violations.add(normalized + ":" + (i + 1));
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("Production RewindRegistry ownership must stay in approved gameplay lifecycle code:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void objectManagerDynamicRewindCodecsDoNotReferenceConcreteGamePackages() throws IOException {
        Path source = Path.of("src/main/java/com/openggf/level/objects/ObjectManager.java");
        List<String> violations = new ArrayList<>();
        List<String> lines = Files.readAllLines(source);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("com.openggf.game.sonic1")
                    || line.contains("com.openggf.game.sonic2")
                    || line.contains("com.openggf.game.sonic3k")) {
                violations.add(normalize(source) + ":" + (i + 1) + ": " + line.trim());
            }
        }

        if (!violations.isEmpty()) {
            fail("ObjectManager is shared object infrastructure; dynamic rewind codec knowledge "
                    + "for concrete games must live behind ObjectRegistry providers:\n"
                    + String.join("\n", violations));
        }
    }

    private static Map<String, Integer> objectRewindOverrideCounts() throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Path source : objectSources()) {
            String text = Files.readString(source);
            addCount(counts, normalize(source) + "#captureRewindState",
                    CAPTURE_OVERRIDE.matcher(text).results().count());
            addCount(counts, normalize(source) + "#restoreRewindState",
                    RESTORE_OVERRIDE.matcher(text).results().count());
        }
        return counts;
    }

    private static Map<String, Integer> objectRewindAnnotationCounts() throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Path source : objectSources()) {
            String text = Files.readString(source);
            addCount(counts, normalize(source) + "#@RewindTransient",
                    countOccurrences(text, "@RewindTransient"));
            addCount(counts, normalize(source) + "#@RewindDeferred",
                    countOccurrences(text, "@RewindDeferred"));
        }
        return counts;
    }

    private static void addCount(Map<String, Integer> counts, String key, long count) {
        if (count > 0) {
            counts.put(key, Math.toIntExact(count));
        }
    }

    private static long countOccurrences(String text, String token) {
        return Pattern.compile(Pattern.quote(token)).matcher(text).results().count();
    }

    private static List<Path> objectSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path root : OBJECT_SOURCE_ROOTS) {
            for (Path source : javaSources(root)) {
                String normalized = normalize(source);
                if (normalized.contains("/objects/")) {
                    sources.add(source);
                }
            }
        }
        sources.sort(Comparator.comparing(TestRewindArchitectureGuard::normalize));
        return sources;
    }

    private static List<Path> javaSources(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(TestRewindArchitectureGuard::normalize))
                    .toList();
        }
    }

    private static void assertBaselineMatches(Map<String, Integer> actual,
                                              Map<String, Integer> baseline,
                                              String message) {
        List<String> unexpected = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : actual.entrySet()) {
            Integer expected = baseline.get(entry.getKey());
            if (expected == null) {
                unexpected.add(entry.getKey() + " = " + entry.getValue());
            } else if (!expected.equals(entry.getValue())) {
                unexpected.add(entry.getKey() + " = " + entry.getValue()
                        + " (baseline " + expected + ")");
            }
        }

        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : baseline.entrySet()) {
            Integer observed = actual.get(entry.getKey());
            if (observed == null) {
                stale.add(entry.getKey() + " baseline " + entry.getValue()
                        + " is no longer present");
            }
        }

        if (!unexpected.isEmpty() || !stale.isEmpty()) {
            List<String> sections = new ArrayList<>();
            if (!unexpected.isEmpty()) {
                sections.add("Unexpected growth:\n" + String.join("\n", unexpected));
            }
            if (!stale.isEmpty()) {
                sections.add("Stale baseline:\n" + String.join("\n", stale));
            }
            fail(message + "\n" + String.join("\n\n", sections));
        }
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }
}

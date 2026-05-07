package com.openggf.game.rewind;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.RuntimeManager;
import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-shot diagnostic from {@code 2026-05-07-rewind-encounter-validation.md}:
 * drives the EHZ1 trace forward through frames 0..1631 in a fresh fixture,
 * captures every frame, then calls {@code controller.seekTo(1620)} and steps
 * forward 11 times capturing each. The first frame whose diff is non-empty
 * pinpoints where the 11-step replay from keyframe 1620 diverges from the
 * original forward stepping.
 *
 * <p>Compares snapshots both via the lenient {@link RewindSnapshotDiff#diffKey}
 * (used by torture tests) and via a strict reflective deep-equals that does not
 * apply the lenient {@code compareLevel}/{@code compareObjectManager} relaxations.
 * Strict-only divergence at a frame indicates state that the lenient comparator
 * (correctly) treats as cosmetic but that nonetheless might affect replay if
 * something in the engine reads it.
 *
 * <p>Currently {@link Disabled} because the divergence it surfaces is the same
 * iter-1631 framework gap blocking {@code TestRewindTorture} (see that test's
 * @Disabled message and {@code 2026-05-07-rewind-encounter-validation.md}).
 * Re-run manually to confirm fixes.
 */
@Disabled("Local rewind diagnostic for the iter-1631 framework gap; excluded from normal verification.")
@RequiresRom(SonicGame.SONIC_2)
class TestRewindIter1631Diagnostic {

    private static final Path EHZ1_TRACE = Path.of("src/test/resources/traces/s2/ehz1_fullrun");
    private static final int KEYFRAME_INTERVAL = 60;
    private static final int KEYFRAME_TARGET = 1620;
    private static final int REPLAY_END = 1631;

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
        GenericRewindEligibility.clearForTest();
    }

    @Disabled("Diagnostic for the iter-1631 framework gap; surfaces the same divergence as TestRewindTorture#tortureFixedAdjacent. Re-run manually to confirm framework fixes.")
    @Test
    void replayFromKeyframe1620MatchesForwardRunFrameByFrame() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(EHZ1_TRACE),
                "EHZ1 trace directory not found: " + EHZ1_TRACE);
        Path bk2Path = findBk2(EHZ1_TRACE);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + EHZ1_TRACE);

        Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);

        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withRecording(bk2Path)
                .withZoneAndAct(0, 0)
                .build();
        GameplayModeContext gm = RuntimeManager.getCurrent().getGameplayModeContext();
        RewindRegistry registry = gm.getRewindRegistry();
        RewindController controller = new RewindController(
                registry,
                new InMemoryKeyframeStore(),
                new MovieInputSource(movie),
                new FixtureStepper(fixture),
                KEYFRAME_INTERVAL);

        // Phase A: forward run to REPLAY_END, capturing every frame.
        Map<Integer, CompositeSnapshot> forwardSnaps = new LinkedHashMap<>();
        forwardSnaps.put(0, registry.capture());
        for (int f = 1; f <= REPLAY_END; f++) {
            controller.step();
            forwardSnaps.put(f, registry.capture());
        }

        // Phase B: seek to KEYFRAME_TARGET, capture; then step to REPLAY_END
        // capturing every frame.
        controller.seekTo(KEYFRAME_TARGET);
        Map<Integer, CompositeSnapshot> rewindSnaps = new LinkedHashMap<>();
        rewindSnaps.put(KEYFRAME_TARGET, registry.capture());
        for (int f = KEYFRAME_TARGET + 1; f <= REPLAY_END; f++) {
            controller.step();
            rewindSnaps.put(f, registry.capture());
        }

        // Print the snapshot keys at frame 1620 to confirm what we're checking.
        System.out.println("Registered snapshot keys at frame " + KEYFRAME_TARGET + ":");
        for (String key : forwardSnaps.get(KEYFRAME_TARGET).entries().keySet()) {
            System.out.println("  " + key);
        }

        // Diff every frame in [KEYFRAME_TARGET..REPLAY_END] across all keys.
        // ALSO check LevelSnapshot fields that compareLevel() ignores
        // (frameCounter, levelRings, levelTimerFrames, levelTimerPaused,
        // respawnRequested) since those CAN affect replay determinism.
        List<String> firstDivergentKeys = null;
        int firstDivergentFrame = -1;
        StringBuilder report = new StringBuilder();
        StringBuilder strictReport = new StringBuilder();
        for (int f = KEYFRAME_TARGET; f <= REPLAY_END; f++) {
            CompositeSnapshot a = forwardSnaps.get(f);
            CompositeSnapshot b = rewindSnaps.get(f);
            List<String> frameDiffs = new ArrayList<>();
            List<String> strictFrameDiffs = new ArrayList<>();
            for (Map.Entry<String, Object> e : a.entries().entrySet()) {
                String key = e.getKey();
                List<String> keyDiffs = RewindSnapshotDiff.diffKey(key, e.getValue(), b.get(key));
                if (!keyDiffs.isEmpty()) {
                    frameDiffs.add("  [" + key + "]");
                    for (String d : keyDiffs) {
                        frameDiffs.add("    " + d);
                    }
                }
            }
            // Strict LevelSnapshot field check (compareLevel skips most fields).
            List<String> levelExtras = compareLevelExtras(a.get("level"), b.get("level"));
            if (!levelExtras.isEmpty()) {
                frameDiffs.add("  [level (extra fields ignored by compareLevel)]");
                for (String d : levelExtras) {
                    frameDiffs.add("    " + d);
                }
            }
            // Strict identity check: for each key, compare via deep reflection
            // bypassing the diff helper's relaxed comparators. Reports any key
            // whose snapshot differs by anything beyond what compareLevel /
            // compareObjectManager allow.
            for (Map.Entry<String, Object> e : a.entries().entrySet()) {
                String key = e.getKey();
                Object av = e.getValue();
                Object bv = b.get(key);
                List<String> strict = strictDiff(key, av, bv);
                if (!strict.isEmpty()) {
                    strictFrameDiffs.add("  [STRICT " + key + "]");
                    for (String d : strict) {
                        strictFrameDiffs.add("    " + d);
                    }
                }
            }
            if (!strictFrameDiffs.isEmpty()) {
                strictReport.append("Frame ").append(f).append(":\n");
                for (String d : strictFrameDiffs) {
                    strictReport.append(d).append("\n");
                }
            }
            if (!frameDiffs.isEmpty()) {
                if (firstDivergentFrame < 0) {
                    firstDivergentFrame = f;
                    firstDivergentKeys = frameDiffs;
                }
                report.append("Frame ").append(f).append(":\n");
                for (String d : frameDiffs) {
                    report.append(d).append("\n");
                }
            }
        }

        System.out.println("=== Replay diagnostic from keyframe " + KEYFRAME_TARGET
                + " through frame " + REPLAY_END + " ===");
        if (firstDivergentFrame < 0) {
            System.out.println("All frames match forward run. (No divergence detected.)");
            if (strictReport.length() > 0) {
                System.out.println("---");
                System.out.println("Strict-only diagnostic differences:");
                System.out.print(strictReport);
            }
        } else {
            System.out.println("First divergent frame: " + firstDivergentFrame);
            System.out.println("Diff at first divergent frame:");
            for (String d : firstDivergentKeys) {
                System.out.println(d);
            }
            System.out.println("---");
            System.out.println("Full diff report (all divergent frames):");
            System.out.print(report);
        }
        System.out.println("=== END ===");

        if (firstDivergentFrame >= 0) {
            org.junit.jupiter.api.Assertions.fail(
                    "Replay from keyframe " + KEYFRAME_TARGET + " diverges from forward run."
                            + " First divergent frame: " + firstDivergentFrame
                            + ". See stdout for full per-frame diff.");
        }
    }

    /**
     * Determinism check: build two fresh fixtures, run each forward 0..1631
     * with no rewinds, and compare the per-frame player state between them.
     * If the two fresh runs match, the engine is deterministic and the
     * iter-1631 divergence is solely introduced by the rewind path. If they
     * diverge, the engine itself has a non-determinism source we need to
     * find before any rewind fix is meaningful.
     *
     * <p>Compares only the player snapshot fields (xPixel, yPixel, gSpeed,
     * ySpeed, air, rolling, jumping, etc.) which are primitive/enum and
     * cross-fixture safe. Skips object-manager and level snapshots whose
     * lenient comparator depends on identity-equal {@code ObjectSpawn} refs
     * that are different across fixtures.
     */
    @Test
    void twoFreshForwardRunsProduceIdenticalPlayerState() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(EHZ1_TRACE),
                "EHZ1 trace directory not found: " + EHZ1_TRACE);
        Path bk2Path = findBk2(EHZ1_TRACE);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + EHZ1_TRACE);

        // Run A: fresh fixture, forward to REPLAY_END, capture per-frame
        // player snapshots.
        List<com.openggf.level.objects.PerObjectRewindSnapshot.PlayerRewindExtra>
                runA = captureFreshForwardPlayer(bk2Path);

        // Force teardown so run B gets a completely fresh fixture.
        TestEnvironment.resetAll();
        GenericRewindEligibility.clearForTest();

        // Run B: another fresh fixture.
        List<com.openggf.level.objects.PerObjectRewindSnapshot.PlayerRewindExtra>
                runB = captureFreshForwardPlayer(bk2Path);

        // Compare frame-by-frame.
        org.junit.jupiter.api.Assertions.assertEquals(runA.size(), runB.size(),
                "Run sizes differ");
        int firstDivergentFrame = -1;
        StringBuilder report = new StringBuilder();
        for (int f = 0; f < runA.size(); f++) {
            List<String> diffs = strictDiff("playerExtra", runA.get(f), runB.get(f));
            if (!diffs.isEmpty()) {
                if (firstDivergentFrame < 0) firstDivergentFrame = f;
                report.append("Frame ").append(f).append(":\n");
                for (String d : diffs) {
                    report.append("  ").append(d).append("\n");
                }
                if (f - firstDivergentFrame > 5) break;   // cap output
            }
        }

        System.out.println("=== Two-fresh-forward-runs determinism check ===");
        if (firstDivergentFrame < 0) {
            System.out.println("Two fresh forward runs (0.." + REPLAY_END
                    + ") produce identical per-frame player state.");
            System.out.println("Engine is deterministic across fresh fixtures.");
            System.out.println("Therefore: iter-1631 divergence is rewind-path-only.");
        } else {
            System.out.println("Two fresh forward runs DIVERGE at frame "
                    + firstDivergentFrame + ":");
            System.out.print(report);
            System.out.println("Engine itself has non-determinism — find that "
                    + "before chasing rewind fixes.");
        }
        System.out.println("=== END ===");

        if (firstDivergentFrame >= 0) {
            org.junit.jupiter.api.Assertions.fail(
                    "Two fresh forward runs diverge at frame " + firstDivergentFrame
                            + ". Engine non-determinism detected. See stdout.");
        }
    }

    private static List<com.openggf.level.objects.PerObjectRewindSnapshot.PlayerRewindExtra>
            captureFreshForwardPlayer(Path bk2Path) throws Exception {
        Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withRecording(bk2Path)
                .withZoneAndAct(0, 0)
                .build();
        GameplayModeContext gm = RuntimeManager.getCurrent().getGameplayModeContext();
        RewindRegistry registry = gm.getRewindRegistry();
        RewindController controller = new RewindController(
                registry,
                new InMemoryKeyframeStore(),
                new MovieInputSource(movie),
                new FixtureStepper(fixture),
                KEYFRAME_INTERVAL);

        List<com.openggf.level.objects.PerObjectRewindSnapshot.PlayerRewindExtra> out =
                new ArrayList<>(REPLAY_END + 1);
        out.add(extractPlayerExtra(registry.capture()));
        for (int f = 1; f <= REPLAY_END; f++) {
            controller.step();
            out.add(extractPlayerExtra(registry.capture()));
        }
        return out;
    }

    /**
     * Per-frame reflective dump probe: dumps reflective state at every
     * frame from 1620..1631 in BOTH phases. Filters out the known
     * fresh-vs-fresh noise floor, then reports the first frame where any
     * remaining (= seekTo-introduced) field diverges.
     */
    @Test
    void perFrameReflectiveDumpFindsFirstDivergentSubsystem() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(EHZ1_TRACE),
                "EHZ1 trace directory not found: " + EHZ1_TRACE);
        Path bk2Path = findBk2(EHZ1_TRACE);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + EHZ1_TRACE);

        Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withRecording(bk2Path)
                .withZoneAndAct(0, 0)
                .build();
        GameplayModeContext gm = RuntimeManager.getCurrent().getGameplayModeContext();
        RewindController controller = new RewindController(
                gm.getRewindRegistry(),
                new InMemoryKeyframeStore(),
                new MovieInputSource(movie),
                new FixtureStepper(fixture),
                KEYFRAME_INTERVAL);

        // Phase A: forward to KEYFRAME_TARGET, dumping at each step ≥ KEYFRAME_TARGET.
        java.util.Map<Integer, Map<String, String>> dumpsA = new java.util.LinkedHashMap<>();
        for (int f = 1; f <= REPLAY_END; f++) {
            controller.step();
            if (f >= KEYFRAME_TARGET) {
                dumpsA.put(f, fullReflectiveDump(gm));
            }
        }

        // Phase B: seekTo + step, dump per frame.
        controller.seekTo(KEYFRAME_TARGET);
        java.util.Map<Integer, Map<String, String>> dumpsB = new java.util.LinkedHashMap<>();
        dumpsB.put(KEYFRAME_TARGET, fullReflectiveDump(gm));
        for (int f = KEYFRAME_TARGET + 1; f <= REPLAY_END; f++) {
            controller.step();
            dumpsB.put(f, fullReflectiveDump(gm));
        }

        // Find which fields diverge per frame, excluding the known noise floor.
        // Noise floor = fields that differ between two fresh runs (24 fields
        // observed via twoFreshForwardRunsReflectiveDumpsMatch). Hardcoded
        // here to keep this probe self-contained.
        // Tightened noise floor: only exclude things that are STRUCTURAL
        // identity-only diffs (not content). Removed touchResponses,
        // solidContacts, pendingPlayerBoundEntries, dynamicObjects,
        // usedSlots — those may have real content differences.
        java.util.Set<String> noiseFloorPaths = java.util.Set.of(
                "ObjectManager.execOrder",
                "ObjectManager.objectServices",
                "ObjectManager.placement",
                "ObjectManager.planeSwitchers",
                "RingManager.attractedRings",
                "RingManager.placement",
                "SpriteManager.levelManager",
                "ParallaxManager.currentAct",
                "ParallaxManager.currentZone",
                "ParallaxManager.hScroll",
                "ParallaxManager.maxScroll",
                "ParallaxManager.minScroll",
                "ParallaxManager.scrollProvider",
                "ParallaxManager.vscrollFactorFG"
        );
        java.util.Set<String> noisePrefixes = java.util.Set.of(
                "player[sonic].ceilingSensors",
                "player[sonic].groundSensors",
                "player[sonic].powerUpSpawner",
                "player[sonic].pushSensors",
                "player[sonic].runtimeBoundStateModule",
                "player[sonic].spriteRenderer",
                "player[tails_p2].ceilingSensors",
                "player[tails_p2].groundSensors",
                "player[tails_p2].powerUpSpawner",
                "player[tails_p2].pushSensors",
                "player[tails_p2].runtimeBoundStateModule",
                "player[tails_p2].spriteRenderer",
                "slot[24].ExplosionObjectInstance.renderManager"
        );

        System.out.println("=== Per-frame reflective dump diff (noise-filtered) ===");
        for (int f = KEYFRAME_TARGET; f <= REPLAY_END; f++) {
            Map<String, String> a = dumpsA.get(f);
            Map<String, String> b = dumpsB.get(f);
            if (a == null || b == null) continue;
            java.util.Set<String> allKeys = new java.util.TreeSet<>();
            allKeys.addAll(a.keySet());
            allKeys.addAll(b.keySet());
            int diffCount = 0;
            StringBuilder lines = new StringBuilder();
            for (String key : allKeys) {
                if (noiseFloorPaths.contains(key)) continue;
                boolean noiseMatch = false;
                for (String prefix : noisePrefixes) {
                    if (key.equals(prefix) || key.startsWith(prefix + ".")) {
                        noiseMatch = true;
                        break;
                    }
                }
                if (noiseMatch) continue;
                String av = a.get(key);
                String bv = b.get(key);
                if (!java.util.Objects.equals(av, bv)) {
                    // Strip JDK identity hashes (@<digits>) so that "same
                    // content, different identity" is not flagged. If the
                    // stripped strings match, this is identity-noise only.
                    String avStripped = av == null ? "null"
                            : av.replaceAll("@-?\\d+", "@<id>");
                    String bvStripped = bv == null ? "null"
                            : bv.replaceAll("@-?\\d+", "@<id>");
                    if (java.util.Objects.equals(avStripped, bvStripped)) {
                        continue;   // identity-noise, skip
                    }
                    diffCount++;
                    if (lines.length() < 16000) {
                        // Find the first character position where they diverge.
                        int divPos = 0;
                        int minLen = Math.min(avStripped.length(), bvStripped.length());
                        while (divPos < minLen
                                && avStripped.charAt(divPos) == bvStripped.charAt(divPos)) {
                            divPos++;
                        }
                        // Show 100 chars before and after the divergence point.
                        int start = Math.max(0, divPos - 30);
                        int endA = Math.min(avStripped.length(), divPos + 100);
                        int endB = Math.min(bvStripped.length(), divPos + 100);
                        lines.append("    ").append(key)
                                .append(" diverges at char ").append(divPos)
                                .append(":\n        A=...").append(avStripped.substring(start, endA)).append("...")
                                .append("\n        B=...").append(bvStripped.substring(start, endB)).append("...")
                                .append("\n");
                    }
                }
            }
            if (diffCount > 0) {
                System.out.println("Frame " + f + ": " + diffCount + " diverging fields");
                System.out.print(lines);
            }
        }
        System.out.println("=== END ===");
    }

    private static Map<String, String> fullReflectiveDump(GameplayModeContext gm) {
        com.openggf.level.objects.ObjectManager om = gm.getLevelManager().getObjectManager();
        Map<String, String> dump = dumpObjectManagerInstances(om);
        com.openggf.sprites.managers.SpriteManager sm = gm.getSpriteManager();
        for (com.openggf.sprites.Sprite s : sm.getAllSprites()) {
            if (s instanceof com.openggf.sprites.playable.AbstractPlayableSprite player) {
                dumpInstanceFields(player, "player[" + s.getCode() + "]", dump);
            }
        }
        dumpInstanceFields(sm, "SpriteManager", dump);
        dumpInstanceFields(gm.getCamera(), "Camera", dump);
        dumpInstanceFields(gm.getLevelManager().getRingManager(), "RingManager", dump);
        dumpInstanceFields(gm.getParallaxManager(), "ParallaxManager", dump);
        dumpInstanceFields(gm.getWaterSystem(), "WaterSystem", dump);
        // Dump ObjectManager's inner SolidContacts and TouchResponses as
        // top-level entries so their own state isn't masked by the embedded
        // ObjectManager.dynamicObjects substring.
        Object solidContacts = readField(om, "solidContacts");
        if (solidContacts != null) {
            dumpInstanceFields(solidContacts, "SolidContacts", dump);
        }
        Object touchResponses = readField(om, "touchResponses");
        if (touchResponses != null) {
            dumpInstanceFields(touchResponses, "TouchResponses", dump);
        }
        Object placement = readField(om, "placement");
        if (placement != null) {
            dumpInstanceFields(placement, "Placement", dump);
        }
        // ── Extended subsystem coverage (uncovered candidates) ──
        // LevelManager itself (top-level fields beyond what we already cover
        // via getObjectManager / getRingManager).
        try {
            dumpInstanceFields(gm.getLevelManager(), "LevelManager", dump);
        } catch (Exception ignored) {}
        // Shared registries via attachSharedRegistries.
        try { if (gm.getZoneRuntimeRegistry() != null)
                dumpInstanceFields(gm.getZoneRuntimeRegistry(), "ZoneRuntimeRegistry", dump);
        } catch (Exception ignored) {}
        try { if (gm.getPaletteOwnershipRegistry() != null)
                dumpInstanceFields(gm.getPaletteOwnershipRegistry(), "PaletteOwnershipRegistry", dump);
        } catch (Exception ignored) {}
        try { if (gm.getAnimatedTileChannelGraph() != null)
                dumpInstanceFields(gm.getAnimatedTileChannelGraph(), "AnimatedTileChannelGraph", dump);
        } catch (Exception ignored) {}
        try { if (gm.getSpecialRenderEffectRegistry() != null)
                dumpInstanceFields(gm.getSpecialRenderEffectRegistry(), "SpecialRenderEffectRegistry", dump);
        } catch (Exception ignored) {}
        try { if (gm.getAdvancedRenderModeController() != null)
                dumpInstanceFields(gm.getAdvancedRenderModeController(), "AdvancedRenderModeController", dump);
        } catch (Exception ignored) {}
        try { if (gm.getZoneLayoutMutationPipeline() != null)
                dumpInstanceFields(gm.getZoneLayoutMutationPipeline(), "ZoneLayoutMutationPipeline", dump);
        } catch (Exception ignored) {}
        // Core gameplay-mode managers.
        try { if (gm.getTimerManager() != null)
                dumpInstanceFields(gm.getTimerManager(), "TimerManager", dump);
        } catch (Exception ignored) {}
        try { if (gm.getGameStateManager() != null)
                dumpInstanceFields(gm.getGameStateManager(), "GameStateManager", dump);
        } catch (Exception ignored) {}
        try { if (gm.getRng() != null)
                dumpInstanceFields(gm.getRng(), "GameRng", dump);
        } catch (Exception ignored) {}
        try { if (gm.getFadeManager() != null)
                dumpInstanceFields(gm.getFadeManager(), "FadeManager", dump);
        } catch (Exception ignored) {}
        try { if (gm.getSolidExecutionRegistry() != null)
                dumpInstanceFields(gm.getSolidExecutionRegistry(), "SolidExecutionRegistry", dump);
        } catch (Exception ignored) {}
        try { if (gm.getCollisionSystem() != null)
                dumpInstanceFields(gm.getCollisionSystem(), "CollisionSystem", dump);
        } catch (Exception ignored) {}
        try { if (gm.getTerrainCollisionManager() != null)
                dumpInstanceFields(gm.getTerrainCollisionManager(), "TerrainCollisionManager", dump);
        } catch (Exception ignored) {}
        // Level event manager (game-specific via the active GameModule).
        try {
            com.openggf.game.GameModule gameModule = gm.getLevelManager().getGameModule();
            if (gameModule != null) {
                com.openggf.game.LevelEventProvider lep = gameModule.getLevelEventProvider();
                if (lep instanceof com.openggf.game.AbstractLevelEventManager alem) {
                    dumpInstanceFields(alem, "LevelEventManager", dump);
                }
            }
        } catch (Exception ignored) {}
        // OscillationManager — purely static fields. Snapshot it through its
        // public snapshot() API so we see its state (control, values, deltas).
        try {
            com.openggf.game.OscillationSnapshot oscSnap =
                    com.openggf.game.OscillationManager.snapshot();
            dump.put("OscillationManager.snapshot", formatForCompare(oscSnap, 0));
        } catch (Exception ignored) {}
        // Pattern / palette animators — exposed via LevelManager.
        try {
            Object animatedPattern = gm.getLevelManager().getAnimatedPatternManager();
            if (animatedPattern != null) {
                dumpInstanceFields(animatedPattern, "AnimatedPatternManager", dump);
            }
        } catch (Exception ignored) {}
        try {
            Object animatedPalette = gm.getLevelManager().getAnimatedPaletteManager();
            if (animatedPalette != null) {
                dumpInstanceFields(animatedPalette, "AnimatedPaletteManager", dump);
            }
        } catch (Exception ignored) {}
        // Object render manager — keeps per-frame execution scratch state.
        try {
            Object orm = gm.getLevelManager().getObjectRenderManager();
            if (orm != null) {
                dumpInstanceFields(orm, "ObjectRenderManager", dump);
            }
        } catch (Exception ignored) {}
        return dump;
    }

    // ============================================================
    // Deep reflective walker — extension point for the iter-1631
    // investigation. Walks every reachable scalar via reflection
    // with IdentityHashMap-based cycle detection. No depth-3 cut.
    // Iterates collection contents (including IdentityHashMap-backed
    // sets via stable sort by formatted leaf-string). Each path maps
    // to one stable leaf-scalar string.
    //
    // Used by deepReflectiveDumpFindsFirstDivergentField to compute
    // per-frame state hashes and pinpoint the FIRST mutable field in
    // ANY reachable object that diverges between forward/replay.
    // ============================================================

    /**
     * Roots walked by the deep dump. Wider than {@link #fullReflectiveDump}:
     * includes managers behind {@link GameplayModeContext} (TimerManager,
     * GameStateManager, FadeManager, GameRng, SolidExecutionRegistry,
     * ZoneRuntimeRegistry, PaletteOwnershipRegistry, AnimatedTileChannelGraph,
     * SpecialRenderEffectRegistry, AdvancedRenderModeController,
     * ZoneLayoutMutationPipeline, TerrainCollisionManager, CollisionSystem,
     * LevelManager itself).
     */
    private static Map<String, String> deepReflectiveDump(GameplayModeContext gm) {
        DeepWalker walker = new DeepWalker();
        com.openggf.level.objects.ObjectManager om = gm.getLevelManager().getObjectManager();
        // Per-slot dump of every active object instance.
        java.util.Map<Integer, Integer> slotCount = new java.util.HashMap<>();
        for (com.openggf.level.objects.ObjectInstance inst : om.getActiveObjects()) {
            if (inst instanceof com.openggf.level.objects.AbstractObjectInstance aoi) {
                int slot = aoi.getSlotIndex();
                slotCount.merge(slot, 1, Integer::sum);
                int idx = slotCount.get(slot) - 1;
                String suffix = idx == 0 ? "" : "#" + idx;
                String prefix = "slot[" + slot + "]" + suffix + "."
                        + aoi.getClass().getSimpleName();
                walker.walk(aoi, prefix);
            }
        }
        // Player sprites.
        com.openggf.sprites.managers.SpriteManager sm = gm.getSpriteManager();
        for (com.openggf.sprites.Sprite s : sm.getAllSprites()) {
            if (s instanceof com.openggf.sprites.playable.AbstractPlayableSprite player) {
                walker.walk(player, "player[" + s.getCode() + "]");
            }
        }
        // Tier-1 managers (intentionally narrower than the full set behind
        // GameplayModeContext: walking LevelManager via reflection blows
        // heap because it holds tilemap byte arrays, art arrays, etc.).
        walker.walk(om, "ObjectManager");
        walker.walk(sm, "SpriteManager");
        walker.walk(gm.getCamera(), "Camera");
        walker.walk(gm.getLevelManager().getRingManager(), "RingManager");
        walker.walk(gm.getParallaxManager(), "ParallaxManager");
        walker.walk(gm.getWaterSystem(), "WaterSystem");
        walker.walk(gm.getTimerManager(), "TimerManager");
        walker.walk(gm.getGameStateManager(), "GameStateManager");
        walker.walk(gm.getFadeManager(), "FadeManager");
        walker.walk(gm.getRng(), "GameRng");
        walker.walk(gm.getSolidExecutionRegistry(), "SolidExecutionRegistry");
        walker.walk(gm.getCollisionSystem(), "CollisionSystem");
        walker.walk(gm.getZoneRuntimeRegistry(), "ZoneRuntimeRegistry");
        walker.walk(gm.getPaletteOwnershipRegistry(), "PaletteOwnershipRegistry");
        walker.walk(gm.getAnimatedTileChannelGraph(), "AnimatedTileChannelGraph");
        walker.walk(gm.getSpecialRenderEffectRegistry(), "SpecialRenderEffectRegistry");
        walker.walk(gm.getAdvancedRenderModeController(), "AdvancedRenderModeController");
        walker.walk(gm.getZoneLayoutMutationPipeline(), "ZoneLayoutMutationPipeline");
        // Level event manager (game-specific via the active GameModule).
        try {
            com.openggf.game.GameModule gameModule = gm.getLevelManager().getGameModule();
            if (gameModule != null) {
                com.openggf.game.LevelEventProvider lep = gameModule.getLevelEventProvider();
                if (lep instanceof com.openggf.game.AbstractLevelEventManager alem) {
                    walker.walk(alem, "LevelEventManager");
                }
            }
        } catch (Exception ignored) {}
        // ObjectManager inner helpers as top-level entries.
        Object solidContacts = readField(om, "solidContacts");
        if (solidContacts != null) walker.walk(solidContacts, "SolidContacts");
        Object touchResponses = readField(om, "touchResponses");
        if (touchResponses != null) walker.walk(touchResponses, "TouchResponses");
        Object placement = readField(om, "placement");
        if (placement != null) walker.walk(placement, "Placement");
        Object planeSwitchers = readField(om, "planeSwitchers");
        if (planeSwitchers != null) walker.walk(planeSwitchers, "PlaneSwitchers");
        return walker.dump;
    }

    /**
     * Recursive reflection walker that emits one path-keyed scalar entry per
     * reachable mutable field. Uses {@link java.util.IdentityHashMap} to
     * avoid cycles. Walks Set/List/Map contents (sorted for stability) and
     * record components. JDK opaque objects (java.*, javax.*, jdk.*, sun.*)
     * are emitted as identity-stripped class tags.
     */
    private static final class DeepWalker {
        final java.util.Map<String, String> dump = new java.util.TreeMap<>();
        // Visited object identity → first path it was seen at (for cycle reporting).
        final java.util.IdentityHashMap<Object, String> visited = new java.util.IdentityHashMap<>();
        // Hard limits to keep test runtime bounded.
        static final int MAX_DEPTH = 6;
        static final int MAX_COLLECTION_ITEMS = 64;
        static final int MAX_DUMP_ENTRIES = 30_000;
        static final int MAX_VALUE_LEN = 200;
        // Field names to skip globally — pure caches / services / parent
        // references / engine bytes (not gameplay state).
        static final java.util.Set<String> SKIP_FIELD_NAMES = java.util.Set.of(
                "LOGGER", "services", "audioManager", "controller", "renderer",
                "art", "artData", "paletteData", "paletteCache", "patternBank",
                "patternAtlas", "spriteRenderer", "renderManager", "renderContext",
                "objectServices", "engine", "gameRuntime", "worldSession",
                "ringRenderer", "patternData", "tilemap", "blocks", "chunks",
                "patterns", "blockPalette", "chunkPalette", "fgChunks",
                "bgChunks", "fgBlocks", "bgBlocks", "fgPlaneIds", "bgPlaneIds",
                "fgGfx", "bgGfx", "primaryCollision", "secondaryCollision",
                "primaryAngle", "secondaryAngle", "fgScrollByte", "bgScrollByte",
                "transitions", "levelRenderer", "levelDebugRenderer", "tilemapManager",
                "graphicsManager", "fboHelper", "fbo", "shaders", "shader", "buffer",
                "framebuffer", "texture", "patternBuffers"
        );

        void walk(Object root, String path) {
            if (dump.size() >= MAX_DUMP_ENTRIES) return;
            walkInternal(root, path, 0);
        }

        private void walkInternal(Object v, String path, int depth) {
            if (dump.size() >= MAX_DUMP_ENTRIES) return;
            if (v == null) {
                dump.put(path, "null");
                return;
            }
            Class<?> cls = v.getClass();
            // Primitives, wrappers, strings, enums: scalar leaf.
            if (v instanceof Number || v instanceof Boolean || v instanceof Character
                    || v instanceof Enum<?>) {
                dump.put(path, v.toString());
                return;
            }
            if (v instanceof String s) {
                dump.put(path, s.length() > MAX_VALUE_LEN
                        ? s.substring(0, MAX_VALUE_LEN) + "...[+" + (s.length() - MAX_VALUE_LEN) + "]"
                        : s);
                return;
            }
            // Arrays: walk elements.
            if (cls.isArray()) {
                Class<?> elem = cls.getComponentType();
                if (elem.isPrimitive()) {
                    String arrStr;
                    if (elem == byte.class) arrStr = primArrayHash((byte[]) v);
                    else if (elem == short.class) arrStr = primArrayHash((short[]) v);
                    else if (elem == int.class) arrStr = primArrayHash((int[]) v);
                    else if (elem == long.class) arrStr = primArrayHash((long[]) v);
                    else if (elem == float.class) arrStr = primArrayHash((float[]) v);
                    else if (elem == double.class) arrStr = primArrayHash((double[]) v);
                    else if (elem == boolean.class) arrStr = primArrayHash((boolean[]) v);
                    else if (elem == char.class) arrStr = primArrayHash((char[]) v);
                    else arrStr = "<unknown-prim>";
                    dump.put(path, arrStr);
                    return;
                }
                // Object[] — walk each element.
                Object[] arr = (Object[]) v;
                dump.put(path + ".#length", Integer.toString(arr.length));
                int n = Math.min(arr.length, MAX_COLLECTION_ITEMS);
                for (int i = 0; i < n; i++) {
                    walkInternal(arr[i], path + "[" + i + "]", depth + 1);
                }
                if (arr.length > n) {
                    dump.put(path + ".#truncated", "true");
                }
                return;
            }
            // Cycle check via identity.
            String prior = visited.get(v);
            if (prior != null) {
                // Report this as a back-reference to a stable path tag, not
                // an identity hash. The back-ref alias should be the same in
                // both phases as long as walk order is the same.
                dump.put(path, "<ref:" + prior + ">");
                return;
            }
            // PlayableEntity / ObjectInstance refs: emit slot-stable tag.
            // (We will walk them anyway as roots, so this is fine.)
            if (v instanceof com.openggf.sprites.playable.AbstractPlayableSprite player) {
                dump.put(path, "PlayerRef[code=" + player.getCode() + "]");
                return;
            }
            if (v instanceof com.openggf.game.PlayableEntity pe) {
                dump.put(path, "PlayerRef[" + pe.getClass().getSimpleName() + "]");
                return;
            }
            if (v instanceof com.openggf.level.objects.AbstractObjectInstance aoi) {
                dump.put(path, "ObjectRef[" + aoi.getClass().getSimpleName()
                        + "@slot=" + aoi.getSlotIndex() + "]");
                return;
            }
            if (v instanceof com.openggf.level.objects.ObjectInstance oi) {
                dump.put(path, "ObjectRef[" + oi.getClass().getSimpleName() + "]");
                return;
            }
            // ObjectSpawn: identity-stable tuple.
            if (v instanceof com.openggf.level.objects.ObjectSpawn os) {
                dump.put(path, "ObjectSpawn[x=" + os.x() + ",y=" + os.y()
                        + ",objectId=" + os.objectId() + ",layoutIdx=" + os.layoutIndex() + "]");
                return;
            }
            // From here we may recurse, so register identity.
            visited.put(v, path);

            if (depth >= MAX_DEPTH) {
                dump.put(path, cls.getSimpleName() + "@<depth-cut>");
                return;
            }

            // Collections — walk content with sorted-by-leaf-string keys
            // so iteration-order noise (IdentityHashMap, HashSet bucket
            // ordering) does not cause spurious diffs.
            if (v instanceof java.util.Set<?> set) {
                java.util.List<Object> items = new java.util.ArrayList<>(set);
                java.util.List<String> tags = new java.util.ArrayList<>();
                for (Object e : items) tags.add(leafTag(e));
                java.util.List<Integer> idx = new java.util.ArrayList<>();
                for (int i = 0; i < items.size(); i++) idx.add(i);
                idx.sort(java.util.Comparator.comparing(tags::get));
                int n = Math.min(idx.size(), MAX_COLLECTION_ITEMS);
                dump.put(path + ".#size", Integer.toString(items.size()));
                for (int i = 0; i < n; i++) {
                    walkInternal(items.get(idx.get(i)), path + "{" + i + "}", depth + 1);
                }
                if (idx.size() > n) dump.put(path + ".#truncated", "true");
                return;
            }
            if (v instanceof java.util.List<?> list) {
                int n = Math.min(list.size(), MAX_COLLECTION_ITEMS);
                dump.put(path + ".#size", Integer.toString(list.size()));
                for (int i = 0; i < n; i++) {
                    walkInternal(list.get(i), path + "[" + i + "]", depth + 1);
                }
                if (list.size() > n) dump.put(path + ".#truncated", "true");
                return;
            }
            if (v instanceof java.util.Map<?, ?> map) {
                java.util.List<Object> keys = new java.util.ArrayList<>(map.keySet());
                java.util.List<String> tags = new java.util.ArrayList<>();
                for (Object k : keys) tags.add(leafTag(k));
                java.util.List<Integer> idx = new java.util.ArrayList<>();
                for (int i = 0; i < keys.size(); i++) idx.add(i);
                idx.sort(java.util.Comparator.comparing(tags::get));
                int n = Math.min(idx.size(), MAX_COLLECTION_ITEMS);
                dump.put(path + ".#size", Integer.toString(keys.size()));
                for (int i = 0; i < n; i++) {
                    Object k = keys.get(idx.get(i));
                    walkInternal(k, path + ".key[" + i + "]", depth + 1);
                    walkInternal(map.get(k), path + ".val[" + i + "]", depth + 1);
                }
                if (idx.size() > n) dump.put(path + ".#truncated", "true");
                return;
            }
            // Records: walk components.
            if (cls.isRecord()) {
                for (var c : cls.getRecordComponents()) {
                    try {
                        Object av = c.getAccessor().invoke(v);
                        walkInternal(av, path + "." + c.getName(), depth + 1);
                    } catch (ReflectiveOperationException ex) {
                        dump.put(path + "." + c.getName(), "<error:" + ex + ">");
                    }
                }
                return;
            }
            // JDK opaque (other than collections/records/wrappers above).
            String pkg = cls.getPackageName();
            if (pkg.startsWith("java.") || pkg.startsWith("javax.")
                    || pkg.startsWith("jdk.") || pkg.startsWith("sun.")) {
                dump.put(path, "JDK<" + cls.getSimpleName() + ">");
                return;
            }
            // Lambdas / synthetic.
            if (cls.isSynthetic()) {
                dump.put(path, "Synthetic<" + cls.getSimpleName() + ">");
                return;
            }
            // POJO: walk all non-static, non-synthetic fields.
            int fieldCount = 0;
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                String cpkg = c.getPackageName();
                if (cpkg.startsWith("java.") || cpkg.startsWith("javax.")
                        || cpkg.startsWith("jdk.") || cpkg.startsWith("sun.")) {
                    continue;
                }
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    int mod = f.getModifiers();
                    if (java.lang.reflect.Modifier.isStatic(mod)) continue;
                    if (f.isSynthetic()) continue;
                    String name = f.getName();
                    if (SKIP_FIELD_NAMES.contains(name)) continue;
                    if (name.startsWith("cached") || name.startsWith("dpc")
                            || name.startsWith("dplc") || name.startsWith("plc")
                            || name.startsWith("anim") && name.endsWith("Script")) {
                        continue;
                    }
                    try {
                        f.setAccessible(true);
                        Object fv = f.get(v);
                        walkInternal(fv, path + "." + name, depth + 1);
                        fieldCount++;
                    } catch (IllegalAccessException ignored) {
                    } catch (Throwable t) {
                        dump.put(path + "." + name, "<reflection-error:" + t.getClass().getSimpleName() + ">");
                    }
                }
            }
            if (fieldCount == 0) {
                // No fields walked; emit a class tag so the path appears.
                dump.put(path, cls.getSimpleName() + "@<empty>");
            }
        }

        // Compress large primitive arrays to len+hash; show small arrays
        // verbatim so the actual values appear in diffs.
        private static String primArrayHash(byte[] a) {
            if (a.length <= 16) return java.util.Arrays.toString(a);
            return "byte[" + a.length + "]:hash=" + java.util.Arrays.hashCode(a);
        }
        private static String primArrayHash(short[] a) {
            if (a.length <= 16) return java.util.Arrays.toString(a);
            return "short[" + a.length + "]:hash=" + java.util.Arrays.hashCode(a);
        }
        private static String primArrayHash(int[] a) {
            if (a.length <= 16) return java.util.Arrays.toString(a);
            return "int[" + a.length + "]:hash=" + java.util.Arrays.hashCode(a);
        }
        private static String primArrayHash(long[] a) {
            if (a.length <= 16) return java.util.Arrays.toString(a);
            return "long[" + a.length + "]:hash=" + java.util.Arrays.hashCode(a);
        }
        private static String primArrayHash(float[] a) {
            if (a.length <= 16) return java.util.Arrays.toString(a);
            return "float[" + a.length + "]:hash=" + java.util.Arrays.hashCode(a);
        }
        private static String primArrayHash(double[] a) {
            if (a.length <= 16) return java.util.Arrays.toString(a);
            return "double[" + a.length + "]:hash=" + java.util.Arrays.hashCode(a);
        }
        private static String primArrayHash(boolean[] a) {
            if (a.length <= 16) return java.util.Arrays.toString(a);
            return "boolean[" + a.length + "]:hash=" + java.util.Arrays.hashCode(a);
        }
        private static String primArrayHash(char[] a) {
            if (a.length <= 16) return java.util.Arrays.toString(a);
            return "char[" + a.length + "]:hash=" + java.util.Arrays.hashCode(a);
        }

        private static String leafTag(Object v) {
            if (v == null) return "null";
            if (v instanceof Number || v instanceof Boolean || v instanceof Character
                    || v instanceof String || v instanceof Enum<?>) {
                return v.toString();
            }
            if (v instanceof com.openggf.sprites.playable.AbstractPlayableSprite player) {
                return "Player[" + player.getCode() + "]";
            }
            if (v instanceof com.openggf.level.objects.AbstractObjectInstance aoi) {
                return aoi.getClass().getSimpleName() + "@slot=" + aoi.getSlotIndex();
            }
            if (v instanceof com.openggf.level.objects.ObjectSpawn os) {
                return "spawn(x=" + os.x() + ",y=" + os.y()
                        + ",id=" + os.objectId() + ",lay=" + os.layoutIndex() + ")";
            }
            return v.getClass().getSimpleName() + "@" + System.identityHashCode(v);
        }
    }

    /**
     * Deep reflective per-frame divergence finder: at every frame in
     * [KEYFRAME_TARGET..REPLAY_END] in BOTH phases, compute a deep
     * reflective dump (recursive, cycle-detected, all mutable fields of
     * all reachable objects). For each frame, compute a set of paths
     * whose value differs between A and B (after stripping JVM identity
     * hashes). The first frame with any new (not-already-stable) path
     * difference is the smoking gun.
     *
     * <p>Reports for every divergent frame: the count of new diverging
     * paths and the first few paths (with values truncated).
     */
    @Test
    void deepReflectiveDumpFindsFirstDivergentField() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(EHZ1_TRACE),
                "EHZ1 trace directory not found: " + EHZ1_TRACE);
        Path bk2Path = findBk2(EHZ1_TRACE);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + EHZ1_TRACE);

        Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withRecording(bk2Path)
                .withZoneAndAct(0, 0)
                .build();
        GameplayModeContext gm = RuntimeManager.getCurrent().getGameplayModeContext();
        RewindController controller = new RewindController(
                gm.getRewindRegistry(),
                new InMemoryKeyframeStore(),
                new MovieInputSource(movie),
                new FixtureStepper(fixture),
                KEYFRAME_INTERVAL);

        long t0 = System.currentTimeMillis();
        // Phase A: forward 0..REPLAY_END, dumping at each frame >= KEYFRAME_TARGET.
        java.util.Map<Integer, Map<String, String>> dumpsA = new java.util.LinkedHashMap<>();
        for (int f = 1; f <= REPLAY_END; f++) {
            controller.step();
            if (f >= KEYFRAME_TARGET) {
                dumpsA.put(f, deepReflectiveDump(gm));
            }
        }
        long t1 = System.currentTimeMillis();
        System.out.println("Phase A done in " + (t1 - t0) + "ms; "
                + dumpsA.size() + " frames dumped, first frame size = "
                + dumpsA.get(KEYFRAME_TARGET).size() + " entries");

        // Phase B: seekTo + step.
        controller.seekTo(KEYFRAME_TARGET);
        java.util.Map<Integer, Map<String, String>> dumpsB = new java.util.LinkedHashMap<>();
        dumpsB.put(KEYFRAME_TARGET, deepReflectiveDump(gm));
        for (int f = KEYFRAME_TARGET + 1; f <= REPLAY_END; f++) {
            controller.step();
            dumpsB.put(f, deepReflectiveDump(gm));
        }
        long t2 = System.currentTimeMillis();
        System.out.println("Phase B done in " + (t2 - t1) + "ms");

        // For each frame, compute the SET of diverging paths (after
        // identity-hash stripping). Then for each frame f, emit only the
        // paths whose status changed since frame f-1: NEWLY diverging
        // paths (= just-broke at this frame) and CONVERGED paths (= just-fixed).
        java.util.Set<String> prevDivergent = java.util.Collections.emptySet();
        Map<String, String> prevAValues = java.util.Collections.emptyMap();
        Map<String, String> prevBValues = java.util.Collections.emptyMap();
        int firstFrameWithNew = -1;
        java.util.List<String> firstFrameNewPaths = null;
        StringBuilder report = new StringBuilder();
        for (int f = KEYFRAME_TARGET; f <= REPLAY_END; f++) {
            Map<String, String> a = dumpsA.get(f);
            Map<String, String> b = dumpsB.get(f);
            if (a == null || b == null) continue;
            java.util.Set<String> divergent = new java.util.TreeSet<>();
            Map<String, String> aValues = new java.util.HashMap<>();
            Map<String, String> bValues = new java.util.HashMap<>();
            java.util.Set<String> allKeys = new java.util.TreeSet<>();
            allKeys.addAll(a.keySet());
            allKeys.addAll(b.keySet());
            for (String key : allKeys) {
                String av = a.get(key);
                String bv = b.get(key);
                if (java.util.Objects.equals(av, bv)) continue;
                String avS = av == null ? "null" : av.replaceAll("@-?\\d+", "@<id>");
                String bvS = bv == null ? "null" : bv.replaceAll("@-?\\d+", "@<id>");
                if (java.util.Objects.equals(avS, bvS)) continue;
                divergent.add(key);
                aValues.put(key, avS);
                bValues.put(key, bvS);
            }

            java.util.Set<String> newlyDivergent = new java.util.TreeSet<>(divergent);
            newlyDivergent.removeAll(prevDivergent);
            java.util.Set<String> converged = new java.util.TreeSet<>(prevDivergent);
            converged.removeAll(divergent);
            // Also report fields that were divergent before AND now, but
            // whose A/B VALUES changed (= the underlying state evolved).
            java.util.Set<String> updatedDivergent = new java.util.TreeSet<>();
            for (String key : divergent) {
                if (!prevDivergent.contains(key)) continue;
                String prevA = prevAValues.get(key);
                String prevB = prevBValues.get(key);
                if (!java.util.Objects.equals(prevA, aValues.get(key))
                        || !java.util.Objects.equals(prevB, bValues.get(key))) {
                    updatedDivergent.add(key);
                }
            }

            int total = divergent.size();
            int newly = newlyDivergent.size();
            int conv = converged.size();
            int upd = updatedDivergent.size();

            if (newly > 0 || conv > 0 || upd > 0) {
                report.append("Frame ").append(f).append(": ")
                        .append(total).append(" total divergent, ")
                        .append(newly).append(" newly, ")
                        .append(conv).append(" converged, ")
                        .append(upd).append(" value-changed\n");
                if (newly > 0) {
                    if (firstFrameWithNew < 0) {
                        firstFrameWithNew = f;
                        firstFrameNewPaths = new java.util.ArrayList<>(newlyDivergent);
                    }
                    int shown = 0;
                    for (String key : newlyDivergent) {
                        if (shown++ >= 60) {
                            report.append("    ... (").append(newly - 60)
                                    .append(" more newly-divergent paths)\n");
                            break;
                        }
                        String avS = aValues.get(key);
                        String bvS = bValues.get(key);
                        report.append("    NEW [").append(key).append("]\n")
                                .append("        A=").append(truncate(avS, 200)).append("\n")
                                .append("        B=").append(truncate(bvS, 200)).append("\n");
                    }
                }
                if (upd > 0) {
                    int shown = 0;
                    for (String key : updatedDivergent) {
                        if (shown++ >= 30) {
                            report.append("    ... (").append(upd - 30)
                                    .append(" more value-changed paths)\n");
                            break;
                        }
                        String avS = aValues.get(key);
                        String bvS = bValues.get(key);
                        report.append("    UPD [").append(key).append("]\n")
                                .append("        A=").append(truncate(avS, 200)).append("\n")
                                .append("        B=").append(truncate(bvS, 200)).append("\n");
                    }
                }
                if (conv > 0 && f >= KEYFRAME_TARGET + 1) {
                    int shown = 0;
                    for (String key : converged) {
                        if (shown++ >= 20) {
                            report.append("    ... (").append(conv - 20)
                                    .append(" more converged paths)\n");
                            break;
                        }
                        report.append("    CONV [").append(key).append("]\n");
                    }
                }
            }
            prevDivergent = divergent;
            prevAValues = aValues;
            prevBValues = bValues;
        }
        long t3 = System.currentTimeMillis();
        System.out.println("Diff done in " + (t3 - t2) + "ms");

        System.out.println("=== Deep reflective per-frame divergence ===");
        System.out.println("Phase A frames dumped: " + dumpsA.size());
        System.out.println("Phase B frames dumped: " + dumpsB.size());
        if (firstFrameWithNew < 0) {
            System.out.println("No newly-divergent paths detected at any frame in ["
                    + KEYFRAME_TARGET + ".." + REPLAY_END + "].");
        } else {
            System.out.println("First frame with NEWLY-divergent paths: " + firstFrameWithNew);
            System.out.println("Newly-divergent paths at frame " + firstFrameWithNew + ":");
            int shown = 0;
            for (String key : firstFrameNewPaths) {
                if (shown++ >= 30) {
                    System.out.println("  ... (" + (firstFrameNewPaths.size() - 30) + " more)");
                    break;
                }
                System.out.println("  " + key);
            }
        }
        // Dump the FULL set of divergent paths at the frame just before
        // the smoking-gun explosion. For iter-1631 the smoking gun is
        // frame 1630, so we report frame 1629's full divergent set.
        int explosionFrame = REPLAY_END - 1;   // documented signature
        Map<String, String> aPrev = dumpsA.get(explosionFrame - 1);
        Map<String, String> bPrev = dumpsB.get(explosionFrame - 1);
        if (aPrev != null && bPrev != null) {
            System.out.println("--- Full divergent-path dump at frame "
                    + (explosionFrame - 1) + " (frame BEFORE the explosion) ---");
            java.util.Set<String> allKeys = new java.util.TreeSet<>();
            allKeys.addAll(aPrev.keySet());
            allKeys.addAll(bPrev.keySet());
            int shown = 0;
            for (String key : allKeys) {
                String av = aPrev.get(key);
                String bv = bPrev.get(key);
                if (java.util.Objects.equals(av, bv)) continue;
                String avS = av == null ? "null" : av.replaceAll("@-?\\d+", "@<id>");
                String bvS = bv == null ? "null" : bv.replaceAll("@-?\\d+", "@<id>");
                if (java.util.Objects.equals(avS, bvS)) continue;
                if (shown++ >= 80) {
                    System.out.println("  ... (more divergent paths suppressed)");
                    break;
                }
                System.out.println("  [" + key + "]");
                System.out.println("    A=" + truncate(avS, 200));
                System.out.println("    B=" + truncate(bvS, 200));
            }
        }
        System.out.println("---");
        System.out.println("Per-frame report:");
        System.out.print(report);
        System.out.println("=== END ===");
    }

    private static String truncate(String s, int n) {
        if (s == null) return "null";
        if (s.length() <= n) return s;
        return s.substring(0, n) + "...[+" + (s.length() - n) + "]";
    }

    private static Object readField(Object owner, String name) {
        try {
            for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(owner);
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Vary the pre-seek window size to narrow down whether iter-1631 is
     * caused by accumulated future-state contamination (e.g., something
     * mutated at frame 1625 that doesn't get reset by seekTo back to 1620)
     * vs an immediate restoration bug (seekTo introduces divergence even
     * with zero forward stepping past the keyframe).
     */
    @Test
    void singleSeekToOnlyCaptureAtEnd() throws Exception {
        // SAME scenario as the failing iter-1631 test, but capture ONLY at the
        // start (frame 1630) and the end. If divergence exists in the
        // engine state at frame 1630, this captures it. If not, the
        // mid-step `registry.capture()` calls in the original test are
        // themselves causing the divergence.
        Assumptions.assumeTrue(Files.isDirectory(EHZ1_TRACE),
                "EHZ1 trace directory not found: " + EHZ1_TRACE);
        Path bk2Path = findBk2(EHZ1_TRACE);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + EHZ1_TRACE);

        Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withRecording(bk2Path)
                .withZoneAndAct(0, 0)
                .build();
        GameplayModeContext gm = RuntimeManager.getCurrent().getGameplayModeContext();
        RewindController controller = new RewindController(
                gm.getRewindRegistry(),
                new InMemoryKeyframeStore(),
                new MovieInputSource(movie),
                new FixtureStepper(fixture),
                KEYFRAME_INTERVAL);

        // Phase A: forward 0..1630, capture ONCE at end.
        for (int f = 1; f <= 1630; f++) controller.step();
        CompositeSnapshot phaseA1630 = gm.getRewindRegistry().capture();

        // Continue forward to 1631 so we have a far-future state.
        controller.step();   // 1630 → 1631

        // Phase B: seekTo(1620), forward to 1630, capture.
        controller.seekTo(KEYFRAME_TARGET);
        for (int f = KEYFRAME_TARGET + 1; f <= 1630; f++) controller.step();
        CompositeSnapshot phaseB1630 = gm.getRewindRegistry().capture();

        // Lenient diff at frame 1630.
        int diffs = 0;
        StringBuilder diffSummary = new StringBuilder();
        for (var e : phaseA1630.entries().entrySet()) {
            String key = e.getKey();
            var keyDiffs = RewindSnapshotDiff.diffKey(key, e.getValue(), phaseB1630.get(key));
            if (!keyDiffs.isEmpty()) {
                diffs++;
                diffSummary.append("  ").append(key).append(": ").append(keyDiffs.size())
                        .append(" diff lines\n");
            }
        }
        System.out.println("=== Single seekTo, capture only at frame 1630 ===");
        System.out.println("Diverging keys at frame 1630: " + diffs);
        System.out.print(diffSummary);
        System.out.println("=== END ===");
    }

    @Test
    void smallPreSeekWindowStillDivergesAtFrame1630() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(EHZ1_TRACE),
                "EHZ1 trace directory not found: " + EHZ1_TRACE);
        Path bk2Path = findBk2(EHZ1_TRACE);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + EHZ1_TRACE);

        // Three sub-tests in one fixture build (resetting between):
        // - window 1: forward to 1621, seekTo(1620), forward to 1631, compare.
        // - window 5: forward to 1625, seekTo(1620), forward to 1631, compare.
        // - window 11: forward to 1631, seekTo(1620), forward to 1631, compare.
        // For each, report whether player physics at 1630 diverges.
        int[] windows = { 1, 5, 11 };
        for (int window : windows) {
            int preSeek = KEYFRAME_TARGET + window;
            // Need full reference forward — captured every frame so we can compare at 1630.
            Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withRecording(bk2Path)
                    .withZoneAndAct(0, 0)
                    .build();
            GameplayModeContext gm = RuntimeManager.getCurrent().getGameplayModeContext();
            RewindController controller = new RewindController(
                    gm.getRewindRegistry(),
                    new InMemoryKeyframeStore(),
                    new MovieInputSource(movie),
                    new FixtureStepper(fixture),
                    KEYFRAME_INTERVAL);
            // Phase A: capture full forward including frame 1630.
            Map<Integer, CompositeSnapshot> forwardSnaps = new java.util.LinkedHashMap<>();
            for (int f = 1; f <= REPLAY_END; f++) {
                controller.step();
                if (f == 1630 || f == REPLAY_END) {
                    forwardSnaps.put(f, gm.getRewindRegistry().capture());
                }
            }
            // Reset internal step counter via seekTo back to KEYFRAME_TARGET.
            // currentFrame is REPLAY_END (1631). To simulate a "smaller pre-seek
            // window", we'd need to be AT preSeek before the seekTo. seekTo
            // first to KEYFRAME_TARGET, then forward to preSeek, then seekTo to
            // KEYFRAME_TARGET again (smaller window).
            controller.seekTo(KEYFRAME_TARGET);
            for (int f = KEYFRAME_TARGET + 1; f <= preSeek; f++) controller.step();
            controller.seekTo(KEYFRAME_TARGET);
            // Now currentFrame = KEYFRAME_TARGET, with `window` frames of past
            // forward state contaminating any uncaptured fields.
            for (int f = KEYFRAME_TARGET + 1; f <= REPLAY_END; f++) controller.step();
            CompositeSnapshot bAt1630 = forwardSnaps.containsKey(1630)
                    ? gm.getRewindRegistry().capture() : null;
            // Actually we need the rewind run's state at 1630 specifically.
            // Re-do: seekTo to redo, capture at 1630.
            controller.seekTo(KEYFRAME_TARGET);
            for (int f = KEYFRAME_TARGET + 1; f <= preSeek; f++) controller.step();
            controller.seekTo(KEYFRAME_TARGET);
            for (int f = KEYFRAME_TARGET + 1; f < 1630; f++) controller.step();
            controller.step();   // step to 1630
            CompositeSnapshot rewindAt1630 = gm.getRewindRegistry().capture();

            CompositeSnapshot forwardAt1630 = forwardSnaps.get(1630);
            int diffs = 0;
            for (var e : forwardAt1630.entries().entrySet()) {
                if (!RewindSnapshotDiff.diffKey(e.getKey(), e.getValue(),
                        rewindAt1630.get(e.getKey())).isEmpty()) {
                    diffs++;
                }
            }
            System.out.println("[pre-seek window=" + window + "] frame 1630 keys diverging: " + diffs);

            TestEnvironment.resetAll();
            GenericRewindEligibility.clearForTest();
        }
    }

    /**
     * Sanity probe: build two fresh fixtures, forward each to frame 1620, do
     * a full reflective dump on both, and diff. This tells us which fields
     * are STABLE across two fresh runs (deterministic) vs which fields drift
     * due to JVM identity hashing or other test artifacts. The output is the
     * "noise floor" that we should subtract from the real (forward vs
     * seekTo) reflective dump diff.
     */
    @Test
    void twoFreshForwardRunsReflectiveDumpsMatch() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(EHZ1_TRACE),
                "EHZ1 trace directory not found: " + EHZ1_TRACE);
        Path bk2Path = findBk2(EHZ1_TRACE);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + EHZ1_TRACE);

        Map<String, String> dumpA = freshForwardReflectiveDump(bk2Path);
        TestEnvironment.resetAll();
        GenericRewindEligibility.clearForTest();
        Map<String, String> dumpB = freshForwardReflectiveDump(bk2Path);

        java.util.Set<String> allKeys = new java.util.TreeSet<>();
        allKeys.addAll(dumpA.keySet());
        allKeys.addAll(dumpB.keySet());
        StringBuilder report = new StringBuilder();
        java.util.Set<String> divergentFields = new java.util.TreeSet<>();
        for (String key : allKeys) {
            String av = dumpA.get(key);
            String bv = dumpB.get(key);
            if (!java.util.Objects.equals(av, bv)) {
                divergentFields.add(key);
                report.append(key).append(": A=").append(av)
                        .append(" B=").append(bv).append("\n");
            }
        }
        System.out.println("=== Two fresh forward runs reflective dump diff ===");
        System.out.println("Diverging fields between two fresh runs (= noise floor): "
                + divergentFields.size());
        if (!divergentFields.isEmpty()) {
            System.out.println("Field paths:");
            for (String key : divergentFields) {
                System.out.println("  " + key);
            }
        }
        System.out.println("=== END ===");
    }

    private static Map<String, String> freshForwardReflectiveDump(Path bk2Path)
            throws Exception {
        Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withRecording(bk2Path)
                .withZoneAndAct(0, 0)
                .build();
        GameplayModeContext gm = RuntimeManager.getCurrent().getGameplayModeContext();
        RewindController controller = new RewindController(
                gm.getRewindRegistry(),
                new InMemoryKeyframeStore(),
                new MovieInputSource(movie),
                new FixtureStepper(fixture),
                KEYFRAME_INTERVAL);
        for (int f = 1; f <= KEYFRAME_TARGET; f++) {
            controller.step();
        }
        com.openggf.level.objects.ObjectManager om = gm.getLevelManager().getObjectManager();
        Map<String, String> dump = dumpObjectManagerInstances(om);
        com.openggf.sprites.managers.SpriteManager sm = gm.getSpriteManager();
        for (com.openggf.sprites.Sprite s : sm.getAllSprites()) {
            if (s instanceof com.openggf.sprites.playable.AbstractPlayableSprite player) {
                dumpInstanceFields(player, "player[" + s.getCode() + "]", dump);
            }
        }
        dumpInstanceFields(sm, "SpriteManager", dump);
        dumpInstanceFields(gm.getCamera(), "Camera", dump);
        dumpInstanceFields(gm.getLevelManager().getRingManager(), "RingManager", dump);
        dumpInstanceFields(gm.getParallaxManager(), "ParallaxManager", dump);
        dumpInstanceFields(gm.getWaterSystem(), "WaterSystem", dump);
        return dump;
    }

    /**
     * Reflective state dump probe: at frame 1620 in Phase A (during natural
     * forward stepping) and at frame 1620 in Phase B (immediately after
     * {@code seekTo(1620)}), dump the instance fields of every active object
     * in the {@code ObjectManager}. Diff per-slot. Any field that differs
     * is uncaptured cross-frame state — its value at frame 1620 was set
     * naturally during forward play but is stale-from-frame-1631 after
     * {@code seekTo} restores the captured snapshot but leaves uncaptured
     * fields untouched.
     */
    @Test
    void reflectiveDumpAtFrame1620IdentifiesUncapturedObjectFields() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(EHZ1_TRACE),
                "EHZ1 trace directory not found: " + EHZ1_TRACE);
        Path bk2Path = findBk2(EHZ1_TRACE);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + EHZ1_TRACE);

        Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);

        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withRecording(bk2Path)
                .withZoneAndAct(0, 0)
                .build();
        GameplayModeContext gm = RuntimeManager.getCurrent().getGameplayModeContext();
        RewindRegistry registry = gm.getRewindRegistry();
        RewindController controller = new RewindController(
                registry,
                new InMemoryKeyframeStore(),
                new MovieInputSource(movie),
                new FixtureStepper(fixture),
                KEYFRAME_INTERVAL);

        // Phase A: forward to 1620, dump live state AND capture snapshot.
        for (int f = 1; f <= KEYFRAME_TARGET; f++) {
            controller.step();
        }
        com.openggf.level.objects.ObjectManager om =
                gm.getLevelManager().getObjectManager();
        Map<String, String> dumpA = dumpObjectManagerInstances(om);
        // Also dump the player sprite (Sonic) reflectively, plus the
        // SpriteManager and Camera.
        com.openggf.sprites.managers.SpriteManager sm = gm.getSpriteManager();
        for (com.openggf.sprites.Sprite s : sm.getAllSprites()) {
            if (s instanceof com.openggf.sprites.playable.AbstractPlayableSprite player) {
                dumpInstanceFields(player, "player[" + s.getCode() + "]", dumpA);
            }
        }
        dumpInstanceFields(sm, "SpriteManager", dumpA);
        dumpInstanceFields(gm.getCamera(), "Camera", dumpA);
        dumpInstanceFields(gm.getLevelManager().getRingManager(), "RingManager", dumpA);
        dumpInstanceFields(gm.getParallaxManager(), "ParallaxManager", dumpA);
        dumpInstanceFields(gm.getWaterSystem(), "WaterSystem", dumpA);
        // Direct probe: walk every active object and call its
        // captureRewindState() inline. Compare those values to the live
        // motionState. If they differ, captureRewindState is reading
        // stale or different fields than the live motionState.
        System.out.println("=== Phase A: live captureRewindState() vs reflective motionState ===");
        for (com.openggf.level.objects.ObjectInstance inst : om.getActiveObjects()) {
            if (inst instanceof com.openggf.game.sonic2.objects.badniks.BadnikProjectileInstance proj) {
                var snap = proj.captureRewindState();
                var subExtra = snap.objectSubclassExtra();
                System.out.println("  proj@slot=" + proj.getSlotIndex()
                        + " System.identityHashCode=" + System.identityHashCode(proj));
                System.out.println("    captureRewindState.subExtra = " + subExtra);
                // Read live motionState reflectively to confirm.
                try {
                    java.lang.reflect.Field f = proj.getClass().getDeclaredField("motionState");
                    f.setAccessible(true);
                    Object ms = f.get(proj);
                    System.out.println("    live motionState = " + formatForCompare(ms, 0));
                } catch (Exception ex) {
                    System.out.println("    live motionState = <error: " + ex + ">");
                }
            }
        }
        System.out.println("=== END live capture vs reflective ===");
        // Also capture a fresh registry snapshot at frame 1620 in Phase A so
        // we can verify the snapshot at the same moment as the live dump.
        CompositeSnapshot snapAtA1620 = registry.capture();

        // Continue to REPLAY_END.
        for (int f = KEYFRAME_TARGET + 1; f <= REPLAY_END; f++) {
            controller.step();
        }

        // Phase B: seekTo(1620), dump live state again.
        controller.seekTo(KEYFRAME_TARGET);
        Map<String, String> dumpB = dumpObjectManagerInstances(om);
        for (com.openggf.sprites.Sprite s : sm.getAllSprites()) {
            if (s instanceof com.openggf.sprites.playable.AbstractPlayableSprite player) {
                dumpInstanceFields(player, "player[" + s.getCode() + "]", dumpB);
            }
        }
        dumpInstanceFields(sm, "SpriteManager", dumpB);
        dumpInstanceFields(gm.getCamera(), "Camera", dumpB);
        dumpInstanceFields(gm.getLevelManager().getRingManager(), "RingManager", dumpB);
        dumpInstanceFields(gm.getParallaxManager(), "ParallaxManager", dumpB);
        dumpInstanceFields(gm.getWaterSystem(), "WaterSystem", dumpB);
        CompositeSnapshot snapAtB1620 = registry.capture();

        // Verify: do the two snapshots themselves match?
        com.openggf.game.rewind.snapshot.ObjectManagerSnapshot omA =
                (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot) snapAtA1620.get("object-manager");
        com.openggf.game.rewind.snapshot.ObjectManagerSnapshot omB =
                (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot) snapAtB1620.get("object-manager");
        System.out.println("=== Snapshot vs live state cross-check ===");
        System.out.println("Phase A snapshot dynamicObjects:");
        for (var e : omA.dynamicObjects()) {
            System.out.println("  slot=" + e.slotIndex() + " class=" + e.className()
                    + " spawn=" + e.spawn()
                    + " state.objectSubclassExtra=" + (e.state() != null ? e.state().objectSubclassExtra() : null));
        }
        System.out.println("Phase B snapshot dynamicObjects:");
        for (var e : omB.dynamicObjects()) {
            System.out.println("  slot=" + e.slotIndex() + " class=" + e.className()
                    + " spawn=" + e.spawn()
                    + " state.objectSubclassExtra=" + (e.state() != null ? e.state().objectSubclassExtra() : null));
        }
        System.out.println("=== END snapshot cross-check ===");

        // Diff. Keys are "slot[N].ClassName.fieldName".
        java.util.Set<String> allKeys = new java.util.TreeSet<>();
        allKeys.addAll(dumpA.keySet());
        allKeys.addAll(dumpB.keySet());
        StringBuilder report = new StringBuilder();
        int diffCount = 0;
        for (String key : allKeys) {
            String av = dumpA.get(key);
            String bv = dumpB.get(key);
            if (!java.util.Objects.equals(av, bv)) {
                report.append(key).append(": A=").append(av)
                        .append(" B=").append(bv).append("\n");
                diffCount++;
            }
        }
        System.out.println("=== Reflective dump @ frame " + KEYFRAME_TARGET
                + ": Phase A (forward) vs Phase B (post-seekTo) ===");
        System.out.println("Total fields dumped (A): " + dumpA.size());
        System.out.println("Total fields dumped (B): " + dumpB.size());
        System.out.println("Diverging fields: " + diffCount);
        if (diffCount > 0) {
            System.out.println(report);
        } else {
            System.out.println("No per-instance field divergence detected.");
        }
        System.out.println("=== END ===");

        if (diffCount > 0) {
            org.junit.jupiter.api.Assertions.fail(
                    "Found " + diffCount + " uncaptured per-instance fields. See stdout.");
        }
    }

    private static Map<String, String> dumpObjectManagerInstances(
            com.openggf.level.objects.ObjectManager om) {
        // EAGER STRING SNAPSHOT — store formatted values not refs.
        Map<String, String> out = new java.util.TreeMap<>();
        java.util.Map<Integer, Integer> slotCount = new java.util.HashMap<>();
        for (com.openggf.level.objects.ObjectInstance inst : om.getActiveObjects()) {
            if (inst instanceof com.openggf.level.objects.AbstractObjectInstance aoi) {
                int slot = aoi.getSlotIndex();
                slotCount.merge(slot, 1, Integer::sum);
                int idx = slotCount.get(slot) - 1;
                String suffix = idx == 0 ? "" : "#" + idx;
                String prefix = "slot[" + slot + "]" + suffix + "." + aoi.getClass().getSimpleName();
                dumpInstanceFields(aoi, prefix, out);
            }
        }
        for (var e : slotCount.entrySet()) {
            if (e.getValue() > 1) {
                System.out.println("WARN: slot " + e.getKey() + " has "
                        + e.getValue() + " objects (slot collision)");
            }
        }
        // Also dump the ObjectManager's own fields (placement, solidContacts,
        // touchResponses inner state) — anything that isn't an
        // AbstractObjectInstance but lives inside the object manager.
        dumpInstanceFields(om, "ObjectManager", out);
        return out;
    }

    private static void dumpInstanceFields(Object inst, String prefix,
                                            Map<String, String> out) {
        Class<?> cls = inst.getClass();
        while (cls != null && cls != Object.class) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(mod)) continue;
                if (f.isSynthetic()) continue;
                String name = f.getName();
                // Skip structural / service / cache / debug refs that hold
                // live engine references (would compare unequal across the
                // restore boundary even if logically equivalent).
                if (name.equals("services") || name.equals("spawn")
                        || name.equals("dynamicSpawn") || name.equals("renderer")
                        || name.equals("audioManager") || name.equals("camera")
                        || name.equals("controller") || name.startsWith("cached")
                        || name.equals("LOGGER") || name.equals("parent")
                        || name.startsWith("art") || name.equals("paletteData")
                        || name.equals("objectManager") || name.equals("table")
                        || name.equals("debugState")) {
                    continue;
                }
                f.setAccessible(true);
                try {
                    Object v = f.get(inst);
                    out.put(prefix + "." + name, formatForCompare(v, 0));
                } catch (IllegalAccessException ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static String formatForCompare(Object v) {
        return formatForCompare(v, 0);
    }

    private static String formatForCompare(Object v, int depth) {
        if (v == null) return "null";
        Class<?> cls = v.getClass();
        if (cls.isArray()) {
            Class<?> elem = cls.getComponentType();
            if (elem == byte.class) return java.util.Arrays.toString((byte[]) v);
            if (elem == short.class) return java.util.Arrays.toString((short[]) v);
            if (elem == int.class) return java.util.Arrays.toString((int[]) v);
            if (elem == long.class) return java.util.Arrays.toString((long[]) v);
            if (elem == boolean.class) return java.util.Arrays.toString((boolean[]) v);
            if (elem == char.class) return java.util.Arrays.toString((char[]) v);
            if (elem == float.class) return java.util.Arrays.toString((float[]) v);
            if (elem == double.class) return java.util.Arrays.toString((double[]) v);
            return java.util.Arrays.deepToString((Object[]) v);
        }
        if (v instanceof Number || v instanceof Boolean || v instanceof Character
                || v instanceof String || v instanceof Enum<?>) {
            return v.toString();
        }
        // Skip PlayableEntity refs — we know via separate determinism test
        // that the player state matches across phases.
        if (v instanceof com.openggf.game.PlayableEntity
                || v instanceof com.openggf.sprites.playable.AbstractPlayableSprite) {
            return "<player>";
        }
        // ObjectInstance refs: identity differs across restore; bucket by
        // slot so the diff is stable.
        if (v instanceof com.openggf.level.objects.AbstractObjectInstance aoi) {
            return aoi.getClass().getSimpleName() + "@slot[" + aoi.getSlotIndex() + "]";
        }
        if (v instanceof com.openggf.level.objects.ObjectInstance oi) {
            return oi.getClass().getSimpleName() + "@<object-ref>";
        }
        // ObjectSpawn refs: their content might differ identity-wise but
        // logical (x,y,objectId) should match. Format the identity-stable
        // tuple.
        if (v instanceof com.openggf.level.objects.ObjectSpawn os) {
            return "ObjectSpawn[x=" + os.x() + ", y=" + os.y()
                    + ", objectId=" + os.objectId() + ", layoutIdx=" + os.layoutIndex() + "]";
        }
        // Iterate Set/Collection content (JPMS blocks reading private fields
        // but the public API works). Sort by toString for stability.
        if (v instanceof java.util.Set<?> set) {
            java.util.List<String> items = new java.util.ArrayList<>();
            for (Object e : set) items.add(formatForCompare(e, depth + 1));
            java.util.Collections.sort(items);
            return "Set" + items;
        }
        if (v instanceof java.util.List<?> list) {
            java.util.List<String> items = new java.util.ArrayList<>();
            for (Object e : list) items.add(formatForCompare(e, depth + 1));
            return "List" + items;
        }
        if (v instanceof java.util.Map<?, ?> map) {
            java.util.List<String> items = new java.util.ArrayList<>();
            for (var e : map.entrySet()) {
                items.add(formatForCompare(e.getKey(), depth + 1) + "->"
                        + formatForCompare(e.getValue(), depth + 1));
            }
            java.util.Collections.sort(items);
            return "Map" + items;
        }
        // Other JDK types: opaque.
        String pkg = cls.getPackageName();
        if (pkg.startsWith("java.") || pkg.startsWith("javax.")
                || pkg.startsWith("jdk.") || pkg.startsWith("sun.")) {
            return cls.getSimpleName() + "@" + System.identityHashCode(v);
        }
        // For helper-style objects (records, simple POJOs): walk fields up to
        // a depth limit and dump them. This makes the diff show actual content
        // divergence instead of Java identity-hash noise.
        if (depth >= 3) {
            return cls.getSimpleName() + "@<depth-cut>";
        }
        StringBuilder sb = new StringBuilder(cls.getSimpleName()).append("{");
        boolean first = true;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            String cpkg = c.getPackageName();
            if (cpkg.startsWith("java.") || cpkg.startsWith("javax.")
                    || cpkg.startsWith("jdk.") || cpkg.startsWith("sun.")) {
                continue;
            }
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(mod)) continue;
                if (f.isSynthetic()) continue;
                String name = f.getName();
                if (name.equals("services") || name.equals("audioManager")
                        || name.equals("camera") || name.equals("controller")
                        || name.startsWith("cached") || name.equals("LOGGER")
                        || name.equals("parent")) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object fv = f.get(v);
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append(name).append("=").append(formatForCompare(fv, depth + 1));
                } catch (Exception ignored) {}
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static com.openggf.level.objects.PerObjectRewindSnapshot.PlayerRewindExtra
            extractPlayerExtra(CompositeSnapshot snap) {
        Object spritesObj = snap.get("sprites");
        if (!(spritesObj instanceof com.openggf.game.rewind.snapshot.SpriteManagerSnapshot s)) {
            return null;
        }
        for (var entry : s.sprites()) {
            var state = entry.state();
            if (state != null && state.playerExtra() != null) {
                return state.playerExtra();
            }
        }
        return null;
    }

    private static List<String> strictDiff(String path, Object a, Object b) {
        List<String> out = new ArrayList<>();
        strictDiffWalk(path, a, b, out);
        return out;
    }

    private static void strictDiffWalk(String path, Object a, Object b, List<String> out) {
        if (out.size() >= 500) return;
        if (a == b) return;
        if (a == null || b == null) {
            out.add(path + ": A=" + a + " B=" + b);
            return;
        }
        Class<?> cls = a.getClass();
        if (cls != b.getClass()) {
            out.add(path + ": class A=" + cls.getSimpleName() + " B=" + b.getClass().getSimpleName());
            return;
        }
        if (cls.isArray()) {
            Class<?> elem = cls.getComponentType();
            if (elem.isPrimitive()) {
                boolean equal;
                if (elem == byte.class) equal = java.util.Arrays.equals((byte[]) a, (byte[]) b);
                else if (elem == short.class) equal = java.util.Arrays.equals((short[]) a, (short[]) b);
                else if (elem == int.class) equal = java.util.Arrays.equals((int[]) a, (int[]) b);
                else if (elem == long.class) equal = java.util.Arrays.equals((long[]) a, (long[]) b);
                else if (elem == float.class) equal = java.util.Arrays.equals((float[]) a, (float[]) b);
                else if (elem == double.class) equal = java.util.Arrays.equals((double[]) a, (double[]) b);
                else if (elem == char.class) equal = java.util.Arrays.equals((char[]) a, (char[]) b);
                else equal = java.util.Arrays.equals((boolean[]) a, (boolean[]) b);
                if (!equal) {
                    int la = java.lang.reflect.Array.getLength(a);
                    int lb = java.lang.reflect.Array.getLength(b);
                    if (la != lb) {
                        out.add(path + ": length A=" + la + " B=" + lb);
                    } else {
                        out.add(path + ": " + elem.getSimpleName() + "[" + la + "] differs");
                    }
                }
                return;
            }
            Object[] aa = (Object[]) a;
            Object[] bb = (Object[]) b;
            if (aa.length != bb.length) {
                out.add(path + ": length A=" + aa.length + " B=" + bb.length);
                return;
            }
            for (int i = 0; i < aa.length; i++) {
                strictDiffWalk(path + "[" + i + "]", aa[i], bb[i], out);
            }
            return;
        }
        if (cls.isRecord()) {
            for (var c : cls.getRecordComponents()) {
                try {
                    Object av = c.getAccessor().invoke(a);
                    Object bv = c.getAccessor().invoke(b);
                    strictDiffWalk(path + "." + c.getName(), av, bv, out);
                } catch (ReflectiveOperationException ex) {
                    throw new RuntimeException(ex);
                }
            }
            return;
        }
        if (a instanceof List<?> al && b instanceof List<?> bl) {
            if (al.size() != bl.size()) {
                out.add(path + ": list-size A=" + al.size() + " B=" + bl.size());
                return;
            }
            for (int i = 0; i < al.size(); i++) {
                strictDiffWalk(path + "[" + i + "]", al.get(i), bl.get(i), out);
            }
            return;
        }
        if (a instanceof java.util.Map<?, ?> am && b instanceof java.util.Map<?, ?> bm) {
            if (!am.keySet().equals(bm.keySet())) {
                out.add(path + ": keys differ");
                return;
            }
            for (Object key : am.keySet()) {
                strictDiffWalk(path + "." + key, am.get(key), bm.get(key), out);
            }
            return;
        }
        if (!java.util.Objects.equals(a, b)) {
            out.add(path + ": A=" + a + " B=" + b);
        }
    }

    private static boolean strictDeepEquals(Object a, Object b) {
        return strictDiff("", a, b).isEmpty();
    }

    private static List<String> compareLevelExtras(Object av, Object bv) {
        List<String> diffs = new ArrayList<>();
        if (!(av instanceof LevelSnapshot a) || !(bv instanceof LevelSnapshot b)) {
            return diffs;
        }
        if (a.frameCounter() != b.frameCounter()) {
            diffs.add("frameCounter: A=" + a.frameCounter() + " B=" + b.frameCounter());
        }
        if (a.hasLevelHudState() != b.hasLevelHudState()) {
            diffs.add("hasLevelHudState: A=" + a.hasLevelHudState() + " B=" + b.hasLevelHudState());
        }
        if (a.levelRings() != b.levelRings()) {
            diffs.add("levelRings: A=" + a.levelRings() + " B=" + b.levelRings());
        }
        if (a.levelTimerFrames() != b.levelTimerFrames()) {
            diffs.add("levelTimerFrames: A=" + a.levelTimerFrames() + " B=" + b.levelTimerFrames());
        }
        if (a.levelTimerPaused() != b.levelTimerPaused()) {
            diffs.add("levelTimerPaused: A=" + a.levelTimerPaused() + " B=" + b.levelTimerPaused());
        }
        if (a.respawnRequested() != b.respawnRequested()) {
            diffs.add("respawnRequested: A=" + a.respawnRequested() + " B=" + b.respawnRequested());
        }
        return diffs;
    }

    private static Path findBk2(Path dir) throws Exception {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static final class FixtureStepper implements RewindSeekAwareEngineStepper {
        private final HeadlessTestFixture fixture;

        FixtureStepper(HeadlessTestFixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public void step(Bk2FrameInput inputs) {
            int p1 = inputs.p1InputMask();
            fixture.runner().stepFrame(
                    (p1 & AbstractPlayableSprite.INPUT_UP) != 0,
                    (p1 & AbstractPlayableSprite.INPUT_DOWN) != 0,
                    (p1 & AbstractPlayableSprite.INPUT_LEFT) != 0,
                    (p1 & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                    (p1 & AbstractPlayableSprite.INPUT_JUMP) != 0,
                    inputs.p2InputMask(),
                    inputs.p2StartPressed());
        }

        @Override
        public void restoreToFrame(int frame, Bk2FrameInput inputAtFrame) {
            fixture.runner().primeInputState(inputAtFrame);
        }
    }

    private static final class MovieInputSource implements InputSource {
        private final Bk2Movie movie;

        MovieInputSource(Bk2Movie movie) {
            this.movie = movie;
        }

        @Override
        public int frameCount() {
            return movie.getFrames().size();
        }

        @Override
        public Bk2FrameInput read(int frame) {
            if (frame < 0 || frame >= frameCount()) {
                return new Bk2FrameInput(frame, 0, 0, false, "diag:oor:" + frame);
            }
            return movie.getFrame(frame);
        }
    }
}

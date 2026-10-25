package com.openggf.sprites.managers;

import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production fixed-Dust resource/rebuild and owner-registry boundary contracts.
 * Native044 loc_39AD2 (sonic3k.asm:77315) starts anim4 on Dust/Dust_P2 at
 * y=$5C0; loc_18BEC/18C04 (34051/34058) retain its world position and draw
 * ArtUnc_SplashDrown through the real ROM mappings/DPLCs. No splash trigger,
 * controller/renderer setter, clock/phase seed or fabricated snapshot is used.
 *
 * The first case uses every session-registry key. The other cases deliberately
 * test the production owner in its own real registry, not a whole-world restore.
 * Borrowed renderer identity is a same-session resource assertion; mutable DPLC
 * caches, pixel parity, cross-session lifetime and player recreation are separate.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestPlayableDustEffectsRewind {
    private static final String EFFECTS_KEY = "playable-dust-effects";
    private static final int SURFACE_Y = 0x5C0;
    private static final int FIRST_SURFACE_FRAME = 0x16;
    private static final int LAST_SURFACE_FRAME = 0x1D;
    private static final int SURFACE_FRAME_TICKS = 6; // native duration5 plus underflow tick
    private static final int ENTRY_LIMIT = 600; // finite natural title/launch prerequisite, never a seed
    private static final GameplayCaptureSession.Settings SETTINGS =
            new GameplayCaptureSession.Settings(320, "sonic", "tails", "off",
                    null, null, null, null, true, false);

    @Test
    void naturalSurfaceSplashRebindsActualReplacementControllersAndReplaysTheWholeRegistry() throws Exception {
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            boot(session);
            awaitNaturalSurfaceSplash(session);
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            assertNotNull(registry);
            var worldA = registry.capture();
            var effectsA = effects(worldA);
            requireActiveSurface(effectsA);
            var renderer = sharedSurfaceRenderer(effectsA);

            int frames = 0;
            for (int step = 1; step <= SURFACE_FRAME_TICKS; step++) {
                ordinaryFrame(session);
                if (players().getFirst().getSpindashDustController().surfaceSplashMappingFrame()
                        != effectsA.get(players().getFirst().getCode()).surfaceSplashMappingFrame()) {
                    frames = step;
                    break;
                }
            }
            assertTrue(frames > 0, "prerequisite: ordinary animation reaches a distinct displayed surface frame");
            var worldB = registry.capture();
            var effectsB = effects(worldB);
            requireActiveSurface(effectsB);
            assertSame(renderer, sharedSurfaceRenderer(effectsB));
            assertNotEquals(effectsA, effectsB, "prerequisite: actual surface state changes before rewind");

            List<Executable> oracles = new ArrayList<>();
            for (int branch = 1; branch <= 2; branch++) {
                String label = "actual controller rebuild / restore / forward branch " + branch;
                var previousControllers = controllers();
                var previousEffects = currentEffects();
                // This real public ROM-art owner replaces controllers on the SAME players.
                // SpriteManager.restore is not claimed to recreate configured players.
                GameServices.level().refreshPlayableSpriteArt();
                var replacements = controllers();
                for (String code : previousControllers.keySet()) {
                    assertNotSame(previousControllers.get(code), replacements.get(code),
                            "prerequisite: refreshPlayableSpriteArt really replaced " + code + "'s controller");
                    assertFalse(replacements.get(code).isSurfaceSplashActive(),
                            "prerequisite: a fresh controller has not inherited the old surface effect");
                }

                registry.restore(worldA); // all actual adapters, then production resource/roster closures
                var immediate = registry.capture();
                var immediateEffects = effects(immediate);
                var immediateControllers = controllers();
                var immediateDifferences = differences(worldA, immediate);
                oracles.add(() -> assertTrue(immediateDifferences.isEmpty(),
                        () -> label + " immediate complete registry: " + immediateDifferences));
                oracles.add(() -> assertEquals(effectsA, immediateEffects, label + " saved supplementary state"));
                for (String code : previousControllers.keySet()) {
                    oracles.add(() -> assertSame(replacements.get(code), immediateControllers.get(code),
                            label + " state restored into the current controller: " + code));
                    oracles.add(() -> assertSame(renderer, immediateEffects.get(code).surfaceSplashRenderer(),
                            label + " producer-supplied resource binding: " + code));
                    var staleState = previousControllers.get(code).captureEffectsRewindState();
                    oracles.add(() -> assertEquals(previousEffects.get(code), staleState,
                            label + " stale controller is not the restore target: " + code));
                }

                for (int step = 0; step < frames; step++) ordinaryFrame(session);
                var forward = registry.capture();
                var forwardDifferences = differences(worldB, forward);
                var forwardEffects = effects(forward);
                oracles.add(() -> assertTrue(forwardDifferences.isEmpty(),
                        () -> label + " identical ordinary-input forward complete registry: " + forwardDifferences));
                oracles.add(() -> assertEquals(effectsB, forwardEffects, label + " forward supplementary state"));
            }
            // Expected restore/forward differences cannot preempt the second honest branch.
            assertAll("natural LBZ surface effect and actual controller replacement", oracles);
        }
    }

    @Test
    void olderOwnerRegistryCaptureResetsAllSupplementaryDefaultsWithoutChangingOrdinaryDust() throws Exception {
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            boot(session);
            // Read the actual fresh ROM-art controllers before any driven gameplay.
            var constructorOrdinary = ordinaryDust();
            constructorOrdinary.values().forEach(state -> assertFalse(state.activeLastTick()));
            var ownerRegistry = new RewindRegistry();
            var beforeRegistration = ownerRegistry.capture();
            assertTrue(beforeRegistration.entries().isEmpty());
            SpriteManagerInternalAccess.registerDustEffectsRewind(GameServices.sprites(), ownerRegistry);
            awaitNaturalSurfaceSplash(session);
            var active = ownerRegistry.capture();
            assertEquals(Set.of(EFFECTS_KEY), active.entries().keySet(), "declared owner-only partition");
            requireActiveSurface(effects(active));
            awaitNaturalOrdinarySpindash(session);
            // The launch effect may finish before landing. At this reset boundary
            // assert its actual retained position/resource, not simultaneous activity.
            var effectsBeforeReset = currentEffects();
            effectsBeforeReset.forEach((code, state) -> {
                assertTrue(state.surfaceSplashX() > 0, "retained production splash position: " + code);
                assertEquals(SURFACE_Y, state.surfaceSplashY());
                assertSame(effects(active).get(code).surfaceSplashRenderer(), state.surfaceSplashRenderer(),
                        "retained producer-supplied resource remains observably nondefault: " + code);
            });
            var ordinaryBefore = ordinaryDust();
            String mainCode = players().getFirst().getCode();
            assertTrue(ordinaryBefore.get(mainCode).activeLastTick(), "actual grounded spindash dust was updated");
            assertNotEquals(constructorOrdinary.get(mainCode), ordinaryBefore.get(mainCode),
                    "prerequisite: a wholesale ordinary-dust reset would now be observable");

            // Exercise resetForMissingSnapshot via genuine late registration, not a dropped key.
            ownerRegistry.restore(beforeRegistration);
            var reset = ownerRegistry.capture();
            assertEquals(Set.of(EFFECTS_KEY), reset.entries().keySet());
            assertEquals(ordinaryBefore, ordinaryDust(), "legacy ordinary four-field state remains owned separately");
            assertDefaults(effects(reset));
        }
    }

    @ParameterizedTest(name = "missing captured participant {0}")
    @ValueSource(ints = {0, 1})
    void missingCapturedParticipantIsRefusedBeforeAnySupplementaryStateIsApplied(int removedIndex) throws Exception {
        try (var session = new GameplayCaptureSession(SETTINGS)) {
            boot(session);
            awaitNaturalSurfaceSplash(session);
            var ownerRegistry = new RewindRegistry();
            SpriteManagerInternalAccess.registerDustEffectsRewind(GameServices.sprites(), ownerRegistry);
            var captured = ownerRegistry.capture();
            var capturedEffects = effects(captured);
            requireActiveSurface(capturedEffects);
            var originalPlayers = players();
            // Both genuine participants are independently removed. Thus either
            // possible captured-map iteration order must validate the missing one
            // before applying the other participant's observable saved effect.
            var survivingPlayer = originalPlayers.get(1 - removedIndex);
            String survivor = survivingPlayer.getCode();
            String removed = originalPlayers.get(removedIndex).getCode();

            // Produce genuine constructor defaults through the ROM art-rebuild owner.
            GameServices.level().refreshPlayableSpriteArt();
            var beforeRefusal = currentEffects();
            assertFalse(beforeRefusal.get(survivor).surfaceSplashActive());
            assertNotEquals(capturedEffects.get(survivor), beforeRefusal.get(survivor),
                    "prerequisite: applying the captured state to the survivor would be observable");
            var survivorController = survivingPlayer.getSpindashDustController();
            var ordinaryBefore = survivorController.captureRewindState();
            assertTrue(GameServices.sprites().removeSprite(removed), "real public roster removal");
            assertNull(GameServices.sprites().getSprite(removed));

            var refusal = assertThrows(IllegalStateException.class, () -> ownerRegistry.restore(captured));
            assertEquals("Missing restored fixed-Dust participant: " + removed, refusal.getMessage());
            assertSame(survivorController, survivingPlayer.getSpindashDustController());
            assertEquals(beforeRefusal.get(survivor), survivorController.captureEffectsRewindState(),
                    "validate EVERY captured participant before applying any supplementary value");
            assertEquals(ordinaryBefore, survivorController.captureRewindState());
        }
    }

    private static void boot(GameplayCaptureSession session) throws Exception {
        var rom = RomTestUtils.ensureSonic3kRomAvailable();
        assertNotNull(rom, "prerequisite: original S3K ROM");
        session.boot(rom.toPath(), Sonic3kZoneIds.ZONE_LBZ, 0, SETTINGS);
    }

    private static void awaitNaturalSurfaceSplash(GameplayCaptureSession session) {
        boolean sawTitle = session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        boolean active = false;
        for (int step = 0; step < ENTRY_LIMIT; step++) {
            var sprites = GameServices.sprites();
            var sidekicks = sprites.getRegisteredSidekicks();
            if (session.loop().getCurrentGameMode() == GameMode.LEVEL
                    && sprites.getMainPlayable() != null && sidekicks.size() == 1) {
                var participants = List.of(sprites.getMainPlayable(), sidekicks.getFirst());
                active = participants.stream().allMatch(player -> player.getSpindashDustController() != null
                        && player.getSpindashDustController().isSurfaceSplashActive());
                if (active) break;
            }
            ordinaryFrame(session);
            sawTitle |= session.loop().getCurrentGameMode() == GameMode.TITLE_CARD;
        }
        assertTrue(sawTitle, "prerequisite: actual production title phase was observed");
        assertTrue(active, "prerequisite: real LBZ ground-launch producer activates both fixed-Dust surface effects");
        assertEquals(Sonic3kZoneIds.ZONE_LBZ, GameServices.level().getCurrentZone());
        assertEquals(0, GameServices.level().getCurrentAct());
        for (var player : players()) {
            assertFalse(player.isControlLocked(), "native emergence releases control");
            assertFalse(player.isObjectControlled(), "native emergence releases object ownership");
        }
        // Advance naturally past the initial trigger write: state must include a real animation tick.
        ordinaryFrame(session);
    }

    private static void awaitNaturalOrdinarySpindash(GameplayCaptureSession session) {
        var main = players().getFirst();
        boolean groundedCrouch = false;
        int inputLabel = 0; // external input labels are never engine clocks
        for (; inputLabel < ENTRY_LIMIT; inputLabel++) {
            session.step(new Bk2FrameInput(inputLabel, AbstractPlayableSprite.INPUT_DOWN,
                    0, false, "declared Down hold for natural landing/crouch"));
            session.renderFrame();
            assertSame(main, GameServices.sprites().getMainPlayable(), "prerequisite: same production participant");
            assertFalse(main.getDead(), "prerequisite: unrelated death while establishing grounded crouch");
            if (session.loop().getCurrentGameMode() == GameMode.LEVEL && !main.getAir()
                    && main.getGSpeed() == 0 && main.getCrouching()
                    && main.getAnimationId() == Sonic3kAnimationIds.DUCK.id()
                    && !main.isControlLocked() && !main.isObjectControlled()) {
                groundedCrouch = true;
                break;
            }
        }
        assertTrue(groundedCrouch, "prerequisite: real Down input reaches an unlocked grounded Duck pose");
        // The preceding ordinary frames held no action button, so A has a genuine press edge.
        session.step(new Bk2FrameInput(inputLabel + 1, AbstractPlayableSprite.INPUT_DOWN,
                InputActionMasks.ACTION_A, false, "declared Down plus fresh A press"));
        session.renderFrame();
        assertSame(main, GameServices.sprites().getMainPlayable());
        assertFalse(main.getDead());
        assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
        assertTrue(main.getSpindash(), "prerequisite: ordinary native spindash owner accepted the controller press");
        assertFalse(main.getAir());
        assertTrue(main.getSpindashDustController().captureRewindState().activeLastTick(),
                "prerequisite: the ordinary controller frame produced nondefault dust state");
    }

    private static void ordinaryFrame(GameplayCaptureSession session) {
        session.step(null);
        session.renderFrame(); // normal sprite presentation; no image/log/capture artifact
        assertFalse(GameServices.sprites().getMainPlayable().getDead(), "prerequisite: unrelated death");
    }

    private static List<AbstractPlayableSprite> players() {
        var sprites = GameServices.sprites();
        assertNotNull(sprites.getMainPlayable());
        var sidekicks = sprites.getRegisteredSidekicks();
        assertEquals(1, sidekicks.size(), "declared native Sonic+Tails roster");
        return List.of(sprites.getMainPlayable(), sidekicks.getFirst());
    }

    private static Map<String, SpindashDustController> controllers() {
        var result = new LinkedHashMap<String, SpindashDustController>();
        for (var player : players()) {
            assertNotNull(player.getSpindashDustController());
            result.put(player.getCode(), player.getSpindashDustController());
        }
        return Map.copyOf(result);
    }

    private static Map<String, SpindashDustController.EffectsRewindState> currentEffects() {
        var result = new LinkedHashMap<String, SpindashDustController.EffectsRewindState>();
        controllers().forEach((code, controller) -> result.put(code, controller.captureEffectsRewindState()));
        return Map.copyOf(result);
    }

    private static Map<String, SpindashDustController.RewindState> ordinaryDust() {
        var result = new LinkedHashMap<String, SpindashDustController.RewindState>();
        controllers().forEach((code, controller) -> result.put(code, controller.captureRewindState()));
        return Map.copyOf(result);
    }

    private static Map<String, SpindashDustController.EffectsRewindState> effects(CompositeSnapshot snapshot) {
        var raw = assertInstanceOf(Map.class, snapshot.get(EFFECTS_KEY), "actual registered supplement capture");
        var result = new LinkedHashMap<String, SpindashDustController.EffectsRewindState>();
        raw.forEach((key, value) -> result.put(assertInstanceOf(String.class, key),
                assertInstanceOf(SpindashDustController.EffectsRewindState.class, value)));
        return Map.copyOf(result);
    }

    private static PlayerSpriteRenderer sharedSurfaceRenderer(
            Map<String, SpindashDustController.EffectsRewindState> states) {
        PlayerSpriteRenderer renderer = null;
        for (var state : states.values()) {
            assertNotNull(state.surfaceSplashRenderer(), "real producer supplies ROM-backed surface art");
            assertTrue(state.surfaceSplashRenderer().patternBankCapacity() > 0);
            if (renderer == null) renderer = state.surfaceSplashRenderer();
            else assertSame(renderer, state.surfaceSplashRenderer(), "the real LBZ producer supplies one borrowed resource");
        }
        return renderer;
    }

    private static void requireActiveSurface(Map<String, SpindashDustController.EffectsRewindState> states) {
        assertEquals(2, states.size(), "both actual native participants are captured");
        for (var state : states.values()) {
            assertTrue(state.surfaceSplashActive());
            assertTrue(state.surfaceSplashX() > 0);
            assertEquals(SURFACE_Y, state.surfaceSplashY());
            assertTrue(state.surfaceSplashAnimFrame() > 0, "ordinary frame advanced beyond the producer write");
            assertTrue(state.surfaceSplashTimer() >= 0);
            assertTrue(state.surfaceSplashMappingFrame() >= FIRST_SURFACE_FRAME
                    && state.surfaceSplashMappingFrame() <= LAST_SURFACE_FRAME);
            assertNotNull(state.surfaceSplashRenderer());
        }
    }

    private static void assertDefaults(Map<String, SpindashDustController.EffectsRewindState> states) {
        assertEquals(2, states.size());
        List<Executable> oracles = new ArrayList<>();
        states.forEach((code, state) -> oracles.add(() -> assertAll(code + " constructor supplementary defaults",
                () -> assertFalse(state.splashActive()),
                () -> assertEquals(0, state.splashX()), () -> assertEquals(0, state.splashY()),
                () -> assertFalse(state.splashFacingLeft()),
                () -> assertEquals(0, state.splashFrameIndex()), () -> assertEquals(0, state.splashTick()),
                () -> assertFalse(state.surfaceSplashActive()),
                () -> assertEquals(0, state.surfaceSplashX()), () -> assertEquals(0, state.surfaceSplashY()),
                () -> assertEquals(0, state.surfaceSplashAnimFrame()), () -> assertEquals(0, state.surfaceSplashTimer()),
                () -> assertEquals(FIRST_SURFACE_FRAME, state.surfaceSplashMappingFrame()),
                () -> assertNull(state.surfaceSplashRenderer()))));
        assertAll("missing owner key resets only the supplementary partition", oracles);
    }

    private static List<String> differences(CompositeSnapshot expected, CompositeSnapshot actual) {
        var differences = new ArrayList<String>();
        if (!expected.entries().keySet().equals(actual.entries().keySet()))
            differences.add("registry keys: expected=" + expected.entries().keySet() + " actual=" + actual.entries().keySet());
        var keys = new LinkedHashSet<>(expected.entries().keySet());
        keys.addAll(actual.entries().keySet());
        for (String key : keys) {
            if (!RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)))
                differences.add(key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
        return List.copyOf(differences);
    }
}

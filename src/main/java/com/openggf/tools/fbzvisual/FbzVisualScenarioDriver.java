package com.openggf.tools.fbzvisual;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-closed scenario catalog for the reviewed FBZ visual manifest.
 *
 * <p>Every reviewed native checkpoint has an explicit strategy. Thresholds
 * prove both crossing directions and redraw completion; boss recipes wait on
 * named production routine states; exit/capsule recipes prove their native art
 * queue and object identities. Unknown checkpoints remain fail closed.</p>
 */
public final class FbzVisualScenarioDriver {

    private static final String EXACT_START = "fbz1-start-outdoor";
    private static final Set<String> ACT1_BOUNDARIES = Set.of(
            "fbz1-boundary-1-outdoor", "fbz1-boundary-2-outdoor",
            "fbz1-boundary-3-outdoor", "fbz1-boundary-4-horizontal",
            "fbz1-boundary-5-outdoor", "fbz1-boundary-6-outdoor");
    private static final Set<String> ACT1_ANIPLC = Set.of(
            "fbz1-aniplc-200", "fbz1-aniplc-208", "fbz1-aniplc-210",
            "fbz1-aniplc-230", "fbz1-aniplc-238");
    private static final String SEAMLESS = "fbz-seamless-transition";
    private static final String ACT2_BOUNDARY = "fbz2-boundary-outdoor";
    private static final Set<String> ACT2_PLANE = Set.of(
            "fbz2-plane-transition-entry", "fbz2-plane-reversal-steady");
    private static final String MINIBOSS = "fbz1-miniboss";
    private static final String SUBBOSS = "fbz2-subboss";
    private static final String END_BOSS = "fbz2-end-boss";
    private static final String EXIT = "fbz2-exit";
    private static final String CAPSULE = "fbz2-capsule";

    private final Map<String, ScenarioPlan> plans;

    public FbzVisualScenarioDriver(FbzVisualManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        Map<String, ScenarioPlan> built = new LinkedHashMap<>();
        for (FbzVisualManifest.Recipe recipe : manifest.recipes()) {
            boolean supported = EXACT_START.equals(recipe.id())
                    || ACT1_BOUNDARIES.contains(recipe.id()) || ACT1_ANIPLC.contains(recipe.id())
                    || SEAMLESS.equals(recipe.id()) || ACT2_BOUNDARY.equals(recipe.id())
                    || ACT2_PLANE.contains(recipe.id()) || MINIBOSS.equals(recipe.id())
                    || SUBBOSS.equals(recipe.id()) || END_BOSS.equals(recipe.id())
                    || EXIT.equals(recipe.id()) || CAPSULE.equals(recipe.id());
            String blocker = supported ? null
                    : "strict fixture branch is not implemented; nearest coordinate/frame capture is forbidden";
            int frames = 0;
            String strategy = EXACT_START.equals(recipe.id()) ? "native-start"
                    : ACT1_BOUNDARIES.contains(recipe.id()) ? "act1-bidirectional-boundary"
                    : ACT1_ANIPLC.contains(recipe.id()) ? "act1-aniplc"
                    : SEAMLESS.equals(recipe.id()) ? "seamless-transition"
                    : ACT2_BOUNDARY.equals(recipe.id()) ? "act2-bidirectional-boundary"
                    : "fbz2-plane-transition-entry".equals(recipe.id()) ? "act2-plane-entry"
                    : "fbz2-plane-reversal-steady".equals(recipe.id()) ? "act2-plane-steady"
                    : MINIBOSS.equals(recipe.id()) ? "act1-miniboss-active"
                    : SUBBOSS.equals(recipe.id()) ? "act2-subboss-active"
                    : END_BOSS.equals(recipe.id()) ? "act2-end-boss-active"
                    : EXIT.equals(recipe.id()) ? "act2-exit-ready"
                    : CAPSULE.equals(recipe.id()) ? "act2-final-capsule"
                    : "blocked";
            built.put(recipe.id(), new ScenarioPlan(
                    recipe.id(), recipe.act() - 1, frames, supported, blocker, recipe,
                    fixtureFor(strategy, recipe), strategy));
        }
        plans = Map.copyOf(built);
    }

    public ScenarioPlan plan(String checkpointId) {
        ScenarioPlan plan = plans.get(checkpointId);
        if (plan == null) {
            throw new IllegalArgumentException("Unknown FBZ visual checkpoint: " + checkpointId);
        }
        return plan;
    }

    public Map<String, ScenarioPlan> plans() {
        return plans;
    }

    private static FbzVisualFixture.Mutation exactStartFixture(FbzVisualManifest.Recipe recipe) {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("zone", 4);
        expected.put("act", 1);
        expected.put("player_x", recipe.centreX());
        expected.put("player_y", recipe.centreY());
        expected.put("level_frame_counter", 0);
        expected.put("foreground_region", requiredNumber(recipe, "Events_bg_00"));
        expected.put("foreground_outdoor", rawFlag(recipe, "Events_bg_02"));
        expected.put("background_outdoor", rawFlag(recipe, "Events_bg_04"));
        expected.put("events_routine_bg", requiredNumber(recipe, "Events_routine_bg"));
        return new FbzVisualFixture.Mutation(expected, Map.of());
    }

    private static FbzVisualFixture.Mutation fixtureFor(String strategy, FbzVisualManifest.Recipe recipe) {
        return switch (strategy) {
            case "native-start" -> exactStartFixture(recipe);
            case "act1-bidirectional-boundary" -> boundaryFixture(recipe);
            case "act1-aniplc" -> aniPlcFixture(recipe);
            case "seamless-transition" -> seamlessFixture(recipe);
            case "act2-bidirectional-boundary" -> act2BoundaryFixture(recipe);
            case "act2-plane-entry" -> act2PlaneFixture(recipe, false);
            case "act2-plane-steady" -> act2PlaneFixture(recipe, true);
            case "act1-miniboss-active" -> minibossFixture(recipe);
            case "act2-subboss-active" -> subbossFixture(recipe);
            case "act2-end-boss-active" -> endBossFixture(recipe);
            case "act2-exit-ready" -> exitFixture(recipe);
            case "act2-final-capsule" -> capsuleFixture(recipe);
            default -> new FbzVisualFixture.Mutation(Map.of(), Map.of());
        };
    }

    private static FbzVisualFixture.Mutation minibossFixture(FbzVisualManifest.Recipe recipe) {
        Map<String, Object> writes = basePositionWrites(recipe);
        writes.put("camera_x", recipe.setup().path("camera").path("x").asInt());
        writes.put("camera_y", recipe.setup().path("camera").path("y").asInt());
        writes.put("camera_min_x", scalar(recipe, "camera_min_x"));
        writes.put("camera_max_x", scalar(recipe, "camera_max_x"));
        writes.put("camera_max_y", scalar(recipe, "camera_target_max_y"));
        writes.put("camera_target_max_y", scalar(recipe, "camera_target_max_y"));
        writes.put("level_frame_counter", 0);
        return new FbzVisualFixture.Mutation(Map.of("zone", 4, "act", 1), writes);
    }

    private static FbzVisualFixture.Mutation subbossFixture(FbzVisualManifest.Recipe recipe) {
        Map<String, Object> writes = basePositionWrites(recipe);
        writes.put("camera_x", recipe.setup().path("camera").path("x").asInt());
        writes.put("camera_y", recipe.setup().path("camera").path("y").asInt());
        writes.put("camera_min_x", scalar(recipe, "camera_min_x"));
        writes.put("camera_max_y", scalar(recipe, "camera_target_max_y"));
        writes.put("camera_target_max_y", scalar(recipe, "camera_target_max_y"));
        writes.put("level_frame_counter", 0);
        return new FbzVisualFixture.Mutation(Map.of("zone", 4, "act", 2), writes);
    }

    private static FbzVisualFixture.Mutation endBossFixture(FbzVisualManifest.Recipe recipe) {
        Map<String, Object> writes = basePositionWrites(recipe);
        writes.put("camera_x", recipe.setup().path("camera").path("x").asInt());
        writes.put("camera_y", recipe.setup().path("camera").path("y").asInt());
        writes.put("boss_event_setup_attempted", true);
        writes.put("events_routine_fg", requiredNumber(recipe, "Events_routine_fg"));
        writes.put("events_routine_bg", requiredNumber(recipe, "Events_routine_bg"));
        writes.put("plane_assignment", "FG=B,BG=A");
        writes.put("collision_mode", "FOREGROUND_AND_BACKGROUND");
        writes.put("screen_shake_active", rawFlag(recipe, "screen_shake_flag"));
        writes.put("boss_load_position_adjustment_pending", rawFlag(recipe, "Events_bg_06"));
        writes.put("camera_max_x", scalar(recipe, "camera_max_x"));
        writes.put("camera_target_max_x", scalar(recipe, "camera_max_x"));
        writes.put("level_frame_counter", 0);
        return new FbzVisualFixture.Mutation(Map.of("zone", 4, "act", 2), writes);
    }

    private static FbzVisualFixture.Mutation exitFixture(FbzVisualManifest.Recipe recipe) {
        Map<String, Object> writes = basePositionWrites(recipe);
        writes.put("camera_x", recipe.setup().path("camera").path("x").asInt());
        writes.put("boss_event_setup_attempted", true);
        writes.put("events_routine_fg", requiredNumber(recipe, "Events_routine_fg"));
        writes.put("events_routine_bg", requiredNumber(recipe, "Events_routine_bg"));
        writes.put("plane_assignment", Objects.toString(recipe.state().get("plane_assignment")));
        writes.put("collision_mode", "FOREGROUND_ONLY");
        writes.put("screen_shake_active", rawFlag(recipe, "screen_shake_flag"));
        writes.put("boss_active", rawFlag(recipe, "boss_flag"));
        writes.put("boss_defeated", Boolean.TRUE.equals(recipe.state().get("end_boss_defeated")));
        writes.put("level_frame_counter", 0);
        return new FbzVisualFixture.Mutation(Map.of("zone", 4, "act", 2), writes);
    }

    private static FbzVisualFixture.Mutation capsuleFixture(FbzVisualManifest.Recipe recipe) {
        Map<String, Object> writes = basePositionWrites(recipe);
        writes.put("camera_y", recipe.setup().path("camera").path("y").asInt());
        writes.put("boss_active", rawFlag(recipe, "boss_flag"));
        writes.put("level_frame_counter", 0);
        return new FbzVisualFixture.Mutation(Map.of("zone", 4, "act", 2), writes);
    }

    private static FbzVisualFixture.Mutation boundaryFixture(FbzVisualManifest.Recipe recipe) {
        Map<String, Object> writes = new LinkedHashMap<>();
        writes.put("player_x", recipe.centreX());
        writes.put("player_y", recipe.centreY());
        writes.put("foreground_region", requiredNumber(recipe, "Events_bg_00"));
        writes.put("foreground_outdoor", rawFlag(recipe, "Events_bg_02"));
        writes.put("background_outdoor", rawFlag(recipe, "Events_bg_04"));
        writes.put("events_routine_bg", requiredNumber(recipe, "Events_routine_bg"));
        writes.put("level_frame_counter", 0);
        return new FbzVisualFixture.Mutation(Map.of("zone", 4, "act", 1), writes);
    }

    private static FbzVisualFixture.Mutation aniPlcFixture(FbzVisualManifest.Recipe recipe) {
        int channel = recipe.setup().path("timers").path("ani_channel").asInt(-1);
        if (channel < 0) throw new IllegalArgumentException("Missing AniPLC channel for " + recipe.id());
        Map<String, Object> writes = new LinkedHashMap<>();
        writes.put("player_x", recipe.centreX());
        writes.put("player_y", recipe.centreY());
        writes.put("background_outdoor", rawFlag(recipe, "Events_bg_04"));
        return new FbzVisualFixture.Mutation(Map.of("zone", 4, "act", 1), writes);
    }

    private static FbzVisualFixture.Mutation seamlessFixture(FbzVisualManifest.Recipe recipe) {
        Map<String, Object> writes = basePositionWrites(recipe);
        writes.put("camera_x", 0x2E00);
        writes.put("events_routine_bg", requiredNumber(recipe, "Events_routine_bg"));
        writes.put("events_fg_5", rawFlag(recipe, "Events_fg_5"));
        writes.put("level_frame_counter", 0);
        return new FbzVisualFixture.Mutation(Map.of("zone", 4, "act", 1), writes);
    }

    private static FbzVisualFixture.Mutation act2BoundaryFixture(FbzVisualManifest.Recipe recipe) {
        Map<String, Object> writes = basePositionWrites(recipe);
        writes.put("foreground_region", 0);
        writes.put("foreground_outdoor", false);
        writes.put("background_outdoor", rawFlag(recipe, "Events_bg_04"));
        writes.put("events_routine_fg", requiredNumber(recipe, "Events_fg_00"));
        writes.put("events_routine_bg", requiredNumber(recipe, "Events_routine_bg"));
        writes.put("level_frame_counter", 0);
        return new FbzVisualFixture.Mutation(Map.of("zone", 4, "act", 2), writes);
    }

    private static FbzVisualFixture.Mutation act2PlaneFixture(FbzVisualManifest.Recipe recipe,
                                                              boolean steady) {
        Map<String, Object> writes = basePositionWrites(recipe);
        writes.put("camera_x", recipe.setup().path("camera").path("x").asInt());
        writes.put("events_routine_fg", 0);
        writes.put("events_routine_bg", 4);
        writes.put("plane_assignment", "FG=A,BG=B");
        writes.put("collision_mode", "FOREGROUND_ONLY");
        writes.put("screen_shake_active", false);
        writes.put("boss_event_setup_attempted", true);
        if (steady) {
            writes.remove("events_routine_fg");
            writes.remove("events_routine_bg");
            writes.remove("plane_assignment");
            writes.remove("collision_mode");
            writes.remove("screen_shake_active");
            writes.put("events_routine_fg", requiredNumber(recipe, "Events_routine_fg"));
            writes.put("events_routine_bg", requiredNumber(recipe, "Events_routine_bg"));
            writes.put("plane_assignment", Objects.toString(recipe.state().get("plane_assignment")));
            writes.put("collision_mode", "FOREGROUND_AND_BACKGROUND");
            writes.put("screen_shake_active", rawFlag(recipe, "screen_shake_flag"));
            writes.put("screen_shake_offset", recipe.setup().path("timers")
                    .path("screen_shake_offset").asInt());
        }
        writes.put("level_frame_counter", 0);
        return new FbzVisualFixture.Mutation(Map.of("zone", 4, "act", 2), writes);
    }

    private static Map<String, Object> basePositionWrites(FbzVisualManifest.Recipe recipe) {
        Map<String, Object> writes = new LinkedHashMap<>();
        writes.put("player_x", recipe.centreX());
        writes.put("player_y", recipe.centreY());
        return writes;
    }

    private static int requiredNumber(FbzVisualManifest.Recipe recipe, String key) {
        Object value = recipe.state().get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("FBZ recipe " + recipe.id()
                    + " requires numeric " + key + ", got " + value);
        }
        return number.intValue();
    }

    private static boolean rawFlag(FbzVisualManifest.Recipe recipe, String key) {
        return requiredNumber(recipe, key) != 0;
    }

    private static int scalar(FbzVisualManifest.Recipe recipe, String key) {
        Object value = recipe.state().get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            String digits = text.trim();
            int radix = 10;
            if (digits.startsWith("$")) {
                digits = digits.substring(1);
                radix = 16;
            } else if (digits.startsWith("0x") || digits.startsWith("0X")) {
                digits = digits.substring(2);
                radix = 16;
            }
            try {
                return Integer.parseInt(digits, radix);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("FBZ recipe " + recipe.id()
                        + " has invalid scalar " + key + ": " + value, invalid);
            }
        }
        throw new IllegalArgumentException("FBZ recipe " + recipe.id()
                + " requires scalar " + key + ", got " + value);
    }

    public record ScenarioPlan(
            String checkpointId,
            int zeroBasedAct,
            int framesToAdvance,
            boolean captureSupported,
            String blocker,
            FbzVisualManifest.Recipe recipe,
            FbzVisualFixture.Mutation fixtureMutation,
            String strategy) {
    }
}

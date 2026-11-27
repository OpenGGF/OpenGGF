package com.openggf.mods.mutators;

import com.openggf.game.mutators.RingHungerMutatorPolicy;
import com.openggf.game.mutators.StartingStateMutatorPolicy;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Bounds, composition and capture-request parsing for the starting-state and hunger mutators. */
class TestStartingStateMutatorPolicies {
    private static final Set<MutatorCapability> ALL = EnumSet.allOf(MutatorCapability.class);

    @Test
    void recordsEnforceNativeCounterBounds() {
        assertDoesNotThrow(() -> new StartingStatePolicy.Rings(0, 0));
        assertDoesNotThrow(() -> new StartingStatePolicy.Rings(999, 600));
        assertThrows(IllegalArgumentException.class, () -> new StartingStatePolicy.Rings(1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new StartingStatePolicy.Rings(5, -1));
        assertDoesNotThrow(() -> new StartingStatePolicy.Time(1));
        assertDoesNotThrow(() -> new StartingStatePolicy.Time(570));
        assertThrows(IllegalArgumentException.class, () -> new StartingStatePolicy.Time(0));
        assertThrows(IllegalArgumentException.class, () -> new StartingStatePolicy.Time(571), "9:31 would leave under 30 s");
        assertDoesNotThrow(() -> new StartingStatePolicy.Score(999_990));
        assertThrows(IllegalArgumentException.class, () -> new StartingStatePolicy.Score(25_005), "native units of ten");
        assertThrows(IllegalArgumentException.class, () -> new StartingStatePolicy.Score(1_000_000));
        assertThrows(IllegalArgumentException.class, () -> new StartingStatePolicy.Lives(0));
        assertThrows(IllegalArgumentException.class, () -> new StartingStatePolicy.Lives(100));
        assertDoesNotThrow(() -> new RingHungerPolicy(30, 1, 1, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new RingHungerPolicy(30, 0, 1, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new RingHungerPolicy(30, 1, 0, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new RingHungerPolicy(30, 1, 1, 0, 0));
    }

    @Test
    void scoreAndLivesCapabilitiesRequireALaunchBoundary() {
        assertEquals(MutatorScope.LAUNCH, MutatorCapability.STARTING_SCORE.minimumScope());
        assertEquals(MutatorScope.LAUNCH, MutatorCapability.STARTING_LIVES.minimumScope());
        assertThrows(IllegalArgumentException.class, () -> new MutatorDefinition("score", "Score", "",
                MutatorScope.LIVE, MutatorScope.LIVE, List.of(), Set.of(MutatorCapability.STARTING_SCORE),
                values -> List.of(new StartingStatePolicy.Score(0))), "a live toggle cannot seed a session value");
        assertDoesNotThrow(() -> new MutatorDefinition("rings", "Rings", "",
                MutatorScope.LIVE, MutatorScope.LIVE, List.of(), Set.of(MutatorCapability.STARTING_RINGS),
                values -> List.of(new StartingStatePolicy.Rings(50, 0))));
    }

    @Test
    void effectiveProjectionTakesTheFirstAvailableContributionAndRespectsAvailability() {
        var policies = new LinkedHashMap<String, List<MutatorPolicy>>();
        policies.put("a:rings", List.of(new StartingStatePolicy.Rings(85, 3)));
        policies.put("b:rings", List.of(new StartingStatePolicy.Rings(10, 0)));
        policies.put("a:time", List.of(new StartingStatePolicy.Time(270)));
        policies.put("a:score", List.of(new StartingStatePolicy.Score(25_000)));
        policies.put("a:lives", List.of(new StartingStatePolicy.Lives(7)));
        policies.put("a:hunger", List.of(new RingHungerPolicy(30, 2, 3, 4, 5)));
        var effective = new MutatorSessionState.Effective(1, policies);
        assertEquals(new StartingStateMutatorPolicy(85, 3, 270, 25_000, 7), effective.startingState(ALL));
        assertEquals(RingHungerMutatorPolicy.ofSeconds(30, 2, 3, 4, 5), effective.ringHunger(ALL));
        assertEquals(StartingStateMutatorPolicy.STOCK, effective.startingState(Set.of()), "unsupported cell stays stock");
        assertEquals(RingHungerMutatorPolicy.STOCK, effective.ringHunger(Set.of()));
        var timeOnly = effective.startingState(Set.of(MutatorCapability.STARTING_TIME));
        assertEquals(-1, timeOnly.rings());
        assertEquals(270, timeOnly.timeSeconds());
        assertEquals(StartingStateMutatorPolicy.STOCK, new MutatorSessionState.Effective(2, Map.of()).startingState(ALL));
    }

    private static List<OwnedMutator> catalogue() {
        var slider = new MutatorOption.IntegerSlider("rings", "Rings", "", MutatorScope.LIVE, 50, 0, 999, 1, "");
        var delay = new MutatorOption.IntegerSlider("delay", "Delay", "", MutatorScope.LIVE, 0, 0, 300, 1, "");
        var check = new MutatorOption.Checkbox("flag", "Flag", "", MutatorScope.LIVE, false);
        var choice = new MutatorOption.Choice("mode", "Mode", "", MutatorScope.LIVE, "one", List.of("one", "two"));
        var rings = new MutatorDefinition("start-rings", "Start with Rings", "", MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(slider, delay, check, choice), Set.of(MutatorCapability.STARTING_RINGS),
                values -> List.of(new StartingStatePolicy.Rings(values.integer("rings"), values.integer("delay"))));
        var time = new MutatorDefinition("start-time", "Start with Time", "", MutatorScope.LIVE, MutatorScope.LIVE,
                List.of(new MutatorOption.IntegerSlider("seconds", "Time", "", MutatorScope.LIVE, 300, 1, 570, 1, "")),
                Set.of(MutatorCapability.STARTING_TIME), values -> List.of(new StartingStatePolicy.Time(values.integer("seconds"))));
        return List.of(new OwnedMutator("lab", rings), new OwnedMutator("lab", time));
    }

    @Test
    void launchRequestsEnableNamedMutatorsAndConvertOptionsByWidgetType() {
        var requests = MutatorLaunchRequests.parse(List.of("start-rings:rings=85,delay=2,flag=on,mode=two", "lab/start-time"));
        var applied = requests.applyTo(catalogue(), Map.of());
        var rings = applied.get("lab:start-rings");
        assertTrue(rings.enabled());
        assertEquals(85, rings.options().get("rings"));
        assertEquals(2, rings.options().get("delay"));
        assertEquals(Boolean.TRUE, rings.options().get("flag"));
        assertEquals("two", rings.options().get("mode"));
        var time = applied.get("lab:start-time");
        assertTrue(time.enabled(), "an owner/local id without options enables the defaults");
        assertEquals(300, time.options().get("seconds"));
    }

    @Test
    void launchRequestsRejectUnknownIdsOptionsAndInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> MutatorLaunchRequests.parse(List.of("start-rings:rings")));
        assertThrows(IllegalArgumentException.class, () -> MutatorLaunchRequests.parse(List.of("start-rings:")));
        assertThrows(IllegalArgumentException.class, () -> MutatorLaunchRequests.parse(List.of("start-rings:rings=1,rings=2")));
        assertThrows(IllegalArgumentException.class, () -> MutatorLaunchRequests.parse(List.of("start-rings", "start-rings")));
        var unknown = assertThrows(IllegalArgumentException.class, () -> MutatorLaunchRequests.parse(List.of("nope"))
                .applyTo(catalogue(), Map.of()));
        assertTrue(unknown.getMessage().contains("start-rings"), "the error lists the available ids");
        assertThrows(IllegalArgumentException.class, () -> MutatorLaunchRequests.parse(List.of("start-rings:count=3"))
                .applyTo(catalogue(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MutatorLaunchRequests.parse(List.of("start-rings:rings=1000"))
                .applyTo(catalogue(), Map.of()), "slider bounds still apply");
        assertThrows(IllegalArgumentException.class, () -> MutatorLaunchRequests.parse(List.of("start-rings:rings=many"))
                .applyTo(catalogue(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MutatorLaunchRequests.parse(List.of("start-rings:flag=maybe"))
                .applyTo(catalogue(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MutatorLaunchRequests.parse(List.of("start-rings:mode=three"))
                .applyTo(catalogue(), Map.of()));
    }
}

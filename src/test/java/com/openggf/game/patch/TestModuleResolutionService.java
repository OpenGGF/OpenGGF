package com.openggf.game.patch;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModBackedGamePatch;
import com.openggf.mods.code.ModRegistrationPlan;
import com.openggf.mods.code.ModZoneContribution;
import com.openggf.mods.code.PreparedModZone;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestModuleResolutionService {

    interface PatchTrail {
        List<String> appliedPatchIds();
    }

    private static GameplayLaunchRequest anyRequest() {
        return new GameplayLaunchRequest("s2", "sonic", List.of());
    }

    private static GamePatch stubPatch(String id, String baseGameId, boolean activates) {
        return patch(id, baseGameId, request -> activates, Set.of(), false);
    }

    private static GamePatch patch(String id, String baseGameId,
            java.util.function.Predicate<GameplayLaunchRequest> activation,
            Set<LogicalRom> prerequisites, boolean throwOnApply) {
        return new GamePatch() {
            @Override public String id() { return id; }
            @Override public String displayName() { return id; }
            @Override public String baseGameId() { return baseGameId; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) {
                return activation.test(request);
            }
            @Override public Set<LogicalRom> romPrerequisites() { return prerequisites; }
            @Override public List<String> providedMainCharacters() { return List.of(); }
            @Override public GameModule apply(GameModule base, PatchContext ctx) {
                if (throwOnApply) {
                    throw new IllegalStateException("apply failed: " + id);
                }
                List<String> trail = new ArrayList<>(
                        base instanceof PatchTrail t ? t.appliedPatchIds() : List.of());
                trail.add(id);
                class Patched extends DelegatingGameModule implements PatchTrail {
                    Patched() { super(base, id); }
                    @Override public List<String> appliedPatchIds() { return List.copyOf(trail); }
                }
                return new Patched();
            }
        };
    }

    @Test
    void zeroSurvivorsReturnsBaseInstanceUnchanged() {
        ModuleResolutionService service = ModuleResolutionService.forTests(PatchEnablement.ALL_ENABLED);
        GameModule base = new Sonic2GameModule();
        ResolutionContext context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, List.of(
                new RegisteredPatch(new PatchOwner.Mod("test"), "test:p1",
                        stubPatch("p1", "s2", false), 0)), Map.of());

        assertSame(base, ((ResolutionResult.Resolved)
                service.resolve(context, base, anyRequest())).module());
    }

    @Test
    void survivorsComposeInEnablementThenRegistrationOrder() {
        PatchOwner builtin = new PatchOwner.BuiltIn("builtin");
        PatchOwner modB = new PatchOwner.Mod("mod-b");
        PatchOwner modA = new PatchOwner.Mod("mod-a");
        PatchEnablement policy = new PatchEnablement() {
            @Override public boolean isEnabled(PatchOwner owner) { return !owner.equals(modB); }
            @Override public int orderOf(PatchOwner owner) {
                return owner instanceof PatchOwner.BuiltIn ? -1 : owner.equals(modA) ? 0 : 1;
            }
        };
        ModuleResolutionService service = ModuleResolutionService.forTests(policy);
        ResolutionContext context = ResolutionContext.forTests(policy, List.of(
                new RegisteredPatch(builtin, "builtin", stubPatch("builtin", "s2", true), 0),
                new RegisteredPatch(modB, "mod-b:patch", stubPatch("mod-b", "s2", true), 1),
                new RegisteredPatch(modA, "mod-a:patch", stubPatch("mod-a", "s2", true), 2)), Map.of());

        GameModule resolved = ((ResolutionResult.Resolved)
                service.resolve(context, new Sonic2GameModule(), anyRequest())).module();

        assertEquals(List.of("builtin", "mod-a"), ((PatchTrail) resolved).appliedPatchIds());
    }

    @Test
    void wrongBaseGameNeverApplies() {
        ModuleResolutionService service = ModuleResolutionService.forTests(PatchEnablement.ALL_ENABLED);
        GameModule base = new Sonic2GameModule();
        ResolutionContext context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, List.of(
                new RegisteredPatch(new PatchOwner.Mod("test"), "test:p1",
                        stubPatch("p1", "s3k", true), 0)), Map.of());

        assertSame(base, ((ResolutionResult.Resolved)
                service.resolve(context, base, anyRequest())).module());
    }

    @Test
    void requestGameMustMatchRootModuleGame() {
        ResolutionContext context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED,
                List.of(), Map.of());

        assertThrows(IllegalArgumentException.class, () -> ModuleResolutionService
                .forTests(PatchEnablement.ALL_ENABLED)
                .resolve(context, new Sonic2GameModule(),
                        new GameplayLaunchRequest("s1", "sonic", List.of())));
    }

    @Test
    void duplicateNamespacedPatchIdIsRejected() {
        PatchOwner a = new PatchOwner.Mod("a");
        PatchOwner b = new PatchOwner.Mod("b");
        List<RegisteredPatch> patches = List.of(
                new RegisteredPatch(a, "same:id", stubPatch("a", "s2", true), 0),
                new RegisteredPatch(b, "same:id", stubPatch("b", "s2", true), 1));

        assertThrows(IllegalArgumentException.class,
                () -> ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, patches, Map.of()));
    }

    @Test
    void duplicateOwnerAndPatchIdIsRejected() {
        PatchOwner owner = new PatchOwner.Mod("test");
        List<RegisteredPatch> patches = List.of(
                new RegisteredPatch(owner, "test:p", stubPatch("first", "s2", true), 0),
                new RegisteredPatch(owner, "test:p", stubPatch("second", "s2", true), 1));

        assertThrows(IllegalArgumentException.class,
                () -> ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, patches, Map.of()));
    }

    @Test
    void duplicateRegistrationIndexWithinOneOwnerIsRejected() {
        PatchOwner owner = new PatchOwner.Mod("test");
        List<RegisteredPatch> patches = List.of(
                new RegisteredPatch(owner, "test:first", stubPatch("first", "s2", true), 0),
                new RegisteredPatch(owner, "test:second", stubPatch("second", "s2", true), 0));

        assertThrows(IllegalArgumentException.class,
                () -> ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, patches, Map.of()));
    }

    @Test
    void disabledOwnerPredicateIsNeverInvoked() {
        PatchOwner disabled = new PatchOwner.Mod("disabled");
        PatchEnablement policy = new PatchEnablement() {
            @Override public boolean isEnabled(PatchOwner owner) { return !owner.equals(disabled); }
            @Override public int orderOf(PatchOwner owner) { return 0; }
        };
        AtomicInteger calls = new AtomicInteger();
        GamePatch throwingPredicate = patch("bad", "s2", request -> {
            calls.incrementAndGet();
            throw new IllegalStateException("must not run");
        }, Set.of(), false);
        ResolutionContext context = ResolutionContext.forTests(policy, List.of(
                new RegisteredPatch(disabled, "disabled:bad", throwingPredicate, 0)), Map.of());
        GameModule base = new Sonic2GameModule();

        ResolutionResult result = ModuleResolutionService.forTests(policy)
                .resolve(context, base, anyRequest());

        assertSame(base, ((ResolutionResult.Resolved) result).module());
        assertEquals(0, calls.get());
        assertTrue(context.failures().isEmpty());
    }

    @Test
    void enablementAndOrderAreFrozenWhenLaunchContextIsCreated() {
        PatchOwner a = new PatchOwner.Mod("a");
        PatchOwner b = new PatchOwner.Mod("b");
        java.util.concurrent.atomic.AtomicBoolean enabled =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicBoolean reverse =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        PatchEnablement mutablePolicy = new PatchEnablement() {
            @Override public boolean isEnabled(PatchOwner owner) { return enabled.get(); }
            @Override public int orderOf(PatchOwner owner) {
                return reverse.get() ? (owner.equals(a) ? 1 : 0) : (owner.equals(a) ? 0 : 1);
            }
        };
        ResolutionContext context = ResolutionContext.forTests(mutablePolicy, List.of(
                new RegisteredPatch(b, "b:p", stubPatch("b", "s2", true), 0),
                new RegisteredPatch(a, "a:p", stubPatch("a", "s2", true), 1)), Map.of());
        enabled.set(false);
        reverse.set(true);

        GameModule resolved = ((ResolutionResult.Resolved) ModuleResolutionService
                .forTests(mutablePolicy).resolve(context, new Sonic2GameModule(), anyRequest())).module();

        assertEquals(List.of("a", "b"), ((PatchTrail) resolved).appliedPatchIds());
    }

    @Test
    void enabledDependentDoesNotApplyWhenDeclaredDependencyIsDisabled() {
        PatchOwner dependency = new PatchOwner.Mod("dependency");
        PatchOwner dependent = new PatchOwner.Mod("dependent");
        PatchEnablement policy = new PatchEnablement() {
            @Override public boolean isEnabled(PatchOwner owner) { return !owner.equals(dependency); }
            @Override public int orderOf(PatchOwner owner) { return 0; }
        };
        ResolutionContext context = ResolutionContext.forTests(policy, List.of(
                new RegisteredPatch(dependent, "dependent:p",
                        stubPatch("dependent", "s2", true), 0),
                new RegisteredPatch(dependency, "dependency:p",
                        stubPatch("dependency", "s2", true), 1)),
                Map.of(dependent, Set.of(dependency)));
        GameModule base = new Sonic2GameModule();

        ResolutionResult result = ModuleResolutionService.forTests(policy)
                .resolve(context, base, anyRequest());

        assertSame(base, ((ResolutionResult.Resolved) result).module());
    }

    @Test
    void metadataFailureFailsOwnerAndTransitiveDependentsButIndependentOwnerContinues() {
        PatchOwner dependency = new PatchOwner.Mod("dependency");
        PatchOwner dependent = new PatchOwner.Mod("dependent");
        PatchOwner transitive = new PatchOwner.Mod("transitive");
        PatchOwner independent = new PatchOwner.Mod("independent");
        GamePatch badMetadata = patch("bad", "s2", request -> {
            throw new IllegalStateException("metadata failed");
        }, Set.of(), false);
        ResolutionContext context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, List.of(
                new RegisteredPatch(transitive, "transitive:p", stubPatch("transitive", "s2", true), 0),
                new RegisteredPatch(dependent, "dependent:p", stubPatch("dependent", "s2", true), 1),
                new RegisteredPatch(dependency, "dependency:bad", badMetadata, 2),
                new RegisteredPatch(independent, "independent:p", stubPatch("independent", "s2", true), 3)),
                Map.of(dependent, Set.of(dependency), transitive, Set.of(dependent)));

        ResolutionResult.Resolved result = (ResolutionResult.Resolved) ModuleResolutionService
                .forTests(PatchEnablement.ALL_ENABLED).resolve(
                        context, new Sonic2GameModule(), anyRequest());
        GameModule resolved = result.module();

        assertEquals(List.of("independent"), ((PatchTrail) resolved).appliedPatchIds());
        assertEquals(Set.of(dependency, dependent, transitive), context.failedOwners());
        assertEquals(Set.of(dependency, dependent, transitive), result.ownerFailures().keySet());
        assertInstanceOf(IllegalStateException.class, context.failures().get(dependency));
    }

    @Test
    void unsupportedZoneHostDisablesOwnerAndDependentsWithoutPublishingZone() {
        PatchOwner.Mod owner = new PatchOwner.Mod("zone-owner");
        PatchOwner dependent = new PatchOwner.Mod("dependent");
        ModZoneContribution declaration = new ModZoneContribution(
                "sky", new BakedLevelRef("sky/level.json"), null, null);
        PreparedModZone prepared = new PreparedModZone(
                "zone-owner", "sky", null, null, null,
                "SKY", 0x400, 0x40, 0, 0);
        ModRegistrationPlan plan = new ModRegistrationPlan(
                "zone-owner", "s1", Map.of(), Map.of(), Map.of(), List.of(),
                List.of(declaration), List.of(prepared));
        ModBackedGamePatch backing = new ModBackedGamePatch(plan);
        List<RegisteredPatch> registrations = new ArrayList<>(
                ModPatchPlanAssembler.backingFirst(owner, backing, List.of()));
        registrations.add(new RegisteredPatch(dependent, "dependent:p",
                stubPatch("dependent", "s1", true), 0));
        ResolutionContext context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED,
                registrations, Map.of(dependent, Set.of(owner)));
        GameModule base = new com.openggf.game.sonic1.Sonic1GameModule();

        ResolutionResult.Resolved result = (ResolutionResult.Resolved) ModuleResolutionService
                .forTests(PatchEnablement.ALL_ENABLED)
                .resolve(context, base, new GameplayLaunchRequest("s1", "sonic", List.of()));

        assertSame(base, result.module());
        assertEquals(Set.of(owner, dependent), context.failedOwners());
        assertEquals(Set.of(owner, dependent), result.ownerFailures().keySet());
        assertTrue(result.module().getZoneRegistry()
                .resolveZoneKey(com.openggf.game.ZoneKey.mod("zone-owner", "sky")).isEmpty());
    }

    @Test
    void dependencyAppliesBeforeDependentEvenWhenDependentRegisteredFirst() {
        PatchOwner dependency = new PatchOwner.Mod("dependency");
        PatchOwner dependent = new PatchOwner.Mod("dependent");
        ResolutionContext context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, List.of(
                new RegisteredPatch(dependent, "dependent:p",
                        stubPatch("dependent", "s2", true), 0),
                new RegisteredPatch(dependency, "dependency:p",
                        stubPatch("dependency", "s2", true), 1)),
                Map.of(dependent, Set.of(dependency)));

        GameModule resolved = ((ResolutionResult.Resolved) ModuleResolutionService
                .forTests(PatchEnablement.ALL_ENABLED)
                .resolve(context, new Sonic2GameModule(), anyRequest())).module();

        assertEquals(List.of("dependency", "dependent"),
                ((PatchTrail) resolved).appliedPatchIds());
    }

    @Test
    void unmetRomPrerequisiteSkipsPatchWithoutFailingOwner() {
        PatchOwner owner = new PatchOwner.Mod("test");
        ResolutionContext context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, List.of(
                new RegisteredPatch(owner, "test:p", patch("p", "s2", request -> true,
                        Set.of(LogicalRom.SK), false), 0)), Map.of());
        GameModule base = new Sonic2GameModule();

        ResolutionResult result = ModuleResolutionService.forTests(
                PatchEnablement.ALL_ENABLED, rom -> false).resolve(context, base, anyRequest());

        assertSame(base, ((ResolutionResult.Resolved) result).module());
        assertTrue(context.failures().isEmpty());
    }

    @Test
    void availableMainCharactersReflectEnabledOwnersBaseGameAndPrerequisites() {
        PatchOwner enabled = new PatchOwner.Mod("enabled");
        PatchOwner disabled = new PatchOwner.Mod("disabled");
        PatchEnablement policy = new PatchEnablement() {
            @Override public boolean isEnabled(PatchOwner owner) { return !owner.equals(disabled); }
            @Override public int orderOf(PatchOwner owner) { return 0; }
        };
        GamePatch available = characterPatch("available", "s2", Set.of(), "amy", "amy");
        GamePatch duplicate = characterPatch("duplicate", "s2", Set.of(), "amy", "mighty");
        GamePatch missingRom = characterPatch("missing", "s2", Set.of(LogicalRom.SK), "knuckles");
        GamePatch wrongGame = characterPatch("wrong", "s1", Set.of(), "tails");
        ResolutionContext context = ResolutionContext.forTests(policy, List.of(
                new RegisteredPatch(enabled, "enabled:available", available, 0),
                new RegisteredPatch(enabled, "enabled:duplicate", duplicate, 1),
                new RegisteredPatch(enabled, "enabled:missing", missingRom, 2),
                new RegisteredPatch(enabled, "enabled:wrong", wrongGame, 3),
                new RegisteredPatch(disabled, "disabled:p",
                        characterPatch("disabled", "s2", Set.of(), "ray"), 4)), Map.of());

        List<String> characters = ModuleResolutionService.forTests(policy, rom -> false)
                .availableMainCharacters(context, "s2");

        assertEquals(List.of("amy", "mighty"), characters);
        assertTrue(context.failures().isEmpty());
    }

    @Test
    void arbitraryCreatorApplyFailureReturnsTypedLaunchAbortAndFailsDependents() {
        PatchOwner failing = new PatchOwner.Mod("failing");
        PatchOwner dependent = new PatchOwner.Mod("dependent");
        ResolutionContext context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, List.of(
                new RegisteredPatch(failing, "failing:bad",
                        patch("bad", "s2", request -> true, Set.of(), true), 0),
                new RegisteredPatch(dependent, "dependent:p", stubPatch("dependent", "s2", true), 1)),
                Map.of(dependent, Set.of(failing)));

        ResolutionResult result = ModuleResolutionService.forTests(PatchEnablement.ALL_ENABLED)
                .resolve(context, new Sonic2GameModule(), anyRequest());

        ResolutionResult.LaunchAborted aborted = assertInstanceOf(
                ResolutionResult.LaunchAborted.class, result);
        assertEquals(failing, aborted.failedOwner());
        assertEquals("failing:bad", aborted.patchId());
        assertInstanceOf(IllegalStateException.class, aborted.cause());
        assertEquals(Set.of(failing, dependent), context.failedOwners());
        assertEquals(Set.of(failing, dependent), aborted.failedOwners());
    }

    @Test
    void nonFatalErrorFromCreatorApplyReturnsTypedLaunchAbort() {
        PatchOwner failing = new PatchOwner.Mod("failing");
        GamePatch errorPatch = new GamePatch() {
            @Override public String id() { return "error"; }
            @Override public String displayName() { return "error"; }
            @Override public String baseGameId() { return "s2"; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of(); }
            @Override public GameModule apply(GameModule base, PatchContext context) {
                throw new AssertionError("creator assertion");
            }
        };
        ResolutionContext context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED,
                List.of(new RegisteredPatch(failing, "failing:error", errorPatch, 0)), Map.of());

        ResolutionResult.LaunchAborted result = assertInstanceOf(
                ResolutionResult.LaunchAborted.class,
                ModuleResolutionService.forTests(PatchEnablement.ALL_ENABLED)
                        .resolve(context, new Sonic2GameModule(), anyRequest()));

        assertInstanceOf(AssertionError.class, result.cause());
        assertInstanceOf(AssertionError.class, context.failures().get(failing));
    }

    @Test
    void engineGeneratedDecoratorFailureContinuesFromLastGoodModule() {
        PatchOwner first = new PatchOwner.Mod("first");
        PatchOwner backing = new PatchOwner.Mod("backing");
        PatchOwner last = new PatchOwner.Mod("last");
        RegisteredPatch safeFailure = RegisteredPatch.engineGeneratedDecorator(
                backing, "backing:bad", patch("bad", "s2", request -> true, Set.of(), true), 1);
        ResolutionContext context = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, List.of(
                new RegisteredPatch(first, "first:p", stubPatch("first", "s2", true), 0),
                safeFailure,
                new RegisteredPatch(last, "last:p", stubPatch("last", "s2", true), 2)), Map.of());

        GameModule resolved = ((ResolutionResult.Resolved) ModuleResolutionService
                .forTests(PatchEnablement.ALL_ENABLED).resolve(context, new Sonic2GameModule(), anyRequest())).module();

        assertEquals(List.of("first", "last"), ((PatchTrail) resolved).appliedPatchIds());
        assertEquals(Set.of(backing), context.failedOwners());
    }

    @Test
    void repeatedResolutionFromSameRootDoesNotDoubleWrapStack() {
        PatchOwner a = new PatchOwner.Mod("a");
        PatchOwner b = new PatchOwner.Mod("b");
        List<RegisteredPatch> plan = List.of(
                new RegisteredPatch(a, "a:p", stubPatch("a", "s2", true), 0),
                new RegisteredPatch(b, "b:p", stubPatch("b", "s2", true), 1));
        ModuleResolutionService service = ModuleResolutionService.forTests(PatchEnablement.ALL_ENABLED);
        GameModule root = new Sonic2GameModule();

        GameModule firstLaunch = ((ResolutionResult.Resolved) service.resolve(
                ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, plan, Map.of()), root, anyRequest())).module();
        GameModule secondLaunch = ((ResolutionResult.Resolved) service.resolve(
                ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, plan, Map.of()), root, anyRequest())).module();

        assertEquals(List.of("a", "b"), ((PatchTrail) firstLaunch).appliedPatchIds());
        assertEquals(List.of("a", "b"), ((PatchTrail) secondLaunch).appliedPatchIds());
        assertNotSame(firstLaunch, secondLaunch);
    }

    @Test
    void eachLaunchGetsFreshFailureStateAndServiceNeverAccumulatesModRegistrations() {
        PatchOwner bad = new PatchOwner.Mod("bad");
        ResolutionContext failedLaunch = ResolutionContext.forTests(PatchEnablement.ALL_ENABLED, List.of(
                new RegisteredPatch(bad, "bad:p", patch("p", "s2", request -> {
                    throw new IllegalStateException("boom");
                }, Set.of(), false), 0)), Map.of());
        ModuleResolutionService service = ModuleResolutionService.forTests(PatchEnablement.ALL_ENABLED);
        service.resolve(failedLaunch, new Sonic2GameModule(), anyRequest());

        ResolutionContext cleanLaunch = ResolutionContext.forTests(
                PatchEnablement.ALL_ENABLED, List.of(), Map.of());
        GameModule root = new Sonic2GameModule();

        assertSame(root, ((ResolutionResult.Resolved)
                service.resolve(cleanLaunch, root, anyRequest())).module());
        assertTrue(cleanLaunch.failures().isEmpty());
    }

    @Test
    void serviceKeepsImmutableBuiltInsAndFreezesEachModPlanWithoutAccumulatingIt() {
        RegisteredPatch builtIn = new RegisteredPatch(new PatchOwner.BuiltIn("builtin"),
                "builtin:p", stubPatch("builtin", "s2", true), 0);
        ModuleResolutionService service = new ModuleResolutionService(List.of(builtIn),
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> null),
                SonicConfigurationService.getInstance());
        List<RegisteredPatch> mutablePlan = new ArrayList<>();
        mutablePlan.add(new RegisteredPatch(new PatchOwner.Mod("mod"), "mod:p",
                stubPatch("mod", "s2", true), 0));

        ResolutionContext first = service.newContext(mutablePlan, Map.of());
        mutablePlan.clear();
        ResolutionContext second = service.newContext(List.of(), Map.of());

        assertEquals(2, first.registrations().size());
        assertEquals(List.of(builtIn), second.registrations());
        assertThrows(UnsupportedOperationException.class,
                () -> first.registrations().add(builtIn));
    }

    private static GamePatch characterPatch(String id, String baseGameId,
            Set<LogicalRom> prerequisites, String... characters) {
        return new GamePatch() {
            @Override public String id() { return id; }
            @Override public String displayName() { return id; }
            @Override public String baseGameId() { return baseGameId; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return false; }
            @Override public Set<LogicalRom> romPrerequisites() { return prerequisites; }
            @Override public List<String> providedMainCharacters() { return List.of(characters); }
            @Override public GameModule apply(GameModule base, PatchContext context) { return base; }
        };
    }
}

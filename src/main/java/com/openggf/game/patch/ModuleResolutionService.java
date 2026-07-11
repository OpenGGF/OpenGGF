package com.openggf.game.patch;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Engine-owned patch registry and resolver. Built-ins are immutable; each launch
 * supplies a frozen mod plan through a fresh {@link ResolutionContext}.
 */
public final class ModuleResolutionService {

    public enum LaunchPolicy {
        STANDARD,
        DETERMINISTIC
    }

    /** Supplies a frozen mod contribution plan for one launch. */
    @FunctionalInterface
    public interface PatchPlanSource {
        PatchPlan scan(PatchEnablement enablement);
    }

    public record PatchPlan(List<RegisteredPatch> registrations,
            Map<PatchOwner, ? extends Set<PatchOwner>> ownerDependencies) {
        public PatchPlan {
            registrations = List.copyOf(Objects.requireNonNull(registrations, "registrations"));
            Map<PatchOwner, Set<PatchOwner>> frozenDependencies = new java.util.LinkedHashMap<>();
            Objects.requireNonNull(ownerDependencies, "ownerDependencies")
                    .forEach((owner, dependencies) -> frozenDependencies.put(
                            Objects.requireNonNull(owner, "dependency owner"),
                            Set.copyOf(Objects.requireNonNull(dependencies, "owner dependencies"))));
            ownerDependencies = Map.copyOf(frozenDependencies);
        }

        public static PatchPlan empty() {
            return new PatchPlan(List.of(), Map.of());
        }
    }

    /** One immutable plan/context shared by every decision in a single launch. */
    public static final class PreparedLaunch {
        private final ModuleResolutionService owner;
        private final ResolutionContext context;

        private PreparedLaunch(ModuleResolutionService owner, ResolutionContext context) {
            this.owner = owner;
            this.context = context;
        }
    }

    private final List<RegisteredPatch> builtIns;
    private final PatchEnablement enablement;
    private final Predicate<LogicalRom> prerequisiteAvailable;
    private final PatchContext patchContext;
    private final PatchPlanSource patchPlanSource;
    private volatile PatchEnablement installedEnablement;
    private volatile PatchPlanSource installedPatchPlanSource;

    public ModuleResolutionService(List<RegisteredPatch> builtIns,
            PatchEnablement enablement, LogicalRomResolver logicalRoms,
            SonicConfigurationService configService) {
        this(builtIns, enablement, logicalRoms, configService, ignored -> PatchPlan.empty());
    }

    public ModuleResolutionService(List<RegisteredPatch> builtIns,
            PatchEnablement enablement, LogicalRomResolver logicalRoms,
            SonicConfigurationService configService, PatchPlanSource patchPlanSource) {
        this(builtIns, enablement, logicalRoms::isAvailable,
                new PatchContext(logicalRoms::openOrThrow, configService), patchPlanSource);
    }

    private ModuleResolutionService(List<RegisteredPatch> builtIns,
            PatchEnablement enablement, Predicate<LogicalRom> prerequisiteAvailable,
            PatchContext patchContext, PatchPlanSource patchPlanSource) {
        this.builtIns = List.copyOf(Objects.requireNonNull(builtIns, "builtIns"));
        this.enablement = Objects.requireNonNull(enablement, "enablement");
        this.prerequisiteAvailable = Objects.requireNonNull(
                prerequisiteAvailable, "prerequisiteAvailable");
        this.patchContext = Objects.requireNonNull(patchContext, "patchContext");
        this.patchPlanSource = Objects.requireNonNull(patchPlanSource, "patchPlanSource");
        this.installedEnablement = this.enablement;
        this.installedPatchPlanSource = this.patchPlanSource;
        ResolutionContext.create(enablement, this.builtIns, Map.of());
        for (RegisteredPatch registration : this.builtIns) {
            if (!(registration.owner() instanceof PatchOwner.BuiltIn)) {
                throw new IllegalArgumentException(
                        "Built-in registry contains mod-owned patch " + registration.namespacedId());
            }
        }
    }
    /** Installs the boot-frozen mod policy and fresh-per-launch registration source. */
    public synchronized void installModPlanSource(PatchEnablement enablement,
                                                   PatchPlanSource source) {
        installedEnablement = Objects.requireNonNull(enablement, "enablement");
        installedPatchPlanSource = Objects.requireNonNull(source, "source");
    }

    public static ModuleResolutionService forTests(PatchEnablement enablement) {
        return forTests(enablement, rom -> true);
    }

    static ModuleResolutionService forTests(PatchEnablement enablement,
            Predicate<LogicalRom> prerequisiteAvailable) {
        PatchContext context = new PatchContext(rom -> {
            throw new java.io.IOException("Logical ROM bytes unavailable in test resolver");
        }, SonicConfigurationService.createStandalone());
        return new ModuleResolutionService(List.of(), enablement,
                prerequisiteAvailable, context, ignored -> PatchPlan.empty());
    }

    static ModuleResolutionService forTests(List<RegisteredPatch> registrations,
            PatchEnablement enablement) {
        List<RegisteredPatch> builtIns = registrations.stream()
                .filter(registration -> registration.owner() instanceof PatchOwner.BuiltIn)
                .toList();
        List<RegisteredPatch> mods = registrations.stream()
                .filter(registration -> registration.owner() instanceof PatchOwner.Mod)
                .toList();
        PatchContext context = new PatchContext(rom -> {
            throw new java.io.IOException("Logical ROM bytes unavailable in test resolver");
        }, SonicConfigurationService.createStandalone());
        return new ModuleResolutionService(builtIns, enablement, rom -> true, context,
                ignored -> new PatchPlan(mods, Map.of()));
    }

    /** Creates the fresh per-launch context; mod registrations are never retained. */
    public ResolutionContext newContext(List<RegisteredPatch> frozenModPlan,
            Map<PatchOwner, ? extends Set<PatchOwner>> ownerDependencies) {
        return newContext(enablement, frozenModPlan, ownerDependencies);
    }

    private ResolutionContext newContext(PatchEnablement launchEnablement,
            List<RegisteredPatch> frozenModPlan,
            Map<PatchOwner, ? extends Set<PatchOwner>> ownerDependencies) {
        Objects.requireNonNull(frozenModPlan, "frozenModPlan");
        List<RegisteredPatch> combined = new ArrayList<>(builtIns.size() + frozenModPlan.size());
        combined.addAll(builtIns);
        for (RegisteredPatch registration : frozenModPlan) {
            if (!(registration.owner() instanceof PatchOwner.Mod)) {
                throw new IllegalArgumentException(
                        "Mod plan contains built-in-owned patch " + registration.namespacedId());
            }
            combined.add(registration);
        }
        return ResolutionContext.create(launchEnablement, combined, ownerDependencies);
    }

    /** Resolves one launch from the undecorated root module. */
    public GameModule resolveForLaunch(GameModule root, GameplayLaunchRequest request,
            LaunchPolicy policy) {
        return resolveForLaunch(prepareLaunch(policy), root, request);
    }

    public PreparedLaunch prepareLaunch(LaunchPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        PatchEnablement configuredEnablement = installedEnablement;
        PatchPlanSource configuredSource = installedPatchPlanSource;
        PatchEnablement launchEnablement = policy == LaunchPolicy.DETERMINISTIC
                ? builtInsOnly(configuredEnablement) : configuredEnablement;
        // Deterministic surfaces must not touch external mod storage at all.
        PatchPlan plan = policy == LaunchPolicy.DETERMINISTIC
                ? PatchPlan.empty()
                : Objects.requireNonNull(configuredSource.scan(launchEnablement), "patch plan");
        return new PreparedLaunch(this, newContext(launchEnablement,
                plan.registrations(), plan.ownerDependencies()));
    }

    public GameModule resolveForLaunch(PreparedLaunch launch, GameModule root,
            GameplayLaunchRequest request) {
        ResolutionResult result = resolveForLaunchResult(launch, root, request);
        if (result instanceof ResolutionResult.Resolved resolved) {
            return resolved.module();
        }
        ResolutionResult.LaunchAborted aborted = (ResolutionResult.LaunchAborted) result;
        throw new IllegalStateException("Patch resolution aborted at " + aborted.patchId(),
                aborted.cause());
    }

    /** Returns the typed result so host launch code can abort without opening a session. */
    public ResolutionResult resolveForLaunchResult(PreparedLaunch launch, GameModule root,
            GameplayLaunchRequest request) {
        Objects.requireNonNull(launch, "launch");
        requireOwnedLaunch(launch);
        return resolve(launch.context, root, request);
    }

    public List<String> availableMainCharactersForLaunch(String gameId, LaunchPolicy policy) {
        return availableMainCharacters(prepareLaunch(policy), gameId);
    }

    public List<String> availableMainCharacters(PreparedLaunch launch, String gameId) {
        Objects.requireNonNull(launch, "launch");
        requireOwnedLaunch(launch);
        return availableMainCharacters(launch.context, gameId);
    }

    private void requireOwnedLaunch(PreparedLaunch launch) {
        if (launch.owner != this) {
            throw new IllegalArgumentException("Prepared launch belongs to a different resolver");
        }
    }

    private static PatchEnablement builtInsOnly(PatchEnablement delegate) {
        return new PatchEnablement() {
            @Override
            public boolean isEnabled(PatchOwner owner) {
                return owner instanceof PatchOwner.BuiltIn && delegate.isEnabled(owner);
            }

            @Override
            public int orderOf(PatchOwner owner) {
                return delegate.orderOf(owner);
            }
        };
    }

    public ResolutionResult resolve(ResolutionContext context, GameModule base,
            GameplayLaunchRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(request, "request");
        if (!base.getGameId().code().equals(request.gameId())) {
            throw new IllegalArgumentException("Launch request game " + request.gameId()
                    + " does not match root module " + base.getGameId().code());
        }

        List<RegisteredPatch> ordered = context.topologicallyOrderedFor(base);
        Set<RegisteredPatch> candidates = new HashSet<>();
        for (RegisteredPatch registration : ordered) {
            if (!context.ownerEligible(registration.owner())) {
                continue;
            }
            try {
                GamePatch patch = registration.patch();
                if (!patch.baseGameId().equals(request.gameId())
                        || !patch.activatesFor(request)
                        || !prerequisitesMet(patch)) {
                    continue;
                }
                candidates.add(registration);
            } catch (Throwable failure) {
                rethrowIfFatal(failure);
                context.failOwnerAndDependents(registration.owner(), failure);
            }
        }

        List<RegisteredPatch> survivors = ordered.stream()
                .filter(candidates::contains)
                .filter(registration -> context.ownerEligible(registration.owner()))
                .toList();

        GameModule module = base;
        for (RegisteredPatch registration : survivors) {
            if (!context.ownerEligible(registration.owner())) {
                continue;
            }
            try {
                module = Objects.requireNonNull(
                        registration.patch().apply(module, patchContext),
                        "Patch apply returned null: " + registration.namespacedId());
            } catch (Throwable failure) {
                rethrowIfFatal(failure);
                context.failOwnerAndDependents(registration.owner(), failure);
                if (!registration.engineGeneratedDecoratorOnly()) {
                    return new ResolutionResult.LaunchAborted(registration.owner(),
                            registration.namespacedId(), failure, context.failedOwners());
                }
            }
        }
        return new ResolutionResult.Resolved(module, context.failures());
    }

    /** Patch-backed extra main characters whose owner and ROM metadata are usable. */
    public List<String> availableMainCharacters(ResolutionContext context, String gameId) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(gameId, "gameId");
        List<CharacterContribution> contributions = new ArrayList<>();
        for (RegisteredPatch registration : context.registrations()) {
            if (!context.ownerEligible(registration.owner())) {
                continue;
            }
            try {
                GamePatch patch = registration.patch();
                if (patch.baseGameId().equals(gameId) && prerequisitesMet(patch)) {
                    contributions.add(new CharacterContribution(registration.owner(),
                            validatedMainCharacters(registration, patch)));
                }
            } catch (Throwable failure) {
                rethrowIfFatal(failure);
                context.failOwnerAndDependents(registration.owner(), failure);
            }
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        contributions.stream()
                .filter(contribution -> context.ownerEligible(contribution.owner()))
                .flatMap(contribution -> contribution.characters().stream())
                .forEach(result::add);
        return List.copyOf(result);
    }

    private static List<String> validatedMainCharacters(
            RegisteredPatch registration, GamePatch patch) {
        List<String> characters = List.copyOf(Objects.requireNonNull(
                patch.providedMainCharacters(), "providedMainCharacters"));
        for (String character : characters) {
            if (character.isBlank()
                    || !character.equals(character.trim())
                    || !character.equals(character.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Patch " + registration.namespacedId()
                        + " provided invalid main-character code: '" + character + "'");
            }
        }
        return characters;
    }

    private boolean prerequisitesMet(GamePatch patch) {
        for (LogicalRom logicalRom : Objects.requireNonNull(
                patch.romPrerequisites(), "romPrerequisites")) {
            if (!prerequisiteAvailable.test(logicalRom)) {
                return false;
            }
        }
        return true;
    }

    private static void rethrowIfFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (failure instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }

    private record CharacterContribution(PatchOwner owner, List<String> characters) {
    }
}

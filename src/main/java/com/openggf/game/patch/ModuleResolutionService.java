package com.openggf.game.patch;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Engine-owned patch registry and resolver. Built-ins are immutable; each launch
 * supplies a frozen mod plan through a fresh {@link ResolutionContext}.
 */
public final class ModuleResolutionService {

    private final List<RegisteredPatch> builtIns;
    private final PatchEnablement enablement;
    private final Predicate<LogicalRom> prerequisiteAvailable;
    private final PatchContext patchContext;

    public ModuleResolutionService(List<RegisteredPatch> builtIns,
            PatchEnablement enablement, LogicalRomResolver logicalRoms,
            SonicConfigurationService configService) {
        this(builtIns, enablement, logicalRoms::isAvailable,
                new PatchContext(logicalRoms::openOrThrow, configService));
    }

    private ModuleResolutionService(List<RegisteredPatch> builtIns,
            PatchEnablement enablement, Predicate<LogicalRom> prerequisiteAvailable,
            PatchContext patchContext) {
        this.builtIns = List.copyOf(Objects.requireNonNull(builtIns, "builtIns"));
        this.enablement = Objects.requireNonNull(enablement, "enablement");
        this.prerequisiteAvailable = Objects.requireNonNull(
                prerequisiteAvailable, "prerequisiteAvailable");
        this.patchContext = Objects.requireNonNull(patchContext, "patchContext");
        ResolutionContext.create(enablement, this.builtIns, Map.of());
        for (RegisteredPatch registration : this.builtIns) {
            if (!(registration.owner() instanceof PatchOwner.BuiltIn)) {
                throw new IllegalArgumentException(
                        "Built-in registry contains mod-owned patch " + registration.namespacedId());
            }
        }
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
                prerequisiteAvailable, context);
    }

    /** Creates the fresh per-launch context; mod registrations are never retained. */
    public ResolutionContext newContext(List<RegisteredPatch> frozenModPlan,
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
        return ResolutionContext.create(enablement, combined, ownerDependencies);
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
                            registration.namespacedId(), failure);
                }
            }
        }
        return new ResolutionResult.Resolved(module);
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
                            List.copyOf(Objects.requireNonNull(patch.providedMainCharacters(),
                                    "providedMainCharacters"))));
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

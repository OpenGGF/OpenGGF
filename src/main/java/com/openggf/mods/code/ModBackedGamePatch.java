package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.level.objects.ObjectRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Engine-owned backing decorator for one frozen content registration plan. */
public final class ModBackedGamePatch implements GamePatch {
    private final ModRegistrationPlan plan;

    public ModBackedGamePatch(ModRegistrationPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (!plan.hasContent()) throw new IllegalArgumentException("Backing patch requires content");
        if (!plan.objectArt().isEmpty()
                && !plan.preparedObjectArt().keySet().equals(plan.objectArt().keySet())) {
            throw new IllegalArgumentException("Backing patch requires validated object art");
        }
    }

    public ModRegistrationPlan plan() { return plan; }
    @Override public String id() { return plan.ownerModId() + ":content"; }
    @Override public String displayName() { return plan.ownerModId() + " content"; }
    @Override public String baseGameId() { return plan.baseGameId(); }
    @Override public boolean activatesFor(GameplayLaunchRequest request) {
        return plan.baseGameId().equals(request.gameId());
    }
    @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
    @Override public List<String> providedMainCharacters() { return List.of(); }
    @Override public GameModule apply(GameModule base, PatchContext context) {
        List<ModObjectKeyRegistry.Registration> registrations = plan.objectFactories().entrySet().stream()
                .map(entry -> new ModObjectKeyRegistry.Registration(
                        plan.ownerModId(), entry.getKey(), entry.getValue()))
                .toList();
        ModObjectKeyRegistry objectKeys = new ModObjectKeyRegistry(registrations);
        return new DelegatingGameModule(base, id()) {
            private com.openggf.game.ObjectArtProvider objectArtProvider;

            @Override
            public ObjectRegistry createObjectRegistry() {
                ObjectRegistry stockOrDecorated = super.createObjectRegistry();
                return registrations.isEmpty()
                        ? stockOrDecorated
                        : new ModDecoratedObjectRegistry(stockOrDecorated, objectKeys);
            }

            @Override
            public synchronized com.openggf.game.ObjectArtProvider getObjectArtProvider() {
                if (objectArtProvider == null) {
                    com.openggf.game.ObjectArtProvider inherited = super.getObjectArtProvider();
                    objectArtProvider = plan.preparedObjectArt().isEmpty()
                            ? inherited
                            : ModArtOverlayProvider.decorate(inherited, plan.preparedObjectArt());
                }
                return objectArtProvider;
            }
        };
    }
}

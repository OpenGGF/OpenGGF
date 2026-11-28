package com.openggf.mods.mutators;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.DataSelectProvider;
import com.openggf.game.GameModule;
import com.openggf.game.LevelInputOverlay;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;

/** Supplies the cartridge mutator title and pause overlay after the mod stack is assembled. */
public final class DefaultMutatorHost extends DelegatingGameModule {
    private final CartridgeMutatorSupport support;
    private WorldSession screenWorld;
    private MutatorConfigurationScreen screen;

    public static GameModule decorate(GameModule module, GameplayLaunchRequest request, PatchContext patchContext) {
        var catalogue = module.getGameService(MutatorCatalog.class);
        if (catalogue == null || catalogue.definitions().isEmpty()
                || module.getGameService(MutatorSupportProfile.class) != null) {
            return module;
        }
        var support = CartridgeMutatorSupport.forGame(module.getGameId(),
                () -> patchContext.configService().getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
        return support != null && support.supportsNativeCharacter(request.mainCharacter())
                ? new DefaultMutatorHost(module, support) : module;
    }

    private DefaultMutatorHost(GameModule base, CartridgeMutatorSupport support) {
        super(base, "native-mutators");
        this.support = support;
    }

    private MutatorConfigurationScreen screen() {
        WorldSession world = SessionManager.getCurrentWorldSession();
        if (world == null || world.resolvedGameModule() != this || MutatorWorldAccess.state(world) == null) {
            return null;
        }
        if (screenWorld != world) {
            screen = MutatorConfigurationScreen.forCurrentWorld("MUTATOR LAB", super.getTitleScreenProvider(),
                    support.cueId(MutatorConfigurationScreen.Cue.NAVIGATE),
                    support.cueId(MutatorConfigurationScreen.Cue.CONFIRM),
                    support.cueId(MutatorConfigurationScreen.Cue.START),
                    support.cueId(MutatorConfigurationScreen.Cue.ERROR));
            screenWorld = world;
        }
        return screen;
    }

    @Override public TitleScreenProvider getTitleScreenProvider() {
        var current = screen();
        return current == null ? super.getTitleScreenProvider() : current;
    }

    /** The mutator title's Start begins a fresh challenge. */
    @Override public DataSelectProvider getDataSelectProvider() { return null; }

    @Override public String requiredDisplayAspect() {
        String inherited = super.requiredDisplayAspect();
        return inherited == null ? "NATIVE_4_3" : inherited;
    }

    @Override public boolean suppressesLevelSelect() { return true; }

    @Override public <T> T getGameService(Class<T> type) {
        if (type == MutatorSupportProfile.class) return type.cast(support);
        if (type == LevelInputOverlay.class) {
            var current = screen();
            if (current != null) return type.cast(current);
        }
        return super.getGameService(type);
    }
}

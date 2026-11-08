package com.openggf.game.sonic2.options;

import com.openggf.game.GameModule;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.nativemenu.NativeTitleModeProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic2.competition.Sonic2CompetitionVisit;

/** Isolated route-qualification composition. Does not activate any production qualified zone. */
public final class Sonic2CompetitionRouteAdapter extends DelegatingGameModule {
    private Sonic2CompetitionVisit visit;
    private final com.openggf.game.sonic2.titlescreen.TitleScreenManager title;
    private final NativeTitleModeProvider provider = new NativeTitleModeProvider() {
        private final Sonic2NativeModeProvider qualified = new Sonic2NativeModeProvider(true, zone -> true);
        @Override public NativeMenuScreen optionsScreen() { return qualified.optionsScreen(); }
        @Override public NativeMenuScreen competitionScreen(NativeMenuHostContext context) {
            visit = (Sonic2CompetitionVisit) qualified.competitionScreen(context);
            return visit;
        }
    };

    public Sonic2CompetitionRouteAdapter(GameModule base) {
        super(base, "route-qualification");
        title = (com.openggf.game.sonic2.titlescreen.TitleScreenManager) base.getTitleScreenProvider();
        title.setNativeMenuAvailability(true);
    }
    @Override public <T> T getGameService(Class<T> type) {
        return type == NativeTitleModeProvider.class ? type.cast(provider) : super.getGameService(type);
    }
    @Override public com.openggf.game.TitleScreenProvider getTitleScreenProvider() {
        // Cache the qualification provider: the root getter reapplies its empty production gate
        // and would reset selection1 to0 on every GameLoop lookup.
        return title;
    }
    public Sonic2CompetitionVisit visit() { return visit; }
}

package paradise;

import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.level.Level;
import com.openggf.level.MutableLevel;
import paradise.ui.GolfMenu;

/** Leaves level geometry, objects, art and collision sourced from the S2 ROM. */
public final class GolfModule extends DelegatingGameModule {
    private final GolfMode mode = new GolfMode();
    private Game game;
    private GolfMenu menu;
    public GolfModule(GameModule base) { super(base, "putt-putt-paradise:golf"); }
    @Override public Game createGame(Rom rom) { return game = super.createGame(rom); }
    @Override public Game createGame(GameDataSource source) { return game = super.createGame(source); }
    @Override public String requiredDisplayAspect() {
        return menu == null || menu.getSelection() == null ? null : menu.getSelection().viewport().configName();
    }
    @Override public boolean supportsSidekick() { return false; }
    @Override public boolean suppressesLevelSelect() { return true; }
    @Override public TitleScreenProvider getTitleScreenProvider() {
        if (menu == null) menu = new GolfMenu(GameServices.graphics(),
                () -> GameServices.graphics().getProjectionWidth(), selection -> {
                    var config = GameServices.configuration();
                    config.setSessionOverride(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE,
                            selection.playerOne().code());
                    config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
                    mode.configure(selection);
                });
        return menu;
    }
    @Override public Level loadLevelOverride(int index) throws java.io.IOException {
        Level original = super.loadLevelOverride(index);
        if (original == null) original = game.loadLevel(index);
        if (original.getZoneIndex() != 0) throw new IllegalArgumentException("Golf supports Emerald Hill only");
        // S2 Object Index: Obj0D signpost and Obj3E egg prison are the ROM end markers.
        // Their placement coordinates define the gate; no camera/boss-derived guess is used.
        var endpoint = original.getObjects().stream().filter(s -> s.objectId() == 0x0D || s.objectId() == 0x3E)
                .max(java.util.Comparator.comparingInt(com.openggf.level.objects.ObjectSpawn::x)).orElseThrow(
                        () -> new IllegalStateException("ROM course has no end marker"));
        mode.finishGate(endpoint.x(), endpoint.y());
        var course = MutableLevel.snapshot(original);
        for (var spawn : original.getObjects())
            if (spawn.objectId() == 0x0D || spawn.objectId() == 0x3E) course.removeObjectSpawn(spawn);
        return course;
    }
    private final LevelEventProvider courseEvents = new LevelEventProvider() {
        @Override public void initLevel(int zone, int act) { }
        @Override public void update() { }
    };
    @Override public LevelEventProvider getLevelEventProvider() { return courseEvents; }
    @Override public com.openggf.game.mode.GameplayFrameController gameplayFrameController() { return mode; }
    @Override public java.util.List<com.openggf.game.rewind.RewindSnapshottable<?>> rewindAdapters() {
        var adapters = new java.util.ArrayList<com.openggf.game.rewind.RewindSnapshottable<?>>(super.rewindAdapters());
        adapters.add(mode); return java.util.List.copyOf(adapters);
    }
}

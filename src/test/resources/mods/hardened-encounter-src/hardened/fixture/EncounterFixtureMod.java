package hardened.fixture;

import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import hardened.EncounterState;
import hardened.Sentry;
import hardened.Spore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Explicitly trusted integration harness for the maintained example's four gameplay
 * classes. It preserves the concrete ROM level and all stock providers. It supplies
 * no title, placement transform or progression claims; those are tested by the full
 * packaged prototype separately.
 */
public final class EncounterFixtureMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.registerObject("spore-sentry", (spawn, registry) -> new Sentry(spawn));
        context.registerObject("spore-shot", (spawn, registry) -> new Spore(spawn));
        context.registerGamePatch(new Patch());
    }
    public static final class Patch implements GamePatch {
        @Override public String id() { return "hardened-s3k:encounter-fixture"; }
        @Override public String displayName() { return "Encounter fixture"; }
        @Override public String baseGameId() { return "s3k"; }
        @Override public boolean activatesFor(GameplayLaunchRequest request) { return request.gameId().equals("s3k"); }
        @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
        @Override public List<String> providedMainCharacters() { return List.of(); }
        @Override public GameModule apply(GameModule base, PatchContext context) { return new Module(base); }
    }
    public static final class Module extends DelegatingGameModule {
        private final EncounterState state = new EncounterState();
        public Module(GameModule base) { super(base, "hardened-s3k:encounter-fixture"); }
        @Override public <T> T getGameService(Class<T> type) {
            return type == EncounterState.class ? type.cast(state) : super.getGameService(type);
        }
        @Override public List<RewindSnapshottable<?>> rewindAdapters() {
            var adapters = new ArrayList<RewindSnapshottable<?>>(super.rewindAdapters());
            adapters.add(state);
            return List.copyOf(adapters);
        }
    }
}

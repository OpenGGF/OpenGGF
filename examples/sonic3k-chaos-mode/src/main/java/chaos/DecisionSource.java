package chaos;

import java.util.List;
import com.openggf.mods.state.SnapshotRandom;

/** Local decisions today; an external source must journal accepted decisions before admission. */
@FunctionalInterface
public interface DecisionSource {
    ChaosCatalog.Effect choose(List<ChaosCatalog.Effect> eligible,SnapshotRandom random);
    static DecisionSource local() { return (effects,random)->effects.get(random.nextInt(effects.size())); }
}

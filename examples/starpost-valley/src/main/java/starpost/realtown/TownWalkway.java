package starpost.realtown;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.mods.scene.*;
import com.openggf.mods.ui.LevelOverlayCanvas;
import java.util.List;
import java.util.Map;

/** ROM bridge-log stair/deck, real top-solid contact shared by player and scheduled neighbours. */
public final class TownWalkway extends AbstractObjectInstance implements ModRewindRecreatable,SolidObjectProvider {
    public TownWalkway(ObjectSpawn spawn) { super(spawn,"Town footpath"); }
    public boolean isPersistent() { return true; }
    public SolidObjectParams getSolidParams() { return SolidObjectParams.of(8,4,4); }
    public boolean isTopSolidOnly() { return true; }
    public void update(int vIntRunCount,PlayableEntity entity) {}
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new TownWalkway(context.spawn()); }
    public void appendRenderCommands(List<GLCommand> commands) {
        var town=services().gameService(TownSession.class); if(town==null || !town.active() || town.presentation()==null) return;
        town.presentation().walkway(services(),spawn.x(),spawn.y()-4);
    }
}

package starpost.realfest;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;
/** Restored lake bridge: native top-solid logs, captured through object reconstruction. */
public final class ActivityDeck extends AbstractObjectInstance implements ModRewindRecreatable,SolidObjectProvider {
 private transient ActivityPresentation art;
 public ActivityDeck(ObjectSpawn spawn) { super(spawn,"Lake bridge"); }
 public boolean isPersistent() { return true; }
 public boolean isTopSolidOnly() { return true; }
 public SolidObjectParams getSolidParams() { return SolidObjectParams.of(8,4,4); }
 public void update(int clock,PlayableEntity player) {}
 public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext c) { return new ActivityDeck(c.spawn()); }
 public void appendRenderCommands(List<GLCommand> commands) {
  var s=services().gameService(ActivitySession.class); if(s==null||!s.active()) return;
  if(art==null) art=new ActivityPresentation(s); art.log(services(),s,spawn.x(),spawn.y()-4);
 }
}

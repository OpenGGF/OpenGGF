package starpost.realfest;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;
/** Snowboard obstacle; native airborne movement clears it, grounded contact starts captured tumble. */
public final class ActivityRock extends AbstractObjectInstance implements ModRewindRecreatable {
 private boolean hit;
 private transient ActivityPresentation art;
 public ActivityRock(ObjectSpawn spawn) { super(spawn,"Snowboard rock"); }
 public boolean isPersistent() { return true; }
 public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext c) { return new ActivityRock(c.spawn()); }
 public void update(int clock,PlayableEntity entity) {
  var s=services().gameService(ActivitySession.class);
  if(hit||s==null||!s.active()||!(entity instanceof AbstractPlayableSprite p)||s.heldPlayer()) return;
  if(!p.getAir() && Math.abs(p.getCentreX()-spawn.x())<14 && Math.abs(p.getCentreY()+p.getYRadius()-spawn.y())<16) { hit=true; s.tumble(p); }
 }
 public void appendRenderCommands(List<GLCommand> commands) {
  var s=services().gameService(ActivitySession.class); if(hit||s==null||!s.active()) return;
  if(art==null) art=new ActivityPresentation(s); art.rock(services(),s,spawn.x(),spawn.y());
 }
}

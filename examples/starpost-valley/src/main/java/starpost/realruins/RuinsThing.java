package starpost.realruins;

import com.openggf.game.*;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.ArrayList;
import java.util.List;
import starpost.ruins.*;

/** Real level objects backed by the existing chamber behavior and yields. */
public final class RuinsThing extends AbstractObjectInstance implements ModRewindRecreatable {
    private transient RuinsPresentation presentation;
    public RuinsThing(ObjectSpawn spawn) { super(spawn,"Ruins find or badnik"); }
    public boolean isPersistent() { return true; }
    public int getPriorityBucket() { return 3; } // S1 badnik init routines set obPriority=3.
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new RuinsThing(context.spawn()); }
    public void update(int vIntRunCount,PlayableEntity entity) {
        var session=services().gameService(RuinsSession.class);
        if(session==null || !session.active() || session.taken(spawn.subtype()) || !(entity instanceof AbstractPlayableSprite player)) return;
        var thing=session.chamber().things.get(spawn.subtype());
        int x=player.getCentreX(), y=player.getCentreY()-RuinsLevel.ORIGIN;
        boolean attacking=player.getRolling() || player.getSpindash() || player.getInvincibleFrames()>0;
        if(thing.type()==Chamber.BADNIK) {
            var b=session.badnik(spawn.subtype()); if(b==null || !b.alive) return;
            // Existing mod's S1 ports: Cat_Undulate/Cat_Floor, Bas_Action_DropDown/Fly,
            // Buzz_Action_Move/Fire, Yad_Action_Move, Jaws_Swim, Burro_Action_Move/Jump,
            // Orb_CheckSonic/CircleSpikeball, Bom_Action_Walking/WaitAndExplode, Hog_Action.
            // Source: s1disasm/_incObj objects 78,55,22/23,50,2C,2D,60,5F,1E/20 (FixBugs=0).
            b.update(session.chamber(),x,y,session.shots());
            if(!b.alive) { session.take(spawn.subtype()); session.puff(b.x,b.y); return; }
            List<float[]> harms=new ArrayList<>(); b.harm(harms);
            for(float[] h:harms) if(overlap(player,x,y,h[0],h[1],h[2],h[3]))
                RuinsController.hurt(services(),player,Math.round(h[0]),DamageCause.NORMAL,vIntRunCount);
            if(overlap(player,x,y,b.x,b.y,Badnik.halfWidth(b.kind),Badnik.halfHeight(b.kind))) {
                if(attacking && b.bopable() && !(b.spikyTop() && y<b.y-8)) {
                    session.pop(spawn.subtype());
                    player.setYSpeed((short)RuinsRules.bopBounce(player.getYSpeed(),y<b.y));

                } else RuinsController.hurt(services(),player,Math.round(b.x),DamageCause.NORMAL,vIntRunCount);
            }
            return;
        }
        boolean near=Math.abs(thing.x()-x)<player.getXRadius()+12 && Math.abs(thing.y()-y)<player.getYRadius()+20;
        switch(thing.type()) {
            case Chamber.RING -> {
                int reach=player.getShieldType()==ShieldType.LIGHTNING?64:18;
                if(Math.abs(thing.x()-x)<reach && Math.abs(thing.y()-y)<reach && player.getInvulnerableFrames()<=90) {
                    session.take(spawn.subtype()); player.addRings(1);
                }
            }
            case Chamber.ROCK -> {
                boolean fire=player.getShieldType()==ShieldType.FIRE && session.action();
                if(near && (fire || attacking && Math.abs(player.getGSpeed())>=3*256)) session.breakRock(spawn.subtype(),fire);
            }
            case Chamber.MONITOR -> {
                if(near && attacking) {
                    session.take(spawn.subtype());
                    if(thing.param()>=0 && thing.param()<session.chamber().prizes.size()) session.drop(session.chamber().prizes.get(thing.param()),1,thing.x(),thing.y());
                    else player.addRings(10);
                    if(player.getYSpeed()>0) player.setYSpeed((short)-player.getYSpeed());
                }
            }
            default -> { }
        }
    }
    private static boolean overlap(AbstractPlayableSprite p,int x,int y,float bx,float by,float hw,float hh) {
        return Math.abs(x-bx)<p.getXRadius()+hw && Math.abs(y-by)<p.getYRadius()+hh;
    }
    public void appendRenderCommands(List<GLCommand> commands) {
        var session=services().gameService(RuinsSession.class);
        if(session==null || !session.active() || session.taken(spawn.subtype())) return;
        if(presentation==null) presentation=new RuinsPresentation(session);
        presentation.thing(services(),session,spawn.subtype());
    }
}

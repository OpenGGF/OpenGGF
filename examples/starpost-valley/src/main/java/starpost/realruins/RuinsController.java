package starpost.realruins;

import com.openggf.game.*;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;
import starpost.ruins.*;

/** Persistent clock, shafts and projectile director. Native player owns movement/hurt/drowning. */
public final class RuinsController extends AbstractObjectInstance implements ModRewindRecreatable {
    private boolean arrived;
    private boolean wasAir;
    private String selected;
    private transient RuinsPresentation presentation;
    public RuinsController(ObjectSpawn spawn) { super(spawn,"Ruins director"); }
    public boolean isPersistent() { return true; }
    public boolean isHighPriority() { return true; }
    public int getPriorityBucket() { return 0; }
    public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) { return new RuinsController(context.spawn()); }
    public void update(int vIntRunCount,PlayableEntity entity) {
        var session=services().gameService(RuinsSession.class);
        if(session==null || !session.active() || !(entity instanceof AbstractPlayableSprite player)) return;
        var chamber=session.chamber();
        if(!arrived) { arrived=true; player.setInvulnerableFrames(RuinsRules.ARRIVAL_FLASH); session.arrived(player.getRingCount()); }
        String held=session.game().inventory.selectedId();
        if(!java.util.Objects.equals(held,selected)) {
            selected=held;
            if("water_shield".equals(held)) player.giveShield(ShieldType.BUBBLE);
            else if("fire_shield".equals(held)) player.giveShield(ShieldType.FIRE);
            else if("lightning_shield".equals(held)) player.giveShield(ShieldType.LIGHTNING);
        }
        var drowning=player.getDrowningController();
        if(player.isInWater() && drowning!=null && drowning.getRemainingAir()<=0
                && session.game().inventory.remove("tide_sapphire",1)>0) player.replenishAir();
        session.updateMusic(drowning!=null && drowning.isDrowningMusicPlaying());
        if(player.getDead()) session.request(ActExit.FAINTED,"fainted");
        session.tick(player.getRingCount());
        // S3K Obj_Spring yellow vertical launch is -$A00; preserve the Ruins spring Momentum reward.
        if(!wasAir && player.getAir() && player.getYSpeed()==-0xA00) session.game().restoreBySpeed(1);
        wasAir=player.getAir();
        int x=player.getCentreX(),feet=player.getCentreY()+player.getYRadius()-RuinsLevel.ORIGIN;
        if(!player.getAir() && !player.isHurt()) {
            if(session.up() && near(x,feet,chamber.entryX,chamber.entryY)) session.request(ActExit.LEFT,"leave");
            if(chamber.elevatorX>=0 && near(x,feet,chamber.elevatorX,chamber.elevatorY)) {
                RuinsSystem.section(session.game()).reachElevator(chamber.number);
                if(session.up()) session.request(ActExit.LEFT,"elevator");
            }
            if(session.down() && chamber.exitX>=0 && near(x,feet,chamber.exitX,chamber.exitY)) {
                if(chamber.number<RuinsRules.CHAMBERS) session.request(ActExit.COMPLETED,"next");
                else session.sealed();
            }
        }
        if(feet>chamber.height+32 && chamber.number<RuinsRules.CHAMBERS) session.request(ActExit.COMPLETED,"next");
        if(!RuinsRules.lavaImmune(session.game())) for(var lava:chamber.lava)
            if(lava.contains(x,feet) || lava.contains(x,feet-4)) hurt(services(),player,x,DamageCause.FIRE,vIntRunCount);
        for(var shot:session.shots()) {
            if(!shot.alive) continue;
            shot.update(chamber);
            if(Math.abs(shot.x-x)<shot.half()+player.getXRadius()
                    && Math.abs(shot.y+RuinsLevel.ORIGIN-player.getCentreY())<shot.half()+player.getYRadius())
                { hurt(services(),player,Math.round(shot.x),DamageCause.NORMAL,vIntRunCount);
                  if(shot.kind!=Badnik.Shot.SPIKEBALL) shot.alive=false; }
            if(!shot.alive && shot.kind==Badnik.Shot.CANNONBALL) session.puff(shot.x,shot.y);
        }
        session.shots().removeIf(s->!s.alive);
        for(int i=session.puffs().size()-1;i>=0;i--) {
            var puff=session.puffs().get(i);
            if(puff.age()>=20) session.puffs().remove(i);
            else session.puffs().set(i,new RuinsSession.Puff(puff.x(),puff.y(),puff.age()+1));
        }
        // S1 object 28 Anml_Variables / Anml_NormalGravity (same speeds as scene Loose.Animal).
        for(int i=session.animals().size()-1;i>=0;i--) {
            var a=session.animals().get(i);
            int[] speed=switch(a.name()) {
                case "pocky" -> new int[]{-0x200,-0x400};
                case "cucky" -> new int[]{-0x200,-0x300};
                case "pecky" -> new int[]{-0x180,-0x300};
                case "rocky" -> new int[]{-0x140,-0x180};
                default -> new int[]{-0x280,-0x380};
            };
            float ax=a.x()+(a.hopping()?speed[0]/256f:0), y=a.feet()+a.vy();
            float vy=a.vy()+(a.hopping() && a.name().equals("cucky")?0x18:0x38)/256f;
            boolean hopping=a.hopping();
            int floor=chamber.floorBelow(Math.round(ax),Math.round(y-a.vy())-2);
            if(vy>=0 && y>=floor && floor<chamber.height) { y=floor; vy=speed[1]/256f; hopping=true; }
            if(a.age()>600 || ax<-24 || y>chamber.height+32) session.animals().remove(i);
            else session.animals().set(i,new RuinsSession.Animal(a.name(),ax,y,vy,hopping,a.age()+1));
        }
        for(int i=session.nextFindToSpawn();i>=0;i=session.nextFindToSpawn()) {
            var find=session.finds().get(i);
            services().objectManager().addDynamicObject(new RuinsFind(RuinsContent.spawn("ruins-find",i,Math.round(find.x()),Math.round(find.y())+RuinsLevel.ORIGIN)));
        }
        // Native ring monitors mark their placement remembered; carry that progress across scene menus.
        for(int i=0;i<chamber.things.size();i++) {
            var thing=chamber.things.get(i);
            if(thing.type()==Chamber.MONITOR && thing.param()<0) {
                int y=thing.y()+RuinsLevel.ORIGIN-16;
                var monitor=new ObjectSpawn(thing.x(),y,1,1,0,false,y,i+1);
                if(services().objectManager().isRemembered(monitor)) session.take(i);
            }
        }
        if(session.exit()!=null) services().requestActExit(session.exit(),session.payload());
        session.clearInput(); services().levelGamestate().pauseTimer();
    }
    private static boolean near(int x,int feet,int targetX,int targetY) { return Math.abs(x-targetX)<=18 && Math.abs(feet-targetY)<=12; }
    /** S3K native HurtCharacter and ring-loss objects, including native shield absorption. */
    public static void hurt(ObjectServices services,AbstractPlayableSprite player,int sourceX,DamageCause cause,int vIntRunCount) {
        boolean shield=player.getShieldType()!=null;
        boolean rings=player.getRingCount()>0;
        if(player.applyHurtOrDeath(sourceX,cause,rings) && !shield && rings) services.spawnLostRings(player,vIntRunCount);
    }
    public void appendRenderCommands(List<GLCommand> commands) {
        var session=services().gameService(RuinsSession.class);
        if(session==null || !session.active()) return;
        if(presentation==null) presentation=new RuinsPresentation(session);
        presentation.draw(services(),session);
    }
}

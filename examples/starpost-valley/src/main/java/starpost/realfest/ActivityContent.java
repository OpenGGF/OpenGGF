package starpost.realfest;

import com.openggf.game.*;
import com.openggf.mods.code.*;
import com.openggf.level.objects.*;
import java.util.List;

/** Four real destinations share a captured session and native controller objects. */
public final class ActivityContent {
    private ActivityContent() {}
    public static ActivitySession register(ModContext context) {
        var session=new ActivitySession(); var input=new ActivityInput(session);
        context.registerServiceBundle("activities",()->GameServiceBundle.builder()
            .capturedService("state",ActivitySession.class,session).build());
        context.registerObject("activity-controller",(spawn,registry)->new ActivityController(spawn));
        context.registerObject("activity-rock",(spawn,registry)->new ActivityRock(spawn));
        context.registerObject("activity-deck",(spawn,registry)->new ActivityDeck(spawn));
        context.registerObject("activity-ring",(spawn,registry)->new ActivityRing(spawn));
        for(String name:List.of("race","hunt","snowboard","lake")) {
            context.registerZone(ModZoneContribution.singleAct(name,new BakedLevelRef("levels/valley/level.json"),null,null,false));
            var key=new ZoneKey.Mod("starpost-valley",name);
            context.registerInputFilter(new ModInputFilterContribution(key,input));
            context.registerHudProfile(new ModHudProfileContribution(key,new HudProfile(List.of())));
        }
        context.registerGamePatch(new ActivityPatch(session)); return session;
    }
    public static ObjectSpawn spawn(String key,int index,int x,int y) {
        return new ObjectSpawn(x,y,0,index,0,false,y,index+1,"starpost-valley","starpost-valley:"+key);
    }
}

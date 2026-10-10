package starpost.realruins;

import com.openggf.game.*;
import com.openggf.game.patch.LogicalRom;
import com.openggf.level.objects.*;
import com.openggf.mods.code.*;
import java.util.List;

/** Captured session, named ROM art, destination input and generated-act registration. */
public final class RuinsContent {
    private RuinsContent() {}
    public static RuinsSession register(ModContext context) {
        var session=new RuinsSession();
        context.registerServiceBundle("ruins",()->GameServiceBundle.builder()
                .capturedService("state",RuinsSession.class,session).build());
        context.registerObject("ruins-controller",(spawn,registry)->new RuinsController(spawn));
        context.registerObject("ruins-thing",(spawn,registry)->new RuinsThing(spawn));
        context.registerObject("ruins-find",(spawn,registry)->new RuinsFind(spawn));
        // s1disasm sonic.lst: Nem_Cater/Map_Cat, Nem_Basaran/Map_Bas, Nem_Buzz/Map_Buzz,
        // Nem_Yadrin/Map_Yad, Nem_Jaws/Map_Jaws, Nem_Burrobot/Map_Burro, Nem_Orbinaut/Map_Orb,
        // Nem_Bomb/Map_Bomb, Nem_BallHog/Map_Hog. S1 byte-count/five-byte mapping pieces.
        int[][] requests={{0x39076,0x1751A,1},{0x386BC,0x108CA,0},{0x3639E,0xA0B4,0},
            {0x382D4,0xFFBA,1},{0x3727E,0xB2FA,1},{0x3692C,0xB4E6,0},{0x38E98,0x125B8,0},
            {0x38C00,0x122FC,0},{0x35AF0,0x94E0,1}};
        for(int i=0;i<requests.length;i++) {
            int[] r=requests[i];
            context.registerRomObjectArt("ruins-badnik-"+i,LogicalRom.S1,
                    new RomArtRequest(r[0],RomArtCompression.NEMESIS,0,r[1],0,r[2],1));
        }
        context.registerZone(ModZoneContribution.singleAct("ruins",new BakedLevelRef("levels/ruins/level.json"),
                null,null,false).withRuntime(runtime->com.openggf.game.modzone.ModZoneRuntimeServices.builder()
                    .water(new RuinsWater(session.chamber())).build()));
        var destination=new ZoneKey.Mod("starpost-valley","ruins");
        context.registerInputFilter(new ModInputFilterContribution(destination,new RuinsInput(session)));
        context.registerHudProfile(new ModHudProfileContribution(destination,new HudProfile(List.of())));
        context.registerGamePatch(new RuinsPatch(session));
        return session;
    }
    public static int index(ObjectSpawn spawn) { return spawn.layoutIndex()>0?spawn.layoutIndex()-1:spawn.subtype(); }
    public static ObjectSpawn spawn(String key,int index,int x,int y) {
        return new ObjectSpawn(x,y,0,index,0,false,y,key.equals("ruins-controller")?0:index+1,"starpost-valley","starpost-valley:"+key);
    }
}

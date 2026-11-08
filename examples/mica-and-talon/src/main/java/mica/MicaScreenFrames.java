package mica;
import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;
import java.io.IOException;
import java.util.*;
final class MicaScreenFrames {
    static PresentationFrameSet load(ModAssetRoot assets, CharacterPresentation.Host host) throws IOException {
        var banks=new LinkedHashMap<String,Map<Integer,PresentationFrameSet.Pose>>();
        String prefix="art/mica/";
        if(host==CharacterPresentation.Host.S1) {
            add(banks,assets,"continue/floor",1,prefix+"continue/s1-floor-01.png",20,12);
            add(banks,assets,"continue/floor",2,prefix+"continue/s1-floor-02.png",20,12);
            add(banks,assets,"continue/floor",3,prefix+"continue/s1-floor-03.png",20,12);
            add(banks,assets,"continue/icon",6,prefix+"continue/s1-icon-06.png",0,0);
            add(banks,assets,"continue/icon",7,prefix+"continue/s1-icon-07.png",0,0);
            add(banks,assets,"continue/player",60,prefix+"continue/s1-player-3c.png",20,12);
            add(banks,assets,"continue/player",61,prefix+"continue/s1-player-3d.png",24,12);
            add(banks,assets,"continue/player",83,prefix+"continue/s1-player-53.png",28,12);
            add(banks,assets,"continue/player",62,prefix+"continue/s1-player-3e.png",28,12);
            add(banks,assets,"continue/player",84,prefix+"continue/s1-player-54.png",12,12);
            add(banks,assets,"continue/player",6,prefix+"continue/s1-player-06.png",20,21);
            add(banks,assets,"continue/player",7,prefix+"continue/s1-player-07.png",19,20);
            add(banks,assets,"continue/player",8,prefix+"continue/s1-player-08.png",13,19);
            add(banks,assets,"continue/player",9,prefix+"continue/s1-player-09.png",20,21);
            add(banks,assets,"continue/player",10,prefix+"continue/s1-player-0a.png",21,20);
            add(banks,assets,"continue/player",11,prefix+"continue/s1-player-0b.png",20,19);
            add(banks,assets,"continue/player",30,prefix+"continue/s1-player-1e.png",20,18);
            add(banks,assets,"continue/player",31,prefix+"continue/s1-player-1f.png",20,18);
            add(banks,assets,"continue/player",32,prefix+"continue/s1-player-20.png",20,18);
            add(banks,assets,"continue/player",33,prefix+"continue/s1-player-21.png",20,18);
        }
        if(host==CharacterPresentation.Host.S2) {
            add(banks,assets,"continue/icon",4,prefix+"continue/s2-icon-04.png",0,0);
            add(banks,assets,"continue/icon",5,prefix+"continue/s2-icon-05.png",0,0);
            add(banks,assets,"continue/player",8,prefix+"continue/s2-player-08.png",24,12);
            add(banks,assets,"continue/player",9,prefix+"continue/s2-player-09.png",24,12);
            add(banks,assets,"continue/player",7,prefix+"continue/s2-player-07.png",16,12);
            add(banks,assets,"continue/player",13,prefix+"continue/s2-player-0d.png",20,21);
            add(banks,assets,"continue/player",14,prefix+"continue/s2-player-0e.png",20,20);
            add(banks,assets,"continue/player",15,prefix+"continue/s2-player-0f.png",19,19);
            add(banks,assets,"continue/player",16,prefix+"continue/s2-player-10.png",18,20);
            add(banks,assets,"continue/player",17,prefix+"continue/s2-player-11.png",18,21);
            add(banks,assets,"continue/player",18,prefix+"continue/s2-player-12.png",20,20);
            add(banks,assets,"continue/player",19,prefix+"continue/s2-player-13.png",19,19);
            add(banks,assets,"continue/player",20,prefix+"continue/s2-player-14.png",18,20);
            add(banks,assets,"continue/player",45,prefix+"continue/s2-player-2d.png",16,18);
            add(banks,assets,"continue/player",46,prefix+"continue/s2-player-2e.png",16,18);
            add(banks,assets,"continue/player",47,prefix+"continue/s2-player-2f.png",16,18);
            add(banks,assets,"continue/player",48,prefix+"continue/s2-player-30.png",16,18);
        }
        if(host==CharacterPresentation.Host.S3K) {
            add(banks,assets,"continue/team-idle",0,prefix+"continue/s3k-team-idle-00.png",12,20);
            add(banks,assets,"continue/team-idle",1,prefix+"continue/s3k-team-idle-01.png",12,20);
            add(banks,assets,"continue/icon",0,prefix+"continue/s3k-icon-00.png",12,12);
            add(banks,assets,"continue/icon",1,prefix+"continue/s3k-icon-01.png",12,12);
            add(banks,assets,"continue/player",90,prefix+"continue/s3k-player-5a.png",18,20);
            add(banks,assets,"continue/player",89,prefix+"continue/s3k-player-59.png",17,20);
            add(banks,assets,"continue/player",85,prefix+"continue/s3k-player-55.png",16,20);
            add(banks,assets,"continue/player",86,prefix+"continue/s3k-player-56.png",17,20);
            add(banks,assets,"continue/player",87,prefix+"continue/s3k-player-57.png",16,20);
            add(banks,assets,"continue/player",189,prefix+"continue/s3k-player-bd.png",18,20);
            add(banks,assets,"continue/player",190,prefix+"continue/s3k-player-be.png",18,20);
            add(banks,assets,"continue/player",186,prefix+"continue/s3k-player-ba.png",16,20);
            add(banks,assets,"continue/player",33,prefix+"continue/s3k-player-21.png",16,20);
            add(banks,assets,"continue/player",34,prefix+"continue/s3k-player-22.png",16,20);
            add(banks,assets,"continue/player",35,prefix+"continue/s3k-player-23.png",16,20);
            add(banks,assets,"continue/player",36,prefix+"continue/s3k-player-24.png",16,20);
        }
        if(host==CharacterPresentation.Host.S3K) {
            add(banks,assets,"portrait",5,prefix+"portrait/mica.png",18,40);
            add(banks,assets,"snowboard",0,prefix+"snowboard/mica-00.png",0,0);
            add(banks,assets,"snowboard",1,prefix+"snowboard/mica-01.png",22,36);
            add(banks,assets,"snowboard",2,prefix+"snowboard/mica-02.png",16,18);
            add(banks,assets,"snowboard",3,prefix+"snowboard/mica-03.png",23,23);
            add(banks,assets,"snowboard",4,prefix+"snowboard/mica-04.png",21,22);
            add(banks,assets,"snowboard",5,prefix+"snowboard/mica-05.png",19,22);
            add(banks,assets,"snowboard",6,prefix+"snowboard/mica-06.png",28,17);
            add(banks,assets,"snowboard",7,prefix+"snowboard/mica-07.png",28,17);
            add(banks,assets,"snowboard",8,prefix+"snowboard/mica-08.png",28,17);
            add(banks,assets,"snowboard",9,prefix+"snowboard/mica-09.png",27,15);
            add(banks,assets,"snowboard",10,prefix+"snowboard/mica-10.png",34,21);
            add(banks,assets,"snowboard",11,prefix+"snowboard/mica-11.png",28,18);
            add(banks,assets,"snowboard",12,prefix+"snowboard/mica-12.png",26,22);
        }
        int[] rgb=PresentationImages.backing(assets,prefix+"portrait/mica.png",0).rgb();
        return new PresentationFrameSet(banks,rgb);
    }
    private static void add(Map<String,Map<Integer,PresentationFrameSet.Pose>> banks,ModAssetRoot assets,
            String bank,int id,String file,int x,int y) throws IOException {
        banks.computeIfAbsent(bank,ignored->new LinkedHashMap<>()).put(id,
                new PresentationFrameSet.Pose(PresentationImages.read(assets,file),x,y));
    }
}

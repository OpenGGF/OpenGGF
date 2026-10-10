package starpost.realruins;

import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.game.modzone.ModZoneLevelData;
import com.openggf.level.objects.ObjectSpawn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import starpost.realvalley.*;
import starpost.ruins.Chamber;

/** Generated S1 kit grid, encoded by the valley's exact art/collision encoder. */
public final class RuinsLevel {
    public static final int ORIGIN = 128;
    private RuinsLevel() {}
    public static EncodedValley encode(S1Terrain source, Chamber chamber) {
        return ValleyEncoder.encodeGrid(source, new ValleyEncoder.Spec(chamber.cells, 1, -1, -1,
                1, 2), chamber.cols, chamber.rows);
    }
    public static ModZoneLevelData build(ModZoneLevelData placeholder, S1Terrain source, Chamber chamber) {
        return build(placeholder,source,chamber,null);
    }
    public static ModZoneLevelData build(ModZoneLevelData placeholder, S1Terrain source, Chamber chamber,RuinsSession session) {
        var data = encode(source, chamber);
        List<ModPaletteClaim> claims = new ArrayList<>();
        for (int[] c : data.claims()) claims.add(new ModPaletteClaim(c[0], c[1], c[2]));
        return new ModZoneLevelData(2, placeholder.zoneIndex(), 8, data.width(), data.height(),
                0, Math.max(0,chamber.width-320), 0, Math.max(0,data.height()*128-224),
                data.patterns(),data.chunks(),data.blocks(),data.foreground(),data.background(),
                data.heights(),data.widths(),data.angles(),data.primary(),data.secondary(),
                placeholder.paletteLines(),placeholder.hostMetadata(),claims,objects(chamber,session),List.of(),
                data.patternCount(),data.chunkCount(),data.blockCount());
    }
    public static List<ObjectSpawn> objects(Chamber chamber) { return objects(chamber,null); }
    private static List<ObjectSpawn> objects(Chamber chamber,RuinsSession session) {
        List<ObjectSpawn> spawns = new ArrayList<>();
        spawns.add(RuinsContent.spawn("ruins-controller", 0, chamber.entryX, ORIGIN+chamber.entryY));
        int i=0;
        for (var thing : chamber.things) {
            if(session!=null && session.taken(i)) { i++; continue; }
            int stock = switch (thing.type()) {
                case Chamber.SPRING -> 7; case Chamber.SPIKES -> 8;
                case Chamber.MONITOR -> thing.param()<0?1:0;
                case Chamber.BUBBLES -> 0x54;
                default -> 0;
            };
            if (stock != 0) {
                int subtype = stock==7?2:stock==1?1:stock==0x54?0x80:0; // S3K Obj_Monitor: subtype 1 = ten rings.
                int y=thing.y()+ORIGIN-(stock==7?8:stock==8?16:16);
                spawns.add(new ObjectSpawn(thing.x(),y,stock,subtype,0,false,y,i+1));
            } else {
                spawns.add(RuinsContent.spawn("ruins-thing", i,thing.x(),ORIGIN+thing.y()));
            }
            i++;
        }
        if (chamber.elevatorX>=0) {
            int y=ORIGIN+chamber.elevatorY-32;
            spawns.add(new ObjectSpawn(chamber.elevatorX,y,0x34,1,0,false,y,i+1));
        }
        spawns.sort(Comparator.comparingInt(ObjectSpawn::x));
        return spawns;
    }
}

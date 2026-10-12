package starpost.realfest;

import com.openggf.game.modzone.*;
import com.openggf.level.objects.ObjectSpawn;
import java.util.*;
import starpost.festivals.*;
import starpost.realvalley.*;

/** Exact ROM chunks, collision profiles and loop planes on native activity courses. */
public final class CourseLevel {
    public static final int START=60;
    public static final int POOL_Y=128+176;
    private CourseLevel() {}
    public static int[] blocks(String kind,int year) {
        return switch(kind) {
            case "hunt" -> RealValley.blocks(); case "lake" -> new int[]{1,51,52};
            case "snowboard" -> Snowboard.course(year); default -> RaceTrack.blocks();
        };
    }
    public static int lapLength() { return RaceTrack.length()-START-120; }
    public static int finish(String kind,int year) { return blocks(kind,year).length*256-120; }
    public static int start(String kind) { return kind.equals("hunt")?700:kind.equals("lake")?40:START; }
    public static int floor(S1Terrain source,int block,int x) {
        for(int y=0;y<256;y++) {
            int word=source.blocks()[block][y/16*16+x/16];
            if((word&0x1000)==0) continue;
            int profile=source.collision()[word&0x3FF],px=(word&0x400)!=0?15-x%16:x%16;
            int h=source.heights()[profile*16+px], row=(word&0x800)!=0?15-y%16:y%16;
            if(h>0 && row>=16-h || h<0 && row<-h) return y;
        }
        return 256;
    }
    private record Grid(S1Terrain source,int[] cells,int columns,int rows) {}
    /** Align snowboard blocks on native 16-pixel chunk rows; no fitted surfaces or movement. */
    private static Grid downhill(S1Terrain source,int[] course) {
        int[] offsets=new int[course.length]; int min=0,max=0;
        for(int i=1;i<course.length;i++) {
            offsets[i]=offsets[i-1]+Math.floorDiv(floor(source,course[i-1],255)-floor(source,course[i],0)+8,16);
            min=Math.min(min,offsets[i]); max=Math.max(max,offsets[i]);
        }
        int rows=Math.floorDiv(max-min+31,16)+1;
        int[] cells=new int[course.length*rows]; List<int[]> added=new ArrayList<>(Arrays.asList(source.blocks()));
        for(int col=0;col<course.length;col++) for(int row=0;row<rows;row++) {
            int[] words=new int[256]; boolean any=false;
            for(int y=0;y<16;y++) {
                int sy=row*16+y-offsets[col]+min;
                if(sy<0 || sy>=16) continue;
                System.arraycopy(source.blocks()[course[col]],sy*16,words,y*16,16); any=true;
            }
            if(any) { cells[row*course.length+col]=added.size(); added.add(words); }
        }
        var shifted=new S1Terrain(source.pixels(),source.chunks(),source.collision(),added.toArray(int[][]::new),
            source.heights(),source.widths(),source.angles(),source.palette(),source.bgWidth(),source.bgHeight(),source.bgLayout());
        return new Grid(shifted,cells,course.length,rows);
    }
    public static ModZoneLevelData build(ModZoneLevelData placeholder,S1Terrain source,ActivitySession session) {
        String kind=session.kind(); int[] course=blocks(kind,session.game().calendar.year());
        Grid grid=kind.equals("snowboard")?downhill(source,course):new Grid(source,course,course.length,1);
        var data=ValleyEncoder.encodeGrid(grid.source(),new ValleyEncoder.Spec(grid.cells(),1,
            kind.equals("snowboard")?-1:53,54,3,15),grid.columns(),grid.rows());
        List<ModPaletteClaim> claims=new ArrayList<>(); for(int[] c:data.claims()) claims.add(new ModPaletteClaim(c[0],c[1],c[2]));
        List<ObjectSpawn> objects=new ArrayList<>();
        for(int col=0;col<course.length;col++) {
            objects.add(ActivityContent.spawn("activity-controller",col,col*256+60,320));
            if(!kind.equals("snowboard") && course[col]==53) {
                int x=col*256;
                objects.add(stock(objects.size(),x+44,256,2,2));
                objects.add(stock(objects.size(),x+128,192,2,0x11));
                objects.add(stock(objects.size(),x+256,256,2,0x12));
            }
            if(course[col]==3) objects.add(stock(objects.size(),col*256+20,128+floor(source,3,20)-8,7,2));
        }
        objects.sort(Comparator.comparingInt(ObjectSpawn::x));
        return new ModZoneLevelData(2,placeholder.zoneIndex(),8,data.width(),data.height(),
            0,Math.max(0,course.length*256-session.width()),0,Math.max(0,data.height()*128-224),
            data.patterns(),data.chunks(),data.blocks(),data.foreground(),data.background(),data.heights(),data.widths(),
            data.angles(),data.primary(),data.secondary(),placeholder.paletteLines(),placeholder.hostMetadata(),claims,objects,List.of(),
            data.patternCount(),data.chunkCount(),data.blockCount());
    }
    private static ObjectSpawn stock(int index,int x,int y,int id,int subtype) {
        return new ObjectSpawn(x,y,id,subtype,0,false,y,index+1000);
    }
}

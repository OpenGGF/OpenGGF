package com.openggf.level.objects;

import com.openggf.level.LevelManager;
import com.openggf.physics.ObjectTerrainUtils;
import java.util.Objects;

/** Delegate to the native FindFloor owner; no detached masks or global gameplay lookup. */
final class TerrainSurfaceQueries {
    private TerrainSurfaceQueries() { }
    static TerrainSurface floor(ObjectServices services,int x,int y,TerrainSurface.Layer layer,TerrainSurface.Path path) {
        Objects.requireNonNull(layer,"collision layer");Objects.requireNonNull(path,"collision path");
        checkedY(y);
        return floor(services.levelManager(),x,y,layer,path);
    }
    private static TerrainSurface floor(LevelManager level,int x,int y,TerrainSurface.Layer layer,TerrainSurface.Path path) {
        if(level==null)return none();
        var result=ObjectTerrainUtils.checkFloorDistOnLayer(level,x,y,
                (byte)(layer==TerrainSurface.Layer.FOREGROUND?0:1),path==TerrainSurface.Path.SECONDARY,true);
        return result.foundSurface()?new TerrainSurface(true,y+result.distance(),result.distance(),result.angle()&255):none();
    }
    static TerrainSurface search(ObjectServices services,int x,int y,int above,int below,
            TerrainSurface.Layer layer,TerrainSurface.Path path) {
        Objects.requireNonNull(layer,"collision layer");Objects.requireNonNull(path,"collision path");
        if(above<0||above>256||below<0||below>512)
            throw new IllegalArgumentException("Floor probe range must be 0..256 above and 0..512 below");
        long first=(long)y-above,last=(long)y+below;
        if(first<Integer.MIN_VALUE+32L||last>Integer.MAX_VALUE-32L)
            throw new IllegalArgumentException("Floor probe range overflows world coordinates");
        LevelManager level=services.levelManager();
        if(level==null)return none();
        // Preserve the creator consumers' native 16px probe cadence and acceptance window.
        // Each FindFloor checks only the current/adjacent collision tiles; at most49 origins.
        for(int probe=(int)first;probe<=last;probe+=16) {
            TerrainSurface found=floor(level,x,probe,layer,path);
            if(found.foundSurface()&&found.distance()>=-16&&found.distance()<16)
                return new TerrainSurface(true,found.surfaceY(),found.surfaceY()-y,found.angle());
        }
        return none();
    }
    private static void checkedY(int y) {
        if(y<Integer.MIN_VALUE+32||y>Integer.MAX_VALUE-32)
            throw new IllegalArgumentException("Floor probe overflows world coordinates");
    }
    private static TerrainSurface none(){return new TerrainSurface(false,0,0,0);}
}

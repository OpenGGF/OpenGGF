package com.openggf;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.*;

/** Cross-boot value comparison: retain gameplay bytes and ROM resource contents, not Java identity. */
final class RaceStateSnapshots {
    private RaceStateSnapshots() { }

    static Object canonical(Object value) {
        if (value==null) return null;
        if (value instanceof com.openggf.sprites.animation.SpriteAnimationSet animations)
            return Map.of("resourceType",animations.getClass().getName(),"scripts",canonical(animations.getAllScripts()));
        if (value instanceof com.openggf.game.rewind.schema.RewindObjectStateBlob blob)
            return Map.of("schemaId",blob.schemaId(),"type",blob.type().getName(),
                    "scalarData",canonical(blob.scalarData()),"opaqueValues",canonical(blob.opaqueValues()));
        if (value instanceof AbstractPlayableSprite player) return "player:"+player.getCode();
        if (value instanceof com.openggf.level.Palette palette) {
            var colours=new ArrayList<Integer>();
            for(int i=0;i<16;i++) {
                var colour=palette.getColor(i);
                colours.add(((colour.r&255)<<16)|((colour.g&255)<<8)|(colour.b&255));
            }
            return colours;
        }
        if (value instanceof com.openggf.level.Block block) return canonical(block.saveState());
        if (value instanceof com.openggf.level.Chunk chunk) return canonical(chunk.saveState());
        if (value instanceof com.openggf.level.PatternDesc desc) return desc.get();
        if (value instanceof com.openggf.level.ChunkDesc desc) return desc.get();
        if (value instanceof com.openggf.level.Pattern pattern) {
            var pixels=new ArrayList<Integer>();
            for(int y=0;y<8;y++) for(int x=0;x<8;x++) pixels.add(pattern.getPixel(x,y)&255);
            return pixels;
        }
        var type=value.getClass();
        if(type.isArray()) {
            var list=new ArrayList<Object>();
            for(int i=0;i<java.lang.reflect.Array.getLength(value);i++) list.add(canonical(java.lang.reflect.Array.get(value,i)));
            return list;
        }
        if(value instanceof List<?> list) return list.stream().map(RaceStateSnapshots::canonical).toList();
        if(value instanceof Map<?,?> map) {
            var out=new TreeMap<String,Object>();map.forEach((key,entry)->out.put(String.valueOf(canonical(key)),canonical(entry)));return out;
        }
        if(type.isRecord()) {
            var out=new TreeMap<String,Object>();out.put("record-type",type.getName());
            for(var field:type.getRecordComponents()) try {
                var accessor=field.getAccessor();accessor.setAccessible(true);out.put(field.getName(),canonical(accessor.invoke(value)));
            } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
            return out;
        }
        return value;
    }
}

package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;

import java.lang.reflect.Array;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test-only captured VALUE equality, promoted from the private World-fixture comparators.
 * CdAssetBytes and CpuSat expose defensive byte reads; records/containers are compared recursively.
 * Every other value keeps its own equals, including opaque owners and knowledge-scoped states.
 * This assertion grants no source, phase, invocation, capture, mutation or adoption authority.
 * Keep assertSame checks for actual owner/World/Frame/source identity facts at their call sites.
 * No arbitrary object-field reflection, state serialization, cloning or hydration is performed.
 */
final class CdCapturedValueAssertions {
    private static final int MAX_DEPTH=64;
    private CdCapturedValueAssertions(){ }
    static void assertCapturedValueEquals(Object expected,Object actual){assertCapturedValueEquals(expected,actual,"value");}
    static void assertCapturedValueEquals(Object expected,Object actual,String path){new Comparison().same(expected,actual,Objects.requireNonNull(path,"field path"),0);}

    private static final class Comparison {
        private final IdentityHashMap<Object,IdentityHashMap<Object,Boolean>> active=new IdentityHashMap<>();
        void same(Object expected,Object actual,String path,int depth) {
            if(expected==actual)return;
            if(expected==null || actual==null){assertEquals(expected,actual,path);return;}
            if(depth>MAX_DEPTH)fail(path+": captured-value depth exceeds "+MAX_DEPTH);
            if(expected instanceof CdAssetBytes bytes){bytes(bytes.copy(),type(CdAssetBytes.class,actual,path).copy(),path);return;}
            if(expected instanceof CdObjectDrawQueue.CpuSat sat){
                var other=type(CdObjectDrawQueue.CpuSat.class,actual,path);bytes(sat.bytes(),other.bytes(),path+".bytes");
                assertEquals(sat.spriteCount(),other.spriteCount(),path+".spriteCount");same(sat.provenance(),other.provenance(),path+".provenance",depth+1);return;
            }
            boolean structured=expected instanceof Optional<?> || expected instanceof Map<?,?> || expected instanceof List<?>
                    || expected.getClass().isArray() || expected.getClass().isRecord();
            if(!structured){assertEquals(expected,actual,path);return;}
            var pairs=active.computeIfAbsent(expected,ignored->new IdentityHashMap<>());
            if(pairs.put(actual,Boolean.TRUE)!=null)fail(path+": distinct captured-value graphs contain a cycle");
            try {
                if(expected instanceof Optional<?> option){var other=type(Optional.class,actual,path);assertEquals(option.isPresent(),other.isPresent(),path+".present");if(option.isPresent())same(option.orElseThrow(),other.orElseThrow(),path+".value",depth+1);}
                else if(expected instanceof Map<?,?> map){var other=type(Map.class,actual,path);assertEquals(map.keySet(),other.keySet(),path+".keys");for(var key:map.keySet())same(map.get(key),other.get(key),path+"["+key+"]",depth+1);}
                else if(expected instanceof List<?> list){var other=type(List.class,actual,path);assertEquals(list.size(),other.size(),path+".size");for(int i=0;i<list.size();i++)same(list.get(i),other.get(i),path+"["+i+"]",depth+1);}
                else if(expected.getClass().isArray()){
                    assertEquals(expected.getClass(),actual.getClass(),path+".type");
                    if(expected instanceof byte[] bytes)bytes(bytes,(byte[])actual,path);
                    else{int length=Array.getLength(expected);assertEquals(length,Array.getLength(actual),path+".length");for(int i=0;i<length;i++)same(Array.get(expected,i),Array.get(actual,i),path+"["+i+"]",depth+1);}
                } else {
                    assertEquals(expected.getClass(),actual.getClass(),path+".type");
                    for(var component:expected.getClass().getRecordComponents()){
                        String field=path+"."+component.getName();var accessor=component.getAccessor();
                        try{accessor.setAccessible(true);same(accessor.invoke(expected),accessor.invoke(actual),field,depth+1);}
                        catch(ReflectiveOperationException|SecurityException failure){fail(field+": cannot read captured record component",failure);}
                    }
                }
            } finally {pairs.remove(actual);if(pairs.isEmpty())active.remove(expected);}
        }
    }
    private static <T>T type(Class<T> kind,Object actual,String path){return assertInstanceOf(kind,actual,path+".type");}
    private static void bytes(byte[] expected,byte[] actual,String path){assertEquals(expected.length,actual.length,path+".length");for(int i=0;i<expected.length;i++)if(expected[i]!=actual[i])assertEquals(expected[i],actual[i],path+"["+i+"]");}
}

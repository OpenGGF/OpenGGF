package com.openggf.tests;

import java.util.Map;
import java.util.NavigableMap;
import java.util.function.BiPredicate;

/**
 * Purpose: comparison-only boundary for unsupported native CPU overruns.
 * Inputs: consecutive completed physical samples, native body counter and V-int.
 * Origin: lane F, lead ruling2026-11-08; actual EHZ IRQ PC$16E28.
 * It never drives input, gameplay, clocks or a lag selector, and never re-aligns.
 */
public final class NativeReplayLagBoundary {
    private NativeReplayLagBoundary() { }
    public record Boundary(int frame, long bodyCounter, long previousVint, long vint) { }

    public static Boundary first(NavigableMap<Integer,Map<String,Long>> rows, int first, int last,
            String bodyCounter, BiPredicate<Map<String,Long>,Map<String,Long>> sameBodyPhase) {
        if (first > last) return null;
        for (var entry : rows.subMap(first,true,last,true).entrySet()) {
            var previous=rows.get(entry.getKey()-1);
            var current=entry.getValue();
            if(previous==null || !sameBodyPhase.test(previous,current)) continue;
            long before=value(previous,bodyCounter),after=value(current,bodyCounter);
            long previousVint=value(previous,"vint"),vint=value(current,"vint");
            if(before==after && ((vint-previousVint)&0xFFFFFFFFL)==1)
                return new Boundary(entry.getKey(),after,previousVint,vint);
        }
        return null;
    }
    private static long value(Map<String,Long> row,String field) {
        Long value=row.get(field);
        if(value==null)throw new IllegalArgumentException("Missing native clock: "+field);
        return value;
    }
}

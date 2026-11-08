package com.openggf.editor.route;

import com.openggf.route.*;
import java.util.*;

/** Authoring catalogue of the S2 route observer's documented copied facts; never gameplay input. */
public final class RouteCompetitionFacts {
    private RouteCompetitionFacts() { }
    public static final List<String> KEYS;
    static {
        var keys=new ArrayList<String>(List.of("competition.phase","competition.selection","competition.romZone",
                "competition.act","competition.resultsReady","competition.firstFinisher","competition.specialStage",
                "competition.paused","input.menu","input.gameplayAnchorRows","title.state","title.selection",
                "competition.zoneWins.p1","competition.zoneWins.p2","competition.slot.inUse","competition.slot.routine",
                "competition.slot.timer","competition.slot.position0","competition.slot.position1","competition.slot.position2"));
        for(String player:List.of("p1","p2")) for(String metric:List.of("rings","totalRings","monitors","lives",
                "timerPaused","timeOver","score","time","cameraX","cameraY","routine","dead","objectControlled"))
            keys.add("competition."+player+"."+metric);
        for(String field:List.of("visible","player","screenX","screenY","word","rgb","frame","displayedFrame0",
                "displayedFrame1","planeVisible","cachedCells")) keys.add("competition.flip."+field);
        // The owner indexes animation submissions. More indices may be typed in the text editor;
        // their presence at a particular zone/phase is checked against observed values, not assumed.
        for(int i=0;i<16;i++) for(String suffix:List.of("nextFrame","timer")) keys.add("competition.animation."+i+"."+suffix);
        KEYS=List.copyOf(keys);
    }
    public static void requireKey(String key) {
        if(key==null || key.isBlank()) throw new IllegalArgumentException("Missing fact key");
        if(!KEYS.contains(key) && !key.matches("competition\\.animation\\.[0-9]+\\.(nextFrame|timer)"))
            throw new IllegalArgumentException("Unknown fact key: "+key);
    }
    public static List<String> keys(SamplePredicate predicate) {
        return switch(predicate) {
            case SamplePredicate.FactEquals f -> List.of(f.key());
            case SamplePredicate.FactsEqual f -> List.of(f.left(),f.right());
            case SamplePredicate.All all -> all.clauses().stream().flatMap(p->keys(p).stream()).distinct().toList();
            default -> List.of();
        };
    }
    public static void validate(GoalProgram goals) {
        goals.routeGuard().holds().ifPresent(p->keys(p).forEach(RouteCompetitionFacts::requireKey));
        for(var leg:goals.legs()) {
            leg.guard().holds().ifPresent(p->keys(p).forEach(RouteCompetitionFacts::requireKey));
            if(leg.goal() instanceof RouteGoal.Achieve a) keys(a.condition()).forEach(RouteCompetitionFacts::requireKey);
        }
    }
}

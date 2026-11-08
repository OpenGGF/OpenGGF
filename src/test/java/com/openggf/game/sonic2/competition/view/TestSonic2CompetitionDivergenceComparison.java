package com.openggf.game.sonic2.competition.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic2CompetitionDivergenceComparison {
    @TempDir Path temporary;
    @Test void everyComparedColumnDetectsADeliberateSingleColumnOffset() throws Exception {
        var fields=List.of("p1_x","p1_y","p2_x","p2_y","p1_vx","p1_vy","p2_vx","p2_vy",
                "p1_camx","p1_camy","p2_camx","p2_camy","p1_rings","p2_rings","p1_lives","p2_lives",
                "p1_score","p2_score","lfc","vint");
        String header="frame,"+String.join(",",fields);
        String values="1330,"+String.join(",",Collections.nCopies(fields.size(),"7"));
        Path csv=temporary.resolve("state.csv");Files.writeString(csv,header+"\n"+values+"\n");
        var nativeRow=DebugSonic2CompetitionDivergence.readRows(csv).get(1330);
        var actual=new LinkedHashMap<>(nativeRow);actual.remove("frame");
        assertEquals("",DebugSonic2CompetitionDivergence.difference(actual,nativeRow,Set.of()));
        for(String field:fields){var broken=new LinkedHashMap<>(nativeRow);broken.put(field,8L);
            assertEquals(field+" engine=7 native=8",DebugSonic2CompetitionDivergence.difference(actual,broken,Set.of()),field);}
        actual.put("vint",99L);
        assertEquals("",DebugSonic2CompetitionDivergence.difference(actual,nativeRow,Set.of("vint")));
    }
    @Test void duplicateFramesAndTruncatedRowsFailClosed() throws Exception {
        Path csv=temporary.resolve("state.csv");Files.writeString(csv,"frame,p1_x\n1,7\n1,8\n");
        assertThrows(IllegalArgumentException.class,()->DebugSonic2CompetitionDivergence.readRows(csv));
        Files.writeString(csv,"frame,p1_x\n1\n");
        assertThrows(IllegalArgumentException.class,()->DebugSonic2CompetitionDivergence.readRows(csv));
    }
}

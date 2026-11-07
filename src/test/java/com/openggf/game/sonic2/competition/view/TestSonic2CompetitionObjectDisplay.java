package com.openggf.game.sonic2.competition.view;

import com.openggf.game.ObjectArtProvider;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.*;
import com.openggf.level.render.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2CompetitionObjectDisplay {
    @Test void citedInitOperandsAndAdjustmentPreserveAttributes() {
        var explosion=Sonic2CompetitionSpriteArtTable.entry(0x27);
        assertEquals(0x21120,explosion.mappingsAddress());
        assertEquals(0x5A4,explosion.artTile1P());
        assertEquals(12,explosion.widthPixels());
        assertEquals(4,explosion.renderFlagsInit());
        assertEquals(0,explosion.yRadius());
        var points=Sonic2CompetitionSpriteArtTable.entry(0x29);
        assertEquals(0x11ED0,points.mappingsAddress());
        assertEquals(0x84AC,points.artTile1P());
        assertEquals(8,points.widthPixels());
        assertEquals(4,points.renderFlagsInit());
        assertEquals(0,points.yRadius());
        for(int word=0;word<=0xFFFF;word++)
            assertEquals((word/2048)*2048+(word%2048)/2,
                    Sonic2CompetitionSpriteArtTable.adjust2PArtPointer(word));
    }

    @Test void eachRowProjectsOnlyItsRendererWithFlipsPalettePriorityAndWordCoordinates() {
        var sheet=new ObjectSpriteSheet(new Pattern[]{new Pattern()},List.of(
                new SpriteMappingFrame(List.of(new SpriteMappingPiece(0,0,1,1,0,false,false,0)))),0,1);
        var renderer=new PatternSpriteRenderer(sheet,new GraphicsManager());
        var art=mock(ObjectArtProvider.class);
        for(var row:Sonic2CompetitionSpriteArtTable.entries().entrySet()) {
            when(art.getRenderer(row.getValue().expectedArtKey())).thenReturn(renderer);
            var owner=new Probe(row.getKey());
            var slot=Sonic2CompetitionObjectDisplay.project(owner,art,()->renderer.drawFrameIndex(0,-2,0x10003,true,true,2));
            assertEquals(row.getKey().intValue(),slot.id());
            assertEquals(7,slot.renderFlags());
            assertEquals(65534,slot.x()); assertEquals(3,slot.y());
            assertEquals(row.getValue().mappingsAddress(),slot.mapping().address());
            assertEquals(0,slot.mapping().frame());
            assertEquals((row.getValue().artTile1P()&2047)/2 | 0xC000,slot.artTile());
            assertEquals(row.getValue().widthPixels(),slot.width());
            assertNull(Sonic2CompetitionObjectDisplay.project(owner,art,()->{}));
            assertThrows(IllegalStateException.class,()->Sonic2CompetitionObjectDisplay.project(owner,art,()->{
                renderer.drawFrameIndex(0,0,0);renderer.drawFrameIndex(0,1,1);
            }));
            assertThrows(IllegalStateException.class,()->Sonic2CompetitionObjectDisplay.project(owner,art,
                    ()->renderer.drawPatternIndex(0,0,0,0)));
            var other=new PatternSpriteRenderer(sheet,new GraphicsManager());
            assertThrows(IllegalStateException.class,()->Sonic2CompetitionObjectDisplay.project(owner,art,
                    ()->other.drawFrameIndex(0,0,0)));
        }
        assertThrows(IllegalStateException.class,()->Sonic2CompetitionObjectDisplay.project(new Probe(0x41),art,
                ()->renderer.drawFrameIndex(0,0,0)));
        assertNull(Sonic2CompetitionObjectDisplay.project(new Probe(0),art,()->fail("ID zero cannot draw")));
        assertNull(Sonic2CompetitionObjectDisplay.project(new Explicit(),art,()->fail("Explicit source takes precedence")));
    }
    private static class Probe extends AbstractObjectInstance {
        Probe(int id){super(new ObjectSpawn(0,0,id,0,0,false,0),"projection-probe");}
        @Override public boolean isHighPriority(){return true;}
        @Override public void update(int clock,PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
    private static final class Explicit extends Probe implements Sonic2NativeDisplaySource {
        Explicit(){super(0x41);}
        @Override public com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.ObjectSlot nativeDisplaySlot(){return null;}
    }
}

package com.openggf.game.sonic3k.objects;

import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.level.objects.ObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static com.openggf.game.sonic3k.objects.DezEndBossDamageState.Contact.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezEndBossDamageState {
    private record Fixture(ObjectServices services,List<PaletteWrite> writes) { }
    private Fixture fixture() throws Exception {
        var services=mock(ObjectServices.class); var registry=mock(PaletteOwnershipRegistry.class);
        when(services.rom()).thenReturn(TestEnvironment.objectServices().rom());
        when(services.paletteOwnershipRegistryOrNull()).thenReturn(registry);
        List<PaletteWrite> writes=new ArrayList<>();
        doAnswer(call->{writes.add(call.getArgument(0));return null;}).when(registry).submit(any());
        return new Fixture(services,writes);
    }
    @Test void enemyCentredSignedRangePreservesTheAsymmetricEdgesOnBothAxes() {
        for(int x:new int[]{-41,-40,-39,0,39,40,41}) for(int y:new int[]{-41,-40,-39,0,39,40,41}) {
            var state=new DezEndBossDamageState();
            var expected=x>-40&&x<=40&&y>-40&&y<=40?HIT:NONE;
            assertEquals(expected,state.enemyContact(0x3500,0x2A0,0x3500+x,0x2A0+y,false,-1),x+":"+y);
        }
        assertEquals(NONE,new DezEndBossDamageState().enemyContact(0x7FF0,0,0x8000,0,false,-1),
                "signed native endpoints straddle $8000 and reject this range");
    }
    @Test void verticalOrientationChoosesDamageButWrongOrientationStillConsumesTheEnemy() {
        for(boolean flip:new boolean[]{false,true}) for(int velocity:new int[]{-0x8000,-1,0,1,0x7FFF}) {
            var state=new DezEndBossDamageState(); boolean matches=flip^(velocity<0);
            assertEquals(matches?HIT:CONSUME,state.enemyContact(100,100,100,100,flip,velocity));
            assertEquals(8,state.health(),"enemy pass does not decrement root health");
            assertEquals(matches,state.invulnerable());
        }
    }
    @Test void latchBlocksFurtherEnemiesAndTheRootFlashesExactlyThirtyTwoPasses() throws Exception {
        var f=fixture(); var state=new DezEndBossDamageState();
        assertEquals(HIT,state.enemyContact(100,100,100,100,false,-1));
        int[] destinations={3,4,11,12,13,14};
        int[][] colors={{0x888,0xAAA,0x888,0xAAA,0xCCC,0xEEE},{0x2A,6,0xA48,0x644,0x422,0}};
        for(int pass=0;pass<32;pass++) {
            assertEquals(NONE,state.enemyContact(100,100,100,100,false,-1));
            assertFalse(state.update(f.services)); assertEquals(7,state.health());
            assertEquals(pass<31,state.invulnerable());
            assertEquals((pass+1)*6,f.writes.size());
            for(int i=0;i<6;i++) {
                var write=f.writes.get(pass*6+i); assertEquals(1,write.lineIndex());
                assertEquals(destinations[i],write.startColor());
                assertArrayEquals(new byte[]{(byte)(colors[pass&1][i]>>>8),(byte)colors[pass&1][i]},write.segaData());
            }
        }
        assertFalse(state.update(f.services)); assertEquals(192,f.writes.size());
        assertEquals(HIT,state.enemyContact(100,100,100,100,false,-1));
    }
    @Test void eighthHitPublishesDefeatOnceWithoutAFlashAndRestorationReplaysPendingHits() throws Exception {
        var f=fixture(); var state=new DezEndBossDamageState();
        for(int hit=0;hit<7;hit++) {
            assertEquals(HIT,state.enemyContact(100,100,100,100,false,-1));
            for(int pass=0;pass<32;pass++) assertFalse(state.update(f.services));
        }
        assertEquals(1,state.health());
        state.enemyContact(100,100,100,100,true,1); var saved=state.captureRewindStateValue();
        int writes=f.writes.size(); assertTrue(state.update(f.services));
        assertEquals(0,state.health()); assertTrue(state.defeated());
        assertEquals(writes,f.writes.size()); assertFalse(state.update(f.services));
        assertEquals(NONE,state.enemyContact(100,100,100,100,false,-1));
        var restored=new DezEndBossDamageState(); restored.restoreRewindStateValue(saved);
        assertTrue(restored.update(f.services)); assertEquals(state.captureRewindStateValue(),restored.captureRewindStateValue());
    }
}

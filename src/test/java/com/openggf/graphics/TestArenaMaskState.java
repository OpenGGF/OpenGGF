package com.openggf.graphics;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestArenaMaskState {
    @Test void activationReleaseReversalAndRewindAreDeterministic() {
        var state = new ArenaMaskState();
        assertEquals(0, state.intensity());
        state.activate(320);
        for (int i=0;i<45;i++) { state.activate(320); state.advance(); }
        assertEquals(1, state.intensity());
        var saved = ByteBuffer.allocate(ArenaMaskState.SNAPSHOT_BYTES);
        state.writeTo(saved);
        state.release(); state.advance(); float fading=state.intensity();
        assertTrue(fading<1 && fading>0);
        int tick=state.noiseFrame();
        assertEquals(tick,state.noiseFrame(), "reading/rendering never advances the clock");
        state.activate(320); state.advance(); assertEquals(1,state.intensity());
        state.readFrom(saved.flip()); assertEquals(45,state.noiseFrame());
        state.release(); state.advance(); assertEquals(fading,state.intensity());
        for(int i=0;i<44;i++) state.advance();
        assertEquals(0,state.intensity());
        assertEquals(0,new ArenaMaskState().intensity(), "fresh loads have no inherited mask");
        assertThrows(IllegalArgumentException.class,()->state.activate(0));
    }
}

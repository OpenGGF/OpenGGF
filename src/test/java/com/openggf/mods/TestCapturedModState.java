package com.openggf.mods;

import com.openggf.mods.state.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestCapturedModState {
    @Test void randomRetainsHistoricalXorshiftSequenceAcrossRestore() {
        long state=0x5EED5EEDL;
        var random=new SnapshotRandom(state);
        for (int i=0;i<256;i++) {
            state ^= state >>> 12; state ^= state << 25; state ^= state >>> 27;
            assertEquals(state*0x2545F4914F6CDD1DL,random.nextLong());
        }
        long snapshot=random.snapshot();
        long expected=random.nextLong();
        random.restore(snapshot); assertEquals(expected,random.nextLong());
        long before=random.snapshot();
        assertEquals(0,random.nextInt(1)); assertEquals(0,random.nextInt(0));
        assertEquals(before,random.snapshot());
        random.restore(0);
        assertEquals(0,random.nextLong()); assertEquals(0,random.snapshot());
    }
    @Test void boundedPoolDeepCopiesAndRestoresAtomically() {
        var pool=new CapturedPool<int[]>(2,int[]::clone);
        int[] value={1}; pool.add(value);
        var snapshot=pool.snapshot(); value[0]=9;
        assertEquals(1,snapshot.getFirst()[0]);
        pool.add(new int[]{2}); pool.add(new int[]{3});
        assertEquals(2,pool.get(0)[0]);
        pool.restore(snapshot); snapshot.getFirst()[0]=7;
        assertEquals(1,pool.get(0)[0]);
        assertThrows(IllegalArgumentException.class,()->pool.restore(List.of(new int[]{1},new int[]{2},new int[]{3})));
        assertEquals(1,pool.get(0)[0]);
    }
    @Test void unversionedExampleSettingsRetainExactFormatAndFutureFormatsAreRejected() {
        String existing="bank=42\nemeralds=3\nshop.0=2\n";
        var settings=VersionedSettings.parse(existing).requireVersion(0);
        assertEquals(existing,settings.serialize());
        assertEquals("bank=45\nemeralds=3\nshop.0=2\n",settings.with("bank","45").serialize());
        assertEquals(2,VersionedSettings.empty(2).version());
        assertThrows(IllegalArgumentException.class,()->VersionedSettings.parse("formatVersion=2\n").requireVersion(0));
        assertThrows(IllegalArgumentException.class,()->settings.with("../bad\nkey","1"));
    }
}

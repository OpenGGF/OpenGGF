package com.openggf.level;

import com.openggf.level.objects.ObjectSpawn;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelPatch {
    private ObjectSpawn spawn(int slot, String owner) {
        return new ObjectSpawn(100,200,0x41,3,2,true,0xA0C8,slot,owner,owner==null ? null : owner+":spring");
    }
    @Test void transformationsPreserveOrderOwnerSlotAndFlagsAndRunOnce() {
        var owned=spawn(1,"another-mod"); var nativeSpawn=spawn(2,null); var removed=spawn(3,null);
        AtomicInteger callbacks=new AtomicInteger();
        var patch=LevelPatch.empty().select(s->s.layoutIndex()==3).remove()
                .select(s->s.layoutIndex()==1).replace(s->{callbacks.incrementAndGet(); return s.withPosition(120,220);})
                .select(s->s.layoutIndex()==2).bind("sample","sample:spring");
        var result=patch.applyToObjects(List.of(owned,nativeSpawn,removed));
        assertEquals(1,callbacks.get());
        assertEquals(List.of(1,2),result.stream().map(ObjectSpawn::layoutIndex).toList());
        assertEquals("another-mod:spring",result.get(0).objectKey());
        assertEquals(0xA0DC,result.get(0).rawYWord());
        assertEquals(owned.renderFlags(),result.get(0).renderFlags());
        assertTrue(result.get(0).respawnTracked());
        assertEquals("sample:spring",result.get(1).objectKey());
        assertEquals(100,owned.x(),"Source record remains immutable");
    }
    @Test void rejectsIdentityTheftDuplicateSlotsAndCoordinateTruncation() {
        var nativeSpawn=spawn(1,null); var owned=spawn(2,"other");
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().applyToObjects(List.of(nativeSpawn,nativeSpawn)));
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().select(s->true).replace(s->spawn(8,null)).applyToObjects(List.of(nativeSpawn)));
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().select(s->true).replace(s->spawn(1,"thief")).applyToObjects(List.of(nativeSpawn)));
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().select(s->true).bind("thief","thief:spring").applyToObjects(List.of(owned)));
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().select(s->true).move(-101,0).applyToObjects(List.of(nativeSpawn)));
    }
}

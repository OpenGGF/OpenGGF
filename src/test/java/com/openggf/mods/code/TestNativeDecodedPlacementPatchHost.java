package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.level.AbstractLevel;
import com.openggf.level.LevelPatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.io.ModAssetRoot;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeDecodedPlacementPatchHost {
    private static final class NativeFixture extends AbstractLevel {
        NativeFixture() {
            super(0);
            objects=List.of(new ObjectSpawn(100,200,0x8d,0,0,false,200,1));
            rings=List.of(new RingSpawn(120,180,1),new RingSpawn(140,180,2));
        }
    }
    private static ModFaultBoundary boundary() {
        return new ModFaultBoundary(Map.of(),new ModRuntimeFindingStore(),
                ignored->new ModStateSaveResult.Saved(),ignored->{ });
    }

    @Test void hostAdmitsAndAppliesNativePlacementCallbacksWithinTheRegisteredOwnerBoundary() {
        var context=new ModContext("sample","s3k",ModAssetRoot.forTests("sample"));
        context.registerObject("guard",(spawn,registry)->null);
        var calls=new AtomicInteger();
        context.decodedLevelPatch("placements",LevelPatch.empty().forZone(0).nativePlacements()
                .whenInventory(objects->{assertEquals("sample",OwnerCallbackScope.current());return true;})
                .select(s->s.ownerModId()==null).bind("guard")
                .retainRings((rings,objects)->{
                    assertEquals("sample",OwnerCallbackScope.current());calls.incrementAndGet();
                    assertNull(objects.getFirst().ownerModId());return List.of(rings.getFirst());
                }));
        var base=mock(GameModule.class,CALLS_REAL_METHODS);
        var module=OwnedModServices.decorate(base,context.freeze(),boundary());
        var source=new NativeFixture();
        assertSame(source,module.transformDecodedLevel(source));
        assertEquals("sample:guard",source.getObjects().getFirst().objectKey());
        assertEquals(1,source.getRings().size());assertEquals(1,calls.get());
        assertNull(OwnerCallbackScope.current());
    }

    @Test void failedOwnerRetentionLeavesBothNativeTablesUnpublished() {
        var context=new ModContext("sample","s3k",ModAssetRoot.forTests("sample"));
        context.registerObject("guard",(spawn,registry)->null);
        context.decodedLevelPatch("placements",LevelPatch.empty().nativePlacements()
                .select(s->s.ownerModId()==null).bind("guard")
                .retainRings((rings,objects)->{
                    assertEquals("sample",OwnerCallbackScope.current());
                    throw new IllegalArgumentException("controlled retention failure");
                }));
        var module=OwnedModServices.decorate(mock(GameModule.class,CALLS_REAL_METHODS),context.freeze(),boundary());
        var source=new NativeFixture();var objects=source.getObjects();var rings=source.getRings();
        assertThrows(RuntimeException.class,()->module.transformDecodedLevel(source));
        assertSame(objects,source.getObjects());assertSame(rings,source.getRings());
        assertNull(OwnerCallbackScope.current());
    }
}

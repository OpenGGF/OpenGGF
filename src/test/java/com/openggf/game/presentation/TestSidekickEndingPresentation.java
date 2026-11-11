package com.openggf.game.presentation;

import com.openggf.game.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSidekickEndingPresentation {
    @Test void absentUnknownAndBuiltinSidekicksKeepNativePilot() throws Exception {
        var registry=PlayableCharacterRegistry.empty();
        for(var codes:List.of(List.<String>of(),List.of("tails"),List.of("missing:bird")))
            assertNull(EndingCharacterPresentation.sidekick(registry,codes,EndingSpriteSet.Surface.S2_TORNADO_PILOT));
    }
    @Test void firstCompanionOwnsThePilotAndMissingSlotsKeepNative() throws Exception {
        var key=CharacterKey.parsePersisted("test:bird");
        var image=new CharacterPresentation.Image(24,16,new byte[24*16]);
        var set=new EndingSpriteSet(new StageSpriteSet(Map.of(1,new StageSpriteSet.Frame(image,48,8))),new int[16]);
        var definition=new CharacterDefinition(key,"Bird",(code,x,y)->null,null,PlayerCharacter.TAILS_ALONE,
                com.openggf.sprites.playable.SecondaryAbility.NONE,false,code->null,null,Set.of(),null,
                code->new CharacterPresentation("Bird","Bird",Map.of(),Map.of(),Map.of(),
                        Map.of(EndingSpriteSet.Surface.S2_TORNADO_PILOT,set)),null);
        var registry=PlayableCharacterRegistry.empty().register(key,definition);
        assertSame(set,EndingCharacterPresentation.sidekick(registry,List.of("test:bird","tails"),EndingSpriteSet.Surface.S2_TORNADO_PILOT));
        assertNull(EndingCharacterPresentation.sidekick(registry,List.of("tails","test:bird"),EndingSpriteSet.Surface.S2_TORNADO_PILOT));
        assertNull(EndingCharacterPresentation.sidekick(registry,List.of("test:bird"),EndingSpriteSet.Surface.S2_PHOTOS));
    }
    @Test void soloPilotIsExplicitCompanionArtRatherThanTheLeadOrAnArbitraryRegistryEntry() throws Exception {
        var key=CharacterKey.parsePersisted("test:lead");
        var image=new CharacterPresentation.Image(24,16,new byte[24*16]);
        var own=new EndingSpriteSet(new StageSpriteSet(Map.of(1,new StageSpriteSet.Frame(image,48,8))),new int[16]);
        var companion=new EndingSpriteSet(new StageSpriteSet(Map.of(45,new StageSpriteSet.Frame(image,48,8))),new int[16]);
        var definition=new CharacterDefinition(key,"Lead",(code,x,y)->null,null,PlayerCharacter.TAILS_ALONE,
                com.openggf.sprites.playable.SecondaryAbility.NONE,false,code->null,null,Set.of(),null,
                code->new CharacterPresentation("Lead","Lead",Map.of(),Map.of(),Map.of(),Map.of(
                        EndingSpriteSet.Surface.S2_TORNADO_PILOT,own,
                        EndingSpriteSet.Surface.S2_SOLO_TORNADO_PILOT,companion,
                        EndingSpriteSet.Surface.S3K_SOLO_TORNADO_PILOT,companion)),null);
        var registry=PlayableCharacterRegistry.empty().register(key,definition);
        assertSame(companion,EndingCharacterPresentation.pilot(registry,"test:lead",List.of(),EndingSpriteSet.Surface.S2_TORNADO_PILOT));
        assertSame(companion,EndingCharacterPresentation.pilot(registry,"test:lead",List.of(),EndingSpriteSet.Surface.S3K_TORNADO_PILOT));
        assertSame(own,EndingCharacterPresentation.pilot(registry,"sonic",List.of("test:lead"),EndingSpriteSet.Surface.S2_TORNADO_PILOT));
        assertNull(EndingCharacterPresentation.pilot(registry,"test:lead",List.of("tails"),EndingSpriteSet.Surface.S2_TORNADO_PILOT));
        assertNull(EndingCharacterPresentation.pilot(registry,"tails",List.of(),EndingSpriteSet.Surface.S2_TORNADO_PILOT));
        assertNull(EndingCharacterPresentation.pilot(registry,"missing:lead",List.of(),EndingSpriteSet.Surface.S2_TORNADO_PILOT));
    }
    @Test void clearMasksAreBoundedBinaryAndDetached() {
        var image=new CharacterPresentation.Image(2,2,new byte[4]);
        var frames=new StageSpriteSet(Map.of(0,new StageSpriteSet.Frame(image,0,0)));
        assertTrue(new EndingSpriteSet(frames,new int[16]).nativeClearMasks().isEmpty());
        var source=new HashMap<Integer,CharacterPresentation.Image>();
        source.put(0,new CharacterPresentation.Image(2,2,new byte[]{1,0,0,1}));
        var set=new EndingSpriteSet(frames,new int[16],source);source.clear();
        assertEquals(1,set.nativeClearMasks().size());
        assertThrows(UnsupportedOperationException.class,()->set.nativeClearMasks().clear());
        assertThrows(IllegalArgumentException.class,()->new EndingSpriteSet(frames,new int[16],Map.of(1,image)));
        assertThrows(IllegalArgumentException.class,()->new EndingSpriteSet(frames,new int[16],Map.of(0,new CharacterPresentation.Image(1,1,new byte[1]))));
        assertThrows(IllegalArgumentException.class,()->new EndingSpriteSet(frames,new int[16],Map.of(0,new CharacterPresentation.Image(2,2,new byte[]{2,0,0,0}))));
    }
}

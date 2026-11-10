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
}

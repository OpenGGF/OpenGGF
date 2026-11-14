package com.openggf.game.sonic3k.competition;

import com.openggf.game.*;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.SecondaryAbility;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kCompetitionCharacterArt {
    @Test void configuredModCharactersReplaceOnlyTheirNativeArchetypeSlots() {
        var mica=definition("mica",PlayerCharacter.SONIC_AND_TAILS);
        var talon=definition("talon",PlayerCharacter.TAILS_ALONE);
        var registry=PlayableCharacterRegistry.empty().register(mica.key(),mica).register(talon.key(),talon);
        var slots=S3kCompetitionCharacterArt.slots(registry,List.of(mica.key().persisted(),talon.key().persisted()));
        assertEquals(mica,slots.get(S3kCompetitionPlayer.Character.SONIC));
        assertEquals(talon,slots.get(S3kCompetitionPlayer.Character.TAILS));
        assertFalse(slots.containsKey(S3kCompetitionPlayer.Character.KNUCKLES));
        assertTrue(S3kCompetitionCharacterArt.slots(registry,List.of("sonic","tails")).isEmpty());
    }
    @Test void nativeIdsAndIndependentHotspotsSurviveStaticCompilation() {
        var set=new StageSpriteSet(Map.of(7,new StageSpriteSet.Frame(
                new CharacterPresentation.Image(1,1,new byte[]{14}),3,9),
                38,new StageSpriteSet.Frame(new CharacterPresentation.Image(1,1,new byte[]{2}),-4,5)));
        var sheet=S3kCompetitionCharacterArt.compile(set);
        assertEquals(39,sheet.getFrameCount());
        assertTrue(sheet.getFrame(0).pieces().isEmpty());
        var first=sheet.getFrame(7).pieces().getFirst();
        var second=sheet.getFrame(38).pieces().getFirst();
        assertEquals(-3,first.xOffset()); assertEquals(-9,first.yOffset());
        assertEquals(4,second.xOffset()); assertEquals(-5,second.yOffset());
        assertEquals(14,sheet.getPatterns()[first.tileIndex()].getPixel(0,0));
        assertEquals(2,sheet.getPatterns()[second.tileIndex()].getPixel(0,0));
    }
    @Test void missingBankIsAnExplicitNativeFallback() throws Exception {
        var banks=S3kCompetitionCharacterArt.load(Map.of(S3kCompetitionPlayer.Character.TAILS,definition("talon",PlayerCharacter.TAILS_ALONE)));
        assertTrue(banks.sheets().isEmpty()); assertTrue(banks.ids().isEmpty());
    }
    private static CharacterDefinition definition(String id,PlayerCharacter archetype) {
        return new CharacterDefinition(CharacterKey.mod("test",id),id,(code,x,y)->null,null,
                archetype,SecondaryAbility.NONE,false,code->SpriteArtSet.EMPTY);
    }
}

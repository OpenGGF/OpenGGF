package com.openggf.mods;

import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real catalog order and successful prepared-audio arbitration, including decode failures. */
public final class PreparedCompositionAssertions {
    private PreparedCompositionAssertions() { }
    public static void verify() {
        var first=descriptor("first","s2",Map.of(12,"music"),Map.of(),List.of(),
                new ModCompositionMetadata(List.of(),List.of("second"),List.of(),Set.of()));
        var second=descriptor("second","s2",Map.of(12,"music"),Map.of(),List.of(),ModCompositionMetadata.EMPTY);
        for(var discovery:List.of(List.of(first,second),List.of(second,first))) {
            var catalog=new EffectiveCatalogBuilder().build(discovery,enabled(first,second));
            assertEquals(List.of("second","first"),catalog.effective().orderedEnabled().stream().map(d->d.manifest().id()).toList());
            var firstTrack=track("first"); var secondTrack=track("second");
            var registry=registry(List.of(firstTrack,secondTrack));
            try(var music=PreparedModMusic.build(catalog.effective(),registry,
                    new PreparedAudioSession(List.of(secondTrack,firstTrack),List.of(),Set.of()),8000)) {
                var target=music.overrideReport().getFirst();
                assertEquals(firstTrack.key(),target.winner());assertEquals(List.of(secondTrack.key()),target.shadowed());
                assertSame(firstTrack,ModMusicResolver.from(music).resolveStockOverride("s2",12).orElseThrow().track());
                assertThrows(UnsupportedOperationException.class,()->music.overrideReport().clear());
            }
            try(var music=PreparedModMusic.build(catalog.effective(),registry,
                    new PreparedAudioSession(List.of(secondTrack),List.of(),Set.of("first")),8000)) {
                assertEquals(secondTrack.key(),music.overrideReport().getFirst().winner());
                assertTrue(music.overrideReport().getFirst().shadowed().isEmpty());
                assertSame(secondTrack,ModMusicResolver.from(music).resolveStockOverride("s2",12).orElseThrow().track());
            }
        }
        var exclusive=descriptor("first","s2",Map.of(12,"music"),Map.of(),List.of(),
                new ModCompositionMetadata(List.of(),List.of(),List.of(),Set.of("audio:12")));
        var dependent=descriptor("dependent","s2",Map.of(),Map.of(),List.of(new ModDependency("first",VersionRange.parse("*"))),ModCompositionMetadata.EMPTY);
        var otherGame=descriptor("other-game","s1",Map.of(12,"music"),Map.of(),List.of(),ModCompositionMetadata.EMPTY);
        var rejected=new EffectiveCatalogBuilder().build(List.of(exclusive,second,dependent,otherGame),enabled(exclusive,second,dependent,otherGame));
        assertEquals(List.of("other-game"),rejected.effective().orderedEnabled().stream().map(d->d.manifest().id()).toList());
        assertEquals("MOD_COMPOSITION_REJECTED",rejected.eligibility().get("first").reasons().getFirst().code());
        assertEquals("DEPENDENCY_BLOCKED",rejected.eligibility().get("dependent").reasons().getFirst().code());
        var unused=descriptor("unused","s2",Map.of(),Map.of(),List.of(),new ModCompositionMetadata(List.of(),List.of(),List.of(),Set.of("audio:999")));
        var unknown=descriptor("unknown","s2",Map.of(),Map.of("EndSign","art/sheet"),List.of(),new ModCompositionMetadata(List.of(),List.of(),List.of(),Set.of("art:EndSign")));
        assertTrue(new EffectiveCatalogBuilder().build(List.of(unused,unknown),enabled(unused,unknown)).effective().orderedEnabled().isEmpty());
    }
    private static PreparedTrack track(String owner) {
        return new PreparedTrack(new TrackKey(owner,"music"),PcmData.takeOwnership(8000,1,new short[]{1,2}),0,0,1,false,"a".repeat(64));
    }
    private static ModTrackRegistry registry(List<PreparedTrack> tracks) {
        return new ModTrackRegistry(tracks.stream().map(t->new ModAudioTrack(t.key(),"audio/music.wav",false,0,OptionalLong.empty(),1,false)).toList());
    }
    private static ModDescriptor descriptor(String owner,String game,Map<Integer,String> audio,Map<String,String> art,
                                             List<ModDependency> dependencies,ModCompositionMetadata metadata) {
        return new ModDescriptor(Path.of(owner+".jar"),new ModManifest(1,owner,owner,SemanticVersion.parse("1.0.0"),
                List.of("Author"),"Description",VersionRange.parse("*"),ModType.PATCH,game,null,dependencies,audio,art,null,OptionalInt.empty(),metadata),
                "b".repeat(64),false,List.of());
    }
    private static ModState enabled(ModDescriptor... descriptors) {
        var entries=new ArrayList<ModState.Entry>();for(int i=0;i<descriptors.length;i++)entries.add(new ModState.Entry(descriptors[i].manifest().id(),true,i,false,null));
        return new ModState(1,entries);
    }
}

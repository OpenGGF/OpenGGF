package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import static com.openggf.game.soniccd.level.CdR12bDirectArtSources.View.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests authored first; ALL JAVA UNRUN. Raw bytes are genuine original USA views, not decoded/current art. */
class TestCdR12bDirectArtSources {
    private static CdR12bTerrainAssets assets;
    private static CdMmdImage original;
    private static final String MANIFEST="""
LIFE_ICON 362d6 384 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
HUD_NUMBERS 36456 1024 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
HUD_RESET_TILES ae52 12 12 3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c 3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c
HUD_DIVISORS afb2 40 40 7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea 7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea
SHIELD_ART_INDEX 63d4 20 20 e5f83ef559b1e3eaa1e15d9c73a6d771b41634426b2dce4ecabf6a6ebe222834 e5f83ef559b1e3eaa1e15d9c73a6d771b41634426b2dce4ecabf6a6ebe222834
SHIELD_COPY 344e8 1152 1024 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e cbca98fc186fec63ea75c2c0dd8da06ec651c02c37232c15e38cecb749d5b7d0
INV_STARS_COPY 34968 1152 1024 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75 706736889a2cced446522a69a86853de41d4dfc9204c83ab0a7d2690d3036fcc
TIME_STARS_COPY 34de8 992 1024 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa 2094d9156ba35fdb865feb01bee3e64b809ad0297e3d46d7defcf79d94fb5919
GAME_OVER_COPY 33d3c 1024 1024 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd
TIME_OVER_COPY 3397c 960 1024 a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241 8830d700eed4f092a9f4329993caf68940ac65bae7656e54a823031711e371f3
            """;
    @BeforeAll static void actualOriginalSourceClosesBeforeDetachedViewsAreUsed() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Required original USA media");assertFalse(root.isBlank());
        Path cue=Path.of(root).resolve("USA/Sonic CD (US).cue");assertTrue(Files.isRegularFile(cue));
        try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            assets=CdR12bTerrainAssets.fromPrograms(p);original=p.mmd(CdNativeFile.R12B);
        }
    }

    @Test void allTenFiniteRawViewsAndTheirCanonicalSourceMetadataHaveIndependentOriginalHashes() {
        var sources=assets.directArtSources();assertEquals(10,CdR12bDirectArtSources.View.values().length);
        for(var line:MANIFEST.strip().split("\n")) {
            var c=line.split(" ");var id=CdR12bDirectArtSources.View.valueOf(c[0]);var bytes=sources.view(id);
            assertEquals(Integer.parseInt(c[1],16),id.range().fileOffset());assertEquals(Integer.parseInt(c[2]),id.canonicalRange().length());
            assertEquals(Integer.parseInt(c[3]),id.range().length());assertEquals(c[4],id.canonicalSha256());assertEquals(c[5],id.sha256());
            assertEquals(c[4],hash(original.readFile(id.canonicalRange().fileOffset(),id.canonicalRange().length())));
            assertEquals(c[5],hash(bytes.copy()));assertSame(bytes,sources.view(id));
            assertEquals(CdNativeFile.R12B,id.range().file());assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,id.range().domain());
        }
    }

    @Test void authenticIndexAndActualOperandsSelectExactlyTheFiveOriginalRawCopyRoots() {
        var s=assets.directArtSources();long[] expected={0x2344E8,0x234968,0x234DE8,0x233D3C,0x23397C};
        assertEquals(20,s.index().length());for(int i=0;i<5;i++)assertEquals(expected[i],s.index().unsignedLong(i*4));
        assertArrayEquals(java.util.HexFormat.of().parseHex("227b0038"),original.readFile(0x639A,4));
        assertArrayEquals(java.util.HexFormat.of().parseHex("43f9002362d6"),original.readFile(0x1B82,6));
        assertEquals(0x63D4,SHIELD_ART_INDEX.range().fileOffset());
        for(int i=0;i<5;i++){assertEquals(expected[i],s.copySource(i).range().nativeAddress());assertEquals(1024,s.copy(i).length());assertSame(s.copy(i),s.view(s.copySource(i)));}
    }

    @Test void shortCanonicalTimeSourcesUseRealAdjacentOriginalBytesInsteadOfInventedPadding() {
        var s=assets.directArtSources();var timeStars=s.copy(2);var timeOver=s.copy(4);
        assertEquals(992,TIME_STARS_COPY.canonicalRange().length());assertEquals(960,TIME_OVER_COPY.canonicalRange().length());
        assertArrayEquals(original.readFile(0x34DE8,992),timeStars.slice(0,992).copy());
        assertArrayEquals(assets.sectionSources().view(CdR12bSectionSources.SourceId.ART_SPRING_45,CdR12bSectionSources.ReaderKind.CANONICAL).slice(0,32).copy(),timeStars.slice(992,32).copy());
        assertArrayEquals(s.view(GAME_OVER_COPY).slice(0,64).copy(),timeOver.slice(960,64).copy());
        assertEquals("2094d9156ba35fdb865feb01bee3e64b809ad0297e3d46d7defcf79d94fb5919",hash(timeStars.copy()));
        assertEquals("8830d700eed4f092a9f4329993caf68940ac65bae7656e54a823031711e371f3",hash(timeOver.copy()));
    }

    @Test void sharedPowerupCopyRetainsTheEntireActualCallerTailAndDoesNotManufactureAResidentWindow() {
        var s=assets.directArtSources();var runtime=CdPowerupArtRuntime.fromRawSources(s);
        byte[] cpu=new byte[0x480];Arrays.fill(cpu,(byte)0xD7);
        var actualCpu=CdAssetBytes.copyOf(cpu);var request=runtime.prepareActiveCopy(actualCpu,6);
        assertArrayEquals(s.copy(2).copy(),request.bytes().slice(0,1024).copy());
        for(int i=1024;i<0x480;i++)assertEquals(0xD7,request.bytes().unsignedByte(i));
        assertEquals(0xA880,request.vramByteAddress());assertEquals(0x480,request.bytes().length());
        assertArrayEquals(cpu,actualCpu.copy(),"Input remains caller-owned, no hidden tail default");
    }

    @Test void cachesAndHudViewsRemainTheSameDetachedImmutableValuesAfterMediaClose() {
        var s=assets.directArtSources();assertSame(s,CdR12bDirectArtSources.fromAssets(assets));assertSame(s,assets.directArtSources());assertSame(assets.identity(),s.identity());
        var hud=s.hudAssets();assertSame(hud,s.hudAssets());assertSame(s.view(HUD_NUMBERS),hud.numbers());assertSame(s.view(HUD_RESET_TILES),hud.resetTiles());assertSame(s.view(HUD_DIVISORS),hud.divisors());
        byte[] copy=s.view(LIFE_ICON).copy();Arrays.fill(copy,(byte)0);assertEquals(LIFE_ICON.sha256(),hash(s.view(LIFE_ICON).copy()));
        for(var ctor:CdR12bDirectArtSources.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(ctor.getModifiers()));
    }

    @Test void literalSlotNullAndChangedIndexControlsRejectWithoutSourceOrExecutionRepair() {
        var s=assets.directArtSources();assertThrows(NullPointerException.class,()->CdR12bDirectArtSources.fromAssets(null));assertThrows(NullPointerException.class,()->s.view(null));
        for(int i:new int[]{-1,5,128})assertThrows(IndexOutOfBoundsException.class,()->s.copy(i));
        byte[] changed=s.index().copy();changed[3]^=1;assertThrows(IllegalArgumentException.class,()->CdR12bDirectArtSources.requireIndex(CdAssetBytes.copyOf(changed)));
        assertThrows(IllegalArgumentException.class,()->CdR12bDirectArtSources.requireIndex(s.index().slice(0,19)));
    }

    @Test void changedBoundRawViewStopsBeforeCachingAndNeverReadsADifferentFiniteView() {
        var declared=mock(CdR12bTerrainAssets.class);when(declared.identity()).thenReturn(assets.identity());when(declared.profile()).thenReturn(assets.profile());
        byte[] changed=assets.directArtSources().view(LIFE_ICON).copy();changed[0]^=1;
        when(declared.directArtView(LIFE_ICON)).thenReturn(CdAssetBytes.copyOf(changed));var sources=CdR12bDirectArtSources.bind(declared);
        assertThrows(IllegalArgumentException.class,()->sources.view(LIFE_ICON));verify(declared,times(1)).directArtView(LIFE_ICON);
        when(declared.directArtView(LIFE_ICON)).thenReturn(assets.directArtSources().view(LIFE_ICON));assertSame(assets.directArtSources().view(LIFE_ICON),sources.view(LIFE_ICON));
        verify(declared,never()).directArtView(HUD_NUMBERS);
    }
    private static String hash(byte[] bytes){return CdR11aProfile.sha256(bytes);}
}

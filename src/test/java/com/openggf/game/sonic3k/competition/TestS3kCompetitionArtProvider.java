package com.openggf.game.sonic3k.competition;

import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Pattern;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionArtProvider {
    private S3kCompetitionAssets assets;
    private S3kCompetitionArtProvider provider;

    @BeforeEach void createCpuOnlyProvider() throws IOException {
        assets=new S3kCompetitionAssets(GameServices.rom().getRom());
        provider=new S3kCompetitionArtProvider(assets);
    }

    @Test void loadingAllFiveArenasAndQueryingSheetsNeverRequiresGraphics() {
        for (int zone=0xE;zone<=0x12;zone++) {
            provider.loadArtForZone(zone);
            assertSame(assets.sheet("competition_item"),provider.getSheet("competition_item"));
            assertSame(assets.animations("competition_neon"),provider.getAnimations("competition_neon"));
            assertNull(provider.getRenderer("competition_goal"));
            assertFalse(provider.isReady());
        }
        assertThrows(IllegalArgumentException.class,()->provider.loadArtForZone(0));
        assertThrows(IllegalArgumentException.class,()->provider.loadArtForZone(0x13));
    }

    @Test void providerExposesOnlyCompetitionArtAndNativeHudObjects() {
        assertTrue(provider.getRendererKeys().stream().allMatch(key->key.startsWith("competition_")));
        assertNull(provider.getSheet("monitor")); assertNull(provider.getRenderer("checkpoint"));
        assertNull(provider.getAnimations("invincibility"));
        assertNull(provider.getHudDigitPatterns()); assertNull(provider.getHudTextPatterns());
        assertNull(provider.getHudLivesPatterns()); assertNull(provider.getHudLivesNumbers());
        assertNull(provider.getHudHexDigitPatterns()); assertNull(provider.getHudStaticArt());
        assertEquals(-1,provider.getZoneData("animal_type_a",0xE));
        assertSame(assets.sheet("competition_timer_digit"),provider.getArtBundle().sheet("competition_timer_digit"));
        assertSame(assets.animations("competition_sonic"),provider.getArtBundle().animation("competition_sonic"));
        assertEquals(390,provider.getSheet("competition_sonic").getPatterns().length);
    }

    @Test void regularPatternCountCountsSharedBanksByIdentityWithoutUploading() {
        IdentityHashMap<Pattern[],Boolean> banks=new IdentityHashMap<>();
        int total=0,naive=0;
        for (String key:provider.getRendererKeys()) {
            Pattern[] bank=provider.getSheet(key).getPatterns(); naive+=bank.length;
            if (banks.put(bank,Boolean.TRUE)==null) total+=bank.length;
        }
        assertEquals(total,provider.getRegularPatternCount());
        assertTrue(total<naive/2,"sharing must prevent thirty copies of the complete native VRAM banks");
        assertTrue(total<PatternAtlasRange.OBJECTS.size());
        assertFalse(provider.isReady());
    }

    @Test void oneUploadPerUniqueBankBindsEveryRendererToTheSharedVirtualBase() {
        provider.loadArtForZone(0xE);
        RecordingGraphics graphics=new RecordingGraphics();
        int base=PatternAtlasRange.OBJECTS.base();
        int next=provider.ensurePatternsCached(graphics,base);
        assertEquals(base+provider.getRegularPatternCount(),next);
        assertEquals(provider.getRegularPatternCount(),graphics.uploads);
        assertEquals(provider.getRegularPatternCount(),graphics.patterns.size());
        IdentityHashMap<Pattern[],Integer> bankBases=new IdentityHashMap<>();
        int expectedBase=base;
        for (String key:provider.getRendererKeys()) {
            Pattern[] bank=provider.getSheet(key).getPatterns();
            Integer expected=bankBases.get(bank);
            if (expected==null) { expected=expectedBase; bankBases.put(bank,expected); expectedBase+=bank.length; }
            var renderer=provider.getRenderer(key);
            assertNotNull(renderer,key); assertTrue(renderer.isReady(),key);
            assertEquals(expected.intValue(),renderer.getPatternBase(),key);
            for (int i=0;i<bank.length;i++) assertSame(bank[i],graphics.patterns.get(expected+i),key+" tile "+i);
        }
        assertEquals(provider.getRenderer("competition_item").getPatternBase(),
                provider.getRenderer("competition_goal").getPatternBase());
        assertNotEquals(provider.getRenderer("competition_item").getPatternBase(),
                provider.getRenderer("competition_blade").getPatternBase());
        assertTrue(provider.isReady());
    }

    @Test void unchangedUploadIsIdempotentAndLoadInvalidatesResidency() {
        provider.loadArtForZone(0xE);
        RecordingGraphics graphics=new RecordingGraphics(); int base=PatternAtlasRange.OBJECTS.base();
        provider.ensurePatternsCached(graphics,base);
        var renderer=provider.getRenderer("competition_goal");
        provider.ensurePatternsCached(graphics,base);
        assertSame(renderer,provider.getRenderer("competition_goal"));
        assertEquals(provider.getRegularPatternCount(),graphics.uploads);
        provider.loadArtForZone(0xF);
        assertFalse(provider.isReady()); assertNull(provider.getRenderer("competition_goal"));
        provider.ensurePatternsCached(graphics,base);
        assertEquals(provider.getRegularPatternCount()*2,graphics.uploads);
        assertNotSame(renderer,provider.getRenderer("competition_goal"));
    }

    @Test void differentGraphicsOwnerOrBaseRebindsWithOneNewUploadGeneration() {
        provider.loadArtForZone(0x10);
        RecordingGraphics first=new RecordingGraphics(),second=new RecordingGraphics();
        int base=PatternAtlasRange.OBJECTS.base();
        provider.ensurePatternsCached(first,base);
        provider.ensurePatternsCached(second,base+1);
        assertEquals(provider.getRegularPatternCount(),first.uploads);
        assertEquals(provider.getRegularPatternCount(),second.uploads);
        assertTrue(second.patterns.containsKey(base+1)); assertFalse(second.patterns.containsKey(base));
    }

    @Test void rangeOrLoadFailuresOccurBeforeTheFirstUpload() {
        RecordingGraphics graphics=new RecordingGraphics();
        int base=PatternAtlasRange.OBJECTS.base();
        assertThrows(IllegalStateException.class,()->provider.ensurePatternsCached(graphics,base));
        provider.loadArtForZone(0x12);
        assertThrows(IllegalArgumentException.class,()->provider.ensurePatternsCached(graphics,base-1));
        assertThrows(IllegalArgumentException.class,()->provider.ensurePatternsCached(graphics,
                PatternAtlasRange.OBJECTS.endExclusive()-provider.getRegularPatternCount()+1));
        assertEquals(0,graphics.uploads); assertFalse(provider.isReady());
    }

    @Test void failedUploadDoesNotPublishReadyRenderersAndCanRetry() {
        provider.loadArtForZone(0x11);
        RecordingGraphics broken=new RecordingGraphics(); broken.failAt=123;
        assertThrows(IllegalStateException.class,()->provider.ensurePatternsCached(broken,PatternAtlasRange.OBJECTS.base()));
        assertFalse(provider.isReady()); assertNull(provider.getRenderer("competition_item"));
        RecordingGraphics repaired=new RecordingGraphics();
        provider.ensurePatternsCached(repaired,PatternAtlasRange.OBJECTS.base());
        assertTrue(provider.isReady()); assertEquals(provider.getRegularPatternCount(),repaired.uploads);
    }

    /** Headless upload oracle: no real GL context, display or singleton is used. */
    private static final class RecordingGraphics extends GraphicsManager {
        private int uploads;
        private int failAt=-1;
        private final Map<Integer,Pattern> patterns=new HashMap<>();
        @Override public void cachePatternTexture(Pattern pattern,int id) {
            if (uploads==failAt) throw new IllegalStateException("injected texture upload failure");
            assertTrue(PatternAtlasRange.OBJECTS.contains(id));
            patterns.put(id,pattern); uploads++;
        }
    }
}

package com.openggf.game.sonic2.dataselect;

import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.game.dataselect.DataSelectHostProfile;
import com.openggf.game.dataselect.DataSelectDestination;
import com.openggf.game.dataselect.DataSelectPresentationProvider;
import com.openggf.game.dataselect.DataSelectSessionController;
import com.openggf.game.dataselect.DataSelectActionType;
import com.openggf.game.save.SaveSlotSummary;
import com.openggf.game.save.SaveSlotState;
import com.openggf.game.dataselect.HostSlotPreview;
import com.openggf.game.session.EngineContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestS2DataSelectProfile {

    @BeforeAll
    static void configureEngineServices() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void clearSession() {
        SessionManager.clear();
    }

    @Test
    void s2UsesEightSlots() {
        assertEquals(8, new S2DataSelectProfile().slotCount());
    }

    @Test
    void gameCode_returnsS2() {
        assertEquals("s2", new S2DataSelectProfile().gameCode());
    }

    @Test
    void builtInTeams_containsExpectedCharacters() {
        S2DataSelectProfile profile = new S2DataSelectProfile();
        List<SelectedTeam> teams = profile.builtInTeams();
        assertEquals(3, teams.size());
        assertEquals("sonic", teams.get(0).mainCharacter());
        assertTrue(teams.get(0).sidekicks().isEmpty());
        assertEquals("sonic", teams.get(1).mainCharacter());
        assertEquals(List.of("tails"), teams.get(1).sidekicks());
        assertEquals("knuckles", teams.get(2).mainCharacter());
        assertTrue(teams.get(2).sidekicks().isEmpty());
    }

    @Test
    void summarizeFreshSlot_returnsEmpty() {
        S2DataSelectProfile profile = new S2DataSelectProfile();
        var summary = profile.summarizeFreshSlot(3);
        assertEquals(3, summary.slot());
        assertTrue(summary.payload().isEmpty());
    }

    @Test
    void clearRestartDestinations_coverS2MainPath() {
        S2DataSelectProfile profile = new S2DataSelectProfile();
        List<DataSelectDestination> destinations = profile.clearRestartDestinations(Map.of(
                "zone", Sonic2ZoneConstants.ZONE_DEZ,
                "act", 0,
                "mainCharacter", "sonic",
                "sidekicks", List.of("tails"),
                "chaosEmeralds", List.of(0, 1, 2, 3, 4, 5, 6),
                "clear", true
        ));

        assertEquals(new DataSelectDestination(Sonic2ZoneConstants.ZONE_EHZ, 0), destinations.getFirst());
        assertEquals(new DataSelectDestination(Sonic2ZoneConstants.ZONE_DEZ, 0), destinations.getLast());
        assertTrue(destinations.contains(new DataSelectDestination(Sonic2ZoneConstants.ZONE_WFZ, 0)));
    }

    @Test
    void resolveSlotPreview_returnsTextOnlyWithZoneLabel() {
        S2DataSelectProfile profile = new S2DataSelectProfile();

        HostSlotPreview ehzPreview = profile.resolveSlotPreview(Map.of("zone", 0));
        HostSlotPreview cpzPreview = profile.resolveSlotPreview(Map.of("zone", 1));
        HostSlotPreview dezPreview = profile.resolveSlotPreview(Map.of("zone", 10));

        assertNotNull(ehzPreview);
        assertEquals(HostSlotPreview.HostSlotPreviewType.NUMBERED_ZONE, ehzPreview.type());
        assertEquals(1, ehzPreview.zoneDisplayNumber());
        assertEquals(2, cpzPreview.zoneDisplayNumber());
        assertEquals(11, dezPreview.zoneDisplayNumber());
    }

    @Test
    void resolveSlotPreview_returnsNullForEmptyPayload() {
        S2DataSelectProfile profile = new S2DataSelectProfile();
        assertNull(profile.resolveSlotPreview(null));
        assertNull(profile.resolveSlotPreview(Map.of()));
    }

    @Test
    void resolveSelectedSlotIconIndex_usesClearRestartDestinationWhenProvided() {
        S2DataSelectProfile profile = new S2DataSelectProfile();

        int wfzIcon = profile.resolveSelectedSlotIconIndex(Map.of("zone", 0),
                new DataSelectDestination(Sonic2ZoneConstants.ZONE_WFZ, 0));

        assertEquals(Sonic2ZoneConstants.ZONE_WFZ, wfzIcon);
    }

    @Test
    void module_exposesHostProfileSeparatelyFromPresentationProvider() {
        Sonic2GameModule module = new Sonic2GameModule();

        DataSelectHostProfile hostProfile = module.getDataSelectHostProfile();
        DataSelectPresentationProvider provider = module.getDataSelectPresentationProvider();

        assertNotNull(hostProfile);
        assertEquals("s2", hostProfile.gameCode());
        assertInstanceOf(S3kDataSelectManager.class, provider.delegate(),
                "S2 donated Data Select should use the S3K presentation manager");
    }

    @Test
    void taggedModZoneSurvivesOtherOwnerReorderAndUsesGenericPreview() {
        ZoneRegistry before = modRegistry(List.of(
                new ZoneKey.Mod("owner-a", "a"), new ZoneKey.Mod("owner-b", "b")));
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        S2SavedZone.write(payload, before.zoneKey(12));
        payload.put("act", 0);

        ZoneRegistry after = modRegistry(List.of(new ZoneKey.Mod("owner-b", "b")));
        S2DataSelectProfile profile = new S2DataSelectProfile(() -> after);
        assertEquals(new DataSelectDestination(11, 0), profile.resolveLoadDestination(payload));
        assertEquals(HostSlotPreview.textOnly("MOD"), profile.resolveSlotPreview(payload));
        assertEquals(-1, profile.resolveSelectedSlotIconIndex(payload, null));
    }

    @Test
    void missingModAndLegacySyntheticNumericPreservePayloadButFallbackWithoutRetargeting() {
        List<S2SaveFinding> findings = new java.util.ArrayList<>();
        ZoneRegistry current = modRegistry(List.of(new ZoneKey.Mod("other", "zone")));
        S2DataSelectProfile profile = new S2DataSelectProfile(() -> current, findings::add);
        Map<String, Object> missing = new java.util.LinkedHashMap<>();
        S2SavedZone.write(missing, ZoneKey.mod("owner-b", "b"));
        missing.put("act", 0);

        assertEquals(new DataSelectDestination(0, 0), profile.resolveLoadDestination(missing));
        assertEquals(new DataSelectDestination(0, 0),
                profile.resolveLoadDestination(Map.of("zone", 11, "act", 0)));
        assertEquals(List.of("S2_MOD_ZONE_MISSING", "S2_LEGACY_ZONE_OUT_OF_RANGE"),
                findings.stream().map(S2SaveFinding::code).toList());
        assertEquals(ZoneKey.mod("owner-b", "b"), S2SavedZone.read(missing).zoneKey());
    }

    @Test
    void controllerUsesHostSavedZoneResolverBeforeLaunchingSlot() {
        ZoneRegistry current = modRegistry(List.of(new ZoneKey.Mod("owner-b", "b")));
        S2DataSelectProfile profile = new S2DataSelectProfile(() -> current);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        S2SavedZone.write(payload, ZoneKey.mod("owner-b", "b"));
        payload.put("act", 0);
        payload.put("mainCharacter", "sonic");
        payload.put("sidekicks", List.of());
        payload.put("lives", 3);
        payload.put("chaosEmeralds", List.of());
        payload.put("clear", false);
        payload.put("progressCode", 1);
        payload.put("clearState", 0);
        DataSelectSessionController controller = new DataSelectSessionController(profile);
        controller.loadAvailableTeams(null);
        controller.loadSlotSummaries(List.of(new SaveSlotSummary(1, SaveSlotState.VALID, payload)));
        controller.menuModel().setSelectedRow(1);

        var action = controller.confirmSelection();
        assertEquals(DataSelectActionType.LOAD_SLOT, action.type());
        assertEquals(11, action.zone());
    }

    @Test
    void savedZoneCodecRejectsAmbiguousFractionalNonFiniteAndOverflowNumbers() {
        Map<String, Object> tagged = new java.util.LinkedHashMap<>();
        S2SavedZone.write(tagged, ZoneKey.mod("owner", "zone"));
        tagged.put("zone", 0);
        assertThrows(IllegalArgumentException.class, () -> S2SavedZone.read(tagged));
        assertThrows(IllegalArgumentException.class,
                () -> S2SavedZone.read(Map.of("zone", 1.5d)));
        assertThrows(IllegalArgumentException.class,
                () -> S2SavedZone.read(Map.of("zone", Double.NaN)));
        assertThrows(IllegalArgumentException.class,
                () -> S2SavedZone.read(Map.of("zone", Long.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class,
                () -> S2SavedZone.read(Map.of("savedZone", Map.of("stock", 2.25d))));
    }

    @Test
    void writingModIdentityCanonicalizesLegacyMapInPlace() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("zone", 7);
        payload.put("act", 0);
        S2SavedZone.write(payload, ZoneKey.mod("owner", "zone"));
        assertFalse(payload.containsKey("zone"));
        assertEquals(ZoneKey.mod("owner", "zone"), S2SavedZone.read(payload).zoneKey());
    }

    @Test
    void writingStockIdentityCanonicalizesTaggedMapToHistoricalNumericShape() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        S2SavedZone.write(payload, ZoneKey.mod("owner", "zone"));
        S2SavedZone.write(payload, ZoneKey.stock(7));
        assertEquals(7, payload.get("zone"));
        assertFalse(payload.containsKey(S2SavedZone.FIELD));
        S2SavedZone decoded = S2SavedZone.read(payload);
        assertEquals(ZoneKey.stock(7), decoded.zoneKey());
        assertTrue(decoded.legacyNumeric());
    }

    @Test
    void configServiceOwnsDefaultSkipWithoutYamlPresence() {
        var config = com.openggf.configuration.SonicConfigurationService.createStandalone();
        assertEquals(Boolean.TRUE, config.getDefaultValue(
                com.openggf.configuration.SonicConfiguration.SKIP_MOD_ZONE_TITLE_CARDS));
    }

    private static ZoneRegistry modRegistry(List<ZoneKey.Mod> modKeys) {
        ZoneRegistry stock = new com.openggf.game.sonic2.Sonic2ZoneRegistry();
        return new ZoneRegistry() {
            public int getZoneCount() { return 11 + modKeys.size(); }
            public int getActCount(int zone) { return 1; }
            public String getZoneName(int zone) { return zone < 11 ? stock.getZoneName(zone) : "MOD"; }
            public int[] getStartPosition(int zone, int act) { return new int[]{0, 0}; }
            public List<com.openggf.level.LevelDescriptor> getLevelDataForZone(int zone) {
                return zone < 11 ? stock.getLevelDataForZone(zone) : List.of();
            }
            public List<List<com.openggf.level.LevelDescriptor>> getAllZones() { return stock.getAllZones(); }
            public int getMusicId(int zone, int act) { return 0; }
            public ZoneKey zoneKey(int zone) { return zone < 11 ? ZoneKey.stock(zone) : modKeys.get(zone - 11); }
            public java.util.OptionalInt resolveZoneKey(ZoneKey key) {
                if (key instanceof ZoneKey.Stock s && s.zoneIndex() < 11) return java.util.OptionalInt.of(s.zoneIndex());
                int index = modKeys.indexOf(key);
                return index < 0 ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(11 + index);
            }
        };
    }
}

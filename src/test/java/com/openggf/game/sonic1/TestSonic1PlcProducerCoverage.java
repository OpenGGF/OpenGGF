package com.openggf.game.sonic1;

import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.credits.Sonic1CreditsManager;
import com.openggf.game.sonic1.resources.Sonic1PlcService;
import com.openggf.game.sonic1.titlescreen.Sonic1TitleScreenManager;
import com.openggf.game.sonic1.titlecard.Sonic1TitleCardManager;
import com.openggf.game.sonic1.titlecard.Sonic1TitleCardState;
import com.openggf.game.sonic1.objects.Sonic1EggPrisonObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1SignpostObjectInstance;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageProvider;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.level.resources.NemesisPlcPatternCounts;
import com.openggf.level.resources.PlcParser;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Executes the S1-owned credits producer and records the complete descriptor
 * sequence it leaves in the real native FIFO.  This deliberately compares ROM
 * descriptors rather than {@code isBusy()}: identical queue length is not
 * sufficient evidence that a producer selected the right PLC.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestSonic1PlcProducerCoverage {
    private static final int[] CREDITS_NATIVE_ZONES = {0, 2, 4, 1, 3, 5, 5, 0, 1};

    @BeforeEach
    void createSonic1Services() {
        GameServices.module().createGame(TestEnvironment.currentRom());
    }

    @Test
    void titleScreenOwnerPublishesMainBeforePresentationBegins() throws Exception {
        Sonic1TitleScreenManager.getInstance().initialize();
        Sonic1PlcService queue = GameServices.module().getGameService(Sonic1PlcService.class);

        assertEquals(expectedDescriptors(0), queue.capture().queuedEntries(),
                "S1 title initialization must replace the native Main PLC before presentation");
    }

    @ParameterizedTest(name = "title-card progression zone {0} maps to native animal PLC {1}")
    @MethodSource("titleCardZones")
    void titleCardOwnerPublishesExplodeThenNativeZoneAnimalAtExitEdge(int progressionZone, int animalPlc)
            throws Exception {
        Sonic1TitleCardManager card = new Sonic1TitleCardManager();
        card.initialize(progressionZone, 0);
        Field state = Sonic1TitleCardManager.class.getDeclaredField("state");
        state.setAccessible(true);
        state.set(card, Sonic1TitleCardState.SLIDE_OUT);
        Field timer = Sonic1TitleCardManager.class.getDeclaredField("stateTimer");
        timer.setAccessible(true);
        timer.setInt(card, 21);

        for (int frame = 0; frame < 20; frame++) {
            card.update();
        }

        Sonic1PlcService queue = GameServices.module().getGameService(Sonic1PlcService.class);
        assertEquals(expectedDescriptors(2, animalPlc), queue.capture().queuedEntries(),
                "Card_ChangeArt must publish explode then its native-zone animal PLC at text exit");
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> titleCardZones() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(0, 21),
                org.junit.jupiter.params.provider.Arguments.of(1, 23),
                org.junit.jupiter.params.provider.Arguments.of(2, 25),
                org.junit.jupiter.params.provider.Arguments.of(3, 22),
                org.junit.jupiter.params.provider.Arguments.of(4, 24),
                org.junit.jupiter.params.provider.Arguments.of(5, 26));
    }

    @Test
    void levelInitOwnerClearsThenPublishesHeaderPrimaryAndMain2BeforeTitleCard() throws Exception {
        LevelLoadContext context = new LevelLoadContext();
        com.openggf.level.Level level = org.mockito.Mockito.mock(com.openggf.level.Level.class);
        org.mockito.Mockito.when(level.getZoneIndex()).thenReturn(0);
        context.setLevel(level);
        Sonic1LevelInitProfile profile = new Sonic1LevelInitProfile(
                new com.openggf.game.sonic1.events.Sonic1LevelEventManager(),
                new Sonic1SwitchManager(), new Sonic1ConveyorState());
        profile.levelLoadSteps(context).stream()
                .filter(step -> step.name().equals("QueueInitialPlcs"))
                .findFirst().orElseThrow().execute();

        int primary = primaryForNativeZone(0);
        Sonic1PlcService queue = GameServices.module().getGameService(Sonic1PlcService.class);
        assertEquals(expectedDescriptors(primary, 1), queue.capture().queuedEntries(),
                "S1 Level must commit ClearPLC/header-primary/Main2 through the lifecycle owner");
    }

    @Test
    void bothNormalEndActOwnersReplaceResultsPlcAtTheirActualHandoff() throws Exception {
        TestObjectServices services = new TestObjectServices().withGameModule(GameServices.module());
        AbstractPlayableSprite player = org.mockito.Mockito.mock(AbstractPlayableSprite.class);
        for (Object owner : List.of(new Sonic1SignpostObjectInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)),
                new Sonic1EggPrisonObjectInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)))) {
            ObjectConstructionContext.with(services, () -> {
                ((com.openggf.level.objects.AbstractObjectInstance) owner).setServices(services);
                try {
                    java.lang.reflect.Method handoff = owner.getClass().getDeclaredMethod(
                            "triggerGotThroughAct", AbstractPlayableSprite.class);
                    handoff.setAccessible(true);
                    handoff.invoke(owner, player);
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError("normal end-act owner handoff unavailable", failure);
                }
            });
            Sonic1PlcService queue = GameServices.module().getGameService(Sonic1PlcService.class);
            assertEquals(expectedDescriptors(16), queue.capture().queuedEntries(),
                    owner.getClass().getSimpleName() + " must replace results art at GotThroughAct");
        }
    }

    @Test
    void specialStageResultsOwnerReplacesMainThenAppendsResultsArt() throws Exception {
        new Sonic1SpecialStageProvider().resetForResults();
        Sonic1PlcService queue = GameServices.module().getGameService(Sonic1PlcService.class);
        assertEquals(expectedDescriptors(0, 27), queue.capture().queuedEntries(),
                "S1 special-stage results boundary must replace Main then append result art");
    }

    @Test
    void creditsOwnerPrequeuesEveryTextPageInNativeRomZoneOrder() throws Exception {
        Sonic1CreditsManager credits = new Sonic1CreditsManager();
        Sonic1PlcService queue = GameServices.module().getGameService(Sonic1PlcService.class);

        for (int credit = 0; credit < CREDITS_NATIVE_ZONES.length; credit++) {
            setCreditsNumber(credits, credit);
            credits.onReturnToText();

            int primary = primaryForNativeZone(CREDITS_NATIVE_ZONES[credit]);
            assertEquals(expectedDescriptors(primary, 1), queue.capture().queuedEntries(),
                    "credits page " + credit + " must publish ClearPLC, primary AddPLC, Main2 AddPLC");
        }
    }

    @Test
    void everyAuditedS1CueParsesAndPreservesItsExactAppendOrReplaceDescriptorOrder()
            throws Exception {
        Sonic1PlcService queue = GameServices.module().getGameService(Sonic1PlcService.class);
        // This covers the concrete producer IDs in the audit.  Owner-to-facade
        // wiring is guarded by TestPlcProducerCoverageGuard; this assertion
        // catches an ID/table regression even where a visual owner cannot be
        // booted headlessly (title card and boss presentation paths).
        int[] appendIds = {1, 2, 17, 21, 22, 23, 24, 25, 26, 27, 30, 31};
        for (int plcId : appendIds) {
            queue.transact(Sonic1PlcService.clear(), Sonic1PlcService.appendOperation(plcId));
            assertEquals(expectedDescriptors(plcId), queue.capture().queuedEntries(),
                    "S1 audited append PLC " + plcId);
        }
        for (int plcId : new int[] {0, 16}) {
            queue.transact(Sonic1PlcService.replace(plcId));
            assertEquals(expectedDescriptors(plcId), queue.capture().queuedEntries(),
                    "S1 audited replace PLC " + plcId);
        }
    }

    private static void setCreditsNumber(Sonic1CreditsManager credits, int value) throws Exception {
        Field field = Sonic1CreditsManager.class.getDeclaredField("creditsNum");
        field.setAccessible(true);
        field.setInt(credits, value);
    }

    private static int primaryForNativeZone(int zone) throws IOException {
        int header = Sonic1Constants.LEVEL_HEADERS_ADDR + zone * 16;
        return GameServices.rom().getRom().readByte(header) & 0xFF;
    }

    private static List<NemesisPlcQueueSnapshot.Entry> expectedDescriptors(int... ids)
            throws IOException {
        List<NemesisPlcQueueSnapshot.Entry> result = new ArrayList<>();
        for (int id : ids) {
            PlcParser.PlcDefinition definition = PlcParser.parse(GameServices.rom().getRom(),
                    Sonic1Constants.ART_LOAD_CUES_ADDR, id);
            List<Integer> counts = NemesisPlcPatternCounts.derive(GameServices.rom().getRom(), definition);
            for (int index = 0; index < definition.entries().size(); index++) {
                PlcParser.PlcEntry entry = definition.entries().get(index);
                int count = counts.get(index);
                result.add(new NemesisPlcQueueSnapshot.Entry(entry.romAddr(), entry.tileIndex(), count, count));
            }
        }
        return result;
    }
}

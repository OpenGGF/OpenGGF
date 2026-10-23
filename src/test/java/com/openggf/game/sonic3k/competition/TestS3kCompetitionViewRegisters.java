package com.openggf.game.sonic3k.competition;

import com.openggf.game.resources.PlcLifecyclePhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kCompetitionViewRegisters {
    @ParameterizedTest @EnumSource(value = PlcLifecyclePhase.class,
            names = {"ORDINARY_LEVEL", "LEVEL_TITLE_CARD", "NORMAL_PAUSE"})
    void levelHandlersPublishBothVscrollCopiesAndTheFull108By116ScrollImage(PlcLifecyclePhase phase) {
        var registers = new S3kCompetitionViewRegisters();
        var words = words(1000);
        registers.publish(phase, 0xFFFF, 20, 100, 200, words);
        var state = registers.capture();
        assertTrue(state.published()); assertEquals(-1, state.foreground1());
        assertEquals(20, state.background1()); assertEquals(100, state.foreground2());
        assertEquals(200, state.background2()); assertEquals(words, state.horizontalScroll());
        assertEquals(1107, state.horizontalScroll().get(107));
        assertEquals(1108, state.horizontalScroll().get(108));
        assertEquals(1223, state.horizontalScroll().get(223));
    }

    @Test void lagUpdatesOnlyP1WhileFadeUploadsHscrollWithoutRefreshingP2() {
        var registers = new S3kCompetitionViewRegisters();
        registers.publish(PlcLifecyclePhase.ORDINARY_LEVEL, 1, 2, 3, 4, words(10));
        registers.publish(PlcLifecyclePhase.LAG, 5, 6, 7, 8, words(20));
        assertEquals(new S3kCompetitionViewRegisters.State(true, 5, 6, 3, 4, words(10)), registers.capture());
        registers.publish(PlcLifecyclePhase.PALETTE_FADE, 9, 10, 11, 12, words(30));
        assertEquals(new S3kCompetitionViewRegisters.State(true, 9, 10, 3, 4, words(30)), registers.capture());
    }

    @Test void nativeBackgroundInitSeedsP2AndSnapshotRetainsThePublishedImageAcrossReplay() {
        var registers = new S3kCompetitionViewRegisters();
        registers.initialize(-112, -80);
        assertFalse(registers.capture().published());
        registers.publish(PlcLifecyclePhase.LAG, 1, 2, 3, 4, words(10));
        assertEquals(-112, registers.capture().foreground2());
        assertEquals(-80, registers.capture().background2());
        assertEquals(Collections.nCopies(224, 0), registers.capture().horizontalScroll());
        var mutable = new ArrayList<>(words(20));
        registers.publish(PlcLifecyclePhase.ORDINARY_LEVEL, 20, 30, 40, 50, mutable);
        var snapshot = registers.capture(); mutable.set(0, -999);
        assertEquals(20, snapshot.horizontalScroll().getFirst());
        registers.publish(PlcLifecyclePhase.PALETTE_FADE, 60, 70, 80, 90, words(30));
        var expected = registers.capture();
        registers.resetForMissingSnapshot(); registers.restore(snapshot);
        registers.publish(PlcLifecyclePhase.PALETTE_FADE, 60, 70, 80, 90, words(30));
        assertEquals(expected, registers.capture());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.horizontalScroll().set(0, 1));
    }

    @Test void unrelatedHandlersAndMalformedPublishedImagesCannotBecomeNativeRaceRegisters() {
        var registers = new S3kCompetitionViewRegisters();
        assertThrows(IllegalArgumentException.class,
                () -> registers.publish(PlcLifecyclePhase.SPECIAL_STAGE, 0, 0, 0, 0, words(0)));
        assertThrows(IllegalArgumentException.class,
                () -> registers.publish(PlcLifecyclePhase.ORDINARY_LEVEL, 0, 0, 0, 0, List.of(0)));
        assertFalse(registers.capture().published());
    }

    private static List<Integer> words(int start) {
        return java.util.stream.IntStream.range(0, 224).map(i -> start + i).boxed().toList();
    }
}

package com.openggf.game.titlecard;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic1.titlecard.Sonic1TitleCardManager;
import com.openggf.game.sonic1.titlecard.Sonic1TitleCardMappings;
import com.openggf.game.sonic2.titlecard.TitleCardManager;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardMappings;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated
class TestTitleCardManagerRenderOrder {
    private final TestTitleCardSpriteRenderer.RecordingGraphics graphics =
            new TestTitleCardSpriteRenderer.RecordingGraphics();
    private EngineContext previous;

    @BeforeEach
    void bindGraphicsWithoutBootingGameplayOrAudio() {
        previous = EngineServices.current();
        var context = mock(EngineContext.class);
        when(context.graphics()).thenReturn(graphics);
        when(context.configuration()).thenReturn(previous.configuration());
        graphics.initHeadless();
        graphics.setProjectionWidth(320);
        EngineServices.configure(context);
    }

    @AfterEach
    void restoreServices() {
        EngineServices.configure(previous);
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 424})
    void sonic1DrawsElementsAndMappingPiecesInReverseOrder(int width) throws Exception {
        graphics.setProjectionWidth(width);
        int offset = (width - 320) / 2;
        var manager = new Sonic1TitleCardManager();
        stageArt(manager, "patterns");
        set(manager, "elements", List.of(
                element(Sonic1TitleCardMappings.FRAME_ACT_1, 100, 60),
                element(Sonic1TitleCardMappings.FRAME_ACT_2, 200, 80)));
        manager.draw();
        assertPatternOrder(PatternAtlasRange.MENU_AND_DATA_SELECT.base(),
                0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F, 0x53, 0x54, 0x55, 0x56,
                0x57, 0x58, 0x59, 0x53, 0x54, 0x55, 0x56);
        assertEquals(208 + offset, graphics.submissions.getFirst().x());
        assertEquals(68, graphics.submissions.getFirst().y());
        assertEquals(112 + offset, graphics.submissions.get(10).x());
        assertEquals(48, graphics.submissions.get(10).y());
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 424})
    void sonic2DrawsSwooshFirstThenOtherElementsAndMappingPiecesInForwardOrder(int width) throws Exception {
        graphics.setProjectionWidth(width);
        int offset = (width - 320) / 2;
        var manager = new TitleCardManager();
        stageArt(manager, "combinedPatterns");
        TitleCardElement swoosh = element(TitleCardMappings.FRAME_ACT_2, 80, 100);
        set(manager, "elements", List.of(
                element(TitleCardMappings.FRAME_ZONE, 160, 70),
                element(TitleCardMappings.FRAME_ACT_1, 240, 90), swoosh));
        set(manager, "leftSwooshElement", swoosh);
        manager.draw();
        assertPatternOrder(PatternAtlasRange.TITLE_CARDS.base(),
                0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23,
                0x0C, 0x0D, 0x0E, 0x0F, 8, 9, 10, 11, 4, 5, 6, 7, 0, 1, 2, 3,
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17);
        assertEquals(80 + offset, graphics.submissions.getFirst().x());
        assertEquals(100, graphics.submissions.getFirst().y());
        assertEquals(161 + offset, graphics.submissions.get(12).x());
        assertEquals(70, graphics.submissions.get(12).y());
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 424})
    void sonic3kDrawsPublishedElementOrderWithReverseMappingPiecesAndHiddenActOmitted(int width) throws Exception {
        graphics.setProjectionWidth(width);
        int offset = (width - 320) / 2;
        var manager = new Sonic3kTitleCardManager();
        stageArt(manager, "combinedPatterns");
        set(manager, "elemX", new int[]{100, 200, 240, 280});
        set(manager, "elemY", new int[]{60, 80, 90, 100});
        set(manager, "elemFrame", new int[]{Sonic3kTitleCardMappings.FRAME_ACT,
                Sonic3kTitleCardMappings.FRAME_ZONE, Sonic3kTitleCardMappings.FRAME_BLANK,
                Sonic3kTitleCardMappings.FRAME_ACT});
        set(manager, "actNumberVisible", false);
        manager.prepareTitleCardPresentation();
        manager.publishTitleCardPresentation();
        set(manager, "elemX", new int[4]);
        manager.draw();
        assertPatternOrder(PatternAtlasRange.MENU_AND_DATA_SELECT.base(),
                0x3D, 0x3E, 0x3F, 0x40, 0x41, 0x42, 0x43, 0x44,
                0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C,
                0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C,
                0x1C, 0x1D, 0x1E, 0x1F, 0x20, 0x21,
                0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
                0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F, 0x30,
                0x31, 0x32, 0x33, 0x34, 0x35, 0x36);
        assertEquals(89 + offset, graphics.submissions.getFirst().x());
        assertEquals(60, graphics.submissions.getFirst().y());
        assertEquals(220 + offset, graphics.submissions.get(22).x());
        assertEquals(80, graphics.submissions.get(22).y());
    }

    private static TitleCardElement element(int frame, int x, int y) {
        var element = new TitleCardElement(frame, x, x, y, 0, 64);
        element.updateSlideIn();
        return element;
    }

    private static void stageArt(Object manager, String patterns) throws Exception {
        set(manager, patterns, new Pattern[256]);
        set(manager, "artLoaded", true);
        set(manager, "artCached", true);
    }

    private void assertPatternOrder(int base, int... offsets) {
        assertEquals(Arrays.stream(offsets).map(offset -> base + offset).boxed().toList(),
                graphics.submissions.stream().map(TestTitleCardSpriteRenderer.Submission::patternId).toList());
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

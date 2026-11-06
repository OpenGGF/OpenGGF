package com.openggf.editor.stageplay;

import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.editor.project.EditorProjectMenu.PresentationKind;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayGround;
import com.openggf.i18n.UiMessages;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Presentation intent remains usable with arbitrary translated titles and duplicate labels. */
class TestStageplayMenuPresentation {
    private StageplayWorkspace workspace() {
        var workspace = new StageplayWorkspace(new StageplayWorkspace.Host() {
            @Override public StageplayDocument initialDocument() {
                return StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withActors(List.of(
                        new StageplayDocument.Actor("hero", "Sonic", "s2", "sonic", List.of(
                                new StageplayDocument.ActorKey(0, 160, 112, 0, -1, StageplayDocument.Facing.RIGHT,
                                        true, 1, StageplayDocument.Interpolation.HOLD)))))
                        .withPuppets(List.of(new StageplayDocument.PuppetTrack("hero", true, 0, List.of())));
            }
            @Override public StageplayGround.Report ground(StageplayDocument document, Path root, int frame,
                                                           StageplayGround.Batch batch) {
                var query = batch.queries().getFirst();
                return new StageplayGround.Report(new StageplayGround.SceneFrame(document.set(), frame), List.of(
                        new StageplayGround.Answer(query.id(), StageplayGround.Outcome.FOUND, List.of(
                                new StageplayGround.Placement(query.x(), 200, 219, 0,
                                        new StageplayGround.PlayerTerrainProposal(StageplayGround.Foot.LEFT, 0, 9, 19))),
                                List.of(), false)), new StageplayGround.Work(10, 0, 1, StageplayGround.StopReason.NONE));
            }
        });
        workspace.activate();
        workspace.selectActor("hero");
        return workspace;
    }

    @Test
    void frenchAndArabicTitlesRetainPositionPickerIntentWithoutChangingTheDocument() {
        for (String title : List.of("Position du personnage", "موضع الشخصية")) {
            var workspace = workspace();
            var before = workspace.controller().document();
            var menu = workspace.menu();
            menu.showForm(PresentationKind.ACTOR_POSITION, title, List.of(
                    new EditorProjectMenu.Field("x", "المحور س", "160", List.of()),
                    new EditorProjectMenu.Field("y", "المحور ص", "112", List.of()),
                    new EditorProjectMenu.Field("facing", "Direction", "Right", List.of("Left", "Right"))),
                    "Appliquer", values -> fail("Opening a picker must not submit the form"));
            assertTrue(menu.canPick("x"), title);
            menu.pick("x");
            assertTrue(workspace.pickingPosition(), title);
            assertTrue(workspace.positionPicker().snap(), title);
            assertNotNull(workspace.positionPicker().floor(), "Physical placement retains the typed floor query: " + title);
            assertEquals(200, workspace.positionPicker().pickedY());
            workspace.cancelPositionPick();
            assertEquals(before, workspace.controller().document(), title);
            assertEquals("160", menu.values().get("x"));
            assertEquals("112", menu.values().get("y"));
        }
    }

    @Test
    void arbitraryDuplicateLabelsRetainPrimaryActionAndSeparateCallbacks() {
        var workspace = workspace();
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        var menu = workspace.menu();
        menu.showActions(PresentationKind.FLOOR_REFUSAL, "عنوان مختلف تماما", List.of(
                StageplayMenuPresentation.action("Action originale", "إجراء", true, first::incrementAndGet),
                StageplayMenuPresentation.action("Autre action", "إجراء", false, second::incrementAndGet)));
        var rows = menu.actionView().rows();
        assertEquals(UiMessages.text("editor.stageplay.presentation.title_floor_refusal"), menu.title());
        assertTrue(menu.helpText().contains("عنوان مختلف تماما"), "Details retains the original title");
        assertEquals(List.of("إجراء", "إجراء"), rows.stream().map(EditorProjectMenu.ActionRow::label).toList());
        assertTrue(rows.getFirst().primary());
        assertFalse(rows.getLast().primary());
        menu.runAction(rows.getFirst().index());
        assertEquals(1, first.get());
        assertEquals(0, second.get());
        menu.showActions(PresentationKind.FLOOR_REFUSAL, "Autre titre", List.of(
                StageplayMenuPresentation.action("Action originale", "إجراء", true, first::incrementAndGet),
                StageplayMenuPresentation.action("Autre action", "إجراء", false, second::incrementAndGet)));
        menu.runAction(1);
        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void explicitSaveIntentDoesNotDependOnTheTranslatedTitleAndLegacyShowsResetIt() {
        var workspace = workspace();
        var menu = workspace.menu();
        var fields = List.of(new EditorProjectMenu.Field("path", "Fichier", "scene.stageplay", List.of()));
        menu.showForm(PresentationKind.SAVE_SCENE, "حفظ الملف", fields, "حفظ", values -> { });
        assertEquals(UiMessages.text("editor.stageplay.presentation.title_save_scene"), menu.title());
        menu.showForm("ملف آخر", fields, "Continuer", values -> { });
        assertEquals(PresentationKind.DEFAULT, menu.presentationKind());
        assertEquals("ملف آخر", menu.title(), "Legacy callers cannot inherit the preceding form's intent");
    }
}

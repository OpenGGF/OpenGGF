package com.openggf.editor.project;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.MenuTextEditor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestEditorProjectMenu {
    @Test
    void groupedRowsUseOnePitchAndKeepThePrimaryActionVisible() {
        var menu=new EditorProjectMenu();
        menu.showForm("Plan route",List.of(
                new EditorProjectMenu.Field("who","Who","Sonic",MenuTextEditor.Mode.TEXT),
                new EditorProjectMenu.Field("position","Position","160, 612",MenuTextEditor.Mode.TEXT),
                new EditorProjectMenu.Field("start","Start","Walk",List.of("Walk","Jump")),
                new EditorProjectMenu.Field("speed","Speed","Run",List.of("Run","Walk")),
                new EditorProjectMenu.Field("stop","Stop","Gentle",List.of("Gentle","Hard")),
                new EditorProjectMenu.Field("face","Face","Keep",List.of("Keep","Left","Right")),
                new EditorProjectMenu.Field("frames","Frames","120",MenuTextEditor.Mode.TEXT)),"Find route",v->{});
        menu.setHelp("Choose a destination.","");
        menu.setGroup("Destination","position");menu.setGroup("Motion","start","speed","stop","face");
        menu.setSection("Advanced","frames");
        var font=new RecordingFont();menu.render(font,320);
        assertTrue(font.y.get("Who")-font.y.get("Choose a destination.")>=12,
                "Leave space below the subtitle at the standard HUD scale");
        for(var pair:List.of(List.of("Start","Speed"),List.of("Speed","Stop"),List.of("Stop","Face")))
            assertEquals(12,font.y.get(pair.get(1))-font.y.get(pair.get(0)),"Use a 36-pixel pitch at the standard 3x HUD scale");
        assertTrue(font.text.contains("+ Advanced"));
        assertTrue(font.text.contains("Find route"),"The primary action must be visible before navigation");
        var initialRows=java.util.Map.copyOf(font.y);
        var input=new InputHandler();for(int i=0;i<7;i++)press(menu,input,GLFW_KEY_DOWN);
        font.text.clear();font.y.clear();menu.render(font,320);
        assertTrue(font.text.contains("Find route"));
        assertEquals(initialRows.get("Who"),font.y.get("Who"),"Selecting Apply must not scroll the default form");
        assertTrue(font.text.containsAll(List.of("MOVE", "ENTER", "APPLY", "ESC", "CLOSE", "F1", "HELP")),
                "Keep all four primary key-cap hints when scrolling to Apply");
        assertTrue(font.y.get("Find route")-font.y.get("+ Advanced")>=19,
                "Keep at least twenty displayed pixels clear above the primary button");
    }

    @Test
    void everyCollapsedStageplayFormShowsItsFieldsAndPrimaryAction() throws Exception {
        var checked=new AtomicInteger();
        StageplayMenuSurvey.capture((name,workspace)->{
            var menu=workspace.menu();var view=menu.formView();if(view==null)return;
            var font=new RecordingFont();menu.render(font,320);
            for(var group:view.groups()) {
                if(group.advanced())assertTrue(font.text.contains("+ "+group.title()),name+" hides Advanced");
                else for(var field:group.fields())
                    assertTrue(font.text.contains(field.field().label()),name+" hides "+field.field().label());
            }
            assertTrue(font.text.contains(view.apply()),name+" hides "+view.apply());
            assertTrue(font.y.get(view.apply())+10<=205,name+" clips its primary button");
            checked.incrementAndGet();
        });
        assertTrue(checked.get()>=30,"Cover every form family, not just Plan route");
    }

    @Test
    void pairedPositionHasOneRowAndKeepsBothEditableValuesAndFractions() {
        var submitted=new AtomicReference<java.util.Map<String,String>>();var menu=new EditorProjectMenu();
        menu.showForm("Position",List.of(new EditorProjectMenu.Field("x","X","160.0",MenuTextEditor.Mode.TEXT),
                new EditorProjectMenu.Field("y","Y","612.25",MenuTextEditor.Mode.TEXT)),"Apply",submitted::set);
        menu.setPair("x","y","Position");menu.setGroup("Destination","x","y");
        var fields=menu.formView().groups().getFirst().fields();
        assertEquals(1,fields.size());assertEquals("160, 612.25",fields.getFirst().display());
        assertEquals("y",fields.getFirst().partner().key());
        var input=new InputHandler();press(menu,input,GLFW_KEY_ENTER);assertTrue(menu.capturesText());
        // Clear the compound field through the real editor, then type both coordinates.
        for(int i=0;i<11;i++)press(menu,input,GLFW_KEY_BACKSPACE);
        for(char c:"240, 600.5".toCharArray())MenuInput.handleCharEvent(input,c);
        input.refreshLogicalSnapshot();menu.update(input);input.update();press(menu,input,GLFW_KEY_ENTER);
        assertFalse(menu.capturesText());assertNull(menu.error());menu.submit();
        assertEquals(java.util.Map.of("x","240","y","600.5"),submitted.get());
    }

    @Test
    void dialoguePreviewUsesTwoRealLinesAndOnlyTheLastLineEllipsizes() {
        var menu=new EditorProjectMenu();
        String raw="Ready?\\nLet's go!";
        menu.showForm("Dialogue line",List.of(new EditorProjectMenu.Field("text","Text",raw,MenuTextEditor.Mode.TEXT)),"Apply",v->{});
        menu.setMultilinePreview("text");menu.setGroup("Line","text");
        var font=new RecordingFont();menu.render(font,320);
        assertTrue(font.text.contains("Ready?"));assertTrue(font.text.contains("Let's go!"));assertFalse(font.text.contains(raw));
        assertEquals(raw,menu.values().get("text"),"Preview never rewrites the stored escaped value");
        menu.setValue("text","First line\\nSecond line\\nA third line");font.text.clear();menu.render(font,320);
        assertTrue(font.text.contains("First line"));assertTrue(font.text.contains("Second line..."));
        assertFalse(font.text.contains("A third line"));
    }

    @Test
    void optionalSectionsKeepTheirValuesAcrossCollapseErrorsAndSubmission() {
        var accepted=new AtomicReference<java.util.Map<String,String>>();
        var menu=new EditorProjectMenu();
        menu.showForm("Route",List.of(
                new EditorProjectMenu.Field("x","Destination X","120",MenuTextEditor.Mode.TEXT),
                new EditorProjectMenu.Field("nodes","Search nodes","1024",MenuTextEditor.Mode.TEXT)),"Plan",values->{
            if(values.get("nodes").equals("1024"))throw new IllegalArgumentException("Choose a larger limit");
            accepted.set(values);
        });
        menu.setSection("Advanced","nodes");
        var input=new InputHandler();press(menu,input,GLFW_KEY_DOWN);press(menu,input,GLFW_KEY_ENTER);
        // Opening Advanced owns no scene edit; its first field can still use the ordinary text editor.
        press(menu,input,GLFW_KEY_DOWN);press(menu,input,GLFW_KEY_ENTER);assertTrue(menu.capturesText());
        press(menu,input,GLFW_KEY_ESCAPE);press(menu,input,GLFW_KEY_UP);press(menu,input,GLFW_KEY_ENTER);
        press(menu,input,GLFW_KEY_DOWN);press(menu,input,GLFW_KEY_ENTER);
        assertTrue(menu.isOpen());assertEquals("Choose a larger limit",menu.error());
        menu.setValue("nodes","2048");menu.submit();
        assertEquals(java.util.Map.of("x","120","nodes","2048"),accepted.get());
    }

    @Test
    void fullHelpKeepsMultilineHintAndSelectedValuesWithoutPuttingThemOnTheForm() {
        var menu=new EditorProjectMenu();
        menu.showForm("Line",List.of(new EditorProjectMenu.Field("line","Text","A long editable line",MenuTextEditor.Mode.TEXT)),"Apply",v->{});
        menu.setHelp("Line one.\nLine two.","Detailed explanation.");
        var font=new RecordingFont();menu.render(font,320);
        assertTrue(font.text.contains("Line one."));assertFalse(font.text.contains("Line two."));
        assertFalse(font.text.contains("Detailed explanation."));
        press(menu,new InputHandler(),GLFW_KEY_F1);font.text.clear();menu.render(font,320);
        assertFalse(font.text.contains("Detailed explanation."));
        assertTrue(font.text.contains("Line two."));
        assertTrue(font.text.stream().anyMatch(t->t.contains("A long editable line")));
        press(menu,new InputHandler(),GLFW_KEY_UP);press(menu,new InputHandler(),GLFW_KEY_ENTER);
        font.text.clear();menu.render(font,320);
        assertTrue(font.text.contains("Detailed explanation."));
    }

    @Test void helpForLongActionListsKeepsTheBackButtonAndFullInventoryReachable() {
        var menu=new EditorProjectMenu();
        menu.showActions("Cast",java.util.stream.IntStream.range(0,40)
                .mapToObj(i->new EditorProjectMenu.Action("Actor "+i,()->{})).toList());
        menu.openDetails();var font=new RecordingFont();menu.render(font,320);
        assertTrue(font.text.contains("Back"));assertTrue(font.text.contains("+ Show technical details"));
        assertTrue(font.y.get("Back")>=180);assertTrue(menu.detailsText().contains("Actor 39"));
    }

    private static final class RecordingFont extends com.openggf.graphics.PixelFont {
        final java.util.List<String> text=new java.util.ArrayList<>();
        final java.util.Map<String,Integer> y=new java.util.HashMap<>();
        @Override public void drawText(String value,int x,int y,float scale,float r,float g,float b,float a) {text.add(value);this.y.put(value,y);}
    }

    @Test
    void navigationRunsOnlyTheSelectedActionAndOwnsTheInput() {
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        var menu = new EditorProjectMenu();
        menu.showActions("Projects", List.of(
                new EditorProjectMenu.Action("New", first::incrementAndGet),
                new EditorProjectMenu.Action("Open", second::incrementAndGet)));
        var input = new InputHandler();
        press(menu, input, GLFW_KEY_DOWN);
        press(menu, input, GLFW_KEY_ENTER);
        assertEquals(0, first.get());
        assertEquals(1, second.get());
        assertFalse(menu.isOpen());
    }

    @Test
    void rejectedFormPreservesValuesAndCanBeCorrectedWithoutClosing() {
        var accepted = new AtomicReference<String>();
        var menu = new EditorProjectMenu();
        menu.showForm("Save project", List.of(new EditorProjectMenu.Field("path", "Path", "",
                MenuTextEditor.Mode.PATH)), "Save", values -> {
            if (values.get("path").isBlank()) throw new IllegalArgumentException("Choose a project path");
            accepted.set(values.get("path"));
        });
        menu.submit();
        assertTrue(menu.isOpen());
        assertEquals("Choose a project path", menu.error());
        menu.setValue("path", "projects/my-level.ogfproject");
        menu.submit();
        assertEquals("projects/my-level.ogfproject", accepted.get());
        assertFalse(menu.isOpen());
    }

    @Test
    void textCaptureSuppressesMenuActionsAndEscapeCancelsOnlyTheField() {
        var menu = new EditorProjectMenu();
        var submits = new AtomicInteger();
        menu.showForm("Properties", List.of(new EditorProjectMenu.Field("name", "Name", "Level",
                MenuTextEditor.Mode.TEXT)), "Apply", values -> submits.incrementAndGet());
        var input = new InputHandler();
        press(menu, input, GLFW_KEY_ENTER);
        assertTrue(menu.capturesText());
        MenuInput.handleCharEvent(input, 'x');
        input.refreshLogicalSnapshot();
        menu.update(input);
        input.update();
        press(menu, input, GLFW_KEY_ESCAPE);
        assertFalse(menu.capturesText());
        assertTrue(menu.isOpen());
        assertEquals("Level", menu.values().get("name"));
        assertEquals(0, submits.get());
    }

    @Test
    void callbackCanReplaceTheMenuWithTheNextWizardPage() {
        var menu = new EditorProjectMenu();
        menu.showActions("Games", List.of(new EditorProjectMenu.Action("Sonic 1", () ->
                menu.showActions("Zones", List.of(new EditorProjectMenu.Action("Green Hill", () -> {}))))));
        press(menu, new InputHandler(), GLFW_KEY_ENTER);
        assertTrue(menu.isOpen());
        assertEquals("Zones", menu.title());
    }

    private static void press(EditorProjectMenu menu, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        input.refreshLogicalSnapshot();
        if (key == GLFW_KEY_ENTER) {
            assertFalse(MenuInput.back(input), "physical Enter must not be Back");
            assertTrue(MenuInput.accept(input), "physical Enter must be Accept");
        }
        menu.update(input);
        input.update();
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
    }
}

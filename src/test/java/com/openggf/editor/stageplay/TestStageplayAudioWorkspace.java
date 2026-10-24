package com.openggf.editor.stageplay;

import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.stageplay.StageplayDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestStageplayAudioWorkspace {
    private EditorProjectMenu.FormField sound(StageplayWorkspace workspace) {
        return workspace.menu().formView().groups().stream().flatMap(g -> g.fields().stream())
                .filter(f -> f.field().key().equals("sound")).findFirst().orElseThrow();
    }

    @Test void musicChoicesUseNamesAndNativeNumbersAndRefreshForTheSelectedGameAndKind() {
        var workspace = new StageplayWorkspace(() -> StageplayDocument.empty("s2",0,0,0,0,320,224));
        workspace.activate(); workspace.invoke(StageplayWorkspace.Action.AUDIO); workspace.menu().runAction(0);
        workspace.menu().setValue("kind","ROM_MUSIC"); workspace.menu().setValue("sound","130");
        assertTrue(sound(workspace).display().contains("Emerald Hill"), sound(workspace).display());
        assertTrue(sound(workspace).display().contains("$82"));
        assertTrue(sound(workspace).optionLabels().stream().anyMatch(s -> s.contains("Metropolis")));
        workspace.menu().submit(); assertNull(workspace.menu().error());
        assertEquals(130,workspace.controller().document().audio().getFirst().soundId());
        workspace.invoke(StageplayWorkspace.Action.UNDO); assertTrue(workspace.controller().document().audio().isEmpty());
        workspace.invoke(StageplayWorkspace.Action.REDO);
        workspace.invoke(StageplayWorkspace.Action.AUDIO); workspace.menu().runAction(1); workspace.menu().runAction(0);
        workspace.menu().setValue("game","s1"); workspace.menu().setValue("sound","129");
        assertTrue(sound(workspace).display().contains("Green Hill"));
        workspace.menu().setValue("game","s3k"); workspace.menu().setValue("sound","220");
        assertTrue(sound(workspace).display().contains("Credits"));
        workspace.menu().setValue("kind","ROM_SFX"); workspace.menu().setValue("sound","51");
        assertTrue(sound(workspace).display().contains("Ring"));
        workspace.menu().submit(); assertNull(workspace.menu().error());
        assertEquals(51,workspace.controller().document().audio().getFirst().soundId());
    }
}

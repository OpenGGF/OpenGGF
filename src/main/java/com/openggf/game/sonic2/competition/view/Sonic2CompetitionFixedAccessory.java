package com.openggf.game.sonic2.competition.view;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Real fixed SST dispatch owners around the existing canonical player controllers. */
public final class Sonic2CompetitionFixedAccessory extends AbstractObjectInstance
        implements RewindRecreatable, Sonic2NativeDisplaySource {
    // Harmless generic-rewind probe. The captured slot selects the real constructor in recreateForRewind.
    Sonic2CompetitionFixedAccessory() { this(128); }
    public Sonic2CompetitionFixedAccessory(int slot) {
        super(new ObjectSpawn(0,0,slot==128?5:8,0,0,false,0),"S2FixedAccessory");
        if(slot!=128 && slot!=132 && slot!=133)throw new IllegalArgumentException("Not a native accessory slot");
    }
    @Override public void update(int vint,PlayableEntity primary) {
        // Tails_Tails=$D000 ($80), Sonic_Dust=$D100 ($84), Tails_Dust=$D140 ($85),
        // then Sonic_Shield=$D180 ($86) / Tails_Shield=$D1C0 ($87).
        // LevelOnly_Object_RAM (s2.constants.asm:1149-1165), InitPlayers (s2.asm:5181-5196),
        // RunObjects (s2.asm:29805-29822).
        // The actual SST walk, including its teleport gate, owns these calls before shields/stars.
        int slot=getSlotIndex();var sprites=services().spriteManager();
        if(slot==128)sprites.processInitialTailsFixedSlot();
        else {
            sprites.processInitialDustFixedSlot(slot-132);
            services().gameModule().getGameService(Sonic2CompetitionDustDisplay.class).captureChildren(slot-132);
        }
    }
    @Override public Sonic2CompetitionDisplayInputs.ObjectSlot nativeDisplaySlot() {
        // A2-R's existing tail source still owns Obj05's display projection at128.
        if(getSlotIndex()==128)return null;
        var source=services().gameModule().getGameService(Sonic2CompetitionDustDisplay.class);
        return java.util.Objects.requireNonNull(source,"Native dust display owner").fixed(getSlotIndex()-132);
    }
    // Obj05_Init (s2.asm:41713-41721), Obj08_Init (s2.asm:42721-42725).
    @Override public int getPriorityBucket(){return com.openggf.graphics.RenderPriority.bucket(getSpawn().objectId()==5?2:1);}
    @Override public boolean usesCustomOutOfRangeCheck(){return true;}
    @Override public boolean isCustomOutOfRange(int cameraX){return false;}
    @Override public void appendRenderCommands(List<GLCommand> commands){ }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new Sonic2CompetitionFixedAccessory(context.state().slotIndex());
    }
}

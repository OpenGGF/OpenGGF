package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Folded four-SST SKL {@code $4F}, {@code Obj_DEZStaircase} (sonic3k.asm:93363-93554). */
public final class S3kDezStaircaseObjectInstance extends AbstractObjectInstance
        implements MultiPieceSolidProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private int baseX, baseY;
    private int routine;
    private int timer;
    private int[] offsets = new int[4];
    private boolean p1Contact, p2Contact;
    private boolean slotsReserved;

    public S3kDezStaircaseObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZStaircase");
        baseX = spawn.x();
        baseY = spawn.y();
        routine = spawn.subtype() & 7;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        reserveSlots();
        switch (routine) {
            case 0, 4 -> updateP2Wait();
            case 1, 3 -> raise();
            case 2, 6 -> updateP1Wait();
            case 5, 7 -> lower();
            default -> { }
        }
        if (!isInRangeAt(baseX)) setDestroyedByOffscreen();
    }

    private void updateP2Wait() {
        if (timer == 0) {
            if (p2Contact) timer = 30;
            return;
        }
        if (--timer == 0) advanceAndSound();
    }

    private void updateP1Wait() {
        if (timer == 0) {
            if (p1Contact) timer = 0x3C;
            return;
        }
        if (--timer == 0) {
            advanceAndSound();
            return;
        }
        int value = (timer >> 2) & 1;
        for (int i = 0; i < 4; i++) {
            offsets[i] = value;
            value ^= 1;
        }
    }

    private void raise() {
        if (offsets[0] == 0x80) return;
        setRampOffsets(offsets[0] + 1);
    }

    private void lower() {
        if (offsets[0] == -0x80) return;
        setRampOffsets(offsets[0] - 1);
    }

    private void setRampOffsets(int value) {
        offsets[0] = value;
        offsets[1] = (value * 3) >> 2;
        offsets[2] = value >> 1;
        offsets[3] = value >> 2;
    }

    private void advanceAndSound() {
        routine = (routine + 1) & 7;
        if (tryServices() != null) services().playSfx(Sonic3kSfx.FAN_BIG.id);
    }

    private void reserveSlots() {
        if (slotsReserved || getSlotIndex() < 0) return;
        slotsReserved = true;
        if (tryServices() != null && services().objectManager() != null)
            services().objectManager().allocateChildSlotsAfter(spawn, 3, getSlotIndex());
    }

    @Override public int getReservedChildSlotCount() { return 3; }
    @Override public int getPieceCount() { return 4; }
    @Override public int getPieceX(int piece) { return baseX + piece * 0x20; }
    @Override public int getPieceY(int piece) { return baseY + offsets[offsetIndex(piece)]; }
    private int offsetIndex(int piece) { return (spawn.renderFlags() & 1) == 0 ? piece : 3 - piece; }
    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x1B, 0x10, 0x11); }
    @Override public boolean usesPieceScopedStandingBits() { return true; }
    @Override public boolean resolvesEarlierPiecesBeforeRidingPiece() { return true; }

    @Override
    public void onPieceContact(int piece, PlayableEntity player, SolidContact contact, int frame) {
        if (player == null || contact == null || !(contact.standing() || contact.pushing())) return;
        if (player.isCpuControlled()) p2Contact = true;
        else p1Contact = true;
    }

    @Override public int getX() { return baseX; }
    @Override public int getY() { return baseY; }
    @Override public int getOnScreenHalfWidth() { return 0x50; }
    @Override public int getOnScreenHalfHeight() { return 0x90; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x180); }
    @Override public int romObjectCodePointerHighWord() { return 4; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_STAIRCASE);
        boolean hFlip = (spawn.renderFlags() & 1) != 0
                ^ (spawn.subtype() & 7) >= 4
                ^ (spawn.renderFlags() & 2) != 0;
        if (renderer != null && renderer.isReady())
            for (int i = 0; i < 4; i++)
                renderer.drawFrameIndex(0, getPieceX(i), getPieceY(i), hFlip, false);
    }

    void triggerForTest(boolean p2) { if (p2) p2Contact = true; else p1Contact = true; }
    int routineForTest() { return routine; }
    int offsetForTest(int index) { return offsets[index]; }
}

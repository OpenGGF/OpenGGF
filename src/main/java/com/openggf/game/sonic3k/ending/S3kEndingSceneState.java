package com.openggf.game.sonic3k.ending;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

/** Native Load_Level scene selection, direct FIFO handles and retained name tables. */
public final class S3kEndingSceneState {
    public static final int SSZ = 0, AIZ = 1;
    private int pendingScene = -1, activeScene = SSZ, directCount;
    private final long[] direct = new long[3];
    private final Plane foreground = new Plane(), background = new Plane();
    static final int SNAPSHOT_BYTES = 3 * Integer.BYTES + 3 * Long.BYTES + 2 * Plane.BYTES;
    public int activeScene() { return activeScene; }
    public int pendingScene() { return pendingScene; }
    public int directCount() { return directCount; }
    public long directOrdinal(int index) { return direct[index]; }
    public Plane foreground() { return foreground; }
    public Plane background() { return background; }
    int snapshotBytes() { return 3 * Integer.BYTES + 3 * Long.BYTES + foreground.snapshotBytes() + background.snapshotBytes(); }
    void beginQueue(int scene) { pendingScene = scene; directCount = 0; Arrays.fill(direct, -1); }
    void appendDirect(long ordinal) { direct[directCount++] = ordinal; }
    void installed() { activeScene = pendingScene; pendingScene = -1; directCount = 0; }
    void captureTo(ByteBuffer b) {
        b.putInt(pendingScene).putInt(activeScene).putInt(directCount);
        for (long ordinal : direct) b.putLong(ordinal);
        foreground.captureTo(b); background.captureTo(b);
    }
    void restoreFrom(ByteBuffer b) {
        pendingScene = b.getInt(); activeScene = b.getInt(); directCount = b.getInt();
        for (int i = 0; i < direct.length; i++) direct[i] = b.getLong();
        foreground.restoreFrom(b); background.restoreFrom(b);
    }
    public static final class Plane {
        private static final int BYTES = 64 * 32 * Short.BYTES + 5 * Integer.BYTES;
        private final short[] cells = new short[64 * 32];
        private int revision, delayedY, remaining = -1, sourceX, sourceY;
        public int revision() { return revision; }
        public int remaining() { return remaining; }
        public int descriptor(int x, int y) { return cells[index(x, y)] & 0xFFFF; }
        /** Reset_TileOffsetPositionActual parks draw cursors without refilling native cells. */
        public void resetScrollPosition(int x,int y){sourceX=x&0xFFF0;sourceY=y&0xFF0;}
        /** Reset_TileOffsetPositionActual followed by Draw_delayed_position=$E0+roundedY. */
        public void begin(int x, int y) {
            sourceX = x & ~15; sourceY = y & 0xFF0;
            delayedY = (sourceY + 0xE0) & 0xFF0; remaining = 15;
        }
        /** Draw_PlaneVertBottomUp calls its single-row routine twice while count >= 0. */
        public boolean advance(IntBinaryOperator source) {
            return advanceComplex(y -> sourceX,source);
        }
        /** Draw_PlaneVertBottomUpComplex selects both source and physical cell X for each row. */
        public boolean advanceComplex(IntUnaryOperator sourceXForY,IntBinaryOperator source) {
            for (int row = 0; row < 2 && remaining >= 0; row++) {
                if (delayedY >= sourceY && delayedY <= ((sourceY + 0xF0) & 0xFF0))
                    writeRow(sourceXForY.applyAsInt(delayedY), delayedY, source);
                delayedY = (short) (delayedY - 16); remaining--;
            }
            return remaining < 0;
        }
        public void fill(int x, int y, IntBinaryOperator source) {
            sourceX=x&0xFFF0;sourceY=y&0xFF0;
            for (int row = 0; row < 16; row++) writeRow(x & ~15, (y & ~15) + row * 16, source);
        }
        /** Draw_TileRow retains the old ring, with the native byte direction/two-write cap. */
        public void streamRows(int x,int y,int blocks,IntBinaryOperator source) {
            streamRowsComplex(row->x,y,blocks,source);
        }
        /** Get_DeformDrawPosVert selects source X from the same native band as its incoming row. */
        public void streamRowsComplex(IntUnaryOperator sourceXForY,int y,int blocks,IntBinaryOperator source) {
            int next=y&0xFF0,difference=(short)(sourceY-next),row=next;
            if((byte)difference<0){difference=-difference;row=(sourceY+0xF0)&0xFF0;}
            sourceY=next;if(difference==0)return;
            writeRow(sourceXForY.applyAsInt(row),row,blocks,source);
            if((difference&0x30)!=0x10){row=(row+16)&0xFF0;writeRow(sourceXForY.applyAsInt(row),row,blocks,source);}
        }
        /** DrawTilesAsYouMove draws columns first, then the 21-block incoming rows. */
        public void drawAsYouMove(int x,int y,IntBinaryOperator source) {
            int next=x&0xFFF0,difference=(short)(sourceX-next),column=next;
            if((byte)difference<0){column=(sourceX+0x150)&0xFFFF;difference=-difference;}
            sourceX=next;
            if(difference!=0){
                writeColumn(column,y,source);
                if((difference&0x30)!=0x10)writeColumn(column+16,y,source);
            }
            streamRows(x,y,21,source);
        }
        private void writeColumn(int x,int y,IntBinaryOperator source) {
            x=(short)x&~15;y=(short)y&~15;
            for(int dy=0;dy<15*16;dy+=8)for(int dx=0;dx<16;dx+=8)
                cells[index(x+dx,y+dy)]=(short)source.applyAsInt(x+dx,y+dy);
            revision++;
        }
        private static int index(int x, int y) { return ((y >>> 3) & 31) * 64 + ((x >>> 3) & 63); }
        private void writeRow(int x, int y, IntBinaryOperator source) { writeRow(x,y,32,source); }
        private void writeRow(int x, int y, int blocks, IntBinaryOperator source) {
            x=(short)x&~15;y=(short)y&~15;
            for (int dy = 0; dy < 16; dy += 8) for (int dx = 0; dx < blocks*16; dx += 8)
                cells[index(x + dx, y + dy)] = (short) source.applyAsInt(x + dx, y + dy);
            revision++;
        }
        private void captureTo(ByteBuffer b) {
            b.putInt(revision).putInt(delayedY).putInt(remaining).putInt(sourceX).putInt(sourceY);
            if(revision!=0)for (short cell : cells) b.putShort(cell);
        }
        private void restoreFrom(ByteBuffer b) {
            revision=b.getInt(); delayedY=b.getInt(); remaining=b.getInt(); sourceX=b.getInt(); sourceY=b.getInt();
            if(revision==0)Arrays.fill(cells,(short)0);
            else for (int i=0;i<cells.length;i++) cells[i]=b.getShort();
        }
        private int snapshotBytes() { return 5 * Integer.BYTES + (revision == 0 ? 0 : cells.length * Short.BYTES); }
    }
}

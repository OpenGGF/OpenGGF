package example.tide;

import com.openggf.game.*;
import com.openggf.game.animation.*;
import com.openggf.game.modzone.*;
import com.openggf.game.render.*;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.level.*;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.mods.code.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/** Original bounded content; the user's S2 ROM supplies the native character and signpost/results services. */
public final class TideCircuitMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.registerObject("finish-gate",(spawn,registry)->new TideCircuitGoal(spawn));
        context.registerZone(ModZoneContribution.multiAct("tide-circuit", List.of(
                new BakedLevelRef("levels/tide/act1/level.json"),
                new BakedLevelRef("levels/tide/act2/level.json")), "ehz2", Events::new, false)
                .withRuntime(TideCircuitMod::runtime));
    }
    private static ModZoneRuntimeServices runtime(ModZoneRuntimeContext context) {
        State state = new State(context);
        Level level = context.level();
        AnimatedTileChannel waves = new AnimatedTileChannel("waves", () -> true,
                frame -> (frame.frameCounter()/8)&1, DestinationPlan.single(2),
                AnimatedTileCachePolicy.ON_PHASE_CHANGE, frame -> {
                    state.tileApplications++;
                    state.tileColor=2+((frame.frameCounter()/8)&1);
                    state.applyTileColor();
                });
        SpecialRenderEffect effect = new SpecialRenderEffect() {
            public SpecialRenderEffectStage stage() { return SpecialRenderEffectStage.AFTER_SPRITES; }
            public void render(SpecialRenderEffectContext frame) {
                state.renderCalls++;
                // A small color beacon on the original background tiles exposes the active render contribution.
                level.getPalette(0).getColor(3).fromSegaFormat(context.actIndex()==0 ? 0x0E22 : 0x022E);
                frame.graphicsManager().updatePatternTexture(level.getPattern(2),2);
                frame.graphicsManager().cachePaletteTexture(level.getPalette(0),0);
            }
        };
        WaterDataProvider water = new WaterDataProvider() {
            public boolean hasWater(int zone,int act,PlayerCharacter character) { return true; }
            public int getStartingWaterLevel(int zone,int act) { return context.actIndex()==0 ? 248 : 240; }
            public Palette[] getUnderwaterPalette(com.openggf.data.Rom rom,int zone,int act,PlayerCharacter character) {
                Palette[] colors = new Palette[level.getPaletteCount()];
                for (int i=0;i<colors.length;i++) colors[i]=level.getPalette(i).deepCopy();
                colors[0].getColor(1).fromSegaFormat(0x0E42);
                return colors;
            }
            public DynamicWaterHandler getDynamicHandler(int zone,int act,PlayerCharacter character) {
                return (water,x,y) -> { state.waterCalls++; water.setTarget(context.actIndex()==0 ? 248 : 240); };
            }
        };
        ZoneScrollHandler scroll = new ZoneScrollHandler() {
            public void update(int[] lines,int x,int y,int frame,int act) {
                if (state.lastScrollFrame != frame) { state.scrollCalls++; state.lastScrollFrame=frame; }
                Arrays.fill(lines,((-x)&0xffff)<<16|((-x/2)&0xffff));
            }
            public short getVscrollFactorBG() { return 0; }
            public int getMinScrollOffset() { return 0; }
            public int getMaxScrollOffset() { return 0; }
        };
        return ModZoneRuntimeServices.builder().water(water).scroll(scroll).state(state)
                .animatedTile(waves).paletteAnimation(() -> {
                    state.paletteCalls++;
                    level.getPalette(0).getColor(2).fromSegaFormat((state.paletteCalls/8&1)==0 ? 0x0E80 : 0x0E40);
                }).renderEffect(effect).build();
    }
    /** The runtime registry captures these counters and restores the same active act state. */
    public static final class State implements ZoneRuntimeState {
        final ModZoneRuntimeContext context;
        public int tileApplications,waterCalls,scrollCalls,paletteCalls,renderCalls;
        private int lastScrollFrame=Integer.MIN_VALUE;
        private int tileColor=2;
        State(ModZoneRuntimeContext context) { this.context=context; }
        public String gameId() { return context.hostGameId(); }
        public int zoneIndex() { return context.zoneIndex(); }
        public int actIndex() { return context.actIndex(); }
        public byte[] captureBytes() {
            return ByteBuffer.allocate(28).putInt(tileApplications).putInt(waterCalls).putInt(scrollCalls).putInt(paletteCalls).putInt(renderCalls).putInt(lastScrollFrame).putInt(tileColor).array();
        }
        private void applyTileColor() {
            for (int y=0;y<8;y++) for (int x=0;x<8;x++) context.level().getPattern(2).setPixel(x,y,(byte)tileColor);
        }
        public void restoreBytes(byte[] bytes) {
            ByteBuffer data=ByteBuffer.wrap(bytes); tileApplications=data.getInt(); waterCalls=data.getInt();
            scrollCalls=data.getInt(); paletteCalls=data.getInt(); renderCalls=data.getInt();lastScrollFrame=data.getInt();tileColor=data.getInt();applyTileColor();
        }
    }
    public static final class Events implements RewindableZoneEvents<Integer> {
        private int ticks;
        public void initLevel(int zone,int act) { ticks=0; }
        public void update() { ticks++; }
        public Integer capture() { return ticks; }
        public void restore(Integer value) { ticks=value; }
        public void resetForMissingSnapshot() { ticks=0; }
        public void reconcileAfterRewindRestore() { }
    }
}

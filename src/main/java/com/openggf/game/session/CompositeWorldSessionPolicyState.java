package com.openggf.game.session;

import com.openggf.LevelFrameContext;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameplayObservation;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.sprites.managers.SpriteManager;
import java.util.*;

/** Internal per-world composition. Children retain their own captured state and service contracts. */
public final class CompositeWorldSessionPolicyState implements WorldSessionPolicyState, RewindSnapshottable<List<Object>> {
    private final List<WorldSessionPolicyState> children;
    private CompositeWorldSessionPolicyState(List<WorldSessionPolicyState> children) { this.children = List.copyOf(children); }
    public static WorldSessionPolicyState of(WorldSessionPolicyState... states) {
        var values = Arrays.stream(states).filter(Objects::nonNull).toList();
        return values.isEmpty() ? null : values.size() == 1 ? values.getFirst() : new CompositeWorldSessionPolicyState(values);
    }
    @Override public <T> T getService(Class<T> type) {
        for (var child : children) { var value = child.getService(type); if (value != null) return value; }
        return null;
    }
    @Override public RewindSnapshottable<?> rewindAdapter() { return this; }
    @Override public String key() { return "world-policies"; }
    @Override public List<Object> capture() { return children.stream().map(c -> (Object)c.rewindAdapter().capture()).toList(); }
    @SuppressWarnings({"rawtypes","unchecked"})
    @Override public void restore(List<Object> snapshot) {
        if (snapshot.size() != children.size()) throw new IllegalArgumentException("World policy graph changed");
        for (int i=0;i<children.size();i++) ((RewindSnapshottable)children.get(i).rewindAdapter()).restore(snapshot.get(i));
    }
    @Override public void beforeAssembly(LevelLoadCause cause) { children.forEach(c -> c.beforeAssembly(cause)); }
    @Override public void bindGameplay(GameplayModeContext context) { children.forEach(c -> c.bindGameplay(context)); }
    @Override public void bindRoster(SpriteManager sprites) { children.forEach(c -> c.bindRoster(sprites)); }
    @Override public void beforeSpecialStageForwardTick() { children.forEach(WorldSessionPolicyState::beforeSpecialStageForwardTick); }
    @Override public void observeTick(LevelFrameContext context, LogicalInputSnapshot input) { children.forEach(c -> c.observeTick(context,input)); }
    @Override public void beforeNativeTick(LevelFrameContext context, LogicalInputSnapshot input) { children.forEach(c -> c.beforeNativeTick(context,input)); }
    @Override public void afterNativeTick(LevelFrameContext context, LogicalInputSnapshot input) { children.forEach(c -> c.afterNativeTick(context,input)); }
    @Override public void onPresentation(GameplayModeContext context, LogicalInputSnapshot input, GameplayObservation.Phase phase) { children.forEach(c -> c.onPresentation(context,input,phase)); }
    @Override public void onMenuPresentation(LogicalInputSnapshot input,int width,int height,
            java.util.function.Consumer<com.openggf.audio.GameSound> sounds) { children.forEach(c -> c.onMenuPresentation(input,width,height,sounds)); }
    @Override public void leaveOrdinaryGameplay(GameplayObservation.Phase phase) { clean(c -> c.leaveOrdinaryGameplay(phase)); }
    @Override public void drawOverlay(PixelCanvas canvas) { children.forEach(c -> c.drawOverlay(canvas)); }
    @Override public boolean holdsGameplay() { return children.stream().anyMatch(WorldSessionPolicyState::holdsGameplay); }
    @Override public void failedAssembly(LevelLoadCause cause) { clean(c -> c.failedAssembly(cause)); }
    @Override public void closeScreens() { clean(WorldSessionPolicyState::closeScreens); }
    @Override public void retire() { clean(WorldSessionPolicyState::retire); }
    private void clean(java.util.function.Consumer<WorldSessionPolicyState> operation) {
        RuntimeException first = null;
        for (var child : children) try { operation.accept(child); } catch (RuntimeException failure) { if(first==null)first=failure;else first.addSuppressed(failure); }
        if(first!=null)throw first;
    }
}

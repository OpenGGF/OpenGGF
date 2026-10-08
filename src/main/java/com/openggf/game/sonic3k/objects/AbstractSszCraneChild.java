package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;

/**
 * Child object of the SSZ2 crane encounter with a ROM {@code parent3} link.
 * Scalars and the centrally registered parent reference use generic capture;
 * the identity table resolves the link after the whole graph is recreated.
 */
abstract class AbstractSszCraneChild extends AbstractObjectInstance
        implements RewindRecreatable, SszCranePose {
    /** {@code parent3(a0)}. */
    protected AbstractObjectInstance parent;

    protected boolean retiring;
    @Override public boolean craneRetiring() { return retiring; }
    @Override public boolean craneFlipped() { return false; }

    protected AbstractSszCraneChild(ObjectSpawn spawn, String name,
                                                     AbstractObjectInstance parent) {
        super(spawn, name);
        this.parent = parent;
    }

    protected boolean parentFlipped() {
        return parent instanceof SszCranePose pose && pose.craneFlipped();
    }

    /** {@code Child_Draw_Sprite}: {@code btst #7,status(parent)} means the parent is going away. */
    protected boolean parentGone() {
        return parent == null || parent.isDestroyed()
                || parent instanceof SszCranePose pose && pose.craneRetiring();
    }


}

/** Render_flags bit 0 shared along the crane's native parent3 chain. */
interface SszCranePose {
    boolean craneFlipped();
    default boolean craneRetiring() { return false; }
}

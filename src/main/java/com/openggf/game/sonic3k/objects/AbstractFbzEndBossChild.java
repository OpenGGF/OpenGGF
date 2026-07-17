package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;

abstract class AbstractFbzEndBossChild extends AbstractObjectInstance
        implements FbzEndBossGraphMember, RewindRecreatable {
    protected FbzEndBossInstance boss;
    protected String role;
    protected int x;
    protected int y;

    AbstractFbzEndBossChild(FbzEndBossInstance boss, String role, String name) {
        this(boss, role, name, 0);
    }
    AbstractFbzEndBossChild(FbzEndBossInstance boss, String role, String name, int subtype) {
        this(new ObjectSpawn(boss.getX(), boss.getY(), FbzEndBossInstance.OBJECT_ID,
                subtype, 0, false, 0), boss, role, name);
    }

    AbstractFbzEndBossChild(ObjectSpawn spawn, String role, String name) {
        this(spawn, null, role, name);
    }

    private AbstractFbzEndBossChild(ObjectSpawn spawn, FbzEndBossInstance boss, String role, String name) {
        super(spawn, name);
        this.boss = boss;
        this.role = role;
        this.x = spawn.x();
        this.y = spawn.y();
    }

    @Override public String rewindRole() { return role; }
    @Override public FbzEndBossInstance boss() { return boss; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; }
    @Override public void update(int frameCounter, PlayableEntity mainPlayer) { }
}

package com.openggf.level.objects;

@FunctionalInterface
@com.openggf.game.ModApi
public interface ObjectFactory {
    ObjectInstance create(ObjectSpawn spawn, ObjectRegistry registry);
}

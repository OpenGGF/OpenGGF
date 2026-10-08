package com.openggf.mods;

import java.util.List;
import java.util.Optional;

/** Bounded UTF-8 files private to a verified mod owner, shared by its scenes and gameplay modules. */
@com.openggf.game.ModApi
public interface ModStorage {
    Optional<String> read(String name);
    boolean write(String name, String text);
    boolean delete(String name);
    List<String> list();
}

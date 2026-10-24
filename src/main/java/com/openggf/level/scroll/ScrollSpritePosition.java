package com.openggf.level.scroll;

/** Picture-only world coordinates; never an instruction to move a native object's RAM. */
public record ScrollSpritePosition(int x, int y) {}

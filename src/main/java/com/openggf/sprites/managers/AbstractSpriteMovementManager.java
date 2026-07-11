package com.openggf.sprites.managers;

import com.openggf.sprites.Sprite;

@com.openggf.game.ModApi
public abstract class AbstractSpriteMovementManager<T extends Sprite> implements
		SpriteMovementManager {
	protected final T sprite;

	protected AbstractSpriteMovementManager(T sprite) {
		this.sprite = sprite;
	}
}

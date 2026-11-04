package com.openggf.route;

/**
 * How an object holds a player. {@link #STANDING} is the generic solid ride (OnObj and the ride
 * owner); the others are owner-reported custody.
 */
public enum InteractionKind { STANDING, GRAB, HANG, CARRIED, TUBE, AIRFLOW, LATCH }

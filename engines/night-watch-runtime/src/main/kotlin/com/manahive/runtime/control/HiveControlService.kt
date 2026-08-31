package com.manahive.runtime.control

/**
 * Service Layer (Fowler P of EAA) — orchestrates hive control.
 * Pure orchestration, no NATS/HTTP. Depends on abstractions (DIP).
 */
interface HiveControlService {
    fun reload(cmd: HiveCommand): HiveControlEvent
    fun reset(cmd: HiveCommand): HiveControlEvent
    fun resetFull(cmd: HiveCommand): HiveControlEvent
}

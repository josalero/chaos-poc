package com.samba.chaos.relay.model;

/**
 * How the latest command sits relative to the instances that should have applied it.
 *
 * <p>DEFAULT is no command, or an applied DISABLE. DESIRED is PENDING or PARTIAL. APPLIED is an
 * applied configure or enable. PARTIAL is TIMED_OUT. FAILED is a failed aggregate.
 */
public enum ServiceConfigState {
  DEFAULT,
  DESIRED,
  APPLIED,
  PARTIAL,
  FAILED
}

package com.samba.chaos.relay.model;

/**
 * Aggregate of one command across every expected instance.
 *
 * <p>Evaluation order is FAILED, then APPLIED, then TIMED_OUT, then PENDING, then PARTIAL.
 * PUBLISHED is the status on the 202 body, before any instance has reported.
 */
public enum CommandAggregateStatus {
  PUBLISHED,
  PENDING,
  PARTIAL,
  APPLIED,
  FAILED,
  TIMED_OUT
}

package com.samba.chaos.relay.model;

public enum CommandAggregateStatus {
  PUBLISHED,
  PENDING,
  PARTIAL,
  APPLIED,
  FAILED,
  TIMED_OUT
}

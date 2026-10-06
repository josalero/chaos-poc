package com.samba.chaos.command;

/** Per-instance result of one command. */
public enum InstanceOutcome {
  SUCCESS,
  ACTUATOR_ERROR,
  UNREACHABLE,
  REJECTED
}

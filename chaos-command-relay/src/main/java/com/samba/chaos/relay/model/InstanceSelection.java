package com.samba.chaos.relay.model;

/**
 * Which UP instances receive a command.
 *
 * <p>{@code ALL} is every UP instance. {@code SOME} is only the ids listed on the command. Omitted
 * selection means {@code ALL}.
 */
public enum InstanceSelection {
  ALL,
  SOME
}

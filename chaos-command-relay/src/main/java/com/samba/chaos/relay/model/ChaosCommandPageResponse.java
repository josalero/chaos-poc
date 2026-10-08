package com.samba.chaos.relay.model;

import java.util.List;

/**
 * One page of stored commands, newest {@code publishedAt} first.
 *
 * @param content commands on this page
 * @param page zero-based page index
 * @param size page size
 * @param totalElements matching commands
 */
public record ChaosCommandPageResponse(
    List<ChaosCommandStatusResponse> content, int page, int size, long totalElements) {}

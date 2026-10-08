package com.samba.chaos.relay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samba.chaos.command.ChaosCommandAction;
import com.samba.chaos.relay.config.ChaosRelayProperties;
import com.samba.chaos.relay.model.ChaosCommandPageResponse;
import com.samba.chaos.relay.model.ChaosCommandRequest;
import com.samba.chaos.relay.model.ChaosCommandStatusResponse;
import com.samba.chaos.relay.model.CommandAggregateStatus;
import com.samba.chaos.relay.model.ValidationErrorResponse;
import com.samba.chaos.relay.model.ValidationErrorResponse.FieldError;
import com.samba.chaos.relay.store.ChaosCommandStore;
import com.samba.chaos.relay.store.CommandRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ChaosCommandServiceTest {

  @Test
  void rejectsInvalidRequestBeforeDiscovery() {
    ChaosCommandValidator validator = mock(ChaosCommandValidator.class);
    ChaosCommandRequest request =
        new ChaosCommandRequest(
            null, "production", "orders", ChaosCommandAction.DISABLE, null, null, "op", null);
    when(validator.validate(request))
        .thenReturn(List.of(new FieldError("environment", "must be 'test'")));
    when(validator.toErrorResponse(org.mockito.ArgumentMatchers.anyList()))
        .thenReturn(
            new ValidationErrorResponse(
                "REJECTED", List.of(new FieldError("environment", "must be 'test'"))));
    ChaosCommandService service =
        new ChaosCommandService(
            validator, mock(TargetInstancesResolver.class), null, null, null, null);

    ChaosCommandService.SubmitResult result = service.submit(request);

    assertThat(result).isInstanceOf(ChaosCommandService.SubmitResult.Rejected.class);
  }

  @Test
  void returnsEmptyStatusWhenCommandIsUnknown() {
    ChaosCommandStore store = mock(ChaosCommandStore.class);
    UUID commandId = UUID.randomUUID();
    when(store.findById(commandId)).thenReturn(Optional.empty());
    ChaosCommandService service =
        new ChaosCommandService(
            null, null, null, store, mock(ChaosCommandStatusService.class), null);

    assertThat(service.getStatus(commandId)).isEmpty();
  }

  @Test
  void listsAllowlistedCommandsAndFiltersComputedStatus() {
    ChaosRelayProperties properties = new ChaosRelayProperties();
    properties.setAllowedTargetApplications(List.of("orders"));
    ChaosCommandStore store = mock(ChaosCommandStore.class);
    ChaosCommandStatusService statusService = mock(ChaosCommandStatusService.class);
    CommandRecord appliedRecord = mock(CommandRecord.class);
    CommandRecord failedRecord = mock(CommandRecord.class);
    ChaosCommandStatusResponse applied = mock(ChaosCommandStatusResponse.class);
    ChaosCommandStatusResponse failed = mock(ChaosCommandStatusResponse.class);
    when(applied.status()).thenReturn(CommandAggregateStatus.APPLIED);
    when(failed.status()).thenReturn(CommandAggregateStatus.FAILED);
    when(store.findPage(eq(List.of("orders")), isNull(), any()))
        .thenReturn(new PageImpl<>(List.of(appliedRecord), PageRequest.of(0, 1), 4));
    when(statusService.toStatusResponse(appliedRecord)).thenReturn(applied);
    when(store.findMatching(List.of("orders"), ChaosCommandAction.DISABLE))
        .thenReturn(List.of(appliedRecord, failedRecord));
    when(statusService.toStatusResponse(failedRecord)).thenReturn(failed);
    ChaosCommandService service =
        new ChaosCommandService(null, null, null, store, statusService, properties);

    ChaosCommandPageResponse empty = service.list(0, 50, "billing", null, null);
    ChaosCommandPageResponse page = service.list(-1, 500, " ", null, null);
    ChaosCommandPageResponse filtered =
        service.list(1, 1, "orders", CommandAggregateStatus.APPLIED, ChaosCommandAction.DISABLE);

    assertThat(empty.totalElements()).isZero();
    assertThat(empty.content()).isEmpty();
    assertThat(empty.page()).isZero();
    assertThat(empty.size()).isEqualTo(50);
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(store).findPage(eq(List.of("orders")), isNull(), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
    assertThat(pageable.getValue().getPageNumber()).isZero();
    assertThat(page.content()).containsExactly(applied);
    assertThat(page.totalElements()).isEqualTo(4);
    assertThat(page.size()).isEqualTo(200);
    assertThat(filtered.content()).isEmpty();
    assertThat(filtered.page()).isEqualTo(1);
    assertThat(filtered.size()).isEqualTo(1);
    assertThat(filtered.totalElements()).isEqualTo(1);
  }
}

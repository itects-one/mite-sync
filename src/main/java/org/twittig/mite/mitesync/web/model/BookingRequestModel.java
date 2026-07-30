package org.twittig.mite.mitesync.web.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request body for POST /daily-reports/{date}/book — carries the final entries (typically
 * produced by the preview endpoint and optionally edited by the user).
 */
public class BookingRequestModel {

  // @Valid sits on the type argument, not on the container — Hibernate Validator 9 deprecates
  // the container form (HV000271).
  @NotEmpty(message = "entries must not be empty")
  private List<@Valid ProposalEntryModel> entries;

  public BookingRequestModel() {}

  public List<ProposalEntryModel> getEntries() {
    return entries;
  }

  public void setEntries(List<ProposalEntryModel> entries) {
    this.entries = entries;
  }
}

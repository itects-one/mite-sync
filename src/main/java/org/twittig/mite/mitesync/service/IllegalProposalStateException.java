package org.twittig.mite.mitesync.service;

import org.twittig.mite.mitesync.persistence.ProposalStatus;

/**
 * Thrown when an operation (edit, confirm) is attempted on a proposal that is not in the required
 * state. Mapped to 409.
 */
public class IllegalProposalStateException extends RuntimeException {

  public IllegalProposalStateException(Long id, ProposalStatus actual, ProposalStatus required) {
    super(
        "Proposal '"
            + id
            + "' is "
            + actual
            + " but must be "
            + required
            + " for this operation");
  }
}

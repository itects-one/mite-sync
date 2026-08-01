package org.twittig.mite.mitesync.service;

/**
 * Thrown when a proposal without entries is confirmed. Mapped to 409.
 *
 * <p>Booking nothing is not a booking: without this guard the empty result would be derived as
 * {@code BOOKED}, freezing the proposal in a state that claims a success that never happened.
 */
public class EmptyProposalException extends RuntimeException {

  public EmptyProposalException(Long id) {
    super("Proposal '" + id + "' has no entries — there is nothing to book");
  }
}

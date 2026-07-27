package org.twittig.mite.mitesync.service;

/** Thrown when a request references a proposal id that does not exist. Mapped to 404. */
public class UnknownProposalException extends RuntimeException {

  public UnknownProposalException(Long id) {
    super("Unknown proposal '" + id + "'");
  }
}

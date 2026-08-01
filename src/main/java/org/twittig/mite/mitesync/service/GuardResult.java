package org.twittig.mite.mitesync.service;

import java.util.List;

/**
 * The verdict of {@link ProposalGuard} on a proposal.
 *
 * <p>Deliberately not an exception: the caller decides what a rejected proposal means. The intended
 * handling is to fall back to the rule-based proposal and carry the violations out as warnings — a
 * proposal that failed the guard must never end up stored as a {@code DRAFT} as though nothing had
 * happened.
 *
 * @param violations one message per failed check, empty when the proposal passed
 */
public record GuardResult(List<String> violations) {

  /** Nothing to complain about. */
  public static GuardResult passed() {
    return new GuardResult(List.of());
  }

  public boolean isPassed() {
    return violations.isEmpty();
  }
}

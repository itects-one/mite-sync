package org.twittig.mite.mitesync.persistence;

/**
 * Lifecycle of a stored booking proposal.
 *
 * <ul>
 *   <li>{@link #DRAFT} — generated and still editable; the only state in which entries may be
 *       changed or the proposal confirmed.
 *   <li>{@link #BOOKED} — confirmed and every entry was created in Mite.
 *   <li>{@link #PARTIALLY_BOOKED} — confirmed, but some entries failed (best-effort booking).
 *   <li>{@link #FAILED} — confirmed, but no entry could be created.
 * </ul>
 */
public enum ProposalStatus {
  DRAFT,
  BOOKED,
  PARTIALLY_BOOKED,
  FAILED
}

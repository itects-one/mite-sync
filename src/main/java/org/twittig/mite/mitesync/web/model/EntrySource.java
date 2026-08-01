package org.twittig.mite.mitesync.web.model;

/**
 * The provenance values a {@link ProposalEntryModel} can carry: where the entry came from.
 *
 * <p>Generated entries are labelled by the pipeline that produced them. {@link #MANUAL} marks an
 * entry a human wrote or changed through {@code PUT /proposals/{id}/entries} — the distinction
 * between "the app derived this from evidence" and "a human typed this".
 */
public final class EntrySource {

  /** Meeting taken from the calendar. */
  public static final String CALENDAR = "calendar";

  /** Fill-up onto the main PBI, up to the daily target. */
  public static final String MAIN_PBI_FILL = "main-pbi-fill";

  /** Derived from the commit history of the profile's local repositories. */
  public static final String GIT = "git";

  /** Fill-up onto the configured git fill-up ticket, up to the daily target. */
  public static final String GIT_FILL = "git-fill";

  /**
   * Composed by the LLM agent from the day's evidence, and passed by {@code ProposalGuard}. Set
   * server-side like every other value here — a model's own claim about where an entry came from is
   * not evidence of anything.
   */
  public static final String AGENT = "agent";

  /** Written or changed by hand. */
  public static final String MANUAL = "manual";

  private EntrySource() {}
}

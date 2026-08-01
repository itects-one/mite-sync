package org.twittig.mite.mitesync.service;

import java.util.List;

import org.twittig.mite.mitesync.web.model.WorkItemModel;

/**
 * What one Azure DevOps query produced: the work items, and what went wrong while fetching them.
 *
 * <p>Same reason as {@link GitActivityResult}: a query that fails must not abort the whole preview,
 * but the empty list it leaves behind is indistinguishable from a day without DevOps activity — and
 * an empty day makes the proposal fill every remaining minute onto the main PBI. An expired PAT
 * would otherwise look like a plausible report.
 *
 * <p>The warnings are single sentences by design. The response body of a failed call is often an
 * HTML sign-in page and belongs in the log, not in a banner.
 *
 * @param items the work items the query returned, empty when it failed
 * @param warnings one message per failed call, naming which query failed and why
 */
public record WorkItemResult(List<WorkItemModel> items, List<String> warnings) {

  /** A successful query with nothing to report. */
  public static WorkItemResult of(List<WorkItemModel> items) {
    return new WorkItemResult(items, List.of());
  }
}

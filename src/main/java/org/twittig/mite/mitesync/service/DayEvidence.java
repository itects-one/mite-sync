package org.twittig.mite.mitesync.service;

import java.util.List;

import org.twittig.mite.mitesync.web.model.CalendarEventModel;
import org.twittig.mite.mitesync.web.model.GitCommitModel;
import org.twittig.mite.mitesync.web.model.MiteEntryModel;
import org.twittig.mite.mitesync.web.model.WorkItemModel;

/**
 * Everything the app knows about one day, as gathered by {@code DailyReportFacade}: what the
 * calendar, the work item tracker and the local repositories show, and what is already booked.
 *
 * <p>This is what a proposal has to be justified by. {@link ProposalGuard} uses it to check that a
 * proposed entry refers to something that actually happened, which is the point of collecting it in
 * one place rather than passing four lists around.
 *
 * @param calendarEvents meetings of the day, already rounded
 * @param workItems work items the tracker reported for the day
 * @param gitCommits commits of the day across the profile's repositories
 * @param alreadyBooked entries that exist in Mite for this day
 * @param allowedTickets ticket ids that are legitimate although they appear nowhere in the day's
 *     evidence — the profile's main PBI, its meeting collector and its fill-up ticket. Booking onto
 *     them is the configured behaviour, not an invention.
 */
public record DayEvidence(
    List<CalendarEventModel> calendarEvents,
    List<WorkItemModel> workItems,
    List<GitCommitModel> gitCommits,
    List<MiteEntryModel> alreadyBooked,
    List<String> allowedTickets) {

  /** Whether the day shows any activity at all. An empty proposal is only correct if it does not. */
  public boolean hasActivity() {
    return !calendarEvents.isEmpty() || !workItems.isEmpty() || !gitCommits.isEmpty();
  }

  /** Minutes already booked in Mite for this day; they count towards the daily target. */
  public int alreadyBookedMinutes() {
    return alreadyBooked.stream().mapToInt(MiteEntryModel::getMinutes).sum();
  }
}

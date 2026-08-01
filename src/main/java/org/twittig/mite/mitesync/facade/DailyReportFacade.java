package org.twittig.mite.mitesync.facade;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.twittig.mite.mitesync.config.DailyReportProperties.Profile;
import org.twittig.mite.mitesync.config.ProfileRegistry;
import org.twittig.mite.mitesync.service.AzureDevOpsService;
import org.twittig.mite.mitesync.service.BookingProposalService;
import org.twittig.mite.mitesync.service.GitActivityEstimator;
import org.twittig.mite.mitesync.service.GitActivityResult;
import org.twittig.mite.mitesync.service.GitActivityService;
import org.twittig.mite.mitesync.service.GitCommit;
import org.twittig.mite.mitesync.service.GoogleCalendarService;
import org.twittig.mite.mitesync.service.MiteBookingService;
import org.twittig.mite.mitesync.web.model.BookingResultModel;
import org.twittig.mite.mitesync.web.model.CalendarEventModel;
import org.twittig.mite.mitesync.web.model.DailyReportModel;
import org.twittig.mite.mitesync.web.model.GitCommitModel;
import org.twittig.mite.mitesync.web.model.MiteEntryModel;
import org.twittig.mite.mitesync.web.model.PbiAssignmentModel;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;
import org.twittig.mite.mitesync.web.model.WorkItemModel;

/**
 * Orchestrates the daily-report workflow of the requested project profile: reads the profile's
 * sources, builds a proposal and books it.
 *
 * <p>Which sources are read depends on the profile's workflow type: {@code calendar-devops}
 * combines Google Calendar + Azure DevOps + already-booked Mite entries; {@code git-activity}
 * derives the proposal purely from local git history (the calendar/DevOps services are not
 * touched, keeping their lazy initialization untriggered).
 *
 * <p>The actual booking lives in a separate method to allow a manual review pause between
 * preview and book.
 */
@Component
public class DailyReportFacade {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final ProfileRegistry profileRegistry;
  private final GoogleCalendarService googleCalendarService;
  private final AzureDevOpsService azureDevOpsService;
  private final MiteBookingService miteBookingService;
  private final BookingProposalService bookingProposalService;
  private final GitActivityService gitActivityService;
  private final GitActivityEstimator gitActivityEstimator;

  public DailyReportFacade(
      ProfileRegistry profileRegistry,
      GoogleCalendarService googleCalendarService,
      AzureDevOpsService azureDevOpsService,
      MiteBookingService miteBookingService,
      BookingProposalService bookingProposalService,
      GitActivityService gitActivityService,
      GitActivityEstimator gitActivityEstimator) {
    this.profileRegistry = profileRegistry;
    this.googleCalendarService = googleCalendarService;
    this.azureDevOpsService = azureDevOpsService;
    this.miteBookingService = miteBookingService;
    this.bookingProposalService = bookingProposalService;
    this.gitActivityService = gitActivityService;
    this.gitActivityEstimator = gitActivityEstimator;
  }

  /** Builds the daily report for the default profile (legacy endpoints without a project). */
  public DailyReportModel preview(LocalDate date, PbiAssignmentModel pbiAssignment) {
    return preview(profileRegistry.defaultProfileKey(), date, pbiAssignment);
  }

  /** Builds the full daily report including the booking proposal for the given profile. */
  public DailyReportModel preview(String profileKey, LocalDate date, PbiAssignmentModel pbiAssignment) {
    Profile profile = profileRegistry.resolve(profileKey);
    return switch (profile.getWorkflowType()) {
      case CALENDAR_DEVOPS -> previewCalendarDevops(profile, date, pbiAssignment);
      case GIT_ACTIVITY -> previewGitActivity(profile, date, pbiAssignment);
    };
  }

  private DailyReportModel previewCalendarDevops(
      Profile profile, LocalDate date, PbiAssignmentModel pbiAssignment) {
    if (pbiAssignment.getMainPbiId() == null) {
      throw new MissingMainPbiException();
    }

    List<CalendarEventModel> events =
        googleCalendarService.getEventsForDay(date, profile.getRules().getRoundingStepMinutes());
    List<MiteEntryModel> alreadyBooked = miteBookingService.getEntriesForDate(profile, date);
    List<WorkItemModel> changedToday = azureDevOpsService.getWorkItemsChangedByMeOnDate(date);
    List<WorkItemModel> openItems = azureDevOpsService.getOpenWorkItemsAssignedToMe();

    List<ProposalEntryModel> proposal =
        bookingProposalService.buildProposal(profile, events, alreadyBooked, openItems, pbiAssignment);

    DailyReportModel m = newReport(date);
    m.setCalendarEvents(events);
    m.setAlreadyBookedInMite(alreadyBooked);
    m.setDevOpsActivityOnDate(changedToday);
    m.setOpenWorkItems(openItems);
    setProposal(m, proposal);
    return m;
  }

  private DailyReportModel previewGitActivity(
      Profile profile, LocalDate date, PbiAssignmentModel pbiAssignment) {
    GitActivityResult activity = gitActivityService.getCommitsForDay(profile.getGit(), date);
    List<GitCommit> commits = activity.commits();
    List<MiteEntryModel> alreadyBooked = miteBookingService.getEntriesForDate(profile, date);

    List<ProposalEntryModel> estimated =
        gitActivityEstimator.estimate(
            commits, profile.getGit(), profile.getRules().getRoundingStepMinutes());
    List<ProposalEntryModel> proposal =
        bookingProposalService.buildGitProposal(
            profile, estimated, alreadyBooked, pbiAssignment.getTargetHours());

    DailyReportModel m = newReport(date);
    m.setCalendarEvents(List.of());
    m.setDevOpsActivityOnDate(List.of());
    m.setOpenWorkItems(List.of());
    m.setAlreadyBookedInMite(alreadyBooked);
    m.setGitCommits(commits.stream().map(DailyReportFacade::toModel).toList());
    m.setWarnings(activity.warnings());
    setProposal(m, proposal);
    return m;
  }

  private static GitCommitModel toModel(GitCommit commit) {
    String time = commit.time().atZone(ZoneId.systemDefault()).toLocalTime().format(TIME);
    return new GitCommitModel(time, commit.author(), commit.subjectLine());
  }

  private static DailyReportModel newReport(LocalDate date) {
    DailyReportModel m = new DailyReportModel();
    m.setDate(date);
    m.setDayOfWeek(date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH));
    return m;
  }

  private static void setProposal(DailyReportModel m, List<ProposalEntryModel> proposal) {
    m.setProposal(proposal);
    m.setProposalTotalMinutes(proposal.stream().mapToInt(ProposalEntryModel::getMinutes).sum());
  }

  /** Books the supplied entries via the default profile (legacy endpoints without a project). */
  public BookingResultModel book(LocalDate date, List<ProposalEntryModel> entries) {
    return book(profileRegistry.defaultProfileKey(), date, entries);
  }

  /** Books the supplied entries into the Mite instance of the given profile. */
  public BookingResultModel book(String profileKey, LocalDate date, List<ProposalEntryModel> entries) {
    Profile profile = profileRegistry.resolve(profileKey);
    return miteBookingService.book(profile, date, entries);
  }
}

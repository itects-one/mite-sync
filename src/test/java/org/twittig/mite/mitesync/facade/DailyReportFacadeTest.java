package org.twittig.mite.mitesync.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.twittig.mite.mitesync.config.DailyReportProperties.Profile;
import org.twittig.mite.mitesync.config.DailyReportProperties.WorkflowType;
import org.twittig.mite.mitesync.config.ProfileRegistry;
import org.twittig.mite.mitesync.config.UnknownProfileException;
import org.twittig.mite.mitesync.service.AzureDevOpsService;
import org.twittig.mite.mitesync.service.BookingProposalService;
import org.twittig.mite.mitesync.service.GitActivityEstimator;
import org.twittig.mite.mitesync.service.GitActivityResult;
import org.twittig.mite.mitesync.service.GitActivityService;
import org.twittig.mite.mitesync.service.GitCommit;
import org.twittig.mite.mitesync.service.GitEstimate;
import org.twittig.mite.mitesync.service.GoogleCalendarService;
import org.twittig.mite.mitesync.service.MiteBookingService;
import org.twittig.mite.mitesync.service.WorkItemResult;
import org.twittig.mite.mitesync.web.model.BookingResultModel;
import org.twittig.mite.mitesync.web.model.CalendarEventModel;
import org.twittig.mite.mitesync.web.model.DailyReportModel;
import org.twittig.mite.mitesync.web.model.MiteEntryModel;
import org.twittig.mite.mitesync.web.model.PbiAssignmentModel;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;
import org.twittig.mite.mitesync.web.model.WorkItemModel;

@ExtendWith(MockitoExtension.class)
class DailyReportFacadeTest {

  @Mock private ProfileRegistry profileRegistry;
  @Mock private GoogleCalendarService googleCalendarService;
  @Mock private AzureDevOpsService azureDevOpsService;
  @Mock private MiteBookingService miteBookingService;
  @Mock private BookingProposalService bookingProposalService;
  @Mock private GitActivityService gitActivityService;
  @Mock private GitActivityEstimator gitActivityEstimator;
  @InjectMocks private DailyReportFacade facade;

  private Profile profile;

  @BeforeEach
  void setUp() {
    profile = new Profile();
    profile.setWorkflowType(WorkflowType.CALENDAR_DEVOPS);
    profile.getRules().setRoundingStepMinutes(15);
  }

  private void givenDefaultProfile() {
    when(profileRegistry.defaultProfileKey()).thenReturn("default");
    when(profileRegistry.resolve("default")).thenReturn(profile);
  }

  @Test
  void preview_populatesAllFields() {
    givenDefaultProfile();
    LocalDate date = LocalDate.of(2024, 3, 15);
    PbiAssignmentModel pbi = new PbiAssignmentModel();
    pbi.setMainPbiId(12345);

    CalendarEventModel event = new CalendarEventModel();
    event.setSummary("Standup");
    event.setMinutes(15);

    MiteEntryModel booked = new MiteEntryModel(1L, 60, "Previous", 11L, 22L);
    WorkItemModel changed = new WorkItemModel();
    WorkItemModel open = new WorkItemModel();
    ProposalEntryModel proposal = new ProposalEntryModel(375, "Dev work", "main-pbi-fill", 12345, "My PBI");

    when(googleCalendarService.getEventsForDay(date, 15)).thenReturn(List.of(event));
    when(miteBookingService.getEntriesForDate(profile, date)).thenReturn(List.of(booked));
    when(azureDevOpsService.getWorkItemsChangedByMeOnDate(date))
        .thenReturn(WorkItemResult.of(List.of(changed)));
    when(azureDevOpsService.getOpenWorkItemsAssignedToMe())
        .thenReturn(WorkItemResult.of(List.of(open)));
    when(bookingProposalService.buildProposal(eq(profile), any(), any(), any(), eq(pbi)))
        .thenReturn(List.of(proposal));

    DailyReportModel result = facade.preview(date, pbi);

    assertThat(result.getDate()).isEqualTo(date);
    assertThat(result.getDayOfWeek()).isNotBlank();
    assertThat(result.getCalendarEvents()).containsExactly(event);
    assertThat(result.getAlreadyBookedInMite()).containsExactly(booked);
    assertThat(result.getDevOpsActivityOnDate()).containsExactly(changed);
    assertThat(result.getOpenWorkItems()).containsExactly(open);
    assertThat(result.getProposal()).containsExactly(proposal);
    assertThat(result.getProposalTotalMinutes()).isEqualTo(375);
    assertThat(result.getWarnings()).isEmpty();
  }

  @Test
  void preview_calendarDevops_reportsFailuresOfBothDevOpsQueries() {
    givenDefaultProfile();
    LocalDate date = LocalDate.of(2024, 3, 15);
    PbiAssignmentModel pbi = new PbiAssignmentModel();
    pbi.setMainPbiId(12345);

    when(googleCalendarService.getEventsForDay(any(), anyInt())).thenReturn(List.of());
    when(miteBookingService.getEntriesForDate(eq(profile), any())).thenReturn(List.of());
    when(azureDevOpsService.getWorkItemsChangedByMeOnDate(any()))
        .thenReturn(new WorkItemResult(List.of(), List.of("changed-today query failed")));
    when(azureDevOpsService.getOpenWorkItemsAssignedToMe())
        .thenReturn(new WorkItemResult(List.of(), List.of("open-items query failed")));
    when(bookingProposalService.buildProposal(any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    DailyReportModel result = facade.preview(date, pbi);

    // Both queries can fail independently, and the empty report they leave behind would otherwise
    // look like a day without DevOps activity.
    assertThat(result.getWarnings())
        .containsExactly("changed-today query failed", "open-items query failed");
  }

  @Test
  void preview_passesProfileRoundingStepToCalendarService() {
    givenDefaultProfile();
    profile.getRules().setRoundingStepMinutes(30);
    LocalDate date = LocalDate.of(2024, 3, 15);
    PbiAssignmentModel pbi = new PbiAssignmentModel();
    pbi.setMainPbiId(12345);

    when(googleCalendarService.getEventsForDay(date, 30)).thenReturn(List.of());
    when(miteBookingService.getEntriesForDate(profile, date)).thenReturn(List.of());
    when(azureDevOpsService.getWorkItemsChangedByMeOnDate(date))
        .thenReturn(WorkItemResult.of(List.of()));
    when(azureDevOpsService.getOpenWorkItemsAssignedToMe())
        .thenReturn(WorkItemResult.of(List.of()));
    when(bookingProposalService.buildProposal(any(), any(), any(), any(), any())).thenReturn(List.of());

    facade.preview(date, pbi);

    verify(googleCalendarService).getEventsForDay(date, 30);
  }

  @Test
  void preview_emptyProposal_totalMinutesIsZero() {
    givenDefaultProfile();
    LocalDate date = LocalDate.of(2024, 3, 15);
    PbiAssignmentModel pbi = new PbiAssignmentModel();
    pbi.setMainPbiId(12345);

    when(googleCalendarService.getEventsForDay(any(), anyInt())).thenReturn(List.of());
    when(miteBookingService.getEntriesForDate(eq(profile), any())).thenReturn(List.of());
    when(azureDevOpsService.getWorkItemsChangedByMeOnDate(any()))
        .thenReturn(WorkItemResult.of(List.of()));
    when(azureDevOpsService.getOpenWorkItemsAssignedToMe())
        .thenReturn(WorkItemResult.of(List.of()));
    when(bookingProposalService.buildProposal(any(), any(), any(), any(), any())).thenReturn(List.of());

    DailyReportModel result = facade.preview(date, pbi);

    assertThat(result.getProposalTotalMinutes()).isEqualTo(0);
  }

  @Test
  void preview_resolvesTheRequestedProfile() {
    when(profileRegistry.resolve("alpha")).thenReturn(profile);
    LocalDate date = LocalDate.of(2024, 3, 15);
    PbiAssignmentModel pbi = new PbiAssignmentModel();
    pbi.setMainPbiId(12345);

    when(googleCalendarService.getEventsForDay(any(), anyInt())).thenReturn(List.of());
    when(miteBookingService.getEntriesForDate(eq(profile), any())).thenReturn(List.of());
    when(azureDevOpsService.getWorkItemsChangedByMeOnDate(any()))
        .thenReturn(WorkItemResult.of(List.of()));
    when(azureDevOpsService.getOpenWorkItemsAssignedToMe())
        .thenReturn(WorkItemResult.of(List.of()));
    when(bookingProposalService.buildProposal(any(), any(), any(), any(), any())).thenReturn(List.of());

    facade.preview("alpha", date, pbi);

    verify(profileRegistry).resolve("alpha");
  }

  @Test
  void preview_unknownProfile_propagatesException() {
    when(profileRegistry.resolve("nope")).thenThrow(new UnknownProfileException("nope"));

    assertThatThrownBy(() -> facade.preview("nope", LocalDate.now(), new PbiAssignmentModel()))
        .isInstanceOf(UnknownProfileException.class);
    verifyNoInteractions(googleCalendarService, azureDevOpsService, miteBookingService);
  }

  @Test
  void preview_calendarDevops_withoutMainPbi_throws() {
    when(profileRegistry.resolve("alpha")).thenReturn(profile);

    assertThatThrownBy(() -> facade.preview("alpha", LocalDate.now(), new PbiAssignmentModel()))
        .isInstanceOf(MissingMainPbiException.class);
    verifyNoInteractions(googleCalendarService, azureDevOpsService, miteBookingService);
  }

  // -------- git-activity workflow --------

  @Test
  void preview_gitActivity_composesReportWithoutTouchingCalendarOrDevOps() {
    profile.setWorkflowType(WorkflowType.GIT_ACTIVITY);
    when(profileRegistry.resolve("git")).thenReturn(profile);
    LocalDate date = LocalDate.of(2024, 3, 15);

    GitCommit commit =
        new GitCommit(
            date.atTime(9, 30).atZone(java.time.ZoneId.systemDefault()).toInstant(),
            "VC-1: Fix the thing",
            "Dev");
    MiteEntryModel booked = new MiteEntryModel(1L, 60, "Previous", 11L, 22L);
    ProposalEntryModel estimated = new ProposalEntryModel(60, "#VC-1 Fix the thing", "git", null, null);
    ProposalEntryModel proposed = new ProposalEntryModel(60, "#VC-1 Fix the thing", "git", null, null);

    when(gitActivityService.getCommitsForDay(profile.getGit(), date))
        .thenReturn(new GitActivityResult(List.of(commit), List.of("Repository 'x' could not be read: gone")));
    when(miteBookingService.getEntriesForDate(profile, date)).thenReturn(List.of(booked));
    when(gitActivityEstimator.estimate(List.of(commit), profile.getGit(), 15))
        .thenReturn(new GitEstimate(List.of(estimated), List.of("VC-0 has no ticket id")));
    when(bookingProposalService.buildGitProposal(profile, List.of(estimated), List.of(booked), null))
        .thenReturn(List.of(proposed));

    // No mainPbiId — not required for git-activity profiles
    DailyReportModel result = facade.preview("git", date, new PbiAssignmentModel());

    assertThat(result.getDate()).isEqualTo(date);
    assertThat(result.getProposal()).containsExactly(proposed);
    assertThat(result.getProposalTotalMinutes()).isEqualTo(60);
    assertThat(result.getAlreadyBookedInMite()).containsExactly(booked);
    assertThat(result.getCalendarEvents()).isEmpty();
    assertThat(result.getDevOpsActivityOnDate()).isEmpty();
    assertThat(result.getOpenWorkItems()).isEmpty();
    assertThat(result.getGitCommits()).hasSize(1);
    assertThat(result.getGitCommits().get(0).getTime()).isEqualTo("09:30");
    assertThat(result.getGitCommits().get(0).getSubject()).isEqualTo("VC-1: Fix the thing");
    // Both halves report: what could not be read, and what could not be attributed to a ticket.
    assertThat(result.getWarnings())
        .containsExactly("Repository 'x' could not be read: gone", "VC-0 has no ticket id");
    verifyNoInteractions(googleCalendarService, azureDevOpsService);
  }

  @Test
  void preview_gitActivity_passesTargetHoursOverrideToProposal() {
    profile.setWorkflowType(WorkflowType.GIT_ACTIVITY);
    when(profileRegistry.resolve("git")).thenReturn(profile);
    LocalDate date = LocalDate.of(2024, 3, 15);
    PbiAssignmentModel pbi = new PbiAssignmentModel();
    pbi.setTargetHours(4.0);

    when(gitActivityService.getCommitsForDay(any(), any()))
            .thenReturn(new GitActivityResult(List.of(), List.of()));
    when(miteBookingService.getEntriesForDate(eq(profile), any())).thenReturn(List.of());
    when(gitActivityEstimator.estimate(any(), any(), anyInt()))
        .thenReturn(new GitEstimate(List.of(), List.of()));
    when(bookingProposalService.buildGitProposal(any(), any(), any(), any())).thenReturn(List.of());

    facade.preview("git", date, pbi);

    verify(bookingProposalService).buildGitProposal(eq(profile), any(), any(), eq(4.0));
  }

  @Test
  void book_delegatesToMiteBookingServiceWithDefaultProfile() {
    givenDefaultProfile();
    LocalDate date = LocalDate.of(2024, 3, 15);
    List<ProposalEntryModel> entries = List.of(
        new ProposalEntryModel(90, "Work", "main-pbi-fill", 1, "PBI"));
    BookingResultModel expected = new BookingResultModel();
    expected.setDate(date);

    when(miteBookingService.book(profile, date, entries)).thenReturn(expected);

    BookingResultModel result = facade.book(date, entries);

    assertThat(result).isSameAs(expected);
    verify(miteBookingService).book(profile, date, entries);
  }

  @Test
  void book_resolvesTheRequestedProfile() {
    when(profileRegistry.resolve("alpha")).thenReturn(profile);
    LocalDate date = LocalDate.of(2024, 3, 15);
    List<ProposalEntryModel> entries = List.of(
        new ProposalEntryModel(90, "Work", "main-pbi-fill", 1, "PBI"));
    BookingResultModel expected = new BookingResultModel();

    when(miteBookingService.book(profile, date, entries)).thenReturn(expected);

    BookingResultModel result = facade.book("alpha", date, entries);

    assertThat(result).isSameAs(expected);
  }
}

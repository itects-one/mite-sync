package org.twittig.mite.mitesync.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.twittig.mite.mitesync.web.model.CalendarEventModel;
import org.twittig.mite.mitesync.web.model.EntrySource;
import org.twittig.mite.mitesync.web.model.GitCommitModel;
import org.twittig.mite.mitesync.web.model.MiteEntryModel;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;
import org.twittig.mite.mitesync.web.model.WorkItemModel;

class ProposalGuardTest {

  private static final String TICKET_PATTERN = "^([A-Z]+-\\d+)";
  private static final int TARGET = 375;
  private static final int TOLERANCE = 60;

  private ProposalGuard guard;

  @BeforeEach
  void setUp() {
    guard = new ProposalGuard();
  }

  // -------- Helpers --------

  private static ProposalEntryModel entry(int minutes, String note) {
    return new ProposalEntryModel(minutes, note, EntrySource.AGENT, null, null);
  }

  private static GitCommitModel commit(String subject) {
    return new GitCommitModel("09:30", "Dev", subject);
  }

  private static WorkItemModel workItem(int id) {
    WorkItemModel w = new WorkItemModel();
    w.setId(id);
    return w;
  }

  private static CalendarEventModel meeting(String summary) {
    CalendarEventModel e = new CalendarEventModel();
    e.setSummary(summary);
    return e;
  }

  private static DayEvidence evidence(
      List<CalendarEventModel> events,
      List<WorkItemModel> items,
      List<GitCommitModel> commits,
      List<MiteEntryModel> booked,
      List<String> allowed) {
    return new DayEvidence(events, items, commits, booked, allowed);
  }

  private static DayEvidence fromCommits(GitCommitModel... commits) {
    return evidence(List.of(), List.of(), List.of(commits), List.of(), List.of());
  }

  private GuardResult check(List<ProposalEntryModel> proposed, DayEvidence evidence) {
    return guard.check(proposed, evidence, TICKET_PATTERN, TARGET, TOLERANCE);
  }

  // -------- Tickets have to be backed by the day --------

  @Test
  void entryOnATicketFromTheDaysCommits_passes() {
    GuardResult result =
        check(List.of(entry(120, "#VC-1 Fix the thing")), fromCommits(commit("VC-1: Fix the thing")));

    assertThat(result.isPassed()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void entryOnATicketThatAppearsNowhere_isRejected() {
    // The failure worth guarding against: a plausible entry that books billed time onto work that
    // was never touched.
    GuardResult result =
        check(List.of(entry(120, "#VC-999 Fix the thing")), fromCommits(commit("VC-1: Real work")));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().get(0)).contains("#VC-999").contains("evidence");
  }

  @Test
  void workItemIdsCountAsEvidence() {
    DayEvidence evidence =
        evidence(List.of(), List.of(workItem(12345)), List.of(), List.of(), List.of());

    assertThat(check(List.of(entry(120, "#12345 Development")), evidence).isPassed()).isTrue();
  }

  @Test
  void configuredTicket_isAllowedAlthoughItIsAbsentFromTheDay() {
    // The main PBI and the fill-up ticket are configuration, not invention: booking onto them is
    // exactly what the profile asks for.
    DayEvidence evidence =
        evidence(
            List.of(meeting("Team Daily")), List.of(), List.of(), List.of(), List.of("12345"));

    assertThat(check(List.of(entry(120, "#12345 Development")), evidence).isPassed()).isTrue();
  }

  @Test
  void noteWithoutATicketReference_isNotCheckedForOne() {
    // git-activity profiles with a blank fallback-ticket produce exactly this; #21 warns about it
    // elsewhere, and it is not the guard's business.
    assertThat(check(List.of(entry(60, "update .gitignore")), fromCommits(commit("update .gitignore")))
            .isPassed())
        .isTrue();
  }

  @Test
  void customTicketPatternIsHonoured() {
    GuardResult result =
        guard.check(
            List.of(entry(60, "#4711 Fix encoding")),
            fromCommits(commit("#4711 Fix encoding")),
            "^#(\\d+)",
            TARGET,
            TOLERANCE);

    assertThat(result.isPassed()).isTrue();
  }

  // -------- Empty proposals --------

  @Test
  void emptyProposalOnADayWithCommits_isRejected() {
    GuardResult result = check(List.of(), fromCommits(commit("VC-1: Work"), commit("VC-2: More")));

    assertThat(result.isPassed()).isFalse();
    assertThat(result.violations().get(0)).contains("empty").contains("2 commit(s)");
  }

  @Test
  void emptyProposalOnAQuietDay_passes() {
    // Nothing happened, so proposing nothing is the correct answer.
    DayEvidence quiet = evidence(List.of(), List.of(), List.of(), List.of(), List.of());

    assertThat(check(List.of(), quiet).isPassed()).isTrue();
    assertThat(check(null, quiet).isPassed()).isTrue();
  }

  @Test
  void emptyProposalOnADayOfMeetings_isRejected() {
    DayEvidence evidence =
        evidence(List.of(meeting("Review")), List.of(), List.of(), List.of(), List.of());

    assertThat(check(List.of(), evidence).violations().get(0)).contains("1 calendar event(s)");
  }

  // -------- Per-entry sanity --------

  @Test
  void entryWithoutMinutes_isRejected() {
    GuardResult result = check(List.of(entry(0, "#VC-1 Work")), fromCommits(commit("VC-1: Work")));

    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().get(0)).contains("0 minutes");
  }

  @Test
  void entryLongerThanADay_isRejected() {
    // The Mite write API takes a short, so an absurd value would not stay absurd for long.
    GuardResult result =
        guard.check(
            List.of(entry(2000, "#VC-1 Work")), fromCommits(commit("VC-1: Work")), TICKET_PATTERN,
            TARGET, -1);

    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().get(0)).contains("full day");
  }

  @Test
  void entryWithoutANote_isRejected() {
    GuardResult result = check(List.of(entry(60, "  ")), fromCommits(commit("VC-1: Work")));

    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().get(0)).contains("no note");
  }

  // -------- Duplicates --------

  @Test
  void entryThatIsAlreadyBookedInMite_isRejected() {
    DayEvidence evidence =
        evidence(
            List.of(),
            List.of(),
            List.of(commit("VC-1: Work")),
            List.of(new MiteEntryModel(1L, 60, "  #VC-1 WORK  ", 11L, 22L)),
            List.of());

    GuardResult result = check(List.of(entry(60, "#VC-1 Work")), evidence);

    // Same rule as the rule-based duplicate guard: trimmed and case-insensitive.
    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().get(0)).contains("already booked");
  }

  // -------- Total against the daily target --------

  @Test
  void totalBeyondTargetPlusTolerance_isRejected() {
    GuardResult result =
        check(List.of(entry(440, "#VC-1 Work")), fromCommits(commit("VC-1: Work")));

    assertThat(result.violations()).hasSize(1);
    assertThat(result.violations().get(0)).contains("440").contains("375");
  }

  @Test
  void totalWithinTolerance_passes() {
    assertThat(check(List.of(entry(435, "#VC-1 Work")), fromCommits(commit("VC-1: Work"))).isPassed())
        .isTrue();
  }

  @Test
  void minutesAlreadyBookedCountTowardsTheTotal() {
    DayEvidence evidence =
        evidence(
            List.of(),
            List.of(),
            List.of(commit("VC-1: Work")),
            List.of(new MiteEntryModel(1L, 300, "Earlier work", 11L, 22L)),
            List.of());

    GuardResult result = check(List.of(entry(300, "#VC-1 Work")), evidence);

    assertThat(result.violations().get(0)).contains("600");
  }

  @Test
  void bookingLessThanTheTarget_isNeverReported() {
    // git-activity books only what the history shows; falling short of the target is normal.
    assertThat(check(List.of(entry(30, "#VC-1 Work")), fromCommits(commit("VC-1: Work"))).isPassed())
        .isTrue();
  }

  @Test
  void negativeToleranceTurnsTheTotalCheckOff() {
    // Long days are a fact of life on some profiles — the check has to be switchable off.
    GuardResult result =
        guard.check(
            List.of(entry(700, "#VC-1 Work")),
            fromCommits(commit("VC-1: Work")),
            TICKET_PATTERN,
            TARGET,
            -1);

    assertThat(result.isPassed()).isTrue();
  }

  // -------- Reporting --------

  @Test
  void everyProblemIsReported_notOnlyTheFirst() {
    GuardResult result =
        check(
            List.of(entry(0, "#VC-999 Invented"), entry(500, "#VC-1 Work")),
            fromCommits(commit("VC-1: Work")));

    // Zero minutes, an unknown ticket and a day over target — a caller that fixes one at a time
    // would come back three times.
    assertThat(result.violations()).hasSize(3);
  }
}

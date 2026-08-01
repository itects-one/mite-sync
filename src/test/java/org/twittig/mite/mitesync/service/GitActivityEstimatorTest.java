package org.twittig.mite.mitesync.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.twittig.mite.mitesync.config.DailyReportProperties.GitActivity;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;

class GitActivityEstimatorTest {

  private static final Instant T0 = Instant.parse("2026-06-10T09:00:00Z");

  private GitActivityEstimator estimator;
  private GitActivity config;

  @BeforeEach
  void setUp() {
    estimator = new GitActivityEstimator();
    config = new GitActivity(); // defaults: gap 90, lead-in 30, pattern ^([A-Z]+-\d+)
  }

  private static GitCommit commit(int minutesAfterT0, String message) {
    return new GitCommit(T0.plusSeconds(minutesAfterT0 * 60L), message, "Dev");
  }

  private List<ProposalEntryModel> estimate(GitCommit... commits) {
    return estimator.estimate(List.of(commits), config, 15).entries();
  }

  private List<String> warningsOf(GitCommit... commits) {
    return estimator.estimate(List.of(commits), config, 15).warnings();
  }

  // -------- Basics --------

  @Test
  void noCommits_returnsEmptyProposal() {
    assertThat(estimator.estimate(List.of(), config, 15).entries()).isEmpty();
    assertThat(estimator.estimate(null, config, 15).entries()).isEmpty();
    assertThat(estimator.estimate(null, config, 15).warnings()).isEmpty();
  }

  @Test
  void singleCommit_countsLeadInMinutes() {
    List<ProposalEntryModel> result = estimate(commit(0, "VC-1: Fix the thing"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getMinutes()).isEqualTo(30); // lead-in only
    assertThat(result.get(0).getNote()).isEqualTo("#VC-1 Fix the thing");
    assertThat(result.get(0).getSource()).isEqualTo("git");
  }

  @Test
  void sessionSpansFirstToLastCommit_plusLeadIn() {
    List<ProposalEntryModel> result =
        estimate(commit(0, "VC-1: Start"), commit(60, "VC-1: Finish"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getMinutes()).isEqualTo(90); // 60 span + 30 lead-in
  }

  @Test
  void noteUsesSubjectOfLatestCommit() {
    List<ProposalEntryModel> result =
        estimate(commit(0, "VC-1: Start"), commit(60, "VC-1: Finish"));

    assertThat(result.get(0).getNote()).isEqualTo("#VC-1 Finish");
  }

  // -------- Sessions --------

  @Test
  void gapLargerThanSessionGap_splitsIntoTwoSessions() {
    // 4 h gap → two single-commit sessions of 30 min lead-in each
    List<ProposalEntryModel> result =
        estimate(commit(0, "VC-1: Morning"), commit(240, "VC-1: Afternoon"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getMinutes()).isEqualTo(60); // 30 + 30
  }

  @Test
  void gapWithinSessionGap_staysOneSession() {
    // 89 min gap (≤ 90) → one session: 89 + 30 = 119 → rounded up to 120
    List<ProposalEntryModel> result =
        estimate(commit(0, "VC-1: Start"), commit(89, "VC-1: End"));

    assertThat(result.get(0).getMinutes()).isEqualTo(120);
  }

  // -------- Distribution across tickets --------

  @Test
  void sessionMinutesAreDistributedProportionallyToCommitCount() {
    // Session 09:00–10:00 + 30 lead-in = 90 min; VC-1 has 2 of 3 commits, VC-2 one
    List<ProposalEntryModel> result =
        estimate(
            commit(0, "VC-1: Part one"),
            commit(30, "VC-2: Other work"),
            commit(60, "VC-1: Part two"));

    assertThat(byNotePrefix(result, "#VC-1").getMinutes()).isEqualTo(60); // 90 * 2/3
    assertThat(byNotePrefix(result, "#VC-2").getMinutes()).isEqualTo(30); // 90 * 1/3
  }

  @Test
  void perTicketTotalsAreRoundedUpToTheStep() {
    // Session 09:00–09:50 + 30 = 80 min; VC-1: 53.3 → 60, VC-2: 26.7 → 30
    List<ProposalEntryModel> result =
        estimate(
            commit(0, "VC-1: Part one"),
            commit(25, "VC-2: Other work"),
            commit(50, "VC-1: Part two"));

    assertThat(byNotePrefix(result, "#VC-1").getMinutes()).isEqualTo(60);
    assertThat(byNotePrefix(result, "#VC-2").getMinutes()).isEqualTo(30);
  }

  @Test
  void ticketMinutesAccumulateAcrossSessions() {
    // Two sessions (gap 240): each 30 min lead-in for the same ticket → 60 total
    List<ProposalEntryModel> result =
        estimate(
            commit(0, "VC-1: Morning"),
            commit(240, "VC-1: Afternoon"),
            commit(250, "VC-2: Quick fix"));

    // Session 2: span 10 + 30 = 40; VC-1 1/2 → 20, VC-2 1/2 → 20
    assertThat(byNotePrefix(result, "#VC-1").getMinutes()).isEqualTo(60); // 30 + 20 → ceil 60
    assertThat(byNotePrefix(result, "#VC-2").getMinutes()).isEqualTo(30); // 20 → ceil 30
  }

  // -------- Ticket extraction --------

  @Test
  void commitWithoutTicket_usesFallbackTicket() {
    config.setFallbackTicket("MISC");

    List<ProposalEntryModel> result = estimate(commit(0, "Refactor build setup"));

    assertThat(result.get(0).getNote()).isEqualTo("#MISC Refactor build setup");
  }

  @Test
  void commitWithoutTicket_blankFallback_noteHasNoTicketPrefix() {
    List<ProposalEntryModel> result = estimate(commit(0, "Refactor build setup"));

    assertThat(result.get(0).getNote()).isEqualTo("Refactor build setup");
  }

  @Test
  void customTicketPattern_isApplied() {
    config.setTicketPattern("^#(\\d+)");

    List<ProposalEntryModel> result = estimate(commit(0, "#4711 Fix encoding"));

    assertThat(result.get(0).getNote()).isEqualTo("#4711 Fix encoding");
  }

  @Test
  void subjectIsFirstLineOfMultilineMessage() {
    List<ProposalEntryModel> result =
        estimate(commit(0, "VC-9: Subject line\n\nLong body\nwith details"));

    assertThat(result.get(0).getNote()).isEqualTo("#VC-9 Subject line");
  }

  @Test
  void messageThatIsOnlyATicketId_getsPlaceholderSubject() {
    List<ProposalEntryModel> result = estimate(commit(0, "VC-9"));

    assertThat(result.get(0).getNote()).isEqualTo("#VC-9 (no subject)");
  }

  // -------- Non-billable commits --------

  @Test
  void nonBillableCommit_getsNoEntryAndItsShareGoesToTheDaysTickets() {
    config.setNonBillablePatterns(List.of("^updating (develop )?poms"));

    // One session 09:00–10:00 + 30 lead-in = 90 min. Without the pattern VC-1 would only get
    // 90 * 2/3 = 60 and the release commit a 30 min entry of its own.
    List<ProposalEntryModel> result =
        estimate(
            commit(0, "VC-1: Part one"),
            commit(30, "updating poms for branch"),
            commit(60, "VC-1: Part two"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getNote()).isEqualTo("#VC-1 Part two");
    assertThat(result.get(0).getMinutes()).isEqualTo(90);
  }

  @Test
  void redistributionIsProportional_andKeepsTheDayTotal() {
    config.setNonBillablePatterns(List.of("^updating poms"));

    // 60 span + 30 lead-in = 90 min over three billable commits: VC-1 two, VC-2 one.
    List<ProposalEntryModel> result =
        estimate(
            commit(0, "VC-1: Part one"),
            commit(20, "VC-1: Part two"),
            commit(40, "VC-2: Other work"),
            commit(60, "updating poms for branch"));

    assertThat(byNotePrefix(result, "#VC-1").getMinutes()).isEqualTo(60);
    assertThat(byNotePrefix(result, "#VC-2").getMinutes()).isEqualTo(30);
    assertThat(result.stream().mapToInt(ProposalEntryModel::getMinutes).sum()).isEqualTo(90);
  }

  @Test
  void sessionOfOnlyNonBillableCommits_contributesNothing() {
    config.setNonBillablePatterns(List.of("^updating poms"));

    // Two sessions (gap 240): the first is release mechanics only, the second real work.
    List<ProposalEntryModel> result =
        estimate(commit(0, "updating poms for branch"), commit(240, "VC-1: Real work"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getNote()).isEqualTo("#VC-1 Real work");
    assertThat(result.get(0).getMinutes()).isEqualTo(30);
  }

  @Test
  void dayOfOnlyNonBillableCommits_producesNoEntriesAtAll() {
    config.setNonBillablePatterns(List.of("^updating poms"));

    assertThat(estimate(commit(0, "updating poms"), commit(30, "updating poms"))).isEmpty();
  }

  @Test
  void anchoredPatternDoesNotMatchInTheMiddleOfASubject() {
    config.setNonBillablePatterns(List.of("^updating poms"));

    List<ProposalEntryModel> result = estimate(commit(0, "VC-1: updating poms is not the point"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getNote()).isEqualTo("#VC-1 updating poms is not the point");
  }

  @Test
  void blankPatternsAreIgnored() {
    config.setNonBillablePatterns(List.of("  "));

    assertThat(estimate(commit(0, "VC-1: Work"))).hasSize(1);
  }

  // -------- Warning about entries without a ticket reference --------

  @Test
  void ticketlessEntry_isReportedWithItsNoteAndMinutes() {
    List<String> warnings = warningsOf(commit(0, "update .gitignore"));

    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0))
        .contains("1 commit has")
        .contains("\"update .gitignore\"")
        .contains("30 min")
        .contains("fallback-ticket");
  }

  @Test
  void severalTicketlessCommits_areReportedTogether() {
    List<String> warnings = warningsOf(commit(0, "update .gitignore"), commit(30, "fix a typo"));

    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0)).contains("2 commits have").contains("\"fix a typo\"");
  }

  @Test
  void ticketlessCommit_withFallbackTicket_isNotReported() {
    // The entry carries "#MISC", so nothing reaches Mite without a reference.
    config.setFallbackTicket("MISC");

    assertThat(warningsOf(commit(0, "update .gitignore"))).isEmpty();
  }

  @Test
  void ticketlessCommit_thatIsNonBillable_isNotReported() {
    // It produces no entry at all, so there is nothing to review.
    config.setNonBillablePatterns(List.of("^update \\.gitignore"));

    assertThat(warningsOf(commit(0, "update .gitignore"), commit(30, "VC-1: Work"))).isEmpty();
  }

  @Test
  void commitsWithTicketIds_produceNoWarning() {
    assertThat(warningsOf(commit(0, "VC-1: Work"), commit(30, "VC-2: More work"))).isEmpty();
  }

  private static ProposalEntryModel byNotePrefix(List<ProposalEntryModel> entries, String prefix) {
    return entries.stream()
        .filter(e -> e.getNote() != null && e.getNote().startsWith(prefix + " "))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No entry with note prefix: " + prefix));
  }
}

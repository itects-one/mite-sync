package org.twittig.mite.mitesync.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.twittig.mite.mitesync.config.DailyReportProperties.GitActivity;
import org.twittig.mite.mitesync.web.model.EntrySource;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;

/**
 * Pure logic: turns the commits of one day into per-ticket proposal entries with estimated
 * durations. No I/O — the commits come from {@link GitActivityService}.
 *
 * <p>Estimation heuristic:
 * <ol>
 *   <li>Commits are sorted chronologically and grouped into <b>sessions</b>: a gap larger than
 *       {@code session-gap-minutes} (default 90) between two consecutive commits starts a new
 *       session.
 *   <li>A session lasts from its first to its last commit, plus {@code lead-in-minutes} (default
 *       30) for the work leading up to the first commit. A single-commit session therefore counts
 *       {@code lead-in-minutes}.
 *   <li>Commits matching one of {@code non-billable-patterns} get no entry. They keep delimiting
 *       their session, so the minutes they would have claimed are redistributed over the session's
 *       remaining commits instead of vanishing — filtering them out earlier could split a session
 *       in two and change the whole day's estimate. A session made up of nothing but such commits
 *       contributes nothing.
 *   <li>The session duration is distributed across the tickets of its billable commits
 *       proportionally to their commit counts.
 *   <li>The ticket id is the first regex group of {@code ticket-pattern} matched against the
 *       start of the commit subject; commits without a match fall into the
 *       {@code fallback-ticket} bucket.
 *   <li>Per-ticket totals are rounded <b>up</b> to the rounding step, so the estimate may exceed
 *       the raw session time slightly. The entry note is {@code #<ticket> <subject>} (subject of
 *       the ticket's most recent commit, ticket prefix stripped) — without the {@code #<ticket>}
 *       prefix when the bucket has no ticket id, which is reported as a warning.
 * </ol>
 */
@Service
public class GitActivityEstimator {

  /** Builds per-ticket proposal entries (source {@code "git"}) from the day's commits. */
  public GitEstimate estimate(
      List<GitCommit> commits, GitActivity config, int roundingStepMinutes) {
    if (commits == null || commits.isEmpty()) {
      return new GitEstimate(List.of(), List.of());
    }

    List<GitCommit> sorted =
        commits.stream().sorted(java.util.Comparator.comparing(GitCommit::time)).toList();
    Pattern ticketPattern = Pattern.compile(config.getTicketPattern());
    List<Pattern> nonBillable = compile(config.getNonBillablePatterns());

    // Raw (unrounded) minutes, commit count and latest subject per ticket, in order of first
    // appearance
    Map<String, Double> minutesByTicket = new LinkedHashMap<>();
    Map<String, Integer> commitsByTicket = new LinkedHashMap<>();
    Map<String, GitCommit> latestCommitByTicket = new LinkedHashMap<>();

    for (List<GitCommit> session : splitIntoSessions(sorted, config.getSessionGapMinutes())) {
      double sessionMinutes =
          Duration.between(session.get(0).time(), session.get(session.size() - 1).time())
                  .toMillis()
              / 60_000.0
              + config.getLeadInMinutes();

      Map<String, Integer> commitCountByTicket = new LinkedHashMap<>();
      int billableCommits = 0;
      for (GitCommit commit : session) {
        if (matchesAny(commit.subjectLine(), nonBillable)) {
          continue;
        }
        billableCommits++;
        String ticket = extractTicket(commit, ticketPattern, config.getFallbackTicket());
        commitCountByTicket.merge(ticket, 1, Integer::sum);
        latestCommitByTicket.merge(
            ticket, commit, (a, b) -> a.time().isAfter(b.time()) ? a : b);
      }

      // Dividing by the billable count, not the session size, is what redistributes the skipped
      // commits' share. A session without a single billable commit is not billable at all.
      for (Map.Entry<String, Integer> e : commitCountByTicket.entrySet()) {
        double share = sessionMinutes * e.getValue() / billableCommits;
        minutesByTicket.merge(e.getKey(), share, Double::sum);
        commitsByTicket.merge(e.getKey(), e.getValue(), Integer::sum);
      }
    }

    List<ProposalEntryModel> entries = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    for (Map.Entry<String, Double> e : minutesByTicket.entrySet()) {
      int minutes = roundUpToStep(e.getValue(), roundingStepMinutes);
      if (minutes <= 0) {
        continue;
      }
      String ticket = e.getKey();
      String subject = subjectWithoutTicket(latestCommitByTicket.get(ticket), ticketPattern);
      String note = (ticket.isBlank() ? subject : "#" + ticket + " " + subject).strip();
      entries.add(new ProposalEntryModel(minutes, note, EntrySource.GIT, null, null));
      if (ticket.isBlank()) {
        warnings.add(ticketlessWarning(commitsByTicket.get(ticket), minutes, note));
      }
    }
    return new GitEstimate(entries, warnings);
  }

  /**
   * Such an entry is the easiest one to wave through: it looks like any other, and only the missing
   * {@code #} tells that it will reach the time-tracking system without a ticket reference.
   */
  private static String ticketlessWarning(int commitCount, int minutes, String note) {
    return commitCount
        + (commitCount == 1 ? " commit has" : " commits have")
        + " no recognizable ticket id — booked as \""
        + note
        + "\" ("
        + minutes
        + " min) without a ticket reference. Set 'fallback-ticket' or 'non-billable-patterns'"
        + " for this profile.";
  }

  private static List<Pattern> compile(List<String> regexes) {
    if (regexes == null) {
      return List.of();
    }
    return regexes.stream().filter(r -> r != null && !r.isBlank()).map(Pattern::compile).toList();
  }

  private static boolean matchesAny(String subject, List<Pattern> patterns) {
    return patterns.stream().anyMatch(p -> p.matcher(subject).find());
  }

  private static List<List<GitCommit>> splitIntoSessions(List<GitCommit> sorted, int gapMinutes) {
    List<List<GitCommit>> sessions = new ArrayList<>();
    List<GitCommit> current = new ArrayList<>();
    for (GitCommit commit : sorted) {
      if (!current.isEmpty()
          && Duration.between(current.get(current.size() - 1).time(), commit.time()).toMinutes()
              > gapMinutes) {
        sessions.add(current);
        current = new ArrayList<>();
      }
      current.add(commit);
    }
    sessions.add(current);
    return sessions;
  }

  private static String extractTicket(GitCommit commit, Pattern pattern, String fallbackTicket) {
    Matcher m = pattern.matcher(commit.subjectLine());
    if (m.find() && m.groupCount() >= 1 && m.group(1) != null) {
      return m.group(1);
    }
    return fallbackTicket == null ? "" : fallbackTicket;
  }

  /** Subject of the commit with the matched ticket prefix and separator characters removed. */
  private static String subjectWithoutTicket(GitCommit commit, Pattern pattern) {
    String subject = commit.subjectLine();
    Matcher m = pattern.matcher(subject);
    if (m.find()) {
      subject = subject.substring(m.end()).replaceFirst("^[\\s:,—-]+", "");
    }
    return subject.isBlank() ? "(no subject)" : subject;
  }

  private static int roundUpToStep(double minutes, int step) {
    if (minutes <= 0) {
      return 0;
    }
    return (int) (Math.ceil(minutes / step) * step);
  }
}

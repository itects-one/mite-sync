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
 *   <li>The session duration is distributed across the tickets of its commits proportionally to
 *       their commit counts.
 *   <li>The ticket id is the first regex group of {@code ticket-pattern} matched against the
 *       start of the commit subject; commits without a match fall into the
 *       {@code fallback-ticket} bucket.
 *   <li>Per-ticket totals are rounded <b>up</b> to the rounding step, so the estimate may exceed
 *       the raw session time slightly. The entry note is {@code #<ticket> <subject>} (subject of
 *       the ticket's most recent commit, ticket prefix stripped) — without the {@code #<ticket>}
 *       prefix when the bucket has no ticket id.
 * </ol>
 */
@Service
public class GitActivityEstimator {

  /** Builds per-ticket proposal entries (source {@code "git"}) from the day's commits. */
  public List<ProposalEntryModel> estimate(
      List<GitCommit> commits, GitActivity config, int roundingStepMinutes) {
    if (commits == null || commits.isEmpty()) {
      return List.of();
    }

    List<GitCommit> sorted =
        commits.stream().sorted(java.util.Comparator.comparing(GitCommit::time)).toList();
    Pattern ticketPattern = Pattern.compile(config.getTicketPattern());

    // Raw (unrounded) minutes and latest subject per ticket, in order of first appearance
    Map<String, Double> minutesByTicket = new LinkedHashMap<>();
    Map<String, GitCommit> latestCommitByTicket = new LinkedHashMap<>();

    for (List<GitCommit> session : splitIntoSessions(sorted, config.getSessionGapMinutes())) {
      double sessionMinutes =
          Duration.between(session.get(0).time(), session.get(session.size() - 1).time())
                  .toMillis()
              / 60_000.0
              + config.getLeadInMinutes();

      Map<String, Integer> commitCountByTicket = new LinkedHashMap<>();
      for (GitCommit commit : session) {
        String ticket = extractTicket(commit, ticketPattern, config.getFallbackTicket());
        commitCountByTicket.merge(ticket, 1, Integer::sum);
        latestCommitByTicket.merge(
            ticket, commit, (a, b) -> a.time().isAfter(b.time()) ? a : b);
      }

      for (Map.Entry<String, Integer> e : commitCountByTicket.entrySet()) {
        double share = sessionMinutes * e.getValue() / session.size();
        minutesByTicket.merge(e.getKey(), share, Double::sum);
      }
    }

    List<ProposalEntryModel> entries = new ArrayList<>();
    for (Map.Entry<String, Double> e : minutesByTicket.entrySet()) {
      int minutes = roundUpToStep(e.getValue(), roundingStepMinutes);
      if (minutes <= 0) {
        continue;
      }
      String ticket = e.getKey();
      String subject = subjectWithoutTicket(latestCommitByTicket.get(ticket), ticketPattern);
      String note = ticket.isBlank() ? subject : "#" + ticket + " " + subject;
      entries.add(new ProposalEntryModel(minutes, note.strip(), EntrySource.GIT, null, null));
    }
    return entries;
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

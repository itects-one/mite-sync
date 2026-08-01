package org.twittig.mite.mitesync.service;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.stereotype.Service;
import org.twittig.mite.mitesync.config.DailyReportProperties.GitActivity;

/**
 * Reads the commits of one day from the locally checked-out repositories of a profile. All
 * branches are considered (work usually happens on feature branches); commits reachable from
 * several refs are visited only once per repository.
 *
 * <p>Per-repository errors (missing path, corrupt repo) are skipped so one broken configuration
 * entry does not take down the whole preview — but they are reported back in
 * {@link GitActivityResult#warnings()} rather than only logged. The duration estimation lives in
 * {@link GitActivityEstimator} — this class is intentionally a thin I/O layer.
 */
@Service
public class GitActivityService {

  private static final Logger log = LogManager.getLogger(GitActivityService.class);

  /**
   * Returns the day's commits from all configured repositories, sorted chronologically, together
   * with a warning for every repository that could not be read.
   */
  public GitActivityResult getCommitsForDay(GitActivity config, LocalDate date) {
    ZoneId zone = ZoneId.systemDefault();
    Instant dayStart = date.atStartOfDay(zone).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();

    List<GitCommit> result = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    for (String repoPath : config.getRepositories()) {
      try (Repository repository =
              new FileRepositoryBuilder()
                  .setGitDir(new File(repoPath, ".git"))
                  .setMustExist(true)
                  .build();
          Git git = new Git(repository)) {
        Iterable<RevCommit> commits =
            git.log().all().setRevFilter(CommitTimeRevFilter.between(dayStart, dayEnd)).call();
        for (RevCommit commit : commits) {
          // Merge commits are not work of their own — the time already sits in the feature
          // commits they bring in. Skipping them also keeps their branch-slug subject lines
          // ("Merged in feature/...") out of the booking notes.
          if (commit.getParentCount() > 1) {
            continue;
          }
          Instant when = commit.getAuthorIdent().getWhenAsInstant();
          // The rev filter works on commit time; re-check the author time against the local day.
          if (when.isBefore(dayStart) || !when.isBefore(dayEnd)) {
            continue;
          }
          if (!matchesAuthor(commit, config.getAuthor())) {
            continue;
          }
          result.add(
              new GitCommit(when, commit.getFullMessage(), commit.getAuthorIdent().getName()));
        }
      } catch (Exception e) {
        log.warn("Skipping git repository '{}': {}", repoPath, e.getMessage());
        // Also handed back to the caller: a log line alone leaves a misconfigured path looking
        // like a day without commits.
        warnings.add("Repository '" + repoPath + "' could not be read: " + e.getMessage());
      }
    }
    result.sort(Comparator.comparing(GitCommit::time));
    return new GitActivityResult(result, warnings);
  }

  private boolean matchesAuthor(RevCommit commit, String authorFilter) {
    if (authorFilter == null || authorFilter.isBlank()) {
      return true;
    }
    String needle = authorFilter.toLowerCase(Locale.ROOT);
    String name = commit.getAuthorIdent().getName();
    String email = commit.getAuthorIdent().getEmailAddress();
    return (name != null && name.toLowerCase(Locale.ROOT).contains(needle))
        || (email != null && email.toLowerCase(Locale.ROOT).contains(needle));
  }
}

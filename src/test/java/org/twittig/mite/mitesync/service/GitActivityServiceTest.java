package org.twittig.mite.mitesync.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.twittig.mite.mitesync.config.DailyReportProperties.GitActivity;

/** Tests against real (temporary) git repositories built with JGit. */
class GitActivityServiceTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 10);
  private static final ZoneId ZONE = ZoneId.systemDefault();

  @TempDir Path tempDir;

  private GitActivityService service;
  private GitActivity config;

  @BeforeEach
  void setUp() {
    service = new GitActivityService();
    config = new GitActivity();
  }

  private static Instant at(LocalDate day, int hour, int minute) {
    return day.atTime(hour, minute).atZone(ZONE).toInstant();
  }

  private Git initRepo(String name) throws Exception {
    File dir = tempDir.resolve(name).toFile();
    Git git = Git.init().setDirectory(dir).call();
    config.getRepositories().add(dir.getAbsolutePath());
    return git;
  }

  private static void commit(Git git, String message, String author, String email, Instant time)
      throws Exception {
    PersonIdent ident = new PersonIdent(author, email, time, ZONE);
    git.commit()
        .setAllowEmpty(true)
        .setSign(false)
        .setAuthor(ident)
        .setCommitter(ident)
        .setMessage(message)
        .call();
  }

  /**
   * Creates a real two-parent merge commit on the current branch with a controlled timestamp.
   * JGit's {@code git.merge()} stamps the merge commit with the current wall-clock time, which
   * would fall outside the fixed test day — so the merge is built low-level instead.
   */
  private static void mergeCommit(
      Git git, String message, String email, Instant time, ObjectId parent1, ObjectId parent2)
      throws Exception {
    Repository repo = git.getRepository();
    PersonIdent ident = new PersonIdent("Dev", email, time, ZONE);
    try (RevWalk walk = new RevWalk(repo);
        ObjectInserter inserter = repo.newObjectInserter()) {
      RevCommit firstParent = walk.parseCommit(parent1);
      CommitBuilder builder = new CommitBuilder();
      builder.setTreeId(firstParent.getTree()); // no content change, reuse the first parent's tree
      builder.setParentIds(parent1, parent2);
      builder.setAuthor(ident);
      builder.setCommitter(ident);
      builder.setMessage(message);
      ObjectId mergeId = inserter.insert(builder);
      inserter.flush();
      RefUpdate refUpdate = repo.updateRef(repo.getFullBranch());
      refUpdate.setNewObjectId(mergeId);
      refUpdate.setForceUpdate(true);
      refUpdate.update();
    }
  }

  @Test
  void returnsCommitsOfTheDay_sortedChronologically() throws Exception {
    try (Git git = initRepo("repo")) {
      commit(git, "VC-2: Second", "Dev", "dev@example.org", at(DAY, 14, 0));
      commit(git, "VC-1: First", "Dev", "dev@example.org", at(DAY, 9, 0));
    }

    List<GitCommit> result = service.getCommitsForDay(config, DAY).commits();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).subjectLine()).isEqualTo("VC-1: First");
    assertThat(result.get(1).subjectLine()).isEqualTo("VC-2: Second");
    assertThat(result.get(0).author()).isEqualTo("Dev");
  }

  @Test
  void commitsOfOtherDays_areExcluded() throws Exception {
    try (Git git = initRepo("repo")) {
      commit(git, "VC-1: Yesterday", "Dev", "dev@example.org", at(DAY.minusDays(1), 23, 50));
      commit(git, "VC-2: Today", "Dev", "dev@example.org", at(DAY, 0, 10));
      commit(git, "VC-3: Tomorrow", "Dev", "dev@example.org", at(DAY.plusDays(1), 0, 5));
    }

    List<GitCommit> result = service.getCommitsForDay(config, DAY).commits();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).subjectLine()).isEqualTo("VC-2: Today");
  }

  @Test
  void authorFilter_excludesOtherCommitters() throws Exception {
    config.setAuthor("thomas");
    try (Git git = initRepo("repo")) {
      commit(git, "VC-1: Mine", "Thomas Dev", "thomas@example.org", at(DAY, 9, 0));
      commit(git, "VC-2: By name match", "THOMAS", "other@example.org", at(DAY, 10, 0));
      commit(git, "VC-3: By email match", "Someone", "thomas@example.org", at(DAY, 11, 0));
      commit(git, "VC-4: Not mine", "Colleague", "colleague@example.org", at(DAY, 12, 0));
    }

    List<GitCommit> result = service.getCommitsForDay(config, DAY).commits();

    assertThat(result)
        .extracting(GitCommit::subjectLine)
        .containsExactly("VC-1: Mine", "VC-2: By name match", "VC-3: By email match");
  }

  @Test
  void commitsFromAllConfiguredRepositories_areMerged() throws Exception {
    try (Git git = initRepo("repo-a")) {
      commit(git, "VC-1: In repo A", "Dev", "dev@example.org", at(DAY, 10, 0));
    }
    try (Git git = initRepo("repo-b")) {
      commit(git, "VC-2: In repo B", "Dev", "dev@example.org", at(DAY, 9, 0));
    }

    List<GitCommit> result = service.getCommitsForDay(config, DAY).commits();

    assertThat(result)
        .extracting(GitCommit::subjectLine)
        .containsExactly("VC-2: In repo B", "VC-1: In repo A");
  }

  @Test
  void commitsOnFeatureBranches_areFound() throws Exception {
    try (Git git = initRepo("repo")) {
      commit(git, "VC-1: On main", "Dev", "dev@example.org", at(DAY, 9, 0));
      git.checkout().setCreateBranch(true).setName("feature/VC-2").call();
      commit(git, "VC-2: On branch", "Dev", "dev@example.org", at(DAY, 10, 0));
    }

    List<GitCommit> result = service.getCommitsForDay(config, DAY).commits();

    assertThat(result)
        .extracting(GitCommit::subjectLine)
        .contains("VC-1: On main", "VC-2: On branch");
  }

  @Test
  void mergeCommits_areExcluded() throws Exception {
    try (Git git = initRepo("repo")) {
      commit(git, "VC-1: Base on main", "Dev", "dev@example.org", at(DAY, 9, 0));
      ObjectId mainTip = git.getRepository().resolve("HEAD");
      git.checkout().setCreateBranch(true).setName("feature/VC-2").call();
      commit(git, "VC-2: Feature work", "Dev", "dev@example.org", at(DAY, 10, 0));
      ObjectId featureTip = git.getRepository().resolve("HEAD");
      mergeCommit(
          git,
          "Merged in feature/VC-2-feature-work (pull request #1)",
          "dev@example.org",
          at(DAY, 11, 0),
          featureTip,
          mainTip);
    }

    List<GitCommit> result = service.getCommitsForDay(config, DAY).commits();

    assertThat(result)
        .extracting(GitCommit::subjectLine)
        .containsExactlyInAnyOrder("VC-1: Base on main", "VC-2: Feature work");
  }

  @Test
  void dayWithOnlyMergeCommits_returnsEmptyList() throws Exception {
    try (Git git = initRepo("repo")) {
      // Base and feature commits live on the day before; only the merge lands on DAY.
      commit(git, "VC-1: Base on main", "Dev", "dev@example.org", at(DAY.minusDays(1), 9, 0));
      ObjectId mainTip = git.getRepository().resolve("HEAD");
      git.checkout().setCreateBranch(true).setName("feature/VC-2").call();
      commit(git, "VC-2: Feature work", "Dev", "dev@example.org", at(DAY.minusDays(1), 10, 0));
      ObjectId featureTip = git.getRepository().resolve("HEAD");
      mergeCommit(
          git,
          "Merged in feature/VC-2-feature-work (pull request #1)",
          "dev@example.org",
          at(DAY, 11, 0),
          featureTip,
          mainTip);
    }

    assertThat(service.getCommitsForDay(config, DAY).commits()).isEmpty();
  }

  @Test
  void brokenRepositoryPath_isSkippedButReported() throws Exception {
    String missing = tempDir.resolve("does-not-exist").toString();
    config.getRepositories().add(missing);
    try (Git git = initRepo("repo")) {
      commit(git, "VC-1: Works", "Dev", "dev@example.org", at(DAY, 9, 0));
    }

    GitActivityResult result = service.getCommitsForDay(config, DAY);

    // The readable repository is still read — one broken path must not take down the preview.
    assertThat(result.commits()).hasSize(1);
    // But it no longer disappears silently: an empty day and a moved path used to look alike.
    assertThat(result.warnings()).hasSize(1);
    assertThat(result.warnings().get(0)).contains(missing);
  }

  @Test
  void readableRepositories_produceNoWarnings() throws Exception {
    try (Git git = initRepo("repo")) {
      commit(git, "VC-1: Works", "Dev", "dev@example.org", at(DAY, 9, 0));
    }

    assertThat(service.getCommitsForDay(config, DAY).warnings()).isEmpty();
  }

  @Test
  void everyRepositoryBroken_warnsForEachAndReturnsNoCommits() {
    String first = tempDir.resolve("gone-a").toString();
    String second = tempDir.resolve("gone-b").toString();
    config.getRepositories().add(first);
    config.getRepositories().add(second);

    GitActivityResult result = service.getCommitsForDay(config, DAY);

    assertThat(result.commits()).isEmpty();
    assertThat(result.warnings()).hasSize(2);
    assertThat(result.warnings()).anyMatch(w -> w.contains(first));
    assertThat(result.warnings()).anyMatch(w -> w.contains(second));
  }

  @Test
  void noRepositoriesConfigured_returnsEmptyResult() {
    GitActivityResult result = service.getCommitsForDay(config, DAY);

    assertThat(result.commits()).isEmpty();
    assertThat(result.warnings()).isEmpty();
  }
}

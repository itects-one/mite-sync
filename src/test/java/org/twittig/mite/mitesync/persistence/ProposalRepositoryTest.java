package org.twittig.mite.mitesync.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class ProposalRepositoryTest {

  @Autowired ProposalRepository repository;

  @Test
  void savesAndLoadsEntriesInOrder() {
    Proposal p = new Proposal("default", LocalDate.of(2026, 7, 20), ProposalStatus.DRAFT);
    p.setCreatedAt(Instant.now());
    p.replaceEntries(
        List.of(
            new ProposalEntry(15, "#595 Daily", "calendar", 595, "Daily"),
            new ProposalEntry(300, "#655 Dev", "dev", 655, "Dev")));

    Proposal saved = repository.saveAndFlush(p);
    repository.flush();

    Proposal found = repository.findById(saved.getId()).orElseThrow();
    assertThat(found.getEntries()).extracting(ProposalEntry::getMinutes).containsExactly(15, 300);
    assertThat(found.totalMinutes()).isEqualTo(315);
  }

  @Test
  void findsExistingDraftByProfileAndDate() {
    LocalDate date = LocalDate.of(2026, 7, 20);
    repository.save(new Proposal("default", date, ProposalStatus.DRAFT));

    assertThat(repository.findByProfileKeyAndReportDateAndStatus("default", date, ProposalStatus.DRAFT))
        .isPresent();
    assertThat(repository.findByProfileKeyAndReportDateAndStatus("default", date, ProposalStatus.BOOKED))
        .isEmpty();
    assertThat(repository.findByProfileKeyAndReportDateAndStatus("other", date, ProposalStatus.DRAFT))
        .isEmpty();
  }

  @Test
  void listsNewestReportDateFirst() {
    repository.save(new Proposal("default", LocalDate.of(2026, 7, 20), ProposalStatus.DRAFT));
    repository.save(new Proposal("default", LocalDate.of(2026, 7, 22), ProposalStatus.DRAFT));
    repository.save(new Proposal("default", LocalDate.of(2026, 7, 21), ProposalStatus.DRAFT));

    List<Proposal> all = repository.findAllByOrderByReportDateDescIdDesc();

    assertThat(all)
        .extracting(Proposal::getReportDate)
        .containsExactly(
            LocalDate.of(2026, 7, 22), LocalDate.of(2026, 7, 21), LocalDate.of(2026, 7, 20));
  }
}

package org.twittig.mite.mitesync.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Proposal}. */
public interface ProposalRepository extends JpaRepository<Proposal, Long> {

  /** Inbox order: newest report date first, newest proposal first within a date. */
  List<Proposal> findAllByOrderByReportDateDescIdDesc();

  /** Existing draft for a (profile, date) — used to upsert on regeneration. */
  Optional<Proposal> findByProfileKeyAndReportDateAndStatus(
      String profileKey, LocalDate reportDate, ProposalStatus status);
}

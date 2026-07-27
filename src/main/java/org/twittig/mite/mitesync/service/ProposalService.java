package org.twittig.mite.mitesync.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.twittig.mite.mitesync.facade.DailyReportFacade;
import org.twittig.mite.mitesync.persistence.Proposal;
import org.twittig.mite.mitesync.persistence.ProposalRepository;
import org.twittig.mite.mitesync.persistence.ProposalStatus;
import org.twittig.mite.mitesync.web.model.BookingResultModel;
import org.twittig.mite.mitesync.web.model.ConfirmResultModel;
import org.twittig.mite.mitesync.web.model.DailyReportModel;
import org.twittig.mite.mitesync.web.model.PbiAssignmentModel;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;
import org.twittig.mite.mitesync.web.model.ProposalModel;

/**
 * Owns the proposal lifecycle. Reuses the existing {@link DailyReportFacade} for the actual work:
 * {@code preview} to generate the proposal entries and {@code book} to confirm them into Mite. The
 * service only persists the result and enforces the state machine (edit/confirm require DRAFT).
 */
@Service
@Transactional
public class ProposalService {

  private final DailyReportFacade facade;
  private final ProposalRepository repository;
  private final ProposalMapper mapper;

  public ProposalService(
      DailyReportFacade facade, ProposalRepository repository, ProposalMapper mapper) {
    this.facade = facade;
    this.repository = repository;
    this.mapper = mapper;
  }

  /**
   * Generates (or regenerates) the DRAFT proposal for a (profile, date) using the existing preview
   * pipeline. An existing DRAFT for the same day is overwritten in place; booked proposals are kept
   * as history.
   */
  public ProposalModel generate(String profileKey, LocalDate date, PbiAssignmentModel assignment) {
    DailyReportModel report = facade.preview(profileKey, date, assignment);

    Proposal proposal =
        repository
            .findByProfileKeyAndReportDateAndStatus(profileKey, date, ProposalStatus.DRAFT)
            .orElseGet(() -> newDraft(profileKey, date));

    proposal.setMainPbiId(assignment.getMainPbiId());
    proposal.setTargetHours(assignment.getTargetHours());
    proposal.replaceEntries(mapper.toEntryEntities(report.getProposal()));
    proposal.setUpdatedAt(Instant.now());

    return mapper.toModel(repository.save(proposal));
  }

  public List<ProposalModel> list() {
    return repository.findAllByOrderByReportDateDescIdDesc().stream().map(mapper::toModel).toList();
  }

  public ProposalModel get(Long id) {
    return mapper.toModel(load(id));
  }

  /** Replaces the entries of a DRAFT proposal (manual edit before confirmation). */
  public ProposalModel editEntries(Long id, List<ProposalEntryModel> entries) {
    Proposal proposal = load(id);
    requireDraft(proposal);
    proposal.replaceEntries(mapper.toEntryEntities(entries));
    proposal.setUpdatedAt(Instant.now());
    return mapper.toModel(repository.save(proposal));
  }

  /**
   * Confirms a DRAFT proposal: books its entries into the profile's Mite instance via the existing
   * best-effort facade and records the resulting status.
   */
  public ConfirmResultModel confirm(Long id) {
    Proposal proposal = load(id);
    requireDraft(proposal);

    List<ProposalEntryModel> entries =
        proposal.getEntries().stream().map(mapper::toEntryModel).toList();
    BookingResultModel result = facade.book(proposal.getProfileKey(), proposal.getReportDate(), entries);

    proposal.setStatus(deriveStatus(result));
    proposal.setBookedAt(Instant.now());
    proposal.setUpdatedAt(Instant.now());
    Proposal saved = repository.save(proposal);

    return new ConfirmResultModel(mapper.toModel(saved), result);
  }

  public void delete(Long id) {
    repository.delete(load(id));
  }

  private Proposal load(Long id) {
    return repository.findById(id).orElseThrow(() -> new UnknownProposalException(id));
  }

  private void requireDraft(Proposal proposal) {
    if (proposal.getStatus() != ProposalStatus.DRAFT) {
      throw new IllegalProposalStateException(
          proposal.getId(), proposal.getStatus(), ProposalStatus.DRAFT);
    }
  }

  private Proposal newDraft(String profileKey, LocalDate date) {
    Proposal proposal = new Proposal(profileKey, date, ProposalStatus.DRAFT);
    proposal.setCreatedAt(Instant.now());
    return proposal;
  }

  private static ProposalStatus deriveStatus(BookingResultModel result) {
    boolean anyCreated = result.getCreated() != null && !result.getCreated().isEmpty();
    boolean anyFailed = result.getFailed() != null && !result.getFailed().isEmpty();
    if (anyFailed && anyCreated) {
      return ProposalStatus.PARTIALLY_BOOKED;
    }
    if (anyFailed) {
      return ProposalStatus.FAILED;
    }
    return ProposalStatus.BOOKED;
  }
}

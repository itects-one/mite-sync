package org.twittig.mite.mitesync.web.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.twittig.mite.mitesync.service.ProposalService;
import org.twittig.mite.mitesync.web.model.BookingRequestModel;
import org.twittig.mite.mitesync.web.model.ConfirmResultModel;
import org.twittig.mite.mitesync.web.model.PbiAssignmentModel;
import org.twittig.mite.mitesync.web.model.ProposalModel;

/**
 * REST controller for the proposal store (the review inbox).
 *
 * <p>Workflow:
 * <ol>
 *   <li>Generate a proposal for a (project, date) — it is stored as a DRAFT.
 *   <li>Review the inbox, optionally edit the entries.
 *   <li>Confirm — the entries are booked into Mite and the proposal is marked BOOKED.
 * </ol>
 *
 * <p>Generation mirrors {@link DailyReportController}: the project path segment selects the profile
 * and the date is an ISO path variable (YYYY-MM-DD). All other operations are id-based.
 */
@RestController
@RequestMapping("/proposals")
public class ProposalController {

  private static final Logger log = LogManager.getLogger(ProposalController.class);
  private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

  private final ProposalService service;

  public ProposalController(ProposalService service) {
    this.service = service;
  }

  /** Lists all stored proposals (inbox), newest report date first. */
  @GetMapping
  public ResponseEntity<List<ProposalModel>> list() {
    return ResponseEntity.ok(service.list());
  }

  /** Returns a single proposal by id. */
  @GetMapping("/{id}")
  public ResponseEntity<ProposalModel> get(@PathVariable("id") Long id) {
    return ResponseEntity.ok(service.get(id));
  }

  /**
   * Generates (or regenerates) the DRAFT proposal for the given project profile and date. The date
   * is an ISO path variable; the PBI assignment is in the request body.
   */
  @PostMapping("/{project}/{date}")
  public ResponseEntity<ProposalModel> generate(
      @PathVariable("project") String project,
      @PathVariable("date") String dateStr,
      @RequestBody @Valid PbiAssignmentModel assignment) {
    LocalDate date = LocalDate.parse(dateStr, ISO);
    log.info(
        "Proposal generate requested for {} (project={}, mainPbi={})",
        date,
        project,
        assignment.getMainPbiId());
    return ResponseEntity.ok(service.generate(project, date, assignment));
  }

  /** Replaces the entries of a DRAFT proposal (manual edit before confirmation). */
  @PutMapping("/{id}/entries")
  public ResponseEntity<ProposalModel> editEntries(
      @PathVariable("id") Long id, @RequestBody @Valid BookingRequestModel request) {
    log.info("Proposal {} edit requested ({} entries)", id, request.getEntries().size());
    return ResponseEntity.ok(service.editEntries(id, request.getEntries()));
  }

  /** Confirms a DRAFT proposal — books its entries into Mite. */
  @PostMapping("/{id}/confirm")
  public ResponseEntity<ConfirmResultModel> confirm(@PathVariable("id") Long id) {
    log.info("Proposal {} confirm requested", id);
    ConfirmResultModel result = service.confirm(id);
    log.info(
        "Proposal {} confirmed: status={}, {} created, {} failed",
        id,
        result.getProposal().getStatus(),
        result.getBooking().getCreated().size(),
        result.getBooking().getFailed().size());
    return ResponseEntity.ok(result);
  }

  /** Deletes a proposal. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}

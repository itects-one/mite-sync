package org.twittig.mite.mitesync.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.twittig.mite.mitesync.facade.DailyReportFacade;
import org.twittig.mite.mitesync.persistence.Proposal;
import org.twittig.mite.mitesync.persistence.ProposalEntry;
import org.twittig.mite.mitesync.persistence.ProposalRepository;
import org.twittig.mite.mitesync.persistence.ProposalStatus;
import org.twittig.mite.mitesync.web.model.BookingResultModel;
import org.twittig.mite.mitesync.web.model.ConfirmResultModel;
import org.twittig.mite.mitesync.web.model.DailyReportModel;
import org.twittig.mite.mitesync.web.model.EntrySource;
import org.twittig.mite.mitesync.web.model.MiteEntryModel;
import org.twittig.mite.mitesync.web.model.PbiAssignmentModel;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;
import org.twittig.mite.mitesync.web.model.ProposalModel;

@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 7, 20);

  @Mock DailyReportFacade facade;
  @Mock ProposalRepository repository;

  private ProposalService service;

  @BeforeEach
  void setUp() {
    service = new ProposalService(facade, repository, new ProposalMapper());
    // repository.save returns the passed entity unchanged (lenient: not every test saves)
    lenient().when(repository.save(any(Proposal.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  // -------- generate --------

  @Test
  void generate_createsNewDraft_whenNoneExists() {
    DailyReportModel report =
        reportWith(new ProposalEntryModel(120, "#1 x", EntrySource.MAIN_PBI_FILL, 1, "x"));
    when(facade.preview(eq("default"), eq(DATE), any())).thenReturn(report);
    when(repository.findByProfileKeyAndReportDateAndStatus("default", DATE, ProposalStatus.DRAFT))
        .thenReturn(Optional.empty());

    ProposalModel m = service.generate("default", DATE, assignment(1, 6.25));

    assertThat(m.getStatus()).isEqualTo("DRAFT");
    assertThat(m.getEntries()).hasSize(1);
    assertThat(m.getTotalMinutes()).isEqualTo(120);
  }

  @Test
  void generate_overwritesEntriesOfExistingDraft() {
    Proposal existing = new Proposal("default", DATE, ProposalStatus.DRAFT);
    existing.setId(5L);
    existing.replaceEntries(
        List.of(new ProposalEntry(60, "old", EntrySource.MAIN_PBI_FILL, null, null)));
    when(facade.preview(eq("default"), eq(DATE), any()))
        .thenReturn(
            reportWith(
                new ProposalEntryModel(300, "#2 new", EntrySource.MAIN_PBI_FILL, 2, "new")));
    when(repository.findByProfileKeyAndReportDateAndStatus("default", DATE, ProposalStatus.DRAFT))
        .thenReturn(Optional.of(existing));

    ProposalModel m = service.generate("default", DATE, assignment(2, null));

    assertThat(m.getId()).isEqualTo(5L);
    assertThat(m.getEntries()).hasSize(1);
    assertThat(m.getEntries().get(0).getNote()).isEqualTo("#2 new");
    assertThat(m.getTotalMinutes()).isEqualTo(300);
  }

  // -------- get / list --------

  @Test
  void get_unknownId_throws() {
    when(repository.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(99L)).isInstanceOf(UnknownProposalException.class);
  }

  @Test
  void list_mapsAll() {
    Proposal p = new Proposal("default", DATE, ProposalStatus.DRAFT);
    p.setId(1L);
    when(repository.findAllByOrderByReportDateDescIdDesc()).thenReturn(List.of(p));
    assertThat(service.list()).hasSize(1);
  }

  // -------- editEntries --------

  @Test
  void editEntries_replacesEntriesOfDraft() {
    Proposal p = draftWithId(3L);
    p.replaceEntries(List.of(new ProposalEntry(60, "old", EntrySource.GIT, null, null)));
    when(repository.findById(3L)).thenReturn(Optional.of(p));

    ProposalModel m =
        service.editEntries(
            3L, List.of(new ProposalEntryModel(200, "#9 edited", EntrySource.GIT, 9, "e")));

    assertThat(m.getEntries()).hasSize(1);
    assertThat(m.getEntries().get(0).getNote()).isEqualTo("#9 edited");
    assertThat(m.getTotalMinutes()).isEqualTo(200);
  }

  @Test
  void editEntries_rejectsNonDraft() {
    Proposal p = draftWithId(4L);
    p.setStatus(ProposalStatus.BOOKED);
    when(repository.findById(4L)).thenReturn(Optional.of(p));

    assertThatThrownBy(
            () ->
                service.editEntries(
                    4L, List.of(new ProposalEntryModel(10, "x", EntrySource.GIT, null, null))))
        .isInstanceOf(IllegalProposalStateException.class);
  }

  // -------- editEntries: provenance --------

  @Test
  void editEntries_keepsSourceOfUntouchedEntry() {
    Proposal p = draftWithId(20L);
    p.replaceEntries(List.of(new ProposalEntry(45, "#VC-1 Fix", EntrySource.GIT, null, null)));
    when(repository.findById(20L)).thenReturn(Optional.of(p));

    // The client sends the entry back unchanged and without a source — the common UI round-trip.
    ProposalModel m =
        service.editEntries(20L, List.of(new ProposalEntryModel(45, "#VC-1 Fix", null, null, null)));

    assertThat(m.getEntries().get(0).getSource()).isEqualTo(EntrySource.GIT);
  }

  @Test
  void editEntries_marksChangedMinutesAsManual() {
    Proposal p = draftWithId(21L);
    p.replaceEntries(List.of(new ProposalEntry(45, "#VC-1 Fix", EntrySource.GIT, null, null)));
    when(repository.findById(21L)).thenReturn(Optional.of(p));

    ProposalModel m =
        service.editEntries(21L, List.of(new ProposalEntryModel(60, "#VC-1 Fix", null, null, null)));

    assertThat(m.getEntries().get(0).getSource()).isEqualTo(EntrySource.MANUAL);
  }

  @Test
  void editEntries_marksChangedNoteAsManual() {
    Proposal p = draftWithId(22L);
    p.replaceEntries(List.of(new ProposalEntry(45, "#VC-1 Fix", EntrySource.GIT, null, null)));
    when(repository.findById(22L)).thenReturn(Optional.of(p));

    ProposalModel m =
        service.editEntries(
            22L, List.of(new ProposalEntryModel(45, "#VC-1 Fix properly", null, null, null)));

    assertThat(m.getEntries().get(0).getSource()).isEqualTo(EntrySource.MANUAL);
  }

  @Test
  void editEntries_marksAddedEntryAsManual_andKeepsTheOtherOne() {
    Proposal p = draftWithId(23L);
    p.replaceEntries(List.of(new ProposalEntry(45, "#VC-1 Fix", EntrySource.GIT, null, null)));
    when(repository.findById(23L)).thenReturn(Optional.of(p));

    ProposalModel m =
        service.editEntries(
            23L,
            List.of(
                new ProposalEntryModel(45, "#VC-1 Fix", null, null, null),
                new ProposalEntryModel(30, "#VC-2 Review", null, null, null)));

    assertThat(m.getEntries()).hasSize(2);
    assertThat(m.getEntries().get(0).getSource()).isEqualTo(EntrySource.GIT);
    assertThat(m.getEntries().get(1).getSource()).isEqualTo(EntrySource.MANUAL);
  }

  @Test
  void editEntries_ignoresClientSuppliedSource() {
    Proposal p = draftWithId(24L);
    p.replaceEntries(List.of(new ProposalEntry(45, "#VC-1 Fix", EntrySource.GIT, null, null)));
    when(repository.findById(24L)).thenReturn(Optional.of(p));

    // A hand-written entry claiming to be derived from git history must not be believed.
    ProposalModel m =
        service.editEntries(
            24L, List.of(new ProposalEntryModel(90, "#VC-9 Invented", EntrySource.GIT, null, null)));

    assertThat(m.getEntries().get(0).getSource()).isEqualTo(EntrySource.MANUAL);
  }

  @Test
  void editEntries_matchesEachStoredEntryOnlyOnce() {
    Proposal p = draftWithId(25L);
    p.replaceEntries(List.of(new ProposalEntry(45, "#VC-1 Fix", EntrySource.GIT, null, null)));
    when(repository.findById(25L)).thenReturn(Optional.of(p));

    // Duplicating an entry keeps one as generated; the copy is a human addition.
    ProposalModel m =
        service.editEntries(
            25L,
            List.of(
                new ProposalEntryModel(45, "#VC-1 Fix", null, null, null),
                new ProposalEntryModel(45, "#VC-1 Fix", null, null, null)));

    assertThat(m.getEntries().get(0).getSource()).isEqualTo(EntrySource.GIT);
    assertThat(m.getEntries().get(1).getSource()).isEqualTo(EntrySource.MANUAL);
  }

  @Test
  void editEntries_treatsDifferentPbiAsManual() {
    Proposal p = draftWithId(26L);
    p.replaceEntries(
        List.of(new ProposalEntry(120, "#1 Work", EntrySource.MAIN_PBI_FILL, 1, "Work")));
    when(repository.findById(26L)).thenReturn(Optional.of(p));

    ProposalModel m =
        service.editEntries(
            26L, List.of(new ProposalEntryModel(120, "#1 Work", null, 2, "Work")));

    assertThat(m.getEntries().get(0).getSource()).isEqualTo(EntrySource.MANUAL);
  }

  // -------- confirm --------

  @Test
  void confirm_marksBooked_whenNoFailures() {
    Proposal p = draftWithBookableEntry(7L);
    when(repository.findById(7L)).thenReturn(Optional.of(p));
    when(facade.book(eq("default"), eq(DATE), anyList())).thenReturn(bookingResult(1, 0));

    ConfirmResultModel cr = service.confirm(7L);

    assertThat(cr.getProposal().getStatus()).isEqualTo("BOOKED");
    assertThat(cr.getProposal().getBookedAt()).isNotNull();
    assertThat(cr.getBooking().getCreated()).hasSize(1);
  }

  @Test
  void confirm_marksPartiallyBooked_whenSomeFail() {
    Proposal p = draftWithBookableEntry(8L);
    when(repository.findById(8L)).thenReturn(Optional.of(p));
    when(facade.book(eq("default"), eq(DATE), anyList())).thenReturn(bookingResult(1, 1));

    assertThat(service.confirm(8L).getProposal().getStatus()).isEqualTo("PARTIALLY_BOOKED");
  }

  @Test
  void confirm_marksFailed_whenAllFail() {
    Proposal p = draftWithBookableEntry(9L);
    when(repository.findById(9L)).thenReturn(Optional.of(p));
    when(facade.book(eq("default"), eq(DATE), anyList())).thenReturn(bookingResult(0, 2));

    assertThat(service.confirm(9L).getProposal().getStatus()).isEqualTo("FAILED");
  }

  @Test
  void confirm_rejectsNonDraft_andDoesNotBook() {
    Proposal p = draftWithId(10L);
    p.setStatus(ProposalStatus.BOOKED);
    when(repository.findById(10L)).thenReturn(Optional.of(p));

    assertThatThrownBy(() -> service.confirm(10L))
        .isInstanceOf(IllegalProposalStateException.class);
    verify(facade, never()).book(any(), any(), anyList());
  }

  // -------- delete --------

  @Test
  void delete_unknownId_throws() {
    when(repository.findById(11L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete(11L)).isInstanceOf(UnknownProposalException.class);
  }

  @Test
  void delete_existing_removesIt() {
    Proposal p = draftWithId(12L);
    when(repository.findById(12L)).thenReturn(Optional.of(p));
    service.delete(12L);
    verify(repository).delete(p);
  }

  // -------- helpers --------

  private static DailyReportModel reportWith(ProposalEntryModel... entries) {
    DailyReportModel report = new DailyReportModel();
    report.setProposal(List.of(entries));
    return report;
  }

  private static PbiAssignmentModel assignment(Integer mainPbi, Double targetHours) {
    PbiAssignmentModel a = new PbiAssignmentModel();
    a.setMainPbiId(mainPbi);
    a.setTargetHours(targetHours);
    return a;
  }

  private static Proposal draftWithId(long id) {
    Proposal p = new Proposal("default", DATE, ProposalStatus.DRAFT);
    p.setId(id);
    return p;
  }

  private static Proposal draftWithBookableEntry(long id) {
    Proposal p = draftWithId(id);
    p.replaceEntries(List.of(new ProposalEntry(120, "#1 x", EntrySource.MAIN_PBI_FILL, 1, "x")));
    return p;
  }

  private static BookingResultModel bookingResult(int created, int failed) {
    BookingResultModel r = new BookingResultModel();
    r.setDate(DATE);
    r.setCreated(
        java.util.stream.IntStream.range(0, created)
            .mapToObj(i -> new MiteEntryModel(i, 120, "#1 x", 1L, 1L))
            .toList());
    r.setFailed(
        java.util.stream.IntStream.range(0, failed)
            .mapToObj(i -> new BookingResultModel.FailedEntry(120, "#1 x", "boom"))
            .toList());
    r.setTotalMinutesCreated(created * 120);
    return r;
  }
}

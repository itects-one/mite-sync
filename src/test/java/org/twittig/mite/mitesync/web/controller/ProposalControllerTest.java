package org.twittig.mite.mitesync.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.twittig.mite.mitesync.config.SecurityConfig;
import org.twittig.mite.mitesync.persistence.ProposalStatus;
import org.twittig.mite.mitesync.service.IllegalProposalStateException;
import org.twittig.mite.mitesync.service.ProposalService;
import org.twittig.mite.mitesync.service.UnknownProposalException;
import org.twittig.mite.mitesync.web.model.BookingResultModel;
import org.twittig.mite.mitesync.web.model.ConfirmResultModel;
import org.twittig.mite.mitesync.web.model.MiteEntryModel;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;
import org.twittig.mite.mitesync.web.model.ProposalModel;

@Import({SecurityConfig.class, ScriptedClientDefaults.class})
@WithMockUser
@WebMvcTest(ProposalController.class)
class ProposalControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean ProposalService service;

  private static final LocalDate DATE = LocalDate.of(2026, 7, 20);

  // -------- GET /proposals --------

  @Test
  void list_returns_200() throws Exception {
    when(service.list()).thenReturn(List.of(proposal(1L, ProposalStatus.DRAFT)));
    mockMvc
        .perform(get("/proposals"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].status").value("DRAFT"));
  }

  // -------- GET /proposals/{id} --------

  @Test
  void get_returns_200() throws Exception {
    when(service.get(1L)).thenReturn(proposal(1L, ProposalStatus.DRAFT));
    mockMvc
        .perform(get("/proposals/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.date").value("2026-07-20"));
  }

  @Test
  void get_unknown_returns_404() throws Exception {
    when(service.get(99L)).thenThrow(new UnknownProposalException(99L));
    mockMvc
        .perform(get("/proposals/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.proposal").value("Unknown proposal '99'"));
  }

  // -------- POST /proposals/{project}/{date} --------

  @Test
  void generate_returns_200() throws Exception {
    when(service.generate(eq("default"), eq(DATE), any()))
        .thenReturn(proposal(7L, ProposalStatus.DRAFT));
    mockMvc
        .perform(
            post("/proposals/default/2026-07-20")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mainPbiId": 655021}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(7))
        .andExpect(jsonPath("$.status").value("DRAFT"));

    verify(service).generate(eq("default"), eq(DATE), any());
  }

  @Test
  void generate_returns_400_when_body_empty() throws Exception {
    mockMvc
        .perform(post("/proposals/default/2026-07-20").contentType(MediaType.APPLICATION_JSON).content(""))
        .andExpect(status().isBadRequest());
  }

  // -------- PUT /proposals/{id}/entries --------

  @Test
  void editEntries_returns_200() throws Exception {
    when(service.editEntries(eq(3L), anyList())).thenReturn(proposal(3L, ProposalStatus.DRAFT));
    mockMvc
        .perform(
            put("/proposals/3/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"entries": [{"minutes": 300, "note": "#655 Dev"}]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(3));
  }

  @Test
  void editEntries_returns_400_when_entries_empty() throws Exception {
    mockMvc
        .perform(
            put("/proposals/3/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"entries": []}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.entries").exists());
  }

  @Test
  void editEntries_returns_409_when_not_draft() throws Exception {
    when(service.editEntries(eq(3L), anyList()))
        .thenThrow(
            new IllegalProposalStateException(3L, ProposalStatus.BOOKED, ProposalStatus.DRAFT));
    mockMvc
        .perform(
            put("/proposals/3/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"entries": [{"minutes": 300, "note": "#655 Dev"}]}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").exists());
  }

  // -------- POST /proposals/{id}/confirm --------

  @Test
  void confirm_returns_200() throws Exception {
    when(service.confirm(7L)).thenReturn(confirmResult());
    mockMvc
        .perform(post("/proposals/7/confirm"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.proposal.status").value("BOOKED"))
        .andExpect(jsonPath("$.booking.totalMinutesCreated").value(120));
  }

  @Test
  void confirm_unknown_returns_404() throws Exception {
    when(service.confirm(99L)).thenThrow(new UnknownProposalException(99L));
    mockMvc
        .perform(post("/proposals/99/confirm"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.proposal").exists());
  }

  // -------- DELETE /proposals/{id} --------

  @Test
  void delete_returns_204() throws Exception {
    mockMvc.perform(delete("/proposals/5")).andExpect(status().isNoContent());
    verify(service).delete(5L);
  }

  // -------- helpers --------

  private static ProposalModel proposal(Long id, ProposalStatus status) {
    ProposalModel m = new ProposalModel();
    m.setId(id);
    m.setProfileKey("default");
    m.setDate(DATE);
    m.setStatus(status.name());
    m.setEntries(List.of(new ProposalEntryModel(300, "#655 Dev", "dev", 655, "Dev")));
    m.setTotalMinutes(300);
    return m;
  }

  private static ConfirmResultModel confirmResult() {
    ProposalModel proposal = proposal(7L, ProposalStatus.BOOKED);
    BookingResultModel booking = new BookingResultModel();
    booking.setDate(DATE);
    booking.setCreated(List.of(new MiteEntryModel(1L, 120, "#1 x", 1L, 1L)));
    booking.setFailed(List.of());
    booking.setTotalMinutesCreated(120);
    return new ConfirmResultModel(proposal, booking);
  }
}

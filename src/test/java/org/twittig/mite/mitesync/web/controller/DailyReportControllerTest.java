package org.twittig.mite.mitesync.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.twittig.mite.mitesync.config.SecurityConfig;
import org.twittig.mite.mitesync.config.UnknownProfileException;
import org.twittig.mite.mitesync.facade.DailyReportFacade;
import org.twittig.mite.mitesync.facade.MissingMainPbiException;
import org.twittig.mite.mitesync.web.model.BookingResultModel;
import org.twittig.mite.mitesync.web.model.CalendarEventModel;
import org.twittig.mite.mitesync.web.model.DailyReportModel;
import org.twittig.mite.mitesync.web.model.MiteEntryModel;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;
import tools.jackson.databind.ObjectMapper;

@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(DailyReportController.class)
class DailyReportControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    DailyReportFacade facade;

    // -------- POST /daily-reports/{date}/preview --------

    @Test
    void preview_returns_200_with_report() throws Exception {
        DailyReportModel report = buildReport(LocalDate.of(2025, 4, 28));
        when(facade.preview(eq(LocalDate.of(2025, 4, 28)), any())).thenReturn(report);

        mockMvc.perform(post("/daily-reports/2025-04-28/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mainPbiId": 12345}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2025-04-28"))
                .andExpect(jsonPath("$.dayOfWeek").value("Mon"))
                .andExpect(jsonPath("$.proposalTotalMinutes").value(375));
    }

    @Test
    void preview_passes_targetHours_to_facade() throws Exception {
        when(facade.preview(any(), any())).thenReturn(buildReport(LocalDate.of(2025, 4, 28)));

        mockMvc.perform(post("/daily-reports/2025-04-28/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mainPbiId": 12345, "targetHours": 4.0}
                                """))
                .andExpect(status().isOk());

        verify(facade).preview(eq(LocalDate.of(2025, 4, 28)), argCaptor(4.0));
    }

    @Test
    void preview_returns_400_when_mainPbiId_missing_for_calendar_profile() throws Exception {
        // The constraint is enforced in the facade (git-activity profiles need no main PBI)
        when(facade.preview(any(), any())).thenThrow(new MissingMainPbiException());

        mockMvc.perform(post("/daily-reports/2025-04-28/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mainPbiId").exists());
    }

    @Test
    void preview_returns_400_when_body_empty() throws Exception {
        mockMvc.perform(post("/daily-reports/2025-04-28/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preview_includes_calendar_events_and_proposal() throws Exception {
        DailyReportModel report = buildReport(LocalDate.of(2025, 4, 28));
        CalendarEventModel ev = new CalendarEventModel();
        ev.setSummary("Team Daily");
        ev.setRoundedMinutes(15);
        ev.setResponseStatus("accepted");
        report.setCalendarEvents(List.of(ev));
        report.setProposal(List.of(new ProposalEntryModel(375, "#12345 Feature XY", "main-pbi-fill", 12345, "Feature XY")));
        when(facade.preview(any(), any())).thenReturn(report);

        mockMvc.perform(post("/daily-reports/2025-04-28/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mainPbiId": 12345}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendarEvents[0].summary").value("Team Daily"))
                .andExpect(jsonPath("$.proposal[0].note").value("#12345 Feature XY"))
                .andExpect(jsonPath("$.proposal[0].source").value("main-pbi-fill"));
    }

    // -------- POST /daily-reports/{project}/{date}/preview --------

    @Test
    void preview_withProject_passes_profile_to_facade() throws Exception {
        DailyReportModel report = buildReport(LocalDate.of(2025, 4, 28));
        when(facade.preview(eq("alpha"), eq(LocalDate.of(2025, 4, 28)), any())).thenReturn(report);

        mockMvc.perform(post("/daily-reports/alpha/2025-04-28/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mainPbiId": 12345}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2025-04-28"));

        verify(facade).preview(eq("alpha"), eq(LocalDate.of(2025, 4, 28)), any());
    }

    @Test
    void preview_unknownProject_returns_404() throws Exception {
        when(facade.preview(eq("nope"), any(), any()))
                .thenThrow(new UnknownProfileException("nope"));

        mockMvc.perform(post("/daily-reports/nope/2025-04-28/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mainPbiId": 12345}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.project").value("Unknown project profile 'nope'"));
    }

    @Test
    void preview_gitProfile_acceptsBodyWithoutMainPbiId() throws Exception {
        when(facade.preview(eq("gitproject"), eq(LocalDate.of(2025, 4, 28)), any()))
                .thenReturn(buildReport(LocalDate.of(2025, 4, 28)));

        mockMvc.perform(post("/daily-reports/gitproject/2025-04-28/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetHours": 4.0}
                                """))
                .andExpect(status().isOk());
    }

    // -------- POST /daily-reports/{date}/book --------

    @Test
    void book_returns_200_with_booking_result() throws Exception {
        BookingResultModel result = buildBookingResult(LocalDate.of(2025, 4, 28), 375);
        when(facade.book(eq(LocalDate.of(2025, 4, 28)), any())).thenReturn(result);

        mockMvc.perform(post("/daily-reports/2025-04-28/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries": [{"minutes": 375, "note": "#12345 Feature XY"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2025-04-28"))
                .andExpect(jsonPath("$.totalMinutesCreated").value(375))
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.failed").isArray());
    }

    @Test
    void book_passes_multiple_entries_to_facade() throws Exception {
        when(facade.book(any(), any())).thenReturn(buildBookingResult(LocalDate.of(2025, 4, 28), 390));

        mockMvc.perform(post("/daily-reports/2025-04-28/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries": [
                                  {"minutes": 15,  "note": "#1234 Team Daily"},
                                  {"minutes": 375, "note": "#12345 Feature XY"}
                                ]}
                                """))
                .andExpect(status().isOk());

        verify(facade).book(eq(LocalDate.of(2025, 4, 28)), any());
    }

    @Test
    void book_returns_400_when_entries_empty() throws Exception {
        mockMvc.perform(post("/daily-reports/2025-04-28/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.entries").exists());
    }

    @Test
    void book_returns_400_when_entries_missing() throws Exception {
        mockMvc.perform(post("/daily-reports/2025-04-28/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void book_returns_partial_failures_in_result() throws Exception {
        BookingResultModel result = new BookingResultModel();
        result.setDate(LocalDate.of(2025, 4, 28));
        result.setCreated(List.of());
        result.setFailed(List.of(
                new BookingResultModel.FailedEntry(375, "#12345 Feature XY", "Connection refused")));
        result.setTotalMinutesCreated(0);
        when(facade.book(any(), any())).thenReturn(result);

        mockMvc.perform(post("/daily-reports/2025-04-28/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries": [{"minutes": 375, "note": "#12345 Feature XY"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed[0].note").value("#12345 Feature XY"))
                .andExpect(jsonPath("$.failed[0].error").value("Connection refused"))
                .andExpect(jsonPath("$.totalMinutesCreated").value(0));
    }

    // -------- POST /daily-reports/{project}/{date}/book --------

    @Test
    void book_withProject_passes_profile_to_facade() throws Exception {
        BookingResultModel result = buildBookingResult(LocalDate.of(2025, 4, 28), 375);
        when(facade.book(eq("alpha"), eq(LocalDate.of(2025, 4, 28)), any())).thenReturn(result);

        mockMvc.perform(post("/daily-reports/alpha/2025-04-28/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries": [{"minutes": 375, "note": "#12345 Feature XY"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutesCreated").value(375));

        verify(facade).book(eq("alpha"), eq(LocalDate.of(2025, 4, 28)), any());
    }

    @Test
    void book_unknownProject_returns_404() throws Exception {
        when(facade.book(eq("nope"), any(), any()))
                .thenThrow(new UnknownProfileException("nope"));

        mockMvc.perform(post("/daily-reports/nope/2025-04-28/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries": [{"minutes": 375, "note": "#12345 Feature XY"}]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.project").exists());
    }

    // -------- Helpers --------

    private DailyReportModel buildReport(LocalDate date) {
        DailyReportModel m = new DailyReportModel();
        m.setDate(date);
        m.setDayOfWeek("Mon");
        m.setCalendarEvents(List.of());
        m.setAlreadyBookedInMite(List.of());
        m.setDevOpsActivityOnDate(List.of());
        m.setOpenWorkItems(List.of());
        m.setProposal(List.of());
        m.setProposalTotalMinutes(375);
        return m;
    }

    private BookingResultModel buildBookingResult(LocalDate date, int totalMinutes) {
        BookingResultModel r = new BookingResultModel();
        r.setDate(date);
        MiteEntryModel created = new MiteEntryModel(999L, totalMinutes, "#12345 Feature XY", 3663221L, 506948L);
        r.setCreated(List.of(created));
        r.setFailed(List.of());
        r.setTotalMinutesCreated(totalMinutes);
        return r;
    }

    /**
     * Helper matcher: checks that the second argument is a PbiAssignmentModel with the given
     * targetHours value. Because @Captor in WebMvcTest is awkward, a plain verify() is enough —
     * the targetHours pass-through is implicitly confirmed by the 200 status.
     */
    private org.twittig.mite.mitesync.web.model.PbiAssignmentModel argCaptor(double targetHours) {
        return org.mockito.ArgumentMatchers.argThat(
                p -> p != null && p.getTargetHours() != null
                        && Double.compare(p.getTargetHours(), targetHours) == 0);
    }
}

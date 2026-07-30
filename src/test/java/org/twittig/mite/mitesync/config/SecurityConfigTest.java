package org.twittig.mite.mitesync.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.twittig.mite.mitesync.service.ProposalService;
import org.twittig.mite.mitesync.web.controller.ProposalController;
import org.twittig.mite.mitesync.web.model.BookingResultModel;
import org.twittig.mite.mitesync.web.model.ConfirmResultModel;
import org.twittig.mite.mitesync.web.model.ProposalModel;

/**
 * Verifies the security filter chain itself: every endpoint requires authentication, valid
 * credentials get through, and CSRF is genuinely off so that write requests from a plain HTTP
 * client work.
 *
 * <p>Runs as a web slice with {@link SecurityConfig} imported explicitly — a full
 * {@code @SpringBootTest} would boot the datasource and open the real H2 file.
 */
@WebMvcTest(ProposalController.class)
@Import(SecurityConfig.class)
@TestPropertySource(
    properties = {
      "spring.security.user.name=tester",
      "spring.security.user.password=s3cret"
    })
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ProposalService service;

  @Test
  void unauthenticatedRequest_isRejected() throws Exception {
    mockMvc.perform(get("/proposals")).andExpect(status().isUnauthorized());
  }

  @Test
  void validCredentials_areAccepted() throws Exception {
    mockMvc
        .perform(get("/proposals").with(httpBasic("tester", "s3cret")))
        .andExpect(status().isOk());
  }

  @Test
  void wrongPassword_isRejected() throws Exception {
    mockMvc
        .perform(get("/proposals").with(httpBasic("tester", "wrong")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownUser_isRejected() throws Exception {
    mockMvc
        .perform(get("/proposals").with(httpBasic("nobody", "s3cret")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void writeRequestWithoutCsrfToken_isNotBlocked() throws Exception {
    when(service.confirm(1L)).thenReturn(confirmResult());
    // CSRF is disabled for this stateless API; without that, this POST would fail with 403.
    mockMvc
        .perform(post("/proposals/1/confirm").with(httpBasic("tester", "s3cret")))
        .andExpect(status().isOk());
  }

  @Test
  void unauthenticatedWriteRequest_isRejected() throws Exception {
    mockMvc.perform(post("/proposals/1/confirm")).andExpect(status().isUnauthorized());
  }

  private static ConfirmResultModel confirmResult() {
    ProposalModel proposal = new ProposalModel();
    proposal.setId(1L);
    proposal.setStatus("BOOKED");
    BookingResultModel booking = new BookingResultModel();
    booking.setCreated(List.of());
    booking.setFailed(List.of());
    return new ConfirmResultModel(proposal, booking);
  }
}

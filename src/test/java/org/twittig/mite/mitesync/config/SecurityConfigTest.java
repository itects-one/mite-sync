package org.twittig.mite.mitesync.config;

import static org.mockito.Mockito.verifyNoInteractions;
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
  void writeRequestWithTheHeader_isAccepted() throws Exception {
    when(service.confirm(1L)).thenReturn(confirmResult());
    // No CSRF token is involved: the header alone marks the request as script-initiated.
    mockMvc
        .perform(
            post("/proposals/1/confirm")
                .with(httpBasic("tester", "s3cret"))
                .header("X-Requested-With", "test"))
        .andExpect(status().isOk());
  }

  @Test
  void writeRequestWithoutTheHeader_isRejected() throws Exception {
    // This is the cross-site form case: valid cached credentials, but no header a form could set.
    mockMvc
        .perform(post("/proposals/1/confirm").with(httpBasic("tester", "s3cret")))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void readRequestNeedsNoHeader() throws Exception {
    mockMvc
        .perform(get("/proposals").with(httpBasic("tester", "s3cret")))
        .andExpect(status().isOk());
  }

  @Test
  void unauthenticatedWriteRequest_isRejected() throws Exception {
    // 401, not 403: missing credentials has to win over the missing header, or the response would
    // hide the real reason.
    mockMvc
        .perform(post("/proposals/1/confirm").header("X-Requested-With", "test"))
        .andExpect(status().isUnauthorized());
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

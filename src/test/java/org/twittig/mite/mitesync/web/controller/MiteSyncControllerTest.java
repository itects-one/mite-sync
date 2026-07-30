package org.twittig.mite.mitesync.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.twittig.mite.mitesync.facade.MiteSyncFacade;
import org.twittig.mite.mitesync.web.model.SyncJobModel;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(MiteSyncController.class)
class MiteSyncControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private MiteSyncFacade miteSyncFacade;

  @Test
  void postSyncJobs_validRequest_returns200() throws Exception {
    doNothing().when(miteSyncFacade).sync(any(LocalDate.class), any(LocalDate.class));

    SyncJobModel request = SyncJobModel.Builder.builder()
        .withName("Test Sync")
        .withFrom("01.01.2024")
        .withTo("31.01.2024")
        .build();

    mockMvc.perform(post("/sync-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.name").value("Test Sync"))
        .andExpect(jsonPath("$.from").value("01.01.2024"))
        .andExpect(jsonPath("$.to").value("31.01.2024"));

    verify(miteSyncFacade).sync(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));
  }

  @Test
  void postSyncJobs_sameDayRange_returns200() throws Exception {
    doNothing().when(miteSyncFacade).sync(any(LocalDate.class), any(LocalDate.class));

    SyncJobModel request = SyncJobModel.Builder.builder()
        .withName("Daily Sync")
        .withFrom("15.03.2024")
        .withTo("15.03.2024")
        .build();

    mockMvc.perform(post("/sync-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void postSyncJobs_missingName_returns400() throws Exception {
    SyncJobModel request = SyncJobModel.Builder.builder()
        .withFrom("01.01.2024")
        .withTo("31.01.2024")
        .build();

    mockMvc.perform(post("/sync-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postSyncJobs_missingFrom_returns400() throws Exception {
    SyncJobModel request = SyncJobModel.Builder.builder()
        .withName("Test")
        .withTo("31.01.2024")
        .build();

    mockMvc.perform(post("/sync-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postSyncJobs_missingTo_returns400() throws Exception {
    SyncJobModel request = SyncJobModel.Builder.builder()
        .withName("Test")
        .withFrom("01.01.2024")
        .build();

    mockMvc.perform(post("/sync-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postSyncJobs_invalidDateFormat_returns400() throws Exception {
    SyncJobModel request = SyncJobModel.Builder.builder()
        .withName("Test")
        .withFrom("2024-01-01")
        .withTo("2024-01-31")
        .build();

    mockMvc.perform(post("/sync-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postSyncJobs_fromAfterTo_returns400() throws Exception {
    SyncJobModel request = SyncJobModel.Builder.builder()
        .withName("Test")
        .withFrom("31.01.2024")
        .withTo("01.01.2024")
        .build();

    mockMvc.perform(post("/sync-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void postSyncJobs_emptyBody_returns400() throws Exception {
    mockMvc.perform(post("/sync-jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
  }
}

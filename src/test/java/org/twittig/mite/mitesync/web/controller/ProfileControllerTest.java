package org.twittig.mite.mitesync.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.twittig.mite.mitesync.config.DailyReportProperties.Profile;
import org.twittig.mite.mitesync.config.DailyReportProperties.WorkflowType;
import org.twittig.mite.mitesync.config.ProfileRegistry;
import org.twittig.mite.mitesync.config.SecurityConfig;

@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(ProfileController.class)
class ProfileControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean ProfileRegistry registry;

  @Test
  void list_returnsProfilesOrderedByKey() throws Exception {
    SortedMap<String, Profile> profiles = new TreeMap<>();
    profiles.put("zulu", profile(WorkflowType.GIT_ACTIVITY, 240));
    profiles.put("alpha", profile(WorkflowType.CALENDAR_DEVOPS, 375));
    when(registry.all()).thenReturn(profiles);
    when(registry.defaultProfileKey()).thenReturn("alpha");

    mockMvc
        .perform(get("/profiles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].key").value("alpha"))
        .andExpect(jsonPath("$[0].workflowType").value("calendar-devops"))
        .andExpect(jsonPath("$[0].requiresMainPbi").value(true))
        .andExpect(jsonPath("$[0].targetMinutes").value(375))
        .andExpect(jsonPath("$[0].default").value(true))
        .andExpect(jsonPath("$[1].key").value("zulu"))
        .andExpect(jsonPath("$[1].workflowType").value("git-activity"))
        .andExpect(jsonPath("$[1].requiresMainPbi").value(false))
        .andExpect(jsonPath("$[1].default").value(false));
  }

  @Test
  void list_returnsEmptyArray_whenNoProfilesConfigured() throws Exception {
    when(registry.all()).thenReturn(new TreeMap<>());
    when(registry.defaultProfileKey()).thenReturn("default");

    mockMvc
        .perform(get("/profiles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void list_requiresAuthentication() throws Exception {
    mockMvc.perform(get("/profiles").with(anonymous())).andExpect(status().isUnauthorized());
  }

  private static Profile profile(WorkflowType type, int targetMinutes) {
    Profile p = new Profile();
    p.setWorkflowType(type);
    p.getRules().setTargetMinutes(targetMinutes);
    return p;
  }
}

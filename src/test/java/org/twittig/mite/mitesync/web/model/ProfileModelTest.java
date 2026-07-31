package org.twittig.mite.mitesync.web.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.twittig.mite.mitesync.config.DailyReportProperties.Profile;
import org.twittig.mite.mitesync.config.DailyReportProperties.WorkflowType;

class ProfileModelTest {

  @Test
  void calendarDevopsProfile_requiresMainPbi() {
    ProfileModel m = ProfileModel.of("default", profile(WorkflowType.CALENDAR_DEVOPS, 375), true);

    assertThat(m.key()).isEqualTo("default");
    assertThat(m.workflowType()).isEqualTo("calendar-devops");
    assertThat(m.requiresMainPbi()).isTrue();
    assertThat(m.targetMinutes()).isEqualTo(375);
    assertThat(m.defaultProfile()).isTrue();
  }

  @Test
  void gitActivityProfile_doesNotRequireMainPbi() {
    ProfileModel m = ProfileModel.of("my-project", profile(WorkflowType.GIT_ACTIVITY, 240), false);

    assertThat(m.workflowType()).isEqualTo("git-activity");
    assertThat(m.requiresMainPbi()).isFalse();
    assertThat(m.targetMinutes()).isEqualTo(240);
    assertThat(m.defaultProfile()).isFalse();
  }

  private static Profile profile(WorkflowType type, int targetMinutes) {
    Profile p = new Profile();
    p.setWorkflowType(type);
    p.getRules().setTargetMinutes(targetMinutes);
    return p;
  }
}

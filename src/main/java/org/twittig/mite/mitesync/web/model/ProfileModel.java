package org.twittig.mite.mitesync.web.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;
import org.twittig.mite.mitesync.config.DailyReportProperties.Profile;
import org.twittig.mite.mitesync.config.DailyReportProperties.WorkflowType;

/**
 * One configured project profile, as far as a client needs it to drive the daily-report and
 * proposal endpoints: which key to put in the {@code {project}} path segment, and what the profile
 * expects in the request body.
 *
 * <p>Mite ids, instance keys and repository paths stay out — they are configuration, not API
 * surface.
 *
 * @param key the {@code {project}} path segment selecting this profile
 * @param workflowType kebab-case spelling as used in {@code application.yml}
 * @param requiresMainPbi whether {@code mainPbiId} must be supplied when generating a proposal
 * @param targetMinutes the profile's daily target, overridable per request via {@code targetHours}
 * @param defaultProfile whether the legacy routes without a project segment fall back to this one
 */
public record ProfileModel(
    String key,
    String workflowType,
    boolean requiresMainPbi,
    int targetMinutes,
    @JsonProperty("default") boolean defaultProfile) {

  public static ProfileModel of(String key, Profile profile, boolean isDefault) {
    WorkflowType type = profile.getWorkflowType();
    return new ProfileModel(
        key,
        kebabCase(type),
        // Only calendar-devops books onto a main PBI; git-activity takes its tickets from commits.
        type == WorkflowType.CALENDAR_DEVOPS,
        profile.getRules().getTargetMinutes(),
        isDefault);
  }

  private static String kebabCase(WorkflowType type) {
    return type.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}

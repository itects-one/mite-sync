package org.twittig.mite.mitesync.config;

import java.util.SortedMap;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import org.twittig.mite.mitesync.config.DailyReportProperties.Profile;

/** Resolves project profiles from configuration. */
@Component
public class ProfileRegistry {

  private final DailyReportProperties properties;

  public ProfileRegistry(DailyReportProperties properties) {
    this.properties = properties;
  }

  /**
   * Returns the profile for the given key.
   *
   * @throws UnknownProfileException when no profile with that key is configured
   */
  public Profile resolve(String profileKey) {
    Profile profile = properties.getProfiles().get(profileKey);
    if (profile == null) {
      throw new UnknownProfileException(profileKey);
    }
    return profile;
  }

  /** Key of the profile used by the legacy endpoints without a project path segment. */
  public String defaultProfileKey() {
    return properties.getDefaultProfile();
  }

  /** All configured profiles, ordered by key so that a listing is stable. */
  public SortedMap<String, Profile> all() {
    return new TreeMap<>(properties.getProfiles());
  }
}

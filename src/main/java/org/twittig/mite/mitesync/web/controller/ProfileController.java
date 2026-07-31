package org.twittig.mite.mitesync.web.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.twittig.mite.mitesync.config.ProfileRegistry;
import org.twittig.mite.mitesync.web.model.ProfileModel;

/**
 * Lists the configured project profiles. Without this, the {@code {project}} path segment of the
 * daily-report and proposal endpoints is undiscoverable: a client has to know the keys out of band
 * and cannot tell whether a profile expects a {@code mainPbiId}.
 */
@RestController
@RequestMapping("/profiles")
public class ProfileController {

  private final ProfileRegistry registry;

  public ProfileController(ProfileRegistry registry) {
    this.registry = registry;
  }

  /** Lists all configured profiles, ordered by key. */
  @GetMapping
  public ResponseEntity<List<ProfileModel>> list() {
    String defaultKey = registry.defaultProfileKey();
    return ResponseEntity.ok(
        registry.all().entrySet().stream()
            .map(e -> ProfileModel.of(e.getKey(), e.getValue(), e.getKey().equals(defaultKey)))
            .toList());
  }
}

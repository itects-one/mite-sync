package org.twittig.mite.mitesync.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.twittig.mite.mitesync.config.RequiredHeaderCsrfFilter;

/**
 * Makes every request of a controller slice test look like one from a script rather than from a
 * cross-site form: {@link RequiredHeaderCsrfFilter} rejects state-changing requests without the
 * header, and these tests are about the controllers, not about that guard.
 *
 * <p>That the guard actually bites is asserted in {@code SecurityConfigTest} — deliberately in one
 * place, so it cannot be weakened here by accident.
 */
@TestConfiguration
public class ScriptedClientDefaults {

  @Bean
  MockMvcBuilderCustomizer requestedWithHeader() {
    return builder -> builder.defaultRequest(get("/").header("X-Requested-With", "test"));
  }
}

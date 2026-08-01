package org.twittig.mite.mitesync.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Requires a custom header on every state-changing request, as cross-site request forgery
 * protection for the web UI.
 *
 * <p>Why this is needed at all: HTTP basic credentials are an <em>ambient</em> credential in a
 * browser. Once the user has authenticated, the browser attaches them to every request to this
 * origin — including one a foreign page triggers. {@code POST /proposals/{id}/confirm} takes no
 * request body, so without a guard a plain cross-site {@code <form method="post">} would be enough
 * to book real entries into the time-tracking system.
 *
 * <p>Why a header is enough: a form cannot set request headers, so the form vector is dead. A
 * scripted cross-origin request can set them, but doing so turns the request into a preflighted
 * one, and this application answers no CORS preflight — the browser blocks it before it is sent.
 * The value of the header is irrelevant; only its presence is checked, because it is not a secret.
 * This is the "custom request header" defence OWASP describes for APIs.
 *
 * <p><b>Do not configure permissive CORS without replacing this.</b> The protection rests on the
 * same-origin boundary, not on a token the attacker cannot guess. Allowing foreign origins with
 * credentials would let them send the header themselves and reopen the hole. The web UI is served
 * from this same origin, so it never needs CORS.
 */
public class RequiredHeaderCsrfFilter extends OncePerRequestFilter {

  /** Presence is what counts — the value is never inspected. */
  static final String REQUIRED_HEADER = "X-Requested-With";

  /** Methods that do not change state and therefore need no protection. */
  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (!SAFE_METHODS.contains(request.getMethod())
        && request.getHeader(REQUIRED_HEADER) == null) {
      response.sendError(
          HttpServletResponse.SC_FORBIDDEN,
          "Missing " + REQUIRED_HEADER + " header on a state-changing request");
      return;
    }
    chain.doFilter(request, response);
  }
}

package org.twittig.mite.mitesync.web;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.twittig.mite.mitesync.config.UnknownProfileException;
import org.twittig.mite.mitesync.facade.MissingMainPbiException;
import org.twittig.mite.mitesync.service.IllegalProposalStateException;
import org.twittig.mite.mitesync.service.UnknownProposalException;

@ControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidationExceptions(
      MethodArgumentNotValidException ex) {

    Map<String, String> errors = new HashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.put(error.getField(), error.getDefaultMessage());
    }

    return ResponseEntity.badRequest().body(errors);
  }

  @ExceptionHandler(UnknownProfileException.class)
  public ResponseEntity<Map<String, String>> handleUnknownProfile(UnknownProfileException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("project", ex.getMessage()));
  }

  @ExceptionHandler(MissingMainPbiException.class)
  public ResponseEntity<Map<String, String>> handleMissingMainPbi(MissingMainPbiException ex) {
    return ResponseEntity.badRequest().body(Map.of("mainPbiId", ex.getMessage()));
  }

  @ExceptionHandler(UnknownProposalException.class)
  public ResponseEntity<Map<String, String>> handleUnknownProposal(UnknownProposalException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("proposal", ex.getMessage()));
  }

  @ExceptionHandler(IllegalProposalStateException.class)
  public ResponseEntity<Map<String, String>> handleIllegalProposalState(
      IllegalProposalStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", ex.getMessage()));
  }
}

package com.claimflow.claims.shared;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Maps exceptions to RFC 9457 problem details. Bean Validation and other Spring MVC errors
 * (400) are handled by the base class.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ProblemDetail> badRequest(BadRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", e.getMessage());
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ProblemDetail> unauthenticated(UnauthenticatedException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthenticated", e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ProblemDetail> forbidden(ForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    /** Two requests passed checkVersion with the same version; the second flush lost. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> optimisticLock(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The claim was changed by someone else, reload and try again");
    }

    /** E.g. a concurrent submit with the same Idempotency-Key, or the one-open-request index. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> dataIntegrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The request conflicts with the current state of the data");
    }

    /**
     * Bean Validation on a request body: same 400 as the base class, plus an {@code errors} list
     * naming each invalid field so the client can show the message next to it.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errors.add(new FieldViolation(fe.getField(), fe.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                errors.add(new FieldViolation(ge.getObjectName(), ge.getDefaultMessage())));
        errors.sort(Comparator.comparing(FieldViolation::field));

        ProblemDetail body = ProblemDetail.forStatusAndDetail(status,
                errors.size() == 1 ? "1 field is invalid" : errors.size() + " fields are invalid");
        body.setTitle("Validation failed");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** One entry of the {@code errors} list in a validation problem. */
    record FieldViolation(String field, String message) {
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return ResponseEntity.status(status).body(body);
    }
}

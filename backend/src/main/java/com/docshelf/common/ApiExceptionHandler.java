// Global REST error mapping to RFC 9457 ProblemDetail (validation -> 400 with errors[], custom exceptions -> their status)
package com.docshelf.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TYPE_BASE = "https://docshelf.local/errors/";

    public record FieldErrorItem(String field, String message) {
    }

    @ExceptionHandler(DocshelfException.class)
    public ProblemDetail handleDocshelf(DocshelfException ex, WebRequest request) {
        if (ex instanceof UpstreamException) {
            log.warn("Upstream failure: {}", ex.getMessage());
        }
        return problem(ex.status(), ex.typeSlug(), ex.status().getReasonPhrase(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<FieldErrorItem> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorItem(fe.getField(), fe.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.add(new FieldErrorItem(ge.getObjectName(), ge.getDefaultMessage())));
        String detail = errors.isEmpty() ? "Validation failed"
                : errors.get(0).field() + " " + errors.get(0).message();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed", detail, request);
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException ex, WebRequest request) {
        List<FieldErrorItem> errors = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            errors.add(new FieldErrorItem(v.getPropertyPath().toString(), v.getMessage()));
        }
        String detail = errors.isEmpty() ? "Validation failed"
                : errors.get(0).field() + " " + errors.get(0).message();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed", detail, request);
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleBadRequest(Exception ex, WebRequest request) {
        String detail = switch (ex) {
            case MissingServletRequestParameterException m -> "Missing parameter " + m.getParameterName();
            case MissingServletRequestPartException m -> "Missing multipart part " + m.getRequestPartName();
            case MethodArgumentTypeMismatchException m -> "Invalid value for " + m.getName();
            default -> "Malformed request body";
        };
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request", detail, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex, WebRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "too-large", "File too large",
                "Upload exceeds the configured size limit", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaType(HttpMediaTypeNotSupportedException ex, WebRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Unsupported media type",
                ex.getMessage(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethod(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed",
                ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex, WebRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not Found", "No such endpoint", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Internal error",
                "An unexpected error occurred", request);
    }

    private static ProblemDetail problem(HttpStatus status, String slug, String title, String detail,
                                         WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + slug));
        pd.setTitle(title);
        String path = request.getDescription(false);
        if (path != null && path.startsWith("uri=")) {
            pd.setInstance(URI.create(path.substring(4)));
        }
        return pd;
    }
}

package ru.mcrpg.authapi.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException exception, HttpServletRequest request) {
        return buildResponse(exception.getStatus(), exception.getError(), exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeaderException(
        MissingRequestHeaderException exception,
        HttpServletRequest request
    ) {
        if ("Authorization".equalsIgnoreCase(exception.getHeaderName())) {
            return buildResponse(HttpStatus.UNAUTHORIZED, "missing_token", "Нужен авторизационный token Bearer.", request.getRequestURI());
        }
        return buildResponse(
            HttpStatus.BAD_REQUEST,
            "missing_header",
            "Не указан обязательный HTTP-заголовок: " + exception.getHeaderName() + ".",
            request.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "Тело запроса некорректно." : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        return buildResponse(HttpStatus.BAD_REQUEST, "validation_error", message, request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        String message = exception.getMessage() == null || exception.getMessage().trim().isEmpty()
            ? "Параметры запроса некорректны."
            : exception.getMessage();
        return buildResponse(HttpStatus.BAD_REQUEST, "validation_error", message, request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessageException(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.BAD_REQUEST, "invalid_json", "Тело запроса должно быть корректным JSON.", request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolationException(
        DataIntegrityViolationException exception,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.CONFLICT, "data_conflict", "Запрошенное изменение конфликтует с текущими данными.", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected Auth API failure for {}", request.getRequestURI(), exception);
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal_error",
            "Неожиданная ошибка сервера.",
            request.getRequestURI()
        );
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}

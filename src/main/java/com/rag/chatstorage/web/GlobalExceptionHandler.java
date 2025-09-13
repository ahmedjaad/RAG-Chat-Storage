package com.rag.chatstorage.web;

import com.rag.chatstorage.service.AiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private boolean wantsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) return true;
        String path = request.getRequestURI();
        return path != null && (path.startsWith("/ui") || 
                path.equals("/") || path.equals("/docs") || path.startsWith("/swagger-ui"));
    }

    private boolean isApi(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/");
    }

    @ExceptionHandler(AiService.AiFriendlyException.class)
        public Object handleAiFriendly(AiService.AiFriendlyException ex, HttpServletRequest request) {
            if (wantsHtml(request) && !isApi(request)) {
                // For UI, keep 5xx page with friendly message
                ModelAndView mav = new ModelAndView("error/5xx");
                mav.setStatus(HttpStatus.SERVICE_UNAVAILABLE);
                mav.addObject("path", request.getRequestURI());
                mav.addObject("status", 503);
                mav.addObject("error", ex.getMessage());
                return mav;
            }
            HttpStatus status = switch (ex.getCode() == null ? "" : ex.getCode()) {
                case "CONFIG_MISSING" -> HttpStatus.SERVICE_UNAVAILABLE;
                case "AI_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
                default -> HttpStatus.SERVICE_UNAVAILABLE;
            };
            ProblemDetail pd = ProblemDetail.forStatus(status);
            pd.setTitle("AI Service Unavailable");
            pd.setDetail(ex.getMessage());
            pd.setType(URI.create("about:blank"));
            pd.setInstance(URI.create(request.getRequestURI()));
            if (ex.getCode() != null) pd.setProperty("code", ex.getCode());
            if (ex.getHint() != null) pd.setProperty("hint", ex.getHint());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/problem+json"));
            return new ResponseEntity<>(pd, headers, status);
        }

        @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        if (wantsHtml(request) && !isApi(request)) {
            ModelAndView mav = new ModelAndView("error/404");
            mav.setStatus(HttpStatus.NOT_FOUND);
            mav.addObject("path", request.getRequestURI());
            mav.addObject("error", ex.getMessage());
            return mav;
        }
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setTitle("Not Found");
        pd.setDetail(ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setInstance(URI.create(request.getRequestURI()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/problem+json"));
        return new ResponseEntity<>(pd, headers, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        if (wantsHtml(request) && !isApi(request)) {
            ModelAndView mav = new ModelAndView("error/4xx");
            mav.setStatus(HttpStatus.BAD_REQUEST);
            mav.addObject("path", request.getRequestURI());
            mav.addObject("status", 400);
            mav.addObject("error", "Validation failed: " + errors);
            return mav;
        }
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Bad Request");
        pd.setDetail("Validation failed");
        pd.setType(URI.create("about:blank"));
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("errors", errors);
        pd.setProperty("code", "VALIDATION_FAILED");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/problem+json"));
        return new ResponseEntity<>(pd, headers, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        if (wantsHtml(request) && !isApi(request)) {
            ModelAndView mav = new ModelAndView("error/4xx");
            mav.setStatus(HttpStatus.PAYLOAD_TOO_LARGE);
            mav.addObject("path", request.getRequestURI());
            mav.addObject("status", 413);
            mav.addObject("error", "Payload too large");
            return mav;
        }
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        pd.setTitle("Payload Too Large");
        pd.setDetail("Request payload exceeds allowed size");
        pd.setType(URI.create("about:blank"));
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("code", "PAYLOAD_TOO_LARGE");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/problem+json"));
        return new ResponseEntity<>(pd, headers, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        if (wantsHtml(request) && !isApi(request)) {
            ModelAndView mav = new ModelAndView("error/4xx");
            mav.setStatus(HttpStatus.BAD_REQUEST);
            mav.addObject("path", request.getRequestURI());
            mav.addObject("status", 400);
            mav.addObject("error", "Missing parameter: " + ex.getParameterName());
            return mav;
        }
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Bad Request");
        pd.setDetail("Missing parameter: " + ex.getParameterName());
        pd.setType(URI.create("about:blank"));
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("code", "MISSING_PARAMETER");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/problem+json"));
        return new ResponseEntity<>(pd, headers, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneric(Exception ex, HttpServletRequest request) {
        if (wantsHtml(request) && !isApi(request)) {
            ModelAndView mav = new ModelAndView("error/5xx");
            mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            mav.addObject("path", request.getRequestURI());
            mav.addObject("status", 500);
            mav.addObject("error", ex.getMessage());
            return mav;
        }
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Internal Server Error");
        pd.setDetail("Internal server error");
        pd.setType(URI.create("about:blank"));
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("code", "INTERNAL_ERROR");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/problem+json"));
        return new ResponseEntity<>(pd, headers, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

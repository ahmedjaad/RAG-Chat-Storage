package com.rag.chatstorage.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

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

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        if (wantsHtml(request) && !isApi(request)) {
            ModelAndView mav = new ModelAndView("error/404");
            mav.setStatus(HttpStatus.NOT_FOUND);
            mav.addObject("path", request.getRequestURI());
            mav.addObject("error", ex.getMessage());
            return mav;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
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
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Validation failed");
        body.put("details", errors);
        return ResponseEntity.badRequest().body(body);
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
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Missing parameter: " + ex.getParameterName());
        return ResponseEntity.badRequest().body(body);
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
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

package com.example.hitmodel.controller;

import com.example.hitmodel.dto.HitModelResp;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public HitModelResp handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return HitModelResp.error("参数校验失败: " + msg);
    }
}

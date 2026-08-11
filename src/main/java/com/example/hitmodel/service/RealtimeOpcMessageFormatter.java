package com.example.hitmodel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RealtimeOpcMessageFormatter {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    public RealtimeOpcMessageFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String format(List<String> tags, Map<String, Object> values, LocalDateTime now)
            throws JsonProcessingException {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("time", TIME_FORMATTER.format(now));

        for (String tag : tags) {
            Object value = values.get(tag);
            payload.put(tag, value == null ? "" : String.valueOf(value));
        }

        return objectMapper.writeValueAsString(payload);
    }
}

package com.example.hitmodel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeOpcMessageFormatterTests {

    @Test
    void formatKeepsLegacyStringValues() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RealtimeOpcMessageFormatter formatter = new RealtimeOpcMessageFormatter(objectMapper);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("TT_82201A_AV", 31.2);
        values.put("FT_33111A_AV", null);

        String json = formatter.format(
                Arrays.asList("TT_82201A_AV", "FT_33111A_AV"),
                values,
                LocalDateTime.of(2026, 8, 11, 14, 0, 0)
        );

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("time").asText()).isEqualTo("2026/08/11 14:00:00");
        assertThat(node.get("TT_82201A_AV").asText()).isEqualTo("31.2");
        assertThat(node.get("FT_33111A_AV").asText()).isEmpty();
    }
}

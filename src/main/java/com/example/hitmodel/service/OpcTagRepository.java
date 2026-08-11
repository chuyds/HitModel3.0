package com.example.hitmodel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Repository
public class OpcTagRepository {

    static final String LOAD_TAGS_SQL =
            "SELECT opc_label_name FROM instrument WHERE opc_label_name IS NOT NULL";

    private final JdbcTemplate jdbcTemplate;

    public OpcTagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> loadTags() {
        List<String> rows = jdbcTemplate.queryForList(LOAD_TAGS_SQL, String.class);
        Set<String> tags = new LinkedHashSet<>();
        for (String row : rows) {
            if (row != null && !row.isBlank()) {
                tags.add(row.trim());
            }
        }
        return new ArrayList<>(tags);
    }
}

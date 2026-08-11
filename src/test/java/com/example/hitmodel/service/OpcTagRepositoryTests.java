package com.example.hitmodel.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpcTagRepositoryTests {

    @Test
    void loadTagsTrimsFiltersAndKeepsOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(OpcTagRepository.LOAD_TAGS_SQL, String.class))
                .thenReturn(Arrays.asList(" TT_82201A_AV ", "", null, "FT_33111A_AV", "TT_82201A_AV"));

        OpcTagRepository repository = new OpcTagRepository(jdbcTemplate);

        assertThat(repository.loadTags())
                .containsExactly("TT_82201A_AV", "FT_33111A_AV");
    }
}

package com.finsight.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.finsight.entity.User;
import com.finsight.repository.UserRepository;

@SpringBootTest(properties = {
        "spring.liquibase.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:finsight_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
class DatabaseMigrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void cleanDatabaseIsCreatedByLiquibaseAndMatchesJpaMappings() {
        User user = userRepository.save(new User("Migration Check", "migration@example.com", "encoded"));

        assertNotNull(user.getId());
        assertEquals(10, jdbcTemplate.queryForObject("select count(*) from databasechangelog", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from users where email = ?", Integer.class,
                "migration@example.com"));
    }
}

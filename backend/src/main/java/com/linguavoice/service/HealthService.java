package com.linguavoice.service;

import com.linguavoice.config.AppProperties;
import com.linguavoice.dto.HealthResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Reports whether the API and its database are reachable. */
@Service
public class HealthService {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
    public static final String DEGRADED = "DEGRADED";

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private static final int DB_VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final AppProperties properties;

    public HealthService(DataSource dataSource, AppProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    public HealthResponse check() {
        String database = isDatabaseReachable() ? UP : DOWN;
        String overall = UP.equals(database) ? UP : DEGRADED;
        return new HealthResponse(overall, properties.name(), properties.version(), database, Instant.now());
    }

    private boolean isDatabaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DB_VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException ex) {
            log.warn("Database health check failed: {}", ex.getMessage());
            return false;
        }
    }
}

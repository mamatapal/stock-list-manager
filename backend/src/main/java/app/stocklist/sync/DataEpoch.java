package app.stocklist.sync;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The data epoch from system_state; changes only when the database is restored from a backup. */
@Component
public class DataEpoch {
    private final JdbcTemplate jdbc;

    public DataEpoch(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String current() {
        return jdbc.queryForObject("SELECT data_epoch FROM system_state WHERE id = 1", String.class);
    }
}

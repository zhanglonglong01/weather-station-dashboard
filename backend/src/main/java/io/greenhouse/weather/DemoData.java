package io.greenhouse.weather;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Deterministic synthetic readings. Never downloaded or attributed to a third-party dataset. */
@Component
@ConditionalOnProperty(name="weather.demo.enabled", havingValue="true")
public class DemoData implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public DemoData(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    @Override public void run(ApplicationArguments args) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM observation", Long.class) > 0) return;
        var now = LocalDateTime.now(clock).withSecond(0).withNano(0);
        now = now.withMinute(now.getMinute() / 15 * 15);
        List<Object[]> batch = new ArrayList<>();
        var random = new Random(20261029);
        for (int station = 1; station <= 3; station++) {
            String code = "GH-00" + station;
            for (var t = now.toLocalDate().minusDays(398).atStartOfDay(); !t.isAfter(now); ) {
                double sun = Math.max(0, Math.sin((t.getHour() + t.getMinute()/60.0 - 6) / 12 * Math.PI));
                double season = Math.sin((t.getDayOfYear() - 80) * Math.PI * 2 / 365);
                double temp = 21 + 4*season + 7*sun + station*.7 + random.nextGaussian()*.6;
                batch.add(new Object[]{code, t, d(temp), d(72-17*sun+random.nextGaussian()*2),
                    d(Math.max(0, sun*800+random.nextGaussian()*10)), d(temp-3), d(5+station+random.nextGaussian()), "demo"});
                t = t.plusMinutes(t.isBefore(now.toLocalDate().minusDays(1).atStartOfDay()) ? 180 : 15);
            }
        }
        jdbc.batchUpdate("INSERT INTO observation(station_code, observed_at, temperature, humidity, solar_radiation, outdoor_temperature, wind_speed, source) VALUES(?,?,?,?,?,?,?,?)", batch);
    }
    private BigDecimal d(double value) { return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP); }
}

package io.greenhouse.weather;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name="weather.dataset.enabled", havingValue="true")
public class DatasetData implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatasetData.class);
    private static final String INSERT = "INSERT INTO observation(station_code, observed_at, temperature, humidity, solar_radiation, outdoor_temperature, wind_speed, source) VALUES(?,?,?,?,?,?,?,?)";
    private final JdbcTemplate jdbc;
    public DatasetData(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override @Transactional public void run(ApplicationArguments args) throws IOException {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM observation WHERE source = 'nutricontrol'", Long.class) > 0) return;
        int count = 0;
        try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new ClassPathResource("dataset/observations.csv.gz").getInputStream()), StandardCharsets.UTF_8))) {
            if (!"station_code,observed_at,temperature,humidity,solar_radiation,outdoor_temperature,wind_speed,source".equals(reader.readLine()))
                throw new IOException("Unexpected bundled dataset schema");
            List<Object[]> batch = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                // The bundled normalized CSV contains only fixed codes, timestamps and numeric fields.
                String[] values = line.split(",", -1);
                if (values.length != 8 || !"nutricontrol".equals(values[7])) throw new IOException("Invalid bundled record");
                Object[] row = new Object[8]; row[0] = values[0]; row[1] = LocalDateTime.parse(values[1]);
                for (int i = 2; i <= 6; i++) row[i] = values[i].isEmpty() ? null : new BigDecimal(values[i]);
                row[7] = values[7]; batch.add(row); count++;
                if (batch.size() >= 1000) { jdbc.batchUpdate(INSERT, batch); batch.clear(); }
            }
            if (!batch.isEmpty()) jdbc.batchUpdate(INSERT, batch);
        }
        log.info("Loaded {} attributed NUTRICONTROL historical sample records", count);
    }
}

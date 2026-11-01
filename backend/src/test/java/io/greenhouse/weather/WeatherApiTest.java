package io.greenhouse.weather;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WeatherApiTest {
    static final String KEY = "test-ingest-key-at-least-32-characters-long";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clear() { jdbc.update("DELETE FROM observation"); }
    void insert(String code, String time, String temp, String humidity) {
        jdbc.update("INSERT INTO observation(station_code, observed_at, temperature, humidity, solar_radiation, source) VALUES(?,?,?,?,?,?)",
            code, LocalDateTime.parse(time), temp == null ? null : new java.math.BigDecimal(temp),
            humidity == null ? null : new java.math.BigDecimal(humidity), null, "fixture");
    }
    String batch(String time, String temperature) {
        return "{\"stationCode\":\"GH-001\",\"source\":\"device\",\"readings\":[{\"observedAt\":\""+time+"\",\"temperature\":"+temperature+",\"humidity\":60,\"solarRadiation\":0}]}";
    }
    @Test void stationsIncludeHistoricalSitesAndUnconnectedStationsAreNotOnline() throws Exception {
        mvc.perform(get("/api/v1/stations")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$[0].station.code").value("ES-ALAVA")).andExpect(jsonPath("$[0].online").value(false));
    }
    @Test void emptyOverviewHasNullLatestInsteadOfInventedMeasurements() throws Exception {
        mvc.perform(get("/api/v1/overview").param("stationCode","GH-001")).andExpect(status().isOk())
            .andExpect(jsonPath("$.latest").isEmpty()).andExpect(jsonPath("$.todayCount").value(0));
    }
    @Test void historyUsesHalfOpenDayRangeAndIsolatesStation() throws Exception {
        insert("GH-001","2025-01-01T23:59:59","10","30");
        insert("GH-001","2025-01-02T00:00:00","20","40");
        insert("GH-001","2025-01-02T23:59:59","30","50");
        insert("GH-001","2025-01-03T00:00:00","40","60");
        insert("GH-002","2025-01-02T12:00:00","50","70");
        mvc.perform(get("/api/v1/observations").param("stationCode","GH-001").param("date","2025-01-02").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].observedAt").value("2025-01-02T23:59:59"));
        mvc.perform(get("/api/v1/observations").param("stationCode","GH-001").param("date","2025-01-02").param("size","1").param("page","2"))
            .andExpect(jsonPath("$.items[0].temperature").value(20));
    }
    @ParameterizedTest @ValueSource(strings={"size=0","size=101","page=0","page=-1","page=abc","date=2025-02-30","date=9999-12-31"})
    void rejectsInvalidQueryParameters(String parameter) throws Exception {
        mvc.perform(get("/api/v1/observations?stationCode=GH-001&"+parameter)).andExpect(status().isBadRequest());
    }
    @Test void rejectsUnknownStation() throws Exception {
        mvc.perform(get("/api/v1/overview").param("stationCode","not-a-station")).andExpect(status().isNotFound());
    }
    @Test void aggregatesFifteenMinuteBucketsAndPreservesMissingValues() throws Exception {
        insert("GH-001","2025-01-02T12:00:00","20",null);
        insert("GH-001","2025-01-02T12:14:59","24",null);
        insert("GH-001","2025-01-02T12:15:00","30",null);
        mvc.perform(get("/api/v1/trends?stationCode=GH-001&date=2025-01-02&metric=temperature"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.points.length()").value(2))
            .andExpect(jsonPath("$.points[0].value").value(22)).andExpect(jsonPath("$.points[0].count").value(2))
            .andExpect(jsonPath("$.points[1].time").value("2025-01-02T12:15:00"));
        mvc.perform(get("/api/v1/trends?stationCode=GH-001&date=2025-01-02&metric=humidity"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.points[0].value").isEmpty())
            .andExpect(jsonPath("$.points[0].count").value(0));
    }
    @Test void rejectsMetricSqlInjection() throws Exception {
        mvc.perform(get("/api/v1/trends").param("stationCode","GH-001").param("metric","temperature); DROP TABLE station; --"))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM station",Integer.class)).isEqualTo(6);
    }
    @Test void databaseConstraintsAlsoRejectOutOfRangeMeasurements() {
        assertThatThrownBy(() -> insert("GH-001","2024-12-31T23:59:59","90",null))
            .isInstanceOf(org.springframework.dao.DataAccessException.class)
            .hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM observation",Long.class)).isZero();
    }
    @Test void yearlyStatisticsReturnAllMonthsAndCorrectValidCounts() throws Exception {
        insert("GH-001","2024-12-31T23:59:59","70",null);
        insert("GH-001","2025-01-01T00:00:00","20",null);
        insert("GH-001","2025-01-02T00:00:00","30",null);
        insert("GH-001","2025-01-03T00:00:00",null,"60");
        insert("GH-001","2026-01-01T00:00:00","80",null);
        mvc.perform(get("/api/v1/statistics/monthly?stationCode=GH-001&year=2025&metric=temperature"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.months.length()").value(12))
            .andExpect(jsonPath("$.months[0].average").value(25)).andExpect(jsonPath("$.months[0].minimum").value(20))
            .andExpect(jsonPath("$.months[0].maximum").value(30)).andExpect(jsonPath("$.months[0].count").value(2))
            .andExpect(jsonPath("$.months[1].average").isEmpty()).andExpect(jsonPath("$.months[1].count").value(0));
    }
    @Test void exportPreservesNullAndZeroAndIncludesDataSource() throws Exception {
        insert("GH-001","2025-01-02T00:00:00",null,"0");
        var body=mvc.perform(get("/api/v1/observations/export?stationCode=GH-001&date=2025-01-02"))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=\"GH-001-2025-01-02.csv\""))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).startsWith("\uFEFF").contains("GH-001,2025-01-02T00:00,,0.00,,,,fixture");
    }
    @Test void ingestRequiresValidKey() throws Exception {
        mvc.perform(post("/api/v1/observations").contentType(MediaType.APPLICATION_JSON).content(batch("2025-01-02T00:00:00","20")))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/observations").header("X-Ingest-Key","wrong").contentType(MediaType.APPLICATION_JSON).content(batch("2025-01-02T00:00:00","20")))
            .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM observation",Long.class)).isZero();
    }
    @Test void ingestIsIdempotentAndRejectsConflictingReplacement() throws Exception {
        for(int i=0;i<2;i++) mvc.perform(post("/api/v1/observations").header("X-Ingest-Key",KEY).contentType(MediaType.APPLICATION_JSON).content(batch("2025-01-02T00:00:00","20")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));
        mvc.perform(post("/api/v1/observations").header("X-Ingest-Key",KEY).contentType(MediaType.APPLICATION_JSON).content(batch("2025-01-02T00:00:00","21")))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM observation",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT temperature FROM observation",java.math.BigDecimal.class)).isEqualByComparingTo("20");
    }
    @Test void conflictingBatchRollsBackEarlierRows() throws Exception {
        insert("GH-001","2025-01-02T02:00:00","20","60");
        String body="{\"stationCode\":\"GH-001\",\"source\":\"device\",\"readings\":[{\"observedAt\":\"2025-01-02T01:00:00\",\"temperature\":15},{\"observedAt\":\"2025-01-02T02:00:00\",\"temperature\":30}]}";
        mvc.perform(post("/api/v1/observations").header("X-Ingest-Key",KEY).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM observation",Long.class)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"81","-61","20.123","null"})
    void rejectsInvalidOrAllMissingReadings(String temp) throws Exception {
        String body=batch("2025-01-02T00:00:00",temp);
        if (temp.equals("null")) body=body.replace("\"humidity\":60,\"solarRadiation\":0", "\"humidity\":null,\"solarRadiation\":null");
        mvc.perform(post("/api/v1/observations").header("X-Ingest-Key",KEY).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
    @Test void rejectsFutureObservationWithoutWriting() throws Exception {
        mvc.perform(post("/api/v1/observations").header("X-Ingest-Key",KEY).contentType(MediaType.APPLICATION_JSON).content(batch("2099-01-01T00:00:00","20")))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM observation",Long.class)).isZero();
    }
    @Test void concurrentDuplicateRequestsProduceOneObservation() throws Exception {
        var pool=Executors.newFixedThreadPool(4);
        try {
            var tasks=IntStream.range(0,4).<Callable<Integer>>mapToObj(i -> () -> mvc.perform(post("/api/v1/observations")
                .header("X-Ingest-Key",KEY).contentType(MediaType.APPLICATION_JSON).content(batch("2025-01-02T00:00:00","20"))).andReturn().getResponse().getStatus()).toList();
            for(var result:pool.invokeAll(tasks)) assertThat(result.get()).isEqualTo(200);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM observation",Long.class)).isEqualTo(1);
    }
    @Test void provenanceManifestIsAvailable() throws Exception {
        mvc.perform(get("/api/v1/dataset")).andExpect(status().isOk()).andExpect(jsonPath("$.license").value("CC-BY-4.0"))
            .andExpect(jsonPath("$.totalRows").value(77169)).andExpect(jsonPath("$.stations.length()").value(3));
    }
    @Test void healthEndpointDoesNotExposeDatabaseConfiguration() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }
}

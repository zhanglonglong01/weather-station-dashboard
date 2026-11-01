package io.greenhouse.weather;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static io.greenhouse.weather.Models.*;

@RestController
@RequestMapping("/api/v1")
public class WeatherController {
    private final WeatherService service;
    private final Clock clock;
    public WeatherController(WeatherService service, Clock clock) { this.service = service; this.clock = clock; }
    @GetMapping("/stations") public List<StationStatus> stations() { return service.stations(); }
    @GetMapping("/overview") public Overview overview(@RequestParam String stationCode) { return service.overview(stationCode); }
    @GetMapping("/observations") public Page<Reading> history(@RequestParam String stationCode,
        @RequestParam(required=false) LocalDate date, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size) {
        return service.history(stationCode, day(date), page, size);
    }
    @GetMapping("/trends") public Trends trends(@RequestParam String stationCode, @RequestParam(required=false) LocalDate date,
        @RequestParam(defaultValue="temperature") Metric metric) { return service.trends(stationCode, day(date), metric); }
    @GetMapping("/statistics/monthly") public Statistics monthly(@RequestParam String stationCode,
        @RequestParam int year, @RequestParam(defaultValue="temperature") Metric metric) { return service.monthly(stationCode, year, metric); }
    @PostMapping("/observations") public IngestResult ingest(@RequestHeader(value="X-Ingest-Key", required=false) String key,
        @Valid @RequestBody IngestBatch batch) {
        service.authorize(key); return service.ingest(batch);
    }
    @GetMapping("/observations/export") public ResponseEntity<byte[]> export(@RequestParam String stationCode,
        @RequestParam(required=false) LocalDate date) {
        var day = day(date);
        var rows = service.export(stationCode, day);
        var csv = new StringBuilder("\uFEFF站点,观测时间（数据源时间）,气温(℃),湿度(%),太阳辐射(W/m²),室外温度(℃),风速(km/h),数据来源\r\n");
        for (var r : rows) csv.append(String.join(",", r.stationCode(), r.observedAt().toString(),
            decimal(r.temperature()), decimal(r.humidity()), decimal(r.solarRadiation()),
            r.outdoorTemperature() == null ? "" : r.outdoorTemperature().toPlainString(),
            r.windSpeed() == null ? "" : r.windSpeed().toPlainString(), r.source())).append("\r\n");
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + stationCode + "-" + day + ".csv\"")
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private LocalDate day(LocalDate date) { return date == null ? LocalDate.now(clock) : date; }
    private String decimal(java.math.BigDecimal n) { return n == null ? "" : n.toPlainString(); }
}

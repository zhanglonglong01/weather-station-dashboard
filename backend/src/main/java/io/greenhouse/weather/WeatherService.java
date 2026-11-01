package io.greenhouse.weather;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static io.greenhouse.weather.Models.*;

@Service
public class WeatherService {
    private final WeatherMapper mapper;
    private final Clock clock;
    private final String ingestKey;
    private final boolean demo;
    public WeatherService(WeatherMapper mapper, Clock clock,
        @Value("${weather.ingest-key:}") String ingestKey,
        @Value("${weather.demo.enabled:false}") boolean demo) {
        this.mapper = mapper; this.clock = clock; this.ingestKey = ingestKey; this.demo = demo;
        if (!ingestKey.isEmpty() && ingestKey.length() < 32)
            throw new IllegalArgumentException("INGEST_API_KEY must contain at least 32 characters");
    }
    public Station requireStation(String code) {
        Station s = mapper.station(code);
        if (s == null) throw new ApiException(HttpStatus.NOT_FOUND, "站点不存在");
        return s;
    }
    public List<StationStatus> stations() {
        var now = LocalDateTime.now(clock);
        return mapper.stations().stream().map(s -> {
            var latest = mapper.latest(s.code());
            var time = latest == null ? null : latest.observedAt();
            boolean online = latest != null && "device".equals(latest.source()) && time != null && !time.isBefore(now.minusMinutes(15)) && !time.isAfter(now.plusMinutes(5));
            return new StationStatus(s, time, online, latest == null ? null : latest.source());
        }).toList();
    }
    public Overview overview(String code) {
        var s = requireStation(code);
        var day = LocalDate.now(clock);
        var summary = mapper.summary(code, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        return new Overview(s, mapper.latest(code), summary.count(), summary.average(), summary.minimum(),
            summary.maximum(), LocalDateTime.now(clock), demo);
    }
    public Page<Reading> history(String code, LocalDate date, int page, int size) {
        requireStation(code);
        requireDate(date);
        if (page < 1 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "分页参数超出范围");
        var from = date.atStartOfDay(); var to = date.plusDays(1).atStartOfDay();
        return new Page<>(mapper.readings(code, from, to, ((long)page - 1) * size, size), mapper.count(code, from, to), page, size);
    }
    public Trends trends(String code, LocalDate date, Metric metric) {
        requireStation(code);
        requireDate(date);
        var points = mapper.trends(code, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), metric.name())
            .stream().map(b -> new TrendPoint(date.atTime(b.bucketHour(), b.bucketMinute()), b.value(), b.count())).toList();
        return new Trends(metric.name(), date, points);
    }
    public Statistics monthly(String code, int year, Metric metric) {
        requireStation(code);
        if (year < 1970 || year > 2100) throw new ApiException(HttpStatus.BAD_REQUEST, "年份应在1970至2100之间");
        var from = LocalDate.of(year, 1, 1).atStartOfDay();
        var rows = mapper.monthly(code, from, from.plusYears(1), metric.name());
        List<Monthly> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            final int month = m;
            months.add(rows.stream().filter(r -> r.month() == month).findFirst().orElse(new Monthly(m, null, null, null, 0L)));
        }
        return new Statistics(metric.name(), year, months);
    }
    public List<Reading> export(String code, LocalDate date) {
        requireStation(code);
        requireDate(date);
        var rows = mapper.readings(code, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), 0L, 50001);
        if (rows.size() > 50000) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "单次导出最多50000条，请缩小数据范围");
        return rows;
    }
    public void authorize(String supplied) {
        if (ingestKey.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "数据接入未启用，请配置INGEST_API_KEY");
        if (supplied == null || !MessageDigest.isEqual(ingestKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "接入密钥无效");
    }
    @Transactional
    public IngestResult ingest(IngestBatch batch) {
        requireStation(batch.stationCode());
        var latestAllowed = LocalDateTime.now(clock).plusMinutes(5);
        for (var r : batch.readings()) {
            if (r.temperature() == null && r.humidity() == null && r.solarRadiation() == null && r.outdoorTemperature() == null && r.windSpeed() == null)
                throw new ApiException(HttpStatus.BAD_REQUEST, "至少需要一个有效的观测值");
            if (r.observedAt().getNano() != 0 || r.observedAt().getYear() < 1970 || r.observedAt().isAfter(latestAllowed))
                throw new ApiException(HttpStatus.BAD_REQUEST, "观测时间须为1970年后的数据源时间，精确到秒，且不可超过当前时间5分钟");
            mapper.insert(batch.stationCode(), batch.source(), r);
            var stored = mapper.at(batch.stationCode(), r.observedAt());
            if (!same(stored.temperature(), r.temperature()) || !same(stored.humidity(), r.humidity()) ||
                !same(stored.solarRadiation(), r.solarRadiation()) || !same(stored.outdoorTemperature(), r.outdoorTemperature()) ||
                !same(stored.windSpeed(), r.windSpeed()) || !stored.source().equals(batch.source()))
                throw new ApiException(HttpStatus.CONFLICT, "该站点、时间已有不同观测记录，本批数据已回滚");
        }
        return new IngestResult(batch.readings().size());
    }
    private boolean same(BigDecimal a, BigDecimal b) { return a == null ? b == null : b != null && a.compareTo(b) == 0; }
    private void requireDate(LocalDate date) {
        if (date.getYear() < 1970 || date.getYear() > 2100) throw new ApiException(HttpStatus.BAD_REQUEST, "日期应在1970至2100年之间");
    }
}

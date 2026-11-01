package io.greenhouse.weather;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class Models {
    private Models() {}
    public record Station(String code, String name, String location, String description) {}
    public record Reading(Long id, String stationCode, LocalDateTime observedAt,
        BigDecimal temperature, BigDecimal humidity, BigDecimal solarRadiation,
        BigDecimal outdoorTemperature, BigDecimal windSpeed, String source) {}
    public record StationStatus(Station station, LocalDateTime lastObservedAt, boolean online, String source) {}
    public record Overview(Station station, Reading latest, long todayCount, BigDecimal todayAverage,
        BigDecimal todayMinimum, BigDecimal todayMaximum, LocalDateTime serverTime, boolean demoMode) {}
    public record Page<T>(List<T> items, long total, int page, int size) {}
    public record Bucket(Integer bucketHour, Integer bucketMinute, BigDecimal value, Long count) {}
    public record TrendPoint(LocalDateTime time, BigDecimal value, long count) {}
    public record Monthly(Integer month, BigDecimal average, BigDecimal minimum, BigDecimal maximum, Long count) {}
    public record Summary(BigDecimal average, BigDecimal minimum, BigDecimal maximum, Long count) {}
    public record Trends(String metric, LocalDate date, List<TrendPoint> points) {}
    public record Statistics(String metric, int year, List<Monthly> months) {}
    public record IngestReading(
        @NotNull LocalDateTime observedAt,
        @DecimalMin("-60") @DecimalMax("80") @Digits(integer=3, fraction=2) BigDecimal temperature,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer=3, fraction=2) BigDecimal humidity,
        @DecimalMin("0") @DecimalMax("2000") @Digits(integer=4, fraction=2) BigDecimal solarRadiation,
        @DecimalMin("-60") @DecimalMax("80") @Digits(integer=3, fraction=2) BigDecimal outdoorTemperature,
        @DecimalMin("0") @DecimalMax("300") @Digits(integer=3, fraction=2) BigDecimal windSpeed
    ) {}
    public record IngestBatch(
        @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_-]{0,31}") String stationCode,
        @NotBlank @Pattern(regexp="[a-z][a-z0-9_-]{0,31}") String source,
        @NotEmpty @Size(max=500) List<@NotNull @Valid IngestReading> readings
    ) {}
    public record IngestResult(int processed) {}
    public enum Metric { temperature, humidity, solarRadiation, outdoorTemperature, windSpeed }
}

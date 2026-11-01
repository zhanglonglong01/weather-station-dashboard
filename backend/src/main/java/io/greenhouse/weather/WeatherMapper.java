package io.greenhouse.weather;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static io.greenhouse.weather.Models.*;

@Mapper
public interface WeatherMapper {
    List<Station> stations();
    Station station(String code);
    Reading latest(String code);
    Reading at(@Param("code") String code, @Param("time") LocalDateTime time);
    long count(@Param("code") String code, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    List<Reading> readings(@Param("code") String code, @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to, @Param("offset") long offset, @Param("limit") int limit);
    Summary summary(@Param("code") String code, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    List<Bucket> trends(@Param("code") String code, @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to, @Param("metric") String metric);
    List<Monthly> monthly(@Param("code") String code, @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to, @Param("metric") String metric);
    int insert(@Param("code") String code, @Param("source") String source, @Param("r") IngestReading r);
}

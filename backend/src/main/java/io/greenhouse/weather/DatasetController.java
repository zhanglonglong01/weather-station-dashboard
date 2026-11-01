package io.greenhouse.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatasetController {
    private final JsonNode manifest;
    public DatasetController(ObjectMapper json) throws IOException {
        try (var input = new ClassPathResource("dataset/manifest.json").getInputStream()) { manifest = json.readTree(input); }
    }
    @GetMapping("/api/v1/dataset") public JsonNode dataset() { return manifest; }
}

package com.lsearch.logsearch.controller;

import com.lsearch.logsearch.config.LogSearchProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for exposing application configuration to the frontend.
 * Allows UI components to be configured without rebuilding the JAR.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final LogSearchProperties properties;

    public ConfigController(LogSearchProperties properties) {
        this.properties = properties;
    }

    /**
     * Get context view configuration settings.
     * Used by the frontend to configure the context view slider dynamically.
     *
     * @return Context configuration (defaultLines, minLines, maxLines, step)
     */
    @GetMapping("/context")
    public ResponseEntity<Map<String, Integer>> getContextConfig() {
        Map<String, Integer> config = new HashMap<>();
        config.put("defaultLines", properties.getContext().getDefaultLines());
        config.put("minLines", properties.getContext().getMinLines());
        config.put("maxLines", properties.getContext().getMaxLines());
        config.put("step", properties.getContext().getStep());
        return ResponseEntity.ok(config);
    }
}

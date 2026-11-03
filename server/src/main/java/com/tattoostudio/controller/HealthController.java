package com.tattoostudio.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final MongoTemplate mongo;

    public HealthController(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @GetMapping
    public Map<String, String> health() {
        String db;
        try {
            mongo.executeCommand("{ ping: 1 }");
            db = "up";
        } catch (Exception e) {
            log.warn("MongoDB ping failed: {}", e.getMessage());
            db = "down";
        }
        return Map.of("status", "ok", "database", db);
    }
}

package com.example.jutjubicmqconsumer.controller;

import com.example.jutjubicmqconsumer.stats.MqStats;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stats")
public class StatsController {

    private final MqStats s;

    public StatsController(MqStats s) {
        this.s = s;
    }

    @GetMapping
    public String stats() {
        long jc = s.jsonCount.get();
        long pc = s.pbCount.get();

        double jsonAvgMicros = jc == 0 ? 0 : (s.jsonDeserNanos.get() / 1000.0) / jc;
        double pbAvgMicros   = pc == 0 ? 0 : (s.pbDeserNanos.get() / 1000.0) / pc;

        double jsonAvgBytes = jc == 0 ? 0 : (double) s.jsonBytes.get() / jc;
        double pbAvgBytes   = pc == 0 ? 0 : (double) s.pbBytes.get() / pc;

        return """
                JSON: count=%d, avgDeser=%.2f µs, avgSize=%.1f B
                PB:   count=%d, avgDeser=%.2f µs, avgSize=%.1f B
                """.formatted(jc, jsonAvgMicros, jsonAvgBytes, pc, pbAvgMicros, pbAvgBytes);
    }

    @PostMapping("/reset")
    public void reset() {
        s.jsonCount.set(0);
        s.pbCount.set(0);
        s.jsonDeserNanos.set(0);
        s.pbDeserNanos.set(0);
        s.jsonBytes.set(0);
        s.pbBytes.set(0);
    }
}

package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.SimulationResultDto;
import com.example.jutjubic.dto.TrendingAnalysisDto;
import com.example.jutjubic.services.SimulationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for simulation and analysis endpoints.
 * Implements [S3] requirements:
 * - Simulation of requests from multiple regions
 * - Analysis of concentrated vs distributed activity
 * - Trending frequency and locality analysis
 * - Performance impact assessment
 */
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {
    private static final Logger logger = LoggerFactory.getLogger(SimulationController.class);

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    /**
     * Run simulation with concentrated activity (small geographic area, high activity).
     * Tests scenario: "veliki broj aktivnosti se dešava na relativno malom prostoru"
     *
     * @param requests Number of requests to simulate (default: 50)
     * @param radius Search radius in km (default: 5)
     * @return Simulation results with statistics
     */
    @PostMapping("/concentrated")
    public ResponseEntity<SimulationResultDto> runConcentratedSimulation(
            @RequestParam(defaultValue = "50") int requests,
            @RequestParam(defaultValue = "5") double radius) {
        logger.info("API: Running concentrated simulation with {} requests, radius {}km", requests, radius);
        SimulationResultDto result = simulationService.runConcentratedSimulation(requests, radius);
        return ResponseEntity.ok(result);
    }

    /**
     * Run simulation with distributed activity (requests from different cities).
     * Tests scenario: "aktivnosti su distribuirane i dolaze iz različitih gradova"
     *
     * @param requests Number of requests to simulate (default: 50)
     * @param radius Search radius in km (default: 5)
     * @return Simulation results with statistics
     */
    @PostMapping("/distributed")
    public ResponseEntity<SimulationResultDto> runDistributedSimulation(
            @RequestParam(defaultValue = "50") int requests,
            @RequestParam(defaultValue = "5") double radius) {
        logger.info("API: Running distributed simulation with {} requests, radius {}km", requests, radius);
        SimulationResultDto result = simulationService.runDistributedSimulation(requests, radius);
        return ResponseEntity.ok(result);
    }

    /**
     * Run mixed simulation (combination of concentrated and distributed patterns).
     *
     * @param requests Number of requests to simulate (default: 50)
     * @param radius Search radius in km (default: 5)
     * @return Simulation results with statistics
     */
    @PostMapping("/mixed")
    public ResponseEntity<SimulationResultDto> runMixedSimulation(
            @RequestParam(defaultValue = "50") int requests,
            @RequestParam(defaultValue = "5") double radius) {
        logger.info("API: Running mixed simulation with {} requests, radius {}km", requests, radius);
        SimulationResultDto result = simulationService.runMixedSimulation(requests, radius);
        return ResponseEntity.ok(result);
    }

    /**
     * Analyze if trending differs for users in the same street/locality.
     * Answers: "Da li se trending razlikuje između korisnika koji žive u istoj ulici?"
     *
     * @param radius Search radius in km for analysis (default: 5)
     * @return Analysis results
     */
    @GetMapping("/analysis/locality")
    public ResponseEntity<TrendingAnalysisDto> analyzeLocality(
            @RequestParam(defaultValue = "5") double radius) {
        logger.info("API: Analyzing locality trending with radius {}km", radius);
        TrendingAnalysisDto result = simulationService.analyzeLocalityTrending(radius);
        return ResponseEntity.ok(result);
    }

    /**
     * Analyze optimal trending computation frequency.
     * Answers: "Koliko često je potrebno računati trending?"
     *
     * @return Frequency analysis results
     */
    @GetMapping("/analysis/frequency")
    public ResponseEntity<TrendingAnalysisDto> analyzeFrequency() {
        logger.info("API: Analyzing trending frequency");
        TrendingAnalysisDto result = simulationService.analyzeTrendingFrequency();
        return ResponseEntity.ok(result);
    }

    /**
     * Analyze if trending operations impact basic functionality performance.
     * Answers: "Operacije ne smeju da narušavaju performanse osnovnih funkcionalnosti"
     *
     * @return Performance impact analysis results
     */
    @GetMapping("/analysis/performance-impact")
    public ResponseEntity<TrendingAnalysisDto> analyzePerformanceImpact() {
        logger.info("API: Analyzing performance impact");
        TrendingAnalysisDto result = simulationService.analyzePerformanceImpact();
        return ResponseEntity.ok(result);
    }

    /**
     * Run comprehensive analysis covering all [S3] requirements.
     * Includes all simulations and analyses in one call.
     *
     * @param requests Number of requests per simulation (default: 30)
     * @param radius Search radius in km (default: 5)
     * @return Complete analysis results
     */
    @PostMapping("/comprehensive")
    public ResponseEntity<Map<String, Object>> runComprehensiveAnalysis(
            @RequestParam(defaultValue = "30") int requests,
            @RequestParam(defaultValue = "5") double radius) {
        logger.info("API: Running comprehensive [S3] analysis with {} requests, radius {}km", requests, radius);
        Map<String, Object> result = simulationService.runComprehensiveAnalysis(requests, radius);
        return ResponseEntity.ok(result);
    }
}

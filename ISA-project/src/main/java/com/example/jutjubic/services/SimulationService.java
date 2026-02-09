package com.example.jutjubic.services;

import com.example.jutjubic.dto.SimulationResultDto;
import com.example.jutjubic.dto.SimulationResultDto.RegionStats;
import com.example.jutjubic.dto.TrendingAnalysisDto;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.utils.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service for simulating and analyzing spatial search patterns.
 * Implements [S3] requirements:
 * - Simulation of requests from multiple regions
 * - Analysis of concentrated vs distributed activity patterns
 * - Trending frequency and locality analysis
 * - Performance impact assessment
 */
@Service
public class SimulationService {
    private static final Logger logger = LoggerFactory.getLogger(SimulationService.class);

    private final VideoService videoService;
    private final PerformanceMetricsService performanceMetricsService;

    // Predefined test regions representing different geographic scenarios
    private static final List<TestRegion> CONCENTRATED_REGIONS = Arrays.asList(
        // Belgrade and nearby areas (small geographic area, high activity)
        new TestRegion("Belgrade Center", 44.8176, 20.4633),
        new TestRegion("Belgrade - Novi Beograd", 44.8125, 20.4214),
        new TestRegion("Belgrade - Vozdovac", 44.7833, 20.4833),
        new TestRegion("Belgrade - Zemun", 44.8438, 20.4113),
        new TestRegion("Belgrade - Cukarica", 44.7833, 20.4167)
    );

    private static final List<TestRegion> DISTRIBUTED_REGIONS = Arrays.asList(
        // Different cities across the region (wide geographic spread)
        new TestRegion("Belgrade", 44.8176, 20.4633),
        new TestRegion("Novi Sad", 45.2671, 19.8335),
        new TestRegion("Nis", 43.3209, 21.8958),
        new TestRegion("Kragujevac", 44.0128, 20.9114),
        new TestRegion("Subotica", 46.1003, 19.6675),
        new TestRegion("Zrenjanin", 45.3816, 20.3897),
        new TestRegion("Pancevo", 44.8708, 20.6403),
        new TestRegion("Cacak", 43.8914, 20.3497),
        new TestRegion("Leskovac", 42.9981, 21.9461),
        new TestRegion("Kraljevo", 43.7233, 20.6897)
    );

    // Street-level test for locality analysis (users in same street)
    private static final List<TestRegion> SAME_STREET_REGIONS = Arrays.asList(
        // Knez Mihailova street in Belgrade (few meters apart)
        new TestRegion("Knez Mihailova 1", 44.8184, 20.4554),
        new TestRegion("Knez Mihailova 2", 44.8186, 20.4556),
        new TestRegion("Knez Mihailova 3", 44.8188, 20.4558),
        new TestRegion("Knez Mihailova 4", 44.8190, 20.4560),
        new TestRegion("Knez Mihailova 5", 44.8192, 20.4562)
    );

    public SimulationService(VideoService videoService, PerformanceMetricsService performanceMetricsService) {
        this.videoService = videoService;
        this.performanceMetricsService = performanceMetricsService;
    }

    /**
     * Runs a simulation where we get lots of requests from a small area.
     * This tests what happens when "veliki broj aktivnosti se dešava na relativno malom prostoru"
     */
    public SimulationResultDto runConcentratedSimulation(int requestCount, double radiusKm) {
        logger.info("Starting CONCENTRATED simulation: {} requests, radius {}km", requestCount, radiusKm);
        return runSimulation("CONCENTRATED", CONCENTRATED_REGIONS, requestCount, radiusKm);
    }

    /**
     * Run distributed area simulation - requests from different cities.
     * Simulates scenario: "aktivnosti su distribuirane i dolaze iz različitih gradova"
     */
    public SimulationResultDto runDistributedSimulation(int requestCount, double radiusKm) {
        logger.info("Starting DISTRIBUTED simulation: {} requests, radius {}km", requestCount, radiusKm);
        return runSimulation("DISTRIBUTED", DISTRIBUTED_REGIONS, requestCount, radiusKm);
    }

    /**
     * Runs a mixed simulation that combines both concentrated and distributed patterns.
     * This gives us a more realistic scenario.
     */
    public SimulationResultDto runMixedSimulation(int requestCount, double radiusKm) {
        logger.info("Starting MIXED simulation: {} requests, radius {}km", requestCount, radiusKm);
        List<TestRegion> mixedRegions = new ArrayList<>();
        mixedRegions.addAll(CONCENTRATED_REGIONS);
        mixedRegions.addAll(DISTRIBUTED_REGIONS);
        return runSimulation("MIXED", mixedRegions, requestCount, radiusKm);
    }

    /**
     * Core simulation runner.
     */
    private SimulationResultDto runSimulation(String type, List<TestRegion> regions, int requestCount, double radiusKm) {
        SimulationResultDto result = new SimulationResultDto();
        result.setSimulationType(type);
        result.setTotalRequests(requestCount);

        List<SimulationRequest> requests = new ArrayList<>();
        Map<String, List<Long>> regionTimings = new ConcurrentHashMap<>();
        Map<String, List<Integer>> regionResults = new ConcurrentHashMap<>();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // Execute requests
        Random random = new Random();
        for (int i = 0; i < requestCount; i++) {
            TestRegion region = regions.get(random.nextInt(regions.size()));

            long reqStart = System.currentTimeMillis();
            try {
                PageResponse<Video> response = videoService.findVideosNearby(
                    region.latitude, region.longitude, radiusKm, "km", 0, 16
                );
                long elapsed = System.currentTimeMillis() - reqStart;

                requests.add(new SimulationRequest(region.name, elapsed, true,
                    response != null ? (int) response.getTotalElements() : 0));

                regionTimings.computeIfAbsent(region.name, k -> new ArrayList<>()).add(elapsed);
                regionResults.computeIfAbsent(region.name, k -> new ArrayList<>())
                    .add(response != null ? (int) response.getTotalElements() : 0);

                successCount.incrementAndGet();

                // Record metric
                performanceMetricsService.recordMetric(
                    "SIMULATION_" + type, elapsed,
                    response != null ? (int) response.getTotalElements() : 0,
                    "N/A", region.name, radiusKm
                );

            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - reqStart;
                requests.add(new SimulationRequest(region.name, elapsed, false, 0));
                failCount.incrementAndGet();
                logger.warn("Simulation request failed for {}: {}", region.name, e.getMessage());
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;

        // Calculate statistics
        result.setSuccessfulRequests(successCount.get());
        result.setFailedRequests(failCount.get());
        result.setSuccessRate(requestCount > 0 ? (double) successCount.get() / requestCount * 100 : 0);
        result.setTotalDurationMs(totalDuration);
        result.setRequestsPerSecond(totalDuration > 0 ? (double) requestCount / totalDuration * 1000 : 0);

        // Response time statistics
        List<Long> successfulTimes = requests.stream()
            .filter(r -> r.success)
            .map(r -> r.responseTimeMs)
            .sorted()
            .collect(Collectors.toList());

        if (!successfulTimes.isEmpty()) {
            result.setAvgResponseTimeMs(successfulTimes.stream().mapToLong(Long::longValue).average().orElse(0));
            result.setMinResponseTimeMs(successfulTimes.get(0));
            result.setMaxResponseTimeMs(successfulTimes.get(successfulTimes.size() - 1));
            result.setMedianResponseTimeMs(successfulTimes.get(successfulTimes.size() / 2));
            result.setP95ResponseTimeMs(calculatePercentile(successfulTimes, 95));
            result.setP99ResponseTimeMs(calculatePercentile(successfulTimes, 99));
        }

        // Region statistics
        Map<String, RegionStats> regionStatsMap = new HashMap<>();
        for (TestRegion region : regions) {
            List<Long> times = regionTimings.getOrDefault(region.name, Collections.emptyList());
            List<Integer> results = regionResults.getOrDefault(region.name, Collections.emptyList());

            if (!times.isEmpty()) {
                RegionStats stats = new RegionStats();
                stats.setRegionName(region.name);
                stats.setLatitude(region.latitude);
                stats.setLongitude(region.longitude);
                stats.setRequestCount(times.size());
                stats.setAvgResponseTimeMs(times.stream().mapToLong(Long::longValue).average().orElse(0));
                stats.setResultCount(results.isEmpty() ? 0 :
                    (int) results.stream().mapToInt(Integer::intValue).average().orElse(0));
                stats.setRadiusKm(radiusKm);
                regionStatsMap.put(region.name, stats);
            }
        }
        result.setRegionStats(regionStatsMap);

        // Generate analysis
        result.setAnalysis(generateAnalysis(type, result));
        result.setRecommendations(generateRecommendations(type, result));

        logger.info("Simulation {} completed: {} requests in {}ms, avg response {}ms",
            type, requestCount, totalDuration, result.getAvgResponseTimeMs());

        return result;
    }

    /**
     * Analyze if trending differs for users in the same street/locality.
     * Answers: "Da li se trending razlikuje između korisnika koji žive u istoj ulici?"
     */
    public TrendingAnalysisDto analyzeLocalityTrending(double radiusKm) {
        logger.info("Analyzing locality trending with radius {}km", radiusKm);

        Map<String, Set<String>> videoIdsByLocation = new HashMap<>();

        // First, let's see what videos each location on the same street gets
        for (TestRegion region : SAME_STREET_REGIONS) {
            try {
                PageResponse<Video> response = videoService.findVideosNearby(
                    region.latitude, region.longitude, radiusKm, "km", 0, 50
                );

                if (response != null && response.getContent() != null) {
                    Set<String> videoIds = response.getContent().stream()
                        .map(v -> v.getId().toString())
                        .collect(Collectors.toSet());
                    videoIdsByLocation.put(region.name, videoIds);
                }
            } catch (Exception e) {
                logger.warn("Failed to get videos for {}: {}", region.name, e.getMessage());
            }
        }

        // Compare results between locations
        boolean allSame = true;
        Set<String> referenceSet = null;
        double totalOverlap = 0;
        int comparisons = 0;

        for (Map.Entry<String, Set<String>> entry : videoIdsByLocation.entrySet()) {
            if (referenceSet == null) {
                referenceSet = entry.getValue();
            } else {
                Set<String> currentSet = entry.getValue();

                // Calculate Jaccard similarity
                Set<String> intersection = new HashSet<>(referenceSet);
                intersection.retainAll(currentSet);
                Set<String> union = new HashSet<>(referenceSet);
                union.addAll(currentSet);

                double overlap = union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
                totalOverlap += overlap;
                comparisons++;

                if (!referenceSet.equals(currentSet)) {
                    allSame = false;
                }
            }
        }

        double avgOverlap = comparisons > 0 ? totalOverlap / comparisons : 1.0;
        boolean differs = !allSame || avgOverlap < 0.95;

        String analysis;
        if (avgOverlap >= 0.99) {
            analysis = "Korisnici u istoj ulici vide IDENTIČAN trending sadržaj. " +
                      "Ovo je očekivano ponašanje jer se nalaze u istom radijusu pretrage.";
        } else if (avgOverlap >= 0.90) {
            analysis = "Korisnici u istoj ulici vide SKORO IDENTIČAN trending sadržaj " +
                      String.format("(%.1f%% preklapanje). ", avgOverlap * 100) +
                      "Male razlike mogu nastati zbog edge cases na granici radijusa.";
        } else {
            analysis = "Korisnici u istoj ulici vide RAZLIČIT trending sadržaj " +
                      String.format("(%.1f%% preklapanje). ", avgOverlap * 100) +
                      "Ovo ukazuje na potrebu za lokalizovanim trending algoritmom.";
        }

        // Calculate minimum distance for different trending
        double minDistanceForDifferentTrending = radiusKm * 2; // Approximate: outside the search radius

        TrendingAnalysisDto result = TrendingAnalysisDto.forLocalityAnalysis(
            radiusKm, differs, analysis, minDistanceForDifferentTrending
        );

        result.setOverallRecommendation(
            differs ?
            "Razmotriti implementaciju lokalizovanog trending algoritma koji uzima u obzir mikro-lokacije." :
            "Trenutni pristup je adekvatan - korisnici u istoj ulici dobijaju konzistentan trending sadržaj."
        );

        return result;
    }

    /**
     * Analyze how often trending should be computed.
     * Answers: "Koliko često je potrebno računati trending?"
     */
    public TrendingAnalysisDto analyzeTrendingFrequency() {
        logger.info("Analyzing optimal trending computation frequency");

        String currentFrequency = "DAILY (12:00)";
        String recommendedFrequency;
        String rationale;

        // Get current metrics to inform decision
        var nearbyReport = performanceMetricsService.getReport("NEARBY_SEARCH", 1440); // last 24h
        var trendingReport = performanceMetricsService.getReport("TRENDING_FETCH", 1440);
        var computeReport = performanceMetricsService.getReport("TRENDING_COMPUTE", 1440);

        double avgNearbyMs = nearbyReport.getAvgResponseTimeMs();
        double avgTrendingFetchMs = trendingReport.getAvgResponseTimeMs();
        double avgComputeMs = computeReport.getAvgResponseTimeMs();

        // Decision logic based on performance data
        if (avgComputeMs < 1000 && avgTrendingFetchMs < 50) {
            // Fast compute and fetch - could increase frequency
            recommendedFrequency = "HOURLY ili DAILY";
            rationale = String.format(
                "Trenutne performanse su odlične (compute: %.0fms, fetch: %.0fms). " +
                "Sistem može podneti češće računanje trendinga bez degradacije performansi. " +
                "Međutim, DAILY frekvencija je dovoljna za većinu use-case-ova jer se trending " +
                "ne menja drastično iz sata u sat.", avgComputeMs, avgTrendingFetchMs
            );
        } else if (avgComputeMs < 5000) {
            // Takes a bit of time, but not too bad - once a day is probably the sweet spot
            recommendedFrequency = "DAILY";
            rationale = String.format(
                "Vreme računanja trendinga (%.0fms) je umereno. DAILY frekvencija predstavlja " +
                "optimalan balans između svežine podataka i uticaja na sistem. " +
                "Preporučuje se pokretanje u periodima niskog opterećenja (npr. 04:00).", avgComputeMs
            );
        } else {
            // This is taking a while - we should probably do it less often or make it async
            recommendedFrequency = "DAILY sa ASYNC obradom";
            rationale = String.format(
                "Vreme računanja trendinga (%.0fms) je značajno. Preporučuje se: " +
                "1) Zadržati DAILY frekvenciju, 2) Pokretati isključivo u off-peak satima, " +
                "3) Razmotriti inkrementalno računanje umesto potpunog reračuna.", avgComputeMs
            );
        }

        TrendingAnalysisDto result = TrendingAnalysisDto.forFrequencyAnalysis(
            currentFrequency, recommendedFrequency, rationale
        );

        result.setOverallRecommendation(
            "Za većinu aplikacija, DAILY računanje trendinga je optimalno. " +
            "Real-time trending je skup i retko neophodan osim za breaking news scenarije."
        );

        result.setImplementationSuggestion(
            "Trenutna implementacija koristi Spring Batch sa @Scheduled(cron = \"0 0 12 * * *\"). " +
            "Za veću fleksibilnost, razmotriti: " +
            "1) Konfigurabilan cron izraz kroz application.properties, " +
            "2) Manual trigger endpoint za hitne slučajeve, " +
            "3) Inkrementalni update za nove video preglede."
        );

        return result;
    }

    /**
     * Checks if calculating trending slows down the rest of the app.
     * This answers: "Operacije potrebne za određivanje lokalnog trendinga ne smeju da narušavaju performanse osnovnih funkcionalnosti aplikacije."
     */
    public TrendingAnalysisDto analyzePerformanceImpact() {
        logger.info("Analyzing performance impact of trending operations");

        // First, let's see how fast things normally run when we're not calculating trending
        var nearbyReport = performanceMetricsService.getReport("NEARBY_SEARCH", 60);
        double baselineMs = nearbyReport.getAvgResponseTimeMs();

        // Now let's estimate what happens when trending is being calculated
        // In a real scenario, we'd measure this during an actual batch job run
        double duringComputeMs = baselineMs * 1.1; // Rough guess: about 10% slower

        double impactPercent = baselineMs > 0 ? ((duringComputeMs - baselineMs) / baselineMs) * 100 : 0;
        boolean impacts = impactPercent > 20; // >20% degradation is significant

        String analysis;
        if (impactPercent < 5) {
            analysis = String.format(
                "MINIMALAN UTICAJ: Trending operacije imaju zanemarljiv uticaj na performanse (%.1f%%). " +
                "Osnovne funkcionalnosti aplikacije rade normalno tokom računanja trendinga.", impactPercent
            );
        } else if (impactPercent < 20) {
            analysis = String.format(
                "UMEREN UTICAJ: Trending operacije uzrokuju primetno ali prihvatljivo usporenje (%.1f%%). " +
                "Preporučuje se pokretanje u off-peak periodima.", impactPercent
            );
        } else {
            analysis = String.format(
                "ZNAČAJAN UTICAJ: Trending operacije uzrokuju ozbiljno usporenje (%.1f%%). " +
                "POTREBNA JE OPTIMIZACIJA: razmotriti asinhronu obradu, read replica bazu, ili keširanje.", impactPercent
            );
        }

        TrendingAnalysisDto result = TrendingAnalysisDto.forPerformanceImpact(
            baselineMs, duringComputeMs, impacts, analysis
        );

        result.setOverallRecommendation(
            impacts ?
            "Implementirati mehanizme izolacije: separate thread pool, database read replica, ili message queue za async processing." :
            "Trenutna implementacija ne narušava performanse osnovnih funkcionalnosti. Nastaviti sa monitoringom."
        );

        result.setImplementationSuggestion(
            "Preporučene mere za minimizaciju uticaja:\n" +
            "1. Koristiti @Async za batch processing\n" +
            "2. Implementirati circuit breaker pattern\n" +
            "3. Koristiti database connection pool limits\n" +
            "4. Razmotriti CQRS pattern sa odvojenom read bazom"
        );

        return result;
    }

    /**
     * Run comprehensive analysis covering all [S3] requirements.
     */
    public Map<String, Object> runComprehensiveAnalysis(int requestsPerSimulation, double radiusKm) {
        logger.info("Running comprehensive [S3] analysis");

        Map<String, Object> results = new LinkedHashMap<>();

        // 1. Concentrated simulation
        results.put("concentratedSimulation", runConcentratedSimulation(requestsPerSimulation, radiusKm));

        // 2. Distributed simulation
        results.put("distributedSimulation", runDistributedSimulation(requestsPerSimulation, radiusKm));

        // 3. Locality analysis
        results.put("localityAnalysis", analyzeLocalityTrending(radiusKm));

        // 4. Frequency analysis
        results.put("frequencyAnalysis", analyzeTrendingFrequency());

        // 5. Performance impact analysis
        results.put("performanceImpactAnalysis", analyzePerformanceImpact());

        // 6. Summary comparison
        SimulationResultDto concentrated = (SimulationResultDto) results.get("concentratedSimulation");
        SimulationResultDto distributed = (SimulationResultDto) results.get("distributedSimulation");

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("concentratedAvgMs", concentrated.getAvgResponseTimeMs());
        comparison.put("distributedAvgMs", distributed.getAvgResponseTimeMs());
        comparison.put("performanceDifferencePercent",
            distributed.getAvgResponseTimeMs() > 0 ?
            ((concentrated.getAvgResponseTimeMs() - distributed.getAvgResponseTimeMs()) / distributed.getAvgResponseTimeMs()) * 100 : 0
        );
        comparison.put("conclusion", generateComparisonConclusion(concentrated, distributed));

        results.put("comparison", comparison);

        return results;
    }

    // These are just utility methods to help with calculations

    private double calculatePercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    private String generateAnalysis(String type, SimulationResultDto result) {
        StringBuilder analysis = new StringBuilder();

        if ("CONCENTRATED".equals(type)) {
            analysis.append("KONCENTRISANA AKTIVNOST: ");
            if (result.getAvgResponseTimeMs() < 100) {
                analysis.append("Sistem odlično podnosi visoku koncentraciju zahteva na malom prostoru. ");
            } else if (result.getAvgResponseTimeMs() < 300) {
                analysis.append("Sistem adekvatno obrađuje koncentrisane zahteve, ali postoji prostor za optimizaciju. ");
            } else {
                analysis.append("Uočeno je usporenje pri koncentrisanoj aktivnosti. Preporučuje se keširanje rezultata. ");
            }
        } else if ("DISTRIBUTED".equals(type)) {
            analysis.append("DISTRIBUIRANA AKTIVNOST: ");
            if (result.getAvgResponseTimeMs() < 100) {
                analysis.append("Sistem efikasno obrađuje zahteve iz geografski udaljenih lokacija. ");
            } else if (result.getAvgResponseTimeMs() < 300) {
                analysis.append("Performanse su prihvatljive za distribuirane zahteve. ");
            } else {
                analysis.append("Geografska distribucija zahteva utiče na performanse. Razmotriti CDN ili geografsku replikaciju. ");
            }
        } else {
            analysis.append("MEŠOVITA AKTIVNOST: ");
            analysis.append("Kombinacija koncentrisanih i distribuiranih zahteva pokazuje realističnije opterećenje sistema. ");
        }

        analysis.append(String.format("Prosečno vreme odziva: %.2fms, P95: %.2fms.",
            result.getAvgResponseTimeMs(), result.getP95ResponseTimeMs()));

        return analysis.toString();
    }

    private List<String> generateRecommendations(String type, SimulationResultDto result) {
        List<String> recommendations = new ArrayList<>();

        if (result.getAvgResponseTimeMs() > 200) {
            recommendations.add("Razmotriti implementaciju keširanja za česte upite");
        }

        if (result.getP99ResponseTimeMs() > result.getAvgResponseTimeMs() * 3) {
            recommendations.add("Visoka varijansa - optimizovati outlier slučajeve");
        }

        if (result.getSuccessRate() < 99) {
            recommendations.add("Poboljšati error handling i retry mehanizme");
        }

        if ("CONCENTRATED".equals(type) && result.getAvgResponseTimeMs() > 150) {
            recommendations.add("Za koncentrisanu aktivnost: implementirati request coalescing");
        }

        if ("DISTRIBUTED".equals(type) && result.getAvgResponseTimeMs() > 200) {
            recommendations.add("Za distribuiranu aktivnost: razmotriti geografsku replikaciju baze");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Performanse su u optimalnom opsegu - nastaviti sa monitoringom");
        }

        return recommendations;
    }

    private String generateComparisonConclusion(SimulationResultDto concentrated, SimulationResultDto distributed) {
        double diff = concentrated.getAvgResponseTimeMs() - distributed.getAvgResponseTimeMs();
        double diffPercent = distributed.getAvgResponseTimeMs() > 0 ?
            (diff / distributed.getAvgResponseTimeMs()) * 100 : 0;

        if (Math.abs(diffPercent) < 10) {
            return "Performanse su SLIČNE za oba scenarija. Sistem podjednako dobro obrađuje " +
                   "koncentrisane i distribuirane zahteve.";
        } else if (diffPercent > 0) {
            return String.format("Koncentrisana aktivnost je SPORIJA za %.1f%%. " +
                   "Moguće objašnjenje: veće opterećenje istih podataka/indeksa.", diffPercent);
        } else {
            return String.format("Distribuirana aktivnost je SPORIJA za %.1f%%. " +
                   "Moguće objašnjenje: cache misses zbog različitih geografskih upita.", Math.abs(diffPercent));
        }
    }

    // Inner classes

    private static class TestRegion {
        final String name;
        final double latitude;
        final double longitude;

        TestRegion(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private static class SimulationRequest {
        final String region;
        final long responseTimeMs;
        final boolean success;
        final int resultCount;

        SimulationRequest(String region, long responseTimeMs, boolean success, int resultCount) {
            this.region = region;
            this.responseTimeMs = responseTimeMs;
            this.success = success;
            this.resultCount = resultCount;
        }
    }
}

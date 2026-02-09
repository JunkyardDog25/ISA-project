package com.example.jutjubic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for trending analysis configuration and results.
 * Used for [S3] requirement - analyzing trending computation frequency
 * and locality considerations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrendingAnalysisDto {

    // Configuration
    private String analysisType;                    // "FREQUENCY", "LOCALITY", "PERFORMANCE_IMPACT"

    // Frequency Analysis
    private String currentFrequency;                // e.g., "DAILY", "HOURLY", "REAL_TIME"
    private String recommendedFrequency;
    private String frequencyRationale;

    // Locality Analysis
    private double analyzedRadiusKm;
    private boolean trendingDiffersByLocality;
    private String localityAnalysis;
    private double minimumDistanceForDifferentTrendingKm;

    // Performance Impact Analysis
    private double baselineResponseTimeMs;
    private double duringTrendingComputeMs;
    private double performanceImpactPercent;
    private boolean impactsBasicFunctionality;
    private String performanceAnalysis;

    // Overall Recommendations
    private String overallRecommendation;
    private String implementationSuggestion;

    /**
     * Builder pattern for easy construction.
     */
    public static TrendingAnalysisDto forFrequencyAnalysis(
            String currentFrequency,
            String recommendedFrequency,
            String rationale) {
        TrendingAnalysisDto dto = new TrendingAnalysisDto();
        dto.setAnalysisType("FREQUENCY");
        dto.setCurrentFrequency(currentFrequency);
        dto.setRecommendedFrequency(recommendedFrequency);
        dto.setFrequencyRationale(rationale);
        return dto;
    }

    public static TrendingAnalysisDto forLocalityAnalysis(
            double radiusKm,
            boolean differs,
            String analysis,
            double minDistanceKm) {
        TrendingAnalysisDto dto = new TrendingAnalysisDto();
        dto.setAnalysisType("LOCALITY");
        dto.setAnalyzedRadiusKm(radiusKm);
        dto.setTrendingDiffersByLocality(differs);
        dto.setLocalityAnalysis(analysis);
        dto.setMinimumDistanceForDifferentTrendingKm(minDistanceKm);
        return dto;
    }

    public static TrendingAnalysisDto forPerformanceImpact(
            double baselineMs,
            double duringComputeMs,
            boolean impacts,
            String analysis) {
        TrendingAnalysisDto dto = new TrendingAnalysisDto();
        dto.setAnalysisType("PERFORMANCE_IMPACT");
        dto.setBaselineResponseTimeMs(baselineMs);
        dto.setDuringTrendingComputeMs(duringComputeMs);
        dto.setPerformanceImpactPercent(
            baselineMs > 0 ? ((duringComputeMs - baselineMs) / baselineMs) * 100 : 0
        );
        dto.setImpactsBasicFunctionality(impacts);
        dto.setPerformanceAnalysis(analysis);
        return dto;
    }
}

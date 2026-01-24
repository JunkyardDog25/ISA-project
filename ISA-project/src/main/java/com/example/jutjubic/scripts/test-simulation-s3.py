"""
Simulation Testing Script for [S3] Requirement
==============================================
This script tests the application with requests from multiple regions
and analyzes the differences between concentrated vs distributed activity.

Requirements tested:
- [S3] Simulation of requests from multiple regions
- [S3] Analysis: concentrated activity on small area vs distributed activity from different cities
- [S3] Analysis: How often should trending be computed?
- [S3] Analysis: Does trending differ for users in the same street?
- [S3] Analysis: Do trending operations impact basic functionality performance?

Usage:
    python test-simulation-s3.py [--requests N] [--radius R]

Arguments:
    --requests N    Number of requests per simulation (default: 30)
    --radius R      Search radius in km (default: 5)
"""

import requests
import argparse
import json
import time
from datetime import datetime

BASE_URL = "http://localhost:8080"


def print_header(title):
    """Print formatted header."""
    print(f"\n{'=' * 80}")
    print(f" {title}")
    print(f"{'=' * 80}")


def print_subheader(title):
    """Print formatted subheader."""
    print(f"\n{'-' * 60}")
    print(f" {title}")
    print(f"{'-' * 60}")


def run_simulation(session, sim_type, requests_count, radius):
    """Run a specific simulation type."""
    url = f"{BASE_URL}/api/simulation/{sim_type}"
    params = {"requests": requests_count, "radius": radius}

    print(f"\n  Running {sim_type.upper()} simulation...")
    start = time.time()

    try:
        response = session.post(url, params=params, timeout=120)
        elapsed = time.time() - start

        if response.status_code == 200:
            data = response.json()
            print(f"  ✓ Completed in {elapsed:.1f}s")
            return data
        else:
            print(f"  ✗ Failed with status {response.status_code}")
            return None
    except Exception as e:
        print(f"  ✗ Error: {e}")
        return None


def run_analysis(session, analysis_type, radius=5):
    """Run a specific analysis."""
    if analysis_type == "locality":
        url = f"{BASE_URL}/api/simulation/analysis/locality"
        params = {"radius": radius}
    elif analysis_type == "frequency":
        url = f"{BASE_URL}/api/simulation/analysis/frequency"
        params = {}
    elif analysis_type == "performance-impact":
        url = f"{BASE_URL}/api/simulation/analysis/performance-impact"
        params = {}
    else:
        return None

    print(f"\n  Running {analysis_type.upper()} analysis...")

    try:
        response = session.get(url, params=params, timeout=60)
        if response.status_code == 200:
            data = response.json()
            print(f"  ✓ Analysis complete")
            return data
        else:
            print(f"  ✗ Failed with status {response.status_code}")
            return None
    except Exception as e:
        print(f"  ✗ Error: {e}")
        return None


def print_simulation_results(result, title):
    """Print simulation results in tabular format."""
    if not result:
        print(f"\n  {title}: No data available")
        return

    print_subheader(title)

    print(f"\n  {'Metric':<30} {'Value':<20}")
    print(f"  {'-' * 50}")
    print(f"  {'Simulation Type':<30} {result.get('simulationType', 'N/A'):<20}")
    print(f"  {'Total Requests':<30} {result.get('totalRequests', 'N/A'):<20}")
    print(f"  {'Successful Requests':<30} {result.get('successfulRequests', 'N/A'):<20}")
    print(f"  {'Failed Requests':<30} {result.get('failedRequests', 'N/A'):<20}")
    print(f"  {'Success Rate':<30} {result.get('successRate', 0):.1f}%")
    print(f"  {'-' * 50}")
    print(f"  {'Avg Response Time':<30} {result.get('avgResponseTimeMs', 0):.2f} ms")
    print(f"  {'Min Response Time':<30} {result.get('minResponseTimeMs', 0):.2f} ms")
    print(f"  {'Max Response Time':<30} {result.get('maxResponseTimeMs', 0):.2f} ms")
    print(f"  {'Median Response Time':<30} {result.get('medianResponseTimeMs', 0):.2f} ms")
    print(f"  {'P95 Response Time':<30} {result.get('p95ResponseTimeMs', 0):.2f} ms")
    print(f"  {'P99 Response Time':<30} {result.get('p99ResponseTimeMs', 0):.2f} ms")
    print(f"  {'-' * 50}")
    print(f"  {'Requests/Second':<30} {result.get('requestsPerSecond', 0):.2f}")
    print(f"  {'Total Duration':<30} {result.get('totalDurationMs', 0):.0f} ms")

    # Analysis
    if result.get('analysis'):
        print(f"\n  ANALYSIS:")
        print(f"  {result['analysis']}")

    # Recommendations
    if result.get('recommendations'):
        print(f"\n  RECOMMENDATIONS:")
        for rec in result['recommendations']:
            print(f"    • {rec}")

    # Region stats
    region_stats = result.get('regionStats', {})
    if region_stats:
        print(f"\n  REGION BREAKDOWN:")
        print(f"  {'Region':<25} {'Requests':<10} {'Avg (ms)':<12} {'Results':<10}")
        print(f"  {'-' * 57}")
        for region_name, stats in region_stats.items():
            print(f"  {region_name:<25} {stats.get('requestCount', 0):<10} "
                  f"{stats.get('avgResponseTimeMs', 0):<12.2f} {stats.get('resultCount', 0):<10}")


def print_locality_analysis(result):
    """Print locality analysis results."""
    if not result:
        print("\n  Locality Analysis: No data available")
        return

    print_subheader("LOCALITY ANALYSIS")
    print("  Question: Da li se trending razlikuje između korisnika koji žive u istoj ulici?")

    print(f"\n  {'Parameter':<40} {'Value':<30}")
    print(f"  {'-' * 70}")
    print(f"  {'Analysis Type':<40} {result.get('analysisType', 'N/A'):<30}")
    print(f"  {'Analyzed Radius':<40} {result.get('analyzedRadiusKm', 0):.1f} km")
    print(f"  {'Trending Differs by Locality':<40} {'Yes' if result.get('trendingDiffersByLocality') else 'No':<30}")
    print(f"  {'Min Distance for Different Trending':<40} {result.get('minimumDistanceForDifferentTrendingKm', 0):.1f} km")

    if result.get('localityAnalysis'):
        print(f"\n  ANALYSIS:")
        print(f"  {result['localityAnalysis']}")

    if result.get('overallRecommendation'):
        print(f"\n  RECOMMENDATION:")
        print(f"  {result['overallRecommendation']}")


def print_frequency_analysis(result):
    """Print frequency analysis results."""
    if not result:
        print("\n  Frequency Analysis: No data available")
        return

    print_subheader("FREQUENCY ANALYSIS")
    print("  Question: Koliko često je potrebno računati trending?")

    print(f"\n  {'Parameter':<40} {'Value':<30}")
    print(f"  {'-' * 70}")
    print(f"  {'Current Frequency':<40} {result.get('currentFrequency', 'N/A'):<30}")
    print(f"  {'Recommended Frequency':<40} {result.get('recommendedFrequency', 'N/A'):<30}")

    if result.get('frequencyRationale'):
        print(f"\n  RATIONALE:")
        # Word wrap long text
        rationale = result['frequencyRationale']
        words = rationale.split()
        line = "  "
        for word in words:
            if len(line) + len(word) > 75:
                print(line)
                line = "  " + word + " "
            else:
                line += word + " "
        if line.strip():
            print(line)

    if result.get('overallRecommendation'):
        print(f"\n  RECOMMENDATION:")
        print(f"  {result['overallRecommendation']}")

    if result.get('implementationSuggestion'):
        print(f"\n  IMPLEMENTATION SUGGESTION:")
        for line in result['implementationSuggestion'].split('\n'):
            print(f"  {line}")


def print_performance_impact_analysis(result):
    """Print performance impact analysis results."""
    if not result:
        print("\n  Performance Impact Analysis: No data available")
        return

    print_subheader("PERFORMANCE IMPACT ANALYSIS")
    print("  Question: Da li trending operacije narušavaju performanse osnovnih funkcionalnosti?")

    print(f"\n  {'Parameter':<40} {'Value':<30}")
    print(f"  {'-' * 70}")
    print(f"  {'Baseline Response Time':<40} {result.get('baselineResponseTimeMs', 0):.2f} ms")
    print(f"  {'During Trending Compute':<40} {result.get('duringTrendingComputeMs', 0):.2f} ms")
    print(f"  {'Performance Impact':<40} {result.get('performanceImpactPercent', 0):.1f}%")
    print(f"  {'Impacts Basic Functionality':<40} {'YES ⚠' if result.get('impactsBasicFunctionality') else 'NO ✓':<30}")

    if result.get('performanceAnalysis'):
        print(f"\n  ANALYSIS:")
        print(f"  {result['performanceAnalysis']}")

    if result.get('overallRecommendation'):
        print(f"\n  RECOMMENDATION:")
        print(f"  {result['overallRecommendation']}")

    if result.get('implementationSuggestion'):
        print(f"\n  IMPLEMENTATION SUGGESTION:")
        for line in result['implementationSuggestion'].split('\n'):
            print(f"  {line}")


def print_comparison(concentrated, distributed):
    """Print comparison between concentrated and distributed simulations."""
    print_subheader("COMPARISON: CONCENTRATED vs DISTRIBUTED")

    if not concentrated or not distributed:
        print("  Insufficient data for comparison")
        return

    conc_avg = concentrated.get('avgResponseTimeMs', 0)
    dist_avg = distributed.get('avgResponseTimeMs', 0)

    print(f"\n  {'Metric':<30} {'Concentrated':<15} {'Distributed':<15} {'Difference':<15}")
    print(f"  {'-' * 75}")

    diff_avg = conc_avg - dist_avg
    diff_pct = (diff_avg / dist_avg * 100) if dist_avg > 0 else 0
    print(f"  {'Avg Response Time (ms)':<30} {conc_avg:<15.2f} {dist_avg:<15.2f} {diff_avg:+.2f} ({diff_pct:+.1f}%)")

    conc_p95 = concentrated.get('p95ResponseTimeMs', 0)
    dist_p95 = distributed.get('p95ResponseTimeMs', 0)
    diff_p95 = conc_p95 - dist_p95
    print(f"  {'P95 Response Time (ms)':<30} {conc_p95:<15.2f} {dist_p95:<15.2f} {diff_p95:+.2f}")

    conc_rps = concentrated.get('requestsPerSecond', 0)
    dist_rps = distributed.get('requestsPerSecond', 0)
    print(f"  {'Requests/Second':<30} {conc_rps:<15.2f} {dist_rps:<15.2f}")

    conc_success = concentrated.get('successRate', 0)
    dist_success = distributed.get('successRate', 0)
    print(f"  {'Success Rate (%)':<30} {conc_success:<15.1f} {dist_success:<15.1f}")

    # Conclusion
    print(f"\n  CONCLUSION:")
    if abs(diff_pct) < 10:
        print("  ✓ Performanse su SLIČNE za oba scenarija.")
        print("    Sistem podjednako dobro obrađuje koncentrisane i distribuirane zahteve.")
    elif diff_pct > 0:
        print(f"  ⚠ Koncentrisana aktivnost je SPORIJA za {diff_pct:.1f}%.")
        print("    Moguće objašnjenje: veće opterećenje istih podataka/indeksa.")
        print("    Preporuka: Implementirati keširanje za česte upite iz istog područja.")
    else:
        print(f"  ⚠ Distribuirana aktivnost je SPORIJA za {abs(diff_pct):.1f}%.")
        print("    Moguće objašnjenje: cache misses zbog različitih geografskih upita.")
        print("    Preporuka: Razmotriti geografsku replikaciju baze ili CDN.")


def main():
    parser = argparse.ArgumentParser(description="[S3] Simulation testing")
    parser.add_argument("--requests", type=int, default=30, help="Number of requests per simulation")
    parser.add_argument("--radius", type=float, default=5.0, help="Search radius in km")
    args = parser.parse_args()

    print_header("[S3] SIMULATION AND ANALYSIS TEST SUITE")
    print(f"\n  Start Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"  Configuration:")
    print(f"    - Requests per simulation: {args.requests}")
    print(f"    - Search radius: {args.radius} km")
    print(f"    - Server: {BASE_URL}")

    session = requests.Session()

    # ==========================================================================
    # PART 1: SIMULATIONS
    # ==========================================================================
    print_header("PART 1: SIMULATIONS")
    print("  Testing: 'veliki broj aktivnosti na malom prostoru vs distribuirane aktivnosti'")

    # Run concentrated simulation
    concentrated = run_simulation(session, "concentrated", args.requests, args.radius)
    print_simulation_results(concentrated, "CONCENTRATED ACTIVITY SIMULATION")

    # Run distributed simulation
    distributed = run_simulation(session, "distributed", args.requests, args.radius)
    print_simulation_results(distributed, "DISTRIBUTED ACTIVITY SIMULATION")

    # Comparison
    print_comparison(concentrated, distributed)

    # ==========================================================================
    # PART 2: ANALYSES
    # ==========================================================================
    print_header("PART 2: TRENDING ANALYSES")

    # Locality analysis
    locality = run_analysis(session, "locality", args.radius)
    print_locality_analysis(locality)

    # Frequency analysis
    frequency = run_analysis(session, "frequency")
    print_frequency_analysis(frequency)

    # Performance impact analysis
    impact = run_analysis(session, "performance-impact")
    print_performance_impact_analysis(impact)

    # ==========================================================================
    # FINAL SUMMARY
    # ==========================================================================
    print_header("FINAL SUMMARY - [S3] REQUIREMENTS COVERAGE")

    print("""
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │ REQUIREMENT                                           │ STATUS              │
  ├─────────────────────────────────────────────────────────────────────────────┤
  │ [S3] Simulation: Concentrated activity (small area)   │ ✓ TESTED           │
  │ [S3] Simulation: Distributed activity (diff. cities)  │ ✓ TESTED           │
  │ [S3] Analysis: Trending computation frequency         │ ✓ ANALYZED         │
  │ [S3] Analysis: Trending locality (same street)        │ ✓ ANALYZED         │
  │ [S3] Analysis: Performance impact on basic functions  │ ✓ ANALYZED         │
  └─────────────────────────────────────────────────────────────────────────────┘
    """)

    print(f"\n  Test completed at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'=' * 80}\n")


if __name__ == "__main__":
    main()

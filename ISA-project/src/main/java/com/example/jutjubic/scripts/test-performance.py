"""
Performance Testing Script for [S2] Requirement
==============================================
This script simulates multiple requests to measure and compare
performance of nearby search and trending video features.

Usage:
    python test-performance.py [--requests N] [--delay D]

Arguments:
    --requests N    Number of test requests per endpoint (default: 50)
    --delay D       Delay between requests in seconds (default: 0.1)
"""

import requests
import time
import random
import argparse
import json
from datetime import datetime

BASE_URL = "http://localhost:8080"

# Test locations (various cities in Serbia and surrounding area)
TEST_LOCATIONS = [
    (44.8176, 20.4633, "Belgrade"),
    (45.2671, 19.8335, "Novi Sad"),
    (43.3209, 21.8958, "Niš"),
    (44.0128, 20.9114, "Kragujevac"),
    (43.8563, 18.4131, "Sarajevo"),
    (45.8150, 15.9819, "Zagreb"),
    (44.7866, 20.4489, "Zemun"),
    (44.7722, 17.1910, "Banja Luka"),
    (42.4304, 19.2594, "Podgorica"),
    (41.9981, 21.4254, "Skopje"),
]

# Test radii in km
TEST_RADII = [1, 5, 10, 25, 50, 100]


def test_nearby_search(location, radius, session):
    """Test nearby search endpoint."""
    lat, lon, name = location
    url = f"{BASE_URL}/api/videos/nearby"
    params = {
        "location": f"{lat},{lon}",
        "radius": radius,
        "units": "km",
        "page": 0,
        "size": 16
    }

    start_time = time.time()
    try:
        response = session.get(url, params=params, timeout=10)
        elapsed_ms = (time.time() - start_time) * 1000

        return {
            "success": response.status_code == 200,
            "status_code": response.status_code,
            "response_time_ms": elapsed_ms,
            "location": name,
            "radius_km": radius,
            "results": response.json().get("totalElements", 0) if response.status_code == 200 else 0
        }
    except Exception as e:
        elapsed_ms = (time.time() - start_time) * 1000
        return {
            "success": False,
            "error": str(e),
            "response_time_ms": elapsed_ms,
            "location": name,
            "radius_km": radius,
            "results": 0
        }


def test_trending_fetch(session):
    """Test trending videos endpoint."""
    url = f"{BASE_URL}/api/daily-popular-videos"

    start_time = time.time()
    try:
        response = session.get(url, timeout=10)
        elapsed_ms = (time.time() - start_time) * 1000

        return {
            "success": response.status_code == 200,
            "status_code": response.status_code,
            "response_time_ms": elapsed_ms,
            "results": len(response.json()) if response.status_code == 200 else 0
        }
    except Exception as e:
        elapsed_ms = (time.time() - start_time) * 1000
        return {
            "success": False,
            "error": str(e),
            "response_time_ms": elapsed_ms,
            "results": 0
        }


def get_performance_report(session):
    """Get current performance report from server."""
    url = f"{BASE_URL}/api/performance/comparison"
    try:
        response = session.get(url, params={"lastMinutes": 60}, timeout=10)
        if response.status_code == 200:
            return response.json()
    except Exception as e:
        print(f"Failed to get performance report: {e}")
    return None


def print_statistics(results, test_name):
    """Print statistics for a set of test results."""
    if not results:
        print(f"\n{test_name}: No results")
        return

    times = [r["response_time_ms"] for r in results if r["success"]]
    if not times:
        print(f"\n{test_name}: All requests failed")
        return

    times.sort()
    avg = sum(times) / len(times)
    min_t = min(times)
    max_t = max(times)
    median = times[len(times) // 2]
    p95 = times[int(len(times) * 0.95)] if len(times) >= 20 else max_t
    p99 = times[int(len(times) * 0.99)] if len(times) >= 100 else max_t
    success_rate = len(times) / len(results) * 100

    print(f"\n{'=' * 60}")
    print(f"{test_name}")
    print(f"{'=' * 60}")
    print(f"Total Requests:  {len(results)}")
    print(f"Successful:      {len(times)} ({success_rate:.1f}%)")
    print(f"Average:         {avg:.2f} ms")
    print(f"Minimum:         {min_t:.2f} ms")
    print(f"Maximum:         {max_t:.2f} ms")
    print(f"Median (P50):    {median:.2f} ms")
    print(f"P95:             {p95:.2f} ms")
    print(f"P99:             {p99:.2f} ms")

    # Performance rating
    if avg < 100:
        rating = "EXCELLENT"
    elif avg < 200:
        rating = "GOOD"
    elif avg < 500:
        rating = "MODERATE"
    else:
        rating = "NEEDS IMPROVEMENT"
    print(f"Rating:          {rating}")


def print_comparison_table(nearby_results, trending_results):
    """Print comparison table."""
    print(f"\n{'=' * 70}")
    print("PERFORMANCE COMPARISON TABLE")
    print(f"{'=' * 70}")
    print(f"{'Metric':<25} {'Nearby Search':<20} {'Trending Fetch':<20}")
    print(f"{'-' * 70}")

    def calc_stats(results):
        times = [r["response_time_ms"] for r in results if r["success"]]
        if not times:
            return {"avg": "N/A", "p95": "N/A", "count": 0}
        times.sort()
        return {
            "avg": f"{sum(times)/len(times):.2f} ms",
            "p95": f"{times[int(len(times)*0.95)] if len(times) >= 20 else max(times):.2f} ms",
            "count": len(times)
        }

    nearby = calc_stats(nearby_results)
    trending = calc_stats(trending_results)

    print(f"{'Sample Count':<25} {nearby['count']:<20} {trending['count']:<20}")
    print(f"{'Average Response Time':<25} {nearby['avg']:<20} {trending['avg']:<20}")
    print(f"{'P95 Response Time':<25} {nearby['p95']:<20} {trending['p95']:<20}")
    print(f"{'-' * 70}")


def main():
    parser = argparse.ArgumentParser(description="Performance testing for [S2]")
    parser.add_argument("--requests", type=int, default=50, help="Number of requests per test")
    parser.add_argument("--delay", type=float, default=0.1, help="Delay between requests (seconds)")
    args = parser.parse_args()

    print(f"\n{'#' * 70}")
    print("PERFORMANCE TEST FOR [S2] - TRENDING VS REAL-TIME ANALYSIS")
    print(f"{'#' * 70}")
    print(f"Start Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"Requests per test: {args.requests}")
    print(f"Delay between requests: {args.delay}s")

    session = requests.Session()

    # Test 1: Nearby Search with various locations and radii
    print("\n[1/3] Testing Nearby Search...")
    nearby_results = []
    for i in range(args.requests):
        location = random.choice(TEST_LOCATIONS)
        radius = random.choice(TEST_RADII)
        result = test_nearby_search(location, radius, session)
        nearby_results.append(result)
        print(f"  Request {i+1}/{args.requests}: {result['location']} ({radius}km) - {result['response_time_ms']:.2f}ms")
        time.sleep(args.delay)

    # Test 2: Trending Videos Fetch
    print("\n[2/3] Testing Trending Fetch...")
    trending_results = []
    for i in range(args.requests):
        result = test_trending_fetch(session)
        trending_results.append(result)
        print(f"  Request {i+1}/{args.requests}: {result['response_time_ms']:.2f}ms")
        time.sleep(args.delay)

    # Print Statistics
    print("\n[3/3] Generating Report...")
    print_statistics(nearby_results, "NEARBY SEARCH PERFORMANCE")
    print_statistics(trending_results, "TRENDING FETCH PERFORMANCE")
    print_comparison_table(nearby_results, trending_results)

    # Get server-side performance report
    print("\n" + "=" * 70)
    print("SERVER-SIDE PERFORMANCE METRICS")
    print("=" * 70)
    report = get_performance_report(session)
    if report:
        for op_type, data in report.items():
            print(f"\n{op_type}:")
            print(f"  Samples: {data.get('totalMeasurements', 'N/A')}")
            print(f"  Avg: {data.get('avgResponseTimeMs', 'N/A')} ms")
            print(f"  P95: {data.get('p95ResponseTimeMs', 'N/A')} ms")
            print(f"  Recommendation: {data.get('recommendation', 'N/A')}")

    # Summary & Recommendations
    print(f"\n{'=' * 70}")
    print("SUMMARY & RECOMMENDATIONS")
    print(f"{'=' * 70}")

    nearby_times = [r["response_time_ms"] for r in nearby_results if r["success"]]
    trending_times = [r["response_time_ms"] for r in trending_results if r["success"]]

    if nearby_times and trending_times:
        nearby_avg = sum(nearby_times) / len(nearby_times)
        trending_avg = sum(trending_times) / len(trending_times)

        print(f"\n1. NEARBY SEARCH: Average {nearby_avg:.2f}ms")
        if nearby_avg < 200:
            print("   ✓ Performance is optimal for real-time usage")
        else:
            print("   ⚠ Consider enabling caching or optimizing spatial indexes")

        print(f"\n2. TRENDING FETCH: Average {trending_avg:.2f}ms")
        if trending_avg < 100:
            print("   ✓ Pre-computed trending data provides excellent performance")
        else:
            print("   ⚠ Consider caching trending results")

        print(f"\n3. OPTIMAL BALANCE:")
        if trending_avg < nearby_avg:
            ratio = nearby_avg / trending_avg
            print(f"   Pre-computed trending is {ratio:.1f}x faster than real-time nearby search")
            print("   Recommendation: Use daily batch computation for trending (current approach)")
        else:
            print("   Real-time computation is competitive with batch processing")
            print("   Recommendation: Consider real-time trending for fresher data")

    print(f"\nTest completed at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{'#' * 70}\n")


if __name__ == "__main__":
    main()

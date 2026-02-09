<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { Line } from 'vue-chartjs';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js';

// Register Chart.js components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

// State
const loading = ref(true);
const error = ref(null);
const comparisonReport = ref({});
const selectedOperation = ref('NEARBY_SEARCH');
const timeWindow = ref(60); // minutes
const rawMetrics = ref([]);
const chartKey = ref(0); // Key for forcing chart re-render

// Available operation types
const operationTypes = ['NEARBY_SEARCH', 'TRENDING_FETCH', 'TRENDING_COMPUTE'];

// Fetch comparison report from API
async function fetchReport() {
  loading.value = true;
  error.value = null;

  try {
    const response = await fetch(`http://localhost:8080/api/performance/comparison?lastMinutes=${timeWindow.value}`);
    if (!response.ok) throw new Error('Failed to fetch performance data');
    comparisonReport.value = await response.json();

    // Also fetch raw metrics for the chart
    await fetchRawMetrics();
  } catch (e) {
    error.value = e.message;
    console.error('Error fetching performance report:', e);
  } finally {
    loading.value = false;
  }
}

// Fetch raw metrics for chart visualization
async function fetchRawMetrics() {
  try {
    const response = await fetch(`http://localhost:8080/api/performance/metrics/${selectedOperation.value}?lastMinutes=${timeWindow.value}`);
    if (!response.ok) throw new Error('Failed to fetch raw metrics');
    rawMetrics.value = await response.json();
    chartKey.value++; // Force chart re-render
  } catch (e) {
    console.error('Error fetching raw metrics:', e);
    rawMetrics.value = [];
  }
}

// Watch for operation change and refetch metrics
watch(selectedOperation, async () => {
  await fetchRawMetrics();
});

// Chart data computed property
const chartData = computed(() => {
  if (!rawMetrics.value || rawMetrics.value.length === 0) {
    return {
      labels: [],
      datasets: []
    };
  }

  // Sort by timestamp and take last 50 for readability
  const sortedMetrics = [...rawMetrics.value]
    .sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
    .slice(-50);

  const labels = sortedMetrics.map(m => {
    const date = new Date(m.timestamp);
    return date.toLocaleTimeString('sr-RS', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  });

  const responseTimes = sortedMetrics.map(m => m.responseTimeMs);

  // Calculate moving average (window of 5)
  const movingAvg = responseTimes.map((_, idx, arr) => {
    const start = Math.max(0, idx - 4);
    const window = arr.slice(start, idx + 1);
    return window.reduce((a, b) => a + b, 0) / window.length;
  });

  return {
    labels,
    datasets: [
      {
        label: 'Response Time (ms)',
        data: responseTimes,
        borderColor: '#007bff',
        backgroundColor: 'rgba(0, 123, 255, 0.1)',
        fill: true,
        tension: 0.3,
        pointRadius: 3,
        pointHoverRadius: 6
      },
      {
        label: 'Moving Average (5)',
        data: movingAvg,
        borderColor: '#28a745',
        backgroundColor: 'transparent',
        borderDash: [5, 5],
        fill: false,
        tension: 0.4,
        pointRadius: 0
      }
    ]
  };
});

// Chart options
const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      position: 'top',
    },
    title: {
      display: true,
      text: `Response Time - ${selectedOperation.value.replace('_', ' ')}`,
      font: { size: 16 }
    },
    tooltip: {
      mode: 'index',
      intersect: false,
      callbacks: {
        label: function(context) {
          return `${context.dataset.label}: ${context.parsed.y.toFixed(2)} ms`;
        }
      }
    }
  },
  scales: {
    x: {
      display: true,
      title: {
        display: true,
        text: 'Time'
      }
    },
    y: {
      display: true,
      title: {
        display: true,
        text: 'Response Time (ms)'
      },
      beginAtZero: true
    }
  },
  interaction: {
    mode: 'nearest',
    axis: 'x',
    intersect: false
  }
}));

// Get report for selected operation
const selectedReport = computed(() => {
  return comparisonReport.value[selectedOperation.value] || null;
});

// Format milliseconds
function formatMs(ms) {
  if (ms === undefined || ms === null) return 'N/A';
  return `${ms.toFixed(2)} ms`;
}

// Format percentage
function formatPercent(ratio) {
  if (ratio === undefined || ratio === null) return 'N/A';
  return `${(ratio * 100).toFixed(1)}%`;
}

// Get performance status class
function getStatusClass(avgMs) {
  if (avgMs < 100) return 'status-excellent';
  if (avgMs < 200) return 'status-good';
  if (avgMs < 500) return 'status-moderate';
  return 'status-poor';
}

// Get status label
function getStatusLabel(avgMs) {
  if (avgMs < 100) return 'Excellent';
  if (avgMs < 200) return 'Good';
  if (avgMs < 500) return 'Moderate';
  return 'Needs Improvement';
}

// Refresh data
function refresh() {
  fetchReport();
}

// Clear all metrics
async function clearMetrics() {
  if (!confirm('Are you sure you want to clear all performance metrics?')) return;

  try {
    await fetch('http://localhost:8080/api/performance/clear', { method: 'DELETE' });
    await fetchReport();
  } catch (e) {
    error.value = 'Failed to clear metrics';
  }
}

onMounted(() => {
  fetchReport();
});
</script>

<template>
  <div class="performance-dashboard">
    <header class="dashboard-header">
      <h1>Performance Metrics Dashboard</h1>
      <p class="subtitle">Real-time vs Performance Analysis</p>
    </header>

    <!-- Controls -->
    <div class="controls">
      <div class="control-group">
        <label>Time Window:</label>
        <select v-model="timeWindow" @change="fetchReport">
          <option :value="15">Last 15 minutes</option>
          <option :value="30">Last 30 minutes</option>
          <option :value="60">Last 1 hour</option>
          <option :value="360">Last 6 hours</option>
          <option :value="1440">Last 24 hours</option>
          <option :value="0">All time</option>
        </select>
      </div>

      <div class="control-group">
        <label>Operation:</label>
        <select v-model="selectedOperation">
          <option v-for="op in operationTypes" :key="op" :value="op">{{ op }}</option>
        </select>
      </div>

      <button @click="refresh" class="btn btn-primary">Refresh</button>
      <button @click="clearMetrics" class="btn btn-danger">Clear All</button>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="loading">
      <div class="spinner"></div>
      <p>Loading performance data...</p>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="error">
      <p>{{ error }}</p>
      <button @click="refresh" class="btn">Retry</button>
    </div>

    <!-- Main Content -->
    <div v-else class="dashboard-content">
      <!-- Summary Cards -->
      <div class="summary-cards">
        <div
          v-for="(report, opType) in comparisonReport"
          :key="opType"
          class="summary-card"
          :class="{ active: selectedOperation === opType }"
          @click="selectedOperation = opType"
        >
          <h3>{{ opType.replace('_', ' ') }}</h3>
          <div class="metric-value" :class="getStatusClass(report.avgResponseTimeMs)">
            {{ formatMs(report.avgResponseTimeMs) }}
          </div>
          <div class="metric-label">Avg Response Time</div>
          <div class="metric-status" :class="getStatusClass(report.avgResponseTimeMs)">
            {{ getStatusLabel(report.avgResponseTimeMs) }}
          </div>
          <div class="metric-count">{{ report.totalMeasurements }} samples</div>
        </div>
      </div>

      <!-- Response Time Chart -->
      <div class="chart-container" v-if="chartData.labels && chartData.labels.length > 0">
        <h2>Response Time Graph - {{ selectedOperation.replace('_', ' ') }}</h2>
        <p class="chart-subtitle">Visual comparison of response times over time for optimal performance analysis</p>
        <div class="chart-wrapper">
          <Line :key="chartKey" :data="chartData" :options="chartOptions" />
        </div>
        <div class="chart-legend-info">
          <div class="legend-item">
            <span class="legend-color blue"></span>
            <span>Individual Response Times - actual latency per request</span>
          </div>
          <div class="legend-item">
            <span class="legend-color green"></span>
            <span>Moving Average - smoothed trend line (window of 5)</span>
          </div>
        </div>
      </div>

      <!-- No Chart Data -->
      <div v-else class="no-chart-data">
        <p>📊 No chart data available. Generate some traffic to see the response time graph.</p>
      </div>

      <!-- Detailed Report -->
      <div v-if="selectedReport" class="detailed-report">
        <h2>{{ selectedOperation.replace('_', ' ') }} - Detailed Analysis</h2>

        <!-- Statistics Table -->
        <div class="stats-table-container">
          <h3>Response Time Statistics</h3>
          <table class="stats-table">
            <thead>
              <tr>
                <th>Metric</th>
                <th>Value</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Average</td>
                <td class="value">{{ formatMs(selectedReport.avgResponseTimeMs) }}</td>
                <td>Mean response time across all measurements</td>
              </tr>
              <tr>
                <td>Minimum</td>
                <td class="value">{{ formatMs(selectedReport.minResponseTimeMs) }}</td>
                <td>Fastest response recorded</td>
              </tr>
              <tr>
                <td>Maximum</td>
                <td class="value">{{ formatMs(selectedReport.maxResponseTimeMs) }}</td>
                <td>Slowest response recorded</td>
              </tr>
              <tr>
                <td>Median (P50)</td>
                <td class="value">{{ formatMs(selectedReport.medianResponseTimeMs) }}</td>
                <td>50th percentile - typical response time</td>
              </tr>
              <tr>
                <td>P95</td>
                <td class="value">{{ formatMs(selectedReport.p95ResponseTimeMs) }}</td>
                <td>95% of requests are faster than this</td>
              </tr>
              <tr>
                <td>P99</td>
                <td class="value">{{ formatMs(selectedReport.p99ResponseTimeMs) }}</td>
                <td>99% of requests are faster than this</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Cache Statistics -->
        <div class="cache-stats">
          <h3>Cache Performance</h3>
          <div class="cache-grid">
            <div class="cache-metric">
              <span class="cache-value">{{ selectedReport.cacheHits }}</span>
              <span class="cache-label">Cache Hits</span>
            </div>
            <div class="cache-metric">
              <span class="cache-value">{{ selectedReport.cacheMisses }}</span>
              <span class="cache-label">Cache Misses</span>
            </div>
            <div class="cache-metric">
              <span class="cache-value">{{ formatPercent(selectedReport.cacheHitRatio) }}</span>
              <span class="cache-label">Hit Ratio</span>
            </div>
          </div>
        </div>

        <!-- Recommendation -->
        <div class="recommendation" v-if="selectedReport.recommendation">
          <h3>Analysis & Recommendations</h3>
          <p>{{ selectedReport.recommendation }}</p>
        </div>

        <!-- Recent Measurements Table -->
        <div v-if="selectedReport.measurements?.length > 0" class="measurements-table-container">
          <h3>Recent Measurements (Last {{ selectedReport.measurements.length }})</h3>
          <div class="table-scroll">
            <table class="measurements-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Response Time</th>
                  <th>Results</th>
                  <th>Cache</th>
                  <th v-if="selectedOperation === 'NEARBY_SEARCH'">Location</th>
                  <th v-if="selectedOperation === 'NEARBY_SEARCH'">Radius</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(m, idx) in selectedReport.measurements.slice(-20).reverse()" :key="idx">
                  <td>{{ new Date(m.timestamp).toLocaleTimeString() }}</td>
                  <td :class="getStatusClass(m.responseTimeMs)">{{ formatMs(m.responseTimeMs) }}</td>
                  <td>{{ m.resultCount }}</td>
                  <td>{{ m.cacheStatus }}</td>
                  <td v-if="selectedOperation === 'NEARBY_SEARCH'">{{ m.location }}</td>
                  <td v-if="selectedOperation === 'NEARBY_SEARCH'">{{ m.radiusKm?.toFixed(1) }} km</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- No Data State -->
      <div v-else class="no-data">
        <p>No performance data available for {{ selectedOperation }}.</p>
        <p>Start using the application to generate metrics.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.performance-dashboard {
  max-width: 1400px;
  margin: 0 auto;
  padding: 2rem;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.dashboard-header {
  text-align: center;
  margin-bottom: 2rem;
}

.dashboard-header h1 {
  margin: 0 0 0.5rem;
  color: #333;
}

.subtitle {
  color: #666;
  font-size: 1.1rem;
}

/* Controls */
.controls {
  display: flex;
  gap: 1rem;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 2rem;
  padding: 1rem;
  background: #f5f5f5;
  border-radius: 8px;
}

.control-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.control-group label {
  font-weight: 500;
  color: #555;
}

.control-group select {
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.9rem;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover {
  background: #0056b3;
}

.btn-danger {
  background: #dc3545;
  color: white;
}

.btn-danger:hover {
  background: #c82333;
}

/* Loading & Error */
.loading, .error, .no-data {
  text-align: center;
  padding: 3rem;
  color: #666;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #eee;
  border-top-color: #007bff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 1rem;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Summary Cards */
.summary-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

/* Chart Container */
.chart-container {
  background: white;
  border-radius: 12px;
  padding: 2rem;
  margin-bottom: 2rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.chart-container h2 {
  margin: 0 0 0.5rem;
  color: #333;
}

.chart-subtitle {
  color: #666;
  font-size: 0.9rem;
  margin: 0 0 1.5rem;
}

.chart-wrapper {
  height: 400px;
  position: relative;
}

.chart-legend-info {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #eee;
  display: flex;
  flex-wrap: wrap;
  gap: 1.5rem;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: #666;
}

.legend-color {
  width: 20px;
  height: 4px;
  border-radius: 2px;
}

.legend-color.blue {
  background: #007bff;
}

.legend-color.green {
  background: #28a745;
}

.no-chart-data {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 2rem;
  margin-bottom: 2rem;
  text-align: center;
  color: #666;
}

.summary-card {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 2px solid transparent;
}

.summary-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
}

.summary-card.active {
  border-color: #007bff;
}

.summary-card h3 {
  margin: 0 0 1rem;
  font-size: 0.9rem;
  color: #666;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.metric-value {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 0.25rem;
}

.metric-label {
  font-size: 0.85rem;
  color: #888;
}

.metric-status {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
  margin-top: 0.5rem;
}

.metric-count {
  font-size: 0.8rem;
  color: #999;
  margin-top: 0.5rem;
}

/* Status Colors */
.status-excellent { color: #28a745; background: #d4edda; }
.status-good { color: #17a2b8; background: #d1ecf1; }
.status-moderate { color: #ffc107; background: #fff3cd; }
.status-poor { color: #dc3545; background: #f8d7da; }

/* Detailed Report */
.detailed-report {
  background: white;
  border-radius: 12px;
  padding: 2rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.detailed-report h2 {
  margin: 0 0 1.5rem;
  color: #333;
  border-bottom: 2px solid #eee;
  padding-bottom: 0.5rem;
}

.detailed-report h3 {
  margin: 1.5rem 0 1rem;
  color: #555;
  font-size: 1.1rem;
}

/* Statistics Table */
.stats-table-container {
  margin-bottom: 2rem;
}

.stats-table {
  width: 100%;
  border-collapse: collapse;
}

.stats-table th,
.stats-table td {
  padding: 0.75rem 1rem;
  text-align: left;
  border-bottom: 1px solid #eee;
}

.stats-table th {
  background: #f8f9fa;
  font-weight: 600;
  color: #555;
}

.stats-table .value {
  font-weight: 600;
  font-family: 'Monaco', 'Consolas', monospace;
}

/* Cache Stats */
.cache-stats {
  margin-bottom: 2rem;
}

.cache-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}

.cache-metric {
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 8px;
  text-align: center;
}

.cache-value {
  display: block;
  font-size: 1.5rem;
  font-weight: 700;
  color: #333;
}

.cache-label {
  font-size: 0.85rem;
  color: #666;
}

/* Recommendation */
.recommendation {
  background: #e7f3ff;
  border-left: 4px solid #007bff;
  padding: 1rem 1.5rem;
  border-radius: 0 8px 8px 0;
  margin-bottom: 2rem;
}

.recommendation h3 {
  margin-top: 0;
  color: #0056b3;
}

.recommendation p {
  margin: 0;
  color: #333;
  line-height: 1.6;
}

/* Measurements Table */
.measurements-table-container {
  margin-top: 2rem;
}

.table-scroll {
  overflow-x: auto;
}

.measurements-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.measurements-table th,
.measurements-table td {
  padding: 0.6rem 0.8rem;
  text-align: left;
  border-bottom: 1px solid #eee;
}

.measurements-table th {
  background: #f8f9fa;
  font-weight: 600;
  color: #555;
  position: sticky;
  top: 0;
}

.measurements-table tbody tr:hover {
  background: #f8f9fa;
}

/* Responsive */
@media (max-width: 768px) {
  .performance-dashboard {
    padding: 1rem;
  }

  .controls {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-cards {
    grid-template-columns: 1fr;
  }

  .cache-grid {
    grid-template-columns: 1fr;
  }
}
</style>

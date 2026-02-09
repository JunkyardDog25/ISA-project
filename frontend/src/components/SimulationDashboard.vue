<script setup>
import { ref, reactive, computed } from 'vue';

// State
const loading = ref(false);
const error = ref(null);
const activeTab = ref('simulations');

// Simulation parameters
const simParams = reactive({
  requests: 30,
  radius: 5
});

// Results
const concentratedResult = ref(null);
const distributedResult = ref(null);
const mixedResult = ref(null);
const localityAnalysis = ref(null);
const frequencyAnalysis = ref(null);
const performanceImpactAnalysis = ref(null);

const BASE_URL = 'http://localhost:8080';

// Run simulation
async function runSimulation(type) {
  loading.value = true;
  error.value = null;

  try {
    const response = await fetch(
      `${BASE_URL}/api/simulation/${type}?requests=${simParams.requests}&radius=${simParams.radius}`,
      { method: 'POST' }
    );

    if (!response.ok) throw new Error(`Simulation failed: ${response.status}`);

    const data = await response.json();

    if (type === 'concentrated') concentratedResult.value = data;
    else if (type === 'distributed') distributedResult.value = data;
    else if (type === 'mixed') mixedResult.value = data;

  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

// Run analysis
async function runAnalysis(type) {
  loading.value = true;
  error.value = null;

  try {
    let url = `${BASE_URL}/api/simulation/analysis/${type}`;
    if (type === 'locality') url += `?radius=${simParams.radius}`;

    const response = await fetch(url);
    if (!response.ok) throw new Error(`Analysis failed: ${response.status}`);

    const data = await response.json();

    if (type === 'locality') localityAnalysis.value = data;
    else if (type === 'frequency') frequencyAnalysis.value = data;
    else if (type === 'performance-impact') performanceImpactAnalysis.value = data;

  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

// Run all
async function runAll() {
  loading.value = true;
  error.value = null;

  try {
    await runSimulation('concentrated');
    await runSimulation('distributed');
    await runAnalysis('locality');
    await runAnalysis('frequency');
    await runAnalysis('performance-impact');
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

// Clear all results
function clearResults() {
  concentratedResult.value = null;
  distributedResult.value = null;
  mixedResult.value = null;
  localityAnalysis.value = null;
  frequencyAnalysis.value = null;
  performanceImpactAnalysis.value = null;
  error.value = null;
}

// Computed comparison
const comparison = computed(() => {
  if (!concentratedResult.value || !distributedResult.value) return null;

  const concAvg = concentratedResult.value.avgResponseTimeMs || 0;
  const distAvg = distributedResult.value.avgResponseTimeMs || 0;
  const diff = concAvg - distAvg;
  const diffPercent = distAvg > 0 ? (diff / distAvg) * 100 : 0;

  return {
    concentratedAvg: concAvg,
    distributedAvg: distAvg,
    difference: diff,
    differencePercent: diffPercent,
    conclusion: Math.abs(diffPercent) < 10
      ? 'Performanse su slične za oba scenarija.'
      : diffPercent > 0
        ? `Koncentrisana aktivnost je sporija za ${diffPercent.toFixed(1)}%.`
        : `Distribuirana aktivnost je sporija za ${Math.abs(diffPercent).toFixed(1)}%.`
  };
});

// Format helpers
function formatMs(value) {
  return value !== null && value !== undefined ? `${value.toFixed(2)} ms` : 'N/A';
}

function formatPercent(value) {
  return value !== null && value !== undefined ? `${value.toFixed(1)}%` : 'N/A';
}

function getPerformanceClass(avgMs) {
  if (avgMs < 100) return 'excellent';
  if (avgMs < 200) return 'good';
  if (avgMs < 500) return 'moderate';
  return 'poor';
}
</script>

<template>
  <div class="simulation-dashboard">
    <!-- Header -->
    <header class="dashboard-header">
      <h1>Simulation & Analysis Dashboard</h1>
      <p class="subtitle">Multiple Regions & Trending Analysis</p>
    </header>

    <!-- Parameters -->
    <div class="params-section">
      <h3>Simulation Parameters</h3>
      <div class="params-grid">
        <div class="param">
          <label>Requests per simulation:</label>
          <input type="number" v-model.number="simParams.requests" min="10" max="200" />
        </div>
        <div class="param">
          <label>Search radius (km):</label>
          <input type="number" v-model.number="simParams.radius" min="1" max="100" step="0.5" />
        </div>
        <button @click="runAll" :disabled="loading" class="btn btn-primary">
          {{ loading ? 'Running...' : 'Run All Tests' }}
        </button>
        <button @click="clearResults" class="btn btn-secondary">Clear Results</button>
      </div>
    </div>

    <!-- Error -->
    <div v-if="error" class="error-banner">
      {{ error }}
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-overlay">
      <div class="spinner"></div>
      <p>Running simulations and analyses...</p>
    </div>

    <!-- Tabs -->
    <div class="tabs">
      <button
        :class="{ active: activeTab === 'simulations' }"
        @click="activeTab = 'simulations'"
      >
        Simulations
      </button>
      <button
        :class="{ active: activeTab === 'analyses' }"
        @click="activeTab = 'analyses'"
      >
        Trending Analyses
      </button>
      <button
        :class="{ active: activeTab === 'comparison' }"
        @click="activeTab = 'comparison'"
      >
        Comparison
      </button>
    </div>

    <!-- Tab Content -->
    <div class="tab-content">
      <!-- Simulations Tab -->
      <div v-if="activeTab === 'simulations'" class="simulations-tab">
        <div class="sim-controls">
          <button @click="runSimulation('concentrated')" :disabled="loading" class="btn">
            Run Concentrated
          </button>
          <button @click="runSimulation('distributed')" :disabled="loading" class="btn">
            Run Distributed
          </button>
          <button @click="runSimulation('mixed')" :disabled="loading" class="btn">
            Run Mixed
          </button>
        </div>

        <!-- Concentrated Results -->
        <div v-if="concentratedResult" class="result-card">
          <h3>
            <span class="indicator concentrated"></span>
            Concentrated Activity Simulation
          </h3>
          <p class="description">Veliki broj aktivnosti na relativno malom prostoru (Beograd i okolina)</p>

          <div class="stats-grid">
            <div class="stat">
              <span class="label">Avg Response Time</span>
              <span class="value" :class="getPerformanceClass(concentratedResult.avgResponseTimeMs)">
                {{ formatMs(concentratedResult.avgResponseTimeMs) }}
              </span>
            </div>
            <div class="stat">
              <span class="label">P95</span>
              <span class="value">{{ formatMs(concentratedResult.p95ResponseTimeMs) }}</span>
            </div>
            <div class="stat">
              <span class="label">Success Rate</span>
              <span class="value">{{ formatPercent(concentratedResult.successRate) }}</span>
            </div>
            <div class="stat">
              <span class="label">Requests/sec</span>
              <span class="value">{{ concentratedResult.requestsPerSecond?.toFixed(2) }}</span>
            </div>
          </div>

          <div v-if="concentratedResult.analysis" class="analysis-text">
            <strong>Analysis:</strong> {{ concentratedResult.analysis }}
          </div>

          <div v-if="concentratedResult.recommendations?.length" class="recommendations">
            <strong>Recommendations:</strong>
            <ul>
              <li v-for="(rec, i) in concentratedResult.recommendations" :key="i">{{ rec }}</li>
            </ul>
          </div>
        </div>

        <!-- Distributed Results -->
        <div v-if="distributedResult" class="result-card">
          <h3>
            <span class="indicator distributed"></span>
            Distributed Activity Simulation
          </h3>
          <p class="description">Aktivnosti distribuirane iz različitih gradova (Beograd, Novi Sad, Niš...)</p>

          <div class="stats-grid">
            <div class="stat">
              <span class="label">Avg Response Time</span>
              <span class="value" :class="getPerformanceClass(distributedResult.avgResponseTimeMs)">
                {{ formatMs(distributedResult.avgResponseTimeMs) }}
              </span>
            </div>
            <div class="stat">
              <span class="label">P95</span>
              <span class="value">{{ formatMs(distributedResult.p95ResponseTimeMs) }}</span>
            </div>
            <div class="stat">
              <span class="label">Success Rate</span>
              <span class="value">{{ formatPercent(distributedResult.successRate) }}</span>
            </div>
            <div class="stat">
              <span class="label">Requests/sec</span>
              <span class="value">{{ distributedResult.requestsPerSecond?.toFixed(2) }}</span>
            </div>
          </div>

          <div v-if="distributedResult.analysis" class="analysis-text">
            <strong>Analysis:</strong> {{ distributedResult.analysis }}
          </div>
        </div>
      </div>

      <!-- Analyses Tab -->
      <div v-if="activeTab === 'analyses'" class="analyses-tab">
        <div class="analysis-controls">
          <button @click="runAnalysis('locality')" :disabled="loading" class="btn">
            Locality Analysis
          </button>
          <button @click="runAnalysis('frequency')" :disabled="loading" class="btn">
            Frequency Analysis
          </button>
          <button @click="runAnalysis('performance-impact')" :disabled="loading" class="btn">
            Performance Impact
          </button>
        </div>

        <!-- Locality Analysis -->
        <div v-if="localityAnalysis" class="analysis-card">
          <h3>🏘️ Locality Analysis</h3>
          <p class="question">Da li se trending razlikuje između korisnika koji žive u istoj ulici?</p>

          <div class="analysis-result">
            <div class="result-item">
              <span class="label">Differs by Locality:</span>
              <span :class="localityAnalysis.trendingDiffersByLocality ? 'yes' : 'no'">
                {{ localityAnalysis.trendingDiffersByLocality ? 'DA' : 'NE' }}
              </span>
            </div>
            <div class="result-item">
              <span class="label">Min Distance for Different Trending:</span>
              <span>{{ localityAnalysis.minimumDistanceForDifferentTrendingKm?.toFixed(1) }} km</span>
            </div>
          </div>

          <div v-if="localityAnalysis.localityAnalysis" class="analysis-text">
            {{ localityAnalysis.localityAnalysis }}
          </div>

          <div v-if="localityAnalysis.overallRecommendation" class="recommendation-box">
            <strong>Recommendation:</strong> {{ localityAnalysis.overallRecommendation }}
          </div>
        </div>

        <!-- Frequency Analysis -->
        <div v-if="frequencyAnalysis" class="analysis-card">
          <h3>⏱️ Frequency Analysis</h3>
          <p class="question">Koliko često je potrebno računati trending?</p>

          <div class="analysis-result">
            <div class="result-item">
              <span class="label">Current Frequency:</span>
              <span>{{ frequencyAnalysis.currentFrequency }}</span>
            </div>
            <div class="result-item">
              <span class="label">Recommended:</span>
              <span class="recommended">{{ frequencyAnalysis.recommendedFrequency }}</span>
            </div>
          </div>

          <div v-if="frequencyAnalysis.frequencyRationale" class="analysis-text">
            {{ frequencyAnalysis.frequencyRationale }}
          </div>

          <div v-if="frequencyAnalysis.implementationSuggestion" class="implementation-box">
            <strong>Implementation:</strong>
            <pre>{{ frequencyAnalysis.implementationSuggestion }}</pre>
          </div>
        </div>

        <!-- Performance Impact Analysis -->
        <div v-if="performanceImpactAnalysis" class="analysis-card">
          <h3>⚡ Performance Impact Analysis</h3>
          <p class="question">Da li trending operacije narušavaju performanse osnovnih funkcionalnosti?</p>

          <div class="analysis-result">
            <div class="result-item">
              <span class="label">Baseline Response Time:</span>
              <span>{{ formatMs(performanceImpactAnalysis.baselineResponseTimeMs) }}</span>
            </div>
            <div class="result-item">
              <span class="label">During Trending Compute:</span>
              <span>{{ formatMs(performanceImpactAnalysis.duringTrendingComputeMs) }}</span>
            </div>
            <div class="result-item">
              <span class="label">Impact:</span>
              <span :class="performanceImpactAnalysis.impactsBasicFunctionality ? 'impact-high' : 'impact-low'">
                {{ formatPercent(performanceImpactAnalysis.performanceImpactPercent) }}
                {{ performanceImpactAnalysis.impactsBasicFunctionality ? '⚠️' : '✓' }}
              </span>
            </div>
          </div>

          <div v-if="performanceImpactAnalysis.performanceAnalysis" class="analysis-text">
            {{ performanceImpactAnalysis.performanceAnalysis }}
          </div>
        </div>
      </div>

      <!-- Comparison Tab -->
      <div v-if="activeTab === 'comparison'" class="comparison-tab">
        <h3>Concentrated vs Distributed Comparison</h3>

        <div v-if="comparison" class="comparison-table">
          <table>
            <thead>
              <tr>
                <th>Metric</th>
                <th>Concentrated</th>
                <th>Distributed</th>
                <th>Difference</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>Avg Response Time</td>
                <td>{{ formatMs(comparison.concentratedAvg) }}</td>
                <td>{{ formatMs(comparison.distributedAvg) }}</td>
                <td :class="comparison.difference > 0 ? 'negative' : 'positive'">
                  {{ comparison.difference > 0 ? '+' : '' }}{{ formatMs(comparison.difference) }}
                  ({{ comparison.differencePercent > 0 ? '+' : '' }}{{ comparison.differencePercent.toFixed(1) }}%)
                </td>
              </tr>
            </tbody>
          </table>

          <div class="conclusion-box">
            <strong>Conclusion:</strong> {{ comparison.conclusion }}
          </div>
        </div>

        <div v-else class="no-data">
          <p>Run both simulations to see comparison</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.simulation-dashboard {
  max-width: 1200px;
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

/* Parameters */
.params-section {
  background: #f5f5f5;
  padding: 1.5rem;
  border-radius: 8px;
  margin-bottom: 2rem;
}

.params-section h3 {
  margin: 0 0 1rem;
  color: #333;
}

.params-grid {
  display: flex;
  gap: 1.5rem;
  align-items: flex-end;
  flex-wrap: wrap;
}

.param {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.param label {
  font-size: 0.9rem;
  color: #555;
}

.param input {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  width: 120px;
}

.btn {
  padding: 0.6rem 1.2rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: background 0.2s;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}

.btn-secondary {
  background: #6c757d;
  color: white;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Error & Loading */
.error-banner {
  background: #f8d7da;
  color: #721c24;
  padding: 1rem;
  border-radius: 4px;
  margin-bottom: 1rem;
}

.loading-overlay {
  text-align: center;
  padding: 2rem;
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

/* Tabs */
.tabs {
  display: flex;
  gap: 0;
  margin-bottom: 1.5rem;
  border-bottom: 2px solid #eee;
}

.tabs button {
  padding: 0.75rem 1.5rem;
  border: none;
  background: none;
  font-size: 1rem;
  color: #666;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
}

.tabs button.active {
  color: #007bff;
  border-bottom-color: #007bff;
}

.tabs button:hover:not(.active) {
  color: #333;
}

/* Result Cards */
.result-card, .analysis-card {
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.result-card h3, .analysis-card h3 {
  margin: 0 0 0.5rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.indicator {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.indicator.concentrated {
  background: #dc3545;
}

.indicator.distributed {
  background: #28a745;
}

.description, .question {
  color: #666;
  font-size: 0.9rem;
  margin-bottom: 1rem;
}

.question {
  font-style: italic;
  background: #f8f9fa;
  padding: 0.5rem;
  border-radius: 4px;
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 1rem;
  margin-bottom: 1rem;
}

.stat {
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 6px;
  text-align: center;
}

.stat .label {
  display: block;
  font-size: 0.8rem;
  color: #666;
  margin-bottom: 0.25rem;
}

.stat .value {
  font-size: 1.25rem;
  font-weight: 600;
}

.stat .value.excellent { color: #28a745; }
.stat .value.good { color: #17a2b8; }
.stat .value.moderate { color: #ffc107; }
.stat .value.poor { color: #dc3545; }

/* Analysis Results */
.analysis-result {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
  margin-bottom: 1rem;
}

.result-item {
  background: #f8f9fa;
  padding: 0.75rem 1rem;
  border-radius: 4px;
}

.result-item .label {
  display: block;
  font-size: 0.8rem;
  color: #666;
}

.result-item .yes { color: #dc3545; font-weight: 600; }
.result-item .no { color: #28a745; font-weight: 600; }
.result-item .recommended { color: #007bff; font-weight: 600; }
.result-item .impact-high { color: #dc3545; }
.result-item .impact-low { color: #28a745; }

/* Text Sections */
.analysis-text {
  background: #e7f3ff;
  padding: 1rem;
  border-radius: 4px;
  margin-bottom: 1rem;
  line-height: 1.6;
}

.recommendation-box, .implementation-box, .conclusion-box {
  background: #d4edda;
  border-left: 4px solid #28a745;
  padding: 1rem;
  border-radius: 0 4px 4px 0;
  margin-top: 1rem;
}

.implementation-box pre {
  background: #f8f9fa;
  padding: 0.5rem;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 0.85rem;
  margin-top: 0.5rem;
}

.recommendations ul {
  margin: 0.5rem 0 0 1.5rem;
  padding: 0;
}

/* Comparison Table */
.comparison-table {
  margin-top: 1rem;
}

.comparison-table table {
  width: 100%;
  border-collapse: collapse;
}

.comparison-table th, .comparison-table td {
  padding: 0.75rem 1rem;
  text-align: left;
  border-bottom: 1px solid #eee;
}

.comparison-table th {
  background: #f8f9fa;
  font-weight: 600;
}

.comparison-table .positive { color: #28a745; }
.comparison-table .negative { color: #dc3545; }

/* Controls */
.sim-controls, .analysis-controls {
  display: flex;
  gap: 1rem;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
}

.no-data {
  text-align: center;
  color: #666;
  padding: 2rem;
}

/* Responsive */
@media (max-width: 768px) {
  .simulation-dashboard {
    padding: 1rem;
  }

  .params-grid {
    flex-direction: column;
    align-items: stretch;
  }

  .param input {
    width: 100%;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

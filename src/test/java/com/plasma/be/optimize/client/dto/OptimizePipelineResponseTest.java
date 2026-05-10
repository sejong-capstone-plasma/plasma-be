package com.plasma.be.optimize.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OptimizePipelineResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void AI_최적화_파이프라인_응답을_역직렬화한다() throws Exception {
        OptimizePipelineResponse response = objectMapper.readValue("""
                {
                  "request_id": "test-001",
                  "process_type": "ETCH",
                  "baseline_outputs": { "etch_score": { "value": 7.5, "unit": "" } },
                  "optimization_result": {
                    "candidate_count": 1,
                    "optimization_candidates": [
                      {
                        "rank": 1,
                        "process_params": {
                          "pressure":     { "value": 42.0, "unit": "mTorr" },
                          "source_power": { "value": 840.0, "unit": "W" },
                          "bias_power":   { "value": 90.0,  "unit": "W" }
                        },
                        "prediction_result": {
                          "ion_flux":   { "value": 1.8, "unit": "cm^-2 s^-1" },
                          "ion_energy": { "value": 5.2, "unit": "eV" },
                          "etch_score": { "value": 9.1, "unit": "" }
                        },
                        "improvement_evaluation": {
                          "increase_value":   { "value": 1.6, "unit": "" },
                          "increase_percent": { "value": 21.3, "unit": "%" }
                        },
                        "score": 9.1
                      }
                    ]
                  },
                  "explanation": { "summary": "최적화 완료", "details": ["상세1"] }
                }
                """, OptimizePipelineResponse.class);

        assertThat(response.requestId()).isEqualTo("test-001");
        assertThat(response.processType()).isEqualTo("ETCH");
        assertThat(response.baselineOutputs().etchScore().value()).isEqualTo(7.5);

        assertThat(response.optimizationResult().candidateCount()).isEqualTo(1);
        assertThat(response.optimizationResult().optimizationCandidates()).hasSize(1);

        OptimizePipelineResponse.OptimizationCandidate candidate =
                response.optimizationResult().optimizationCandidates().get(0);
        assertThat(candidate.rank()).isEqualTo(1);
        assertThat(candidate.processParams().pressure().value()).isEqualTo(42.0);
        assertThat(candidate.processParams().sourcePower().value()).isEqualTo(840.0);
        assertThat(candidate.processParams().biasPower().unit()).isEqualTo("W");
        assertThat(candidate.predictionResult().etchScore().value()).isEqualTo(9.1);
        assertThat(candidate.improvementEvaluation().increasePercent().value()).isEqualTo(21.3);
        assertThat(candidate.score()).isEqualTo(9.1);

        assertThat(response.explanation().summary()).isEqualTo("최적화 완료");
    }
}

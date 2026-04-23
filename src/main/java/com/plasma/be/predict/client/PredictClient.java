package com.plasma.be.predict.client;

import com.plasma.be.predict.client.dto.PredictPipelineResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PredictClient {

    private static final String PREDICT_PIPELINE_ENDPOINT = "/ai/pipelines/predict";

    private final RestClient httpClient;

    public PredictClient(RestClient extractRestClient) {
        this.httpClient = extractRestClient;
    }

    public PredictPipelineResponse requestPredictPipeline(String processType,
                                                          Map<String, Double> paramValues,
                                                          Map<String, String> paramUnits,
                                                          String originalUserInput) {
        Map<String, Object> body = buildBody(processType, paramValues, paramUnits, originalUserInput);
        return httpClient.post()
                .uri(PREDICT_PIPELINE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(PredictPipelineResponse.class);
    }

    Map<String, Object> buildBody(String processType,
                                  Map<String, Double> paramValues,
                                  Map<String, String> paramUnits,
                                  String originalUserInput) {
        Map<String, Object> processParamsMap = new LinkedHashMap<>();
        for (String key : List.of("pressure", "source_power", "bias_power")) {
            processParamsMap.put(key, Map.of(
                    "value", paramValues.getOrDefault(key, 0.0),
                    "unit",  paramUnits.getOrDefault(key, "")));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", UUID.randomUUID().toString());
        body.put("original_user_input", originalUserInput);
        body.put("process_type", processType);
        body.put("process_params", processParamsMap);
        return body;
    }
}

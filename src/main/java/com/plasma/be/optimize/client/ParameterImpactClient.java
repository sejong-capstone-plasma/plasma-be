package com.plasma.be.optimize.client;

import com.plasma.be.chat.dto.ConfirmOptimizationResponse;
import com.plasma.be.optimize.client.dto.ParameterImpactResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ParameterImpactClient {

    private static final String ENDPOINT = "/ai/services/parameter-impact";

    private final RestClient httpClient;

    public ParameterImpactClient(@Qualifier("extractRestClient") RestClient restClient) {
        this.httpClient = restClient;
    }

    public ParameterImpactResponse requestParameterImpact(String processType,
                                                           ConfirmOptimizationResponse.ProcessParams params) {
        return httpClient.post()
                .uri(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildBody(processType, params))
                .retrieve()
                .body(ParameterImpactResponse.class);
    }

    Map<String, Object> buildBody(String processType, ConfirmOptimizationResponse.ProcessParams params) {
        Map<String, Object> processParamsMap = new LinkedHashMap<>();
        putParam(processParamsMap, "pressure", params.pressure());
        putParam(processParamsMap, "source_power", params.sourcePower());
        putParam(processParamsMap, "bias_power", params.biasPower());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request_id", UUID.randomUUID().toString());
        body.put("process_type", processType);
        body.put("process_params", processParamsMap);
        return body;
    }

    private void putParam(Map<String, Object> map, String key, ConfirmOptimizationResponse.ParamValue v) {
        if (v != null) {
            map.put(key, Map.of("value", v.value(), "unit", v.unit() == null ? "" : v.unit()));
        }
    }
}

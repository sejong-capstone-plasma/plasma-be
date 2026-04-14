package com.plasma.be.plasma.client;

import com.plasma.be.plasma.client.dto.PlasmaExtractRequest;
import com.plasma.be.plasma.client.dto.PlasmaExtractResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PlasmaAIClient {

    private final RestClient restClient;

    public PlasmaAIClient(RestClient plasmaAIRestClient) {
        this.restClient = plasmaAIRestClient;
    }

    public PlasmaExtractResponse extractParameters(String requestId, String userInput) {
        PlasmaExtractRequest request = new PlasmaExtractRequest(requestId, userInput);
        return restClient.post()
                .uri("/ai/services/extract-parameters")
                .body(request)
                .retrieve()
                .body(PlasmaExtractResponse.class);
    }
}

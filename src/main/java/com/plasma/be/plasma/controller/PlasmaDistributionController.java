package com.plasma.be.plasma.controller;

import com.plasma.be.plasma.dto.PlasmaDistributionResponse;
import com.plasma.be.plasma.service.PlasmaDistributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlasmaDistributionController implements PlasmaDistributionApi {

    private final PlasmaDistributionService service;

    public PlasmaDistributionController(PlasmaDistributionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<PlasmaDistributionResponse> findClosest(
            @RequestParam double pressure,
            @RequestParam("source_power") double sourcePower,
            @RequestParam("bias_power") double biasPower) {
        return service.findClosest(pressure, sourcePower, biasPower)
                .map(service::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

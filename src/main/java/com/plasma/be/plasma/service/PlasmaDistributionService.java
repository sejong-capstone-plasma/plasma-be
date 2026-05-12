package com.plasma.be.plasma.service;

import com.plasma.be.plasma.dto.PlasmaDistributionResponse;
import com.plasma.be.plasma.entity.PlasmaDistribution;
import com.plasma.be.plasma.repository.PlasmaDistributionRepository;
import com.plasma.be.predict.client.dto.PredictPipelineResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class PlasmaDistributionService {

    private static final Logger log = LoggerFactory.getLogger(PlasmaDistributionService.class);

    private static final double[] PRESSURE_GRID     = {2.0, 4.0, 6.0, 8.0, 10.0};
    private static final double[] SOURCE_POWER_GRID = {100.0, 200.0, 300.0, 400.0, 500.0};
    private static final double[] BIAS_POWER_GRID   = {200.0, 400.0, 600.0, 800.0, 1000.0};

    private final PlasmaDistributionRepository repository;

    public PlasmaDistributionService(PlasmaDistributionRepository repository) {
        this.repository = repository;
    }

    public Optional<PlasmaDistribution> findClosest(double pressure, double sourcePower, double biasPower) {
        double snappedPressure    = snapToNearest(pressure,    PRESSURE_GRID);
        double snappedSourcePower = snapToNearest(sourcePower, SOURCE_POWER_GRID);
        double snappedBiasPower   = snapToNearest(biasPower,   BIAS_POWER_GRID);
        return repository.findByPrsAndSourcePowerAndBiasPower(snappedPressure, snappedSourcePower, snappedBiasPower);
    }

    public PlasmaDistributionResponse toResponse(PlasmaDistribution entity) {
        return new PlasmaDistributionResponse(
                entity.getPrs(),
                entity.getSourcePower(),
                entity.getBiasPower(),
                entity.getIonFlux(),
                entity.getAvgEnergy(),
                entity.getIedEnergyMin(),
                parseJsonArray(entity.getIedValues()),
                parseJsonArray(entity.getIadValues()),
                parseJsonArray(entity.getCurValues())
        );
    }

    private List<Double> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        String trimmed = json.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            log.warn("Unexpected JSON array format: {}", trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed);
            return List.of();
        }
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        if (inner.isEmpty()) return List.of();
        try {
            return Arrays.stream(inner.split(","))
                    .map(String::trim)
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse JSON array element: {}", e.getMessage());
            return List.of();
        }
    }

    public PredictPipelineResponse.Graphs buildGraphs(double pressure, double sourcePower, double biasPower) {
        return findClosest(pressure, sourcePower, biasPower)
                .map(this::toGraphs)
                .orElse(null);
    }

    private PredictPipelineResponse.Graphs toGraphs(PlasmaDistribution entity) {
        List<Double> iedY = parseJsonArray(entity.getIedValues());
        List<Double> iadY = parseJsonArray(entity.getIadValues());
        List<Double> curY = parseJsonArray(entity.getCurValues());
        double iedMin = entity.getIedEnergyMin() != null ? entity.getIedEnergyMin() : 0.0;

        List<PredictPipelineResponse.XYPoint> ied = IntStream.range(0, iedY.size())
                .mapToObj(i -> new PredictPipelineResponse.XYPoint(iedMin + i * 1.0, iedY.get(i)))
                .toList();
        List<PredictPipelineResponse.XYPoint> iad = IntStream.range(0, iadY.size())
                .mapToObj(i -> new PredictPipelineResponse.XYPoint(-10.0 + i * 0.1, iadY.get(i)))
                .toList();
        List<PredictPipelineResponse.XYPoint> cur = IntStream.range(0, curY.size())
                .mapToObj(i -> new PredictPipelineResponse.XYPoint(i / 600.0, curY.get(i)))
                .toList();

        return new PredictPipelineResponse.Graphs(cur, iad, ied);
    }

    private static double snapToNearest(double value, double[] grid) {
        double best = grid[0];
        double bestDist = Math.abs(value - grid[0]);
        for (double g : grid) {
            double dist = Math.abs(value - g);
            if (dist < bestDist) {
                bestDist = dist;
                best = g;
            }
        }
        return best;
    }
}

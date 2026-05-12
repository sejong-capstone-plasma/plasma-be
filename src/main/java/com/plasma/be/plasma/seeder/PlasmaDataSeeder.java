package com.plasma.be.plasma.seeder;

import com.plasma.be.plasma.entity.PlasmaDistribution;
import com.plasma.be.plasma.repository.PlasmaDistributionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PlasmaDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlasmaDataSeeder.class);

    private static final Map<String, Double> PRS_MAP = Map.of(
            "PRS_02", 2.0, "PRS_04", 4.0, "PRS_06", 6.0, "PRS_08", 8.0, "PRS_10", 10.0);
    private static final Map<String, Double> SOURCE_MAP = Map.of(
            "Source_100", 100.0, "Source_200", 200.0, "Source_300", 300.0,
            "Source_400", 400.0, "Source_500", 500.0);
    private static final Map<String, Double> BIAS_MAP = Map.of(
            "Bias_0000", 0.0, "Bias_0200", 200.0, "Bias_0400", 400.0,
            "Bias_0600", 600.0, "Bias_0800", 800.0, "Bias_1000", 1000.0);

    private final PlasmaDistributionRepository repository;

    @Value("${plasma.data.base-dir}")
    private String baseDir;

    public PlasmaDataSeeder(PlasmaDistributionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.info("plasma_distributions 테이블에 이미 데이터가 있습니다. 시딩을 건너뜁니다.");
            return;
        }

        log.info("plasma_distributions 시딩 시작 (base-dir: {})", baseDir);
        List<PlasmaDistribution> rows = new ArrayList<>();

        for (String prsDir : PRS_MAP.keySet().stream().sorted().toList()) {
            for (String srcDir : SOURCE_MAP.keySet().stream().sorted().toList()) {
                for (String biasDir : BIAS_MAP.keySet().stream().sorted().toList()) {
                    if (BIAS_MAP.get(biasDir) == 0.0) {
                        log.debug("Bias=0 스킵: {}/{}/{}", prsDir, srcDir, biasDir);
                        continue;
                    }
                    try {
                        rows.add(processCase(prsDir, srcDir, biasDir));
                    } catch (IOException e) {
                        log.error("파싱 실패: {}/{}/{} → {}", prsDir, srcDir, biasDir, e.getMessage());
                    }
                }
            }
        }

        repository.saveAll(rows);
        log.info("plasma_distributions 적재 완료: {}건", rows.size());
    }

    private PlasmaDistribution processCase(String prsDir, String srcDir, String biasDir) throws IOException {
        Path outPath = Path.of(baseDir, prsDir, srcDir, biasDir, "0d_result", "output");
        Path iedPath = outPath.resolve("IED").resolve("Ar+.txt");

        if (!Files.exists(iedPath)) {
            throw new IOException("출력 파일 없음: " + iedPath);
        }

        double[][] ied = parseDistribution(iedPath);
        double[][] iad = parseDistribution(outPath.resolve("IAD").resolve("Ar+.txt"));
        double[] scalars = parseIonSpecies(outPath.resolve("IonSpecies.txt"));
        double[][] cur = parseDistribution(outPath.resolve("CUR").resolve("J0h_h.txt"));

        return PlasmaDistribution.create(
                PRS_MAP.get(prsDir), SOURCE_MAP.get(srcDir), BIAS_MAP.get(biasDir),
                scalars[0], scalars[1],
                ied[0][0],
                toJson(ied[0]), toJson(ied[1]),
                toJson(iad[0]), toJson(iad[1]),
                toJson(cur[0]), toJson(cur[1]));
    }

    // [0] = x배열, [1] = y배열
    private double[][] parseDistribution(Path path) throws IOException {
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                xs.add(Double.parseDouble(parts[0]));
                ys.add(Double.parseDouble(parts[1]));
            }
        }
        return new double[][]{toArray(xs), toArray(ys)};
    }

    // [0] = flux, [1] = avg_energy
    private double[] parseIonSpecies(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                return new double[]{Double.parseDouble(parts[3]), Double.parseDouble(parts[4])};
            }
        }
        throw new IOException("IonSpecies.txt 데이터 줄 없음: " + path);
    }

    private double[] toArray(List<Double> list) {
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private String toJson(double[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(values[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}

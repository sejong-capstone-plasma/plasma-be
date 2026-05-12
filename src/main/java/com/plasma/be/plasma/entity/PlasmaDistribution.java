package com.plasma.be.plasma.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "plasma_distributions")
public class PlasmaDistribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prs")
    private Double prs;

    @Column(name = "source_power")
    private Double sourcePower;

    @Column(name = "bias_power")
    private Double biasPower;

    @Column(name = "ion_flux")
    private Double ionFlux;

    @Column(name = "avg_energy")
    private Double avgEnergy;

    @Column(name = "ied_energy_min")
    private Double iedEnergyMin;

    @Column(name = "ied_x_values", columnDefinition = "JSON")
    private String iedXValues;

    @Column(name = "ied_y_values", columnDefinition = "JSON")
    private String iedYValues;

    @Column(name = "iad_x_values", columnDefinition = "JSON")
    private String iadXValues;

    @Column(name = "iad_y_values", columnDefinition = "JSON")
    private String iadYValues;

    @Column(name = "cur_x_values", columnDefinition = "JSON")
    private String curXValues;

    @Column(name = "cur_y_values", columnDefinition = "JSON")
    private String curYValues;

    protected PlasmaDistribution() {
    }

    private PlasmaDistribution(Double prs, Double sourcePower, Double biasPower,
                                Double ionFlux, Double avgEnergy,
                                Double iedEnergyMin,
                                String iedXValues, String iedYValues,
                                String iadXValues, String iadYValues,
                                String curXValues, String curYValues) {
        this.prs = prs;
        this.sourcePower = sourcePower;
        this.biasPower = biasPower;
        this.ionFlux = ionFlux;
        this.avgEnergy = avgEnergy;
        this.iedEnergyMin = iedEnergyMin;
        this.iedXValues = iedXValues;
        this.iedYValues = iedYValues;
        this.iadXValues = iadXValues;
        this.iadYValues = iadYValues;
        this.curXValues = curXValues;
        this.curYValues = curYValues;
    }

    public static PlasmaDistribution create(Double prs, Double sourcePower, Double biasPower,
                                             Double ionFlux, Double avgEnergy,
                                             Double iedEnergyMin,
                                             String iedXValues, String iedYValues,
                                             String iadXValues, String iadYValues,
                                             String curXValues, String curYValues) {
        return new PlasmaDistribution(prs, sourcePower, biasPower,
                ionFlux, avgEnergy, iedEnergyMin,
                iedXValues, iedYValues, iadXValues, iadYValues, curXValues, curYValues);
    }

    public Long getId() { return id; }
    public Double getPrs() { return prs; }
    public Double getSourcePower() { return sourcePower; }
    public Double getBiasPower() { return biasPower; }
    public Double getIonFlux() { return ionFlux; }
    public Double getAvgEnergy() { return avgEnergy; }
    public Double getIedEnergyMin() { return iedEnergyMin; }
    public String getIedXValues() { return iedXValues; }
    public String getIedYValues() { return iedYValues; }
    public String getIadXValues() { return iadXValues; }
    public String getIadYValues() { return iadYValues; }
    public String getCurXValues() { return curXValues; }
    public String getCurYValues() { return curYValues; }
}

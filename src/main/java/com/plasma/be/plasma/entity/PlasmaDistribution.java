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

    @Column(name = "ied_values", columnDefinition = "JSON")
    private String iedValues;

    @Column(name = "iad_values", columnDefinition = "JSON")
    private String iadValues;

    protected PlasmaDistribution() {
    }

    private PlasmaDistribution(Double prs, Double sourcePower, Double biasPower,
                                Double ionFlux, Double avgEnergy,
                                Double iedEnergyMin, String iedValues, String iadValues) {
        this.prs = prs;
        this.sourcePower = sourcePower;
        this.biasPower = biasPower;
        this.ionFlux = ionFlux;
        this.avgEnergy = avgEnergy;
        this.iedEnergyMin = iedEnergyMin;
        this.iedValues = iedValues;
        this.iadValues = iadValues;
    }

    public static PlasmaDistribution create(Double prs, Double sourcePower, Double biasPower,
                                             Double ionFlux, Double avgEnergy,
                                             Double iedEnergyMin, String iedValues, String iadValues) {
        return new PlasmaDistribution(prs, sourcePower, biasPower,
                ionFlux, avgEnergy, iedEnergyMin, iedValues, iadValues);
    }

    public Long getId() { return id; }
    public Double getPrs() { return prs; }
    public Double getSourcePower() { return sourcePower; }
    public Double getBiasPower() { return biasPower; }
    public Double getIonFlux() { return ionFlux; }
    public Double getAvgEnergy() { return avgEnergy; }
    public Double getIedEnergyMin() { return iedEnergyMin; }
    public String getIedValues() { return iedValues; }
    public String getIadValues() { return iadValues; }
}

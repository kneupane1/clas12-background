package modules;

import java.util.List;
import objects.Hit;
import analysis.Module;
import objects.Event;
import objects.True;
import org.jlab.detector.base.DetectorType;
import org.jlab.groot.base.GStyle;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.graphics.EmbeddedPad;
import org.jlab.groot.group.DataGroup;

/**
 * Background module for the HTCC (High Threshold Cherenkov Counter).
 *
 * Studies the impact of adding wing shielding in front of the HTCC by
 * measuring hit rates, NPhe spectra, and background particle origins.
 *
 * HTCC bank column mapping (from htcc_hitprocess.cpp integrateDgt):
 * bank "layer" = idhalf → half (1 or 2, left/right PMT within sector-ring)
 * bank "component" = idring → ring (1–4, polar angle layer)
 * A unique PMT = (sector, half, ring), giving 6×2×4 = 48 PMTs total.
 *
 * ADC → NPhe conversion:
 * GEMC stores ADC = 100 × NPhe × mc_gain.
 * mc_gain = 0.5 for all channels (from /calibration/htcc/mc_gain CCDB table).
 * Therefore: NPhe = ADC / (100 × 0.5) = ADC / 50.
 *
 * For optical-photon simulation MC::True pid=0 (optical photon);
 * the module falls back to mpid to identify the Cherenkov-producing particle.
 */
public class HTCCmodule extends Module {

    private static final int NSECTORS = 6;
    private static final int NRINGS = 4; // component 1–4
    private static final int NHALVES = 2; // layer 1–2
    private static final double ADC_TO_NPHE = 50.0; // 100 * mc_gain(=0.5)

    public HTCCmodule() {
        super(DetectorType.HTCC);
    }

    // -----------------------------------------------------------------------
    // Histogram definitions
    // -----------------------------------------------------------------------

    /** 2D hit-rate map: sector (x) vs ring (y), in % occupancy per PMT pair. */
    public DataGroup occupancy() {
        DataGroup dg = new DataGroup(1, 1);
        H2F hi = histo2D("hi_occ_htcc", "Sector", "Ring",
                NSECTORS, 0.5, NSECTORS + 0.5,
                NRINGS, 0.5, NRINGS + 0.5);
        dg.addDataSet(hi, 0);
        return dg;
    }

    /**
     * Hit rate [kHz] vs sector — primary figure-of-merit for the wing study:
     * sector 4 should drop when wings block the Moller cone.
     * Stacked by particle species (uses mpid for optical-photon hits).
     */
    public DataGroup rateBySector() {
        DataGroup dg = new DataGroup(1, 1);
        for (int ip = 0; ip < PNAMES.length; ip++) {
            H1F hi = histo1D("hi_rate_sector_" + PNAMES[ip], PNAMES[ip],
                    "Sector", "Rate [kHz]",
                    NSECTORS, 0.5, NSECTORS + 0.5, 0);
            this.setHistoAttr(hi, ip < 5 ? ip + 1 : ip + 3);
            dg.addDataSet(hi, 0);
        }
        return dg;
    }

    /**
     * Hit rate [kHz] vs ring — shows which polar-angle ring is most affected
     * by the wing shadow (ring 1 is most forward, closest to Moller cone).
     * Ring = bank "component" (1–4); bank "layer" is the half (1–2).
     */
    public DataGroup rateByRing() {
        DataGroup dg = new DataGroup(1, 1);
        for (int ip = 0; ip < PNAMES.length; ip++) {
            H1F hi = histo1D("hi_rate_ring_" + PNAMES[ip], PNAMES[ip],
                    "Ring", "Rate [kHz]",
                    NRINGS, 0.5, NRINGS + 0.5, 0);
            this.setHistoAttr(hi, ip < 5 ? ip + 1 : ip + 3);
            dg.addDataSet(hi, 0);
        }
        return dg;
    }

    /**
     * NPhe spectrum [kHz] — distribution of photoelectrons per HTCC hit.
     * NPhe = ADC / ADC_TO_NPHE = ADC / 50.
     * Background hits from Moller electrons typically give NPhe ~ 5–30.
     */
    public DataGroup npheSpectrum() {
        DataGroup dg = new DataGroup(1, 1);
        H1F hi = histo1D("hi_nphe", "HTCC NPhe", "NPhe", "Rate [kHz]",
                100, 0, 50, 4);
        hi.setLineWidth(2);
        dg.addDataSet(hi, 0);
        return dg;
    }

    /**
     * NPhe spectrum per PMT [kHz/PMT], log-Y — mirrors Figure 6 left:
     * black = all 48 PMTs averaged, then rings 1–4 individually.
     * Ring is identified by bank "component" (1–4).
     */
    public DataGroup nphePerPMT() {
        DataGroup dg = new DataGroup(1, 1);
        H1F hi_all = histo1D("hi_nphe_pmt_all", "All PMTs", "NPhe", "Rate [kHz/PMT]", 100, 0, 50, 0);
        H1F hi_ring1 = histo1D("hi_nphe_pmt_ring1", "Ring 1", "NPhe", "Rate [kHz/PMT]", 100, 0, 50, 0);
        H1F hi_ring2 = histo1D("hi_nphe_pmt_ring2", "Ring 2", "NPhe", "Rate [kHz/PMT]", 100, 0, 50, 0);
        H1F hi_ring3 = histo1D("hi_nphe_pmt_ring3", "Ring 3", "NPhe", "Rate [kHz/PMT]", 100, 0, 50, 0);
        H1F hi_ring4 = histo1D("hi_nphe_pmt_ring4", "Ring 4", "NPhe", "Rate [kHz/PMT]", 100, 0, 50, 0);
        hi_all.setLineColor(1);
        hi_all.setLineWidth(2);
        hi_ring1.setLineColor(4);
        hi_ring1.setLineWidth(2);
        hi_ring2.setLineColor(3);
        hi_ring2.setLineWidth(2);
        hi_ring3.setLineColor(44);
        hi_ring3.setLineWidth(2);
        hi_ring4.setLineColor(2);
        hi_ring4.setLineWidth(2);
        dg.addDataSet(hi_all, 0);
        dg.addDataSet(hi_ring1, 0);
        dg.addDataSet(hi_ring2, 0);
        dg.addDataSet(hi_ring3, 0);
        dg.addDataSet(hi_ring4, 0);
        return dg;
    }

    /**
     * PMT rate [kHz/PMT] vs ring — mirrors Figure 6 right:
     * blue = NPhe > 0 (all hits), red = NPhe > 10 (quality threshold).
     * NPhe computed as ADC / 50 before threshold comparison.
     * hi_10 added last so it draws on top; hi_0 (wider line) stays visible
     * underneath.
     */
    public DataGroup rateVsRingThreshold() {
        DataGroup dg = new DataGroup(1, 1);
        H1F hi0 = histo1D("hi_rvr_nphe0", "NPhe > 0", "Ring", "Rate [kHz/PMT]", NRINGS, 0.5, NRINGS + 0.5, 0);
        H1F hi10 = histo1D("hi_rvr_nphe10", "NPhe > 10", "Ring", "Rate [kHz/PMT]", NRINGS, 0.5, NRINGS + 0.5, 0);
        hi0.setLineColor(4);
        hi0.setLineWidth(3);
        hi10.setLineColor(2);
        hi10.setLineWidth(2);
        dg.addDataSet(hi0, 0);
        dg.addDataSet(hi10, 0);
        return dg;
    }

    /**
     * Background-origin plots:
     * pad 0 — 2D vertex (Vz, Vr)
     * pad 1 — 2D vertex (Vx, Vy)
     * pad 2 — Vz stacked by species [kHz]
     * pad 3 — kinetic energy stacked by species [kHz]
     */
    public DataGroup originBG() {
        DataGroup dg = new DataGroup(2, 2);

        H2F hi_rz = histo2D("hi_bg_rz", "Vz (mm)", "Vr (mm)",
                200, -500, 1500, 200, 0, 700);
        H2F hi_xy = histo2D("hi_bg_xy", "Vx (mm)", "Vy (mm)",
                200, -700, 700, 200, -700, 700);
        dg.addDataSet(hi_rz, 0);
        dg.addDataSet(hi_xy, 1);

        for (int ip = 0; ip < PNAMES.length; ip++) {
            H1F hi_vz = histo1D("hi_bg_vz_" + PNAMES[ip], PNAMES[ip],
                    "Vz (mm)", "Rate [kHz]",
                    200, -500, 1500, 0);
            this.setHistoAttr(hi_vz, ip < 5 ? ip + 1 : ip + 3);
            dg.addDataSet(hi_vz, 2);
        }

        for (int ip = 0; ip < PNAMES.length; ip++) {
            H1F hi_e = histo1D("hi_bg_energy_" + PNAMES[ip], PNAMES[ip],
                    "E (MeV)", "Rate [kHz]",
                    200, 0, 500, 0);
            this.setHistoAttr(hi_e, ip < 5 ? ip + 1 : ip + 3);
            dg.addDataSet(hi_e, 3);
        }

        return dg;
    }

    /**
     * Per-sector background vertex Vz [kHz].
     * Comparing sector 4 (with wings) to sectors 1–3, 5–6 (without wings)
     * is the main diagnostic for the shielding impact.
     */
    public DataGroup sectorBG() {
        DataGroup dg = new DataGroup(3, 2);
        for (int is = 0; is < NSECTORS; is++) {
            int s = is + 1;
            for (int ip = 0; ip < PNAMES.length; ip++) {
                H1F hi = histo1D("hi_bg_s" + s + "_" + PNAMES[ip], PNAMES[ip],
                        "Vz (mm)", "Rate [kHz]",
                        200, -500, 1500, 0);
                this.setHistoAttr(hi, ip < 5 ? ip + 1 : ip + 3);
                dg.addDataSet(hi, is);
            }
        }
        return dg;
    }

    // -----------------------------------------------------------------------
    // Module interface
    // -----------------------------------------------------------------------

    @Override
    public void createHistos() {
        this.getHistos().put("Occupancy", this.occupancy());
        this.getHistos().put("Rate by Sector", this.rateBySector());
        this.getHistos().put("Rate by Ring", this.rateByRing());
        this.getHistos().put("NPhe Spectrum", this.npheSpectrum());
        this.getHistos().put("NPhe per PMT", this.nphePerPMT());
        this.getHistos().put("Rate vs Ring (Threshold)", this.rateVsRingThreshold());
        this.getHistos().put("Origin of Bg", this.originBG());
        this.getHistos().put("Sector BG", this.sectorBG());
    }

    @Override
    public void fillHistos(Event event) {
        List<Hit> hits = event.getHits(DetectorType.HTCC);
        if (hits == null)
            return;

        fillOccupancy(this.getHistos().get("Occupancy"), hits);
        fillRateBySector(this.getHistos().get("Rate by Sector"), hits);
        fillRateByRing(this.getHistos().get("Rate by Ring"), hits);
        fillNPhe(this.getHistos().get("NPhe Spectrum"), hits);
        fillNphePerPMT(this.getHistos().get("NPhe per PMT"), hits);
        fillRateVsRingThreshold(this.getHistos().get("Rate vs Ring (Threshold)"), hits);
        fillOrigin(this.getHistos().get("Origin of Bg"), hits);
        fillSectorBG(this.getHistos().get("Sector BG"), hits);
    }

    @Override
    public void analyzeHistos() {
        // Luminosity scaling: simulation ran at LUMI_EVENT=450, design is 1350.
        // Pass -Dlumi=<LUMI_EVENT> on the command line to rescale rates.
        // lumiScale = x / 1350 (e.g. x=1350 → no change; x=450 → factor 1/3)
        double x = Double.parseDouble(System.getProperty("lumi", "1350.0"));
        double lumiScale = x / 1350.0;

        // Occupancy [%] per PMT pair: hits / (0.01 × nevents × NHALVES)
        this.normalize(this.getHistos().get("Occupancy"),
                0.01 * this.getNevents() * NHALVES);
        this.normalize(this.getHistos().get("Occupancy"), lumiScale);

        this.normalizeToTime(this.getHistos().get("Rate by Sector"));
        this.normalize(this.getHistos().get("Rate by Sector"), lumiScale);

        this.normalizeToTime(this.getHistos().get("Rate by Ring"));
        this.normalize(this.getHistos().get("Rate by Ring"), lumiScale);

        this.normalizeToTime(this.getHistos().get("NPhe Spectrum"));
        this.normalize(this.getHistos().get("NPhe Spectrum"), lumiScale);

        this.normalizeToTime(this.getHistos().get("Origin of Bg"));
        this.normalize(this.getHistos().get("Origin of Bg"), lumiScale);

        this.normalizeToTime(this.getHistos().get("Sector BG"));
        this.normalize(this.getHistos().get("Sector BG"), lumiScale);

        // NPhe per PMT: kHz, lumi-scaled, then divide by number of PMTs
        final int N_ALL_PMTS = NSECTORS * NRINGS * NHALVES; // 48
        final int N_PER_RING = NSECTORS * NHALVES; // 12
        DataGroup npheGroup = this.getHistos().get("NPhe per PMT");
        this.normalizeToTime(npheGroup);
        this.normalize(npheGroup, lumiScale);
        this.normalize(npheGroup.getH1F("hi_nphe_pmt_all"), (double) N_ALL_PMTS);
        for (int ir = 1; ir <= NRINGS; ir++)
            this.normalize(npheGroup.getH1F("hi_nphe_pmt_ring" + ir), (double) N_PER_RING);

        // Rate vs ring by threshold: kHz, lumi-scaled, then per-PMT (12 per ring)
        DataGroup rvrGroup = this.getHistos().get("Rate vs Ring (Threshold)");
        this.normalizeToTime(rvrGroup);
        this.normalize(rvrGroup, lumiScale);
        this.normalize(rvrGroup, (double) N_PER_RING);
    }

    @Override
    public void setPlottingOptions(String name) {
        // Set per-canvas (GStyle is global; must set here to override other modules)
        GStyle.getAxisAttributesX().setTitleFontSize(32);
        GStyle.getAxisAttributesY().setTitleFontSize(32);
        GStyle.getAxisAttributesX().setLabelFontSize(26);
        GStyle.getAxisAttributesY().setLabelFontSize(26);
        GStyle.getAxisAttributesZ().setTitleFontSize(26);
        GStyle.getAxisAttributesZ().setLabelFontSize(22);

        for (EmbeddedPad pad : this.getCanvas(name).getCanvasPads())
            pad.setTitle("");

        // if (name.equals("Occupancy") || name.contains("Origin")) {
        // // GStyle is already set to 32/26 above; redrawing now applies those sizes to
        // H2F axes
        // this.getCanvas(name).draw(this.getHistos().get(name));
        // this.setLogZ(name);
        // }

        // // x=250 matches DC working value; y=50 places legend near the top inside the
        // plot
        // if (name.contains("Rate") || name.contains("BG"))
        // this.setLegend(name, 1100, 50);

        if (name.equals("NPhe per PMT")) {
            for (EmbeddedPad pad : this.getCanvas(name).getCanvasPads())
                pad.getAxisY().setLog(true);
            this.setLegend(name, 1100, 100);
        }

        if (name.equals("Rate vs Ring (Threshold)")) {
            for (EmbeddedPad pad : this.getCanvas(name).getCanvasPads())
                pad.getAxisY().setLog(true);
            this.setLegend(name, 1100, 50);
        }
    }

    // -----------------------------------------------------------------------
    // Fill methods
    // -----------------------------------------------------------------------

    public void fillOccupancy(DataGroup dg, List<Hit> hits) {
        for (Hit h : hits)
            // sector = sector (1-6), ring = component (1-4)
            dg.getH2F("hi_occ_htcc").fill(h.getSector(), h.getComponent());
    }

    public void fillRateBySector(DataGroup dg, List<Hit> hits) {
        for (Hit h : hits) {
            String pname = particleName(h);
            dg.getH1F("hi_rate_sector_all").fill(h.getSector());
            if (pname != null)
                dg.getH1F("hi_rate_sector_" + pname).fill(h.getSector());
            else
                dg.getH1F("hi_rate_sector_other").fill(h.getSector());
        }
    }

    public void fillRateByRing(DataGroup dg, List<Hit> hits) {
        for (Hit h : hits) {
            // ring = component (1-4); layer is the half (1-2)
            String pname = particleName(h);
            dg.getH1F("hi_rate_ring_all").fill(h.getComponent());
            if (pname != null)
                dg.getH1F("hi_rate_ring_" + pname).fill(h.getComponent());
            else
                dg.getH1F("hi_rate_ring_other").fill(h.getComponent());
        }
    }

    public void fillNPhe(DataGroup dg, List<Hit> hits) {
        for (Hit h : hits) {
            double nphe = h.getADC() / ADC_TO_NPHE;
            if (nphe > 0)
                dg.getH1F("hi_nphe").fill(nphe);
        }
    }

    public void fillOrigin(DataGroup dg, List<Hit> hits) {
        for (Hit h : hits) {
            True t = h.getTrue();
            if (t == null)
                continue;

            double vx = t.getVertex().x();
            double vy = t.getVertex().y();
            double vz = t.getVertex().z();
            double r = Math.sqrt(vx * vx + vy * vy);

            dg.getH2F("hi_bg_rz").fill(vz, r);
            dg.getH2F("hi_bg_xy").fill(vx, vy);

            dg.getH1F("hi_bg_vz_all").fill(vz);
            dg.getH1F("hi_bg_energy_all").fill(t.getKinEnergy());

            String pname = particleName(h);
            if (pname != null) {
                dg.getH1F("hi_bg_vz_" + pname).fill(vz);
                dg.getH1F("hi_bg_energy_" + pname).fill(t.getKinEnergy());
            } else {
                dg.getH1F("hi_bg_vz_other").fill(vz);
                dg.getH1F("hi_bg_energy_other").fill(t.getKinEnergy());
            }
        }
    }

    public void fillSectorBG(DataGroup dg, List<Hit> hits) {
        for (Hit h : hits) {
            True t = h.getTrue();
            if (t == null)
                continue;
            int s = h.getSector();

            dg.getH1F("hi_bg_s" + s + "_all").fill(t.getVertex().z());
            String pname = particleName(h);
            if (pname != null)
                dg.getH1F("hi_bg_s" + s + "_" + pname).fill(t.getVertex().z());
            else
                dg.getH1F("hi_bg_s" + s + "_other").fill(t.getVertex().z());
        }
    }

    public void fillNphePerPMT(DataGroup dg, List<Hit> hits) {
        for (Hit h : hits) {
            double nphe = h.getADC() / ADC_TO_NPHE;
            if (nphe <= 0)
                continue;
            dg.getH1F("hi_nphe_pmt_all").fill(nphe);
            // ring = component (1-4)
            int ring = h.getComponent();
            if (ring >= 1 && ring <= NRINGS)
                dg.getH1F("hi_nphe_pmt_ring" + ring).fill(nphe);
        }
    }

    public void fillRateVsRingThreshold(DataGroup dg, List<Hit> hits) {
        for (Hit h : hits) {
            double nphe = h.getADC() / ADC_TO_NPHE;
            // ring = component (1-4)
            int ring = h.getComponent();
            if (nphe > 0)
                dg.getH1F("hi_rvr_nphe0").fill(ring);
            if (nphe > 10)
                dg.getH1F("hi_rvr_nphe10").fill(ring);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the named particle category for a hit.
     * For optical-photon hits (pid == 0) the mother particle pid (mpid) is used
     * so that the Cherenkov-producing track species is reported.
     */
    private String particleName(Hit h) {
        True t = h.getTrue();
        if (t == null)
            return null;
        int pid = Math.abs(t.getPid());
        if (pid == 0)
            pid = Math.abs(t.getMPID());
        return this.pidToName(pid);
    }
}

package modules;

import analysis.Constants;
import analysis.Module;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import objects.Event;
import objects.Hit;
import objects.True;
import org.jlab.detector.base.DetectorType;
import org.jlab.groot.base.GStyle;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.graphics.EmbeddedPad;
import org.jlab.groot.group.DataGroup;

/**
 * Background module for the CLAS12 Forward Time-of-Flight (FTOF) detector.
 *
 * FTOF geometry (MC::True detector ID = 12):
 * Layer 1 = Panel 1a (23 paddles per sector)
 * Layer 2 = Panel 1b (62 paddles per sector)
 * Layer 3 = Panel 2 ( 5 paddles per sector)
 * 6 sectors total
 *
 * Plots produced:
 * Sector-Layer Map — 2D occupancy [%]: sector vs layer (from FTOF::adc/tdc)
 * Rate by Sector — rate [kHz] vs sector, one histogram per panel (from
 * FTOF::adc/tdc)
 * Paddle Map — 2D occupancy [%]: paddle vs layer, one pad per sector (from
 * FTOF::adc/tdc)
 * Position Map — Y vs X and R vs Z from MC::True avgX/Y/Z (always filled when
 * MC::True present)
 *
 * The first three groups require a digitized FTOF::adc or FTOF::tdc bank.
 * The Position Map is filled directly from MC::True and works for pure-MC
 * studies.
 * All position coordinates are in mm.
 */
public class FTOFmodule extends Module {

    private static final int NSECTORS = 6;
    private static final int NLAYERS = 3; // panels: 1=1a, 2=1b, 3=P2
    private static final int NPADDLES = 62; // max paddles (panel 1b)
    private static final String[] PANELS = { "Panel1a", "Panel1b", "Panel2" };

    // Concatenated x-axis for the Rate Map: Panel-1B | Panel-1A | Panel-2
    // Layer 2 (1b, 62 paddles): bins  1-62   offset=0
    // Layer 1 (1a, 23 paddles): bins 73-95   offset=72
    // Layer 3 (P2,  5 paddles): bins 107-111 offset=106
    // PADDLE_XOFFSET index = layer-1
    private static final int[] PADDLE_XOFFSET = { 72, 0, 106 };
    private static final int   RATEMAP_NBINS  = 120;

    // Diagnostic counters — printed once in analyzeHistos()
    private long diagTotal = 0, diagOrder1 = 0, diagLowEdep = 0, diagFilled = 0;

    // Set EXCLUDE_SECTOR=4 in the environment to drop one sector from all plots.
    private static final int EXCLUDED_SECTOR = Integer.parseInt(
            System.getenv().getOrDefault("EXCLUDE_SECTOR", "0"));

    // Physical extent of FTOF in metres (positions from MC::True are in mm → divide
    // by 1000 when filling)
    private static final double FTOF_Z_MIN = 3.5;
    private static final double FTOF_Z_MAX = 9.0;
    private static final double FTOF_R_MAX = 6.0;
    private static final double FTOF_XY_MAX = 5.5;

    public FTOFmodule() {
        super(DetectorType.FTOF);
    }

    // -------------------------------------------------------------------------
    // Histogram group definitions
    // -------------------------------------------------------------------------

    /** 2D occupancy [%] map: sector (x) vs layer/panel (y). */
    public DataGroup sectorLayerMap() {
        DataGroup dg = new DataGroup(1, 1);
        H2F hi = histo2D("hi_sec_lay", "Occupancy (%)",
                "Sector", "Layer  (1=1a  2=1b  3=P2)",
                NSECTORS, 0.5, NSECTORS + 0.5,
                NLAYERS, 0.5, NLAYERS + 0.5);
        dg.addDataSet(hi, 0);
        return dg;
    }

    /** Hit rate [kHz] vs sector — one histogram per panel. */
    public DataGroup rateBySector() {
        DataGroup dg = new DataGroup(3, 1);
        int[] colors = { 4, 2, 3 };
        for (int i = 0; i < NLAYERS; i++) {
            H1F hi = histo1D("hi_rate_sec_" + PANELS[i], PANELS[i],
                    "Sector", "Rate [kHz]",
                    NSECTORS, 0.5, NSECTORS + 0.5, 0);
            hi.setLineColor(colors[i]);
            hi.setLineWidth(2);
            dg.addDataSet(hi, i);
        }
        return dg;
    }

    /**
     * Per-sector 2D occupancy map: paddle/component (x) vs layer/panel (y).
     * Six pads, one per sector.
     */
    public DataGroup paddleMap() {
        DataGroup dg = new DataGroup(3, 2);
        for (int s = 1; s <= NSECTORS; s++) {
            H2F hi = histo2D("hi_paddle_s" + s, "Occupancy (%)",
                    "Paddle", "Layer  (1=1a  2=1b  3=P2)",
                    NPADDLES, 0.5, NPADDLES + 0.5,
                    NLAYERS, 0.5, NLAYERS + 0.5);
            hi.setTitle("Sector " + s);
            dg.addDataSet(hi, s - 1);
        }
        return dg;
    }

    /**
     * 2D rate map [kHz]: all panels concatenated on x (1B | 1A | P2) vs sector on y.
     * Mirrors Figure 7 of CLAS12 Note 2017-016.
     * X layout: Panel-1B paddles 1-62 (bins 1-62), gap, Panel-1A paddles 1-23
     * (bins 73-95), gap, Panel-2 paddles 1-5 (bins 107-111).
     */
    public DataGroup rateMap() {
        DataGroup dg = new DataGroup(1, 1);
        H2F hi = histo2D("hi_rate_map", "Rate [kHz]",
                "Paddle  (1B  |  1A  |  P2)", "Sector",
                RATEMAP_NBINS, 0.5, RATEMAP_NBINS + 0.5,
                NSECTORS, 0.5, NSECTORS + 0.5);
        dg.addDataSet(hi, 0);
        return dg;
    }

    /**
     * 2D PMT current map [µA]: same paddle layout as Rate Map (x) vs sector (y).
     * Weight per hit: Edep_MeV × photon_yield × 1.6e-7 [nC/ph] / (2×h_cm).
     * Photon yields: P1a/P2 = 373 ph/MeV, P1b = 1158 ph/MeV.
     * 2×h: P1a=10 (2×5cm thickness), P1b=12 (2×6cm thickness), P2=10 (2×5cm thickness).
     */
    public DataGroup pmtCurrentMap() {
        DataGroup dg = new DataGroup(1, 1);
        H2F hi = histo2D("hi_pmt_curr", "PMT Current [#muA]",
                "Paddle  (1B  |  1A  |  P2)", "Sector",
                RATEMAP_NBINS, 0.5, RATEMAP_NBINS + 0.5,
                NSECTORS, 0.5, NSECTORS + 0.5);
        dg.addDataSet(hi, 0);
        return dg;
    }

    /**
     * 2D position maps from MC::True avgX/Y/Z.
     * Pad 0: Y vs X (transverse plane).
     * Pad 1: R vs Z (longitudinal view), R = sqrt(X²+Y²).
     */
    public DataGroup positionMap() {
        DataGroup dg = new DataGroup(2, 1);
        H2F hiXY = histo2D("hi_posXY", "Y vs X",
                "X [m]", "Y [m]",
                200, -FTOF_XY_MAX, FTOF_XY_MAX,
                200, -FTOF_XY_MAX, FTOF_XY_MAX);
        H2F hiRZ = histo2D("hi_posRZ", "R vs Z",
                "Z [m]", "R [m]",
                200, FTOF_Z_MIN, FTOF_Z_MAX,
                200, 0, FTOF_R_MAX);
        dg.addDataSet(hiXY, 0);
        dg.addDataSet(hiRZ, 1);
        return dg;
    }

    // -------------------------------------------------------------------------
    // Module interface
    // -------------------------------------------------------------------------

    @Override
    public void createHistos() {
        this.getHistos().put("Sector-Layer Map", this.sectorLayerMap());
        this.getHistos().put("Rate by Sector", this.rateBySector());
        this.getHistos().put("Paddle Map", this.paddleMap());
        this.getHistos().put("Rate Map", this.rateMap());
        this.getHistos().put("PMT Current Map", this.pmtCurrentMap());
        this.getHistos().put("Position Map", this.positionMap());
    }

    @Override
    public void fillHistos(Event event) {
        List<Hit> allHits = event.getHits(DetectorType.FTOF);
        if (allHits != null) {
            // Mirror DC pattern: pre-filter into a clean list before filling.
            // order=0 selects one PMT per paddle (FTOF ADC has two rows/paddle).
            // 1 MeV threshold matches the FTOF hardware discriminator level.
            List<Hit> hits = new ArrayList<>();
            for (Hit h : allHits) {
                diagTotal++;
                if (h.getOrder() != 0)                  { diagOrder1++;  continue; }
                if (h.getTrue().getEdep() <= 1.0e-3)    { diagLowEdep++; continue; }
                if (EXCLUDED_SECTOR > 0 && h.getSector() == EXCLUDED_SECTOR) continue;
                hits.add(h);
            }
            diagFilled += hits.size();

            DataGroup dgMap     = this.getHistos().get("Sector-Layer Map");
            DataGroup dgRate    = this.getHistos().get("Rate by Sector");
            DataGroup dgPaddle  = this.getHistos().get("Paddle Map");
            DataGroup dgRateMap = this.getHistos().get("Rate Map");
            DataGroup dgCurr    = this.getHistos().get("PMT Current Map");

            for (Hit h : hits) {
                int sector = h.getSector();
                int layer  = h.getLayer();
                int paddle = h.getComponent();
                if (sector < 1 || sector > NSECTORS) continue;
                if (layer  < 1 || layer  > NLAYERS)  continue;

                dgMap.getH2F("hi_sec_lay").fill(sector, layer);
                dgRate.getH1F("hi_rate_sec_" + PANELS[layer - 1]).fill(sector);
                dgPaddle.getH2F("hi_paddle_s" + sector).fill(paddle, layer);
                dgRateMap.getH2F("hi_rate_map").fill(paddle + PADDLE_XOFFSET[layer - 1], sector);

                // PMT current (Carman's formula, from FTOF NIM paper):
                //   weight [nC] = Edep_MeV × photon_yield [ph/MeV] × 1.6e-7 [nC/ph] / (2×h)
                // Scintillator thickness h: P1a=5cm → 2h=10, P1b=6cm → 2h=12, P2=5cm → 2h=10.
                // NOTE: the paddle WIDTH (15/6/22 cm from Table 1) is a separate variable used
                // only in the normalised-occupancy plots — it does NOT appear here.
                // P2 has no override in the reference C++ code; it uses the same formula as P1a.
                double edep_MeV = h.getTrue().getEdep() * 1000.0;
                double weightCurrent;
                if (layer == 2) {
                    weightCurrent = edep_MeV * 1158.0 * 1.6e-7 / 12.0; // P1b: 2h=12
                } else {
                    weightCurrent = edep_MeV * 373.0  * 1.6e-7 / 10.0; // P1a and P2: 2h=10
                }
                dgCurr.getH2F("hi_pmt_curr").fill(paddle + PADDLE_XOFFSET[layer - 1], sector, weightCurrent);
            }
        }

        // Position maps from MC::True — convert mm → m to keep axis labels clean
        List<True> trues = event.getTrues(DetectorType.FTOF);
        if (trues != null) {
            DataGroup dgPos = this.getHistos().get("Position Map");
            for (True t : trues) {
                double x = t.getPosition().x() / 1000.0;
                double y = t.getPosition().y() / 1000.0;
                double z = t.getPosition().z() / 1000.0;
                double r = Math.sqrt(x * x + y * y);
                dgPos.getH2F("hi_posXY").fill(x, y);
                dgPos.getH2F("hi_posRZ").fill(z, r);
            }
        }
    }

    @Override
    public void analyzeHistos() {
        double x = Double.parseDouble(System.getProperty("lumi", "1350.0"));
        double lumiScale = x / 1350.0;

        // Occupancy [%] = hits / (events * 0.01), then lumi-scaled
        this.normalizeToEventsX100(
                this.getHistos().get("Sector-Layer Map").getH2F("hi_sec_lay"));
        this.normalize(this.getHistos().get("Sector-Layer Map"), lumiScale);

        for (int s = 1; s <= NSECTORS; s++) {
            this.normalizeToEventsX100(
                    this.getHistos().get("Paddle Map").getH2F("hi_paddle_s" + s));
        }
        this.normalize(this.getHistos().get("Paddle Map"), lumiScale);

        // Rates [kHz], lumi-scaled
        this.normalizeToTime(this.getHistos().get("Rate by Sector"));
        this.normalize(this.getHistos().get("Rate by Sector"), lumiScale);

        // 2D rate map [kHz], lumi-scaled
        this.normalizeToTime(this.getHistos().get("Rate Map"));
        this.normalize(this.getHistos().get("Rate Map"), lumiScale);

        // PMT current map [µA]: normalizeToTime computes sum(w)/total_ns×1e6 = µA, lumi-scaled
        this.normalizeToTime(this.getHistos().get("PMT Current Map"));
        this.normalize(this.getHistos().get("PMT Current Map"), lumiScale);

        // Position map [kHz/bin], lumi-scaled
        this.normalizeToTime(this.getHistos().get("Position Map"));
        this.normalize(this.getHistos().get("Position Map"), lumiScale);

        System.out.printf("%n===== FTOF hit-filter diagnostics =====%n");
        System.out.printf("  Total ADC rows        : %d%n", diagTotal);
        System.out.printf("  Removed order!=0      : %d  (%.1f%%)%n",
                diagOrder1, 100.0 * diagOrder1 / Math.max(1, diagTotal));
        System.out.printf("  Removed Edep<=1MeV    : %d  (%.1f%%)%n",
                diagLowEdep, 100.0 * diagLowEdep / Math.max(1, diagTotal));
        System.out.printf("  Filled into histos    : %d  (%.1f%%)%n",
                diagFilled, 100.0 * diagFilled / Math.max(1, diagTotal));
        System.out.printf("  Per-event filled avg  : %.2f%n",
                (double) diagFilled / Math.max(1, this.getNevents()));
        System.out.printf("=======================================%n%n");

        this.printRatesCSV();
    }

    public void printRatesCSV() {
        double timeWindow_ns = Constants.getTimeWindow();
        String lumi = System.getProperty("lumi", "1350");
        String filename = String.format("ftof_rates_lumi%s_excS%d.csv", lumi, EXCLUDED_SECTOR);
        System.out.println("\n>>>>> Writing FTOF rates to " + filename);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
            bw.write(String.format("# FTOF rates — lumi=%s, time_window_ns=%.0f, excluded_sector=%d%n",
                    lumi, timeWindow_ns, EXCLUDED_SECTOR));
            bw.write("sector,rate_P1a_kHz,rate_P1b_kHz,rate_P2_kHz," +
                    "occ_P1a_pct,occ_P1b_pct,occ_P2_pct\n");

            for (int sec = 1; sec <= NSECTORS; sec++) {
                double[] rates = new double[NLAYERS];
                for (int lay = 0; lay < NLAYERS; lay++) {
                    H1F h = this.getHistos().get("Rate by Sector")
                            .getH1F("hi_rate_sec_" + PANELS[lay]);
                    rates[lay] = h.getBinContent(sec - 1);
                }
                double dt = timeWindow_ns * 1e-4; // kHz × ns → %
                bw.write(String.format("%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        sec,
                        rates[0], rates[1], rates[2],
                        rates[0] * dt, rates[1] * dt, rates[2] * dt));
            }
        } catch (IOException e) {
            System.err.println("Error writing FTOF CSV: " + e.getMessage());
        }
    }

    @Override
    public void setPlottingOptions(String name) {
        GStyle.getAxisAttributesX().setTitleFontSize(26);
        GStyle.getAxisAttributesY().setTitleFontSize(26);
        GStyle.getAxisAttributesX().setLabelFontSize(26);
        GStyle.getAxisAttributesY().setLabelFontSize(26);
        GStyle.getAxisAttributesZ().setTitleFontSize(26);
        GStyle.getAxisAttributesZ().setLabelFontSize(26);

        for (EmbeddedPad pad : this.getCanvas(name).getCanvasPads())
            pad.setTitle("");

        if (name.equals("Sector-Layer Map") || name.equals("Paddle Map")) {
            this.getCanvas(name).draw(this.getHistos().get(name));
            this.setupColorAxis(name, "Occupancy [%]");
        }

        if (name.equals("Position Map")) {
            this.getCanvas(name).draw(this.getHistos().get(name));
            this.setupColorAxis(name, "Rate [kHz]");
        }

        if (name.equals("Rate Map")) {
            this.getCanvas(name).draw(this.getHistos().get(name));
            this.setupColorAxis(name, "Rate [kHz]");
        }

        if (name.equals("PMT Current Map")) {
            this.getCanvas(name).draw(this.getHistos().get(name));
            this.setupColorAxis(name, "Current [#muA]");
        }

        if (name.equals("Rate by Sector"))
            this.setLegend(name, 250, 50);
    }
}

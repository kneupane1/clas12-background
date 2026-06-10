package modules;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import analysis.Constants;
import analysis.Module;
import objects.Event;
import objects.Hit;
import org.jlab.detector.base.DetectorType;
import org.jlab.groot.base.GStyle;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.graphics.EmbeddedPad;
import org.jlab.groot.group.DataGroup;

/**
 * Background module for the CLAS12 electromagnetic calorimeter system.
 *
 * All three sub-detectors share the ECAL::adc bank, distinguished by layer:
 * layers 1-3 → PCAL (preshower, U/V/W views)
 * layers 4-6 → ECIN (inner EC, U/V/W views)
 * layers 7-9 → ECOUT (outer EC, U/V/W views)
 *
 * Strip counts per view:
 * PCAL U=68, V=62, W=62
 * ECIN U=36, V=36, W=36
 * ECOUT U=36, V=36, W=36
 *
 * Plots produced:
 * Sector-Layer Map — 2D occupancy [%]: sector (x) vs layer 1-9 (y)
 * Rate by Sector — 3 pads: kHz vs sector for PCAL / ECIN / ECOUT
 * PCAL Strip Map — 6 pads (one per sector): strip (x) vs view 1-3 (y)
 * ECIN Strip Map — same for ECIN
 * ECOUT Strip Map — same for ECOUT
 * Position Map — 6 pads: R vs Z (top) and Y vs X (bottom) per sub-detector
 * from MC::True avgX/avgY/avgZ; empty if MC::True not in file
 */
public class ECmodule extends Module {

    private static final int NSECTORS = 6;
    private static final int NLAYERS = 9; // 3 sub-dets × 3 views
    private static final int NSTRIPS = 70; // generous upper bound (PCAL U = 68)
    private static final String[] SUBDETS = { "PCAL", "ECIN", "ECOUT" };

    // Physical extent of ECAL in metres (MC::True positions are mm → divide by 1000 when filling)
    private static final double EC_Z_MIN = 5.0;
    private static final double EC_Z_MAX = 9.5;
    private static final double EC_R_MAX = 3.0;
    private static final double EC_XY_MAX = 3.0;

    // Same -excludeSector mechanism as HTCCmodule
    private static final int EXCLUDED_SECTOR = Integer.getInteger("excludeSector", 0);

    public ECmodule() {
        super(DetectorType.ECAL);
    }

    // -----------------------------------------------------------------------
    // Sub-detector helpers
    // -----------------------------------------------------------------------

    /** Returns 0/1/2 for PCAL/ECIN/ECOUT, or -1 for invalid layer. */
    private static int subDetIdx(int layer) {
        if (layer >= 1 && layer <= 3)
            return 0;
        if (layer >= 4 && layer <= 6)
            return 1;
        if (layer >= 7 && layer <= 9)
            return 2;
        return -1;
    }

    /** Local view number 1-3 (U/V/W) within the sub-detector. */
    private static int localView(int layer) {
        return ((layer - 1) % 3) + 1;
    }

    // -----------------------------------------------------------------------
    // Histogram definitions
    // -----------------------------------------------------------------------

    /**
     * 2D occupancy map: sector (x) vs layer 1-9 (y).
     * Colour = fraction of events with at least one hit in that cell [%].
     */
    public DataGroup sectorLayerMap() {
        DataGroup dg = new DataGroup(1, 1);
        H2F hi = histo2D("hi_sec_lay", "Sector",
                "Layer  (1-3=PCAL  4-6=ECIN  7-9=ECOUT)",
                NSECTORS, 0.5, NSECTORS + 0.5,
                NLAYERS, 0.5, NLAYERS + 0.5);
        dg.addDataSet(hi, 0);
        return dg;
    }

    /**
     * Hit rate [kHz] vs sector — one pad per sub-detector.
     * Sector 4 should be suppressed if wings are effective.
     */
    public DataGroup rateBySector() {
        DataGroup dg = new DataGroup(3, 1);
        int[] colors = { 4, 2, 3 };
        for (int i = 0; i < 3; i++) {
            H1F hi = histo1D("hi_rate_sec_" + SUBDETS[i], SUBDETS[i],
                    "Sector", "Rate [kHz]",
                    NSECTORS, 0.5, NSECTORS + 0.5, 0);
            hi.setLineColor(colors[i]);
            hi.setLineWidth(2);
            dg.addDataSet(hi, i);
        }
        return dg;
    }

    /**
     * Per-sector 2D strip map for one sub-detector (6 pads, one per sector).
     * X = strip / component number (proxy for spatial position along view).
     * Y = view 1/2/3 = U/V/W within the sub-detector.
     * This is the detector-coordinate equivalent of a position plot.
     */
    public DataGroup sectorStripMap(int sdIdx) {
        DataGroup dg = new DataGroup(3, 2);
        for (int s = 1; s <= NSECTORS; s++) {
            H2F hi = histo2D("hi_strip_" + SUBDETS[sdIdx] + "_s" + s,
                    "Strip", "View (1=U  2=V  3=W)",
                    NSTRIPS, 0.5, NSTRIPS + 0.5,
                    3, 0.5, 3.5);
            hi.setTitle("Sector " + s);
            dg.addDataSet(hi, s - 1);
        }
        return dg;
    }

    /**
     * 2D position maps from MC::True — one pad per sub-detector.
     * Top row: R vs Z. Bottom row: Y vs X.
     * Requires MC::True bank to be enabled for ECAL in the simulation;
     * hits without truth are silently skipped.
     */
    public DataGroup positionMap() {
        DataGroup dg = new DataGroup(3, 2);
        for (int i = 0; i < 3; i++) {
            H2F hiRZ = histo2D("hi_posRZ_" + SUBDETS[i],
                    SUBDETS[i],
                    "Z [m]", "R [m]",
                    200, EC_Z_MIN, EC_Z_MAX,
                    200, 0, EC_R_MAX);
            H2F hiXY = histo2D("hi_posXY_" + SUBDETS[i],
                    SUBDETS[i],
                    "X [m]", "Y [m]",
                    200, -EC_XY_MAX, EC_XY_MAX,
                    200, -EC_XY_MAX, EC_XY_MAX);
            dg.addDataSet(hiRZ, i);
            dg.addDataSet(hiXY, i + 3);
        }
        return dg;
    }

    public void fillPositionMap(DataGroup dg, List<Hit> hits) {
        for (Hit h : hits) {
            if (h.getTrue() == null)
                continue;
            int isd = subDetIdx(h.getLayer());
            if (isd < 0)
                continue;
            double x = h.getTrue().getPosition().x() / 1000.0;
            double y = h.getTrue().getPosition().y() / 1000.0;
            double z = h.getTrue().getPosition().z() / 1000.0;
            double r = Math.sqrt(x * x + y * y);
            dg.getH2F("hi_posRZ_" + SUBDETS[isd]).fill(z, r);
            dg.getH2F("hi_posXY_" + SUBDETS[isd]).fill(x, y);
        }
    }

    // -----------------------------------------------------------------------
    // Module interface
    // -----------------------------------------------------------------------

    @Override
    public void createHistos() {
        this.getHistos().put("Sector-Layer Map", this.sectorLayerMap());
        this.getHistos().put("Rate by Sector", this.rateBySector());
        this.getHistos().put("PCAL Strip Map", this.sectorStripMap(0));
        this.getHistos().put("ECIN Strip Map", this.sectorStripMap(1));
        this.getHistos().put("ECOUT Strip Map", this.sectorStripMap(2));
        this.getHistos().put("Position Map", this.positionMap());
    }

    @Override
    public void fillHistos(Event event) {
        List<Hit> hits = event.getHits(DetectorType.ECAL);
        if (hits == null)
            return;

        if (EXCLUDED_SECTOR > 0)
            hits = hits.stream()
                    .filter(h -> h.getSector() != EXCLUDED_SECTOR)
                    .collect(Collectors.toList());

        DataGroup dgMap = this.getHistos().get("Sector-Layer Map");
        DataGroup dgSec = this.getHistos().get("Rate by Sector");

        for (Hit h : hits) {
            int sector = h.getSector();
            int layer = h.getLayer();
            int comp = h.getComponent();
            int isd = subDetIdx(layer);
            if (isd < 0)
                continue;

            dgMap.getH2F("hi_sec_lay").fill(sector, layer);
            dgSec.getH1F("hi_rate_sec_" + SUBDETS[isd]).fill(sector);

            DataGroup dgStrip = this.getHistos().get(SUBDETS[isd] + " Strip Map");
            dgStrip.getH2F("hi_strip_" + SUBDETS[isd] + "_s" + sector)
                    .fill(comp, localView(layer));
        }

        this.fillPositionMap(this.getHistos().get("Position Map"), hits);
    }

    @Override
    public void analyzeHistos() {
        double x = Double.parseDouble(System.getProperty("lumi", "1350.0"));
        double lumiScale = x / 1350.0;

        // Sector-Layer Map → occupancy [%] per sector-layer cell
        // Normalise by nevents (so each bin = fraction of events with a hit)
        this.normalize(this.getHistos().get("Sector-Layer Map"), 0.01 * this.getNevents());
        this.normalize(this.getHistos().get("Sector-Layer Map"), lumiScale);

        // Rate by Sector → kHz, lumi-scaled
        this.normalizeToTime(this.getHistos().get("Rate by Sector"));
        this.normalize(this.getHistos().get("Rate by Sector"), lumiScale);

        // Strip maps → occupancy [%] per strip-view cell
        for (String sd : SUBDETS) {
            this.normalize(this.getHistos().get(sd + " Strip Map"), 0.01 * this.getNevents());
            this.normalize(this.getHistos().get(sd + " Strip Map"), lumiScale);
        }

        // Position Map → kHz/mm², lumi-scaled (empty if MC::True not enabled)
        this.normalizeToTime(this.getHistos().get("Position Map"));
        this.normalize(this.getHistos().get("Position Map"), lumiScale);

        this.printRatesCSV();
    }

    public void printRatesCSV() {
        H1F hiPCAL = this.getHistos().get("Rate by Sector").getH1F("hi_rate_sec_PCAL");
        H1F hiECIN = this.getHistos().get("Rate by Sector").getH1F("hi_rate_sec_ECIN");
        H1F hiECOUT = this.getHistos().get("Rate by Sector").getH1F("hi_rate_sec_ECOUT");

        double timeWindow_ns = Constants.getTimeWindow();
        String lumi = System.getProperty("lumi", "1350");
        int excSec = EXCLUDED_SECTOR;

        String filename = String.format("ecal_rates_lumi%s_excS%d.csv", lumi, excSec);
        System.out.println("\n>>>>> Writing ECAL rates to " + filename);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
            bw.write(String.format("# ECAL rates — lumi=%s, time_window_ns=%.0f, excluded_sector=%d%n",
                    lumi, timeWindow_ns, excSec));
            bw.write("# occupancy_pct = rate_kHz * time_window_ns * 1e-4%n");
            bw.write("sector,rate_PCAL_kHz,rate_ECIN_kHz,rate_ECOUT_kHz," +
                    "occ_PCAL_pct,occ_ECIN_pct,occ_ECOUT_pct\n");

            for (int sec = 1; sec <= NSECTORS; sec++) {
                // Histogram has NSECTORS bins [0.5,6.5]; sector fills at x=sec → bin sec-1
                double rPCAL = hiPCAL.getBinContent(sec - 1);
                double rECIN = hiECIN.getBinContent(sec - 1);
                double rECOUT = hiECOUT.getBinContent(sec - 1);
                double dt = timeWindow_ns * 1e-4; // factor: kHz × ns → %
                bw.write(String.format("%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        sec, rPCAL, rECIN, rECOUT,
                        rPCAL * dt, rECIN * dt, rECOUT * dt));
            }
        } catch (IOException e) {
            System.err.println("Error writing ECAL CSV: " + e.getMessage());
        }
    }

    @Override
    public void setPlottingOptions(String name) {
        GStyle.getAxisAttributesX().setTitleFontSize(18);
        GStyle.getAxisAttributesY().setTitleFontSize(18);
        GStyle.getAxisAttributesX().setLabelFontSize(18);
        GStyle.getAxisAttributesY().setLabelFontSize(18);
        GStyle.getAxisAttributesZ().setTitleFontSize(18);
        GStyle.getAxisAttributesZ().setLabelFontSize(18);

        for (EmbeddedPad pad : this.getCanvas(name).getCanvasPads())
            pad.setTitle("");

        if (name.equals("Sector-Layer Map")) {
            this.getCanvas(name).draw(this.getHistos().get(name));
            this.setupColorAxis(name, "Occupancy [%]");
            this.setLogZ(name);
        }

        if (name.contains("Strip Map")) {
            this.getCanvas(name).draw(this.getHistos().get(name));
            this.setupColorAxis(name, "Occupancy [%]");
            this.setLogZ(name);
        }

        if (name.equals("Position Map")) {
            this.getCanvas(name).draw(this.getHistos().get(name));
            this.setupColorAxis(name, "Rate [kHz]");
            this.setLogZ(name);
        }

        if (name.equals("Rate by Sector"))
            this.setLegend(name, 250, 50);
    }
}

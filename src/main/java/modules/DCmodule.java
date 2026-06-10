package modules;

import analysis.Constants;
import java.util.List;
import objects.Hit;
import analysis.Module;
import java.util.ArrayList;
import objects.Event;
import objects.True;
import org.jlab.detector.base.DetectorType;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.math.F1D;
import org.jlab.groot.data.IDataSet;
import org.jlab.groot.graphics.EmbeddedPad;
import org.jlab.groot.group.DataGroup;
import org.jlab.groot.ui.LatexText;
import org.jlab.groot.base.GStyle;
import org.jlab.groot.graphics.GraphicsAxis;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 *
 * @author devita
 */
public class DCmodule extends Module {

    private static final int NREGIONS = 3;
    private static final int NSECTORS = 6;
    private static final int NLAYERS = 36;
    private static final int NWIRES = 112;

    // private static final double[] RWINDOWS = {500, 1400, 1200};
    private static final double[] RWINDOWS = { 250, 500, 500 };
    private static final double[] DR = { 2500, 4000, 5500 };
    private static final double[] DZ = { 3500, 5000, 6500 };

    /////////////// To fix max ///////
    private static final double OCC_Z_MAX = 10; // tune once
    private static final double BG_Y_MAX = 2000; // tune once
    // private static final double BG_SEC_Y_MAX = 50000; // tune once
    private static final double POS_Z_MAX = 300; // for position plots
    private static final double Region_OCC_Max = 10;
    private static final double FLAME_VX_CUT = 0; // tune this
    private static final double FLAME_VY_CUT = 100; // tune this

    private static final double FLAME_VX_LEFT_CUT = -1200; // x left cut
    private static final double FLAME_VX_RIGHT_CUT = 300; // x right cut

    private static final double FLAME_POS_X_LEFT = -5000; // mm
    private static final double FLAME_POS_X_RIGHT = 5000; // mm
    private static final double FLAME_POS_Y_ABS = 60; // mm

    private boolean passVertexFlameCut(True t) {
        return (t.getVertex().x() > FLAME_VX_LEFT_CUT &&
                t.getVertex().x() < 0 &&
                Math.abs(t.getVertex().y()) < FLAME_VY_CUT);
    }

    private boolean passPositionFlameCut(True t) {
        return (t.getPosition().x() > FLAME_POS_X_LEFT &&
                t.getPosition().x() < FLAME_POS_X_RIGHT &&
                Math.abs(t.getPosition().y()) < FLAME_POS_Y_ABS);
    }

    private boolean passPositionFlameAntiCut(True t) {
        return !passPositionFlameCut(t);
    }

    private static final int CUT_MODE_NONE = 0;
    private static final int CUT_MODE_VERTEX = 1;
    private static final int CUT_MODE_POSITION = 2;
    private static final int CUT_MODE_POSITION_ANTI = 3;

    // private static final int CUT_MODE = CUT_MODE_POSITION;
    // private static final int CUT_MODE = CUT_MODE_POSITION_ANTI;
    private static final int CUT_MODE = CUT_MODE_NONE;

    private boolean passSelectedCut(True t) {
        if (CUT_MODE == CUT_MODE_NONE)
            return true;
        if (CUT_MODE == CUT_MODE_VERTEX)
            return passVertexFlameCut(t);
        if (CUT_MODE == CUT_MODE_POSITION)
            return passPositionFlameCut(t);
        if (CUT_MODE == CUT_MODE_POSITION_ANTI)
            return passPositionFlameAntiCut(t);
        return true;
    }

    public DCmodule() {
        super(DetectorType.DC);
    }
    //////// to write a csv file for dc regions

    public void printOccupancyCSV() {
        String lumi = System.getProperty("lumi", "1350");

        // get current working directory and extract just the last folder name
        String cwd = System.getProperty("user.dir");
        String dirName = new java.io.File(cwd).getName(); // e.g. "original_225_lumi_record_passby_1"

        // String filename = cwd + "/wire_cut_occupancy_5_4_6_extra_wire_cut" + dirName
        // + "_lumi" + lumi + ".csv";
        String filename = cwd + "/occupancy_5_4_6_extra_" + dirName + "_lumi" + lumi + ".csv";
        try {
            BufferedWriter buffer = new BufferedWriter(new FileWriter(filename));
            buffer.write("region,sector,occupancy_pct\n");

            DataGroup dg = this.getHistos().get("Region Occupancy");
            for (int ir = 0; ir < NREGIONS; ir++) {
                int region = ir + 1;
                H1F h = dg.getH1F("hi_occ_region" + region);
                for (int sector = 1; sector <= NSECTORS; sector++) {
                    double occ = h.getBinContent(sector - 1);
                    buffer.write(String.format("%d,%d,%.4f\n", region, sector, occ));
                }
            }
            buffer.close();
            System.out.println(">>>>> Occupancy CSV written to: " + filename);
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
    }

    /*
     * occupacy layer vs wire for each sector - need to normalize wrt number of
     * region (3)
     */
    public DataGroup occupancies() {
        DataGroup dg = new DataGroup(3, 2);
        for (int is = 0; is < NSECTORS; is++) {
            int sector = is + 1;
            String name = "sector" + sector;
            H2F hi_occ = histo2D("hi_occ_" + name, "Wire", "layer", NWIRES, 1, NWIRES + 1, NLAYERS, 1, NLAYERS + 1);
            dg.addDataSet(hi_occ, 0 + is);
        }
        return dg;
    }

    /*
     * occupancy vs sector for each region - need to normalize wrt number of layer
     * (12) and wires (112)
     */
    public DataGroup occupancy_region() {
        DataGroup dg = new DataGroup(1, 1);
        for (int ir = 0; ir < NREGIONS; ir++) {
            int region = ir + 1;
            String name = "region" + region;
            H1F hi_occ = histo1D("hi_occ_" + name, name, "Sector", "Occupancy[%] ", NSECTORS, 0.5, NSECTORS + 0.5, 0);
            hi_occ.setLineColor(region + 1);
            hi_occ.setLineWidth(4);

            dg.addDataSet(hi_occ, 0);

        }
        return dg;
    }

    /* histos to understand origin of BG */
    public DataGroup[] origin_bg() {
        DataGroup[] dg = new DataGroup[2];

        for (int i = 0; i < dg.length; i++) {

            dg[i] = new DataGroup(3, 3);

            for (int ir = 0; ir < NREGIONS; ir++) {
                int region = ir + 1;
                H2F hi_bg_origin_rz = histo2D("hi_bg_origin_rz_region" + region, "Vz(mm)", "r(mm) ", 200, -500., DZ[ir],
                        200, 0., DR[ir]);
                H2F hi_bg_origin_xy = histo2D("hi_bg_origin_xy_region" + region, "Vx(mm)", "Vy(mm) ", 200, -DR[ir],
                        DR[ir], 200, -DR[ir], DR[ir]);
                dg[i].addDataSet(hi_bg_origin_xy, 0 + ir);
                dg[i].addDataSet(hi_bg_origin_rz, 3 + ir);

                double min = -500;
                double max = DZ[ir];
                String name = "Vz(mm)";
                if (i == 1) {
                    min = 0;
                    max = 200;
                    name = "E(MeV)";
                }
                for (int ip = 0; ip < PNAMES.length; ip++) {
                    H1F hi_bg = histo1D("hi_bg_region" + region + "_" + PNAMES[ip], PNAMES[ip], name, "Rate [MHz] ",
                            200, -1000, max, 0);
                    this.setHistoAttr(hi_bg, ip < 5 ? ip + 1 : ip + 3);
                    dg[i].addDataSet(hi_bg, 6 + ir);
                }
            }

        }

        return dg;
    }

    public DataGroup sectorBG(int region) {
        DataGroup dg = new DataGroup(2, 3);
        for (int is = 0; is < NSECTORS; is++) {
            int sector = is + 1;
            for (int ip = 0; ip < PNAMES.length; ip++) {
                H1F hi_bg = histo1D(
                        "hi_bg_r" + region + "_s" + sector + "_" + PNAMES[ip],
                        "R" + region + "S" + sector + "-" + PNAMES[ip],
                        "Vz(mm)", "Rate [MHz]",
                        200, -1000, DZ[region - 1], 0);
                this.setHistoAttr(hi_bg, ip < 5 ? ip + 1 : ip + 3);
                dg.addDataSet(hi_bg, is);
            }
        }
        return dg;
    }
    // public DataGroup sectorBG() {
    // DataGroup dg = new DataGroup(2,3);

    // for (int is = 0; is < NSECTORS; is++) {
    // int sector = is + 1;
    // for (int ip=0; ip<PNAMES.length; ip++) {
    // H1F hi_bg = histo1D("hi_bg_r1_s" + sector + "_" + PNAMES[ip], "R1S" +sector +
    // "-" + PNAMES[ip], "Vz(mm)", "Rate [MHz] ", 200, -1000, DZ[0], 0);
    // this.setHistoAttr(hi_bg, ip<5 ? ip+1 : ip+3);
    // dg.addDataSet(hi_bg, is);
    // }
    // }
    // return dg;
    // }

    public DataGroup pos_bg() {
        DataGroup dg = new DataGroup(3, 2);

        for (int ir = 0; ir < NREGIONS; ir++) {
            int region = ir + 1;
            String name = "region" + region;
            H2F hi_posZ_posYR = histo2D("hi-posZ-posR-" + name, "posZ [mm]", "posR [mm]", 300, 0., DZ[ir], 300, 0,
                    DR[ir]);
            H2F hi_posX_posY = histo2D("hi-posX-posY-" + name, "poX [mm]", "posY [mm]", 200, -DR[ir], DR[ir], 200,
                    -DR[ir], DR[ir]);
            dg.addDataSet(hi_posZ_posYR, ir);
            dg.addDataSet(hi_posX_posY, ir + 3);
        }

        return dg;
    }

    public void fixOccupancyZAxis(DataGroup dg) {
        double globalMax = 0;

        // Find global max across all sectors
        for (int i = 0; i < 6; i++) {
            H2F h = dg.getH2F("hi_occ_sector" + (i + 1));
            if (h.getMaximum() > globalMax) {
                globalMax = h.getMaximum();
            }
        }

        // Apply same Z scale to all
        for (int i = 0; i < 6; i++) {
            H2F h = dg.getH2F("hi_occ_sector" + (i + 1));
            // h.setMaximum(globalMax);
            // h.setMinimum(0); // optional but recommended
        }
    }

    @Override
    public void createHistos() {
        this.getHistos().put("Sector Occupancy", this.occupancies());
        this.getHistos().put("Region Occupancy", this.occupancy_region());
        this.getHistos().put("Origin of Bg", this.origin_bg()[0]);
        this.getHistos().put("Origin of Bg - Energy", this.origin_bg()[1]);
        this.getHistos().put("Origin of Bg - Sector R1", this.sectorBG(1));
        this.getHistos().put("Origin of Bg - Sector R2", this.sectorBG(2));
        this.getHistos().put("Origin of Bg - Sector R3", this.sectorBG(3));
        this.getHistos().put("Position of Bg", this.pos_bg());
    }

    @Override
    public void fillHistos(Event event) {
        List<Hit> allhits = event.getHits(DetectorType.DC);
        if (allhits != null) {
            List<Hit> hits = new ArrayList<>();

            for (Hit h : allhits) {
                int region = (h.getLayer() - 1) / 12 + 1;

                // Exclude first 7 wires of Region 1, Sector 4
                boolean isR1S4HotWire = (region == 1
                        && h.getSector() == 4
                        && h.getComponent() <= 7); // change to <= 8 if needed

                boolean isR2S1Hot = (region == 2
                        && h.getSector() == 1
                        && h.getTrue().getPosition().x() > 2350);

                boolean isR2S1HotWire_upper = (region == 2
                        && h.getSector() == 1
                        && h.getLayer() >= 18
                        && h.getComponent() >= 95); // layers 18-24, last 12 wires

                boolean isR2S1HotWire_lower = (region == 2
                        && h.getSector() == 1
                        && h.getLayer() < 18
                        && h.getComponent() >= 101); // layers 13-17, adjust wire threshold

                // if (h.getTrue().getEdep() > 50E-6 && !isR1S4HotWire && !isR2S1HotWire_upper
                // && !isR2S1HotWire_lower)
                if (h.getTrue().getEdep() > 50E-6)
                    hits.add(h);
            }

            this.fillOccupancies(this.getHistos().get("Sector Occupancy"), hits);
            this.fillOccupancy_region(this.getHistos().get("Region Occupancy"), hits);
            this.fillOrigin(this.getHistos().get("Origin of Bg"), hits, false);
            this.fillOrigin(this.getHistos().get("Origin of Bg - Energy"), hits, true);
            this.fillSectorBG(this.getHistos().get("Origin of Bg - Sector R1"), hits, 1);
            this.fillSectorBG(this.getHistos().get("Origin of Bg - Sector R2"), hits, 2);
            this.fillSectorBG(this.getHistos().get("Origin of Bg - Sector R3"), hits, 3);
            this.fillPosition(this.getHistos().get("Position of Bg"), hits);
        }
    }

    public void fillOccupancies(DataGroup group, List<Hit> hits) {
        for (Hit hit : hits) {

            True t = hit.getTrue(); ///////// kr add
            if (!passSelectedCut(t))
                continue;
            // System.out.println(hit.getTrue().getEdep() + " " + hit.getTrue().getTime()+ "
            // " + hit.getTDC());
            group.getH2F("hi_occ_sector" + hit.getSector()).fill(hit.getComponent(), hit.getLayer(),
                    RWINDOWS[(hit.getLayer() - 1) / 12] / Constants.getTimeWindow());
        }
    }

    public void fillOccupancy_region(DataGroup group, List<Hit> hits) {
        for (Hit hit : hits) {
            True t = hit.getTrue(); ////// kr add
            if (!passSelectedCut(t))
                continue;
            int region = (hit.getLayer() - 1) / 12 + 1;
            group.getH1F("hi_occ_region" + region).fill(hit.getSector(),
                    RWINDOWS[region - 1] / Constants.getTimeWindow());
        }
    }

    public void fillOrigin(DataGroup group, List<Hit> hits, boolean energyWeight) {

        for (Hit hit : hits) {

            True t = hit.getTrue();
            // ===== ADD THIS CUT for just sheet of flame =====
            if (!passSelectedCut(t))
                continue;
            int region = (hit.getLayer() - 1) / 12 + 1;

            double r = Math.sqrt(t.getVertex().x() * t.getVertex().x() + t.getVertex().y() * t.getVertex().y());
            double weight = energyWeight ? t.getKinEnergy() : 1;
            // System.out.println(weight);
            group.getH2F("hi_bg_origin_rz_region" + region).fill(t.getVertex().z(), r, weight);
            if (t.getVertex().z() > 1000 * (region))
                group.getH2F("hi_bg_origin_xy_region" + region).fill(t.getVertex().x(), t.getVertex().y(), weight);

            double value = energyWeight ? t.getKinEnergy() : t.getVertex().z();
            group.getH1F("hi_bg_region" + region + "_all").fill(value);
            if (this.pidToName(Math.abs(Math.abs(t.getPid()))) != null)
                group.getH1F("hi_bg_region" + region + "_" + this.pidToName(Math.abs(t.getPid()))).fill(value);
            else
                group.getH1F("hi_bg_region" + region + "_other").fill(value);
        }

    }

    public void fillSectorBG(DataGroup group, List<Hit> hits, int targetRegion) {
        for (Hit hit : hits) {
            True t = hit.getTrue();
            if (!passSelectedCut(t))
                continue;

            int region = (hit.getLayer() - 1) / 12 + 1;
            int sector = hit.getSector();

            if (region != targetRegion)
                continue; // ← only fill matching region

            group.getH1F("hi_bg_r" + region + "_s" + sector + "_all")
                    .fill(t.getVertex().z());
            if (this.pidToName(Math.abs(t.getPid())) != null)
                group.getH1F("hi_bg_r" + region + "_s" + sector + "_"
                        + this.pidToName(Math.abs(t.getPid())))
                        .fill(t.getVertex().z());
            else
                group.getH1F("hi_bg_r" + region + "_s" + sector + "_other")
                        .fill(t.getVertex().z());
        }
    }

    public void fillPosition(DataGroup group, List<Hit> hits) {

        for (Hit hit : hits) {

            True t = hit.getTrue();
            if (!passSelectedCut(t))
                continue;
            int region = (hit.getLayer() - 1) / 12 + 1;

            double r = Math.sqrt(t.getPosition().y() * t.getPosition().y() + t.getPosition().x() * t.getPosition().x());
            // if (t.getVertex().z() > -150 && t.getVertex().z() < 100 && (t.getPid() == 11
            // || t.getPid() == -11)) {
            group.getH2F("hi-posZ-posR-region" + region).fill(t.getPosition().z(), r);
            group.getH2F("hi-posX-posY-region" + region).fill(t.getPosition().x(), t.getPosition().y());
            // }
        }

    }

    @Override
    public void analyzeHistos() {
        //////// luminosity scale
        // Parse -lumi flag from command line args
        double x = Double.parseDouble(System.getProperty("lumi", "1350.0")); // default: no scaling
        double lumiScale = x / 1350.0;

        this.normalizeToEventsX100(this.getHistos().get("Sector Occupancy"));
        this.normalize(this.getHistos().get("Sector Occupancy"), lumiScale);

        fixOccupancyZAxis(this.getHistos().get("Sector Occupancy")); // to fix the Z axis scale for all sectors to be
                                                                     // the same and comparable
        // Rescale RGA to RGH effective luminosity
        ////// this.normalize(this.getHistos().get("Sector Occupancy"), 1.0 /
        // RGA_TO_RGH_LUMI_SCALE);

        double norm = 112 * 12 / 100;
        this.normalizeToEvents(this.getHistos().get("Region Occupancy"));
        this.normalize(this.getHistos().get("Region Occupancy"), norm);
        this.normalize(this.getHistos().get("Region Occupancy"), lumiScale);

        this.fitDataGroup(this.getHistos().get("Region Occupancy"));

        this.divide(this.getHistos().get("Origin of Bg - Energy"), this.getHistos().get("Origin of Bg"));
        this.normalizeToTime(this.getHistos().get("Origin of Bg"));
        this.normalize(this.getHistos().get("Origin of Bg"), lumiScale);

        this.normalizeToTime(this.getHistos().get("Origin of Bg - Energy"));

        // ← ADD HERE — normalize all three sector BG groups
        for (int ir = 1; ir <= 3; ir++) {
            this.normalizeToTime(this.getHistos().get("Origin of Bg - Sector R" + ir));
            this.normalize(this.getHistos().get("Origin of Bg - Sector R" + ir), lumiScale);
        }

        // // FIX: Origin of Bg - Sector (same treatment as Origin of Bg)
        // this.normalizeToTime(this.getHistos().get("Origin of Bg - Sector"));
        // this.normalize(this.getHistos().get("Origin of Bg - Sector"), lumiScale);

        // FIX: Position of Bg (if you want it comparable across runs)
        this.normalizeToTime(this.getHistos().get("Position of Bg"));
        this.normalize(this.getHistos().get("Position of Bg"), lumiScale);

        /////// print the csv file
        this.printOccupancyCSV();
    }

    @Override
    public void fitDataGroup(DataGroup dg) {
        for (int ir = 0; ir < NREGIONS; ir++) {
            int region = ir + 1;
            // F1D f = fitPol0(dg.getH1F("hi_occ_region" + region));
            // dg.addDataSet(f, 0);
            H1F h = dg.getH1F("hi_occ_region" + region);

            double avg = 0.0;
            double sum = 0.0;

            for (int bin = 0; bin < h.getDataSize(0); bin++) {
                avg += h.getBinContent(bin);
                sum += h.getBinContent(bin);
            }

            avg /= h.getDataSize(0);

            // Create constant line at average value
            F1D f = new F1D("avg_region" + region, "[a]", 0.5, 6.5);
            String text = String.format("R%d Avg: %.1f%%  Sum: %.1f%%", ir + 1, avg, sum);
            // String text = String.format("R%d Avg: %.1f%%", ir + 1, avg);

            f.setParameter(0, avg);

            f.setLineColor(region + 1);
            f.setLineWidth(3);
            f.setLineStyle(2);
            dg.addDataSet(f, 0);
        }
    }

    @Override
    public void setPlottingOptions(String name) {

        for (EmbeddedPad pad : this.getCanvas(name).getCanvasPads()) {
            pad.setTitle("");
        }

        if (name.equals("Sector Occupancy")) {
            GStyle.getAxisAttributesX().setTitleFontSize(20);
            GStyle.getAxisAttributesY().setTitleFontSize(20);
            GStyle.getAxisAttributesX().setLabelFontSize(16);
            GStyle.getAxisAttributesY().setLabelFontSize(16);
            for (EmbeddedPad pad : this.getCanvas(name).getCanvasPads()) {
                pad.getAxisZ().setRange(1, OCC_Z_MAX);
            }
            this.setupColorAxis(name, "Occupancy [%]");
        }

        else if (name.equals("Region Occupancy")) {
            GStyle.getAxisAttributesX().setTitleFontSize(26);
            GStyle.getAxisAttributesY().setTitleFontSize(26);
            GStyle.getAxisAttributesX().setLabelFontSize(22);
            GStyle.getAxisAttributesY().setLabelFontSize(22);
            for (EmbeddedPad pad : this.getCanvas(name).getCanvasPads()) {
                pad.getAxisY().setRange(0, Region_OCC_Max);
            }
            DataGroup dg = this.getHistos().get(name);
            for (int ir = 0; ir < NREGIONS; ir++) {
                List<IDataSet> ds = dg.getData(0);
                for (IDataSet d : ds) {
                    if (d instanceof F1D && d.getName().contains("" + (ir + 1))) {
                        H1F h = dg.getH1F("hi_occ_region" + (ir + 1));
                        double avg = 0.0, sum = 0.0;
                        for (int bin = 0; bin < h.getDataSize(0); bin++) {
                            avg += h.getBinContent(bin);
                            sum += h.getBinContent(bin);
                        }
                        avg /= h.getDataSize(0);
                        // String text = String.format("R%d Avg: %.1f%% Sum: %.1f%%", ir + 1, avg, sum);
                        String text = String.format("R%d Avg: %.1f%%", ir + 1, avg);
                        LatexText latexText = new LatexText(text, 90, (ir + 1) * 30);
                        latexText.setColor(ir + 2);
                        latexText.setFontSize(26);
                        latexText.setFont("Arial");
                        this.getCanvas().getCanvas(name).draw(latexText);
                    }
                }
            }
        }

        // ← Sector check BEFORE the generic "Origin of Bg" check
        else if (name.contains("Origin of Bg - Sector")) {
            GStyle.getAxisAttributesX().setTitleFontSize(20);
            GStyle.getAxisAttributesY().setTitleFontSize(20);
            GStyle.getAxisAttributesX().setLabelFontSize(16);
            GStyle.getAxisAttributesY().setLabelFontSize(16);
            // for (EmbeddedPad pad : this.getCanvas(name).getCanvasPads()) {
            // pad.getAxisY().setRange(0, 500);
            // }
            this.setLegend(name, 250, 140);
        }

        else if (name.equals("Origin of Bg") || name.equals("Origin of Bg - Energy")) {
            GStyle.getAxisAttributesX().setTitleFontSize(20);
            GStyle.getAxisAttributesY().setTitleFontSize(20);
            GStyle.getAxisAttributesX().setLabelFontSize(16);
            GStyle.getAxisAttributesY().setLabelFontSize(16);
            this.setupColorAxis(name, "Rate [kHz]");
            for (EmbeddedPad pad : this.getCanvas(name).getCanvasPads()) {
                pad.getAxisZ().setRange(1, BG_Y_MAX);
                if (pad.getDatasetPlotters().size() == 0)
                    continue;
                IDataSet ds = pad.getDatasetPlotters().get(0).getDataSet();
                if (ds instanceof H1F) {
                    if (name.contains("Energy")) {
                        pad.getAxisY().setLog(true);
                        pad.getAxisY().setRange(1, 500);
                    } else {
                        pad.getAxisY().setRange(0, 500);
                    }
                }
            }
        }

        else if (name.equals("Position of Bg")) {
            GStyle.getAxisAttributesX().setTitleFontSize(15);
            GStyle.getAxisAttributesY().setTitleFontSize(15);
            GStyle.getAxisAttributesX().setLabelFontSize(10);
            GStyle.getAxisAttributesY().setLabelFontSize(10);
            for (EmbeddedPad pad : this.getCanvas(name).getCanvasPads()) {
                pad.getAxisZ().setRange(1, POS_Z_MAX);
            }
            for (int ir = 0; ir < NREGIONS; ir++) {
                EmbeddedPad padBot = this.getCanvas(name).getPad(ir + 3);
                padBot.getAxisX().setRange(-DR[ir], DR[ir]);
                padBot.getAxisY().setRange(-DR[ir], DR[ir]);
                padBot.getAxisX().setAxisDivisions(5);
                padBot.getAxisY().setAxisDivisions(5);
            }
        }

        // if (!name.contains("Occupancy"))
        // this.setLogZ(name);
    }

    @Override
    public void normalizeToTime(DataGroup dg) {
        double factor = this.getNevents() * (Constants.getTimeWindow() * 1E-9) / 1E-6; // Mz

        int nrow = dg.getRows();
        int ncol = dg.getColumns();
        for (int i = 0; i < nrow * ncol; i++) {
            for (IDataSet ds : dg.getData(i)) {
                if (ds instanceof H1F) {
                    H1F h = (H1F) ds;
                    h.divide(factor);
                }
            }
        }

    }

}

package analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import objects.Event;
import org.jlab.detector.base.DetectorType;
import org.jlab.groot.base.PadMargins;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.data.IDataSet;
import org.jlab.groot.data.TDirectory;
import org.jlab.groot.fitter.DataFitter;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.graphics.EmbeddedCanvasTabbed;
import org.jlab.groot.graphics.EmbeddedPad;
import org.jlab.groot.graphics.GraphicsAxis;
import org.jlab.groot.graphics.IDataSetPlotter;
import org.jlab.groot.group.DataGroup;
import org.jlab.groot.math.F1D;
import org.jlab.groot.ui.LatexText;

/**
 *
 * @author devita
 */
public class Module {

    private final DetectorType moduleType;
    private Map<String, DataGroup> moduleGroup = new LinkedHashMap<>();
    private EmbeddedCanvasTabbed moduleCanvas = null;
    private List<String> canvasNames = new ArrayList<>();
    private int nevents;

    public static final String[] PNAMES = { "all", "electron", "gamma", "neutron", "proton", "pion", "other" };

    public Module(DetectorType type) {
        this.moduleType = type;
        this.init();
    }

    public void analyzeHistos() {
        // analyze the histograms at the end of the file processing
    }

    public void createHistos() {
        // create histograms
    }

    public void testHistos() {
        // run tests on the filled histograms
    }

    public void fillHistos(Event event) {
        // fill the histograms
    }

    public final String getName() {
        return moduleType.getName();
    }

    public final int getNevents() {
        return nevents;
    }

    public final double getTotalTime() {
        return nevents * Constants.TWINDOW / 1e6; // in ms
    }

    public EmbeddedCanvasTabbed getCanvas() {
        return moduleCanvas;
    }

    public EmbeddedCanvas getCanvas(String name) {
        return moduleCanvas.getCanvas(name);
    }

    public List<String> getCanvasNames() {
        return canvasNames;
    }

    public Map<String, DataGroup> getHistos() {
        return moduleGroup;
    }

    public H1F histo1D(String name, String name1, String xTitle, String yTitle, int nbins, double min, double max,
            int color) {
        H1F histo = new H1F(name, name1, nbins, min, max);
        // histo.setTitle("");
        histo.setTitleX(xTitle);
        histo.setTitleY(yTitle);
        histo.setFillColor(color);
        return histo;
    }

    public H2F histo2D(String name, String xTitle, String yTitle, int xBins, double xMin, double xMax, int yBins,
            double yMin, double yMax) {
        H2F histo = new H2F(name, name, xBins, xMin, xMax, yBins, yMin, yMax);
        histo.setTitle("");
        histo.setTitleX(xTitle);
        histo.setTitleY(yTitle);
        return histo;
    }

    public H2F histo2D(String name, String title, String xTitle, String yTitle, int xBins, double xMin, double xMax,
            int yBins, double yMin, double yMax) {
        H2F histo = new H2F(name, name, xBins, xMin, xMax, yBins, yMin, yMax);
        histo.setTitle(title);
        histo.setTitleX(xTitle);
        histo.setTitleY(yTitle);
        return histo;
    }

    public final void init() {
        this.nevents = 0;
        createHistos();
    }

    public final void processEvent(Event event) {
        // process event
        this.nevents++;
        this.fillHistos(event);
    }

    public final String pidToName(int pid) {
        switch (pid) {
            case 11:
                return "electron";
            case 22:
                return "gamma";
            case 2112:
                return "neutron";
            case 2212:
                return "proton";
            case 211:
                return "pion";
            default:
                return null;
        }
    }

    public final EmbeddedCanvasTabbed plotHistos() {
        this.drawHistos();
        return this.moduleCanvas;
    }

    public void setHistoAttr(H1F a, int col) {
        a.setLineColor(col);
        a.setLineWidth(2);
    }

    public void drawHistos() {
        for (String key : moduleGroup.keySet()) {
            this.addCanvas(key);
            this.moduleCanvas.getCanvas(key).draw(moduleGroup.get(key));
            this.setPlottingOptions(key);
            this.setScaleLabels(key);
            this.moduleCanvas.getCanvas(key).setGridX(false);
            this.moduleCanvas.getCanvas(key).setGridY(false);
            for (EmbeddedPad pad : this.moduleCanvas.getCanvas(key).getCanvasPads()) {
                pad.setTitleFontSize(36);
                pad.setTitleFont("Arial");
            }
        }
    }

    public final void addCanvas(String name) {
        if (this.moduleCanvas == null)
            this.moduleCanvas = new EmbeddedCanvasTabbed(name);
        else
            this.moduleCanvas.addCanvas(name);
        this.canvasNames.add(name);
    }

    public final void addCanvas(String... names) {
        for (String name : names) {
            this.addCanvas(name);
        }
    }

    public final void setHistos(Map<String, DataGroup> group) {
        this.moduleGroup = group;
    }

    public void setPlottingOptions(String name) {

    }

    /**
     * Ensures every pad has at least a single-space title so GROOT allocates
     * a top margin large enough for its automatic ×10^N axis multiplier labels
     * to render inside the canvas area and survive copy-paste.
     * Pads that already have a visible title are left unchanged.
     */
    protected void setScaleLabels(String canvasName) {
        for (EmbeddedPad pad : this.getCanvas(canvasName).getCanvasPads()) {
            if (pad.getTitle() == null || pad.getTitle().trim().isEmpty())
                pad.setTitle(" ");
        }
    }

    public void setLogZ(String name) {
        for (EmbeddedPad p : this.getCanvas().getCanvas(name).getCanvasPads()) {
            p.getAxisZ().setLog(true);
        }
    }

    public void setH1LineWidth(String name) {
        for (EmbeddedPad p : this.getCanvas().getCanvas(name).getCanvasPads()) {
            for (IDataSetPlotter dsp : p.getDatasetPlotters()) {
                IDataSet ds = dsp.getDataSet();
                if (ds instanceof H1F) {
                    H1F h1 = (H1F) ds;
                    h1.setLineWidth(2);
                }
            }
        }
    }

    public void printHistos(String figures) {
        File theDir = new File(figures);
        // if the directory does not exist, create it
        if (!theDir.exists()) {
            boolean result = false;
            try {
                theDir.mkdir();
                result = true;
            } catch (SecurityException se) {
                // handle it
            }
            if (result) {
                System.out.println(">>>>> Created directory " + figures);
            }
        }
        for (String cname : canvasNames) {
            this.moduleCanvas.getCanvas(cname).save(figures + "/" + this.getName() + "_" + cname + ".png");
        }
    }

    public final void readDataGroup(TDirectory dir) {
        for (String key : moduleGroup.keySet()) {
            String folder = this.getName() + "/" + key + "/";
            System.out.println("Reading from: " + folder);
            DataGroup group = this.moduleGroup.get(key);
            int nrows = group.getRows();
            int ncols = group.getColumns();
            int nds = nrows * ncols;
            DataGroup newGroup = new DataGroup(ncols, nrows);
            for (int i = 0; i < nds; i++) {
                List<IDataSet> dsList = group.getData(i);
                for (IDataSet ds : dsList) {
                    System.out.println("\t --> " + ds.getName());
                    if (dir.getObject(folder, ds.getName()) != null)
                        newGroup.addDataSet(dir.getObject(folder, ds.getName()), i);
                    else
                        newGroup.addDataSet(ds, i);
                }
            }
            this.moduleGroup.replace(key, newGroup);
        }
    }

    public final void writeDataGroup(TDirectory dir) {
        String folder = "/" + this.getName();
        System.out.println(this.getName());
        dir.mkdir(folder);
        dir.cd(folder);
        for (String key : moduleGroup.keySet()) {
            String subfolder = key + "/";
            dir.mkdir(subfolder);
            dir.cd(subfolder);
            DataGroup group = this.moduleGroup.get(key);
            int nrows = group.getRows();
            int ncols = group.getColumns();
            int nds = nrows * ncols;
            for (int i = 0; i < nds; i++) {
                List<IDataSet> dsList = group.getData(i);
                for (IDataSet ds : dsList) {
                    // System.out.println("\t --> " + ds.getName());
                    dir.addDataSet(ds);
                }
            }
            dir.cd(folder);
        }
    }

    public void fitDataGroup(DataGroup dg) {
        int nx = dg.getColumns();
        int ny = dg.getRows();
        F1D a = null;
        for (int i = 0; i < nx * ny; i++) {
            List<IDataSet> ds = dg.getData(i);
            for (IDataSet d : ds) {
                if (d instanceof H1F)
                    this.fitGauss((H1F) d);
            }
        }
    }

    public F1D fitPol0(H1F histo) {
        String hname = histo.getName();
        int lastbin = histo.getAxis().getNBins();
        double min = histo.getAxis().getBinCenter(0);
        double max = histo.getAxis().getBinCenter(lastbin - 1);
        double amp = histo.getBinContent(lastbin - 1);
        F1D f1 = new F1D("f_" + hname, "[p0]", min - 0.5, max + 0.5);
        int col = histo.getLineColor();
        f1.setParameter(0, amp);
        f1.setLineColor(col);
        f1.setLineWidth(2);
        // f1.setOptStat(111110);
        DataFitter.fit(f1, histo, "Q");
        return f1;

    }

    public void fitGauss(H1F histo) {
        double mean = histo.getMean();
        int maxBin = histo.getMaximumBin();
        double amp = histo.getBinContent(maxBin);
        double sigma = histo.getRMS();
        // System.out.println(tmp_Amp);
        F1D f1 = new F1D("f1", "[amp]*gaus(x,[mean],[sigma])", 0, 50.0);
        f1.setParameter(0, amp);
        f1.setParameter(1, mean);
        f1.setParameter(2, sigma / 2);
        f1.setRange(mean - 2.0 * sigma, mean + 2.0 * sigma);
        f1.setLineColor(1);
        f1.setLineWidth(2);
        f1.setOptStat(111110);
        DataFitter.fit(f1, histo, "Q");
    }

    public final void normalize(IDataSet ds, double factor) {
        if (ds instanceof H1F) {
            H1F h = (H1F) ds;
            h.divide(factor);
        } else if (ds instanceof H2F) {
            H2F h = (H2F) ds;
            h.normalize(factor);
        }
    }

    public final void normalize(DataGroup dg, double factor) {
        int nrow = dg.getRows();
        int ncol = dg.getColumns();
        for (int i = 0; i < nrow * ncol; i++) {
            for (IDataSet ds : dg.getData(i)) {
                this.normalize(ds, factor);
            }
        }
    }

    public final void normalizeToEvents(IDataSet ds) {
        this.normalize(ds, nevents);
    }

    public final void normalizeToEvents(DataGroup dg) {
        this.normalize(dg, nevents);
    }

    public final void normalizeToEventsX100(IDataSet ds) {
        this.normalize(ds, 0.01 * nevents);
    }

    public final void normalizeToEventsX100(DataGroup dg) {
        this.normalize(dg, 0.01 * nevents);
    }

    public final void normalizeToTime(IDataSet ds, double units) {

        this.normalize(ds, Constants.getTimeWindow() * 1E-9 * nevents * units); // units=1 -> Hz, =1000 -> kHz, ...
    }

    public final void normalizeToTime(IDataSet ds) {

        this.normalizeToTime(ds, 1000); // kHz
    }

    public void normalizeToTime(DataGroup dg) {

        this.normalize(dg, Constants.getTimeWindow() * 1E-9 * nevents * 1000); // kHz
    }

    public final void toDose(IDataSet ds) {
        double factor = 1.6E-13 // MeV/kg to J/kg
                / (Constants.getTimeWindow() * 1E-9 * nevents) // to J/kg/s
                * 3600 * 100; // to 0.01 J/kg/h or rad/h
        this.normalize(ds, Math.pow(factor, -1));
    }

    public void divide(DataGroup dg1, DataGroup dg2) {

        int nrow = dg1.getRows();
        int ncol = dg1.getColumns();

        for (int i = 0; i < nrow * ncol; i++) {
            int idx = 0;
            for (IDataSet ds1 : dg1.getData(i)) {
                if (ds1 instanceof H2F) {
                    H2F h1 = (H2F) ds1;
                    IDataSet ds2 = dg2.getData(i).get(idx);
                    if (ds2 instanceof H2F) {
                        H2F h2 = (H2F) ds2;
                        h1.divide(h2);
                    }
                }
                idx = idx + 1;
            }
        }
    }

    public void printHistogram(H2F h2) {
        try {
            BufferedWriter buffer = new BufferedWriter(new FileWriter(h2.getName() + "_histo.txt"));
            buffer.write("xbin\tybin\tx\ty\tcounts");
            for (int ix = 0; ix < h2.getDataSize(0); ix++) {
                for (int iy = 0; iy < h2.getDataSize(1); iy++) {
                    buffer.write(String.format("%d\t%d\t%.3f\t%.3f\t%.3f\n", ix, iy, h2.getDataX(ix), h2.getDataY(iy),
                            h2.getData(ix, iy)));
                }
            }
            buffer.close();

        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
    }

    public final String extractNumber(String str) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(str);

        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    public void setTextCanvas(String name) {
        DataGroup dg = this.getHistos().get(name);
        int nx = dg.getColumns();
        int ny = dg.getRows();
        LatexText LT = null;
        for (int i = 0; i < nx * ny; i++) {
            List<IDataSet> ds = dg.getData(i);
            for (IDataSet d : ds) {
                if (d instanceof H1F) {
                    String nm = ((H1F) d).getName();
                    int col = ((H1F) d).getLineColor();
                    LT = setLatex(nm, col);
                    this.getCanvas().getCanvas(name).draw(LT);
                }
            }

        }
    }

    public LatexText setLatex(String text, int color) {
        LatexText LT = new LatexText(text, 0, 0);
        LT.setColor(color);
        LT.setFontSize(30);
        LT.setFont("Arial");
        double pos = (color - 1) * 30;
        LT.setLocation(50., pos);
        return LT;
    }

    public void setLegend(String text, int x, int y) {
        DataGroup dg = this.getHistos().get(text);
        int nx = dg.getColumns();
        int ny = dg.getRows();
        for (int i = 0; i < nx * ny; i++) {
            EmbeddedPad pad = this.getCanvas(text).getPad(i);
            if (pad.getDatasetPlotters().get(0).getDataSet() instanceof H1F) {
                if (((H1F) pad.getDatasetPlotters().get(0).getDataSet()).getTitle().trim().isEmpty())
                    continue;
                pad.setLegend(true);
                pad.setLegendPosition(x, y);
            }
        }
    }

    /** Per-pad overload of setupColorAxis for canvases with mixed 1D/2D pads. */
    protected void setupColorAxis(EmbeddedPad pad, String zTitle) {
        GraphicsAxis za = pad.getAxisZ();
        za.getAttributes().setTitleFontName("Arial");
        za.getAttributes().setTitleFontSize(26);
        za.getAttributes().setLabelFontSize(26);
        za.getAttributes().setLabelFontName("Arial");
        za.setTitle(zTitle);
        // Widen the right margin so the color bar is wider and labels have room
        PadMargins pm = new PadMargins();
        pm.setLeftMargin(80).setRightMargin(160).setTopMargin(40).setBottomMargin(70);
        pm.setFixed(true);
        pad.setMargins(pm);
    }

    /**
     * Configures the color-bar (z) axis on every pad of a canvas:
     *  - sets the title font name + size (avoids the invisible-title bug where
     *    the pad-local titleFontSize defaults to 0 and titleFontName to null)
     *  - NOTE: the "xN" exponent is suppressed by patching processAxisExponent()
     *    in the GROOT jar with patch_groot.py (see repo root)
     */
    protected void setupColorAxis(String canvasName, String zTitle) {
        for (EmbeddedPad pad : this.getCanvas(canvasName).getCanvasPads())
            setupColorAxis(pad, zTitle);
    }


}

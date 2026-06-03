package analysis;

import objects.Event;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import modules.CNDmodule;
import modules.DCmodule;
import modules.FTCALmodule;
import modules.HTCCmodule;
import org.jlab.detector.base.DetectorType;
import org.jlab.groot.base.GStyle;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.IDataSet;

import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;


import org.jlab.groot.data.TDirectory;
import org.jlab.groot.graphics.EmbeddedCanvasTabbed;
import org.jlab.groot.graphics.EmbeddedPad;
import org.jlab.groot.graphics.IDataSetPlotter;
import org.jlab.jnp.utils.benchmark.ProgressPrintout;
import org.jlab.utils.options.OptionParser;

/**
 *
 * @author devita
 */

public class Background {
    
    private final boolean debug = false;
    private boolean fastmode = false;
      
    List<Module>     modules = new ArrayList<>();
    List<DetectorType> types = new ArrayList<>();

    private static String OPTSTAT = "";
    
    public Background(String active, double window, String opts) {
        this.init(active, window, opts);
    }
    
    

    private void init(String active, double window, String opts) {
        OPTSTAT = opts;
        GStyle.getH1FAttributes().setOptStat(opts);
        GStyle.getAxisAttributesX().setTitleFontSize(26);
        GStyle.getAxisAttributesX().setLabelFontSize(20);
        GStyle.getAxisAttributesY().setTitleFontSize(26);
        GStyle.getAxisAttributesY().setLabelFontSize(20);
        GStyle.getAxisAttributesZ().setLabelFontSize(18);
        GStyle.getAxisAttributesZ().setTitleFontSize(20);
        GStyle.getAxisAttributesX().setLabelFontName("Arial");
        GStyle.getAxisAttributesY().setLabelFontName("Arial");
        GStyle.getAxisAttributesZ().setLabelFontName("Arial");
        GStyle.getAxisAttributesX().setTitleFontName("Arial");
        GStyle.getAxisAttributesY().setTitleFontName("Arial");
        GStyle.getAxisAttributesZ().setTitleFontName("Arial");
        GStyle.setGraphicsFrameLineWidth(2);
        GStyle.getH1FAttributes().setLineWidth(1);
        GStyle.setPalette("kRainBow");

        Constants.setTimeWindow(window);  
        this.addModule(active, new DCmodule());
        this.addModule(active, new HTCCmodule());
        this.addModule(active, new FTCALmodule());
        this.addModule(active, new CNDmodule());
    }

    private void addModule(String active, Module module) {
        boolean flag = true;
        if(active!=null && !active.isEmpty()) {
            flag = false;
            String[] mods = active.split(":");
            for(String m : mods) {
                if(m.trim().equalsIgnoreCase(module.getName())) {
                    flag = true;
                    break;
                }
            }
        }
        if(flag) {
            System.out.println("Adding module " + module.getName());
            this.modules.add(module);
            this.types.add(DetectorType.getType(module.getName()));
        }
    }
    
    private void processEvent(DataEvent de) {
        Event event = new Event(de, types);
        for(Module m : modules) {
            m.processEvent(event);
        }
    }

    private void analyzeHistos() {
        for(Module m : modules) m.analyzeHistos();
    }

    public JTabbedPane plotHistos() {
        JTabbedPane panel = new JTabbedPane();
        for(Module m : modules) {
            EmbeddedCanvasTabbed canvas = m.plotHistos();
            for(String name : m.getCanvasNames()) {
                for(EmbeddedPad p : canvas.getCanvas(name).getCanvasPads()) {
                    for(IDataSetPlotter dsp: p.getDatasetPlotters()) {
                        IDataSet ds = dsp.getDataSet();
                        if(ds instanceof H1F) {
                            H1F h1 = (H1F) ds;
                            h1.setOptStat(OPTSTAT);
                        }
                    }
                }            
            }
            panel.add(m.getName(), canvas);
        }
        return panel;
    }
    
    public void readHistos(String fileName) {
        System.out.println("Opening file: " + fileName);
        TDirectory dir = new TDirectory();
        dir.readFile(fileName);
        System.out.println(dir.getDirectoryList());
        dir.cd();
        dir.pwd();
        for(Module m : modules) {
            m.readDataGroup(dir);
        }
    }

    public void saveHistos(String fileName) {
        System.out.println("\n>>>>> Saving histograms to file " + fileName);
        TDirectory dir = new TDirectory();
        for(Module m : modules) {
            m.writeDataGroup(dir);
        }
        dir.writeFile(fileName);
    }

    private void printHistos() {
        System.out.println("\n>>>>> Printing canvases to directory plots");
        for(Module m : modules) {
            m.printHistos("plots");
        }
    }
    
    private void testHistos() {
        for(Module m : modules) {
            m.testHistos();
        }
    }
    
    public static void main(String[] args) {
        
        OptionParser parser = new OptionParser("background [options] file1 file2 ... fileN");
        parser.setRequiresInputList(false);
        // valid options for event-base analysis
        parser.addOption("-o"          ,"",     "histogram file name prefix");
        parser.addOption("-n"          ,"-1",   "maximum number of events to process");
        // histogram based analysis
        parser.addOption("-histo"      ,"0",       "read histogram file (0/1)");
        parser.addOption("-plot"       ,"1",       "display histograms (0/1)");
        parser.addOption("-print"      ,"0",       "print histograms (0/1)");
        parser.addOption("-stats"      ,"",        "histogram stat option (e.g. \"10\" will display entries)");
        parser.addOption("-time"       ,"250",     "simulated time window per event in ns");
        parser.addOption("-modules"    ,"",        "colon-separated list of modules to be activated");
        parser.addOption("-lumi"       ,"450",     "LUMI_EVENT used in simulation (default 450 for RGH)");

        parser.parse(args);
        
        String namePrefix  = parser.getOption("-o").stringValue();        
        String histoName   = "histo.hipo";
        if(!namePrefix.isEmpty()) {
            histoName  = namePrefix + "_" + histoName; 
        }
        int     maxEvents     = parser.getOption("-n").intValue();        
        boolean readHistos    = (parser.getOption("-histo").intValue()!=0);            
        boolean openWindow    = (parser.getOption("-plot").intValue()!=0);
        boolean printHistos   = (parser.getOption("-print").intValue()!=0);
        String  optStats      = parser.getOption("-stats").stringValue(); 
        String  modules       = parser.getOption("-modules").stringValue();
        double  timeWindow    = parser.getOption("-time").doubleValue();
        
        if(!openWindow) System.setProperty("java.awt.headless", "true");

        Background bgMon = new Background(modules, timeWindow, optStats);
        
        List<String> inputList = parser.getInputList();
        if(inputList.isEmpty()==true){
            parser.printUsage();
            System.out.println("\n >>>> error: no input file is specified....\n");
            System.exit(0);
        }

        if(readHistos) {
            bgMon.readHistos(inputList.get(0));
            bgMon.analyzeHistos();
            bgMon.testHistos();
        }
        else{

            ProgressPrintout progress = new ProgressPrintout();

            int counter = -1;
            for(String inputFile : inputList){
                HipoDataSource reader = new HipoDataSource();
                reader.open(inputFile);

                
                while (reader.hasEvent()) {

                    counter++;

                    DataEvent event = reader.getNextEvent();
                    bgMon.processEvent(event);
                    
                    progress.updateStatus();
                    if(maxEvents>0){
                        if(counter>=maxEvents) break;
                    }
                }
                progress.showStatus();
                reader.close();
            }    
            bgMon.analyzeHistos();
            bgMon.testHistos();
            bgMon.saveHistos(histoName);
        }

        if(openWindow) {
            JFrame frame = new JFrame("Background");
            frame.setSize(1400, 900);
            frame.add(bgMon.plotHistos());
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            if(printHistos) bgMon.printHistos();
        }
    }

}
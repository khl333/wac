import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.imageio.ImageIO;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WacAnalyzer extends JFrame {

    private static final Color BG_BASE = new Color(10, 11, 16);
    private static final Color BG_SURFACE = new Color(18, 20, 28);
    private static final Color ACCENT_BLUE = new Color(64, 156, 255);
    private static final Color TEXT_MUTED = new Color(110, 120, 145);
    private static final Color BORDER_SUBTLE = new Color(35, 40, 58);

    private JLabel lblStatus, lblDetails;
    private SpectrogramPanel panelA, panelB, panelDiff;

    private AudioData dataA, dataB, dataC;
    private String nameA, nameB, nameC;
    private boolean isShowdown = false;
    private JRadioButton rbViewC1, rbViewC2;

    static class AudioData {
        short[] pcm;
        int sampleRate;
        int channels;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
            }
            new WacAnalyzer().setVisible(true);
        });
    }

    public WacAnalyzer() {
        setTitle("WAC Acoustic Spectral Differential Analyzer");
        setSize(1200, 950);
        setMinimumSize(new Dimension(900, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setBackground(BG_BASE);
        try {
            setIconImage(generateLogo(64));
        } catch (Exception e) {
        }

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_BASE);

        root.add(buildTitleBar(), BorderLayout.NORTH);

        JPanel spectrogramPanels = new JPanel(new GridLayout(3, 1, 0, 5));
        spectrogramPanels.setBackground(BG_BASE);
        spectrogramPanels.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        panelA = new SpectrogramPanel("SLOT A: Drop first audio file here");
        panelB = new SpectrogramPanel("SLOT B: Drop second audio file here to compare");
        panelDiff = new SpectrogramPanel("SPECTRAL DIFFERENCE MAP (A vs B)");

        spectrogramPanels.add(panelA);
        spectrogramPanels.add(panelB);
        spectrogramPanels.add(panelDiff);

        root.add(spectrogramPanels, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

        setContentPane(root);
        setupDragAndDrop();
    }

    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG_SURFACE);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SUBTLE));
        bar.setPreferredSize(new Dimension(0, 56));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 12));
        left.setOpaque(false);
        JLabel logo = new JLabel("WAC ANALYZER");
        try {
            logo.setIcon(new ImageIcon(generateLogo(24)));
            logo.setIconTextGap(10);
        } catch (Exception e) {
        }
        logo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        logo.setForeground(ACCENT_BLUE);
        left.add(logo);

        lblDetails = new JLabel(
                "Drag & Drop up to two audio files (.wac, .mp3, .flac) to compute spectral differences.");
        lblDetails.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblDetails.setForeground(TEXT_MUTED);
        left.add(lblDetails);

        bar.add(left, BorderLayout.WEST);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 15));
        modePanel.setOpaque(false);
        rbViewC1 = new JRadioButton("View C1");
        rbViewC2 = new JRadioButton("View C2");
        rbViewC1.setForeground(Color.WHITE);
        rbViewC2.setForeground(Color.WHITE);
        rbViewC1.setOpaque(false);
        rbViewC2.setOpaque(false);
        rbViewC1.setFocusPainted(false);
        rbViewC2.setFocusPainted(false);
        rbViewC1.setVisible(false);
        rbViewC2.setVisible(false);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbViewC1);
        bg.add(rbViewC2);

        rbViewC1.addActionListener(e -> updateShowdownView(1));
        rbViewC2.addActionListener(e -> updateShowdownView(2));

        modePanel.add(rbViewC1);
        modePanel.add(rbViewC2);
        bar.add(modePanel, BorderLayout.EAST);

        return bar;
    }

    private JPanel buildStatusBar() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(BG_SURFACE);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_SUBTLE));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        leftPanel.setOpaque(false);
        lblStatus = new JLabel("Ready. Drop File A to begin.");
        lblStatus.setForeground(Color.WHITE);
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        leftPanel.add(lblStatus);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 6));
        rightPanel.setOpaque(false);
        JButton btnExport = new JButton("Export Analysis Report & Images");
        btnExport.setBackground(ACCENT_BLUE);
        btnExport.setForeground(Color.WHITE);
        btnExport.setFocusPainted(false);
        btnExport.addActionListener(e -> exportAnalysis());
        rightPanel.add(btnExport);

        bottom.add(leftPanel, BorderLayout.WEST);
        bottom.add(rightPanel, BorderLayout.EAST);
        return bottom;
    }

    private void exportAnalysis() {
        if (dataA == null) {
            JOptionPane.showMessageDialog(this, "No data to export. Please drop and analyze a file first.");
            return;
        }

        lblStatus.setText("Generating full Spectrogram export suite... Please wait.");
        new Thread(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                File outDir = new File("AnalysisReport_" + timestamp);
                if (!outDir.exists())
                    outDir.mkdir();

                if (isShowdown && dataC != null) {
                    SpectrogramPanel pSrc = new SpectrogramPanel("");
                    pSrc.setSize(panelA.getWidth(), panelA.getHeight());
                    pSrc.analyzeAndRender(dataA, "SOURCE: " + nameA, null, null, false);
                    pSrc.saveImage(new File(outDir, "1_SOURCE_" + nameA + ".png"));

                    SpectrogramPanel pC1 = new SpectrogramPanel("");
                    pC1.setSize(panelA.getWidth(), panelA.getHeight());
                    pC1.analyzeAndRender(dataB, "CANDIDATE 1: " + nameB, null, null, false);
                    pC1.saveImage(new File(outDir, "2_SPECTROGRAM_" + nameB + ".png"));

                    SpectrogramPanel pD1 = new SpectrogramPanel("");
                    pD1.setSize(panelA.getWidth(), panelA.getHeight());
                    pD1.analyzeAndRender(dataA, nameA, dataB, nameB, true);
                    pD1.saveImage(new File(outDir, "3_DIFFMAP_" + nameB + ".png"));
                    double score1 = pD1.similarityScore;

                    SpectrogramPanel pC2 = new SpectrogramPanel("");
                    pC2.setSize(panelA.getWidth(), panelA.getHeight());
                    pC2.analyzeAndRender(dataC, "CANDIDATE 2: " + nameC, null, null, false);
                    pC2.saveImage(new File(outDir, "4_SPECTROGRAM_" + nameC + ".png"));

                    SpectrogramPanel pD2 = new SpectrogramPanel("");
                    pD2.setSize(panelA.getWidth(), panelA.getHeight());
                    pD2.analyzeAndRender(dataA, nameA, dataC, nameC, true);
                    pD2.saveImage(new File(outDir, "5_DIFFMAP_" + nameC + ".png"));
                    double score2 = pD2.similarityScore;

                    File report = new File(outDir, "Codec_Showdown_Report.txt");
                    try (PrintWriter pw = new PrintWriter(new FileWriter(report))) {
                        pw.println("====== WAC 3-WAY CODEC SHOWDOWN REPORT ======");
                        pw.println("Created on: " + new Date());
                        pw.println();
                        pw.println("--- SOURCE REFERENCE ---");
                        pw.println("File: " + nameA);
                        pw.println("Sample Rate: " + dataA.sampleRate + " Hz");
                        pw.println("Channels: " + (dataA.channels == 2 ? "Stereo" : "Mono"));
                        pw.println("Length: " + ((dataA.pcm.length / dataA.channels) / dataA.sampleRate) + " seconds");
                        pw.println("Max Amplitude: " + calculateMaxAmplitude(dataA) + " %");

                        pw.println();
                        pw.println("--- CANDIDATE 1 ---");
                        pw.println("File: " + nameB);
                        pw.println("Detailed Similarity: " + String.format("%.2f%%", score1));
                        pw.println("Sample Rate: " + dataB.sampleRate + " Hz");
                        pw.println("Channels: " + (dataB.channels == 2 ? "Stereo" : "Mono"));
                        pw.println("Length: " + ((dataB.pcm.length / dataB.channels) / dataB.sampleRate) + " seconds");
                        pw.println("Max Amplitude: " + calculateMaxAmplitude(dataB) + " %");

                        pw.println();
                        pw.println("--- CANDIDATE 2 ---");
                        pw.println("File: " + nameC);
                        pw.println("Detailed Similarity: " + String.format("%.2f%%", score2));
                        pw.println("Sample Rate: " + dataC.sampleRate + " Hz");
                        pw.println("Channels: " + (dataC.channels == 2 ? "Stereo" : "Mono"));
                        pw.println("Length: " + ((dataC.pcm.length / dataC.channels) / dataC.sampleRate) + " seconds");
                        pw.println("Max Amplitude: " + calculateMaxAmplitude(dataC) + " %");

                        pw.println();
                        pw.println("--- DIFFERENCE FINDINGS ---");
                        pw.println(
                                "The difference maps structurally compare the separated candidates to the Source Reference FLAC.");
                        pw.println("WINNING CODEC CLOSEST TO SOURCE: " + (score1 > score2 ? nameB : nameC));
                    }
                } else {
                    panelA.saveImage(new File(outDir, "SlotA_" + nameA + ".png"));
                    if (dataB != null) {
                        panelB.saveImage(new File(outDir, "SlotB_" + nameB + ".png"));
                        panelDiff.saveImage(new File(outDir, "Spectral_Difference_Map.png"));
                    }

                    File report = new File(outDir, "Acoustic_Analysis_Report.txt");
                    try (PrintWriter pw = new PrintWriter(new FileWriter(report))) {
                        pw.println("====== WAC ACOUSTIC ANALYSIS REPORT ======");
                        pw.println("Generated: " + new Date());
                        pw.println();
                        pw.println("--- SLOT A ---");
                        pw.println("File: " + nameA);
                        pw.println("Sample Rate: " + dataA.sampleRate + " Hz");
                        pw.println("Channels: " + (dataA.channels == 2 ? "Stereo" : "Mono"));
                        pw.println("Length: " + ((dataA.pcm.length / dataA.channels) / dataA.sampleRate) + " seconds");
                        pw.println("Max Amplitude: " + calculateMaxAmplitude(dataA) + " %");

                        if (dataB != null) {
                            pw.println();
                            pw.println("--- SLOT B ---");
                            pw.println("File: " + nameB);
                            pw.println("Sample Rate: " + dataB.sampleRate + " Hz");
                            pw.println("Channels: " + (dataB.channels == 2 ? "Stereo" : "Mono"));
                            pw.println(
                                    "Length: " + ((dataB.pcm.length / dataB.channels) / dataB.sampleRate) + " seconds");
                            pw.println("Max Amplitude: " + calculateMaxAmplitude(dataB) + " %");

                            pw.println();
                            pw.println("--- DIFFERENCE FINDINGS ---");
                            pw.println("The difference map structurally compares Slot A to Slot B.");
                            pw.println(
                                    "Positive Values (Green): Frequencies present in A but missing (or attenuated) in B.");
                            pw.println(
                                    "Negative Values (Red): Frequencies present in B but missing (or attenuated) in A.");
                            pw.println("Neutral Values (Black): Frequencies are structurally identical.");
                            pw.println(
                                    "Please review the exported 'Spectral_Difference_Map.png' for visual acoustic delta analysis.");
                        }
                    }
                }

                Desktop.getDesktop().open(outDir);
                lblStatus.setText("Successfully exported report to: " + outDir.getName());

            } catch (Exception ex) {
                ex.printStackTrace();
                lblStatus.setText("Error exporting: " + ex.getMessage());
            }
        }).start();
    }

    private String calculateMaxAmplitude(AudioData ad) {
        int max = 0;
        for (short s : ad.pcm) {
            if (Math.abs(s) > max)
                max = Math.abs(s);
        }
        return String.format("%.2f", (max / 32768.0) * 100);
    }

    private void setupDragAndDrop() {
        setDropTarget(new DropTarget() {
            @SuppressWarnings("unchecked")
            public synchronized void drop(DropTargetDropEvent event) {
                try {
                    event.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) event.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);

                    if (droppedFiles.size() >= 3) {
                        loadShowdown(droppedFiles.get(0), droppedFiles.get(1), droppedFiles.get(2));
                    } else if (droppedFiles.size() == 2) {
                        loadFiles(droppedFiles.get(0), droppedFiles.get(1));
                    } else if (droppedFiles.size() == 1) {
                        if (dataA == null || (dataA != null && dataB != null)) { // Fresh state
                            dataA = null;
                            dataB = null;
                            panelA.clear();
                            panelB.clear();
                            panelDiff.clear();
                            loadFile(droppedFiles.get(0), 1);
                        } else { // Load second file
                            loadFile(droppedFiles.get(0), 2);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void loadShowdown(File f1, File f2, File f3) {
        dataA = null;
        dataB = null;
        dataC = null;
        panelA.clear();
        panelB.clear();
        panelDiff.clear();
        isShowdown = true;

        File src = f1, c1 = f2, c2 = f3;
        if (f2.getName().toLowerCase().endsWith(".flac") || f2.getName().toLowerCase().endsWith(".wav")) {
            src = f2;
            c1 = f1;
            c2 = f3;
        } else if (f3.getName().toLowerCase().endsWith(".flac") || f3.getName().toLowerCase().endsWith(".wav")) {
            src = f3;
            c1 = f1;
            c2 = f2;
        }

        File finalSrc = src, finalC1 = c1, finalC2 = c2;

        new Thread(() -> {
            try {
                AudioData adSrc = processFile(finalSrc, "SOURCE REFERENCE: " + finalSrc.getName());
                dataA = adSrc;
                nameA = finalSrc.getName();

                AudioData adC1 = processFile(finalC1, "CANDIDATE 1: " + finalC1.getName());
                dataB = adC1;
                nameB = finalC1.getName();

                AudioData adC2 = processFile(finalC2, "CANDIDATE 2: " + finalC2.getName());
                dataC = adC2;
                nameC = finalC2.getName();

                SwingUtilities.invokeLater(() -> {
                    rbViewC1.setText("View: " + nameB);
                    rbViewC2.setText("View: " + nameC);
                    rbViewC1.setVisible(true);
                    rbViewC2.setVisible(true);
                    rbViewC1.setSelected(true);
                    updateShowdownView(1);
                    lblStatus.setText("Showdown Ready! Select which candidate to compare against " + nameA + ".");
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private void updateShowdownView(int candidate) {
        if (!isShowdown || dataA == null)
            return;
        new Thread(() -> {
            panelA.analyzeAndRender(dataA, "SOURCE REFERENCE: " + nameA, null, null, false);
            if (candidate == 1) {
                panelB.analyzeAndRender(dataB, "CANDIDATE 1: " + nameB, null, null, false);
                panelDiff.analyzeAndRender(dataA, nameA, dataB, nameB, true);
            } else {
                panelB.analyzeAndRender(dataC, "CANDIDATE 2: " + nameC, null, null, false);
                panelDiff.analyzeAndRender(dataA, nameA, dataC, nameC, true);
            }
        }).start();
    }

    private void loadFiles(File fA, File fB) {
        dataA = null;
        dataB = null;
        dataC = null;
        isShowdown = false;
        SwingUtilities.invokeLater(() -> {
            rbViewC1.setVisible(false);
            rbViewC2.setVisible(false);
        });
        panelA.clear();
        panelB.clear();
        panelDiff.clear();
        new Thread(() -> {
            try {
                AudioData adA = processFile(fA, "SLOT A: " + fA.getName());
                dataA = adA;
                nameA = fA.getName();
                panelA.analyzeAndRender(dataA, nameA, null, null, false);

                AudioData adB = processFile(fB, "SLOT B: " + fB.getName());
                dataB = adB;
                nameB = fB.getName();
                panelB.analyzeAndRender(dataB, nameB, null, null, false);

                checkAndComputeDiff();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private void loadFile(File f, int slot) {
        new Thread(() -> {
            try {
                AudioData ad = processFile(f, "SLOT " + (slot == 1 ? "A" : "B") + " - " + f.getName());
                if (slot == 1) {
                    dataA = ad;
                    nameA = f.getName();
                    panelA.analyzeAndRender(dataA, nameA, null, null, false);
                    SwingUtilities.invokeLater(() -> lblStatus
                            .setText("Slot A Loaded. Drop another file into the window for Spectral Comparison."));
                } else {
                    dataB = ad;
                    nameB = f.getName();
                    panelB.analyzeAndRender(dataB, nameB, null, null, false);
                    checkAndComputeDiff();
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> lblStatus.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }

    private AudioData processFile(File f, String labelStatus) throws Exception {
        SwingUtilities.invokeLater(() -> lblStatus.setText("Processing " + labelStatus + " ..."));
        File wacFile = f;
        boolean needsTranscode = !f.getName().toLowerCase().endsWith(".wac");

        if (needsTranscode) {
            SwingUtilities.invokeLater(() -> lblStatus
                    .setText("Transcoding " + f.getName() + " to temporary WAC for acoustic analysis..."));
            wacFile = new File("temp_analyzer_" + System.currentTimeMillis() + "_" + f.getName() + ".wac");
            ProcessBuilder pb = new ProcessBuilder("Transcoder.exe", f.getAbsolutePath(), wacFile.getAbsolutePath());
            Process p = pb.start();
            p.waitFor();

            if (!wacFile.exists()) {
                throw new Exception("Transcoding failed! Transcoder did not create output file.");
            }
        }

        SwingUtilities
                .invokeLater(() -> lblStatus.setText("Decoding array mathematically into memory: " + f.getName()));
        AudioData ad = decodeWac(wacFile);

        if (needsTranscode && wacFile.exists()) {
            wacFile.delete();
        }
        return ad;
    }

    private void checkAndComputeDiff() {
        if (dataA != null && dataB != null) {
            SwingUtilities
                    .invokeLater(() -> lblStatus.setText("Computing structural spectral depth difference (A vs B)..."));
            panelDiff.analyzeAndRender(dataA, nameA, dataB, nameB, true);
            SwingUtilities.invokeLater(() -> lblStatus.setText("Spectral Difference Analysis Full Complete."));
        }
    }

    class SpectrogramPanel extends JPanel {
        private BufferedImage spectrogramImage;
        private AudioData data;
        private String trackName;
        private boolean isDiff;
        private String emptyMessage;
        public double similarityScore = 0.0;
        private final int FFT_SIZE = 2048;

        public SpectrogramPanel(String emptyMsg) {
            this.emptyMessage = emptyMsg;
            setBackground(Color.BLACK);
            setBorder(BorderFactory.createLineBorder(BORDER_SUBTLE));
        }

        public void clear() {
            spectrogramImage = null;
            data = null;
            trackName = null;
            repaint();
        }

        public void analyzeAndRender(AudioData a, String nA, AudioData b, String nB, boolean isDiff) {
            this.data = a;
            this.trackName = isDiff ? ("SPECTRAL DIFFERENCE: " + nA + " vs " + nB) : nA;
            this.isDiff = isDiff;

            int channels = a.channels;
            int framesA = a.pcm.length / channels;
            int totalFrames = framesA;
            if (isDiff && b != null) {
                totalFrames = Math.min(framesA, b.pcm.length / b.channels);
            }
            int h = FFT_SIZE / 2;
            int renderWidth = 1000;
            int step = Math.max(1, (totalFrames - FFT_SIZE) / renderWidth);
            renderWidth = (totalFrames - FFT_SIZE) / step;
            if (renderWidth <= 0)
                return;

            BufferedImage img = new BufferedImage(renderWidth, h, BufferedImage.TYPE_INT_RGB);

            double[] window = new double[FFT_SIZE];
            for (int i = 0; i < FFT_SIZE; i++) {
                window[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1));
            }

            double diffSum = 0;
            long validBins = 0;

            for (int x = 0; x < renderWidth; x++) {
                int frameOffset = x * step;
                double[] realA = new double[FFT_SIZE];
                double[] imagA = new double[FFT_SIZE];

                for (int i = 0; i < FFT_SIZE; i++) {
                    short sample = a.pcm[(frameOffset + i) * channels];
                    realA[i] = (sample / 32768.0) * window[i];
                }
                computeFFT(realA, imagA);

                double[] realB = null;
                double[] imagB = null;
                if (isDiff && b != null) {
                    realB = new double[FFT_SIZE];
                    imagB = new double[FFT_SIZE];
                    for (int i = 0; i < FFT_SIZE; i++) {
                        short sampleB = b.pcm[(frameOffset + i) * b.channels];
                        realB[i] = (sampleB / 32768.0) * window[i];
                    }
                    computeFFT(realB, imagB);
                }

                for (int y = 0; y < h; y++) {
                    double magA = Math.sqrt(realA[y] * realA[y] + imagA[y] * imagA[y]) / (FFT_SIZE / 2.0);
                    double dbA = 20 * Math.log10(magA + 1e-10);
                    Color color;
                    if (isDiff && realB != null) {
                        double magB = Math.sqrt(realB[y] * realB[y] + imagB[y] * imagB[y]) / (FFT_SIZE / 2.0);
                        double dbB = 20 * Math.log10(magB + 1e-10);
                        double diffDb = dbA - dbB;
                        color = mapDiffDbToColor(diffDb);
                        if (dbA > -100 || dbB > -100) {
                            diffSum += Math.abs(diffDb);
                            validBins++;
                        }
                    } else {
                        color = mapDbToColor(dbA);
                    }
                    img.setRGB(x, h - 1 - y, color.getRGB());
                }
            }
            if (isDiff && validBins > 0) {
                double mae = diffSum / validBins; // Mean absolute error in dB
                this.similarityScore = Math.max(0.0, 100.0 - (mae / 40.0 * 100.0));
            } else {
                this.similarityScore = 0.0;
            }
            this.spectrogramImage = img;
            repaint();
        }

        public void saveImage(File out) throws Exception {
            if (spectrogramImage == null)
                return;
            BufferedImage img = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            paintComponent(g2);
            g2.dispose();
            ImageIO.write(img, "png", out);
        }

        private Color mapDbToColor(double db) {
            double minDb = -120.0;
            double maxDb = -20.0;
            double clamped = Math.max(minDb, Math.min(maxDb, db));
            double t = (clamped - minDb) / (maxDb - minDb);

            if (t < 0.2)
                return interpolate(new Color(0, 0, 0), new Color(0, 0, 139), t / 0.2);
            if (t < 0.4)
                return interpolate(new Color(0, 0, 139), new Color(128, 0, 128), (t - 0.2) / 0.2);
            if (t < 0.6)
                return interpolate(new Color(128, 0, 128), Color.RED, (t - 0.4) / 0.2);
            if (t < 0.8)
                return interpolate(Color.RED, Color.YELLOW, (t - 0.6) / 0.2);
            return interpolate(Color.YELLOW, Color.WHITE, (t - 0.8) / 0.2);
        }

        private Color mapDiffDbToColor(double diffDb) {
            if (Math.abs(diffDb) < 1.0)
                return new Color(10, 10, 10);
            if (diffDb > 0) {
                // A is louder than B
                double t = Math.min(1.0, diffDb / 20.0);
                return interpolate(new Color(10, 10, 10), Color.GREEN, t);
            } else {
                // A is quieter than B
                double t = Math.min(1.0, -diffDb / 20.0);
                return interpolate(new Color(10, 10, 10), Color.RED, t);
            }
        }

        private Color interpolate(Color a, Color b, double t) {
            return new Color(
                    (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                    (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();

            if (spectrogramImage == null) {
                g.setColor(TEXT_MUTED);
                g.setFont(new Font("Segoe UI", Font.BOLD, 14));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(emptyMessage, (w - fm.stringWidth(emptyMessage)) / 2, h / 2);
                return;
            }

            Graphics2D g2 = (Graphics2D) g;
            int paddingX = 60;
            int paddingY = 40;

            int imgW = w - paddingX * 2 - 50;
            int imgH = h - paddingY * 2;
            g2.drawImage(spectrogramImage, paddingX, paddingY, imgW, imgH, null);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));

            int khz = data.sampleRate / 2000;
            for (int i = 0; i <= khz; i += 5) {
                int y = paddingY + imgH - (int) ((double) i / khz * imgH);
                g2.drawString(i + " kHz", paddingX - 45, y + 4);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.drawLine(paddingX, y, paddingX + imgW, y);
                g2.setColor(Color.WHITE);
            }

            int totalSecs = (data.pcm.length / data.channels) / data.sampleRate;
            for (int i = 0; i <= totalSecs; i += Math.max(1, totalSecs / 10)) {
                int x = paddingX + (int) ((double) i / totalSecs * imgW);
                String time = String.format("%d:%02d", i / 60, i % 60);
                g2.drawString(time, x - 10, paddingY + imgH + 20);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.drawLine(x, paddingY, x, paddingY + imgH);
                g2.setColor(Color.WHITE);
            }

            int legendX = paddingX + imgW + 15;
            int legendW = 15;
            if (isDiff) {
                for (int y = 0; y < imgH; y++) {
                    double diffInfo = 20.0 - (40.0 * y / imgH); // top is +20, bot is -20
                    g2.setColor(mapDiffDbToColor(diffInfo));
                    g2.drawLine(legendX, paddingY + y, legendX + legendW, paddingY + y);
                }
                g2.setColor(Color.WHITE);
                g2.drawString("+20 dB (A > B)", legendX + legendW + 10, paddingY + 10);
                g2.drawString("-20 dB (B > A)", legendX + legendW + 10, paddingY + imgH);
                g2.drawString("  0 dB", legendX + legendW + 10, paddingY + imgH / 2);
            } else {
                for (int y = 0; y < imgH; y++) {
                    double dbInfo = -20.0 - (100.0 * y / imgH);
                    g2.setColor(mapDbToColor(dbInfo));
                    g2.drawLine(legendX, paddingY + y, legendX + legendW, paddingY + y);
                }
                g2.setColor(Color.WHITE);
                for (int dbVal = -20; dbVal >= -120; dbVal -= 20) {
                    int y = paddingY + (int) ((double) (-dbVal - 20) / 100 * imgH);
                    g2.drawString(dbVal + " dB", legendX + legendW + 10, y + 4);
                }
            }

            g2.setFont(new Font("Open Sans", Font.BOLD, 14));
            g2.drawString(trackName + "  |  " + "FFT Size: " + FFT_SIZE + "   |   " + data.sampleRate + " Hz ",
                    paddingX, paddingY - 15);

            if (isDiff && similarityScore > 0) {
                g2.setFont(new Font("Open Sans", Font.BOLD, 16));
                g2.setColor(similarityScore > 90.0 ? Color.GREEN : Color.ORANGE);
                String scoreTxt = String.format("Similarity to Reference: %.2f%%", similarityScore);
                g2.drawString(scoreTxt, paddingX + imgW - g2.getFontMetrics().stringWidth(scoreTxt), paddingY - 15);
                g2.setColor(Color.WHITE);
            }
        }

        private void computeFFT(double[] real, double[] imag) {
            int n = real.length;
            int shift = 1 + Integer.numberOfLeadingZeros(n);
            for (int i = 0; i < n; i++) {
                int j = Integer.reverse(i) >>> shift;
                if (j > i) {
                    double tr = real[i];
                    real[i] = real[j];
                    real[j] = tr;
                    double ti = imag[i];
                    imag[i] = imag[j];
                    imag[j] = ti;
                }
            }
            for (int L = 2; L <= n; L *= 2) {
                double angle = -2 * Math.PI / L;
                double wr = Math.cos(angle);
                double wi = Math.sin(angle);
                for (int i = 0; i < n; i += L) {
                    double w_r = 1.0, w_i = 0.0;
                    for (int j = 0; j < L / 2; j++) {
                        double ur = real[i + j], ui = imag[i + j];
                        double vr = real[i + j + L / 2] * w_r - imag[i + j + L / 2] * w_i;
                        double vi = real[i + j + L / 2] * w_i + imag[i + j + L / 2] * w_r;
                        real[i + j] = ur + vr;
                        imag[i + j] = ui + vi;
                        real[i + j + L / 2] = ur - vr;
                        imag[i + j + L / 2] = ui - vi;
                        double next_w_r = w_r * wr - w_i * wi;
                        double next_w_i = w_r * wi + w_i * wr;
                        w_r = next_w_r;
                        w_i = next_w_i;
                    }
                }
            }
        }
    }

    private static final int[] STEP_TABLE = {
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
            50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253,
            279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166,
            1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428,
            4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289,
            16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    };
    private static final int[] INDEX_TABLE = { -1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8 };

    private AudioData decodeWac(File f) throws Exception {
        byte[] data = Files.readAllBytes(f.toPath());
        if (data.length < 16)
            throw new Exception("Invalid WAC Format.");
        int offset = 0;
        if (data[0] != 'W' || data[1] != 'A' || data[2] != 'R' || data[3] != 'M')
            throw new Exception("Not a valid WARM file.");

        int sampleRate = (data[4] & 0xFF) | ((data[5] & 0xFF) << 8) | ((data[6] & 0xFF) << 16)
                | ((data[7] & 0xFF) << 24);
        int channels = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);

        // Due to C++ struct padding, totalBlocks starts at offset 12, not 10.
        int totalBlocks = (data[12] & 0xFF) | ((data[13] & 0xFF) << 8) | ((data[14] & 0xFF) << 16)
                | ((data[15] & 0xFF) << 24);

        int BSIZE = 128;
        short[] pcm = new short[totalBlocks * BSIZE * channels];
        offset = 16;
        for (int b = 0; b < totalBlocks; b++) {
            if (offset >= data.length)
                break;
            int[] sp = new int[channels], si = new int[channels];
            for (int c = 0; c < channels; c++) {
                if (offset + 4 > data.length)
                    break;
                sp[c] = (short) ((data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8));
                si[c] = (short) ((data[offset + 2] & 0xFF) | ((data[offset + 3] & 0xFF) << 8));
                offset += 4;
            }
            for (int c = 0; c < channels; c++) {
                for (int i = 0; i < BSIZE; i += 2) {
                    if (offset >= data.length)
                        break;
                    int pb = data[offset++] & 0xFF;
                    int n1 = (pb >> 4) & 0xF, n2 = pb & 0xF;
                    for (int j = 0; j < 2; j++) {
                        int nibble = (j == 0) ? n1 : n2;
                        int step = STEP_TABLE[si[c]];
                        int vpdiff = step >> 3;
                        if ((nibble & 4) != 0)
                            vpdiff += step;
                        if ((nibble & 2) != 0)
                            vpdiff += step >> 1;
                        if ((nibble & 1) != 0)
                            vpdiff += step >> 2;
                        if ((nibble & 8) != 0)
                            sp[c] -= vpdiff;
                        else
                            sp[c] += vpdiff;
                        if (sp[c] > 32767)
                            sp[c] = 32767;
                        else if (sp[c] < -32768)
                            sp[c] = -32768;
                        si[c] += INDEX_TABLE[nibble];
                        if (si[c] < 0)
                            si[c] = 0;
                        else if (si[c] > 88)
                            si[c] = 88;
                        pcm[(b * BSIZE + i + j) * channels + c] = (short) sp[c];
                    }
                }
            }
        }
        AudioData ad = new AudioData();
        ad.pcm = pcm;
        ad.sampleRate = sampleRate;
        ad.channels = channels;
        return ad;
    }

    private static java.awt.image.BufferedImage generateLogo(int size) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.RED);
        g2.fillRect(0, 0, size, size);
        g2.setColor(new Color(139, 0, 0));
        int pad = size / 4;
        g2.fillPolygon(new int[] { pad, size - pad, pad }, new int[] { pad, size / 2, size - pad }, 3);
        g2.dispose();
        return img;
    }
}

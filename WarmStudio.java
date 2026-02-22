import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WarmStudio extends JFrame {

    // ─── Design System ────────────────────────────────────────────────────────
    private static final Color BG_BASE = new Color(10, 11, 16);
    private static final Color BG_SURFACE = new Color(18, 20, 28);
    private static final Color BG_ELEVATED = new Color(24, 27, 38);
    private static final Color BG_CARD = new Color(30, 34, 48);
    private static final Color ACCENT_BLUE = new Color(64, 156, 255);
    private static final Color ACCENT_CYAN = new Color(0, 220, 200);
    private static final Color ACCENT_PURPLE = new Color(140, 80, 255);
    private static final Color TEXT_PRIMARY = new Color(230, 235, 245);
    private static final Color TEXT_MUTED = new Color(110, 120, 145);
    private static final Color BORDER_SUBTLE = new Color(35, 40, 58);
    private static final Font FONT_MAIN = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_MONO = new Font("JetBrains Mono", Font.BOLD, 14);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 15);

    // ─── State ────────────────────────────────────────────────────────────────
    private JTable playlistTable;
    private DefaultTableModel playlistModel;
    private JLabel statusLabel, lblTime, lblTrackName, lblSampleRate;
    private JSlider progressSlider, volumeSlider;
    private JProgressBar transcodeProgress;
    private SpectrumPanel spectrumPanel;
    private VuMeter vuLeft, vuRight;
    private GlowButton btnPlayPause, btnStop;

    private SourceDataLine audioLine;
    private FloatControl volumeControl;
    private AtomicBoolean isPlaying = new AtomicBoolean(false);
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private AtomicInteger progressIndex = new AtomicInteger(0);
    private AtomicInteger seekRequest = new AtomicInteger(-1);
    private Thread audioThread;
    private AudioData currentAudioData;
    private List<File> playlist = new ArrayList<>();
    private int currentTrackIndex = -1;

    // ─── Constructor ──────────────────────────────────────────────────────────
    public WarmStudio() {
        setTitle("WAC Studio — Warm Audio Codec");
        setSize(1100, 720);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setBackground(BG_BASE);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_BASE);
        root.add(buildTitleBar(), BorderLayout.NORTH);
        root.add(buildMainArea(), BorderLayout.CENTER);
        root.add(buildPlayerDeck(), BorderLayout.SOUTH);
        setContentPane(root);

        new Timer(60, e -> tickUI()).start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TITLE BAR
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildTitleBar() {
        JPanel bar = new GradientPanel(BG_SURFACE, new Color(14, 16, 23));
        bar.setLayout(new BorderLayout());
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SUBTLE));
        bar.setPreferredSize(new Dimension(0, 56));

        // Logo + Title
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 12));
        left.setOpaque(false);
        JLabel logo = new JLabel("◈ WAC STUDIO");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        logo.setForeground(ACCENT_BLUE);
        JLabel version = new JLabel("v13 • Cinematic 3D Spatial Audio");
        version.setFont(FONT_SMALL);
        version.setForeground(TEXT_MUTED);
        left.add(logo);
        left.add(version);

        // Toolbar buttons
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        right.setOpaque(false);
        GlowButton btnTranscode = new GlowButton("⊕  Transcode", ACCENT_BLUE, true);
        btnTranscode.addActionListener(e -> transcodeFile());
        GlowButton btnAdd = new GlowButton("＋  Add WAC", ACCENT_CYAN, false);
        btnAdd.addActionListener(e -> addWacToPlaylist());
        GlowButton btnClear = new GlowButton("✕  Clear", TEXT_MUTED, false);
        btnClear.addActionListener(e -> {
            playlist.clear();
            playlistModel.setRowCount(0);
            updateStatus("Playlist cleared.");
        });
        right.add(btnTranscode);
        right.add(btnAdd);
        right.add(btnClear);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // MAIN AREA (Playlist + Visualizer)
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildMainArea() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setBackground(BG_BASE);
        panel.setBorder(new EmptyBorder(12, 14, 0, 14));

        panel.add(buildPlaylistPanel(), BorderLayout.WEST);
        panel.add(buildVisualizerPanel(), BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildPlaylistPanel() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(BG_CARD);
        wrap.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
                new EmptyBorder(0, 0, 0, 0)));
        wrap.setPreferredSize(new Dimension(290, 0));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_ELEVATED);
        header.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel title = new JLabel("  MEDIA DECK");
        title.setFont(FONT_SMALL);
        title.setForeground(TEXT_MUTED);
        header.add(title, BorderLayout.WEST);
        wrap.add(header, BorderLayout.NORTH);

        // Table
        String[] cols = { "", "Track", "Type" };
        playlistModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        playlistTable = new JTable(playlistModel);
        playlistTable.setBackground(BG_CARD);
        playlistTable.setForeground(TEXT_PRIMARY);
        playlistTable.setFont(FONT_MAIN);
        playlistTable.setRowHeight(36);
        playlistTable.setSelectionBackground(new Color(64, 156, 255, 40));
        playlistTable.setSelectionForeground(ACCENT_BLUE);
        playlistTable.setShowGrid(false);
        playlistTable.setIntercellSpacing(new Dimension(0, 2));
        playlistTable.getTableHeader().setBackground(BG_ELEVATED);
        playlistTable.getTableHeader().setForeground(TEXT_MUTED);
        playlistTable.getTableHeader().setFont(FONT_SMALL);
        playlistTable.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SUBTLE));
        playlistTable.getColumnModel().getColumn(0).setMaxWidth(28);
        playlistTable.getColumnModel().getColumn(2).setMaxWidth(60);
        playlistTable.setDefaultRenderer(Object.class, new PlaylistCellRenderer());
        playlistTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && playlistTable.getSelectedRow() != -1)
                    playTrack(playlistTable.getSelectedRow());
            }
        });

        JScrollPane scroll = new JScrollPane(playlistTable);
        scroll.setBackground(BG_CARD);
        scroll.getViewport().setBackground(BG_CARD);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setBackground(BG_CARD);
        wrap.add(scroll, BorderLayout.CENTER);

        // Transcode progress at bottom
        transcodeProgress = new JProgressBar(0, 100);
        transcodeProgress.setStringPainted(true);
        transcodeProgress.setString("TRANSCODING...");
        transcodeProgress.setFont(FONT_SMALL);
        transcodeProgress.setForeground(ACCENT_BLUE);
        transcodeProgress.setBackground(BG_BASE);
        transcodeProgress.setBorderPainted(false);
        transcodeProgress.setPreferredSize(new Dimension(0, 20));
        transcodeProgress.setVisible(false);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(BG_ELEVATED);
        footer.setBorder(new EmptyBorder(6, 10, 6, 10));
        footer.add(transcodeProgress, BorderLayout.CENTER);
        wrap.add(footer, BorderLayout.SOUTH);

        return wrap;
    }

    private JPanel buildVisualizerPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG_BASE);

        // --- Top info strip ---
        JPanel infoStrip = new JPanel(new BorderLayout());
        infoStrip.setBackground(BG_ELEVATED);
        infoStrip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
                new EmptyBorder(10, 16, 10, 16)));

        lblTrackName = new JLabel("No Track Selected");
        lblTrackName.setFont(FONT_TITLE);
        lblTrackName.setForeground(TEXT_PRIMARY);

        lblSampleRate = new JLabel("WAC v13  •  44100 Hz  •  Stereo  •  ~375 kbps");
        lblSampleRate.setFont(FONT_SMALL);
        lblSampleRate.setForeground(TEXT_MUTED);

        JPanel nameBlock = new JPanel(new GridLayout(2, 1, 0, 3));
        nameBlock.setOpaque(false);
        nameBlock.add(lblTrackName);
        nameBlock.add(lblSampleRate);
        infoStrip.add(nameBlock, BorderLayout.WEST);

        // VU Meters
        JPanel vuPair = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        vuPair.setOpaque(false);
        JLabel vuLabel = new JLabel("L");
        vuLabel.setFont(FONT_SMALL);
        vuLabel.setForeground(TEXT_MUTED);
        vuLeft = new VuMeter(ACCENT_CYAN);
        vuRight = new VuMeter(ACCENT_BLUE);
        JLabel vuLabel2 = new JLabel("R");
        vuLabel2.setFont(FONT_SMALL);
        vuLabel2.setForeground(TEXT_MUTED);
        vuPair.add(vuLabel);
        vuPair.add(vuLeft);
        vuPair.add(vuRight);
        vuPair.add(vuLabel2);
        infoStrip.add(vuPair, BorderLayout.EAST);

        // --- Spectrum ---
        spectrumPanel = new SpectrumPanel();
        JPanel spectrumWrap = new JPanel(new BorderLayout());
        spectrumWrap.setBackground(BG_SURFACE);
        spectrumWrap.setBorder(BorderFactory.createLineBorder(BORDER_SUBTLE, 1));
        spectrumWrap.add(spectrumPanel);

        panel.add(infoStrip, BorderLayout.NORTH);
        panel.add(spectrumWrap, BorderLayout.CENTER);
        return panel;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PLAYER DECK (Bottom)
    // ═════════════════════════════════════════════════════════════════════════
    private JPanel buildPlayerDeck() {
        JPanel deck = new JPanel(new BorderLayout(0, 0));
        deck.setBackground(BG_SURFACE);
        deck.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_SUBTLE));

        // --- Seek bar ---
        progressSlider = new JSlider(0, 1000, 0);
        progressSlider.setBackground(BG_SURFACE);
        progressSlider.setOpaque(false);
        progressSlider.setBorder(new EmptyBorder(10, 20, 0, 20));
        progressSlider.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (currentAudioData != null && isPlaying.get()) {
                    double pct = (double) e.getX() / progressSlider.getWidth();
                    seekRequest.set((int) (pct * currentAudioData.pcm.length * 2));
                }
            }
        });

        // Style the slider with a custom UI
        progressSlider.setUI(new javax.swing.plaf.basic.BasicSliderUI(progressSlider) {
            public void paintThumb(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Rectangle r = thumbRect;
                g2.setColor(ACCENT_BLUE);
                g2.fillOval(r.x, r.y + 2, r.width, r.height - 4);
                g2.setColor(Color.WHITE);
                g2.fillOval(r.x + 3, r.y + 5, r.width - 6, r.height - 10);
                g2.dispose();
            }

            public void paintTrack(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Rectangle r = trackRect;
                int cy = r.y + r.height / 2;
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(r.x, cy, r.x + r.width, cy);
                int fillX = r.x + (int) ((double) (progressSlider.getValue() - progressSlider.getMinimum())
                        / (progressSlider.getMaximum() - progressSlider.getMinimum()) * r.width);
                GradientPaint gp = new GradientPaint(r.x, cy, ACCENT_PURPLE, fillX, cy, ACCENT_BLUE);
                g2.setPaint(gp);
                g2.drawLine(r.x, cy, fillX, cy);
                g2.dispose();
            }
        });

        // --- Controls row ---
        JPanel controls = new JPanel(new BorderLayout());
        controls.setBackground(BG_SURFACE);
        controls.setBorder(new EmptyBorder(12, 20, 14, 20));

        // Transport buttons
        JPanel transport = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        transport.setOpaque(false);

        GlowButton btnPrev = new GlowButton("⏮", TEXT_MUTED, false);
        btnPrev.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        btnPrev.addActionListener(e -> {
            if (currentTrackIndex > 0)
                playTrack(currentTrackIndex - 1);
        });

        btnPlayPause = new GlowButton("▶  PLAY", ACCENT_BLUE, true);
        btnPlayPause.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnPlayPause.addActionListener(e -> togglePlayPause());

        btnStop = new GlowButton("■  STOP", new Color(200, 80, 80), false);
        btnStop.addActionListener(e -> stopPlayback());

        GlowButton btnNext = new GlowButton("⏭", TEXT_MUTED, false);
        btnNext.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        btnNext.addActionListener(e -> {
            if (currentTrackIndex < playlist.size() - 1)
                playTrack(currentTrackIndex + 1);
        });

        transport.add(btnPrev);
        transport.add(btnPlayPause);
        transport.add(btnStop);
        transport.add(btnNext);

        // Time display
        lblTime = new JLabel("00:00 / 00:00");
        lblTime.setFont(FONT_MONO);
        lblTime.setForeground(ACCENT_BLUE);
        lblTime.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Volume
        JPanel volRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        volRow.setOpaque(false);
        JLabel lVol = new JLabel("⊿ VOL");
        lVol.setFont(FONT_SMALL);
        lVol.setForeground(TEXT_MUTED);
        volumeSlider = new JSlider(0, 100, 80);
        volumeSlider.setOpaque(false);
        volumeSlider.setPreferredSize(new Dimension(110, 22));
        volumeSlider.addChangeListener(e -> applyVolume());
        volRow.add(lVol);
        volRow.add(volumeSlider);

        // Status bar
        statusLabel = new JLabel("  Ready. Load a track to begin.");
        statusLabel.setFont(FONT_SMALL);
        statusLabel.setForeground(TEXT_MUTED);

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setBackground(BG_BASE);
        statusRow.setBorder(new EmptyBorder(5, 20, 5, 20));
        statusRow.add(statusLabel, BorderLayout.WEST);

        controls.add(lblTime, BorderLayout.WEST);
        controls.add(transport, BorderLayout.CENTER);
        controls.add(volRow, BorderLayout.EAST);

        deck.add(progressSlider, BorderLayout.NORTH);
        deck.add(controls, BorderLayout.CENTER);
        deck.add(statusRow, BorderLayout.SOUTH);
        return deck;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PLAYBACK LOGIC
    // ═════════════════════════════════════════════════════════════════════════
    private void togglePlayPause() {
        if (!isPlaying.get() && !playlist.isEmpty()) {
            int idx = playlistTable.getSelectedRow();
            playTrack(idx != -1 ? idx : 0);
        } else if (isPaused.get()) {
            isPaused.set(false);
            btnPlayPause.setText("⏸  PAUSE");
            spectrumPanel.setActive(true);
        } else {
            isPaused.set(true);
            btnPlayPause.setText("▶  RESUME");
            spectrumPanel.setActive(false);
        }
    }

    private void stopPlayback() {
        isPlaying.set(false);
        isPaused.set(false);
        btnPlayPause.setText("▶  PLAY");
        spectrumPanel.setActive(false);
        vuLeft.setLevel(0);
        vuRight.setLevel(0);
    }

    private void playTrack(int index) {
        if (index < 0 || index >= playlist.size())
            return;
        currentTrackIndex = index;
        isPlaying.set(false);
        isPaused.set(false);

        if (audioThread != null) {
            try {
                audioThread.join(300);
            } catch (Exception ignored) {
            }
        }

        File f = playlist.get(index);
        playlistTable.setRowSelectionInterval(index, index);
        spectrumPanel.setActive(false);
        updateStatus("Decoding: " + f.getName());
        lblTrackName.setText(stripExt(f.getName()));

        audioThread = new Thread(() -> {
            try {
                currentAudioData = decodeWac(f);
                SwingUtilities.invokeLater(() -> {
                    lblSampleRate.setText("WAC v13  •  " + currentAudioData.sampleRate + " Hz  •  "
                            + (currentAudioData.channels == 2 ? "Stereo" : "Mono") + "  •  ~375 kbps");
                    updateStatus("Playing: " + f.getName());
                    btnPlayPause.setText("⏸  PAUSE");
                    spectrumPanel.setActive(true);
                });
                streamAudio(currentAudioData);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> updateStatus("Error: " + ex.getMessage()));
            }
            SwingUtilities.invokeLater(() -> {
                btnPlayPause.setText("▶  PLAY");
                spectrumPanel.setActive(false);
                vuLeft.setLevel(0);
                vuRight.setLevel(0);
                updateStatus("Stopped.");
                progressSlider.setValue(0);
                lblTime.setText("00:00 / 00:00");
            });
        });
        audioThread.setDaemon(true);
        audioThread.start();
    }

    private void streamAudio(AudioData data) throws Exception {
        AudioFormat fmt = new AudioFormat(data.sampleRate, 16, data.channels, true, false);
        audioLine = AudioSystem.getSourceDataLine(fmt);
        audioLine.open(fmt, 8192 * 4);
        if (audioLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            volumeControl = (FloatControl) audioLine.getControl(FloatControl.Type.MASTER_GAIN);
            applyVolume();
        }
        audioLine.start();
        isPlaying.set(true);

        byte[] raw = new byte[data.pcm.length * 2];
        for (int i = 0; i < data.pcm.length; i++) {
            raw[i * 2] = (byte) (data.pcm[i] & 0xFF);
            raw[i * 2 + 1] = (byte) ((data.pcm[i] >> 8) & 0xFF);
        }

        int offset = 0;
        int total = raw.length;
        progressIndex.set(0);

        while (offset < total && isPlaying.get()) {
            if (isPaused.get()) {
                Thread.sleep(30);
                continue;
            }
            int seek = seekRequest.getAndSet(-1);
            if (seek >= 0)
                offset = seek - (seek % (data.channels * 2));
            int len = Math.min(4096, total - offset);
            audioLine.write(raw, offset, len);
            offset += len;
            progressIndex.set(offset);

            // Feed VU with live sample energy
            int sampleOffset = offset / 2;
            if (sampleOffset < data.pcm.length && data.channels == 2) {
                float l = Math.abs(data.pcm[Math.max(0, sampleOffset - 2)] / 32768f);
                float r = Math.abs(data.pcm[Math.max(0, sampleOffset - 1)] / 32768f);
                vuLeft.setLevel(l);
                vuRight.setLevel(r);
            }
        }

        if (isPlaying.get() && !isPaused.get())
            audioLine.drain();
        audioLine.close();
        isPlaying.set(false);
    }

    private void applyVolume() {
        if (volumeControl == null)
            return;
        float val = volumeSlider.getValue() / 100f;
        float db = (float) (Math.log(val == 0 ? 0.0001 : val) / Math.log(10.0) * 20.0);
        db = Math.max(volumeControl.getMinimum(), Math.min(volumeControl.getMaximum(), db));
        volumeControl.setValue(db);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TRANSCODING
    // ═════════════════════════════════════════════════════════════════════════
    private void transcodeFile() {
        JFileChooser chooser = new JFileChooser(".");
        chooser.setDialogTitle("Select Audio File to Transcode to .wac");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;

        File in = chooser.getSelectedFile();
        File out = new File(in.getParent(), in.getName() + ".wac");

        updateStatus("Transcoding: " + in.getName());
        transcodeProgress.setValue(0);
        transcodeProgress.setVisible(true);

        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("Transcoder.exe", in.getAbsolutePath(), out.getAbsolutePath());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("PROGRESS:")) {
                        try {
                            int pct = Integer.parseInt(line.split(":")[1].trim());
                            SwingUtilities.invokeLater(() -> transcodeProgress.setValue(pct));
                        } catch (Exception ignored) {
                        }
                    }
                }
                p.waitFor();
                SwingUtilities.invokeLater(() -> {
                    transcodeProgress.setVisible(false);
                    playlist.add(out);
                    playlistModel.addRow(new Object[] { "♪", out.getName(), "WAC" });
                    updateStatus("✓ Transcoded: " + out.getName());
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    transcodeProgress.setVisible(false);
                    updateStatus("⚠ Error: " + ex.getMessage());
                });
            }
        }).start();
    }

    private void addWacToPlaylist() {
        JFileChooser chooser = new JFileChooser(".");
        chooser.setDialogTitle("Add WAC Files");
        chooser.setFileFilter(new FileNameExtensionFilter("WAC Audio (*.wac)", "wac"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        File f = chooser.getSelectedFile();
        playlist.add(f);
        playlistModel.addRow(new Object[] { "♪", f.getName(), "WAC" });
        updateStatus("Added: " + f.getName());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UI TIMER TICK
    // ═════════════════════════════════════════════════════════════════════════
    private void tickUI() {
        if (!isPlaying.get() || currentAudioData == null)
            return;
        long totalBytes = currentAudioData.pcm.length * 2L;
        long curBytes = progressIndex.get();
        if (totalBytes == 0)
            return;
        long totalSec = totalBytes / (currentAudioData.sampleRate * currentAudioData.channels * 2L);
        long curSec = curBytes / (currentAudioData.sampleRate * currentAudioData.channels * 2L);
        lblTime.setText(fmt(curSec) + " / " + fmt(totalSec));
        if (seekRequest.get() == -1)
            progressSlider.setValue((int) ((curBytes * 1000) / totalBytes));
    }

    private String fmt(long s) {
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private String stripExt(String n) {
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }

    private void updateStatus(String msg) {
        statusLabel.setText("  " + msg);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // WAC DECODER
    // ═════════════════════════════════════════════════════════════════════════
    static class AudioData {
        short[] pcm;
        int sampleRate, channels;
    }

    private AudioData decodeWac(File f) throws Exception {
        byte[] data = Files.readAllBytes(f.toPath());
        if (data.length < 16)
            throw new Exception("Invalid WAC Format.");
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[4];
        bb.get(magic);
        if (magic[0] != 'W' || magic[1] != 'A' || magic[2] != 'R' || magic[3] != 'M')
            throw new Exception("Not a WAC file.");
        int sampleRate = bb.getInt();
        short channels = bb.getShort();
        bb.position(12);
        int totalBlocks = bb.getInt();

        int[] STEP = { 7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73,
                80, 88, 97, 107,
                118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
                876,
                963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428,
                4871,
                5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385,
                24623, 27086, 29794, 32767 };
        int[] IDX = { -1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8 };
        int BSIZE = 128;

        short[] pcm = new short[totalBlocks * BSIZE * channels];
        int offset = 16;

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
                        int step = STEP[si[c]];
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
                        si[c] += IDX[nibble];
                        if (si[c] < 0)
                            si[c] = 0;
                        else if (si[c] > 88)
                            si[c] = 88;
                        pcm[(b * BSIZE + i + j) * channels + c] = (short) sp[c];
                    }
                }
            }
        }

        AudioData d = new AudioData();
        d.pcm = pcm;
        d.sampleRate = sampleRate;
        d.channels = channels;
        return d;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CUSTOM COMPONENTS
    // ═════════════════════════════════════════════════════════════════════════

    /** Animated spectrum analyzer panel */
    class SpectrumPanel extends JPanel {
        private final int BAR_COUNT = 48;
        private final float[] heights = new float[BAR_COUNT];
        private final float[] targets = new float[BAR_COUNT];
        private final float[] peaks = new float[BAR_COUNT];
        private boolean active = false;

        SpectrumPanel() {
            setBackground(BG_SURFACE);
            new Timer(40, e -> tick()).start();
        }

        void setActive(boolean a) {
            active = a;
        }

        private void tick() {
            for (int i = 0; i < BAR_COUNT; i++) {
                if (active) {
                    // Weighted frequency distribution (more realistic)
                    double freq = (i + 1.0) / BAR_COUNT;
                    double energy = Math.random() * (0.2 + 0.8 * Math.sin(freq * Math.PI));
                    targets[i] = (float) (energy * getHeight() * 0.82);
                } else {
                    targets[i] = 2;
                }
                // Smooth motion
                heights[i] += (targets[i] - heights[i]) * 0.35f;
                if (active && heights[i] > peaks[i])
                    peaks[i] = heights[i];
                else
                    peaks[i] = Math.max(2, peaks[i] - 1.2f);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int barW = (w - (BAR_COUNT - 1) * 3) / BAR_COUNT;
            if (barW < 2)
                barW = 2;

            // Dark grid lines
            g2.setColor(new Color(35, 40, 55));
            for (int y = h; y > 0; y -= h / 6) {
                g2.drawLine(0, y, w, y);
            }

            // WAC label watermark
            g2.setFont(new Font("Segoe UI", Font.BOLD, 64));
            g2.setColor(new Color(255, 255, 255, 8));
            g2.drawString("WAC", w / 2 - 80, h / 2 + 22);

            for (int i = 0; i < BAR_COUNT; i++) {
                int x = i * (barW + 3);
                int bh = Math.max(2, (int) heights[i]);
                int y = h - bh;

                // Frequency gradient: purple → blue → cyan
                float t = (float) i / BAR_COUNT;
                Color top, bot;
                if (t < 0.4f) {
                    top = interpolate(ACCENT_PURPLE, ACCENT_BLUE, t / 0.4f);
                } else {
                    top = interpolate(ACCENT_BLUE, ACCENT_CYAN, (t - 0.4f) / 0.6f);
                }
                bot = new Color(top.getRed(), top.getGreen(), top.getBlue(), 60);

                GradientPaint gp = new GradientPaint(x, y, top, x, h, bot);
                g2.setPaint(gp);
                g2.fillRoundRect(x, y, barW, bh, 3, 3);

                // Glow below bar
                g2.setColor(new Color(top.getRed(), top.getGreen(), top.getBlue(), 25));
                g2.fillRoundRect(x - 1, y - 2, barW + 2, bh + 6, 5, 5);

                // Peak dot
                if (active && peaks[i] > 4) {
                    int py = h - (int) peaks[i] - 3;
                    g2.setColor(top.brighter());
                    g2.fillRect(x, py, barW, 2);
                }
            }
            g2.dispose();
        }
    }

    private Color interpolate(Color a, Color b, float t) {
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Vertical VU Meter bar */
    class VuMeter extends JPanel {
        private float level = 0;
        private float displayed = 0;
        private final Color color;

        VuMeter(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(14, 52));
            setBackground(BG_BASE);
        }

        void setLevel(float l) {
            this.level = Math.min(1, l);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            displayed += (level - displayed) * 0.25f;
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            int filled = (int) (displayed * h);

            g2.setColor(BORDER_SUBTLE);
            g2.fillRoundRect(0, 0, w, h, 4, 4);

            GradientPaint gp = new GradientPaint(0, h - filled, color, 0, h,
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
            g2.setPaint(gp);
            g2.fillRoundRect(0, h - filled, w, filled, 4, 4);
            g2.dispose();
            repaint();
        }
    }

    /** Premium glow button */
    static class GlowButton extends JButton {
        private final Color accent;
        private final boolean primary;

        GlowButton(String text, Color accent, boolean primary) {
            super(text);
            this.accent = accent;
            this.primary = primary;
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setPreferredSize(new Dimension(text.length() * 9 + 30, 34));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            boolean hover = getModel().isRollover();

            if (primary) {
                GradientPaint gp = new GradientPaint(0, 0,
                        hover ? accent.brighter() : accent,
                        w, h,
                        hover ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180) : accent.darker());
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g2.setColor(Color.WHITE);
            } else {
                Color bg = hover ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 25)
                        : new Color(255, 255, 255, 8);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), hover ? 200 : 90));
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g2.setColor(hover ? accent : accent);
            }
            FontMetrics fm = g2.getFontMetrics(getFont());
            g2.setFont(getFont());
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }
    }

    /** Gradient background panel */
    static class GradientPanel extends JPanel {
        private final Color c1, c2;

        GradientPanel(Color c1, Color c2) {
            this.c1 = c1;
            this.c2 = c2;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(0, 0, c1, 0, getHeight(), c2));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    /** Playlist cell renderer with alternating colors */
    class PlaylistCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            lbl.setFont(col == 0 ? new Font("Segoe UI", Font.PLAIN, 14) : (col == 2 ? FONT_SMALL : FONT_MAIN));
            lbl.setBorder(new EmptyBorder(0, col == 0 ? 8 : 4, 0, 4));
            if (sel) {
                lbl.setBackground(new Color(64, 156, 255, 35));
                lbl.setForeground(ACCENT_BLUE);
            } else {
                lbl.setBackground(row % 2 == 0 ? BG_CARD : new Color(28, 32, 45));
                lbl.setForeground(col == 2 ? TEXT_MUTED : TEXT_PRIMARY);
            }
            lbl.setOpaque(true);
            if (row == currentTrackIndex && isPlaying.get())
                lbl.setForeground(ACCENT_CYAN);
            return lbl;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ENTRY POINT
    // ═════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new WarmStudio().setVisible(true));
    }
}

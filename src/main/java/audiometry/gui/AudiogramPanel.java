package audiometry.gui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class AudiogramPanel extends JPanel {
    private final Map<Integer, Integer> rightEarThresholds = new TreeMap<>(); // sag kulak esik degerleri
    private final Map<Integer, Integer> leftEarThresholds = new TreeMap<>(); // sol kulak esik degerleri
    private final int[] freqs = {250, 500, 1000, 2000, 4000, 8000}; // test frekanslari
    
    // Y-ekseni dB seviyesi
    private final int minDb = -10;
    private final int maxDb = 120;
    private final int dbStep = 10;
    private final int padding = 50;

    public AudiogramPanel() {
        setPreferredSize(new Dimension(600, 500)); // panel cozunurlugu
        setBackground(Color.WHITE); 
    }

    public void addThreshold(int frequency, int thresholdDb, boolean isRightEar) {
        if (isRightEar) {
            rightEarThresholds.put(frequency, thresholdDb); // sag kulaga ekle
        } else {
            leftEarThresholds.put(frequency, thresholdDb); // sol kulaga ekle
        }
        repaint(); // ekrani yenile
    }
    
    public void clear() {
        rightEarThresholds.clear(); // sag verileri temizle
        leftEarThresholds.clear(); // sol verileri temizle
        repaint(); // ekrani yenile
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // onceki cizimleri sil
        Graphics2D g2d = (Graphics2D) g; // Graphics2D objesi
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth(); // panel genisligi
        int height = getHeight(); // panel yuksekligi

        int drawWidth = width - 2 * padding;
        int drawHeight = height - 2 * padding;
        
        // Izgara ve etiketleri çiz
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        
        // Y-eksenini çiz (dB - Ses Şiddeti)
        int dbRange = maxDb - minDb;
        for (int db = minDb; db <= maxDb; db += dbStep) {
            int y = padding + (db - minDb) * drawHeight / dbRange;
            g2d.drawLine(padding, y, width - padding, y);
            
            g2d.setColor(Color.BLACK);
            String label = String.valueOf(db);
            FontMetrics metrics = g2d.getFontMetrics();
            int labelWidth = metrics.stringWidth(label);
            g2d.drawString(label, padding - labelWidth - 5, y + metrics.getHeight() / 2 - 2);
            g2d.setColor(Color.LIGHT_GRAY);
        }
        
        // X-eksenini çiz (Frekanslar)
        for (int i = 0; i < freqs.length; i++) {
            int x = padding + i * drawWidth / (freqs.length - 1);
            g2d.drawLine(x, padding, x, height - padding);
            
            g2d.setColor(Color.BLACK);
            String label = String.valueOf(freqs[i]);
            FontMetrics metrics = g2d.getFontMetrics();
            int labelWidth = metrics.stringWidth(label);
            g2d.drawString(label, x - labelWidth / 2, padding - 10);
            g2d.setColor(Color.LIGHT_GRAY);
        }
        
        // Eksen Başlıkları
        g2d.setColor(Color.BLACK);
        g2d.drawString("Frekans (Hz)", width / 2 - 40, 20); // X-ekseni başlığı
        
        Graphics2D yLabelG2 = (Graphics2D) g2d.create();
        yLabelG2.rotate(-Math.PI / 2);
        yLabelG2.drawString("İşitme Seviyesi (dB)", -height / 2 - 50, 15); // Y-ekseni başlığı
        yLabelG2.dispose();

        // Veri noktalarını çiz
        drawEarData(g2d, rightEarThresholds, Color.RED, true, padding, drawWidth, drawHeight, dbRange);
        drawEarData(g2d, leftEarThresholds, Color.BLUE, false, padding, drawWidth, drawHeight, dbRange);
    }

    private void drawEarData(Graphics2D g2d, Map<Integer, Integer> earThresholds, Color color, boolean isRightEar, int padding, int drawWidth, int drawHeight, int dbRange) {
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2)); // Çizgi kalınlığı
        
        Integer prevX = null;
        Integer prevY = null;
        
        for (int i = 0; i < freqs.length; i++) {
            int f = freqs[i];
            if (earThresholds.containsKey(f)) {
                int threshold = earThresholds.get(f);
                int x = padding + i * drawWidth / (freqs.length - 1);
                int y = padding + (threshold - minDb) * drawHeight / dbRange;
                
                int radius = 5;
                if (isRightEar) {
                    // Kırmızı 'O' sembolünü çiz
                    g2d.drawOval(x - radius, y - radius, radius * 2, radius * 2);
                } else {
                    // Mavi 'X' sembolünü çiz
                    g2d.drawLine(x - radius, y - radius, x + radius, y + radius);
                    g2d.drawLine(x - radius, y + radius, x + radius, y - radius);
                }
                
                // Noktaları birleştiren çizgiyi çiz
                if (prevX != null && prevY != null) {
                    g2d.drawLine(prevX, prevY, x, y);
                }
                
                prevX = x;
                prevY = y;
            }
        }
    }
    
    public void saveAsPng(File file) throws IOException {
        BufferedImage image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB); // bos resim olustur
        Graphics2D g2d = image.createGraphics(); // resme cizim yapmak icin
        this.paint(g2d); // paneli resme ciz
        g2d.dispose(); // hafizayi temizle
        ImageIO.write(image, "PNG", file); // png olarak kaydet
    }

    public void saveAsCsv(File file) throws IOException {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file))) {
            writer.println("Frequency (Hz),Right Ear (dB),Left Ear (dB)");
            for (int f : freqs) {
                String right = rightEarThresholds.containsKey(f) ? String.valueOf(rightEarThresholds.get(f)) : "";
                String left = leftEarThresholds.containsKey(f) ? String.valueOf(leftEarThresholds.get(f)) : "";
                writer.println(f + "," + right + "," + left);
            }
        }
    }
}

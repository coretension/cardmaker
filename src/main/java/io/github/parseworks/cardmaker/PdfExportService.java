package io.github.parseworks.cardmaker;

import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.lowagie.text.pdf.PdfArray;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfNumber;
import java.awt.color.ColorSpace;
import java.awt.image.ColorConvertOp;

public class PdfExportService {
    private final CardTemplate template;
    private final List<Map<String, String>> csvData;
    private final CardMakerController controller;

    public PdfExportService(CardTemplate template, List<Map<String, String>> csvData, CardMakerController controller) {
        this.template = template;
        this.csvData = csvData;
        this.controller = controller;
    }

    public void exportToPdf(File file) throws IOException {
        double cardWidthMm = template.getDimension().getWidthMm();
        double cardHeightMm = template.getDimension().getHeightMm();
        double bleedMm = template.getBleedMm();
        
        float totalWidthPt = (float)((cardWidthMm + 2 * bleedMm) * 72 / 25.4);
        float totalHeightPt = (float)((cardHeightMm + 2 * bleedMm) * 72 / 25.4);

        Document document = new Document(new Rectangle(totalWidthPt, totalHeightPt), 0, 0, 0, 0);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(file));
            
            // Set PDF to use CMYK Intent (simplified)
            writer.setPdfVersion(PdfWriter.VERSION_1_4);
            
            document.open();
            PdfContentByte cb = writer.getDirectContent();

            List<Map<String, String>> records = csvData.isEmpty() ? List.of(Map.of()) : csvData;

            for (Map<String, String> record : records) {
                document.newPage();
                
                // Render card to image at high DPI
                BufferedImage cardImage = renderCardToImage(record, 300);
                
                com.lowagie.text.Image pdfImg = convertToCmykImage(cardImage);
                pdfImg.scaleToFit(totalWidthPt, totalHeightPt);
                pdfImg.setAbsolutePosition(0, 0);
                cb.addImage(pdfImg);
            }
        } catch (Exception e) {
            throw new IOException("Failed to export PDF: " + e.getMessage(), e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    private BufferedImage renderCardToImage(Map<String, String> record, double dpi) {
        double bleedMm = template.getBleedMm();
        double widthPx = (template.getDimension().getWidthMm() + 2 * bleedMm) * dpi / 25.4;
        double heightPx = (template.getDimension().getHeightMm() + 2 * bleedMm) * dpi / 25.4;

        Pane root = new Pane();
        root.setPrefSize(widthPx, heightPx);
        root.setMinSize(widthPx, heightPx);
        root.setMaxSize(widthPx, heightPx);
        root.setStyle("-fx-background-color: white;");

        double scale = dpi / CardDimension.getDpi();
        Pane contentPane = new Pane();
        double bleedPx = bleedMm * dpi / 25.4;
        contentPane.setLayoutX(bleedPx);
        contentPane.setLayoutY(bleedPx);
        contentPane.setScaleX(scale);
        contentPane.setScaleY(scale);
        // Pivot from top-left
        contentPane.setTranslateX((scale - 1) * template.getDimension().getWidthPx() / 2);
        contentPane.setTranslateY((scale - 1) * template.getDimension().getHeightPx() / 2);
        
        root.getChildren().add(contentPane);

        controller.renderElementsExternal(template.getElements(), contentPane, record, true);

        // Snapshot requires a scene to apply styles/layouts correctly in some cases
        new Scene(root); 

        // Snapshot
        javafx.scene.image.WritableImage snapshot = root.snapshot(null, null);
        return SwingFXUtils.fromFXImage(snapshot, null);
    }

    private com.lowagie.text.Image convertToCmykImage(BufferedImage rgbImage) throws Exception {
        int w = rgbImage.getWidth();
        int h = rgbImage.getHeight();
        
        byte[] cmykData = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = rgbImage.getRGB(x, y);
                float r = ((rgb >> 16) & 0xFF) / 255f;
                float g = ((rgb >> 8) & 0xFF) / 255f;
                float b = (rgb & 0xFF) / 255f;
                
                float k = 1.0f - Math.max(r, Math.max(g, b));
                float c = (1.0f - r - k) / (1.0f - k + 1e-9f);
                float m = (1.0f - g - k) / (1.0f - k + 1e-9f);
                float yVal = (1.0f - b - k) / (1.0f - k + 1e-9f);
                
                int index = (y * w + x) * 4;
                cmykData[index] = (byte) (Math.max(0, Math.min(1, c)) * 255);
                cmykData[index + 1] = (byte) (Math.max(0, Math.min(1, m)) * 255);
                cmykData[index + 2] = (byte) (Math.max(0, Math.min(1, yVal)) * 255);
                cmykData[index + 3] = (byte) (Math.max(0, Math.min(1, k)) * 255);
            }
        }
        
        return com.lowagie.text.Image.getInstance(w, h, 4, 8, cmykData);
    }
}

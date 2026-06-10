package com.leo.politicas_de_negocio.documents.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter;
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Convierte documentos de Office a PDF directamente en Java,
 * sin depender del servicio de conversión de OnlyOffice.
 *
 * Soportado:
 *  - DOCX → PDF (Apache POI + fr.opensagres.xwpf.converter.pdf)
 *  - XLSX → PDF (Apache POI XSSFWorkbook + OpenPDF tabla)
 *  - PPTX → PDF (Apache POI XMLSlideShow, renderizado por slide → OpenPDF)
 */
@Service
public class DocumentoPdfConverterService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoPdfConverterService.class);

    // Escala de renderizado para slides PPTX (pixels por punto EMU)
    private static final float PPTX_SCALE = 1.5f;

    /**
     * Convierte el contenido de un archivo al formato PDF.
     *
     * @param contenido bytes del archivo original
     * @param fileType  tipo de archivo de entrada (docx, xlsx, pptx)
     * @return bytes del PDF generado
     */
    public byte[] convertirAPdf(byte[] contenido, String fileType) {
        if (contenido == null || contenido.length == 0) {
            throw new IllegalArgumentException("El contenido del documento está vacío");
        }
        String tipo = fileType == null ? "" : fileType.toLowerCase().trim();
        return switch (tipo) {
            case "docx" -> convertirDocxAPdf(contenido);
            case "xlsx" -> convertirXlsxAPdf(contenido);
            case "pptx" -> convertirPptxAPdf(contenido);
            default -> throw new UnsupportedOperationException(
                    "Tipo de archivo no soportado para conversión a PDF: " + tipo);
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOCX → PDF
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] convertirDocxAPdf(byte[] contenido) {
        log.info("Iniciando conversión DOCX → PDF, entrada: {} bytes", contenido.length);
        try (
                ByteArrayInputStream in = new ByteArrayInputStream(contenido);
                XWPFDocument document = new XWPFDocument(in);
                ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {
            PdfOptions options = PdfOptions.create();
            PdfConverter.getInstance().convert(document, out, options);
            byte[] result = out.toByteArray();
            log.info("DOCX → PDF exitoso, salida: {} bytes", result.length);
            return result;
        } catch (Exception ex) {
            log.error("Error convirtiendo DOCX a PDF", ex);
            throw new RuntimeException("No se pudo convertir el documento Word a PDF: " + ex.getMessage(), ex);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // XLSX → PDF (tabla por hoja)
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] convertirXlsxAPdf(byte[] contenido) {
        log.info("Iniciando conversión XLSX → PDF, entrada: {} bytes", contenido.length);
        try (
                ByteArrayInputStream in = new ByteArrayInputStream(contenido);
                XSSFWorkbook workbook = new XSSFWorkbook(in);
                ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {
            Document pdfDoc = new Document(PageSize.A4.rotate(), 20, 20, 30, 30);
            PdfWriter writer = PdfWriter.getInstance(pdfDoc, out);
            pdfDoc.open();

            Font headerFont = new Font(Font.HELVETICA, 7f, Font.BOLD);
            Font cellFont  = new Font(Font.HELVETICA, 6.5f, Font.NORMAL);
            Font sheetFont = new Font(Font.HELVETICA, 10f, Font.BOLD);

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                Sheet sheet = workbook.getSheetAt(si);
                if (si > 0) {
                    pdfDoc.newPage();
                }

                // Título de la hoja
                Paragraph sheetTitle = new Paragraph(sheet.getSheetName(), sheetFont);
                sheetTitle.setSpacingAfter(6);
                pdfDoc.add(sheetTitle);

                // Determinar rango de columnas con datos
                int firstRow = sheet.getFirstRowNum();
                int lastRow  = sheet.getLastRowNum();
                int firstCol = Integer.MAX_VALUE;
                int lastCol  = Integer.MIN_VALUE;
                for (int r = firstRow; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    if (row.getFirstCellNum() >= 0 && row.getFirstCellNum() < firstCol) {
                        firstCol = row.getFirstCellNum();
                    }
                    if (row.getLastCellNum() > lastCol) {
                        lastCol = row.getLastCellNum() - 1;
                    }
                }
                if (firstCol == Integer.MAX_VALUE) {
                    pdfDoc.add(new Paragraph("(Hoja vacía)", cellFont));
                    continue;
                }

                int numCols = lastCol - firstCol + 1;
                // Limitar columnas excesivas para que quepan en página
                int maxCols = Math.min(numCols, 26);
                float[] widths = new float[maxCols];
                for (int i = 0; i < maxCols; i++) widths[i] = 1f;

                PdfPTable table = new PdfPTable(maxCols);
                table.setWidthPercentage(100);
                table.setWidths(widths);
                table.setSpacingBefore(4);
                table.setKeepTogether(false);

                // Precalcular celdas combinadas
                Map<String, CellRangeAddress> mergedMap = buildMergedMap(sheet);

                int rowCount = 0;
                for (int r = firstRow; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    boolean isHeaderRow = (r == firstRow);
                    for (int c = firstCol; c < firstCol + maxCols; c++) {
                        String value = "";
                        if (row != null) {
                            Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                            if (cell != null) {
                                try {
                                    value = formatter.formatCellValue(cell, evaluator);
                                } catch (Exception ignored) {
                                    value = cell.toString();
                                }
                            }
                        }
                        PdfPCell pdfCell = new PdfPCell(new Phrase(value, isHeaderRow ? headerFont : cellFont));
                        pdfCell.setPadding(2);
                        pdfCell.setBorderWidth(0.3f);
                        if (isHeaderRow) {
                            pdfCell.setBackgroundColor(new java.awt.Color(220, 230, 241));
                        } else if (rowCount % 2 == 0) {
                            pdfCell.setBackgroundColor(new java.awt.Color(245, 245, 245));
                        }
                        table.addCell(pdfCell);
                    }
                    rowCount++;

                    // Limitar filas para no generar PDFs gigantes
                    if (rowCount >= 2000) {
                        log.warn("XLSX tiene más de 2000 filas, se truncará el PDF en la hoja '{}'", sheet.getSheetName());
                        break;
                    }
                }
                pdfDoc.add(table);
            }

            pdfDoc.close();
            byte[] result = out.toByteArray();
            log.info("XLSX → PDF exitoso, salida: {} bytes", result.length);
            return result;
        } catch (Exception ex) {
            log.error("Error convirtiendo XLSX a PDF", ex);
            throw new RuntimeException("No se pudo convertir el archivo Excel a PDF: " + ex.getMessage(), ex);
        }
    }

    private Map<String, CellRangeAddress> buildMergedMap(Sheet sheet) {
        Map<String, CellRangeAddress> map = new HashMap<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                    map.put(r + ":" + c, region);
                }
            }
        }
        return map;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PPTX → PDF (render slide → imagen → página PDF)
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] convertirPptxAPdf(byte[] contenido) {
        log.info("Iniciando conversión PPTX → PDF, entrada: {} bytes", contenido.length);
        // Modo headless para entornos de servidor sin pantalla
        System.setProperty("java.awt.headless", "true");
        try (
                ByteArrayInputStream in = new ByteArrayInputStream(contenido);
                XMLSlideShow pptx = new XMLSlideShow(in);
                ByteArrayOutputStream out = new ByteArrayOutputStream()
        ) {
            Dimension pageSize = pptx.getPageSize();
            int slideW = (int) (pageSize.width  * PPTX_SCALE);
            int slideH = (int) (pageSize.height * PPTX_SCALE);

            // Página PDF con tamaño proporcional al slide
            com.lowagie.text.Rectangle pdfPage = new com.lowagie.text.Rectangle(slideW, slideH);
            Document pdfDoc = new Document(pdfPage, 0, 0, 0, 0);
            PdfWriter pdfWriter = PdfWriter.getInstance(pdfDoc, out);
            pdfDoc.open();

            for (XSLFSlide slide : pptx.getSlides()) {
                // Renderizar slide a BufferedImage
                BufferedImage img = new BufferedImage(slideW, slideH, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = img.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                // Fondo blanco
                g2.setColor(java.awt.Color.WHITE);
                g2.fillRect(0, 0, slideW, slideH);
                g2.scale(PPTX_SCALE, PPTX_SCALE);
                slide.draw(g2);
                g2.dispose();

                // Convertir imagen a bytes PNG
                ByteArrayOutputStream imgOut = new ByteArrayOutputStream();
                ImageIO.write(img, "PNG", imgOut);

                // Insertar imagen como página PDF
                com.lowagie.text.Image pdfImg = com.lowagie.text.Image.getInstance(imgOut.toByteArray());
                pdfImg.setAbsolutePosition(0, 0);
                pdfImg.scaleToFit(slideW, slideH);
                pdfDoc.newPage();
                pdfDoc.add(pdfImg);
            }

            pdfDoc.close();
            byte[] result = out.toByteArray();
            log.info("PPTX → PDF exitoso ({} slides), salida: {} bytes", pptx.getSlides().size(), result.length);
            return result;
        } catch (Exception ex) {
            log.error("Error convirtiendo PPTX a PDF", ex);
            throw new RuntimeException("No se pudo convertir el archivo PowerPoint a PDF: " + ex.getMessage(), ex);
        }
    }
}

package com.leo.politicas_de_negocio.reportes.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.leo.politicas_de_negocio.reportes.dto.PreviewResponseDto;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReporteExportadorService {

    public byte[] exportarExcel(PreviewResponseDto preview) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte");

            if (preview.getFilas().isEmpty()) {
                workbook.write(out);
                return out.toByteArray();
            }

            // Encabezados (filtrando columnas técnicas)
            List<String> keys = preview.getFilas().get(0).keySet().stream()
                    .filter(k -> !k.startsWith("_"))
                    .toList();
            int colIdx = 0;
            Row headerRow = sheet.createRow(0);
            for (String key : keys) {
                headerRow.createCell(colIdx++).setCellValue(key);
            }

            // Datos
            int rowIdx = 1;
            for (Map<String, Object> fila : preview.getFilas()) {
                Row row = sheet.createRow(rowIdx++);
                colIdx = 0;
                for (String key : keys) {
                    Object val = fila.get(key);
                    row.createCell(colIdx++).setCellValue(val != null ? val.toString() : "");
                }
            }

            if (Boolean.TRUE.equals(preview.getAsistido())) {
                rowIdx++; // Fila en blanco
                Row footnoteRow = sheet.createRow(rowIdx++);
                footnoteRow.createCell(0).setCellValue("Nota: Este reporte contiene asistencia extendida (IA+) con estimaciones plausibles y coherentes.");
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error exportando a Excel", e);
        }
    }

    public byte[] exportarPdf(PreviewResponseDto preview) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph(preview.getInterpretacion().getTitulo(), titleFont);
            title.setSpacingAfter(20f);
            document.add(title);

            Paragraph desc = new Paragraph("Entidad Principal: " + preview.getInterpretacion().getEntidadPrincipal());
            desc.setSpacingAfter(20f);
            document.add(desc);

            if (preview.getFilas() != null && !preview.getFilas().isEmpty()) {
                List<String> keys = preview.getFilas().get(0).keySet().stream()
                        .filter(k -> !k.startsWith("_"))
                        .toList();
                PdfPTable table = new PdfPTable(keys.size());

                for (String key : keys) {
                    PdfPCell header = new PdfPCell(new Phrase(key));
                    table.addCell(header);
                }

                for (Map<String, Object> fila : preview.getFilas()) {
                    for (String key : keys) {
                        Object val = fila.get(key);
                        table.addCell(val != null ? val.toString() : "");
                    }
                }
                document.add(table);
            }

            if (Boolean.TRUE.equals(preview.getAsistido())) {
                Paragraph footnote = new Paragraph("Nota: Este reporte contiene asistencia extendida (IA+) con estimaciones plausibles y coherentes.", new Font(Font.HELVETICA, 9, Font.ITALIC));
                footnote.setSpacingBefore(15f);
                document.add(footnote);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error exportando a PDF", e);
        }
    }

    public byte[] exportarWord(PreviewResponseDto preview) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            XWPFRun run = title.createRun();
            run.setText(preview.getInterpretacion().getTitulo());
            run.setBold(true);
            run.setFontSize(16);

            XWPFParagraph desc = document.createParagraph();
            XWPFRun runDesc = desc.createRun();
            runDesc.setText("Entidad Principal: " + preview.getInterpretacion().getEntidadPrincipal());

            if (preview.getFilas() != null && !preview.getFilas().isEmpty()) {
                List<Map<String, Object>> resultados = preview.getFilas();
                List<String> keys = resultados.get(0).keySet().stream()
                        .filter(k -> !k.startsWith("_"))
                        .toList();
                XWPFTable table = document.createTable(resultados.size() + 1, keys.size());

                int colIdx = 0;
                for (String key : keys) {
                    table.getRow(0).getCell(colIdx++).setText(key);
                }

                int rowIdx = 1;
                for (Map<String, Object> fila : resultados) {
                    colIdx = 0;
                    for (String key : keys) {
                        Object val = fila.get(key);
                        table.getRow(rowIdx).getCell(colIdx++).setText(val != null ? val.toString() : "");
                    }
                    rowIdx++;
                }
            }

            if (Boolean.TRUE.equals(preview.getAsistido())) {
                XWPFParagraph footnote = document.createParagraph();
                footnote.setSpacingBefore(200); // 200 twentieths of a point
                XWPFRun runFootnote = footnote.createRun();
                runFootnote.setText("Nota: Este reporte contiene asistencia extendida (IA+) con estimaciones plausibles y coherentes.");
                runFootnote.setItalic(true);
                runFootnote.setFontSize(9);
            }

            document.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error exportando a Word", e);
        }
    }
}

package com.pacs.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigInteger;

@Service
public class Servicepacs {

    private ITesseract tesseract;

    public Servicepacs() {
        tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("eng");
    }

    // Parcours du répertoire
    public void processDirectory(String inputDir, String outputDir) {
        File dir = new File(inputDir);
        if (!dir.exists() || !dir.isDirectory()) return;

        File outDir = new File(outputDir);
        if (!outDir.exists()) outDir.mkdirs();

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf")
                || name.toLowerCase().matches(".*\\.(png|jpg|jpeg)"));
        if (files == null) return;

        for (File file : files) {
            System.out.println("Traitement : " + file.getName());
            String text = "";
            try {
                if (file.getName().toLowerCase().endsWith(".pdf")) {
                    text = extractTextFromPDF(file, 300);
                } else {
                    text = tesseract.doOCR(file);
                }
            } catch (Exception e) {
                System.out.println("Erreur OCR sur " + file.getName() + " : " + e.getMessage());
                e.printStackTrace();
            }

            try {
                String safeName = file.getName().replaceAll("\\.[^.]+$", ".docx");
                String outputFile = new File(outDir, safeName).getAbsolutePath();
                createWordDocument(outputFile, text);
                System.out.println("Généré : " + outputFile);
            } catch (Exception e) {
                System.out.println("Erreur génération Word : " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // OCR sur PDF
    private String extractTextFromPDF(File file, int dpi) throws IOException, TesseractException {
        PDDocument document = PDDocument.load(file);
        PDFRenderer pdfRenderer = new PDFRenderer(document);
        StringBuilder sb = new StringBuilder();
        int pages = document.getNumberOfPages();
        for (int page = 0; page < pages; page++) {
            BufferedImage image = pdfRenderer.renderImageWithDPI(page, dpi > 0 ? dpi : 300, ImageType.RGB);
            String result = tesseract.doOCR(image);
            sb.append(result).append("\n");
        }
        document.close();

        return sb.toString().replaceAll("\\r|\\n", "");
    }

    // === DOCUMENT WORD AVEC STYLE AMÉLIORÉ ===
    private void createWordDocument(String outputFile, String text) throws IOException {

        XWPFDocument doc = new XWPFDocument();

        // --- Titre ---
        XWPFParagraph pTitle = doc.createParagraph();
        pTitle.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = pTitle.createRun();
        run.setBold(true);
        run.setFontSize(16);
        run.setText("Banque Al Wava Mauritanie - Swift Reçu");
        run.addBreak();

        // --- TABLEAU ---
        XWPFTable table = doc.createTable();

        // Ajuster la largeur du tableau
        CTTblWidth width = table.getCTTbl().addNewTblPr().addNewTblW();
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf(9000));

        // Bordures épaisses
        CTTblBorders borders = table.getCTTbl().getTblPr().addNewTblBorders();

        borders.addNewInsideH().setVal(STBorder.SINGLE);
        borders.getInsideH().setSz(BigInteger.valueOf(20));

        borders.addNewInsideV().setVal(STBorder.SINGLE);
        borders.getInsideV().setSz(BigInteger.valueOf(20));

        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.getTop().setSz(BigInteger.valueOf(20));

        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.getBottom().setSz(BigInteger.valueOf(20));

        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.getLeft().setSz(BigInteger.valueOf(20));

        borders.addNewRight().setVal(STBorder.SINGLE);
        borders.getRight().setSz(BigInteger.valueOf(20));


        // Ligne d'en-tête stylée
        XWPFTableRow headerRow = table.getRow(0);
        styleHeaderCell(headerRow.getCell(0), "Champ");
        styleHeaderCell(headerRow.addNewTableCell(), "Valeur");

        boolean isPacs008 = text.toLowerCase().contains("pacs.008") || text.toLowerCase().contains("pacs008");

        if (isPacs008) {
            addRow(table, "Message ID", extractTagPermissive(text, "head", "BizMsgIdr"));
            addRow(table, "End To End", extractTagPermissive(text, "pacs", "EndToEndId"));
            addRow(table, "UETR", extractTagPermissive(text, "pacs", "UETR"));
            addRow(table, "Montant", extractAmount(text));
            addRow(table, "Date valeur", extractTagPermissive(text, "pacs", "IntrBkSttlmDt"));
            addRow(table, "Frais", extractTagPermissive(text, "pacs", "ChrgBr"));
            addRow(table, "Donneur d'ordre nom", extractTagPermissive(text, "pacs", "Nm"));
            addRow(table, "Adresse", extractAddress(text));
            addRow(table, "IBAN Donneur", extractIBAN(text, "DbtrAcct"));
            addRow(table, "BIC Donneur", extractBIC(text, "DbtrAgt"));
            addRow(table, "IBAN Bénéficiaire", extractIBAN(text, "CdtrAcct"));
            addRow(table, "BIC Bénéficiaire", extractBIC(text, "CdtrAgt"));
            addRow(table, "Remittance info", extractRemittance(text));
        } else {
            addRow(table, "Message ID", extractSimpleTag(text, "BizMsgIdr"));
            addRow(table, "End To End", extractSimpleTag(text, "EndToEndId"));
            addRow(table, "UETR", extractSimpleTag(text, "UETR"));
            addRow(table, "Montant", extractAmountNonPacs(text));
            addRow(table, "Date valeur", extractSimpleTag(text, "IntrBkSttlmDt"));

            addRow(table, "Donneur d'ordre BIC", extractDebtorBIC(text));
            addRow(table, "Donneur d'ordre agt", extractDebtorAgtBIC(text));
            addRow(table, "Bénéficiaire agt", extractCreditorAgtBIC(text));
            addRow(table, "BIC", extractCreditorBIC(text));
            addRow(table, "Compte", extractSimpleTag(text, "CdtrAcct"));
        }

        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            doc.write(out);
        }
        doc.close();
    }

    // Style cellule en-tête
    private void styleHeaderCell(XWPFTableCell cell, String text) {
        cell.setColor("D9D9D9");
        XWPFParagraph p = cell.getParagraphs().get(0);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(12);
        r.setText(text);
        p.setAlignment(ParagraphAlignment.CENTER);
    }

    // Style des lignes normales
    private void addRow(XWPFTable table, String champ, String valeur) {
        XWPFTableRow row = table.createRow();

        XWPFTableCell c1 = row.getCell(0);
        c1.setText(champ);
        c1.getParagraphs().get(0).setAlignment(ParagraphAlignment.LEFT);

        XWPFTableCell c2 = row.getCell(1);
        c2.setText(valeur != null ? valeur : "");
        c2.getParagraphs().get(0).setAlignment(ParagraphAlignment.LEFT);
    }

    // --- EXTRACTIONS PACS.008 ---
    private String extractTagPermissive(String text, String prefix, String tagName) {
        if (text == null || tagName == null) return "";
        String startPattern = prefix.isEmpty() ? "<\\s*" + tagName + "[^>]*>" : "<\\s*" + prefix + "[:]?\\s*" + tagName + "[^>]*>";
        String endPattern = prefix.isEmpty() ? "</\\s*" + tagName + "\\s*>" : "</\\s*" + prefix + "[:]?\\s*" + tagName + "\\s*>";
        Pattern pattern = Pattern.compile(startPattern + "(.*?)" + endPattern, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).replaceAll("<[^>]+>", "").trim() : "";
    }

    private String extractAddress(String text) {
        String block = extractTagPermissive(text, "pacs", "PstlAdr");
        if (block == null || block.isEmpty()) return "";
        String strt = extractTagPermissive(block, "pacs", "StrtNm");
        String adrLine = extractTagPermissive(block, "pacs", "AdrLine");
        String pst = extractTagPermissive(block, "pacs", "PstCd");
        String city = extractTagPermissive(block, "pacs", "TwnNm");
        String ctry = extractTagPermissive(block, "pacs", "Ctry");

        StringBuilder result = new StringBuilder();
        if (!strt.isEmpty()) result.append(strt).append("\n");
        if (!adrLine.isEmpty()) result.append(adrLine).append("\n");
        if (!pst.isEmpty() || !city.isEmpty()) result.append((pst + " " + city).trim()).append("\n");
        if (!ctry.isEmpty()) result.append(ctry).append("\n");
        return result.toString().trim();
    }

    private String extractAmount(String text) {
        Pattern p = Pattern.compile(
                "<\\s*pacs:IntrBkSttlmAmt[^>]*ccy\\s*=\\s*\"([A-Za-z]{3})\"[^>]*>\\s*([0-9\\., ]+)\\s*</\\s*pacs:IntrBkSttlmAmt\\s*>",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(text);
        if (m.find()) {
            String ccy = m.group(1).trim();
            String montant = m.group(2).replaceAll("\\s+", "");
            return montant + " " + ccy;
        }
        return "";
    }

    private String extractIBAN(String text, String parentTag) {
        String parent = extractTagPermissive(text, "pacs", parentTag);
        String idBlock = extractTagPermissive(parent, "pacs", "Id");
        return extractTagPermissive(idBlock, "pacs", "IBAN");
    }

    private String extractBIC(String text, String parentTag) {
        String parent = extractTagPermissive(text, "pacs", parentTag);
        String finInst = extractTagPermissive(parent, "pacs", "FinInstnId");
        return extractTagPermissive(finInst, "pacs", "BICFI");
    }

    private String extractRemittance(String text) {
        String block = extractTagPermissive(text, "pacs", "RmtInf");
        return extractTagPermissive(block, "pacs", "Ustrd");
    }

    // --- Non PACS.008 ---
    private String extractSimpleTag(String text, String tagName) {
        if (text == null || tagName == null) return "";
        Pattern pattern = Pattern.compile("<\\s*" + tagName + "[^>]*>(.*?)</\\s*" + tagName + "\\s*>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).replaceAll("<[^>]+>", "").trim() : "";
    }

    private String extractAmountNonPacs(String text) {
        Pattern p = Pattern.compile("<IntrBkSttlmAmt[^>]*ccy\\s*=\\s*\"([A-Za-z]{3})\"[^>]*>([0-9\\., ]+)</IntrBkSttlmAmt>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String ccy = m.group(1).trim();
            String montant = m.group(2).replaceAll("\\s+", "");
            return montant + " " + ccy;
        }
        return "";
    }

    private String extractDebtorBIC(String text) {
        String dbtr = extractSimpleTag(text, "Dbtr");
        String dbtrAgt = extractSimpleTag(dbtr, "FinInstnId");
        return extractSimpleTag(dbtrAgt, "BICFI");
    }

    private String extractDebtorAgtBIC(String text) {
        String dbtrAgt = extractSimpleTag(text, "DbtrAgt");
        String finInst = extractSimpleTag(dbtrAgt, "FinInstnId");
        return extractSimpleTag(finInst, "BICFI");
    }

    private String extractCreditorAgtBIC(String text) {
        String cdtrAgt = extractSimpleTag(text, "CdtrAgt");
        String finInst = extractSimpleTag(cdtrAgt, "FinInstnId");
        return extractSimpleTag(finInst, "BICFI");
    }

    private String extractCreditorBIC(String text) {
        String cdtr = extractSimpleTag(text, "Cdtr");
        String finInst = extractSimpleTag(cdtr, "FinInstnId");
        return extractSimpleTag(finInst, "BICFI");
    }
}

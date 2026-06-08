package com.eliza.aicompetition.common;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts plain text from binary file content (DOCX, PDF, XLSX)
 * stored in the file_asset.file_blob column, for LLM consumption.
 */
@Component
public class FileTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(FileTextExtractor.class);

    /** Safety cap — prevents huge extracted text from blowing up the LLM prompt. */
    private static final int CHAR_LIMIT = 100_000;

    private final Parser parser = new AutoDetectParser(TikaConfig.getDefaultConfig());
    private final PdfOcrExtractor pdfOcrExtractor;

    public FileTextExtractor(PdfOcrExtractor pdfOcrExtractor) {
        this.pdfOcrExtractor = pdfOcrExtractor;
    }

    /**
     * Extract plain text from binary file bytes.
     * For PDF files, falls back to OCR if Tika/PdfBox cannot extract text.
     *
     * @param fileBytes     the LONGBLOB content from file_asset.file_blob
     * @param fileExtension file extension without dot (e.g. "docx", "pdf", "xlsx"), may be null
     * @return extracted text, or an error-description string if extraction fails;
     *         never returns null
     */
    public String extractText(byte[] fileBytes, String fileExtension) {
        if (fileBytes == null || fileBytes.length == 0) {
            return "[File is empty]";
        }

        // 1. Try Tika text extraction first
        String tikaText = extractWithTika(fileBytes, fileExtension);

        // 2. If Tika failed and it's a PDF, try OCR fallback
        if (isExtractionFailure(tikaText) && "pdf".equalsIgnoreCase(fileExtension)) {
            log.info("Tika failed to extract text from PDF, attempting OCR fallback...");
            String ocrText = pdfOcrExtractor.ocrPdf(fileBytes);
            if (ocrText != null && !ocrText.isBlank()) {
                log.info("OCR fallback succeeded: {} chars extracted", ocrText.length());
                return ocrText;
            }
            log.warn("OCR fallback also failed — PDF may be a scanned image with poor quality");
        }

        return tikaText;
    }

    /**
     * Core Tika extraction.
     */
    private String extractWithTika(byte[] fileBytes, String fileExtension) {
        try (InputStream input = new ByteArrayInputStream(fileBytes);
             TikaInputStream tikaInput = TikaInputStream.get(input)) {

            Metadata metadata = new Metadata();
            if (fileExtension != null && !fileExtension.isBlank()) {
                metadata.set(Metadata.CONTENT_TYPE, guessMimeType(fileExtension));
            }

            ContentHandler handler = new BodyContentHandler(CHAR_LIMIT);
            ParseContext context = new ParseContext();
            parser.parse(tikaInput, handler, metadata, context);

            String text = handler.toString().trim();
            if (text.isEmpty()) {
                return "[No extractable text found in file]";
            }
            return text;

        } catch (TikaException | IOException | SAXException e) {
            log.warn("Text extraction failed for .{} file: {}", fileExtension, e.getMessage());
            return "[Text extraction failed: " + e.getMessage() + "]";
        }
    }

    /**
     * Check if Tika extraction produced a failure placeholder rather than real content.
     */
    private boolean isExtractionFailure(String text) {
        return text.startsWith("[No extractable text")
            || text.startsWith("[Text extraction failed")
            || text.startsWith("[File is empty");
    }

    private String guessMimeType(String ext) {
        return switch (ext.toLowerCase()) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc"  -> "application/msword";
            case "pdf"  -> "application/pdf";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls"  -> "application/vnd.ms-excel";
            case "txt"  -> "text/plain";
            default     -> "application/octet-stream";
        };
    }
}

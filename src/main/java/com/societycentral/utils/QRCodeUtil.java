package com.societycentral.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Utility class for generating QR code images.
 *
 * Used by RSVPTicketService to embed a scannable QR code
 * in the PDF event ticket.
 *
 * The QR code content is the RSVP reference string which uniquely
 * identifies the student's RSVP record. When scanned at the door,
 * the backend resolves the student, event, and society from this reference.
 */
public final class QRCodeUtil {

    private QRCodeUtil() {
        // Utility class - no instantiation
    }

    /**
     * Default QR code size in pixels (width and height).
     */
    private static final int DEFAULT_SIZE = 300;

    /**
     * Generates a QR code image as a PNG byte array.
     *
     * @param content the text to encode in the QR code (RSVP reference)
     * @param size    pixel width and height of the output image
     * @return PNG image bytes
     */
    public static byte[] generateQRCodeImage(String content, int size) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("QR code content cannot be empty.");
        }

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            // Use dark navy colour for the QR modules to match branding
            int onColor = 0xFF1B2B4B;   // navy
            int offColor = 0xFFFFFFFF;   // white

            MatrixToImageConfig config = new MatrixToImageConfig(onColor, offColor);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix, config);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", outputStream);

            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code image.", e);
        }
    }

    /**
     * Generates a QR code image with the default size (300x300 pixels).
     *
     * @param content the text to encode in the QR code
     * @return PNG image bytes
     */
    public static byte[] generateQRCodeImage(String content) {
        return generateQRCodeImage(content, DEFAULT_SIZE);
    }

    /**
     * Generates a QR code as a BufferedImage (for embedding directly in PDFs).
     *
     * @param content the text to encode in the QR code
     * @param size    pixel width and height
     * @return BufferedImage of the QR code
     */
    public static BufferedImage generateQRCodeBufferedImage(String content, int size) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("QR code content cannot be empty.");
        }

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            int onColor = 0xFF1B2B4B;
            int offColor = 0xFFFFFFFF;

            MatrixToImageConfig config = new MatrixToImageConfig(onColor, offColor);
            return MatrixToImageWriter.toBufferedImage(bitMatrix, config);
        } catch (WriterException e) {
            throw new RuntimeException("Failed to generate QR code image.", e);
        }
    }
}

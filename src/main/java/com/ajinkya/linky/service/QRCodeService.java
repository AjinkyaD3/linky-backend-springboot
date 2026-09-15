package com.ajinkya.linky.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class QRCodeService {

    public byte[] generateQRCodeImage(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);

            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Could not generate QR code", e);
        }
    }

    public String generateQRCodeSvg(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, 0, 0);
            
            int matrixWidth = bitMatrix.getWidth();
            int matrixHeight = bitMatrix.getHeight();
            
            StringBuilder svgBuilder = new StringBuilder();
            svgBuilder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ");
            svgBuilder.append("width=\"").append(width).append("\" ");
            svgBuilder.append("height=\"").append(height).append("\" ");
            svgBuilder.append("viewBox=\"0 0 ").append(matrixWidth).append(" ").append(matrixHeight).append("\">\n");
            
            svgBuilder.append("<rect width=\"").append(matrixWidth).append("\" height=\"").append(matrixHeight).append("\" fill=\"#ffffff\"/>\n");
            svgBuilder.append("<path d=\"");
            for (int y = 0; y < matrixHeight; y++) {
                for (int x = 0; x < matrixWidth; x++) {
                    if (bitMatrix.get(x, y)) {
                        svgBuilder.append("M").append(x).append(",").append(y).append("h1v1h-1z ");
                    }
                }
            }
            svgBuilder.append("\" fill=\"#000000\"/>\n");
            svgBuilder.append("</svg>");
            
            return svgBuilder.toString();
        } catch (WriterException e) {
            throw new RuntimeException("Could not generate QR code SVG", e);
        }
    }
}

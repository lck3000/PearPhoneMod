package com.pearphone.mod.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class TextureGenerator {
    
    /**
     * Generate a texture for the audio player block
     * 16x16 pixel texture with speaker-like appearance
     */
    public static void generateAudioPlayerBlockTexture(String outputPath) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        
        // Fill base with dark gray
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(new Color(80, 80, 80));
        g2d.fillRect(0, 0, 16, 16);
        
        // Add border
        g2d.setColor(new Color(40, 40, 40));
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawRect(1, 1, 14, 14);
        
        // Draw speaker icon (triangle)
        g2d.setColor(new Color(200, 200, 200));
        int[] xPoints = {6, 6, 10};
        int[] yPoints = {4, 12, 8};
        g2d.fillPolygon(xPoints, yPoints, 3);
        
        // Draw sound waves
        g2d.setColor(new Color(150, 200, 255));
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawArc(11, 6, 3, 3, 315, 90);
        g2d.drawArc(12, 5, 4, 5, 315, 90);
        
        g2d.dispose();
        
        try {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            ImageIO.write(image, "PNG", outputFile);
            System.out.println("Generated audio player block texture: " + outputPath);
        } catch (IOException e) {
            System.err.println("Failed to generate texture: " + e.getMessage());
        }
    }
    
    /**
     * Generate a texture for the pearphone item
     * 16x16 pixel texture resembling a pearphone
     */
    public static void generatePearPhoneTexture(String outputPath) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        // Fill background - silver/gray device
        g2d.setColor(new Color(180, 180, 180));
        g2d.fillRoundRect(2, 2, 12, 12, 2, 2);
        
        // Device border
        g2d.setColor(new Color(100, 100, 100));
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawRoundRect(2, 2, 12, 12, 2, 2);
        
        // Screen/Display area
        g2d.setColor(new Color(50, 50, 50));
        g2d.fillRect(4, 4, 8, 5);
        
        // Screen border
        g2d.setColor(new Color(100, 100, 100));
        g2d.drawRect(4, 4, 8, 5);
        
        // Control buttons (three dots)
        g2d.setColor(new Color(100, 150, 255));
        g2d.fillOval(5, 11, 2, 2);
        g2d.fillOval(7, 11, 2, 2);
        g2d.fillOval(9, 11, 2, 2);
        
        // Indicator light
        g2d.setColor(new Color(0, 255, 0));
        g2d.fillOval(13, 3, 2, 2);
        
        g2d.dispose();
        
        try {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            ImageIO.write(image, "PNG", outputFile);
            System.out.println("Generated pearphone item texture: " + outputPath);
        } catch (IOException e) {
            System.err.println("Failed to generate texture: " + e.getMessage());
        }
    }
    
    /**
     * Generate GUI background texture
     * 256x220 pixel GUI background
     */
    public static void generateGuiTexture(String outputPath) {
        BufferedImage image = new BufferedImage(256, 220, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        // Background
        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(60, 60, 60),
                0, 220, new Color(40, 40, 40)
        );
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, 256, 220);
        
        // Border
        g2d.setColor(new Color(100, 100, 100));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRect(1, 1, 254, 218);
        
        // Inner border
        g2d.setColor(new Color(80, 80, 80));
        g2d.drawRect(2, 2, 252, 216);
        
        g2d.dispose();
        
        try {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            ImageIO.write(image, "PNG", outputFile);
            System.out.println("Generated GUI texture: " + outputPath);
        } catch (IOException e) {
            System.err.println("Failed to generate texture: " + e.getMessage());
        }
    }
    
    /**
     * Generate the mod logo (64x64) used as the mod icon in launchers.
     * Output: src/main/resources/pearphone.png (referenced by neoforge.mods.toml logoFile)
     */
    public static void generateLogo(String outputPath) {
        int size = 64;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background circle
        g2d.setColor(new Color(30, 30, 35));
        g2d.fillOval(2, 2, size - 4, size - 4);

        // Outer ring
        g2d.setColor(new Color(100, 150, 255));
        g2d.setStroke(new BasicStroke(2.5f));
        g2d.drawOval(2, 2, size - 4, size - 4);

        // Phone body
        g2d.setColor(new Color(200, 200, 210));
        g2d.fillRoundRect(18, 12, 28, 40, 5, 5);

        // Phone border
        g2d.setColor(new Color(120, 120, 130));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(18, 12, 28, 40, 5, 5);

        // Screen
        g2d.setColor(new Color(20, 25, 40));
        g2d.fillRect(21, 16, 22, 28);

        // Music note (two eighth notes with beam)
        g2d.setColor(new Color(100, 180, 255));
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawLine(26, 22, 26, 30);
        g2d.fillOval(23, 29, 5, 4);
        g2d.drawLine(32, 20, 32, 28);
        g2d.fillOval(29, 27, 5, 4);
        g2d.drawLine(26, 22, 32, 20);

        // Home button
        g2d.setColor(new Color(150, 150, 160));
        g2d.fillOval(28, 47, 8, 5);

        g2d.dispose();

        try {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            ImageIO.write(image, "PNG", outputFile);
            System.out.println("Generated logo: " + outputPath);
        } catch (IOException e) {
            System.err.println("Failed to generate logo: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String blockTexturePath = "src/main/resources/assets/pearphone/textures/block/audio_player.png";
        String itemTexturePath = "src/main/resources/assets/pearphone/textures/item/pearphone.png";
        String guiTexturePath = "src/main/resources/assets/pearphone/textures/gui/audio_player_gui.png";
        String logoPath = "src/main/resources/pearphone.png";

        generateAudioPlayerBlockTexture(blockTexturePath);
        generatePearPhoneTexture(itemTexturePath);
        generateGuiTexture(guiTexturePath);
        generateLogo(logoPath);

        System.out.println("All textures generated successfully!");
    }
}

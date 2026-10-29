package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * Turns an uploaded product photo into a picture the shop can safely show (ADR-0017). Plain JDK, no Spring.
 * <ol>
 *   <li>Only JPEG and PNG, recognised by their first bytes (the file name and the browser's content type are not
 *       trusted). SVG is refused: it can carry scripts. GIF, WebP and HEIC are refused here; the admin page converts
 *       them in the browser first.</li>
 *   <li>The size is read from the header before any pixel is decoded: at most {@value #MAX_PIXELS} pixels (a
 *       "decompression bomb" of a few kB that would unpack to gigabytes is refused), at least
 *       {@value #MIN_EDGE} px on the short side.</li>
 *   <li>Big photos are decoded at a reduced resolution (subsampling), then turned upright (a phone's EXIF
 *       orientation) and scaled to at most {@value #MAX_EDGE} px on the long side.</li>
 *   <li>The picture is drawn again and saved as a new file: JPEG, or PNG when it is really transparent. Nothing of
 *       the upload survives except the pixels - no EXIF (a phone photo's GPS position), no comments, no hidden data
 *       after the image.</li>
 * </ol>
 * The name of the result is the SHA-256 of its bytes, so the same picture uploaded twice is stored once and its
 * address never changes content (it can be cached for a year).
 */
public class PictureProcessor {

    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final long MAX_PIXELS = 50_000_000L;
    public static final int MIN_EDGE = 200;
    public static final int MAX_EDGE = 1600;
    static final float JPEG_QUALITY = 0.86f;

    static final String EMPTY = "The picture file is empty";
    static final String TOO_BIG = "The picture is larger than 10 MB";
    static final String NOT_JPEG_OR_PNG = "Only JPEG or PNG pictures can be uploaded";
    static final String UNREADABLE = "The picture cannot be read (damaged file or an unsupported kind of JPEG/PNG)";
    static final String TOO_MANY_PIXELS = "The picture has more than 50 megapixels";
    static final String TOO_SMALL = "The picture must be at least 200 pixels on its short side";

    /** The stored picture: bytes, their type and the name (SHA-256 of the bytes). */
    public record Picture(byte[] data, String contentType, String extension, int width, int height, String sha256) {
        public String fileName() {
            return sha256 + "." + extension;
        }
    }

    enum Format { JPEG, PNG }

    public Picture process(byte[] upload) {
        if (upload == null || upload.length == 0) throw new BusinessRuleException(EMPTY);
        if (upload.length > MAX_BYTES) throw new BusinessRuleException(TOO_BIG);
        Format format = sniff(upload);
        if (format == null) throw new BusinessRuleException(NOT_JPEG_OR_PNG);

        BufferedImage image = decode(upload, format);
        if (format == Format.JPEG) image = orient(image, exifOrientation(upload));
        boolean transparent = isTransparent(image);
        image = scaleToFit(normalise(image, transparent), MAX_EDGE);
        byte[] data = transparent ? encodePng(image) : encodeJpeg(image);
        return transparent
                ? new Picture(data, "image/png", "png", image.getWidth(), image.getHeight(), sha256(data))
                : new Picture(data, "image/jpeg", "jpg", image.getWidth(), image.getHeight(), sha256(data));
    }

    /** JPEG starts with FF D8 FF, PNG with its fixed 8-byte signature; anything else is not accepted. */
    static Format sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return Format.JPEG;
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (b.length < png.length) return null;
        for (int i = 0; i < png.length; i++) if (b[i] != png[i]) return null;
        return Format.PNG;
    }

    private static BufferedImage decode(byte[] upload, Format format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format == Format.JPEG ? "jpeg" : "png");
        if (!readers.hasNext()) throw new IllegalStateException("No ImageIO reader for " + format);
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            reader.setInput(in, true, true);                  // ignore metadata: only the pixels are wanted
            long w = reader.getWidth(0);
            long h = reader.getHeight(0);                       // from the header, nothing decoded yet
            if (w <= 0 || h <= 0) throw new BusinessRuleException(UNREADABLE);
            if (w * h > MAX_PIXELS) throw new BusinessRuleException(TOO_MANY_PIXELS);
            if (Math.min(w, h) < MIN_EDGE) throw new BusinessRuleException(TOO_SMALL);
            ImageReadParam param = reader.getDefaultReadParam();
            int step = subsampling(w, h);
            if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
            BufferedImage image = reader.read(0, param);
            if (image == null) throw new BusinessRuleException(UNREADABLE);
            return image;
        } catch (BusinessRuleException e) {
            throw e;
        } catch (IOException | RuntimeException e) {               // damaged data, CMYK JPEG, truncated PNG ...
            throw new BusinessRuleException(UNREADABLE);
        } finally {
            reader.dispose();
        }
    }

    /** Every n-th pixel is enough while the long side stays at least twice the target (then scaled smoothly). */
    static int subsampling(long w, long h) {
        long longEdge = Math.max(w, h);
        return (int) Math.max(1, longEdge / (2L * MAX_EDGE));
    }

    /**
     * EXIF orientation (1-8) of a JPEG, 1 when there is none. Reads only the APP1 "Exif" segment before the image
     * data; every offset is checked, so a damaged or hostile header gives 1, never an exception.
     */
    static int exifOrientation(byte[] b) {
        int i = 2;
        while (i + 4 <= b.length) {
            if ((b[i] & 0xFF) != 0xFF) return 1;
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { i += 2; continue; }
            if (marker == 0xDA || marker == 0xD9) return 1;    // image data starts: no EXIF before it
            int length = u16(b, i + 2, false);
            if (length < 2) return 1;
            if (marker == 0xE1 && length >= 16 && i + 2 + length <= b.length && isExif(b, i + 4)) {
                return tiffOrientation(b, i + 10, i + 2 + length);
            }
            i += 2 + length;
        }
        return 1;
    }

    private static boolean isExif(byte[] b, int at) {
        return b[at] == 'E' && b[at + 1] == 'x' && b[at + 2] == 'i' && b[at + 3] == 'f' && b[at + 4] == 0 && b[at + 5] == 0;
    }

    private static int tiffOrientation(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) return 1;
        boolean little;
        if (b[tiff] == 'I' && b[tiff + 1] == 'I') little = true;
        else if (b[tiff] == 'M' && b[tiff + 1] == 'M') little = false;
        else return 1;
        if (u16(b, tiff + 2, little) != 42) return 1;
        long ifd = u32(b, tiff + 4, little);
        if (ifd < 8 || tiff + ifd + 2 > end) return 1;
        int dir = tiff + (int) ifd;
        int entries = u16(b, dir, little);
        for (int k = 0; k < entries; k++) {
            int e = dir + 2 + 12 * k;
            if (e + 12 > end) return 1;
            if (u16(b, e, little) == 0x0112) {                 // Orientation, type SHORT, value in the entry
                int value = u16(b, e + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int at, boolean little) {
        int x = b[at] & 0xFF;
        int y = b[at + 1] & 0xFF;
        return little ? (y << 8) | x : (x << 8) | y;
    }

    private static long u32(byte[] b, int at, boolean little) {
        long hi = u16(b, at + (little ? 2 : 0), little);
        long lo = u16(b, at + (little ? 0 : 2), little);
        return (hi << 16) | lo;
    }

    /** Turns the picture the way the camera says it was held (EXIF 1-8: mirror and/or quarter turns). */
    static BufferedImage orient(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage dst = new BufferedImage(swap ? h : w, swap ? w : h,
                src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int nx, ny;
                switch (orientation) {
                    case 2 -> { nx = w - 1 - x; ny = y; }            // mirrored
                    case 3 -> { nx = w - 1 - x; ny = h - 1 - y; }    // upside down
                    case 4 -> { nx = x; ny = h - 1 - y; }            // mirrored upside down
                    case 5 -> { nx = y; ny = x; }                    // mirrored, quarter turn
                    case 6 -> { nx = h - 1 - y; ny = x; }            // turned a quarter clockwise
                    case 7 -> { nx = h - 1 - y; ny = w - 1 - x; }    // mirrored, three quarters
                    default -> { nx = y; ny = w - 1 - x; }           // 8: a quarter anticlockwise
                }
                dst.setRGB(nx, ny, row[x]);
            }
        }
        return dst;
    }

    /** True only when at least one pixel is see-through: a PNG with an alpha channel but no transparency is a photo. */
    static boolean isTransparent(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) return false;
        int w = image.getWidth();
        int[] row = new int[w];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int argb : row) if ((argb >>> 24) != 0xFF) return true;
        }
        return false;
    }

    /** RGB on white, or ARGB: the two kinds the encoders below write reliably (grey, indexed, 16-bit all become one). */
    private static BufferedImage normalise(BufferedImage src, boolean transparent) {
        int type = transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (src.getType() == type) return src;
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), type);
        Graphics2D g = dst.createGraphics();
        try {
            if (!transparent) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, dst.getWidth(), dst.getHeight());
            }
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /** At most maxEdge on the long side, never enlarged; halves step by step first, so the result stays sharp. */
    static BufferedImage scaleToFit(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (Math.max(w, h) <= maxEdge) return src;
        double ratio = (double) maxEdge / Math.max(w, h);
        int targetW = Math.max(1, (int) Math.round(w * ratio));
        int targetH = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage current = src;
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return resize(current, targetW, targetH, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h, Object interpolation) {
        BufferedImage dst = new BufferedImage(w, h, src.getType());
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] encodePng(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "png", out)) throw new IllegalStateException("No PNG writer");
        } catch (IOException e) {
            throw new IllegalStateException("PNG encoding failed", e);
        }
        return out.toByteArray();
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

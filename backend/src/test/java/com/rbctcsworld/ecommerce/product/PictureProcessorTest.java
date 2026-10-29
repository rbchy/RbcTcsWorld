package com.rbctcsworld.ecommerce.product;

import com.rbctcsworld.ecommerce.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REQ-CAT-15: an uploaded photo becomes a safe, upright, right-sized picture (ADR-0017). */
public class PictureProcessorTest {

    private final PictureProcessor processor = new PictureProcessor();

    /** Red top-left quarter, blue right half, black bottom-left quarter: shows which way the picture faces. */
    public static byte[] picture(int w, int h, String format, boolean seeThroughCorner) throws IOException {
        BufferedImage b = new BufferedImage(w, h, seeThroughCorner ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = b.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, h / 2);
        g.setColor(Color.BLUE);
        g.fillRect(w / 2, 0, w - w / 2, h);
        g.dispose();
        if (seeThroughCorner) b.setRGB(0, h - 1, 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(b, format, out)).isTrue();
        return out.toByteArray();
    }

    /** A JPEG with an EXIF block that only holds the orientation, as a phone writes it ("II" or "MM" byte order). */
    public static byte[] withOrientation(byte[] jpeg, int orientation, boolean littleEndian) {
        int[] tiff = littleEndian
                ? new int[]{'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 0x01, 3, 0, 1, 0, 0, 0, orientation, 0, 0, 0, 0, 0, 0, 0}
                : new int[]{'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation, 0, 0, 0, 0, 0, 0};
        byte[] exif = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + exif.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8); out.write(0xFF); out.write(0xE1);
        out.write(length >> 8); out.write(length & 0xFF);
        out.writeBytes(exif);
        for (int b : tiff) out.write(b);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static BufferedImage read(PictureProcessor.Picture p) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(p.data()));
    }

    private static boolean isRed(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 200 && c.getGreen() < 60 && c.getBlue() < 60;
    }

    @Test
    void aBigPhotoIsScaledToAtMost1600PixelsKeepingItsShape() throws IOException {
        PictureProcessor.Picture p = processor.process(picture(4000, 3000, "jpg", false));
        assertThat(p.width()).isEqualTo(1600);
        assertThat(p.height()).isEqualTo(1200);
        assertThat(p.contentType()).isEqualTo("image/jpeg");
        assertThat(p.extension()).isEqualTo("jpg");
        BufferedImage stored = read(p);
        assertThat(stored.getWidth()).isEqualTo(1600);
        assertThat(isRed(stored.getRGB(100, 100))).as("still the same picture").isTrue();
    }

    @Test
    void aSmallerPhotoIsNeverEnlarged() throws IOException {
        PictureProcessor.Picture p = processor.process(picture(800, 600, "jpg", false));
        assertThat(p.width()).isEqualTo(800);
        assertThat(p.height()).isEqualTo(600);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aPhoneHeldSidewaysIsTurnedUpright(boolean littleEndian) throws IOException {
        // orientation 6: the camera says "turn a quarter clockwise to show it"
        PictureProcessor.Picture p = processor.process(withOrientation(picture(800, 400, "jpg", false), 6, littleEndian));
        assertThat(p.width()).isEqualTo(400);
        assertThat(p.height()).isEqualTo(800);
        BufferedImage stored = read(p);
        assertThat(isRed(stored.getRGB(p.width() - 10, 10))).as("red quarter moved to the top right").isTrue();
        assertThat(isRed(stored.getRGB(10, 10))).isFalse();
    }

    @Test
    void everyExifOrientationMapsTheTopLeftCornerToTheRightPlace() {
        BufferedImage src = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        src.setRGB(0, 0, 0xFF0000);                                 // the marked top-left pixel
        int[][] expected = {null, {0, 0}, {3, 0}, {3, 1}, {0, 1}, {0, 0}, {1, 0}, {1, 3}, {0, 3}};
        for (int o = 1; o <= 8; o++) {
            BufferedImage out = PictureProcessor.orient(src, o);
            assertThat(out.getWidth()).as("width for %d", o).isEqualTo(o >= 5 ? 2 : 4);
            assertThat(out.getRGB(expected[o][0], expected[o][1]) & 0xFFFFFF).as("orientation %d", o).isEqualTo(0xFF0000);
        }
    }

    @Test
    void theExifHeaderIsReadSafely() throws IOException {
        byte[] jpeg = picture(400, 400, "jpg", false);
        assertThat(PictureProcessor.exifOrientation(jpeg)).as("no EXIF").isEqualTo(1);
        assertThat(PictureProcessor.exifOrientation(withOrientation(jpeg, 8, true))).isEqualTo(8);
        assertThat(PictureProcessor.exifOrientation(withOrientation(jpeg, 9, true))).as("not a valid value").isEqualTo(1);
        byte[] damaged = withOrientation(jpeg, 6, true);
        Arrays.fill(damaged, 12, 30, (byte) 0xEE);
        assertThat(PictureProcessor.exifOrientation(damaged)).isEqualTo(1);
        assertThat(processor.process(damaged).width()).as("a damaged EXIF block does not stop the upload").isEqualTo(400);
    }

    @Test
    void nothingButThePixelsSurvives_noExifInTheStoredFile() throws IOException {
        byte[] stored = processor.process(withOrientation(picture(400, 400, "jpg", false), 1, true)).data();
        for (int i = 0; i + 1 < stored.length; i++) {
            if ((stored[i] & 0xFF) == 0xFF && (stored[i + 1] & 0xFF) == 0xDA) break;   // image data starts
            assertThat((stored[i] & 0xFF) == 0xFF && (stored[i + 1] & 0xFF) == 0xE1).as("APP1 (EXIF) at %d", i).isFalse();
        }
    }

    @Test
    void aTransparentPngStaysPngAnOpaqueOneBecomesJpeg() throws IOException {
        PictureProcessor.Picture cutout = processor.process(picture(600, 600, "png", true));
        assertThat(cutout.contentType()).isEqualTo("image/png");
        assertThat(read(cutout).getColorModel().hasAlpha()).isTrue();
        assertThat(processor.process(picture(600, 600, "png", false)).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void theNameIsTheSha256OfTheStoredBytesSoTheSameUploadGetsTheSameName() throws IOException {
        byte[] upload = picture(500, 500, "png", false);
        PictureProcessor.Picture a = processor.process(upload);
        PictureProcessor.Picture b = processor.process(upload);
        assertThat(a.sha256()).matches("[0-9a-f]{64}").isEqualTo(PictureProcessor.sha256(a.data())).isEqualTo(b.sha256());
        assertThat(a.fileName()).isEqualTo(a.sha256() + ".jpg");
    }

    @Test
    void onlyJpegAndPngAreAccepted_recognisedByTheirBytes() throws IOException {
        assertRefused("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8), PictureProcessor.NOT_JPEG_OR_PNG);
        assertRefused(picture(300, 300, "gif", false), PictureProcessor.NOT_JPEG_OR_PNG);
        assertRefused("RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), PictureProcessor.NOT_JPEG_OR_PNG);
        assertThat(PictureProcessor.sniff(picture(300, 300, "jpg", false))).isEqualTo(PictureProcessor.Format.JPEG);
        assertThat(PictureProcessor.sniff(picture(300, 300, "png", false))).isEqualTo(PictureProcessor.Format.PNG);
    }

    @Test
    void emptyTooBigTooSmallOrDamagedFilesAreRefused() throws IOException {
        assertRefused(new byte[0], PictureProcessor.EMPTY);
        assertRefused(null, PictureProcessor.EMPTY);
        byte[] huge = new byte[PictureProcessor.MAX_BYTES + 1];
        huge[0] = (byte) 0xFF; huge[1] = (byte) 0xD8; huge[2] = (byte) 0xFF;
        assertRefused(huge, PictureProcessor.TOO_BIG);
        assertRefused(picture(150, 300, "png", false), PictureProcessor.TOO_SMALL);
        assertRefused(Arrays.copyOf(picture(800, 600, "jpg", false), 200), PictureProcessor.UNREADABLE);
    }

    @Test
    void aDecompressionBombIsRefusedBeforeAnyPixelIsDecoded() throws IOException {
        byte[] bomb = picture(300, 300, "png", false);
        // the PNG header (IHDR, bytes 16-23) now claims 20000 x 20000 pixels: 400 megapixels
        byte[] size = {0, 0, 0x4E, 0x20, 0, 0, 0x4E, 0x20};
        System.arraycopy(size, 0, bomb, 16, size.length);
        assertRefused(bomb, PictureProcessor.TOO_MANY_PIXELS);
    }

    @Test
    void hugePhotosAreDecodedAtAReducedResolution() {
        assertThat(PictureProcessor.subsampling(3000, 2000)).isEqualTo(1);
        assertThat(PictureProcessor.subsampling(6400, 4800)).isEqualTo(2);
        assertThat(PictureProcessor.subsampling(2000, 9000)).isEqualTo(2);
        assertThat(PictureProcessor.subsampling(12800, 3000)).isEqualTo(4);
    }

    private void assertRefused(byte[] upload, String message) {
        assertThatThrownBy(() -> processor.process(upload)).isInstanceOf(BusinessRuleException.class).hasMessage(message);
    }
}

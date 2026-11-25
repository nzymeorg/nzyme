package app.nzyme.core.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

public class ImageDimensions {

    public static final long MAX_FLOOR_PLAN_PIXELS = 40_000_000L;

    public record Dimensions(int width, int height, String format) {

        public long pixels() {
            return (long) width * height;
        }

        public boolean isFloorPlan() {
            return (format.equals("png") || format.equals("jpeg")) && pixels() <= MAX_FLOOR_PLAN_PIXELS;
        }

    }

    // Reads the header only. Decoding allocates every pixel the file claims to have, whatever size the file is.
    public static Optional<Dimensions> read(byte[] image) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            if (in == null) {
                return Optional.empty();
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return Optional.empty();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    return Optional.empty();
                }

                return Optional.of(new Dimensions(width, height, reader.getFormatName().toLowerCase()));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

}

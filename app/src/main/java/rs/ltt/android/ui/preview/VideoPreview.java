package rs.ltt.android.ui.preview;

import android.graphics.Bitmap;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.ltt.android.ui.PreviewMeasurements;

public class VideoPreview extends ImagePreview {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoPreview.class);

    public static Bitmap getVideoPreview(final File file, final Size size) {
        try (final MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever()) {
            metadataRetriever.setDataSource(file.getAbsolutePath());
            final Bitmap original = metadataRetriever.getFrameAtTime();
            final int width = original.getWidth();
            final int height = original.getHeight();
            final PreviewMeasurements previewMeasurements =
                    PreviewMeasurements.of(width, height, size);
            return cropToMeasurements(original, previewMeasurements);
        }
    }

    private static class MediaMetadataRetriever extends android.media.MediaMetadataRetriever
            implements AutoCloseable {

        @Override
        public void close() {
            try {
                this.release();
            } catch (final IOException e) {
                LOGGER.error("Could not close MediaDataRetriever", e);
            }
        }
    }
}

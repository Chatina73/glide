package com.bumptech.glide.load;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.load.ImageHeaderParser.ImageType;
import com.bumptech.glide.load.data.ParcelFileDescriptorRewinder;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;
import com.bumptech.glide.load.resource.bitmap.RecyclableBufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

/** Utilities for the ImageHeaderParser. */
public final class ImageHeaderParserUtils {
  // 5MB. This is the max image header size we can handle, we preallocate a much smaller buffer but
  // will resize up to this amount if necessary.
  private static final int MARK_READ_LIMIT = 5 * 1024 * 1024;

  private ImageHeaderParserUtils() {}

  /** Returns the ImageType for the given InputStream. */
  @NonNull
  public static ImageType getType(
      @NonNull List<ImageHeaderParser> parsers,
      @Nullable InputStream is,
      @NonNull ArrayPool byteArrayPool)
      throws IOException {
    if (is == null) {
      return ImageType.UNKNOWN;
    }

    if (!is.markSupported()) {
      is = new RecyclableBufferedInputStream(is, byteArrayPool);
    }

    is.mark(MARK_READ_LIMIT);
    InputStream finalIs = is;
    return getTypeInternal(
        parsers,
        parser -> {
          try {
            return parser.getType(finalIs);
          } finally {
            finalIs.reset();
          }
        });
  }

  /** Returns the ImageType for the given ByteBuffer. */
  @NonNull
  public static ImageType getType(
      @NonNull List<ImageHeaderParser> parsers, @Nullable ByteBuffer buffer) throws IOException {
    if (buffer == null) {
      return ImageType.UNKNOWN;
    }

    return getTypeInternal(parsers, parser -> parser.getType(buffer));
  }

  @NonNull
  public static ImageType getType(
      @NonNull List<ImageHeaderParser> parsers,
      @NonNull ParcelFileDescriptorRewinder parcelFileDescriptorRewinder,
      @NonNull ArrayPool byteArrayPool)
      throws IOException {
    return getTypeInternal(
        parsers,
        parser -> {
          // Wrap the FileInputStream into a RecyclableBufferedInputStream to optimize I/O
          // performance.
          InputStream is =
              new RecyclableBufferedInputStream(
                  new FileInputStream(
                      parcelFileDescriptorRewinder.rewindAndGet().getFileDescriptor()),
                  byteArrayPool);
          try {
            return parser.getType(is);
          } finally {
            is.close();
            parcelFileDescriptorRewinder.rewindAndGet();
          }
        });
  }

  @NonNull
  private static ImageType getTypeInternal(
      @NonNull List<ImageHeaderParser> parsers, TypeReader reader) throws IOException {
    //noinspection ForLoopReplaceableByForEach to improve perf
    for (int i = 0, size = parsers.size(); i < size; i++) {
      ImageHeaderParser parser = parsers.get(i);
      ImageType type = reader.getType(parser);
      if (type != ImageType.UNKNOWN) {
        return type;
      }
    }

    return ImageType.UNKNOWN;
  }

  /** Returns the orientation for the given InputStream. */
  public static int getOrientation(
      @NonNull List<ImageHeaderParser> parsers,
      @Nullable InputStream is,
      @NonNull ArrayPool byteArrayPool)
      throws IOException {
    if (is == null) {
      return ImageHeaderParser.UNKNOWN_ORIENTATION;
    }

    if (!is.markSupported()) {
      is = new RecyclableBufferedInputStream(is, byteArrayPool);
    }

    is.mark(MARK_READ_LIMIT);
    InputStream finalIs = is;
    return getOrientationInternal(
        parsers,
        parser -> {
          try {
            return parser.getOrientation(finalIs, byteArrayPool);
          } finally {
            finalIs.reset();
          }
        });
  }

  public static int getOrientation(
      @NonNull List<ImageHeaderParser> parsers,
      @NonNull ParcelFileDescriptorRewinder parcelFileDescriptorRewinder,
      @NonNull ArrayPool byteArrayPool)
      throws IOException {
    return getOrientationInternal(
        parsers,
        parser -> {
          // Wrap the FileInputStream into a RecyclableBufferedInputStream to optimize I/O
          // performance.
          InputStream is =
              new RecyclableBufferedInputStream(
                  new FileInputStream(
                      parcelFileDescriptorRewinder.rewindAndGet().getFileDescriptor()),
                  byteArrayPool);
          try {
            return parser.getOrientation(is, byteArrayPool);
          } finally {
            is.close();
            parcelFileDescriptorRewinder.rewindAndGet();
          }
        });
  }

  private static int getOrientationInternal(
      @NonNull List<ImageHeaderParser> parsers, OrientationReader reader) throws IOException {
    //noinspection ForLoopReplaceableByForEach to improve perf
    for (int i = 0, size = parsers.size(); i < size; i++) {
      ImageHeaderParser parser = parsers.get(i);
      int orientation = reader.getOrientation(parser);
      if (orientation != ImageHeaderParser.UNKNOWN_ORIENTATION) {
        return orientation;
      }
    }

    return ImageHeaderParser.UNKNOWN_ORIENTATION;
  }

  private interface TypeReader {
    ImageType getType(ImageHeaderParser parser) throws IOException;
  }

  private interface OrientationReader {
    int getOrientation(ImageHeaderParser parser) throws IOException;
  }
}

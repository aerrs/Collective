package cc.clancollective.plugin.util;

import cc.clancollective.plugin.CollectiveConfig;
import cc.clancollective.plugin.domain.ChatPrivacyMode;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@Singleton
public class ScreenshotUtil
{
	private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
	private static final int MIN_JPEG_DIMENSION = 16;

	static
	{
		// Never let ImageIO fall back to a temp-file-backed cache stream. On large frames
		// the FileCacheImageOutputStream path throws during close (StreamCloser
		// IndexOutOfBoundsException / EOFException); forcing in-memory caching keeps all
		// encoding self-contained and deterministic.
		ImageIO.setUseCache(false);
	}

	private final Client client;
	private final ClientThread clientThread;
	private final DrawManager drawManager;
	private final CollectiveConfig config;

	@Inject
	public ScreenshotUtil(final Client client, final ClientThread clientThread,
		final DrawManager drawManager, final CollectiveConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.drawManager = drawManager;
		this.config = config;
	}

	public void capture(final Consumer<Screenshot> consumer)
	{
		final ChatPrivacyMode mode = config.chatPrivacy();
		final boolean hideChat = mode == ChatPrivacyMode.HIDE_ALL;
		final boolean hidePm = mode != ChatPrivacyMode.SHOW_ALL;

		final AtomicBoolean chatHidden = new AtomicBoolean(false);
		final AtomicBoolean pmHidden = new AtomicBoolean(false);

		clientThread.invoke(() ->
		{
			chatHidden.set(hideWidget(hideChat, InterfaceID.Chatbox.CHATAREA));
			pmHidden.set(hideWidget(hidePm, InterfaceID.PmChat.CONTAINER));

			drawManager.requestNextFrameListener(frame ->
			{
				// Grab the raw pixels, then restore the chat widgets immediately so they are
				// hidden for the shortest possible window. Encoding (which can be slow for
				// large frames) happens afterwards, once chat is already visible again.
				BufferedImage image = null;
				try
				{
					image = ImageUtil.bufferedImageFromImage(frame);
				}
				catch (Exception e)
				{
					log.warn("Collective: failed to capture screenshot frame", e);
				}
				finally
				{
					unhideWidget(chatHidden.get(), InterfaceID.Chatbox.CHATAREA);
					unhideWidget(pmHidden.get(), InterfaceID.PmChat.CONTAINER);
				}

				if (image == null)
				{
					return;
				}

				try
				{
					final Screenshot shot = encode(rescale(image, config.screenshotScale() / 100.0));
					consumer.accept(shot);
				}
				catch (Exception e)
				{
					log.warn("Collective: failed to encode screenshot", e);
				}
			});
		});
	}

	private boolean hideWidget(final boolean shouldHide, final int componentId)
	{
		if (!shouldHide)
		{
			return false;
		}
		final Widget widget = client.getWidget(componentId);
		if (widget == null || widget.isHidden())
		{
			return false;
		}
		widget.setHidden(true);
		return true;
	}

	private void unhideWidget(final boolean shouldUnhide, final int componentId)
	{
		if (!shouldUnhide)
		{
			return;
		}
		clientThread.invoke(() ->
		{
			final Widget widget = client.getWidget(componentId);
			if (widget != null)
			{
				widget.setHidden(false);
			}
		});
	}

	private static BufferedImage rescale(final BufferedImage input, final double percent)
	{
		if (percent + Math.ulp(1.0) >= 1.0)
		{
			return input;
		}
		final AffineTransform transform = AffineTransform.getScaleInstance(percent, percent);
		final AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
		final BufferedImage output = new BufferedImage(
			Math.max(1, (int) (input.getWidth() * percent)),
			Math.max(1, (int) (input.getHeight() * percent)),
			input.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : input.getType());
		op.filter(input, output);
		return output;
	}

	static Screenshot encode(final BufferedImage image) throws IOException
	{
		final byte[] png = toBytes(image, "png");
		if (png.length <= MAX_IMAGE_BYTES)
		{
			return new Screenshot("collective.png", "image/png", png);
		}

		// PNG is over the ceiling. Fall back to JPEG, but do not trust a single scale
		// pass: JPEG size is not proportional to pixel count, so a computed factor can
		// still overshoot. Reduce quality first, then dimensions, until the encoded
		// bytes are actually within MAX_IMAGE_BYTES.
		BufferedImage rgb = toRgb(image);
		final byte[] jpeg = encodeJpegUnderCeiling(rgb);
		return new Screenshot("collective.jpg", "image/jpeg", jpeg);
	}

	private static byte[] encodeJpegUnderCeiling(BufferedImage rgb) throws IOException
	{
		float quality = 0.9f;
		byte[] best = null;

		// Up to a bounded number of shrink passes so this always terminates.
		for (int pass = 0; pass < 12; pass++)
		{
			// Try progressively lower JPEG quality at the current dimensions.
			for (float q = quality; q >= 0.3f; q -= 0.2f)
			{
				final byte[] bytes = toJpeg(rgb, q);
				best = bytes;
				if (bytes.length <= MAX_IMAGE_BYTES)
				{
					return bytes;
				}
			}

			// Still too big at the lowest quality for these dimensions: halve the
			// linear size (quarter the pixels) and try again. Stop at a sane floor
			// rather than shrinking toward 1px, which produces degenerate images.
			final int nextW = rgb.getWidth() / 2;
			final int nextH = rgb.getHeight() / 2;
			if (nextW < MIN_JPEG_DIMENSION || nextH < MIN_JPEG_DIMENSION)
			{
				// Cannot shrink further without going below the floor; return the
				// smallest encoding we produced.
				break;
			}
			rgb = toRgb(rescale(rgb, 0.5));
		}

		return best;
	}

	private static BufferedImage toRgb(final BufferedImage image)
	{
		if (image.getType() == BufferedImage.TYPE_INT_RGB)
		{
			return image;
		}
		final BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(),
			BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = rgb.createGraphics();
		try
		{
			g.drawImage(image, 0, 0, null);
		}
		finally
		{
			g.dispose();
		}
		return rgb;
	}

	private static byte[] toJpeg(final BufferedImage rgb, final float quality) throws IOException
	{
		final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		if (!writers.hasNext())
		{
			throw new IOException("No image writer for format: jpeg");
		}
		final ImageWriter writer = writers.next();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Use an explicit in-memory cache stream rather than ImageIO.createImageOutputStream,
		// which may fall back to a temp-file-backed FileCacheImageOutputStream. That disk path
		// is what produced the StreamCloser IndexOutOfBoundsException / EOFException on large
		// frames. Keeping everything in memory makes encoding deterministic and self-contained.
		final MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out);
		try
		{
			writer.setOutput(ios);
			final ImageWriteParam param = writer.getDefaultWriteParam();
			if (param.canWriteCompressed())
			{
				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				param.setCompressionQuality(Math.max(0f, Math.min(1f, quality)));
			}
			writer.write(null, new IIOImage(rgb, null, null), param);
			ios.flush();
		}
		finally
		{
			// Detach the image from the writer before disposing, then close our own stream.
			writer.setOutput(null);
			writer.dispose();
			ios.close();
		}
		return out.toByteArray();
	}

	private static byte[] toBytes(final BufferedImage image, final String format) throws IOException
	{
		final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
		if (!writers.hasNext())
		{
			throw new IOException("No image writer for format: " + format);
		}
		final ImageWriter writer = writers.next();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out);
		try
		{
			writer.setOutput(ios);
			writer.write(image);
			ios.flush();
		}
		finally
		{
			writer.setOutput(null);
			writer.dispose();
			ios.close();
		}
		return out.toByteArray();
	}
}

package cc.clancollective.plugin.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScreenshotEncodingTest
{
	@Test
	public void smallImageEncodesAsPng() throws Exception
	{
		final BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		final Screenshot shot = ScreenshotUtil.encode(img);
		assertEquals("collective.png", shot.getFilename());
		assertEquals("image/png", shot.getMimeType());
		assertTrue(shot.getBytes().length > 0);
	}

	@Test
	public void pngBytesAreDecodable() throws Exception
	{
		final BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		final Screenshot shot = ScreenshotUtil.encode(img);
		final BufferedImage back = ImageIO.read(new ByteArrayInputStream(shot.getBytes()));
		assertNotNull(back);
	}

	@Test
	public void oversizedImageFallsBackToJpeg() throws Exception
	{
		final int side = 3000;
		final BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
		final Random rng = new Random(42);
		for (int y = 0; y < side; y++)
		{
			for (int x = 0; x < side; x++)
			{
				img.setRGB(x, y, rng.nextInt(0xFFFFFF));
			}
		}
		final Screenshot shot = ScreenshotUtil.encode(img);
		assertEquals("collective.jpg", shot.getFilename());
		assertEquals("image/jpeg", shot.getMimeType());
	}

	@Test
	public void jpegFallbackStaysUnderCeiling() throws Exception
	{
		final int side = 3000;
		final BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
		final Random rng = new Random(7);
		for (int y = 0; y < side; y++)
		{
			for (int x = 0; x < side; x++)
			{
				img.setRGB(x, y, rng.nextInt(0xFFFFFF));
			}
		}
		final Screenshot shot = ScreenshotUtil.encode(img);
		assertTrue(shot.getBytes().length <= 8 * 1024 * 1024);
	}

	@Test
	public void jpegFallbackBytesAreDecodable() throws Exception
	{
		final int side = 3000;
		final BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
		final Random rng = new Random(99);
		for (int y = 0; y < side; y++)
		{
			for (int x = 0; x < side; x++)
			{
				img.setRGB(x, y, rng.nextInt(0xFFFFFF));
			}
		}
		final Screenshot shot = ScreenshotUtil.encode(img);
		final BufferedImage back = ImageIO.read(new ByteArrayInputStream(shot.getBytes()));
		assertNotNull(back);
	}

	/**
	 * A large, high-entropy image: pure random RGB does not compress, so a single-pass
	 * "scale by sqrt(MAX/size)" fallback can still overshoot 8 MB. The loop must keep
	 * reducing until the encoded JPEG is genuinely within the ceiling.
	 */
	@Test
	public void adversarialNoiseStaysUnderCeiling() throws Exception
	{
		final int side = 5000;
		final BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
		final Random rng = new Random(1234);
		for (int y = 0; y < side; y++)
		{
			for (int x = 0; x < side; x++)
			{
				img.setRGB(x, y, rng.nextInt(0xFFFFFF));
			}
		}
		final Screenshot shot = ScreenshotUtil.encode(img);
		assertEquals("collective.jpg", shot.getFilename());
		assertTrue(shot.getBytes().length <= 8 * 1024 * 1024);

		final BufferedImage back = ImageIO.read(new ByteArrayInputStream(shot.getBytes()));
		assertNotNull(back);
	}
}

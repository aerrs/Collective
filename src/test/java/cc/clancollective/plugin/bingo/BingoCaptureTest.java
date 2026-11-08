package cc.clancollective.plugin.bingo;

import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.Test;

public class BingoCaptureTest
{
	@Test
	public void screenshotEncodesAsJpeg()
	{
		final byte[] jpeg = BingoCapture.jpeg(new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB));
		assertTrue(jpeg != null && jpeg.length > 2 && (jpeg[0] & 0xFF) == 0xFF && (jpeg[1] & 0xFF) == 0xD8);
	}
}

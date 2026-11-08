package cc.clancollective.plugin.bingo;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;

@Singleton
public class BingoCapture
{
	private static final int[] CHAT = {InterfaceID.Chatbox.CHATAREA, InterfaceID.PmChat.CONTAINER};

	private final Client client;
	private final ClientThread clientThread;
	private final DrawManager drawManager;
	private final ScheduledExecutorService executor;

	@Inject
	BingoCapture(final Client client, final ClientThread clientThread, final DrawManager drawManager,
		final ScheduledExecutorService executor)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.drawManager = drawManager;
		this.executor = executor;
	}

	public void take(final Consumer<byte[]> done)
	{
		final List<Widget> hidden = hideChat();
		drawManager.requestNextFrameListener(frame ->
		{
			final BufferedImage image = ImageUtil.bufferedImageFromImage(frame);
			clientThread.invokeLater(() -> hidden.forEach(w -> w.setHidden(false)));
			executor.execute(() -> done.accept(jpeg(image)));
		});
	}

	private List<Widget> hideChat()
	{
		final List<Widget> hidden = new ArrayList<>();
		for (final int id : CHAT)
		{
			final Widget widget = client.getWidget(id);
			if (widget != null && !widget.isSelfHidden())
			{
				widget.setHidden(true);
				hidden.add(widget);
			}
		}
		return hidden;
	}

	static byte[] jpeg(final BufferedImage image)
	{
		final BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = rgb.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		try (ByteArrayOutputStream out = new ByteArrayOutputStream())
		{
			return ImageIO.write(rgb, "jpg", out) ? out.toByteArray() : null;
		}
		catch (IOException e)
		{
			return null;
		}
	}
}

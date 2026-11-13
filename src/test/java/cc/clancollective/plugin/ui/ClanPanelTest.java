package cc.clancollective.plugin.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cc.clancollective.plugin.clan.ClanService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import okhttp3.OkHttpClient;
import org.junit.Test;

public class ClanPanelTest
{
	private static final Gson GSON = new Gson();

	private final List<Consumer<ClanService.Mine>> mines = new ArrayList<>();
	private final List<Consumer<List<ClanService.Clan>>> directories = new ArrayList<>();
	private int avatars;
	private final ClanPanel panel = new ClanPanel(new ClanService(new OkHttpClient(), GSON)
	{
		@Override
		public void mine(final String clan, final Consumer<Mine> done)
		{
			mines.add(done);
		}

		@Override
		public void directory(final String type, final Consumer<List<Clan>> done)
		{
			directories.add(done);
		}

		@Override
		public void avatar(final String path, final Consumer<BufferedImage> done)
		{
			avatars++;
			done.accept(null);
		}
	});

	@Test
	public void turnedOffSendsNothing() throws Exception
	{
		edt(() -> panel.show(null, false));
		assertTrue(mines.isEmpty() && directories.isEmpty());
		assertTrue(texts().contains("Clan listings are turned off."));
	}

	@Test
	public void lateResponsesAreIgnored() throws Exception
	{
		edt(() -> panel.show(null, true));
		edt(() -> panel.show("Clan A", true));
		directories.get(0).accept(clans("Old Clan"));
		mines.get(0).accept(GSON.fromJson("{\"card\":{\"name\":\"Clan A\"}}", ClanService.Mine.class));
		edt(() -> { });
		assertFalse(texts().contains("Old Clan"));
		assertTrue(texts().contains("Clan A"));
	}

	@Test
	public void stoppedPanelIgnoresResponsesAndRequests() throws Exception
	{
		edt(() -> panel.show(null, true));
		panel.stop();
		directories.get(0).accept(clans("Late Clan"));
		edt(() -> panel.show("Clan A", true));
		assertFalse(texts().contains("Late Clan"));
		assertEquals(0, avatars);
		assertTrue(mines.isEmpty());
	}

	@Test
	public void sameViewIsNotFetchedTwice() throws Exception
	{
		edt(() -> panel.show(null, true));
		edt(() -> panel.show(null, true));
		assertEquals(1, directories.size());
	}

	@Test
	public void failureCanBeRetried() throws Exception
	{
		edt(() -> panel.show("Clan A", true));
		mines.get(0).accept(null);
		edt(() -> { });
		assertTrue(texts().contains("Could not load your clan. Click to retry."));
		edt(() -> panel.show("Clan A", true));
		assertEquals(2, mines.size());
	}

	@Test
	public void missingListingSaysNotListed() throws Exception
	{
		edt(() -> panel.show("Clan A", true));
		mines.get(0).accept(GSON.fromJson("{}", ClanService.Mine.class));
		edt(() -> { });
		assertTrue(texts().contains("Not listed on Clan Collective."));
	}

	@Test
	public void clanNamesAreShownAsPlainText() throws Exception
	{
		edt(() -> panel.show(null, true));
		directories.get(0).accept(clans("<html><b>Big</b>"));
		edt(() -> { });
		final JLabel name = labels().stream().filter(l -> "<html><b>Big</b>".equals(l.getText())).findFirst().orElse(null);
		assertEquals(Boolean.TRUE, name.getClientProperty("html.disable"));
	}

	private static List<ClanService.Clan> clans(final String name)
	{
		return GSON.fromJson("[{\"name\":\"" + name + "\"}]", new TypeToken<List<ClanService.Clan>>() { }.getType());
	}

	private static void edt(final Runnable r) throws Exception
	{
		SwingUtilities.invokeAndWait(r);
	}

	private List<String> texts()
	{
		return labels().stream().map(JLabel::getText).collect(Collectors.toList());
	}

	private List<JLabel> labels()
	{
		final List<JLabel> out = new ArrayList<>();
		collect(panel, out);
		return out;
	}

	private static void collect(final Component c, final List<JLabel> out)
	{
		if (c instanceof JLabel)
		{
			out.add((JLabel) c);
		}
		if (c instanceof Container)
		{
			for (final Component child : ((Container) c).getComponents())
			{
				collect(child, out);
			}
		}
	}
}

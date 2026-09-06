package cc.clancollective.plugin.ui;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;

public final class PanelConstants
{
	private PanelConstants()
	{
	}

	public static final Color ACCENT = new Color(0x60, 0x78, 0xF8);

	public static final Color ACCENT_HOVER = new Color(0x50, 0x60, 0xE0);

	public static final Color ACCENT_MUTED = new Color(0x40, 0x4D, 0xA9);

	public static final Color BG = new Color(0x05, 0x04, 0x03);

	public static final Color SURFACE = new Color(0x14, 0x14, 0x16);

	public static final Color BORDER = new Color(0x23, 0x27, 0x43);

	public static final Color TEXT = new Color(0xE1, 0xE1, 0xE3);

	public static final Color TEXT_DIM = ColorScheme.LIGHT_GRAY_COLOR;

	public static final Color STATUS_OK = ColorScheme.PROGRESS_COMPLETE_COLOR;
	public static final Color STATUS_WARN = ColorScheme.BRAND_ORANGE;
	public static final Color STATUS_ERROR = ColorScheme.PROGRESS_ERROR_COLOR;
	public static final Color STATUS_UNKNOWN = ColorScheme.MEDIUM_GRAY_COLOR;

	public static final int PANEL_PADDING = 10;
	public static final int HEADER_PADDING_Y = 8;
	public static final int HEADER_PADDING_X = 10;
	public static final int SECTION_GAP = 10;
	public static final int SECTION_HEADER_PADDING_Y = 4;
	public static final int ROW_PADDING_Y = 5;
	public static final int ROW_PADDING_X = 8;
	public static final int ROW_GAP = 4;
	public static final int ICON_GAP = 8;
	public static final int SEPARATOR_HEIGHT = 1;
	public static final int STATUS_DOT_DIAMETER = 8;
	public static final int HEADER_ICON_SIZE = 24;

	public static final String CHEVRON_EXPANDED = "\u25be";
	public static final String CHEVRON_COLLAPSED = "\u25b8";

	public static final String ICON_RESOURCE = "/cc/clancollective/plugin/cc-logo.png";
	public static final String NAV_TOOLTIP = "Collective";
	public static final String TITLE_TEXT = "Collective";

	public static final String SECTION_FEEDS = "Feed health";
	public static final String SECTION_EVENTS = "Events";
	public static final String SECTION_SETUP = "Setup";

	public static final String EVENT_START = "Start event";
	public static final String EVENT_STOP = "Stop";
	public static final String EVENT_COPY = "Copy";
	public static final String EVENT_RESET = "Reset";
	public static final String EVENT_IDLE_HINT =
		"Records clanmates who appear near you while an event runs. Copies the name list to your clipboard.";
	public static final String EVENT_NO_NAMES = "No clanmates recorded yet.";

	public static final String SETUP_PLACEHOLDER =
		"Paste your clan's Discord webhook URLs in the plugin settings to start posting. "
			+ "Per-feature setup lands here in a future update.";

	public static final String NO_FEEDS_TEXT =
		"No webhooks configured yet. Add one in the plugin settings to see its status here.";

	public static final String FOOTER_WEB_LABEL = "clancollective.cc";
	public static final String FOOTER_WEB_URL = "https://clancollective.cc";

	public static final int NAV_PRIORITY = 7;
}

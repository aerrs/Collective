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

	public static final String SECTION_CLAN = "Clan";
	public static final String SECTION_ROSTER = "Roster";
	public static final String SECTION_CLAN_STATS = "Clan stats";
	public static final String SECTION_PLAYTIME = "Playtime";

	public static final String ROSTER_EMPTY = "Roster loads once your clan settings are open.";
	public static final String ROSTER_FILTER_ALL = "All ranks";
	public static final int ROSTER_MAX_HEIGHT = 240;
	public static final int ROSTER_ICON_SIZE = 16;

	public static final int PILL_RADIUS = 8;

	public static final String PLAYTIME_LOADING = "Loading playtime leaderboard…";
	public static final String PLAYTIME_EMPTY =
		"No playtime recorded yet. It appears once members with the plugin log in.";
	public static final String PLAYTIME_SUBHEADER = "Top players · last 7 days";
	public static final int PLAYTIME_MAX_HEIGHT = 240;
	public static final String SECTION_FEEDS = "Feed health";
	public static final String SECTION_EVENTS = "Events";
	public static final String SECTION_SETUP = "Setup";

	public static final String STATS_LOADING = "Loading clan stats…";
	public static final String STATS_NOT_LISTED =
		"This clan isn't on Clan Collective yet. List it at clancollective.cc to see stats here.";
	public static final String STATS_ERROR = "Couldn't reach Clan Collective. Retrying shortly.";
	public static final String STATS_PENDING =
		"Stats haven't synced yet. They'll appear once Clan Collective updates from the trackers.";
	public static final String STATS_EHP = "EHP";
	public static final String STATS_EHB = "EHB";
	public static final String STATS_TOTAL_XP = "Total XP";
	public static final String STATS_MEMBERS = "Members";
	public static final String STATS_WEEKLY_HEADER = "THIS WEEK";
	public static final String STATS_EHP_GAINED = "EHP gained";
	public static final String STATS_EHB_GAINED = "EHB gained";

	public static final String CLAN_NONE_TITLE = "Not in a clan";
	public static final String CLAN_NONE_HINT =
		"Join a clan in-game to see who's online, your rank, and member counts here.";
	public static final String CLAN_YOU_PREFIX = "You: ";
	public static final int CLAN_MEMBERS_MAX = 40;
	public static final String CLAN_WORLD_PREFIX = "W";

	public static final String EVENT_START = "Start";
	public static final String EVENT_STOP = "Stop";
	public static final String EVENT_COPY = "Copy";
	public static final String EVENT_RESET = "Reset";
	public static final String EVENT_IDLE_HINT =
		"Records clanmates who appear near you while an event runs. Copies the name list to your clipboard.";
	public static final String EVENT_NO_NAMES = "No clanmates recorded yet.";

	public static final String SETUP_PLACEHOLDER =
		"Roster, member counts and your rank show automatically once you're in a clan.<br><br>"
			+ "In the plugin settings you can turn on <b>Clan stats</b> and the <b>Playtime leaderboard</b> "
			+ "(playtime needs a token from your clan's dashboard at clancollective.cc).<br><br>"
			+ "Add Discord webhook URLs for the <b>Clan chat</b> and <b>Clan admin</b> feeds to relay them, "
			+ "then use <b>Events</b> to log attendance.";

	public static final String NO_FEEDS_TEXT =
		"No webhooks configured yet. Add one in the plugin settings to see its status here.";

	public static final String FOOTER_WEB_LABEL = "clancollective.cc";
	public static final String FOOTER_WEB_URL = "https://clancollective.cc";

	public static final int NAV_PRIORITY = 7;
}

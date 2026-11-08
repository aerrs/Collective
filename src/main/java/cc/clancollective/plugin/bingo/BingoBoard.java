package cc.clancollective.plugin.bingo;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public class BingoBoard
{
	private String event;
	private String team;
	private int size;
	private String color;
	private int points;
	private int done;
	private int lines;
	private int total;
	private int rank;
	private int teams;
	private boolean hidden;
	private int picks;
	private String url;
	private List<Tile> tiles = Collections.emptyList();
	private List<Unlock> unlocks = Collections.emptyList();

	BingoBoard named(final String event, final String team)
	{
		this.event = event;
		this.team = team;
		return this;
	}

	@Getter
	public static class Tile
	{
		private int pos;
		private String name;
		private String kind;
		private String icon;
		private String img;
		private List<String> items = Collections.emptyList();
		private int points;
		private int target;
		private int progress;
		private String status;
		private boolean mine;

		public boolean isDone()
		{
			return "done".equals(status);
		}

		public boolean isReview()
		{
			return "review".equals(status);
		}

		public boolean isLocked()
		{
			return "locked".equals(status);
		}

		void update(final int progress, final String status)
		{
			this.progress = progress;
			this.status = status;
		}
	}

	@Getter
	public static class Unlock
	{
		private String tile;
		private String at;
		private List<String> by = Collections.emptyList();
	}
}

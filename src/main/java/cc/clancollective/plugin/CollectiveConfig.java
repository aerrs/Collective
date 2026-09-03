package cc.clancollective.plugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;

@ConfigGroup(CollectiveConfig.GROUP)
public interface CollectiveConfig extends Config
{
	String GROUP = "collective";
}

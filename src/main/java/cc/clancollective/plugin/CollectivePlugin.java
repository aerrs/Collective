package cc.clancollective.plugin;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Collective",
	description = "Multi-clan Discord integration for OSRS clans",
	tags = {"clan", "discord", "webhook", "chat", "drops"}
)
public class CollectivePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private CollectiveConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Collective started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Collective stopped");
	}

	@Provides
	CollectiveConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CollectiveConfig.class);
	}
}

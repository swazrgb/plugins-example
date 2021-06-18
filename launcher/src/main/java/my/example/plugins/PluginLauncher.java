package my.example.plugins;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import my.example.plugins.sample.SamplePlugin;

@Slf4j
public class PluginLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SamplePlugin.class);
		RuneLite.main(args);
	}
}

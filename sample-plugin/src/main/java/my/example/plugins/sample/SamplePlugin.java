package my.example.plugins.sample;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.pf4j.Extension;

@PluginDescriptor(
	name = "Sample Plugin"
)
@Extension
@Slf4j
public class SamplePlugin extends Plugin
{
}

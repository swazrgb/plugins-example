package nl.swazrgb.hotswapagent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import org.hotswap.agent.command.MergeableCommand;
import org.hotswap.agent.javassist.bytecode.Descriptor;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ReloadClassCommand extends MergeableCommand
{
	private static final AgentLogger LOGGER = AgentLogger.getLogger(ReloadClassCommand.class);

	OprsHotswapPlugin plugin;

	@EqualsAndHashCode.Include
	String className;

	public ReloadClassCommand(OprsHotswapPlugin plugin, String className)
	{
		this.plugin = plugin;
		this.className = className;
	}

	@Override
	@SneakyThrows
	public void executeCommand()
	{
		Object pluginManager = plugin.getPluginManager();

		Collection<Object> plugins = (Collection<Object>) ReflectionHelper.invoke(pluginManager, pluginManager.getClass(), "getPlugins", new Class[0]);
		Object eventBus = plugin.getEventBus();

		try
		{
//			plugin.lock.writeLock().lock();

			// reinject new fields
			process(plugin.injector);
			for (Object plugin : plugins)
			{
				process(ReflectionHelper.get(plugin, "injector"));
			}

			// re-register eventbus
			Set<Object> subscriberObjects = (Set<Object>) ReflectionHelper.get(eventBus, OprsHotswapPlugin.INJECT_EVENTBUS_OBJECTS);
			Set<Object> refresh = new HashSet<>();

			for (Object subscriberObject : subscriberObjects)
			{
				if (subscriberObject.getClass().getName().equals(Descriptor.toJavaName(className)))
				{
					refresh.add(subscriberObject);
				}
			}

			for (Object o : refresh)
			{
				LOGGER.info("eventBus reregistering: {}", o);
				ReflectionHelper.invoke(eventBus, eventBus.getClass(), "unregister", new Class[]{Object.class}, o);
				ReflectionHelper.invoke(eventBus, eventBus.getClass(), "register", new Class[]{Object.class}, o);
			}

			Class<?> clazz = plugin.appClassLoader.loadClass(Descriptor.toJavaName(className));
			for (Method declaredMethod : clazz.getDeclaredMethods())
			{
				if (Modifier.isStatic(declaredMethod.getModifiers()) && declaredMethod.getAnnotation(CallAfterHotswap.class) != null)
				{
					declaredMethod.setAccessible(true);
					declaredMethod.invoke(null);
				}
			}
		}
		finally
		{
//			plugin.lock.writeLock().unlock();
		}

		ReflectionHelper.invoke(pluginManager, pluginManager.getClass(), "refreshPlugins", new Class[0]);
	}

	private void process(Object injector)
	{
		Map<Object, Object> bindings = (Map<Object, Object>) ReflectionHelper.invoke(injector, injector.getClass(), "getBindings", new Class[0]);

		// scan all bindings known to injector
		for (Map.Entry<Object, Object> entry : bindings.entrySet())
		{
			Object key = entry.getKey();
			Object value = entry.getValue();

			// check if its the class we're processing
			Class<?> knownClass = (Class<?>) ReflectionHelper.invoke(key, key.getClass(), "getRawType", new Class[0]);
			if (knownClass.getName().equals(Descriptor.toJavaName(className)))
			{
				Object instance = ReflectionHelper.invoke(value, value.getClass(), "getInstance", new Class[0]);
				LOGGER.info("injector reinject: {}", instance);

				// inject newly added fields
				ReflectionHelper.invoke(injector, injector.getClass(), "injectMembers", new Class[]{Object.class}, instance);
			}
		}

	}
}

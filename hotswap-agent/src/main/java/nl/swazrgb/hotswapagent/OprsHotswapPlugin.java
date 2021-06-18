package nl.swazrgb.hotswapagent;

import java.util.Set;
import lombok.SneakyThrows;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.javassist.bytecode.Descriptor;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

@Plugin(name = "OprsHotswapPlugin", testedVersions = {"latest"})
public class OprsHotswapPlugin
{
	public static final String MAIN_CLASS = "net.runelite.client.RuneLite";
	public static final String INJECT_EVENTBUS_OBJECTS = "$$oprshotswap$$objects";
	private static final AgentLogger LOGGER = AgentLogger.getLogger(OprsHotswapPlugin.class);
	@Init
	ClassLoader appClassLoader;
	@Init
	Scheduler scheduler;
	Object injector; //, pluginManager;

	@OnClassLoadEvent(classNameRegexp = MAIN_CLASS)
	public static void transformRuneLiteStart(CtClass ctClass) throws NotFoundException, CannotCompileException
	{
		String src = PluginManagerInvoker.buildInitializePlugin(OprsHotswapPlugin.class);
		src += PluginManagerInvoker.buildCallPluginMethod(OprsHotswapPlugin.class, "onRuneLiteStart", "injector", "java.lang.Object");
		ctClass.getDeclaredMethod("start").insertBefore(src);

		LOGGER.info(MAIN_CLASS + " has been enhanced.");
	}

	@OnClassLoadEvent(classNameRegexp = "net.runelite.client.eventbus.EventBus")
	public static void transformEventBus(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException
	{
		// Add a set to hold all objects that are currently registered
		// this is needed to deal with objects that are registered, but had no subscriptions at that point
		ctClass.addField(CtField.make("private java.util.Set " + INJECT_EVENTBUS_OBJECTS + " = new java.util.HashSet();", ctClass));

		String initializePlugin = PluginManagerInvoker.buildInitializePlugin(OprsHotswapPlugin.class);

		ctClass.getMethod("register", Descriptor.ofMethod(CtClass.voidType, new CtClass[]{classPool.get("java.lang.Object")})).insertBefore(
			initializePlugin +
				PluginManagerInvoker.buildCallPluginMethod(OprsHotswapPlugin.class, "onEventBusRegister", "this", "java.lang.Object", "object", "java.lang.Object")
		);

		ctClass.getMethod("unregister", Descriptor.ofMethod(CtClass.voidType, new CtClass[]{classPool.get("java.lang.Object")})).insertBefore(
			initializePlugin +
				PluginManagerInvoker.buildCallPluginMethod(OprsHotswapPlugin.class, "onEventBusUnregister", "this", "java.lang.Object", "object", "java.lang.Object")
		);

		ctClass.getMethod("post", Descriptor.ofMethod(CtClass.voidType, new CtClass[]{classPool.get("java.lang.Object")})).insertBefore(
			initializePlugin +
				PluginManagerInvoker.buildCallPluginMethod(OprsHotswapPlugin.class, "beforeEventBusPost", "this", "java.lang.Object", "event", "java.lang.Object")
		);

		ctClass.getMethod("post", Descriptor.ofMethod(CtClass.voidType, new CtClass[]{classPool.get("java.lang.Object")})).insertAfter(
			initializePlugin +
				PluginManagerInvoker.buildCallPluginMethod(OprsHotswapPlugin.class, "afterEventBusPost", "this", "java.lang.Object", "event", "java.lang.Object"),
			true
		);

		LOGGER.info(MAIN_CLASS + " has been enhanced.");
	}

	@OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
	public void reloadInjector(String className)
	{
		if (injector == null)
		{
			LOGGER.warning("Redefined classes before injector loaded");
			return;
		}

		LOGGER.info("Scheduling for {}", className);
		scheduler.scheduleCommand(new ReloadClassCommand(this, className));
	}

	public void onEventBusRegister(Object eventBus, Object object)
	{
		Set<Object> objects = (Set<Object>) ReflectionHelper.get(eventBus, INJECT_EVENTBUS_OBJECTS);
		objects.add(object);
	}

	public void onEventBusUnregister(Object eventBus, Object object)
	{
		Set<Object> objects = (Set<Object>) ReflectionHelper.get(eventBus, INJECT_EVENTBUS_OBJECTS);
		objects.remove(object);
	}

	public void beforeEventBusPost(Object eventBus, Object event)
	{
	}

	public void afterEventBusPost(Object eventBus, Object event)
	{
	}

	public void onRuneLiteStart(Object injector)
	{
		this.injector = injector;
		LOGGER.info("Plugin {} initialized on service {}", getClass(), this.injector);
	}

	@SneakyThrows
	public Object getInstance(String className)
	{
		Class<?> clazz = appClassLoader.loadClass(className);
		return ReflectionHelper.invoke(injector, injector.getClass(), "getInstance", new Class[]{Class.class}, clazz);
	}

	public Object getPluginManager()
	{
		return getInstance("net.runelite.client.plugins.PluginManager");
	}

	public Object getEventBus()
	{
		return getInstance("net.runelite.client.eventbus.EventBus");
	}
}

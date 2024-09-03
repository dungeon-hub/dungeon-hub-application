package me.taubsie.dungeonhub.application.loader

import com.google.common.reflect.ClassPath
import com.google.errorprone.annotations.DoNotCall
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.extensions.Extension
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier

object ClassLoader {
    private val startupListeners: MutableMap<StartupListener, OnStart> = HashMap()
    private val extensions: MutableList<Extension> = mutableListOf()
    private val logger: Logger = LoggerFactory.getLogger(ClassLoader::class.java)

    init {
        try {
            runBlocking {
                launch {
                    for (extensionEntry: Map.Entry<Class<Extension>, LoadExtension> in getClassesInPackage(
                        readPackage(ClassLoader::class.java),
                        Extension::class.java,
                        LoadExtension::class.java
                    ).entries) {
                        val extension: Extension = extensionEntry.key.getDeclaredConstructor().newInstance()

                        extensions.add(extension)
                    }
                }
            }
        } catch (exception: InstantiationException) {
            logger.error(null, exception)
        } catch (exception: IllegalAccessException) {
            logger.error(null, exception)
        } catch (exception: InvocationTargetException) {
            logger.error(null, exception)
        } catch (exception: NoSuchMethodException) {
            logger.error(null, exception)
        } catch (exception: ClassCastException) {
            logger.error(null, exception)
        }
    }

    private fun <T : StartupListener> addStartupListener(listener: T, onStart: OnStart) {
        startupListeners[listener] = onStart
    }

    fun loadStartupListeners() {
        for (entry: Map.Entry<Class<StartupListener>, OnStart> in getClassesInPackage(
            readPackage(ClassLoader::class.java),
            StartupListener::class.java,
            OnStart::class.java
        ).entries) {
            try {
                addStartupListener(getListenerInstance(entry.key), entry.value)
            } catch (exception: InvocationTargetException) {
                logger.error(
                    "Couldn't get instance of given listener \"" + entry.key.toString() + "\", " +
                            "causing it to not be executed on startup.", exception
                )
            } catch (exception: InstantiationException) {
                logger.error(
                    ("Couldn't get instance of given listener \"" + entry.key.toString() + "\", " +
                            "causing it to not be executed on startup."), exception
                )
            } catch (exception: IllegalAccessException) {
                logger.error(
                    ("Couldn't get instance of given listener \"" + entry.key.toString() + "\", " +
                            "causing it to not be executed on startup."), exception
                )
            } catch (exception: NoSuchMethodException) {
                logger.error(
                    ("Couldn't get instance of given listener \"" + entry.key.toString() + "\", " +
                            "causing it to not be executed on startup."), exception
                )
            }
        }
    }

    @Throws(
        NoSuchMethodException::class,
        InvocationTargetException::class,
        IllegalAccessException::class,
        InstantiationException::class
    )
    private fun getListenerInstance(clazz: Class<StartupListener>): StartupListener {
        try {
            if (Modifier.isStatic(clazz.getDeclaredMethod("getInstance").modifiers)) {
                val currentInstance = clazz.getDeclaredMethod("getInstance").invoke(null)
                if (currentInstance is StartupListener) {
                    return currentInstance
                } else {
                    throw NoSuchMethodException()
                }
            } else {
                throw NoSuchMethodException()
            }
        } catch (noSuchMethodException: NoSuchMethodException) {
            try {
                if (Modifier.isStatic(clazz.getDeclaredField("INSTANCE").modifiers)
                    && clazz.getDeclaredField("INSTANCE").type == clazz
                ) {
                    val field: Field = clazz.getDeclaredField("INSTANCE")
                    field.setAccessible(true)
                    return field.get(this) as StartupListener
                }
            } catch (noSuchFieldException: NoSuchFieldException) {
                return clazz.getDeclaredConstructor().newInstance()
            }
            return clazz.getDeclaredConstructor().newInstance()
        }
    }

    private val sortedListeners: List<StartupListener>
        get() = startupListeners
            .entries
            .stream()
            .sorted(
                Comparator.comparingInt<Map.Entry<StartupListener, OnStart>> { value: Map.Entry<StartupListener, OnStart> ->
                    if (value.value.order != 100) value.value.order else value.value.priority.priority
                }
            )
            .map { entry -> entry.key }
            .toList()

    suspend fun executePreStart() {
        for (startupListener: StartupListener in sortedListeners) {
            startupListener.preStart()
        }
    }

    suspend fun executeStartup() {
        for (startupListener: StartupListener in sortedListeners) {
            startupListener.onStart()
        }
    }

    suspend fun executePostStart() {
        for (startupListener: StartupListener in sortedListeners) {
            startupListener.postStart()
        }
    }

    /**
     * @return the first two entries seperated with a dot in the package name
     */
    private fun readPackage(clazz: Class<*>): String {
        val fullName = clazz.packageName
        val packageNameAfterFirstDot = fullName.substring(fullName.indexOf('.') + 1)
        return fullName.substring(0, fullName.indexOf('.') + 1) + packageNameAfterFirstDot.substring(
            0,
            packageNameAfterFirstDot.indexOf('.')
        )
    }

    @DoNotCall("possibly unsafe")
    fun <T, A : Annotation> getClassesInPackage(
        packageName: String?,
        clazz: Class<T>,
        annotation: Class<A>
    ): Map<Class<T>, A> {
        val classes: MutableMap<Class<T>, A> = HashMap()

        try {
            ClassPath.from(java.lang.ClassLoader.getSystemClassLoader())
                .allClasses
                .stream()
                .filter { classInfo: ClassPath.ClassInfo ->
                    classInfo.packageName.startsWith(
                        (packageName)!!
                    )
                }
                .map { path -> path.load() }
                .filter { cls ->
                    ((cls.isAnnotationPresent(annotation)
                            && (cls.getAnnotation(annotation) != null)
                            && clazz.isAssignableFrom(cls)))
                }
                .filter { cls ->
                    (!Modifier.isAbstract(cls.modifiers)
                            && !Modifier.isInterface(cls.modifiers))
                }
                // might want to look into if this can be done without unsafe casts -> unchecked inspection
                // had no issues with it yet, so it probably is fine, but the noinspection isn't perfect
                .forEach { cls ->
                    @Suppress("UNCHECKED_CAST", "ReplacePutWithAssignment")
                    classes.put(
                        cls as Class<T>,
                        cls.getAnnotation(annotation)
                    )
                }
        } catch (ioException: IOException) {
            logger.error(null, ioException)
        }

        return classes
    }

    suspend fun loadExtensions(bot: ExtensibleBot) {
        for (extension in extensions) {
            bot.addExtension {
                extension
            }
        }
    }
}
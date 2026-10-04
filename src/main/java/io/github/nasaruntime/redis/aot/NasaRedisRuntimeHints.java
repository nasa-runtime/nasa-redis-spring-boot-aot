package io.github.nasaruntime.redis.aot;

import io.github.nasaruntime.core.aot.NasaAotApplicationPackages;
import io.github.nasaruntime.core.aot.NasaAotClassScanner;
import io.github.nasaruntime.redis.cache.redis.RedisEventBatchListener;
import io.github.nasaruntime.redis.cache.redis.RedisEventSingleListener;
import io.github.nasaruntime.redis.cache.redis.RedisIdempotentCounterProperties;
import io.github.nasaruntime.redis.cache.redis.StreamSubscribe;
import io.github.nasaruntime.redis.cache.redis.Subscribe;
import io.github.nasaruntime.redis.cache.redis.Subscriber;
import io.github.nasaruntime.redis.cache.redis.job.RedisJob;
import io.github.nasaruntime.redis.cache.redis.job.RedisJobContext;
import io.github.nasaruntime.redis.cache.redis.job.RedisJobProperties;
import io.github.nasaruntime.redis.cache.redis.job.RedisJobResult;
import io.github.nasaruntime.redis.cache.redis.partition.RedisPartitionProperties;
import io.github.nasaruntime.redis.cache.redis.search.annotation.RsDocument;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务作用：补充 nasa-redis 在 Spring AOT 阶段无法静态推导的业务方法、消息类型与 RediSearch 实体提示。
 */
public final class NasaRedisRuntimeHints implements RuntimeHintsRegistrar, BeanFactoryInitializationAotProcessor {

    private static final BindingReflectionHintsRegistrar BINDING_HINTS =
            new BindingReflectionHintsRegistrar();

    /**
     * 业务作用：登记 RedisJob、幂等计数和分区配置的静态类型及 Lua 资源。
     *
     * @param hints       当前 AOT 提示集合
     * @param classLoader 应用类加载器
     *                    返回：无返回值；提示写入当前构建上下文。
     */
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerTypeTree(hints, RedisIdempotentCounterProperties.class);
        registerTypeTree(hints, RedisJobProperties.class);
        registerTypeTree(hints, RedisPartitionProperties.class);
        registerTypeTree(hints, RedisJob.class);
        registerTypeTree(hints, RedisJobResult.class);
        registerTypeTree(hints, Subscriber.class);
        registerTypeTree(hints, RsDocument.class);
        hints.resources().registerPattern("lua/*.lua");
        hints.resources().registerPattern("lua/job/*.lua");
    }

    /**
     * 业务作用：捕获容器已知业务 Bean 与应用基础包，在代码生成阶段登记 Redis 动态消费入口。
     *
     * @param beanFactory 应用 BeanFactory
     * @return 负责业务方法、泛型载荷与 RediSearch 实体提示的 AOT contribution
     */
    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(
            ConfigurableListableBeanFactory beanFactory) {
        Set<String> packages = NasaAotApplicationPackages.resolve(beanFactory);
        Set<Class<?>> beanTypes = resolveBeanTypes(beanFactory);
        ClassLoader classLoader = beanFactory.getBeanClassLoader();
        return (generationContext, beanFactoryInitializationCode) -> {
            RuntimeHints hints = generationContext.getRuntimeHints();
            registerBeanTypes(hints, beanTypes);
            registerRediSearchDocuments(hints, classLoader, packages);
        };
    }

    /**
     * 业务作用：读取所有 BeanDefinition 的用户类型而不实例化 Bean，使扫描范围与 RedisProxy 运行期容器遍历一致。
     *
     * @param beanFactory 应用 BeanFactory
     * @return 去重后的 Bean 用户类型
     */
    private static Set<Class<?>> resolveBeanTypes(ConfigurableListableBeanFactory beanFactory) {
        LinkedHashSet<Class<?>> types = new LinkedHashSet<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> type = beanFactory.getType(beanName, false);
            if (type != null) {
                types.add(ClassUtils.getUserClass(type));
            }
        }
        return Set.copyOf(types);
    }

    /**
     * 业务作用：登记容器 Bean 上的订阅与任务方法，并从监听接口解析消息泛型。
     *
     * @param hints     当前 AOT 提示集合
     * @param beanTypes 容器中可解析的用户类型
     *                  返回：无返回值；命中的动态入口与载荷类型写入 hints。
     */
    static void registerBeanTypes(RuntimeHints hints, Set<Class<?>> beanTypes) {
        for (Class<?> beanType : beanTypes) {
            Map<Method, Boolean> methods = MethodIntrospector.selectMethods(
                    beanType,
                    (MethodIntrospector.MetadataLookup<Boolean>) method -> isDynamicMethod(method)
                            ? Boolean.TRUE : null);
            for (Method method : methods.keySet()) {
                hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
                registerMethodBindings(hints, method);
            }
            registerListenerBinding(hints, beanType, Subscribe.class);
            registerListenerBinding(hints, beanType, StreamSubscribe.class);
            registerListenerBinding(hints, beanType, RedisEventSingleListener.class);
            registerListenerBinding(hints, beanType, RedisEventBatchListener.class);
        }
    }

    /**
     * 业务作用：判断方法是否由 Redis 订阅或调度框架在运行期反射调用。
     *
     * @param method 待检查业务方法
     * @return 存在 {@link Subscriber} 或 {@link RedisJob} 时返回 true
     */
    private static boolean isDynamicMethod(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, Subscriber.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(method, RedisJob.class) != null;
    }

    /**
     * 业务作用：为动态方法参数登记 Jackson 绑定提示，任务上下文与原始字节保持框架内建处理。
     *
     * @param hints  当前 AOT 提示集合
     * @param method 已确认由 Redis 框架动态调用的方法
     *               返回：无返回值；业务参数的完整泛型写入 reflection hints。
     */
    private static void registerMethodBindings(RuntimeHints hints, Method method) {
        boolean redisJob = AnnotatedElementUtils.findMergedAnnotation(method, RedisJob.class) != null;
        Type[] declaredTypes = method.getGenericParameterTypes();
        Class<?>[] rawTypes = method.getParameterTypes();
        for (int index = 0; index < declaredTypes.length; index++) {
            if (redisJob && (RedisJobContext.class.isAssignableFrom(rawTypes[index])
                    || rawTypes[index] == byte[].class)) {
                continue;
            }
            BINDING_HINTS.registerReflectionHints(hints.reflection(), declaredTypes[index]);
        }
    }

    /**
     * 业务作用：从监听器实现类解析消息泛型并登记绑定类型，保证 Stream 和 Pub/Sub 解码无需运行期猜测。
     *
     * @param hints        当前 AOT 提示集合
     * @param beanType     监听器 Bean 用户类型
     * @param listenerType 待解析的监听根接口
     *                     返回：无返回值；无法解析或退化为 Object 时不登记。
     */
    private static void registerListenerBinding(
            RuntimeHints hints, Class<?> beanType, Class<?> listenerType) {
        if (!listenerType.isAssignableFrom(beanType)) {
            return;
        }
        ResolvableType resolved = ResolvableType.forClass(beanType).as(listenerType).getGeneric(0);
        Class<?> rawType = resolved.resolve();
        if (rawType != null && rawType != Object.class) {
            BINDING_HINTS.registerReflectionHints(hints.reflection(), resolved.getType());
        }
    }

    /**
     * 业务作用：扫描应用包中的 RediSearch 文档实体并登记完整绑定成员，覆盖非 Spring Bean 的持久化类型。
     *
     * @param hints       当前 AOT 提示集合
     * @param classLoader 应用类加载器
     * @param packages    应用基础包
     *                    返回：无返回值；仅命中 {@link RsDocument} 的类型写入 hints。
     */
    static void registerRediSearchDocuments(
            RuntimeHints hints, ClassLoader classLoader, Set<String> packages) {
        List<Class<?>> types = NasaAotClassScanner.scan(classLoader, packages);
        for (Class<?> type : types) {
            if (AnnotatedElementUtils.findMergedAnnotation(type, RsDocument.class) != null) {
                hints.reflection().registerType(type, MemberCategory.values());
                BINDING_HINTS.registerReflectionHints(hints.reflection(), type);
            }
        }
    }

    /**
     * 业务作用：递归登记公开配置类型及其嵌套类型，确保 Spring Binder 能写入完整配置树。
     *
     * @param hints 当前 AOT 提示集合
     * @param type  根类型
     *              返回：无返回值；根类型与全部 declared class 都会登记。
     */
    private static void registerTypeTree(RuntimeHints hints, Class<?> type) {
        hints.reflection().registerType(type, MemberCategory.values());
        for (Class<?> nested : type.getDeclaredClasses()) {
            registerTypeTree(hints, nested);
        }
    }
}

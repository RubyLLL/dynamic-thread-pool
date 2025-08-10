# 概述
这是一个基于Spring Boot的动态线程池项目，它允许在运行时动态调整Java线程池的配置参数（如核心线程数、最大线程数等），无需重启应用。项目采用了分层架构，分为三个主要模块：

1. __dynamic-thread-pool-spring-boot-starter__：核心功能模块，提供自动配置和线程池管理功能
2. __dynamic-thread-pool-admin__：管理控制台，提供API接口和Web界面来监控和调整线程池
3. __dynamic-thread-pool-test__：测试应用，演示如何使用动态线程池

# 核心实现和原理
## 自动装配和配置
项目通过Spring Boot的自动装配机制，自动注册必要的组件
```Java
@Configuration
@EnableConfigurationProperties(DynamicThreadPoolAutoProperties.class)
@EnableScheduling
public class DynamicThreadPoolAutoConfig {
    // 配置和Bean定义
}
```

通过`spring.factories`文件：
```Java
org.springframework.boot.autoconfigure.EnableAutoConfiguration=cn.bugstack.middleware.dynamic.thread.pool.sdk.config.DynamicThreadPoolAutoConfig
```

## 线程池数据结构
核心数据结构是`ThreadPoolConfigEntity`，包含线程池的配置和运行状态：
```Java
public class ThreadPoolConfigEntity {
    private String appName;           // 应用名称
    private String threadPoolName;    // 线程池名称
    private int corePoolSize;         // 核心线程数
    private int maximumPoolSize;      // 最大线程数
    private int activeCount;          // 当前活跃线程数
    private int poolSize;             // 当前池中线程数
    private String queueType;         // 队列类型
    private int queueSize;            // 当前队列任务数
    private int remainingCapacity;    // 队列剩余任务数
    // 构造器、getter和setter
}
```

## 服务实现与接口
线程池管理服务通过`DynamicThreadPoolService`实现，提供三个主要功能：

- 查询线程池列表
- 根据名称查询特定线程池
- 更新线程池配置
```Java
public class DynamicThreadPoolService implements IDynamicThreadPoolService {
    @Override
    public List<ThreadPoolConfigEntity> queryThreadPoolList() {
        // 遍历应用中的线程池并收集信息
    }
    
    @Override
    public ThreadPoolConfigEntity queryThreadPoolConfigByName(String threadPoolName) {
        // 获取特定线程池信息
    }
    
    @Override
    public void updateThreadPoolConfig(ThreadPoolConfigEntity threadPoolConfigEntity) {
        // 动态调整线程池参数
        threadPoolExecutor.setCorePoolSize(threadPoolConfigEntity.getCorePoolSize());
        threadPoolExecutor.setMaximumPoolSize(threadPoolConfigEntity.getMaximumPoolSize());
    }
}
```

## 分布式协调与通知机制
项目使用Redis作为分布式协调和存储中心：

1. __Redis注册中心__：实现`IRegistry`接口

   ```java
   public class RedisRegistry implements IRegistry {
       @Override
       public void reportThreadPool(List<ThreadPoolConfigEntity> threadPoolEntities) {
           // 上报线程池信息到Redis
       }
       
       @Override
       public void reportThreadPoolConfigParameter(ThreadPoolConfigEntity threadPoolConfigEntity) {
           // 上报特定线程池配置到Redis
       }
   }
   ```

2. __定时上报任务__：通过`@Scheduled`定时将线程池信息上报到Redis

   ```java
   @Scheduled(cron = "0/20 * * * * ?")
   public void execReportThreadPoolList() {
       // 每20秒上报一次线程池状态
   }
   ```

3. __配置变更监听__：通过Redis的发布订阅机制实现配置变更通知

   ```java
   public class ThreadPoolConfigAdjustListener implements MessageListener<ThreadPoolConfigEntity> {
       @Override
       public void onMessage(CharSequence charSequence, ThreadPoolConfigEntity threadPoolConfigEntity) {
           // 接收配置变更通知并更新线程池
       }
   }
   ```

## 管理控制台API

管理控制台提供了三个主要API：

- 查询线程池列表
- 查询特定线程池配置
- 更新线程池配置

```java
@RestController()
@RequestMapping("/api/v1/dynamic/thread/pool/")
public class DynamicThreadPoolController {
    // API接口实现
}
```

## Web前端界面
提供了简单的HTML+JavaScript前端页面，用于可视化监控和调整线程池参数：

- 展示所有线程池的当前状态
- 提供表单修改核心线程数和最大线程数
- 支持自动刷新数据

# 工作流程

1. __初始化阶段__

   - Spring Boot启动时自动装配`DynamicThreadPoolAutoConfig`
   - 初始化Redis连接并创建所需Bean
   - 从Redis获取已保存的线程池配置（如果有），应用到本地线程池

2. __运行阶段__

   - 定时任务`ThreadPoolDataReportJob`每20秒将线程池信息上报到Redis
   - 应用程序正常使用线程池处理任务

3. __配置调整阶段__

   - 管理员通过Web界面查看线程池状态
   - 管理员调整线程池参数（如增加核心线程数）
   - 管理控制台通过Redis发布配置变更消息
   - 应用程序的`ThreadPoolConfigAdjustListener`接收消息并更新线程池
   - 线程池立即应用新配置（如增加核心线程数）
   - 更新后的配置和状态再次上报到Redis

## 关键技术点

1. __利用Java线程池的动态调整能力__

   - Java的`ThreadPoolExecutor`支持运行时调整`corePoolSize`和`maximumPoolSize`
   - 项目充分利用这一特性实现动态调整

2. __使用Redis作为分布式协调中心__

   - 使用Redisson客户端与Redis交互
   - 利用Redis的发布订阅（Pub/Sub）机制实现配置变更通知
   - 使用Redis存储线程池配置和状态信息

3. __Spring Boot自动装配__

   - 使用`@Configuration`和`spring.factories`实现自动装配
   - 通过`@EnableConfigurationProperties`绑定配置属性
   - 通过`@EnableScheduling`启用定时任务

4. __前后端分离__

   - 后端提供RESTful API
   - 前端通过AJAX调用API，实现动态数据展示和操作


## 关键流程描述
### 收集阶段
### 1. 收集触发点：ThreadPoolDataReportJob类

```java
public class ThreadPoolDataReportJob {
    // ...
    
    @Scheduled(cron = "0/20 * * * * ?")  // 每20秒执行一次
    public void execReportThreadPoolList() {
        // 调用服务收集所有线程池信息
        List<ThreadPoolConfigEntity> threadPoolConfigEntities = dynamicThreadPoolService.queryThreadPoolList();
        registry.reportThreadPool(threadPoolConfigEntities);
        logger.info("动态线程池，上报线程池信息：{}", JSON.toJSONString(threadPoolConfigEntities));
        // 遍历每个线程池，单独上报配置参数
        for (ThreadPoolConfigEntity threadPoolConfigEntity : threadPoolConfigEntities) {
            registry.reportThreadPoolConfigParameter(threadPoolConfigEntity);
            logger.info("动态线程池，上报线程池配置：{}", JSON.toJSONString(threadPoolConfigEntity));
        }
    }
}
```

### 2. 收集实现：DynamicThreadPoolService类

```java
public class DynamicThreadPoolService implements IDynamicThreadPoolService {
    // ...
    
    @Override
    public List<ThreadPoolConfigEntity> queryThreadPoolList() {
        // 获取所有线程池Bean名称
        Set<String> threadPoolBeanNames = threadPoolExecutorMap.keySet();
        List<ThreadPoolConfigEntity> threadPoolVOS = new ArrayList<>(threadPoolBeanNames.size());
        
        // 遍历每个线程池，收集状态
        for (String beanName : threadPoolBeanNames) {
            ThreadPoolExecutor threadPoolExecutor = threadPoolExecutorMap.get(beanName);
            ThreadPoolConfigEntity threadPoolConfigVO = new ThreadPoolConfigEntity(applicationName, beanName);
            
            // 收集线程池配置参数
            threadPoolConfigVO.setCorePoolSize(threadPoolExecutor.getCorePoolSize());
            threadPoolConfigVO.setMaximumPoolSize(threadPoolExecutor.getMaximumPoolSize());
            
            // 收集线程池运行时状态
            threadPoolConfigVO.setActiveCount(threadPoolExecutor.getActiveCount());
            threadPoolConfigVO.setPoolSize(threadPoolExecutor.getPoolSize());
            
            // 收集队列信息
            threadPoolConfigVO.setQueueType(threadPoolExecutor.getQueue().getClass().getSimpleName());
            threadPoolConfigVO.setQueueSize(threadPoolExecutor.getQueue().size());
            threadPoolConfigVO.setRemainingCapacity(threadPoolExecutor.getQueue().remainingCapacity());
            
            threadPoolVOS.add(threadPoolConfigVO);
        }
        return threadPoolVOS;
    }
    
    // 查询单个线程池配置的方法实现类似...
}
```

### 收集阶段工作流程

1. __定时触发__：通过Spring的`@Scheduled`注解，定时任务每20秒执行一次

2. __数据收集__：

   - 获取应用中所有注册的`ThreadPoolExecutor`类型的Bean
   - 使用Java的反射API获取线程池的运行时状态（核心线程数、最大线程数、活跃线程数等）
   - 通过调用ThreadPoolExecutor的公共方法（如`getCorePoolSize()`、`getActiveCount()`等）获取线程池的参数和状态
   - 同时收集线程池使用的队列信息（类型、大小、剩余容量）

3. __数据封装__：将收集到的数据封装到`ThreadPoolConfigEntity`对象中

4. __上报处理__：调用`registry.reportThreadPool`和`registry.reportThreadPoolConfigParameter`将数据上报到Redis。不需要修改Java原生的`ThreadPoolExecutor`类，而是通过调用其公开的方法来获取所需信息，实现了对线程池的非侵入式监控。

### 自动装配
在这个动态线程池项目中，SpringBoot 的自动装配机制是实现零侵入集成的关键。

#### 1. 自动装配的核心组件

##### 1.1 META-INF/spring.factories 文件

这是自动装配的入口点，位于 starter 模块中：

```javascript
org.springframework.boot.autoconfigure.EnableAutoConfiguration=cn.bugstack.middleware.dynamic.thread.pool.sdk.config.DynamicThreadPoolAutoConfig
```

这个文件告诉 Spring Boot 在启动时需要加载 `DynamicThreadPoolAutoConfig` 配置类。

##### 1.2 配置类

```java
@Configuration
@EnableConfigurationProperties(DynamicThreadPoolAutoProperties.class)
@EnableScheduling
public class DynamicThreadPoolAutoConfig {
    // Bean 定义...
}
```

`@Configuration` 标记这是一个配置类，Spring 会在这里寻找 Bean 定义。

##### 1.3 配置属性类

```java
@ConfigurationProperties(prefix = "dynamic.thread.pool.config", ignoreInvalidFields = true)
public class DynamicThreadPoolAutoProperties {
    /** 状态；open = 开启、close 关闭 */
    private boolean enable;
    /** redis host */
    private String host;
    // 其他属性...
}
```

`@ConfigurationProperties` 允许从配置文件（如 application.yml）绑定属性到 Java 对象。

#### 2. 自动装配流程

1. __启动阶段__：Spring Boot 启动时会读取所有 jar 包中的 `META-INF/spring.factories` 文件
2. __配置加载__：解析文件内容，找到所有 `EnableAutoConfiguration` 指定的配置类
3. __配置处理__：实例化 `DynamicThreadPoolAutoConfig`，并应用其中的配置
4. __属性绑定__：通过 `@EnableConfigurationProperties` 激活 `DynamicThreadPoolAutoProperties`，将 yml 中的配置绑定到该对象
5. __Bean 注册__：配置类中定义的 Bean 被创建并注册到 Spring 容器

#### 3. Bean 创建与依赖注入

在配置类中，定义了多个 Bean 方法：

```java
@Bean("dynamicThreadRedissonClient")
public RedissonClient redissonClient(DynamicThreadPoolAutoProperties properties) {
    // 根据配置创建 RedissonClient...
}
@Bean
public IRegistry redisRegistry(RedissonClient dynamicThreadRedissonClient) {
    return new RedisRegistry(dynamicThreadRedissonClient);
}
@Bean("dynamicThreadPollService")
public DynamicThreadPoolService dynamicThreadPollService(ApplicationContext applicationContext, 
                                                      Map<String, ThreadPoolExecutor> threadPoolExecutorMap, 
                                                      RedissonClient redissonClient) {
    // 创建并返回服务...
}
```

这些方法不仅创建了需要的 Bean，而且通过参数实现了依赖注入，比如：

- `redissonClient` 方法注入了 `DynamicThreadPoolAutoProperties`
- `redisRegistry` 方法注入了 `RedissonClient`
- `dynamicThreadPollService` 方法注入了 `ApplicationContext`、`Map<String, ThreadPoolExecutor>` 和 `RedissonClient`

#### 4. ThreadPoolExecutor 的获取

一个特别关键的部分是 `Map<String, ThreadPoolExecutor> threadPoolExecutorMap` 参数，它是如何被自动注入的：

```java
@Bean("dynamicThreadPollService")
public DynamicThreadPoolService dynamicThreadPollService(ApplicationContext applicationContext, 
                                                      Map<String, ThreadPoolExecutor> threadPoolExecutorMap, 
                                                      RedissonClient redissonClient) {
    // ...
    
    // 获取缓存数据，设置本地线程池配置
    Set<String> threadPoolKeys = threadPoolExecutorMap.keySet();
    for (String threadPoolKey : threadPoolKeys) {
        ThreadPoolConfigEntity threadPoolConfigEntity = redissonClient.<ThreadPoolConfigEntity>getBucket(
            RegistryEnumVO.THREAD_POOL_CONFIG_PARAMETER_LIST_KEY.getKey() + "_" + applicationName + "_" + threadPoolKey).get();
        if (null == threadPoolConfigEntity) continue;
        ThreadPoolExecutor threadPoolExecutor = threadPoolExecutorMap.get(threadPoolKey);
        threadPoolExecutor.setCorePoolSize(threadPoolConfigEntity.getCorePoolSize());
        threadPoolExecutor.setMaximumPoolSize(threadPoolConfigEntity.getMaximumPoolSize());
    }
    
    return new DynamicThreadPoolService(applicationName, threadPoolExecutorMap);
}
```

Spring Boot 会自动收集应用上下文中所有类型为 `ThreadPoolExecutor` 的 Bean，并将它们注入到这个 Map 中。这是通过 Spring 的类型转换服务实现的，无需开发者手动收集线程池。

#### 5. 初始化配置应用

自动装配还处理了从 Redis 加载已存储的线程池配置并应用到本地线程池的逻辑：

```java
// 获取缓存数据，设置本地线程池配置
Set<String> threadPoolKeys = threadPoolExecutorMap.keySet();
for (String threadPoolKey : threadPoolKeys) {
    ThreadPoolConfigEntity threadPoolConfigEntity = redissonClient.<ThreadPoolConfigEntity>getBucket(
        RegistryEnumVO.THREAD_POOL_CONFIG_PARAMETER_LIST_KEY.getKey() + "_" + applicationName + "_" + threadPoolKey).get();
    if (null == threadPoolConfigEntity) continue;
    ThreadPoolExecutor threadPoolExecutor = threadPoolExecutorMap.get(threadPoolKey);
    threadPoolExecutor.setCorePoolSize(threadPoolConfigEntity.getCorePoolSize());
    threadPoolExecutor.setMaximumPoolSize(threadPoolConfigEntity.getMaximumPoolSize());
}
```

这确保了应用重启时，线程池配置能从 Redis 恢复，而不是使用默认值。

#### 6. 功能启用与监听器注册

最后，自动装配还创建了定时任务和消息监听器：

```java
@Bean
public ThreadPoolDataReportJob threadPoolDataReportJob(IDynamicThreadPoolService dynamicThreadPoolService, IRegistry registry) {
    return new ThreadPoolDataReportJob(dynamicThreadPoolService, registry);
}
@Bean
public ThreadPoolConfigAdjustListener threadPoolConfigAdjustListener(IDynamicThreadPoolService dynamicThreadPoolService, IRegistry registry) {
    return new ThreadPoolConfigAdjustListener(dynamicThreadPoolService, registry);
}
@Bean(name = "dynamicThreadPoolRedisTopic")
public RTopic threadPoolConfigAdjustListener(RedissonClient redissonClient, ThreadPoolConfigAdjustListener threadPoolConfigAdjustListener) {
    RTopic topic = redissonClient.getTopic(RegistryEnumVO.DYNAMIC_THREAD_POOL_REDIS_TOPIC.getKey() + "_" + applicationName);
    topic.addListener(ThreadPoolConfigEntity.class, threadPoolConfigAdjustListener);
    return topic;
}
```

通过这些注册的 Bean，实现了定时上报线程池状态和监听配置变更的功能。

#### 总结

这个动态线程池项目巧妙地利用了 Spring Boot 的自动装配机制，实现了以下目标：

1. __低侵入性集成__：使用者只需添加依赖和少量配置
2. __自动发现线程池__：自动收集应用中的所有线程池实例
3. __配置外部化__：通过 `@ConfigurationProperties` 支持配置外部化
4. __组件自动注册__：自动创建并注册必要的组件（Redis客户端、监听器、定时任务等）
5. __启动时初始化__：应用启动时自动从 Redis 加载配置 这种设计让整个功能可以作为一个独立的模块被集成到任何 Spring Boot 应用中，大大降低了使用门槛。

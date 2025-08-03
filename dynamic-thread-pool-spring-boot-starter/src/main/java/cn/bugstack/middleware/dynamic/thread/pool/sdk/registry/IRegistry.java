package cn.bugstack.middleware.dynamic.thread.pool.sdk.registry;

import cn.bugstack.middleware.dynamic.thread.pool.sdk.domain.model.entity.ThreadPoolConfigEntity;

import java.util.List;

/**
 * @description 注册中心接口
 */
public interface IRegistry {

    /**
     * 上报所有线程池的配置信息
     * @param threadPoolEntities 线程池配置信息
     */
    void reportThreadPool(List<ThreadPoolConfigEntity> threadPoolEntities);

    /**
     * 上报线程池的参数配置
     * @param threadPoolConfigEntity
     */
    void reportThreadPoolConfigParameter(ThreadPoolConfigEntity threadPoolConfigEntity);

}

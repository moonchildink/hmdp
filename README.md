# Spring 笔记



## 项目笔记



Redis缓存内存更新策略：

1. 内存淘汰：Redis内存淘汰机制：适用于低一致性需求
2. 超时剔除：给缓存数据添加TTL时间，到期删除缓存。下次查询时更新
3. 主动更新：修改数据库时更新缓存。

主动更新的实现方案：

1. Cache Aside Pattern：由调用者同时更新
2. Read/Write Through pattern：缓存与数据库整合为一个服务，由服务来维护一致性。调用者无需关心一致性，对外透明
3. Write Behind Caching Pattern：调用者之操作缓存，其他线程异步将数据持久化到数据库，保持最终一致

缓存数据库一致性：

1. 先删缓存，更新数据库：删除缓存，更新数据库；查询数据库，存入缓存。
2. 先更新数据库，在删缓存：更新数据库，删除缓存；查询缓存未命中，未命中，查询数据库，更新缓存。（**发生错误的概率更低**）
3. 异常情况：脏读，更新数据库以后另一线程来查询缓存，得到错误数据；解决方案：保持数据库与缓存操作的原子性，分布式事务锁。



#### 缓存穿透

1. 什么是缓存穿透：客户端请求的数据在客户端和缓存中都不存在的情况，缓存永远不会生效，这些请求会发送到数据库。可能会对数据库产生较大负担，而且当远程数据库会拖慢访问速度。

2. 解决方案：

  1. 缓存空对象：缓存null到Redis，设置TTL(Time to live)。可能会存在短期不一致性

  2. 布隆过滤：在客户端和Redis之间添加一层布隆过滤器，如果不存在（数据库与Redis）拒绝，否则放行。

    1. 实现复杂
    2. 存在误判的可能

  3. 增加ID的复杂度，进行数据格式校验，加强用户权限管理，热点参数的限流

  4. **互斥锁**：类似操作系统中的并行处理。考虑到缓存重建的资源消耗可能较大，且存在多个线程同时存入相同缓存的情况。但是耗费过大：一个线程获取锁以后其他线程可能等待较长时间，且存在可能产生死锁

    1. 实现逻辑：使用Redis的`SETNX`命令：set the value of a key,only if the key doesn't exist.对用Java中的`setIfAbsent`函数

    2. 代码

       ```java
       String shopJson = template.opsForValue().get(SystemConstants.REDIS_CACHE + id);
               Shop shop = null;
               if (shopJson == null) {
                   // 说明不存在于Cache之中,进行缓存重建
                   String lockKey = "lock:shop:" + id;
                   try {
                       boolean isSuccess = tryLock(lockKey);
                       if (!isSuccess) {
                           Thread.sleep(50);
                           // 重试查询
                           return queryWithMutex(id);
                       }
                       // 获取成功,查询数据库，存入Redis，释放lock
                       shop = getById(id);
       
                       // 模拟数据库重建延迟
                       Thread.sleep(200);
                       if (shop == null) {
                           // 说明也不存在于数据库之中,那么在Cache中设置一个空字符串
                           template.opsForValue().set(SystemConstants.REDIS_CACHE + String.valueOf(id), "", 3L, TimeUnit.MINUTES);
                           return null;
                       }
                       template.opsForValue().set(SystemConstants.REDIS_CACHE + id, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
                   } catch (InterruptedException e) {
                       throw new RuntimeException(e);
                   } finally {
                       releaseLock(lockKey);
                   }
                   return shop;
               }
               // 在缓存之中
               // 1. 空对象
               if (shopJson.equals("")) {
                   return null;
               }
               return JSONUtil.toBean(shopJson, Shop.class);
       
       
       private boolean tryLock(String key) {
               Boolean block = template.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
               return BooleanUtil.isTrue(block);
           }
       
           private boolean releaseLock(String key) {
               return Boolean.TRUE.equals(template.delete(key));
           }
       ```



         ![image-20240411210921292](https://raw.githubusercontent.com/moonchildink/image/main/imgs202404112109342.png)

5. **逻辑过期**：

  1. 缓存是永久存在于数据库的，设置的TTL是**逻辑过期时间**。（实际上，再存入到Redis中时未设置过期时间，但是添加了一位数值，即逻辑TTL，查询结果超过logical TTL执行重新从数据库中获取数据存入缓存）。

  2. 实现方案：

     ![image-20240411215819861](https://raw.githubusercontent.com/moonchildink/image/main/imgs202404112158916.png)


![image-20240411210525366](https://raw.githubusercontent.com/moonchildink/image/main/imgs202404112105509.png)

#### 缓存雪崩

1. 什么是缓存雪崩：同一时段大量Key失效，或者Redis服务宕机，导致大量请求到达数据库，带来巨大压力。
2. 解决方案：
  1. 对TTL加入随机值
  2. 利用Redis集群提高服务的可用性
  3. 给缓存业务添加降级限流策略
  4. 添加多级缓存







### 秒杀系统

#### 生成全局唯一ID

1. 使用64位记录，组成：1bit符号位，31bit时间戳，32bit索引位。
2. 方法：首先生成31bit时间戳，之后使用redis increase自增操作生成索引
3. 拼接后返回ID
4. 生成ID的其他方法：雪花ID，UUID，数据库自增

#### 实现优惠券秒杀下单

- ==解决超卖问题==：多线程并发场景下，会产生超卖问题。

1. 悲观锁：认为线程安全问题一定会发生，在操作之前获取锁，确保线程串行执行.如`Synchronized`或者`Lock`
  1. 优缺点：简单粗暴但是性能一般
2. 乐观锁：认为线程安全问题不一定会发生，因此不加锁，只有在**更新数据时**判断有没有修改数据
  1. 判断方法：
    - 版本号法：给column添加version字段，更新时判断version是否正确
    - CAS(Compare and Swap)方法：进行更新时查询指定字段是否发生变化，发生变化拒绝更新
  2. 缺点：成功率较低
3. `AopContext.currentProxy()`代理的使用：在使用`@Transactional`注解声明事务时，可能存在以下情况：
  1. 不同类中，事务方法A调用非事务方法B，事物具有传播性，事务正常执行
  2. 不同类中，非事务方法A调用事务方法B，事务生效；
  3. 同一个类中，事务方法A调用非事务方法B，事务生效；
  4. 同一个类中，非事务方法A调用事务方法B，事务失效，由于使用Spring AOP代理导致的，只有当事务方法被当前类意外的代码调用时，才会有Spring生成的代理对象来管理


#### 一人一单

1. `synchronized(userId.toString().intern())`：根据常量池中的字符串值来加锁，保证同一userid的用户使用同一把锁，否则`toString()`方法将返回不同的`String`对象

2. 使用`@Transcational()`是数据库事务需要整体加锁，否则可能事务失效

3. 对事务加锁：

   ```java
   Long userId = UserHolder.getUser().getId();
           synchronized (userId.toString().intern()) {
               // 对代理对象加锁
               IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
               return proxy.createVoucherOrder(voucherId);
           }
   ```

4. **分布式锁**

  1. 特点：**多进程可见** **互斥** 高可用 高性能 安全性

  2. 实现方案：

    1. MySQL
    2. Redis
    3. Zookeeper：利用节点的唯一性和有序性实现互斥

  3. 基于Redis的实现方案：`set lock thread1 nx ex ttl`，`nx`表示互斥，`ex`表示过期时间

     ![image-20240413232812375](https://raw.githubusercontent.com/moonchildink/image/main/imgs202404132328465.png)

     然而，在上图中的情况表明，可能存在线程执行结束释放其他线程锁的情况，显然会导致数据错误。

     解决方案：使用UUID+ThreadID生成唯一的Value，根据Value判断是否可以释放锁。

     ![image-20240413234507915](https://raw.githubusercontent.com/moonchildink/image/main/imgs202404132345049.png)

     上图的情况又表明，如果判断锁标识语句与删除锁语句之间存在间隔，也就是可能产生“*超时释放*”可能会导致删除的错误。因此，需要保证这两个动作的**原子性**

    1. 然而基于setnx实现的分布式锁依旧存在以下问题：
      1. 不可重入：同一个线程无法多次获取同一把锁(为什么要获取同一把锁？)
      2. 不可重试：获取锁只会尝试一次，没有重试机制
      3. 超时释放
      4. 主从一致

  4. 改进方法：使用Redisson

    1. 执行逻辑

       ![image-20240423212416399](https://raw.githubusercontent.com/moonchildink/image/main/imgs202404232124482.png)

    2. Question

      1. 为什么要设置锁重入？- 同一线程可能多次执行需要互斥性的业务，比如线程递归调用
      2. 为什么要设置计数？-避免误删锁的情况
      3. 锁设置多少TTL合理？-Redisson默认的锁超时时间是30seconds

    3. Redisson的特性：

      1. 可重入：使用hash结构记录线程id和重入次数
      2. 可重试：使用信号量和PubSub机制实现等待、唤醒，获取锁失败的重试机制
      3. 超时释放：利用watchDog，每隔一段时间充值超时时间
      4. 主从一致性：
        1. 产生原因：主节点失效时未完成向从节点消息同步
        2. 解决方法：MultiLock

5. 总结：

  1. 不可重入式Redis分布式锁
    1. 原理：利用`setnx`的互斥性；利用`ex`避免死锁；释放锁时判断线程表示
    2. 缺点：不可重入、无法重试、超时失效
  2. 可重入式Redis分布式锁
    1. 利用Hash结构，记录线程标识和重入次数；利用watchDog延续锁时间；利用信号量控制锁重试和等待
    2. 缺点：Redis宕机导致锁失效问题
  3. Redisson的multiLock
    1. 多个独立的Redis节点，必须在所有节点都获取重入锁，才算获取成功
    2. 缺点：实现复杂，成本高、

6. 抢购流程优化

  1. 将判断库存和写入订单的操作利用Redis进行，这一部分使用Lua脚本来操作，目的是为了**操作的原子性**

     ```lua
     local voucherId = ARGV[1]
     local userId = ARGV[2]
     
     local stockKey = 'seckill:stock:' .. voucherId
     local orderKey = 'seckill:order:' .. voucherId
     
     
     
     -- 判断库存
     if (tonumber(redis.call('get', stockKey)) <= 0) then
         return 1
     end
     
     -- 判断用户是否下单
     if (redis.call('sismember', orderKey, userId) == 1) then
         -- 说明重复下单
         return 2
     end
     
     -- 表示库存充足而且用户没有购买过
     -- 执行减库存
     redis.call('incrby', stockKey, -1)
     -- 指定记录用户ID
     redis.call('sadd', orderKey, userId)
     
     return 0
     
     ```

  2. 修改优惠券秒杀的逻辑，新的逻辑描述如下：

    1. 首先执行Lua脚本，判断用户是否已经购买（使用Set结构存储指定优惠券对应的用户ID），判断当前优惠券库存是否大于零
    2. 如果满足未购买、有库存条件，则：
      1. 将创建订单的操作使用消息队列来处理
    3. 当前逻辑的问题：存在内存安全问题（使用了就JVM提供的阻塞队列），数据安全问题

  3. 使用消息队列完成异步秒杀



### 关注推送的实现

1. 关注推送也叫Feed流，直译为投喂。

2. 常见模式：

  1. timeline：不做内容筛选，简单按照内容发布时间排序
  2. 智能排序：使用派速算法屏蔽违规、用户不感兴趣的内容。推送用户感兴趣的信息

3. 实现方案

  1. 拉模式：延迟高

  2. 推模式（写扩散）：缺点为内存占用较高，同一份消息需要推送给较多的用户

  3. 推拉结合：

     ![image-20240425130422189](https://raw.githubusercontent.com/moonchildink/image/main/imgs202404251304318.png)

   ![image-20240425130440628](https://raw.githubusercontent.com/moonchildink/image/main/imgs202404251304694.png)

4. 实现

  1. 新增笔记时推送到粉丝收件箱
  2. 收件箱按时间戳排序
  3. 使用分页查询收件箱数据
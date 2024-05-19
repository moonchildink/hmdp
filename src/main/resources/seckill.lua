local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

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
-- 发送消息到队列之中，XADD stream.orders * k1 v1...
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)

return 0

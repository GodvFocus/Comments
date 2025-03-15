-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]

local stockKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. voucherId
-- 判断库存是否充足 返回1
if( tonumber(redis.call("get", stockKey)) <= 0) then
    return 1
end
-- 判断用户是否重复下单 返回2
if(redis.call("sismember", orderKey, userId) == 1) then
    return 2
end
-- 扣减库存 存入set 返回0
redis.call("incrby", stockKey, -1)
redis.call("sadd", orderKey, userId)
return 0


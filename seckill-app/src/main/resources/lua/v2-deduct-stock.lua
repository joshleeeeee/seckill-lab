-- v2 库存扣减脚本。
--
-- 为什么要用 Lua：
-- Redis 会把一段 Lua 脚本作为一个原子操作执行，
-- 因此“检查库存”和“扣减库存”不会被其他请求插队打断。
--
-- 入参约定：
-- KEYS[1] -> 库存 key，例如：seckill:v2:stock:1001
-- 本脚本不使用 ARGV。
--
-- 返回码约定：
--  >= 0 : 扣减成功后的剩余库存
--   -1  : 已售罄（stock <= 0）
--   -2  : key 不存在（服务层应回源预热后重试）
--   -3  : 库存值非法（不是数字）

-- Lua 的数组/表下标从 1 开始，所以第一个 key 是 KEYS[1]。
local key = KEYS[1]

-- EXISTS：存在返回 1，不存在返回 0。
if redis.call('EXISTS', key) == 0 then
    return -2
end

-- GET 读到的是字符串，tonumber 用来转数字。
-- 如果转换失败（例如值是 "abc"），tonumber 会返回 nil。
local stock = tonumber(redis.call('GET', key))
if not stock then
    return -3
end

-- 库存已经小于等于 0，直接返回售罄。
if stock <= 0 then
    return -1
end

-- DECR 做原子减 1，并返回最新剩余库存。
return redis.call('DECR', key)

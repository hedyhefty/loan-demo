-- KEYS[1]: 用户可用额度 Redis Key (例如 loan:limit:user:123)
-- ARGV[1]: 本次申请金额 (amount)

local available = redis.call('GET', KEYS[1])
if not available then
    return 0  -- 额度未加载
end

available = tonumber(available)
local request = tonumber(ARGV[1])

if available >= request then
    -- 额度足够，原子扣减（Redis 7+ 使用 INCRBYFLOAT 负值）
    redis.call('INCRBYFLOAT', KEYS[1], -request)
    return 1  -- 成功
else
    return 0  -- 额度不足
end

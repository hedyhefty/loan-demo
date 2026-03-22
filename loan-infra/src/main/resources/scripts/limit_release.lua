-- KEYS[1]: 用户可用额度 Redis Key (例如 loan:limit:user:123)
-- ARGV[1]: 释放金额 (amount)

local available = redis.call('GET', KEYS[1])
if not available then
    return 0
end

available = tonumber(available)
local release_amount = tonumber(ARGV[1])

local new_available = available + release_amount
redis.call('SET', KEYS[1], new_available)
return 1

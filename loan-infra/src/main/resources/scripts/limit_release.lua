-- KEYS[1]: 用户额度的 Redis Key (例如 loan:limit:user:123)
-- ARGV[1]: 释放的金额 (amount)

local current_used = redis.call('GET', KEYS[1])
if not current_used then
    current_used = 0
else
    current_used = tonumber(current_used)
end

local release_amount = tonumber(ARGV[1])

-- 释放额度，确保不为负
local new_used = current_used - release_amount
if new_used < 0 then
    new_used = 0
end

redis.call('SET', KEYS[1], new_used)
return 1

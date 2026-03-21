-- KEYS[1]: 用户额度的 Redis Key (例如 loan:limit:user:123)
-- ARGV[1]: 本次申请金额 (amount)
-- ARGV[2]: 总额度上限 (max_limit)

local current_used = redis.call('GET', KEYS[1])
if not current_used then
    current_used = 0
else
    current_used = tonumber(current_used)
end

local request_amount = tonumber(ARGV[1])
local max_limit = tonumber(ARGV[2])

-- 判断：已用额度 + 本次申请 是否超过 总上限
if (current_used + request_amount) <= max_limit then
    -- 未超限，增加已用额度
    redis.call('INCRBYFLOAT', KEYS[1], request_amount)
    return 1 -- 成功
else
    return 0 -- 失败：额度不足
end
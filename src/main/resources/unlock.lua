-- 判断是否一致
if(redis.call("get", KEYS[1]) == ARGV[1]) then
    -- 如果一致则释放
    return redis.call("del", KEYS[1])
end
return 0
local stock = redis.call('GET', KEYS[1])
if not stock or tonumber(stock) <= 0 then
    return -1
end
redis.call('DECR', KEYS[1])
return 1

-- lua脚本，实现判断锁标识和释放锁原子性
-- KEYS[1] 锁的名称
local val = redis.call('get',KEYS[1])--锁里面的线程id
local id = ARGV[1]--当前线程id

if(val == id) then
    -- 一致就释放锁
    return redis.call('del',KEYS[1])
end
return 0
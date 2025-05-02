-- lua脚本，基于redis实现可重入锁,获取锁
-- KEYS[1] 锁的名称

local key = KEYS[1] --锁的名称
local threadId = ARGV[1] --当前线程id
local releaseTime = ARGV[2] --锁的自动释放时间

--判断key这个锁是否存在
if(redis.call('get',key) == 0)then
    --不存在,获取锁,set key,hash,value
    redis.call('sey',key,threadId,'1');
    --设置有效期
    redis.call('expire',key,releaseTime);
    return 1;
end;
--锁存在，判断是否是自己的锁
if (redis.call('hexists',key,threadId) == 1) then
    --是自己的锁
    redis.call('hincrby',key,threadId,'1');
    --设置有效期
    redis.call('expire',key,releaseTime);
    return 1;
end;
--代码走到这里，说不锁不是自己的,获取锁失败
return 0;


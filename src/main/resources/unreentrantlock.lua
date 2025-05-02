-- lua脚本，基于redis实现可重入锁，释放锁
-- KEYS[1] 锁的名称

local key = KEYS[1] --锁的名称
local threadId = ARGV[1] --当前线程id
local releaseTime = ARGV[2] --锁的自动释放时间

--判断key这个锁是否是自己的锁
if(redis.call('hexists',key,threadId) == 0)then
    --不是自己的锁
    return nil;
end;
--是自己的锁
local count = redis.call('hincrby',key,threadId,-1);
if (count == 0) then --判断count是否为0
    --是0，释放锁
    redis.call('del',key)
    return nil;
else
    --代码走到这个，说明不是0，就刷新有效期
    --设置有效期
    redis.call('expire',key,releaseTime);
    return nil;
end
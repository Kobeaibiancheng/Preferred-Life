-- lua脚本


--1.参数列表
--1.优惠卷id
local voucherId = ARGV[1]
--2.用户id
local userId = ARGV[2]


--3.库存key
local stockKey = 'seckill:stock' .. voucherId
--4.订单key
local orderKey = 'seckill:order' .. voucherId


--5.判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

--6.判断用户是否下单  sismember orderKey userId
if(redis.call('sismember',orderKey,userId) == 1) then
    return 2
end

--7.扣库存，下单 incrby stockKey -1
redis.call('incrby',stockKey,-1)
--8.下单
redis.call('sadd',orderKey,userId)
return 0
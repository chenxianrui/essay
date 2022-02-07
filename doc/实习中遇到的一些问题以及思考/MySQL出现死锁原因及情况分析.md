### MySQL出现死锁原因及情况分析 

#### 现象
  某天凌晨营销侧的系统在飞书上出现大量告警，根据报警消息能看的出是MySQL出现了死锁。报错信息如下类似（可能涉及保密协议，所以就用类似的代替）：
    
    createSQLException MySQLTransactionRollbackException Deadlock found when trying to get lock; try restarting transation SQLError.java
    com.mysql.cj.jdbc.exceptions.SQLError in createSQLException  

由上述信息可知，是MySQL出现了死锁。

#### 背景情况
MySQL版本号为：5.7，数据库引擎使用的是：Innodb，事务隔离级别：READ-COMMITTED（读已提交）。
发生死锁的表结构及索引情况类似如下：
````
CREATE TABLE `coupon_goods` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `coupon_id` int(11) NOT NULL COMMENT '券实体id',
  `sku_id` bigint(20) NOT NULL COMMENT '商品ID',
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT '删除状态',
  PRIMARY KEY (`id`),
  KEY `idx_sku` (`sku_id`),
  KEY `idx_coupon_id` (`coupon_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5187350 DEFAULT CHARSET=utf8mb4 COMMENT='优惠券可用商品';
````
    隐去部分信息字段
该表共有三个索引，1 个聚簇索引(主键索引)，2 个非聚簇索引(非主键索引)。 

    聚簇索引： 
    PRIMARY KEY (`id`)
    非聚簇索引： 
    KEY `idx_sku` (`sku_id`),
    KEY `idx_coupon_id` (`coupon_id`)

#### 死锁日志
发生死锁的日志：
````
------------------------
LATEST DETECTED DEADLOCK
------------------------
2021-11-18 00:19:06 0x7fbae0d7f700
*** TRANSACTION:
TRANSACTION 128871994, ACTIVE 2 sec fetching rows
mysql tables in use 3, locked 3
317 lock struct(s), heap size 41168, 96 row lock(s), undo log entries 23
MySQL thread id 17900657, OS thread handle 140440612894464, query id 1538707159 10.100.131.193 coupon Searching rows for update
UPDATE coupon_goods  SET is_delete=1,update_time='2021-11-18 00:19:04.728'  
 WHERE  is_delete=0

AND (coupon_id = 200643 AND sku_id IN (1922484))
*** HOLDS THE LOCK:
RECORD LOCKS space id 35 page no 7413 n bits 824 index idx_coupon_id_is_delete of table `coupon`.`coupon_goods` trx id 128871994 lock_mode X locks rec but not gap
Record lock, heap no 732 PHYSICAL RECORD: n_fields 3; compact format; info bits 32
 0: len 4; hex 80002982; asc   ) ;;
 1: len 1; hex 80; asc  ;;
 2: len 8; hex 0000000000459915; asc      E  ;;

***  WAITING FOR THIS LOCK TO BE GRANTED, WHICH CONFLICTS WITH THE LOCK HELD BY TRANSACTION 128872002:
RECORD LOCKS space id 35 page no 7383 n bits 952 index idx_coupon_id_is_delete of table `coupon`.`coupon_goods` trx id 128871994 lock_mode X locks rec but not gap waiting
Record lock, heap no 842 PHYSICAL RECORD: n_fields 3; compact format; info bits 32
 0: len 4; hex 80030fc3; asc     ;;
 1: len 1; hex 80; asc  ;;
 2: len 8; hex 0000000000459d1f; asc      E  ;;


*** TRANSACTION:
TRANSACTION 128872002, ACTIVE 1 sec fetching rows
mysql tables in use 3, locked 3
LOCK WAIT 26 lock struct(s), heap size 3520, 25 row lock(s), undo log entries 6
MySQL thread id 17899913, OS thread handle 140440964916992, query id 1538706170 10.100.128.102 coupon Searching rows for update
UPDATE coupon_goods  SET is_delete=1,update_time='2021-11-18 00:19:05.147'  
 WHERE  is_delete=0

AND (coupon_id = 10626 AND sku_id IN (3706865))
*** HOLDS THE LOCK:
RECORD LOCKS space id 35 page no 7383 n bits 952 index idx_coupon_id_is_delete of table `coupon`.`coupon_goods` trx id 128872002 lock_mode X locks rec but not gap
Record lock, heap no 842 PHYSICAL RECORD: n_fields 3; compact format; info bits 32
 0: len 4; hex 80030fc3; asc     ;;
 1: len 1; hex 80; asc  ;;
 2: len 8; hex 0000000000459d1f; asc      E  ;;

***  WAITING FOR THIS LOCK TO BE GRANTED, WHICH CONFLICTS WITH THE LOCK HELD BY TRANSACTION 128871994:
RECORD LOCKS space id 35 page no 7413 n bits 824 index idx_coupon_id_is_delete of table `coupon`.`coupon_goods` trx id 128872002 lock_mode X locks rec but not gap waiting
Record lock, heap no 732 PHYSICAL RECORD: n_fields 3; compact format; info bits 32
 0: len 4; hex 80002982; asc   ) ;;
 1: len 1; hex 80; asc  ;;
 2: len 8; hex 0000000000459915; asc      E  ;;

*** WE ROLL BACK TRANSACTION 128872002
````
关键日志信息：

1.发生死锁的两条sql语句 

    UPDATE coupon_goods  SET is_delete=1,update_time='2021-11-18 00:19:04.728'  
     WHERE  is_delete=0
    
    AND (coupon_id = 200643 AND sku_id IN (1922484))
    UPDATE coupon_goods  SET is_delete=1,update_time='2021-11-18 00:19:05.147'  
     WHERE  is_delete=0
    
    AND (coupon_id = 10626 AND sku_id IN (3706865))

2.解读

    a.事务128871994持有索引idx_coupon_id_is_delete的锁同时等待事务128872002持有的索引idx_coupon_id_is_delete锁 
    b.事务128872002持有索引idx_coupon_id_is_delete的锁同时等待事务128871994持有的索引idx_coupon_id_is_delete锁 
    c.所以两个事务之间产生了循环等待，发生了死锁 
    d.两个事务持有的锁均为：lock_mode X locks rec but not gap 
    
    
#### 问题排查
    


#### 解决方法

#### 总结
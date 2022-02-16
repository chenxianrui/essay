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
    e.lock_mode X locks rec but not gap 直译就是‘X锁模式锁住了记录但是只对该记录加锁’。因此是没有对范围产生影响的。
    f.由上可知两个事务对记录加的是 X 锁和 not gap 锁，和间隙锁是没有关系的。
    
3.锁类型

    a.X锁，写锁或排他锁。若事务T对该事务加上写锁，那其他事务就不能对该数据加上其他类型的锁，但是事务T可以对该数据进行读取或修改。
    b.S锁，读锁。若事务T对该数据加上读锁，那其他事务只能对该数据加上读锁，而不能加其他类型的锁，直到已释放所有的读锁。
    c.LOCK_ORDINARY(Next-Key Lock)，记录锁+GAP锁。锁定一个范围包括该记录本身。
    d.LOCK_GAP，间隙锁。锁定一个范围，但不包括记录本身。
    e.LOCK_REC_NOT_GAP，记录锁。锁定该记录本身。
    
    
#### 问题排查
   RC隔离级别下是没有范围锁的，因此该死锁产生与间隙锁和 Next-Key Lock 是无关的。前面的死锁日志也表明了这一点。那就很有可能是
   代码层面出了问题。
   经过排查发现是因为在mybatis的xml文件中update语句中入参是map类型，该代码实现的内容是利用kafka监听商品服务的数据库信息的改变，进而
   修改营销服务这边的相关券信息。类似于下面这种语句：
   
![在这里插入图片描述](https://img-blog.csdnimg.cn/fff52a2419364d52917f568f1895222c.png)

    众所周知map插入顺序与遍历顺序是不一样的。根据后续的排查发现出现死锁的原因是多线程情况下该语句下两个map都有a与b对象，但是a，b这两个对象的hashcode是相同的
hashcode相同的对象根据map的put原理，他俩是在同一个桶下，属于同一个链条。可能是因为二者插入顺序的不同导致了X-map是a对象排在b对象前头，Y-map是b对象排在
a对象前头。导致二者在入库时互相持有当前锁又请求对方所持有的锁造成了死锁现象的出现。
#### 解决方法
    1.改变入参形式，使用顺序集合列表。
    2.每次update一次就提交一次事务释放锁。
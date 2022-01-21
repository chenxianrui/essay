### 支付数据库高CPU
#### 现象
21年11月下旬晚上近十点支付服务数据库产生慢sql告警

![慢SQL](https://img-blog.csdnimg.cn/78cd4014b72d402e8b1596cb7e159251.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Z2e6buRaWk=,size_20,color_FFFFFF,t_70,g_se,x_16)

此时查看数据库CPU也出现了异常的升高，此前一直稳定在1%左右，在当晚的九点半至十点这段时间飙升到了17%左右。
#### 问题排查
排查后发现是出现了大量的类似 **select * from [table] where order_id = 1** 的sql语句。
这语句乍看下是没什么问题的，但是经过比对代码和mybatis中的xml文件sql语句后发现，**order_id**字段在ddl中是字符串类型，代码传入的类型是数字类型。
所以导致了该sql语句没有走索引而是做了全表查询。实践才是检验真理的唯一标准，请看下方的例子。
##### 例子
表结构：

![在这里插入图片描述](https://img-blog.csdnimg.cn/f705278c2d1a4600b0eecb206d0b6da2.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Z2e6buRaWk=,size_20,color_FFFFFF,t_70,g_se,x_16)

上述中的**id**字段字符串类型。利用**explain**显示索引信息获得的结果：

![图1](https://img-blog.csdnimg.cn/26c0b89dd31449ea9ef0c48491d2e8ee.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Z2e6buRaWk=,size_20,color_FFFFFF,t_70,g_se,x_16)
![图2](https://img-blog.csdnimg.cn/5fbcadd5f2ad4ceab557f0f08cf4db3c.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Z2e6buRaWk=,size_20,color_FFFFFF,t_70,g_se,x_16)

上述两张图片可以清晰的看出两种相同的查询方式，只不过改变了查询条件引用的类型，紧跟着通过**type**字段我们可以
看到数据库的查询表的方式也发生了变化。第一张图走了全表扫描，第二张图则是使用了索引进行查询。下面简单的说明**type**字段
会产生哪些信息。

##### type
- all：此类型为全表扫描，性能最差。
- ref：此类型通常出现在多表的 join 查询，针对于非唯一或非主键索引，或者是使用了 最左前缀 规则索引的查询。
- system：此类型利用主键或者唯一索引进行查询，只能返回一条数据。（通过某列或者多列确认唯一的一条数据就会产生这个类型）
- eqref：此类型通常出现在多表的 join 查询，表示对于前表的每一个结果，都只能匹配到后表的一行结果。
- range：此类型为使用索引范围查询，像一些使用了=，>，<等操作。
- index：此类型为全索引扫描，与全表扫描是有区别的，all针对数据，index针对索引。

通过前面的排查可以发现是慢sql导致了数据库cpu飙升，如果只是为数不多的查询的话是不太可能会产生这种现象的。所以必然是有其他的服务
抑或是别人恶意请求导致的结果。后续继续排查，定位到了是基础技术平台的同事在上述时间端在线上做了数据导入es的操作，
其中一些操作对主库做了查询，从而导致了db出现慢查询。

#### 解决方法
1.更改代码
2.不能对主库进行读取操作。

#### 类型转换为什么会产生慢查询？

#### 总结
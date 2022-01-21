### 支付数据库高CPU
#### 现象
某天晚上近十点支付服务数据库产生慢sql告警。
此时查看数据库CPU也出现了异常的升高，此前一直稳定在1%左右，在当晚的九点半至十点这段时间飙升到了17%左右。
#### 问题排查
排查后发现是出现了大量的类似 **select * from [table] where order_id = 1** 的sql语句。
这语句乍看下是没什么问题的，但是经过比对代码和mybatis中的xml文件sql语句后发现，**order_id**字段在ddl中是字符串类型，代码传入的类型是数字类型。
所以导致了该sql语句没有走索引而是做了全表查询。在mysql查询中，当查询条件左右两侧类型不匹配的时候会发生**隐式转换**，可能导致查询无法使用索引。实践才是检验真理的唯一标准，请看下方的例子。
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

#### 隐式转换
在mysql中，如果ddl中的字段类型与传入的参数类型不匹配的时候，mysql就会将传入的参数进行强制的类型转换。这种类型转换就可能会导致慢查询的出现。
就比如上述的整型转字符串就出现了这种情况。那反过来字符串转整型会出现吗？答案是不会。

##### 那为什么整型转字符串就会出现呢？

因为传入整型参数时，mysql会将全表字符串类型转换成整型最后再进行比较。比如数据为"1","1s"的都会转换成1，"abc"等字母就会转换成0(看下图)。
![在这里插入图片描述](https://img-blog.csdnimg.cn/6d670ebff19d4621be3b789b3f5be93a.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Z2e6buRaWk=,size_20,color_FFFFFF,t_70,g_se,x_16)

上图中的记录并没有包含0的数据，但是传入0参数返回的却是数据为a的记录，这就说明这些带有字母的列数据会自动转换成0再进行查询。

隐式转换中还会产生一个也是全表查询的情况。

在利用in进行查询的时候，不论字段类型是什么，只要in中同时包含数字与字符串两种类型也会触发全表查询。
![在这里插入图片描述](https://img-blog.csdnimg.cn/c0ec784389ba44aaaf3c6110a023367f.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Z2e6buRaWk=,size_20,color_FFFFFF,t_70,g_se,x_16)
![在这里插入图片描述](https://img-blog.csdnimg.cn/23627bc8cb264494a061808f02570f93.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Z2e6buRaWk=,size_20,color_FFFFFF,t_70,g_se,x_16)
如上图第一张走了全表查询，第二张走的是范围索引查询。
#### 总结
1.在业务开发中尽量避免慢查询sql的出现，以免对数据库造成不必要的负担。

2.不要对主库进行查询操作。


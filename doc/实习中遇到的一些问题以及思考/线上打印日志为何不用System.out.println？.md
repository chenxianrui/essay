### 关于System.out.println()的一些事情
#### 前言
实习时发现某服务cpu飙升，排查后发现是部分代码中有system.out.println()(可能是忘记删了)。

我们将围绕以下两个方面展开来讲为啥线上打印日志不用System.out.println()。
1. System.out.println()底层源码实现。
2. Log4j作为日志工具比System.out.println()拥有哪些优点。

#### 1.System.out.println()是同步的
我们通过查看源码可以发现，它是线程安全的，是一种同步方法，底层方法中的部分代码块是被**synchronized**所修饰。所以多线程的情况下性能低下是可能的。

![synchronized 图标](https://img-blog.csdnimg.cn/f9a04b94cd944f86b695c799d7947a52.png?x-oss-process=image/watermark,type_d3F5LXplbmhlaQ,shadow_50,text_Q1NETiBA6Z2e6buRaWk=,size_20,color_FFFFFF,t_70,g_se,x_16)

##### synchronized 作用
被synchronized所修饰的代码块它会有产生三种作用：

1.原子性：



原子性是指该代码块中的操作要么全部执行成功，要么全部执行失败。比如

2.可见性

3.有序性




所以注定着它打印的效率是不可能快的。

#### 2.Log4j的优点


#### 总结 

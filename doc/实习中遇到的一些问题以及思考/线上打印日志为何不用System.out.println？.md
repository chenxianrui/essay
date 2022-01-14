### 关于System.out.println()的一些事情
#### 前言
实习时发现某服务cpu飙升，通过排查后发现是代码中有system.out.println()(可能是忘记删了)。

我们将围绕以下两个方面展开来讲为啥线上打印日志不用System.out.println()。
1. System.out.println()底层源码实现。
2. Log4j作为日志工具比System.out.println()拥有哪些优点。

#### System.out.println()源码
我们通过查看源码可以发现，它是线程安全的，底层方法被**synchronized**所修饰。

![synchronized 图标](https://github.com/chenxianrui/essay/blob/master/doc/img/sout-syn.jpg)

#### Log4j的优点


#### 总结 


Java面试八股文（高频考点完整版）

# 一、Java基础篇（必问）

## 1. 面向对象和面向过程的区别

面向过程：以“步骤”为核心，分析解决问题的流程，用函数实现每个步骤，调用函数完成需求，性能高，适合单片机、嵌入式等场景，耦合度高、可维护性差。

面向对象：以“对象”为核心，将问题拆解为多个对象，描述对象的属性和行为，具备封装、继承、多态三大特性，易维护、易复用、易扩展，适合复杂项目开发，性能略低于面向过程。

## 2. 封装、继承、多态的具体含义

封装：将对象的属性（数据）和行为（方法）捆绑在一起，隐藏内部实现细节，仅对外提供有限访问接口（如get/set方法），避免外部直接操作数据，提高代码安全性和可维护性。

继承：子类继承父类的公共属性和方法，可新增自身属性和方法，也可重写父类方法，减少代码冗余，实现代码复用。注意：Java单继承（一个子类只能有一个父类），但支持多实现（一个类可实现多个接口）。

多态：同一消息作用于不同对象，产生不同的执行结果，分为编译时多态（方法重载）和运行时多态（方法重写）。运行时多态的前提：子类继承父类、子类重写父类方法、父类引用指向子类对象。例如：Animal类有call()方法，Dog和Cat子类重写该方法，Animal a = new Dog()时，a.call()执行Dog的实现。

## 3. Java基本数据类型和包装类

基本数据类型（8种）：byte（1字节）、short（2字节）、int（4字节）、long（8字节）、float（4字节）、double（8字节）、char（2字节）、boolean（1字节），直接存储值，不具备对象特性。

包装类：对应8种基本类型，分别是Byte、Short、Integer、Long、Float、Double、Character、Boolean，是引用类型，继承Object类，提供了大量操作方法（如类型转换、数值计算）。

自动装箱与拆箱：装箱（基本类型→包装类，如int→Integer，调用Integer.valueOf()）；拆箱（包装类→基本类型，如Integer→int，调用Integer.intValue()），JDK1.5后自动实现，避免手动转换的繁琐。

## 4. == 和 equals() 的区别

==：比较的是变量在栈内存中存储的地址（或基本类型的值）。如果是基本类型，比较的是具体数值；如果是引用类型，比较的是两个对象是否指向同一个内存地址（是否为同一个对象）。

equals()：是Object类的方法，默认实现是 ==（比较地址）；子类可重写该方法，用于比较两个对象的内容是否相等（如String、Integer等已重写）。例如："abc".equals("abc")返回true，new String("abc") == new String("abc")返回false（地址不同），但equals返回true。

注意：重写equals()必须重写hashCode()，否则会导致HashSet、HashMap等集合无法正常工作（集合判断元素是否重复时，先比较hashCode，再比较equals）。

## 5. String、StringBuffer、StringBuilder 的区别

String：不可变字符串，底层是final修饰的字符数组，每次修改（如拼接、替换）都会创建新的String对象，效率低，适合少量、不频繁修改的字符串场景。

StringBuffer：可变字符串，线程安全（方法加了synchronized锁），底层是可扩容的字符数组，修改时直接操作原数组，效率高于String，适合多线程环境下频繁修改字符串的场景。

StringBuilder：可变字符串，线程不安全（无锁），底层与StringBuffer一致，效率比StringBuffer更高，适合单线程环境下频繁修改字符串的场景（日常开发首选）。

## 6. 异常体系

Java异常继承自Throwable，分为两大类：

Error（错误）：JVM层面的异常，无法通过代码捕获和处理，如OutOfMemoryError（内存溢出）、StackOverflowError（栈溢出），通常是程序运行环境或硬件问题导致。

Exception（异常）：程序可处理的异常，分为受检异常（编译期必须处理，如IOException、SQLException）和非受检异常（运行时异常，编译期不强制处理，如NullPointerException、ArrayIndexOutOfBoundsException）。

异常处理方式：try-catch-finally（捕获并处理异常，finally无论是否发生异常都会执行，常用于释放资源）、throws（声明异常，由调用者处理）、throw（手动抛出异常）。

# 二、Java集合篇（高频）

## 1. Collection 和 Map 的区别

Collection：单列集合，存储单个元素，继承Iterable接口，核心实现类有List（有序、可重复）、Set（无序、不可重复）。

Map：双列集合，存储键值对（key-value），key唯一，value可重复，核心实现类有HashMap、Hashtable、ConcurrentHashMap、TreeMap。

## 2. ArrayList 和 LinkedList 的区别

ArrayList：底层基于动态数组实现，初始容量10，扩容机制为原容量的1.5倍（JDK1.8），查询效率高（通过索引直接访问，O(1)），增删效率低（需移动数组元素，O(n)），适合查询频繁、增删少的场景。

LinkedList：底层基于双向链表实现，无初始容量，增删效率高（仅需修改链表指针，O(1)），查询效率低（需遍历链表，O(n)），适合增删频繁、查询少的场景。

## 3. HashMap 底层原理（JDK1.8）

底层结构：数组+链表+红黑树（当链表长度>8且数组长度>64时，链表转为红黑树；当链表长度<6时，红黑树转回链表），数组默认初始容量16，负载因子0.75，扩容时容量翻倍（始终是2的幂次）。

哈希冲突解决：采用链地址法（链表/红黑树存储冲突的元素）。

put方法流程：1. 计算key的hash值（通过hashCode() ^ (hashCode() >>> 16) 减少哈希冲突）；2. 根据hash值计算数组索引；3. 若索引位置为空，直接插入元素；4. 若索引位置有元素，判断key是否相同（==或equals），相同则覆盖value；5. 不同则插入链表/红黑树；6. 插入后判断是否需要扩容（元素个数>容量×负载因子）。

线程安全：HashMap线程不安全，多线程环境下可能出现死循环、数据丢失等问题，解决方案：使用ConcurrentHashMap或Collections.synchronizedMap()。

## 4. HashMap 和 Hashtable 的区别

线程安全：HashMap不安全；Hashtable安全（方法加了synchronized锁）。

key值：HashMap允许key为null；Hashtable不允许key和value为null。

底层结构：HashMap是数组+链表+红黑树；Hashtable是数组+链表（无红黑树）。

扩容机制：HashMap初始容量16，扩容翻倍；Hashtable初始容量11，扩容为原容量×2+1。

## 5. ConcurrentHashMap 底层原理（JDK1.8）

底层结构：与HashMap一致（数组+链表+红黑树），线程安全的实现方式：放弃Hashtable的全局锁，采用“CAS+ synchronized”的分段锁机制（对数组的每个索引位置加锁，而非全局锁），提高并发效率。

核心优势：并发安全且效率高于Hashtable，支持多线程同时读写，适合高并发场景。

# 三、Java多线程篇（重中之重）

## 1. 进程和线程的区别

进程：操作系统分配资源的基本单位，一个运行中的程序就是一个进程（如运行的IDEA、MySQL），拥有独立的内存空间，进程间切换开销大，一个进程崩溃通常不影响其他进程。

线程：CPU调度的基本单位，一个进程可包含多个线程，线程共享进程的内存资源（如堆、方法区），线程间切换开销小，一个线程异常可能导致整个进程崩溃，线程间通信更便捷。

## 2. 线程的创建方式（4种）

\1. 继承Thread类：重写run()方法，调用start()方法启动线程（start()会触发JVM创建新线程，执行run()；直接调用run()只是普通方法调用，不会创建新线程）。

示例：class MyThread extends Thread { @Override public void run() { System.out.println("线程执行"); } }，使用new MyThread().start()。

\2. 实现Runnable接口：重写run()方法，将其传入Thread对象，调用start()启动，避免单继承的限制。

示例：class MyRunnable implements Runnable { @Override public void run() { System.out.println("线程执行"); } }，使用new Thread(new MyRunnable()).start()。

\3. 实现Callable接口：重写call()方法，有返回值、可抛出异常，配合FutureTask获取返回结果，适合需要获取线程执行结果的场景。

示例：class MyCallable implements Callable<String> { @Override public String call() { return "执行结果"; } }，使用FutureTask<String> task = new FutureTask<>(new MyCallable()); new Thread(task).start(); task.get()获取结果。

\4. 使用线程池：通过Executors工具类或ThreadPoolExecutor创建线程池，复用线程，减少线程创建/销毁开销，实际开发首选（避免频繁手动创建线程）。

## 3. Runnable 和 Callable 的区别

返回值：Runnable无返回值；Callable有返回值，可通过Future获取。

异常：Runnable的run()方法不能抛出受检异常；Callable的call()方法可抛出异常。

方法名：Runnable重写run()方法；Callable重写call()方法。

## 4. 线程的状态（6种，JDK1.8）

NEW（新建）：线程刚创建，未调用start()方法，未进入JVM调度。

RUNNABLE（可运行）：调用start()后，线程进入该状态，要么正在执行，要么等待CPU调度。

BLOCKED（阻塞）：线程等待获取同步锁（如synchronized锁），无法执行。

WAITING（无限等待）：线程无限期等待，需其他线程调用notify()/notifyAll()唤醒（如调用Object.wait()且未指定超时时间）。

TIMED_WAITING（限时等待）：线程在指定时间内等待，超时自动唤醒（如Thread.sleep(long)、Object.wait(long)）。

TERMINATED（终止）：线程执行完毕或异常终止，生命周期结束。

## 5. sleep() 和 wait() 的区别

所属类：sleep()是Thread类的静态方法；wait()是Object类的方法。

锁释放：sleep()不会释放持有锁；wait()会释放持有锁，需在同步代码块/同步方法中使用。

唤醒方式：sleep()超时自动唤醒；wait()需其他线程调用notify()/notifyAll()唤醒，或超时自动唤醒。

使用场景：sleep()用于暂停线程执行一段时间；wait()用于线程间通信（如生产者-消费者模式）。

## 6. synchronized 和 volatile 的区别

synchronized：可修饰方法、代码块，保证原子性、可见性、有序性，是重量级锁（JDK1.8优化后有偏向锁、轻量级锁、重量级锁的升级过程），会导致线程阻塞。

volatile：修饰变量，仅保证可见性（一个线程修改变量后，其他线程能立即看到最新值）和有序性（禁止指令重排），不保证原子性，不会导致线程阻塞，适合修饰单线程写、多线程读的变量（如状态标记）。

## 7. 线程池的核心参数（七大参数）

ThreadPoolExecutor的七大参数，决定线程池的工作机制：

\1. corePoolSize：核心线程数，线程池中长期存活的线程数（即使空闲，也不会销毁，除非设置allowCoreThreadTimeOut(true)）。

\2. maximumPoolSize：最大线程数，线程池能容纳的最大线程数（核心线程数+非核心线程数）。

\3. keepAliveTime：非核心线程的空闲存活时间，超过该时间，非核心线程会被销毁。

\4. unit：keepAliveTime的时间单位（如TimeUnit.SECONDS、TimeUnit.MILLISECONDS）。

\5. workQueue：任务队列，用于存储等待执行的任务（如ArrayBlockingQueue、LinkedBlockingQueue）。

\6. threadFactory：线程工厂，用于创建线程（可自定义线程名称、优先级等）。

\7. handler：拒绝策略，当线程池满（达到最大线程数+队列满）时，处理新任务的方式（4种：AbortPolicy（抛出异常，默认）、CallerRunsPolicy（由调用线程执行）、DiscardPolicy（丢弃任务）、DiscardOldestPolicy（丢弃队列中最老的任务））。

## 8. 死锁的产生条件和解决方案

死锁产生的4个必要条件（缺一不可）：

\1. 互斥条件：资源只能被一个线程持有，无法共享。

\2. 请求与保持条件：线程持有一个资源，同时请求另一个被其他线程持有的资源，且不释放自身已持有的资源。

\3. 不可剥夺条件：线程持有的资源，无法被其他线程强制剥夺，只能由自身释放。

\4. 循环等待条件：多个线程形成循环等待资源的链条（如A等B的资源，B等C的资源，C等A的资源）。

解决方案：破坏任意一个必要条件即可，常用方式：1. 按固定顺序获取资源（破坏循环等待）；2. 超时释放资源（破坏请求与保持、不可剥夺）；3. 减少锁的持有时间，避免长期占用资源。

# 四、JVM篇（核心难点）

## 1. JVM 内存结构（JDK1.8）

JVM内存分为5个区域，其中堆和方法区是线程共享的，栈、本地方法栈、程序计数器是线程私有（每个线程一份）。

\1. 程序计数器：存储当前线程执行的字节码指令地址，无OOM（内存溢出），是JVM中唯一不会抛出OOM的区域。

\2. 虚拟机栈：存储线程的方法调用栈帧（每个方法调用对应一个栈帧，包含局部变量表、操作数栈、返回地址等），栈深度过大会抛出StackOverflowError（栈溢出），栈内存不足会抛出OOM。

\3. 本地方法栈：与虚拟机栈类似，用于执行本地方法（native方法），同样会抛出StackOverflowError和OOM。

\4. 堆：JVM最大的内存区域，存储对象实例和数组，是垃圾回收（GC）的主要区域，分为年轻代（Eden区、Survivor0区、Survivor1区，比例8:1:1）和老年代，堆内存不足会抛出OOM。

\5. 方法区（元空间，JDK1.8替代永久代）：存储类信息、常量、静态变量、方法字节码等，元空间使用本地内存，默认无上限（可通过参数限制），内存不足会抛出OOM。

## 2. 类加载机制（双亲委派模型）

类加载器的类型（从父到子）：

\1. 启动类加载器（Bootstrap ClassLoader）：加载JDK核心类（如rt.jar中的类），由C++实现，无父加载器。

\2. 扩展类加载器（Extension ClassLoader）：加载JDK扩展类（如ext目录下的类），父加载器是启动类加载器。

\3. 应用程序类加载器（Application ClassLoader）：加载项目中的类（classpath下的类），父加载器是扩展类加载器，是默认的类加载器。

双亲委派模型：类加载时，先委托父加载器加载，父加载器无法加载（找不到类），再由子加载器自己加载。优势：避免类重复加载，保证核心类的安全性（如防止自定义java.lang.String类替代核心类）。

## 3. 垃圾回收（GC）相关

垃圾判断标准：1. 引用计数法（简单但无法解决循环引用问题，如A引用B，B引用A，两者都无其他引用，引用计数不为0，无法回收）；2. 可达性分析算法（JVM默认使用，以GC Roots为起点，遍历对象引用链，不可达的对象视为垃圾，GC Roots包括虚拟机栈中引用的对象、方法区中静态变量引用的对象等）。

垃圾回收算法：

\1. 标记-清除算法：先标记垃圾对象，再清除，效率低，会产生大量内存碎片。

\2. 标记-复制算法：将内存分为两块，标记后将存活对象复制到另一块内存，清除原内存，无内存碎片，效率高，适合年轻代（Eden区和Survivor区的回收）。

\3. 标记-整理算法：标记后将存活对象移动到内存一端，清除另一端的垃圾，无内存碎片，效率介于前两者之间，适合老年代。

\4. 分代回收算法：结合上述算法，年轻代用标记-复制算法（对象存活时间短，回收频繁），老年代用标记-清除/标记-整理算法（对象存活时间长，回收频率低）。

常见垃圾收集器：SerialGC（串行GC，单线程回收，适合小型应用）、ParallelGC（并行GC，多线程回收，注重吞吐量）、CMSGC（并发GC，注重响应时间，老年代回收）、G1GC（区域化分代回收，兼顾吞吐量和响应时间，JDK1.7后推出）。

# 五、Spring篇（后端必问）

## 1. Spring 核心思想

Spring的核心是IOC（控制反转）和AOP（面向切面编程），核心目的是解耦，简化开发。

IOC（控制反转）：将对象的创建、依赖管理的控制权从业务代码转移到Spring容器，而非由业务代码主动new对象。传统开发中，UserService需主动new UserDao，耦合度高；IOC下，Spring容器负责实例化对象、注入依赖，业务代码仅通过@Autowired等注解获取对象，实现解耦。IOC的具体实现是DI（依赖注入），两者常等同提及。

AOP（面向切面编程）：将日志、事务、权限等通用功能（切面）与业务逻辑分离，在不修改业务代码的前提下，通过动态代理为业务方法增强功能，提高代码复用性和可维护性。

## 2. Spring Bean 的生命周期

核心流程（简化版）：

\1. 实例化：Spring通过反射机制创建Bean实例（无参构造器）。

\2. 属性填充：通过@Autowired、@Resource等注解注入依赖对象（DI过程）。

\3. 初始化：执行BeanNameAware的setBeanName()（获取Bean名称）、BeanFactoryAware的setBeanFactory()（获取容器）；执行@PostConstruct注解的方法、InitializingBean的afterPropertiesSet()方法、自定义init-method；执行BeanPostProcessor的前置/后置处理（如AOP代理创建）。

\4. 使用：容器将Bean提供给业务代码使用。

\5. 销毁：容器关闭时，执行@PreDestroy注解的方法、DisposableBean的destroy()方法、自定义destroy-method。

## 3. 依赖注入（DI）的三种方式

\1. 构造器注入：通过类的构造方法注入依赖，@Autowired标注构造器（JDK1.8+可省略），优点是依赖不可为空、对象创建后状态完整，便于单元测试；缺点是依赖过多时构造方法参数冗长。

\2. Setter注入：通过setXxx()方法注入依赖，优点是灵活，可动态修改依赖；缺点是依赖可能为空，对象创建后状态不完整。

\3. 字段注入：直接在字段上标注@Autowired/@Resource，优点是代码简洁、开发效率高；缺点是耦合度高，无法脱离Spring容器进行单元测试，日常开发常用，但不推荐在核心代码中使用。

## 4. @Autowired 和 @Resource 的区别

来源：@Autowired是Spring提供的注解；@Resource是JDK提供的注解（javax.annotation.Resource）。

匹配方式：@Autowired按类型（byType）匹配，若有多个同类型Bean，需配合@Qualifier按名称（byName）匹配；@Resource默认按名称（byName）匹配，若名称不匹配，再按类型（byType）匹配。

作用范围：@Autowired可用于构造器、方法、字段；@Resource可用于字段、方法，不能用于构造器。

## 5. AOP 的实现原理

Spring AOP基于动态代理实现，分为两种情况：

\1. 目标类有接口：使用JDK动态代理，生成接口的代理对象，代理对象实现目标接口，并重写目标方法，在方法中增强功能。

\2. 目标类无接口：使用CGLIB动态代理，生成目标类的子类，子类重写目标方法，在方法中增强功能（需导入CGLIB依赖，Spring默认集成）。

AOP核心概念：切面（Aspect，通用功能，如日志切面）、切入点（Pointcut，指定增强的方法，如所有controller方法）、通知（Advice，增强的逻辑，如前置通知、后置通知、异常通知、环绕通知）、连接点（JoinPoint，所有可能被增强的方法）、目标对象（Target，被增强的对象）。

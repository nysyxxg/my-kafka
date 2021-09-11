# 第2章 通道和FileChannel类的使用

在NIO技术中，要将操作的数据打包到缓冲区中，而缓冲区中的数据想要传输到目的地是要依赖于通道的。**缓冲区是将数据进行打包，而通道是将数据进行传输**。它们也是NIO技术中比较重要的知识点。

## 2.1 通道概述

那么在NIO技术中，可以在通道上传输“源缓冲区”与“目的缓冲区”要交互的数据。NIO技术中的数据要放在缓冲区中进行管理，再使用通道将缓冲区中的数据传输到目的地。

缓冲区都是类，而通道都是接口，这是由于通道的功能实现是要依赖于操作系统的，Channel接口只定义有哪些功能，而功能的具体实现在不同的操作系统中是不一样的，因此，在JDK中，通道被设计成接口数据类型。

## 2.2 通道接口的层次结构

AutoCloseable接口的作用是可以自动关闭，而不需要显式地调用close()方法

**AutoCloseable接口**强调的是与try()结合实现自动关闭，**该接口针对的是任何资源**，不仅仅是I/O，因此，void close()方法抛出Exception异常。该接口不要求是幂等的，也就是**重复调用此接口的close()方法会出现副作用。**
因为**Closeable接口的作用是关闭I/O流**，释放系统资源，所以**该方法抛出IOException异常**。该接口的close()方法是幂等的，可以**重复调用此接口的close()方法，而不会出现任何的效果与影响**。Closeable接口继续继承自AutoCloseable接口，说明Closeable接口有自动关闭的功能，也有本身close()方法手动关闭的功能。

示例代码参考example01包

通道是用于I/O操作的连接，更具体地讲，通道代表数据到硬件设备、文件、网络套接字的连接。通道可处于打开或关闭这两种状态，当创建通道时，通道就处于打开状态，一旦将其关闭，则保持关闭状态。一旦关闭了某个通道，则试图对其调用I/O操作时就会导致ClosedChannel Exception异常被抛出，但可以通过调用通道的isOpen()方法测试通道是否处于打开状态以避免出现ClosedChannelException异常。一般情况下，通道对于多线程的访问是安全的。
在JDK 1.8版本中，Channel接口具有11个子接口，它们列表如下：
1）AsynchronousChannel
2）AsynchronousByteChannel
3）ReadableByteChannel
4）ScatteringByteChannel
5）WritableByteChannel
6）GatheringByteChannel
7）ByteChannel
8）SeekableByteChannel
9）NetworkChannel
10）MulticastChannel
11）InterruptibleChannel

![image-20200430162430328](/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/imgs//image-20200430162430328.png)

### 2.2.1 AsynchronousChannel接口的介绍

AsynchronousChannel接口的**主要作用是使通道支持异步I/O操作**。异步I/O操作有以下两种方式进行实现。	

```java
Future<V> operation(...)
void operation(... A attachment, CompletionHandler<V, ? super A> handler)
```

当一个通道实现了可异步（asynchronously）或可关闭（closeable）相关的接口时，若调用这个正在I/O操作通道中的close()方法，就会使I/O操作发生失败，并且出现Asynchronous CloseException异常。
异步通道在多线程并发的情况下是线程安全的。某些通道的实现是可以支持并发读和写的，但是**不允许在一个未完成的I/O操作上再次调用read或write操作**。
异步通道支持取消的操作，Future接口定义cancel()方法来取消执行，这会导致那些等待处理I/O结果的线程抛出CancellationException异常。
底层的I/O操作是否能被取消，参考的是高层的具体实现，因此没有指定。
取消操作离开通道，或者离开与实体的连接，这会使通道造成不一致的状态，则通道就被置于一个错误的状态，这个状态可以阻止进一步对通道调用read()或write()，以及其他有关联的方法。例如，如果取消了读操作，但实现不能保证在通道中阻止后面的读操作，而且通道还被置于错误的状态，如果进一步尝试启动读操作，就会导致抛出一个未指定的运行时异常。类似的，如果取消一个写操作，但实现不能保证阻止后面的写操作，而且通道还被置于错误的状态，则随后发起一次新的写入的尝试将失败，并出现一个未指定的运行时异常。
当调用通道的cancel()方法时，对mayInterruptIfRunning参数传入true时，在关闭通道时I/O操作也许已经被中断。在这种情况下，所有等待I/O操作结果的线程会抛出Cancellation Exception异常，并且其他在此通道中未完成的操作将会出现AsynchronousCloseException异常。
在调用cancel()方法以取消读或写操作时，建议废弃I/O操作中使用的所有缓冲区，因为缓冲区中的数据并不是完整的，如果再次打开通道，那么也要尽量避免访问这些缓冲区。

### 2.2.2 AsynchronousByteChannel接口的介绍

AsynchronousByteChannel接口的主要作用是使通道支持异步I/O操作，操作单位为字节。
若在上一个read()方法未完成之前，再次调用read()方法，就会抛出异常ReadPendingException。类似的，在上一个write()方法未完成之前再次调用write()方法时，也会抛出异常WritePendingException。其他类型的I/O操作是否可以同时进行read()操作，取决于通道的类型或实现。ByteBuffers类不是线程安全的，尽量保证在对其进行读写操作时，没有其他线程一同进行读写操作。

### 2.2.3 ReadableByteChannel接口的介绍

ReadableByteChannel接口的主要作用是使通道允许对字节进行读操作。
ReadableByteChannel接口只允许有1个读操作在进行。如果1个线程正在1个通道上执行1个read()操作，那么任何试图发起另一个read()操作的线程都会被阻塞，直到第1个read()操作完成。其他类型的I/O操作是否可以与read()操作同时进行，取决于通道的类型。
ReadableByteChannel接口有以下两个特点：

* 将通道当前位置中的字节序列读入1个ByteBuffer中；
* read(ByteBuffer)方法是同步的。

### 2.2.4 ScatteringByteChannel接口的介绍

ScatteringByteChannel接口的主要作用是可以从通道中读取字节到多个缓冲区中。

### 2.2.5 WritableByteChannel接口的介绍

WritableByteChannel接口的主要作用是使通道允许对字节进行写操作。
WritableByteChannel接口只允许有1个写操作在进行。如果1个线程正在1个通道上执行1个write()操作，那么任何试图发起另一个write()操作的线程都会被阻塞，直到第1个write()操作完成。其他类型的I/O操作是否可以与write()操作同时进行，取决于通道的类型。
WritableByteChannel接口有以下两个特点：

* 将1个字节缓冲区中的字节序列写入通道的当前位置；

* write(ByteBuffer)方法是同步的。


### 2.2.6 GatheringByteChannel接口的介绍

GatheringByteChannel接口的主要作用是可以将多个缓冲区中的数据写入到通道中。

### 2.2.7 ByteChannel接口的介绍

ByteChannel接口的主要作用是将ReadableByteChannel（可读字节通道）与WritableByte Channel（可写字节通道）的规范进行了统一，也就是ByteChannel接口的父接口就是ReadableByteChannel和WritableByteChannel。ByteChannel接口没有添加任何的新方法。ByteChannel接口的实现类就具有了读和写的方法，是双向的操作，而单独地实现ReadableByteChannel或WritableByteChannel接口就是单向的操作，因为实现类只能进行读操作，或者只能进行写操作。

### 2.2.8 SeekableByteChannel接口的介绍

SeekableByteChannel接口的主要作用是在字节通道中维护position（位置），以及允许position发生改变。

### 2.2.9 NetworkChannel接口的介绍

NetworkChannel接口的主要作用是使通道与Socket进行关联，使通道中的数据能在Socket技术上进行传输。该接口中的bind()方法用于将Socket绑定到本地地址，get LocalAddress()方法返回绑定到此Socket的SocketAddress对象，并可以结合setOption()和getOption()方法用于设置和查询Socket相关的选项。

### 2.2.10 MulticastChannel接口的介绍

MulticastChannel接口的主要作用是使通道支持Internet Protocol（IP）多播。IP多播就是将多个主机地址进行打包，形成一个组（group），然后将IP报文向这个组进行发送，也就相当于同时向多个主机传输数据。

### 2.2.11 InterruptibleChannel接口的介绍

InterruptibleChannel接口的主要作用是使通道能以异步的方式进行关闭与中断。
当通道实现了asynchronously和closeable特性：如果一个线程在一个能被中断的通道上出现了阻塞状态，那么当其他线程调用这个通道的close()方法时，这个呈阻塞状态的线程将接收到AsynchronousCloseException异常。
当通道在实现了asynchronously和closeable特性的同时还实现了interruptible特性：如果一个线程在一个能被中断的通道上出现了阻塞状态，那么当其他线程调用这个阻塞线程的interrupt()方法后，通道将被关闭，这个阻塞的线程将接收到ClosedByInterruptException异常，这个阻塞线程的状态一直是中断状态。

## 2.3 AbstractInterruptibleChannel类的介绍

NIO核心接口的实现类列表如下：
1）AbstractInterruptibleChannel
2）AbstractSelectableChannel
3）AsynchronousFileChannel
4）AsynchronousServerSocketChannel
5）AsynchronousSocketChannel
6）DatagramChannel
7）FileChannel
8）Pipe.SinkChannel
9）Pipe.SourceChannel
10）SelectableChannel
11）ServerSocketChannel
12）SocketChannel
FileChannel类的父类正是AbstractInterruptibleChannel类。**AbstractInterruptibleChannel类的主要作用是提供了一个可以被中断的通道基本实现类。**
此类封装了能使通道实现异步关闭和中断所需要的最低级别的机制。在调用有可能无限期阻塞的I/O操作的之前和之后，通道类必须分别调用begin()和end()方法，为了确保始终能够调用end()方法，应该在try ... finally块中使用这些方法：

```java
boolean completed = false;
try {
    begin();
    completed = ...; //执行blocking I/O操作
		return ...;                //返回结果
} finally {
  end(completed);
}
```

end()方法的completed参数告知I/O操作实际是否已完成。例如，在读取字节的操作中，只有确实将某些字节传输到目标缓冲区时此参数才应该为true，代表完成的结果是成功的。
具体的通道类还必须实现implCloseChannel()方法，其方式为：如果调用此方法的同时，另一个线程阻塞在该通道上的本机I/O操作中，则该操作将立即返回，要么抛出异常，要么正常返回。如果某个线程被中断，或者异步地关闭了阻塞线程所处的通道，则该通道的end()方法会抛出相应的异常。
此类执行实现Channel规范所需的同步。implCloseChannel()方法的实现不必与其他可能试图关闭通道的线程同步。
![image-20200430163018497](/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter02/imgs//image-20200430163018497.png)

AbstractInterruptibleChannel类是抽象类，另外其内部的API结构比较简单，只有两个方法，因此，具体的使用可参考其子类FileChannel。

## 2.4 FileChannel类的使用

FileChannel类的主要作用是读取、写入、映射和操作文件的通道。**该通道永远是阻塞的操作。**
FileChannel类在内部维护当前文件的position，可对其进行查询和修改。该文件本身包含一个可读写、长度可变的字节序列，并且可以查询该文件的当前大小。当写入的字节超出文件的当前大小时，则增加文件的大小；截取该文件时，则减小文件的大小。文件可能还有某个相关联的元数据，如访问权限、内容类型和最后的修改时间，但此类未定义访问元数据的方法。

除了字节通道中常见的读取、写入和关闭操作外，此类还定义了下列特定于文件的操作。
1）以不影响通道当前位置的方式，对文件中绝对位置的字节进行读取或写入。
2）将文件中的某个区域直接映射到内存中。对于较大的文件，这通常比调用普通的read()或write()方法更为高效。
3）强制对底层存储设备进行文件的更新，确保在系统崩溃时不丢失数据。
4）以一种可被很多操作系统优化为直接向文件系统缓存发送或从中读取的高速传输方法，将字节从文件传输到某个其他通道中，反之亦然。
5）可以锁定某个文件区域，以阻止其他程序对其进行访问。
多个并发线程可安全地使用文件通道。可随时调用关闭方法，正如Channel接口中所指定的。对于涉及通道位置或者可以更改其文件大小的操作，在任意给定时间只能进行一个这样的操作。如果尝试在第一个操作仍在进行时发起第二个操作，则会导致在第一个操作完成之前阻塞第二个操作。可以并发处理其他操作，特别是那些采用显式位置的操作；但是否并发处理则取决于基础实现，因此是未指定的。
确保此类的实例所提供的文件视图与同一程序中其他实例所提供的相同文件视图是一致的。但是，此类的实例所提供的视图不一定与其他并发运行的程序所看到的视图一致，这取决于底层操作系统所执行的缓冲策略和各种网络文件系统协议所引入的延迟。无论其他程序是以何种语言编写的，而且也无论是运行在相同机器还是不同机器上，都是如此。此种不一致的确切性质取决于系统，因此是未指定的。
此类没有定义打开现有文件或创建新文件的方法，以后的版本中可能添加这些方法。在此版本中，可从现有的FileInputStream、FileOutputStream或RandomAccessFile对象获得文件通道，方法是调用该对象的getChannel()方法，这会返回一个连接到相同底层文件的文件通道。
文件通道的状态与其getChannel()方法返回该通道的对象密切相关。显式或者通过读取或写入字节来更改通道的位置将更改发起对象的文件位置，反之亦然。通过文件通道更改此文件的长度将更改通过发起对象看到的长度，反之亦然。通过写入字节更改此文件的内容将更改发起对象所看到的内容，反之亦然。
此类在各种情况下指定要求“允许读取操作”“允许写入操作”或“允许读取和写入操作”的某个实例。通过FileInputStream实例的getChannel()方法所获得的通道将允许进行读取操作。通过FileOutputStream实例的getChannel()方法所获得的通道将允许进行写入操作。最后，如果使用模式“r”创建RandomAccessFile实例，则通过该实例的getChannel()方法所获得的通道将允许进行读取操作；如果使用模式“rw”创建实例，则获得的通道将允许进行读取和写入操作。
如果从文件输出流中获得了允许进行写入操作的文件通道，并且该输出流是通过调用FileOutputStream(File, boolean)构造方法且为第二个参数传入true来创建的，则该文件通道可能处于添加模式。在此模式中，每次调用相关的写入操作都会首先将位置移到文件的末尾，然后写入请求的数据。在单个原子操作中，是否移动位置和写入数据是与系统相关的，因此是未指定的。

### 2.4.1 写操作与位置的使用

int write(ByteBuffer src)方法的作用是将remaining字节序列从给定的缓冲区写入此通道的当前位置，此方法的行为与WritableByteChannel接口所指定的行为完全相同：在任意给定时刻，一个可写入通道上只能进行一个写入操作。如果某个线程在通道上发起写入操作，那么在第一个操作完成之前，将阻塞其他所有试图发起另一个写入操作的线程。其他种类的I/O操作是否继续与写入操作并发执行，取决于该通道的类型。该方法的返回值代表写入的字节数，可能为零。
WritableByteChannel接口有两个特点：
1）**将1个ByteBuffer缓冲区中的remaining字节序列写入通道的当前位置**；
2）**write(ByteBuffer)方法是同步的**。
long position()方法的作用是返回此通道的文件位置。
public abstract FileChannel position(long newPosition)方法的作用是设置此通道的文件位置。
1．验证int write(ByteBuffer src)方法是从通道的当前位置开始写入的

示例代码参考example02包FileChannelWriteTest#test1

2．验证int write(ByteBuffer src)方法将ByteBuffer的remaining写入通道

示例代码参考example02包FileChannelWriteTest#test2

3．验证int write(ByteBuffer src)方法具有同步特性

示例代码参考example02包FileChannelWriteTest#test3

### 2.4.2 读操作

int read(ByteBuffer dst)方法的作用是将字节序列从此通道的当前位置读入给定的缓冲区的当前位置。此方法的行为与ReadableByteChannel接口中指定的行为完全相同：在任意给定时刻，一个可读取通道上只能进行一个读取操作。如果某个线程在通道上发起读取操作，那么在第一个操作完成之前，将阻塞其他所有试图发起另一个读取操作的线程。其他种类的I/O操作是否继续与读取操作并发执行，取决于该通道的类型。该方法的返回值代表读取的字节数，可能为零。如果该通道已到达流的末尾，则返回-1。
ReadableByteChannel接口有以下两个特点：
1）**将通道当前位置中的字节序列读入1个ByteBuffer缓冲区中的remaining空间中**；
2）**read(ByteBuffer)方法是同步的**。

1. 验证int read(ByteBuffer dst)方法返回值的意义
   int read(ByteBuffer dst)方法返回int类型，存在以下3种值。
   1）正数：代表从通道的当前位置向ByteBuffer缓冲区中读的字节个数。
   2）0：代表从通道中没有读取任何的数据，也就是0字节，有可能发生的情况就是缓冲区中没有remainging剩余空间了。
   3）-1：代表到达流的末端。

   示例代码参考example02包FileChannelWriteTest#test1

2. 验证int read(ByteBuffer dst)方法是从通道的当前位置开始读取的

   示例代码参考example02包FileChannelWriteTest#test2

3. 验证int read(ByteBuffer dst)方法将字节放入ByteBuffer当前位置

   示例代码参考example02包FileChannelWriteTest#test3

4. 验证int read(ByteBuffer dst)方法具有同步特性

   示例代码参考example02包FileChannelWriteTest#test4

5. 验证int read(ByteBuffer dst)方法从通道读取的数据大于缓冲区容量

   示例代码参考example02包FileChannelWriteTest#test5

6. 验证int read(ByteBuffer dst)方法从通道读取的字节放入缓冲区的remaining空间中

   示例代码参考example02包FileChannelWriteTest#test6

### 2.4.3 批量写操作

long write(ByteBuffer[] srcs)方法的作用是将每个缓冲区的remaining字节序列写入此通道的当前位置。
long write(ByteBuffer[] srcs)方法实现的是GatheringByteChannel接口中的同名方法。接口GatheringByteChannel的父接口是WritableByteChannel，说明接口GatheringByteChannel具有WritableByteChannel接口的以下两个特性：

1）将1个ByteBuffer缓冲区中的remaining字节序列写入通道的当前位置中；

2）write(ByteBuffer)方法是同步的。

此外，它还具有第3个特性：将多个ByteBuffer缓冲区中的remaining剩余字节序列写入通道的当前位置中。

1. 验证long write(ByteBuffer[] srcs)方法是从通道的当前位置开始写入的

   示例代码参考example03包BatchWriteTest#test1

2. 验证long write(ByteBuffer[] srcs)方法将ByteBuffer的remaining写入通道

   示例代码参考example03包BatchWriteTest#test2

3. 验证long write(ByteBuffer[] srcs)方法具有同步特性

   示例代码参考example03包BatchWriteTest#test3

### 2.4.4 批量读操作

long read(ByteBuffer[] dsts)方法的作用是将字节序列从此通道读入给定的缓冲区数组中的第0个缓冲区的当前位置。调用此方法的形式为c.read(dsts)，该调用与调用c.read(dsts, 0,dsts.length)的形式完全相同。long read(ByteBuffer[] dsts)方法实现的是ScatteringByteChannel接口中的同名方法，而接口ScatteringByteChannel的父接口是ReadableByteChannel，说明接口ScatteringByteChannel具有ReadableByteChannel接口的以下两个特性。

1）将通道当前位置中的字节序列读入1个ByteBuffer缓冲区的remaining空间中；

2）read(ByteBuffer)方法是同步的。

此外，它还具有第3个特性：将通道当前位置的字节序列读入多个ByteBuffer缓冲区的remaining剩余空间中。

1. 验证long read(ByteBuffer[] dsts)方法返回值的意义

   示例代码参考example03包BatchReadTest#test1

2. 验证long read(ByteBuffer[] dsts)方法是从通道的当前位置开始读取的

   示例代码参考example03包BatchReadTest#test2

3. 验证long read(ByteBuffer[] dsts)方法将字节放入ByteBuffer当前位置

   示例代码参考example03包BatchReadTest#test3

4. 验证long read(ByteBuffer[] dsts)方法具有同步特性

   示例代码参考example03包BatchReadTest#test4

### 2.4.5 部分批量写操作

long write(ByteBuffer[] srcs, int offset, int length)方法的作用是以指定缓冲区数组的offset下标开始，向后使用length个字节缓冲区，再将每个缓冲区的remaining剩余字节子序列写入此通道的当前位置。
参数的作用说明如下。
1）offset：第一个缓冲区（要获取该缓冲区中的字节）在缓冲区数组中的偏移量；必须为非负数并且不能大于srcs.length。
2）length：要访问的最大缓冲区数；必须为非负数并且不能大于srcs.length - offset。
long write(ByteBuffer[] srcs, int offset, int length)方法实现的是GatheringByteChannel接口中的同名方法，而接口GatheringByteChannel的父接口是WritableByteChannel，说明接口GatheringByteChannel也具有WritableByteChannel接口的以下两个特性：
1）将1个ByteBuffer缓冲区中的remaining字节序列写入通道的当前位置；
2）write(ByteBuffer)方法是同步的。

1. 验证long write(ByteBuffer[] srcs, int offset, int length)方法是从通道的当前位置开始写入的

   示例代码参考example04包PartBatchWriteTest#test1

2. 验证long write(ByteBuffer[] srcs, int offset, int length)方法将ByteBuffer的remaining写入通道

   示例代码参考example04包PartBatchWriteTest#test2

3. 验证long write(ByteBuffer[] srcs, int offset, int length)方法具有同步特性

   示例代码参考example04包PartBatchWriteTest#test3

### 2.4.6 部分批量读操作

long read(ByteBuffer[] dsts, int offset, int length)方法的作用是将通道中当前位置的字节序列读入以下标为offset开始的ByteBuffer[]数组中的remaining剩余空间中，并且连续写入length个ByteBuffer缓冲区。
参数的作用说明如下。
1）dsts：要向其中传输字节的缓冲区数组。
2）offset：第一个缓冲区（字节传输到该缓冲区中）在缓冲区数组中的偏移量；必须为非负数并且不能大于dsts.length。
3）length：要访问的最大缓冲区数；必须为非负数并且不能大于dsts.length-offset。
long read(ByteBuffer[] dsts, int offset, int length)方法实现的是ScatteringByteChannel接口中的同名方法，而接口ScatteringByteChannel的父接口是ReadableByteChannel，说明接口ScatteringByteChannel也具有ReadableByteChannel接口的以下两个特性：
1）将通道当前位置的字节序列读入1个ByteBuffer缓冲区的remaining空间中；
2）read(ByteBuffer)方法是同步的。

1. 验证long read(ByteBuffer[] dsts, int offset, int length)方法返回值的意义

   示例代码参考example04包PartBatchReadTest#test1

2. 验证long read(ByteBuffer[] dsts, int offset, int length)方法是从通道的当前位置开始读取的

   示例代码参考example04包PartBatchReadTest#test2

3. 验证long read(ByteBuffer[] dsts, int offset, int length)方法将字节放入ByteBuffer当前位置

   示例代码参考example04包PartBatchReadTest#test3

4. 验证long read(ByteBuffer[] dsts, int offset, int length)方法具有同步特性

   示例代码参考example04包PartBatchReadTest#test4

### 2.4.7 向通道的指定position位置写入数据

**write(ByteBuffer src, long position)方法的作用是将缓冲区的remaining剩余字节序列写入通道的指定位置。**
参数src代表要传输其中字节的缓冲区。position代表开始传输的文件位置，必须为非负数。
除了从给定的文件位置开始写入各字节，而不是从该通道的当前位置外，此方法的执行方式与write(ByteBuffer)方法相同。此方法不修改此通道的位置。如果给定的位置大于该文件的当前大小，则该文件将扩大以容纳新的字节；在以前文件末尾和新写入字节之间的字节值是未指定的。

1. 验证write(ByteBuffer src, long position)方法是从通道的指定位置开始写入的

   示例代码参考example05包PositionWriteTest#test1

2. 验证write(ByteBuffer src, long position)方法将ByteBuffer的remaining写入通道

   示例代码参考example05包PositionWriteTest#test2

3. 验证write(ByteBuffer src, long position)方法具有同步特性

   示例代码参考example05包PositionWriteTest#test3

4. 验证write(ByteBuffer src, long position)方法中的position不变性

   > 执行write(ByteBuffer src, long position)方法不改变position的位置（也就是绝对位置），操作不影响position的值。

   示例代码参考example05包PositionWriteTest#test4

### 2.4.8 读取通道指定位置的数据

**read(ByteBuffer dst, long position)方法的作用是将通道的指定位置的字节序列读入给定的缓冲区的当前位置。**
参数dst代表要向其中传输字节的缓冲区。position代表开始传输的文件位置，必须为非负数。
除了从给定的文件位置开始读取各字节，而不是从该通道的当前位置外，此方法的执行方式与read(ByteBuffer)方法相同。此方法不修改此通道的位置。如果给定的位置大于该文件的当前大小，则不读取任何字节。

1. 验证read(ByteBuffer dst, long position)方法返回值的意义

   示例代码参考example05包PositionReadTest#test1

2. 验证read(ByteBuffer dst, long position)方法将字节放入ByteBuffer当前位置

   示例代码参考example05包PositionReadTest#test2

3. 验证read(ByteBuffer dst, long position)方法具有同步特性

   示例代码参考example05包PositionReadTest#test3

### 2.4.9 设置位置与获得大小

position(long newPosition)方法的作用是设置此通道的文件位置。将该位置设置为大于文件当前大小的值是合法的，但这不会更改文件的大小，稍后试图在这样的位置读取字节将立即返回已到达文件末尾的指示，稍后试图在这种位置写入字节将导致文件扩大，以容纳新的字节，在以前文件末尾和新写入字节之间的字节值是未指定的。
long size()方法的作用是返回此通道关联文件的当前大小。

示例代码参考example06包PositionSizeTest#test1

验证"将该位置设置为大于文件当前大小的值是合法的，但这不会更改文件的大小，试图在这样的位置读取字节将立即返回已到达文件末尾的指示，试图在这种位置写入字节将导致文件扩大，以容纳新的字节，在以前文件末尾和新写入字节之间的字节值是未指定的”

示例代码参考example06包PositionSizeTest#test2

### 2.4.10 截断缓冲区

truncate(long size)方法的作用是将此通道的文件截取为给定大小。如果给定大小小于该文件的当前大小，则截取该文件，丢弃文件新末尾后面的所有字节。如果给定大小大于或等于该文件的当前大小，则不修改文件。无论是哪种情况，如果此通道的文件位置大于给定大小，则将位置设置为该大小。

示例代码参考example07包TruncateTest#test1

如果给定大小大于或等于该文件的当前大小，则不修改文件。

示例代码参考example07包TruncateTest#test2

### 2.4.11 将数据传输到其他可写入字节通道

long transferTo(position, count, WritableByteChannel dest)方法的作用是将字节从此通道的文件传输到给定的可写入字节通道。transferTo()方法的功能相当于write()方法，只不过是将通道中的数据传输到另一个通道中，而不是缓冲区中。
试图读取从此通道的文件中给定position处开始的count个字节，并将其写入目标通道的当前位置。此方法的调用不一定传输所有请求的字节，是否传输取决于通道的性质和状态。如果此通道的文件从给定的position处开始所包含的字节数小于count个字节，或者如果目标通道是非阻塞的并且其输出缓冲区中的自由空间少于count个字节，则所传输的字节数要小于请求的字节数。
此方法不修改此通道的位置。如果给定的位置大于该文件的当前大小，则不传输任何字节，否则从目标通道的position位置起始开始写入各字节，然后将该位置增加写入的字节数。
与从此通道读取并将内容写入目标通道的简单循环语句相比，此方法可能高效得多。很多操作系统可将字节直接从文件系统缓存传输到目标通道，而无须实际复制各字节。
该方法中的参数说明如下。
1）position：文件中的位置，从此位置开始传输，必须为非负数。
2）count：要传输的最大字节数；必须为非负数。
3）dest：目标通道。
long transferTo(position, count, WritableByteChannel dest)方法就是将数据写入WritableByteChannel通道中。

1. 如果给定的位置大于该文件的当前大小，则不传输任何字节

   示例代码参考example07包TransferToTest#test1

2. 正常传输数据的测试

   示例代码参考example07包TransferToTest#test2

3. 如果count的字节个数大于position到size的字节个数，则传输通道的sizeposition个字节数到dest通道的当前位置

   示例代码参考example07包TransferToTest#test3

### 2.4.12 将字节从给定可读取字节通道传输到此通道的文件中

long transferFrom(ReadableByteChannel src, position, count)方法的作用是将字节从给定的可读取字节通道传输到此通道的文件中。transferFrom()方法的功能相当于read()方法，只不过是将通道中的数据传输到另一个通道中，而不是缓冲区中。
试着从源通道中最多读取count个字节，并将其写入到此通道的文件中从给定position处开始的位置。此方法的调用不一定传输所有请求的字节；是否传输取决于通道的性质和状态。如果源通道的剩余空间小于count个字节，或者如果源通道是非阻塞的并且其输入缓冲区中直接可用的空间小于count个字节，则所传输的字节数要小于请求的字节数。
此方法不修改此通道的位置。如果给定的位置大于该文件的当前大小，则不传输任何字节。从源通道中的当前位置开始读取各字节写入到当前通道，然后将src通道的位置增加读取的字节数。
与从源通道读取并将内容写入此通道的简单循环语句相比，此方法可能高效得多。很多操作系统可将字节直接从源通道传输到文件系统缓存，而无须实际复制各字节。
该方法的参数说明如下。
1）src：源通道。
2）position：文件中的位置，从此位置开始传输；必须为非负数。
3）count：要传输的最大字节数；必须为非负数。
注意，参数position是指当前通道的位置，而不是指src源通道的位置。
long transferFrom(ReadableByteChannel, position, count)方法就是将数据从ReadableByte Channel通道中读取出来。
参数position针对于调用transferTo()或transferFrom()方法的对象。

1. 如果给定的位置大于该文件的当前大小，则不传输任何字节

   示例代码参考example07包TransferFromTest#test1

2. 正常传输数据的测试

   示例代码参考example07包TransferFromTest#test2

3. 如果count的字节个数大于src.remaining，则通道的src.remaining字节数传输到当前通道的position位置

   示例代码参考example07包TransferFromTest#test3

### 2.4.13 执行锁定操作

**FileLock lock(long position, long size, boolean shared)方法的作用是获取此通道的文件给定区域上的锁定**。在可以锁定该区域之前、已关闭此通道之前或者已中断调用线程之前（以先到者为准），将阻塞此方法的调用。
在此方法调用期间，如果另一个线程关闭了此通道，则抛出AsynchronousCloseException异常。
如果在等待获取锁定的同时中断了调用线程，则将状态设置为中断并抛出FileLock InterruptionException异常。如果调用此方法时已设置调用方的中断状态，则立即抛出该异常；不更改该线程的中断状态。
由position和size参数所指定的区域无须包含在实际的底层文件中，甚至无须与文件重叠。锁定区域的大小是固定的；如果某个已锁定区域最初包含整个文件，并且文件因扩大而超出了该区域，则该锁定不覆盖此文件的新部分。如果期望文件大小扩大并且要求锁定整个文件，则应该锁定的position从零开始，size传入大于或等于预计文件的最大值。零参数的lock()方法只是锁定大小为Long.MAX_VALUE的区域。
文件锁定要么是独占的，要么是共享的。共享锁定可阻止其他并发运行的程序获取重叠的独占锁定，但是允许该程序获取重叠的共享锁定。独占锁定则阻止其他程序获取共享或独占类型的重叠锁定。
某些操作系统不支持共享锁定，在这种情况下，自动将对共享锁定的请求转换为对独占锁定的请求。可通过调用所得锁定对象的isShared()方法来测试新获取的锁定是共享的还是独占的。
**文件锁定是以整个Java虚拟机来保持的。但它们不适用于控制同一虚拟机内多个线程对文件的访问。**

> 共享锁是只读的。独占锁只有自己可以读写，其他人不允许对其读写。

1. 验证FileLock lock(long position, long size, boolean shared)方法是同步的

   示例代码参考example08包FileLockTest#test1、test2

2. 验证AsynchronousCloseException异常的发生
   在FileLock lock(long position, long size, boolean shared)方法调用期间，如果另一个线程关闭了此通道，则抛出AsynchronousCloseException异常。

   示例代码参考example08包FileLockTest#test3

3. 验证FileLockInterruptionException异常的发生
   如果在等待获取锁定的同时中断了调用线程，则将状态设置为中断并抛出FileLock InterruptionException异常。如果调用FileLock lock(long position, long size, boolean shared)方法时已设置调用方的中断状态，则立即抛出该异常；不更改该线程的中断状态。

   示例代码参考example08包FileLockTest#test4

4. 验证共享锁自己不能写（出现异常）
   注意，如果操作锁定的区域，就会出现异常；如果操作未锁定的区域，则不出现异常。

   示例代码参考example08包FileLockTest#test5

5. 验证共享锁别人不能写（出现异常）

6. 验证共享锁自己能读

7. 验证共享锁别人能读

8. 验证独占锁自己能写

   示例代码参考example08包FileLockTest#test6

9. 验证独占锁别人不能写（出现异常）

   示例代码参考example08包FileLockTest#test7、test8

10. 验证独占锁自己能读

    示例代码参考example08包FileLockTest#test9

11. 验证独占锁别人不能读（出现异常）

    示例代码参考example08包FileLockTest#test10、test11

12. 验证lock()方法的参数position和size的含义

    示例代码参考example08包FileLockTest#test12

13. 提前锁定
    FileLock lock(long position, long size, boolean shared)方法可以实现提前锁定，也就是当文件大小小于指定的position时，是可以提前在position位置处加锁的。

14. 验证共享锁与共享锁之间是非互斥关系

> 文件锁定要么是独占的，要么是共享的。共享锁定可阻止其他并发运行的程序获取重叠的独占锁定，但是允许该程序获取重叠的共享锁定。独占锁定则阻止其他程序获取任一类型的重叠锁定。

共享锁之间、独占锁之间，以及共享锁与独占锁之间的关系，
1）共享锁与共享锁之间是非互斥关系；
2）共享锁与独占锁之间是互斥关系；
3）独占锁与共享锁之间是互斥关系；
4）独占锁与独占锁之间是互斥关系。
首先测试：共享锁与共享锁之间是非互斥关系。

### 2.4.14 FileLock lock()方法的使用

无参方法FileLock lock()的作用为获取对此通道的文件的独占锁定，是**对文件的整体进行锁定**。调用此方法的形式为fc.lock()，该调用与以下调用完全相同：fc.lock(0L, Long.MAX_VALUE, false)。

### 2.4.15 获取通道文件给定区域的锁定

**FileLock tryLock(long position, long size, boolean shared)方法的作用是试图获取对此通道的文件给定区域的锁定。此方法不会阻塞。**无论是否已成功地获得请求区域上的锁定，调用总是立即返回。如果由于另一个程序保持着一个重叠锁定而无法获取锁定，则此方法返回null。如果由于任何其他原因而无法获取锁定，则抛出相应的异常。
由position和size参数所指定的区域无须包含在实际的底层文件中，甚至无须与文件重叠。锁定区域的大小是固定的；如果某个已锁定区域最初包含整个文件，但文件因扩大而超出了该区域，则该锁定不覆盖此文件的新部分。如果期望文件大小扩大并且要求锁定整个文件，则应该锁定从零开始，到不小于期望最大文件大小为止的区域。零参数的tryLock()方法只是锁定大小为Long.MAX_VALUE的区域。
FileLock tryLock(long position, long size, boolean shared)方法与FileLock lock(long position, long size, boolean shared)方法的区别：
1）tryLock()方法是非阻塞的；
2）lock()方法是阻塞的。

### 2.4.16 FileLock tryLock()方法的使用

**无参方法FileLock tryLock()的作用为获取对此通道的文件的独占锁定，是对文件的整体进行锁定。**调用此方法的形式为fc. tryLock()，该调用与以下调用完全相同：fc.tryLock(0L, Long.MAX_VALUE, false)。

### 2.4.17 FileLock类的使用

FileLock类表示文件区域锁定的标记。每次通过FileChannel类的lock()或tryLock()方法获取文件上的锁定时，就会创建一个FileLock（文件锁定）对象。
文件锁定对象最初是有效的。通过调用release()方法、关闭用于获取该锁定的通道，或者终止Java虚拟机（以先到者为准）来释放锁定之前，该对象一直是有效的。可通过调用锁定的isValid()方法来测试锁定的有效性。
文件锁定要么是独占的，要么是共享的。共享锁定可阻止其他并发运行的程序获取重叠的独占锁定，但是允许该程序获取重叠的共享锁定。独占锁定则阻止其他程序获取任一类型的重叠锁定。一旦释放某个锁定后，它就不会再对其他程序所获取的锁定产生任何影响。
可通过调用某个锁定的isShared()方法来确定它是独占的还是共享的。某些平台不支持共享锁定，在这种情况下，对共享锁定的请求被自动转换为对独占锁定的请求。
单个Java虚拟机在某个特定文件上所保持的锁定是不重叠的。要测试某个候选锁定范围是否与现有锁定重叠，可使用overlaps()方法。
文件锁定对象记录了在其文件上保持锁定的文件通道、该锁定的类型和有效性，以及锁定区域的位置和大小。只有锁定的有效性是随时间而更改的；锁定状态的所有其他方面都是不可变的。
**文件锁定以整个Java虚拟机来保持。但它们不适用于控制同一虚拟机内多个线程对文件的访问。**
**多个并发线程可安全地使用文件锁定对象。**
**FileLock类具有平台依赖性，此文件锁定API直接映射到底层操作系统的本机锁定机制。**因此，无论程序是用何种语言编写的，某个文件上所保持的锁定对于所有访问该文件的程序来说都应该是可见的。
由于某个锁定是否实际阻止另一个程序访问该锁定区域的内容是与系统相关的，因此是未指定的。有些系统的本机文件锁定机制只是劝告的，意味着为了保证数据的完整性，各个程序必须遵守已知的锁定协议。其他系统本机文件锁定是强制的，意味着如果某个程序锁定了某个文件区域，则实际上阻止其他程序以违反该锁定的方式访问该区域。但在其他系统上，本机文件锁定是劝告的还是强制的可以以每个文件为基础进行配置。为确保平台间的一致性和正确性，强烈建议将此API提供的锁定作为劝告锁定来使用。
在有些系统上，在某个文件区域上获取强制锁定会阻止该区域被java.nio.channels. FileChannel#map映射到内存，反之亦然。组合锁定和映射的程序应该为此组合的失败做好准备。
在有些系统上，关闭某个通道会释放Java虚拟机在底层文件上所保持的所有锁定，而不管该锁定是通过该通道获取的，还是通过同一文件上打开的另一个通道获取的。**强烈建议在某个程序内使用唯一的通道来获取任意给定文件上的所有锁定。**

1. 常见API的使用

   示例代码参考example08包FileLockTest#test13

2. boolean overlaps(long position, long size)方法的使用
   boolean overlaps(long position, long size)方法的作用：判断此锁定是否与给定的锁定区域重叠。返回值是boolean类型，也就是当且仅当此锁定与给定的锁定区域至少重叠一个字节时，才返回true。

   示例代码参考example08包FileLockTest#test14

### 2.4.18 强制将所有对通道文件的更新写入包含文件的存储设备

**void force(boolean metaData)方法的作用是强制将所有对此通道的文件更新写入包含该文件的存储设备中。**如果此通道的文件驻留在本地存储设备上，则此方法返回时可保证：在此通道创建后或在最后一次调用此方法后，对该文件进行的所有更改都已写入该设备中。这对确保在系统崩溃时不会丢失重要信息特别有用。如果该文件不在本地设备上，则无法提供这样的保证。
**metaData参数可用于限制此方法必须执行的I/O操作数量。在为此参数传入false时，只需将对文件内容的更新写入存储设备；在传入true时，则必须写入对文件内容和元数据的更新，这通常需要一个以上的I/O操作。此参数是否实际有效，取决于底层操作系统，因此是未指定的。**
调用此方法可能导致发生I/O操作，即使该通道仅允许进行读取操作时也是如此。例如，某些操作系统将最后一次访问的时间作为元数据的一部分进行维护，每当读取文件时就更新此时间。实际是否执行操作是与操作系统相关的，因此是未指定的。
此方法只保证强制进行通过此类中已定义的方法对此通道的文件所进行的更改。此方法不一定强制进行那些通过修改已映射字节缓冲区（通过调用map()方法获得）的内容所进行的更改。调用已映射字节缓冲区的force()方法将强行对要写入缓冲区的内容进行更改。
以上文字是JDK API文档对该方法的解释，并不能完全反映出该方法的使用意图与作用，**其实在调用FileChannel类的write()方法时，操作系统为了运行的效率，先是把那些将要保存到硬盘上的数据暂时放入操作系统内核的缓存中，以减少硬盘的读写次数，然后在某一个时间点再将内核缓存中的数据批量地同步到硬盘中**，但同步的时间却是由操作系统决定的，因为时间是未知的，这时就不能让操作系统来决定，所以要显式地调用force(boolean)方法来强制进行同步，这样做的目的是防止在系统崩溃或断电时缓存中的数据丢失而造成损失。但是，**force(boolean)方法并不能完全保证数据不丢失，如正在执行force()方法时出现断电的情况**，那么硬盘上的数据有可能就不是完整的，而且由于断电的原因导致内核缓存中的数据也丢失了，最终造成的结果就是force(boolean)方法执行了，数据也有可能丢失。既然调用该方法也有可能造成数据的丢失，那么该方法的最终目的是什么呢？**其实force(boolean)方法的最终目的是尽最大的努力减少数据的丢失。**例如，内核缓存中有10KB的数据需要同步，那么可以每2KB就执行1次force(boolean)方法来同步到硬盘上，也就不至于缓存中有10KB数据，在突然断电时，这10KB数据全部丢失的情况发生，因此，force(boolean)方法的目的是尽可能少地丢失数据，而不是保证完全不丢失数据。

1. void force(boolean metaData)方法的性能

   因为执行force(boolean)方法后性能急剧下降，所以调用该方法是有运行效率成本的。

   示例代码参考example08包ForceTest#test1

2. 布尔参数metaData的作用

   如果传入值为true，则需要此方法强制对要写入存储设备的文件内容和元数据进行更改，否则只需强行写入内容更改。此方法需要依赖于底层操作系统的支持，在Linux所使用的glibc库的2.17版本中，这两个方法的作用是一样的，因为fdatasync调用的就是fsync()方法，无论传入的是false还是true，都会更新文件的元数据。

### 2.4.19 将通道文件区域直接映射到内存

**MappedByteBuffer map(FileChannel.MapMode mode, long position, long size)方法的作用是将此通道的文件区域直接映射到内存中。MappedByteBuffer 映射出一片文件内容之后，不会全部加载到内存中，而是会进行一部分的预读（体现在占用的那 100M 上），MappedByteBuffer 不是文件读写的银弹，它仍然依赖于 PageCache 异步刷盘的机制。**可以通过下列3种模式将文件区域映射到内存中。
1）只读：试图修改得到的缓冲区将导致抛出ReadOnlyBufferException异常。（MapMode. READ_ONLY）
2）读取/写入：**对得到的缓冲区的更改最终将传播到文件**；该更改对映射到同一文件的其他程序不一定是可见的。（MapMode.READ_WRITE）
3）专用：对得到的缓冲区的更改不会传播到文件，并且该更改对映射到同一文件的其他程序也不是可见的；相反，会创建缓冲区已修改部分的专用副本。（MapMode.PRIVATE）对于只读映射关系，此通道必须可以进行读取操作；对于读取/写入或专用映射关系，此通道必须可以进行读取和写入操作。
此方法返回的已映射字节缓冲区位置为零，限制和容量为size；其标记是不确定的。在缓冲区本身被作为垃圾回收之前，该缓冲区及其表示的映射关系都是有效的。
映射关系一经创建，就不再依赖于创建它时所用的文件通道。特别是关闭该通道对映射关系的有效性没有任何影响。
很多内存映射文件的细节从根本上是取决于底层操作系统的，因此是未指定的。当所请求的区域没有完全包含在此通道的文件中时，此方法的行为是未指定的：未指定是否将此程序或另一个程序对底层文件的内容或大小所进行的更改传播到缓冲区；未指定将对缓冲区的更改传播到文件的频率。
对于大多数操作系统而言，与通过普通的read()和write()方法读取或写入数千字节的数据相比，将文件映射到内存中开销更大。从性能的观点来看，通常将相对较大的文件映射到内存中才是值得的。
该方法的3个参数的说明如下。
1）mode：根据只读、读取/写入或专用（写入时复制）来映射文件，分别为FileChannel. MapMode类中所定义的READ_ONLY、READ_WRITE和PRIVATE；
2）position：文件中的位置，映射区域从此位置开始；必须为非负数。
3）size：要映射的区域大小；必须为非负数且不大于Integer.MAX_VALUE。

1. MapMode和MappedByteBuffer类的介绍
  MapMode类的作用是提供文件映射模式。MappedByteBuffer类，它是直接字节缓冲区，其内容是文件的内存映射区域。映射的字节缓冲区是通过FileChannel.map()方法创建的。此类用特定于内存映射文件区域的操作扩展ByteBuffer类。
  映射的字节缓冲区和它所表示的文件映射关系在该缓冲区本身成为垃圾回收缓冲区之前一直保持有效。
  映射的字节缓冲区的内容可以随时更改，如在此程序或另一个程序更改了对应的映射文件区域的内容的情况下。这些更改是否发生（以及何时发生）与操作系统无关，因此是未指定的。
  全部或部分映射的字节缓冲区可能随时成为不可访问的，如截取映射的文件。试图访问映射的字节缓冲区的不可访问区域将不会更改缓冲区的内容，并导致在访问时或访问后的某个时刻抛出未指定的异常。因此，强烈推荐采取适当的预防措施，以避免此程序或另一个同时运行的程序对映射的文件执行操作（读写文件内容除外）。
  除此之外，映射的字节缓冲区的功能与普通的直接字节缓冲区完全相同。

2. map(MapMode mode, long position, long size)方法的使用

  示例代码参考example08包MapTest#test1

3. 只读模式（READ_ONLY）的测试

  示例代码参考example08包MapTest#test2

4. 可写可读模式（READ_WRITE）的测试

  示例代码参考example08包MapTest#test3

5. 专用模式（PRIVATE）的测试

  专用模式可以使对文件的更改只针对当前的MappedByteBuffer可视，并不更改底层文件。

  示例代码参考example08包MapTest#test4

6. MappedByteBuffer类的force()方法的使用

  **作用是将此缓冲区所做的内容更改强制写入包含映射文件的存储设备中。**如果映射到此缓冲区中的文件位于本地存储设备上，那么当此方法返回时，可以保证自此缓冲区创建以来，或自最后一次调用此方法以来，已将对缓冲区所做的所有更改写入到该设备。如果文件不在本地设备上，则无法作出这样的保证。**如果此缓冲区不是以读/写模式（FileChannel.MapMode.READ_WRITE）映射的，则调用此方法无效。**调用该方法后程序在运行效率上会下降。

  示例代码参考example08包MapTest#test5

7. MappedByteBuffer load()和boolean isLoaded()方法的使用
   **load()作用是将此缓冲区内容加载到物理内存中。**此方法最大限度地确保在它返回时此缓冲区内容位于物理内存中。调用此方法可能导致一些页面错误，并导致发生I/O操作。
   isLoaded()方法的作用是判断此缓冲区的内容是否位于物理内存中。返回值为true意味着此缓冲区中所有数据极有可能都位于物理内存中，因此是可访问的，不会导致任何虚拟内存页错误，也无须任何I/O操作。返回值为false不一定意味着缓冲区的内容不位于物理内存中。返回值是一个提示，而不是保证，因为在此方法的调用返回之前，底层操作系统可能已经移出某些缓冲区数据。

   示例代码参考example08包MapTest#test6

### 2.4.20 打开一个文件

FileChannel open(Path path, OpenOption... options)方法的作用是打开一个文件，以便对这个文件进行后期处理。
参数Path代表一个文件在文件系统中的路径。
Path接口的实现类可以使用多种方式进行获取，在本章节中通过调用File类的toPath()方法进行获取。
参数OpenOption代表以什么样的方式打开或创建一个文件。OpenOption接口的实现类通常由StandardOpenOption枚举进行代替。
枚举StandardOpenOption有若干枚举常量，下面就介绍这些常量的使用。

1. 枚举常量CREATE和WRITE的使用
   枚举常量CREATE的作用：创建一个新文件（如果它不存在）。如果还设置了CREATE_NEW选项，则忽略此选项。此选项只是一个创建文件的意图，并不能真正地创建文件，因此，CREATE不能单独使用，那样就会出现java.nio.file.NoSuchFileException异常。
   枚举常量WRITE的作用：打开以进行写入访问。

   示例代码参考example08包OpenTest#test1、test2

2. 枚举常量APPEND的使用

   如果打开文件以进行写入访问，则字节将写入文件末尾而不是开始处。

   示例代码参考example08包OpenTest#test3

3. 枚举常量READ的使用

   打开以进行读取访问。

   示例代码参考example08包OpenTest#test4

4. 枚举常量TRUNCATE_EXISTING的使用

   如果该文件已存在并且为写入访问而打开，则其长度将被截断为0。如果只为读取访问打开文件，则忽略此选项。

   示例代码参考example08包OpenTest#test5

5. 枚举常量CREATE_NEW的使用

   枚举常量CREATE_NEW的作用：创建一个新文件，如果该文件已存在，则失败。

   示例代码参考example08包OpenTest#test6

6. 枚举常量DELETE_ON_CLOSE的使用
   关闭时删除。
   当此选项存在时，实现会尽最大努力尝试在关闭时通过适当的close()方法删除该文件。如果未调用close()方法，则在Java虚拟机终止时尝试删除该文件。此选项主要用于仅由Java虚拟机的单个实例使用的工作文件。在打开由其他实体并发打开的文件时，建议不要使用此选项。有关何时以及如何删除文件的许多详细信息都是特定于实现的，因此没有指定。特别是，实现可能无法保证当文件打开或攻击者替换时，它将删除预期的文件。因此，安全敏感的应用程序在使用此选项时应小心。

   示例代码参考example08包OpenTest#test7

7. 枚举常量SPARSE的使用
   **稀疏文件与CREATE_NEW选项一起使用**，此选项提供了一个提示，表明新文件将是稀疏的。当文件系统不支持创建稀疏文件时，将忽略该选项。
   对那些不存储数据的空间不让其占用硬盘容量，等以后写入有效的数据时再占用硬盘容量，这样就达到了提高硬盘空间利用率的目的，这个需求可以通过创建1个“稀疏文件”进行实现。

   示例代码参考example08包OpenTest#test8、test9

8. 枚举常量SYNC的使用
   **要求对文件内容或元数据的每次更新都同步写入底层存储设备**。如果这样做，程序运行的效率就降低了。

   示例代码参考example08包OpenTest#test10

9. 枚举常量DSYNC的使用
   **要求对文件内容的每次更新都同步写入底层存储设备**。SYNC更新内容与元数据，而DSYNC只更新内容，与force(boolean)方法作用一样。

### 2.4.21 判断当前通道是否打开

public final boolean isOpen()方法的作用是判断当前的通道是否处于打开的状态。

## 2.5 小结

本章主要介绍了NIO技术中的FileChannel类的使用，该类提供了大量的API供程序员调用。但**在使用FileChannel类或MappedByteBuffer类对文件进行操作时，在大部分情况下，它们的效率并不比使用InputStream或OutputStream高很多**，这是因为NIO的出现是为了解决操作I/O线程阻塞的问题，使用NIO就把线程变成了非阻塞，这样就提高了运行效率。但在本章中并没有体会到非阻塞的特性与优势，在后面的章节就会了解NIO真正的优势：非阻塞。**NIO中非阻塞的特性是与Socket有关的通道进行实现的**，因此，要先掌握Socket的使用，然后再来学习非阻塞的特性。
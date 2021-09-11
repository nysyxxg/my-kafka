# 第6章 AIO的使用

在学习I/O技术时，需要了解几个技术点，包括**同步阻塞、同步非阻塞、异步阻塞及异步非阻塞**。这些都是I/O模型，是学习I/O、NIO、AIO必须要了解的概念。只有清楚了这些概念，才能更好地理解不同I/O的优势。

## 6.1 AsynchronousFileChannel类的使用

AsynchronousFileChannel类用于读取、写入和操作文件的异步通道。
在通过调用此类定义的open()方法打开文件时，将创建一个异步文件通道。该文件包含可读写的、可查询其当前大小的可变长度的字节序列。当写入字节超出其当前大小时，文件的大小会增加。文件的大小在截断时会减小。
异步文件通道在文件中没有当前位置，而是将文件位置指定给启动异步操作的每个读取和写入方法。CompletionHandler被指定为参数，并被调用以消耗I/O操作的结果。此类还定义了启动异步操作的读取和写入方法，并返回Future对象以表示操作的挂起结果。将来可用于检查操作是否已完成，等待完成，然后检索结果。
除了读写操作之外，此类还定义了以下操作：
1）对文件所做的更新可能会被强制到底层存储设备，以确保在发生系统崩溃时不会丢失数据。
2）文件的某个区域可能被其他程序的访问锁定。
AsynchronousFileChannel与一个线程池关联，任务被提交来处理I/O事件，并发送到使用通道上I/O操作结果的CompletionHandler对象。在通道上启动的I/O操作的CompletionHandler保证由线程池中的一个线程调用（这样可以确保CompletionHandler程序由具有预期标识的线程运行）。如果I/O操作立即完成，并且起始线程本身是线程池中的线程，则启动线程可以直接调用完成处理程序。当创建AsynchronousFileChannel而不指定线程池时，该通道将与系统相关的默认线程池关联，该线程池可能与其他通道共享。默认线程池由AsynchronousChannelGroup类定义的系统属性配置。
此类型的通道可以安全地由多个并发线程使用。可以在任何时候调用close()方法，如通道接口所指定的那样。这将导致通道上的所有未完成的异步操作都使用异常AsynchronousCloseException。多个读写操作在同一时间可能是未完成的。当多个读写操作未完成时，将不指定I/O操作的顺序以及调用CompletionHandler程序的顺序。特别是，它们没有保证按照行动的启动顺序执行。读取或写入时使用的ByteBuffers不安全，无法由多个并发I/O操作使用。此外，在启动I/O操作之后，应注意确保在操作完成后才能访问缓冲区。
与FileChannel一样，此类的实例提供的文件的视图保证与同一程序中其他实例提供的同一文件的其他视图一致。但是，该类的实例提供的视图可能与其他并发运行的程序所看到的视图一致，也可能不一致，这是由于底层操作系统所执行的缓存和网络文件系统协议引起的延迟。无论编写这些程序的语言是什么，也无论它们是在同一台机器上运行还是在其他机器上，都是如此。任何此类不一致的确切性质都依赖于系统，因此未指定。

### 6.1.1 获取此通道文件的独占锁

`public final Future<FileLock> lock()`方法的作用是获取此通道文件的独占锁。此方法启动一个操作以获取此通道的文件的独占锁。该方法返回一个表示操作的挂起结果的Future对象。Future的get()方法在成功完成时返回FileLock。调用此方法的行为及调用的方式与代码ch.lock(0L, Long.MAX_VALUE, false)完全相同。返回值表示待定结果的Future对象。

示例代码参考example01包AsynchronousFileChannelTest#testLock

### 6.1.2 获取通道文件给定区域的锁

`public abstract Future<FileLock> lock(long position, long size, boolean shared)`方法的作用是获取此通道文件给定区域的锁。此方法启动一个操作以获取此信道文件的给定区域的锁。该方法的行为与`lock(long, long, boolean, Object, CompletionHandler)`方法完全相同，不同之处在于，此方法不指定CompletionHandler程序，而是返回一个表示待定结果的Future对象。Future的get()方法在成功完成时返回FileLock。
参数position代表锁定区域的起始位置，必须是非负数。size代表锁定区域的大小，必须是非负数，并且position + size的结果必须是非负数。**shared值为true代表请求的是共享锁，在这种情况下，此通道必须为读取（并可能写入）打开，如果请求排他锁，在这种情况下，此通道必须为写入而打开（并且可能读取）**。返回值代表待定结果的Future对象。

示例代码参考example01包AsynchronousFileChannelTest#testLock2

### 6.1.3 实现重叠锁定

在两个进程对同一个文件的锁定范围有重叠时，会出现阻塞的状态。

示例代码参考example01包AsynchronousFileChannelTest#testLock3

### 6.1.4 返回此通道文件当前大小与通道打开状态

`public abstract long size()`方法的作用是返回此通道文件的当前大小。
`public boolean isOpen()`方法的作用是判断通道是否呈打开的状态。

示例代码参考example01包AsynchronousFileChannelTest#test1

### 6.1.5 CompletionHandler接口的使用

`public final <A> void lock(A attachment, CompletionHandler<FileLock, ? super A> handler)`方法的作用是获取此通道文件的独占锁。此方法启动一个操作以获取此通道文件的给定区域的锁。handler参数是在获取锁（或操作失败）时调用的CompletionHandler对象。传递给CompletionHandler的结果是生成的FileLock。
调用此方法ch.lock(att, handler)的行为及方式与ch.lock(0L, Long.MAX_VALUE, false, att, handler)完全相同。参数A代表附件的数据类型。参数attachment代表要附加到IO操作的对象，可以为空。CompletionHandler代表处理程序，用于消耗结果的处理程序。

示例代码参考example01包AsynchronousFileChannelTest#testLock4

### 6.1.6 public void failed (Throwable exc, A attachment)方法调用时机

`public void failed(Throwable exc, A attachment)`方法被调用的时机是出现I/O操作异常时。

示例代码参考example01包AsynchronousFileChannelTest#testLock5

### 6.1.7 执行指定范围的锁定与传入附件及整合接口

`public abstract <A> void lock(long position, long size, boolean shared, A attachment, CompletionHandler<FileLock, ? super A> handler)`方法的作用是将`public abstract Future<FileLock> lock (long position, long size, boolean shared)`方法和`public final <A> void lock(A attachment, CompletionHandler<FileLock, ? super A> handler)`方法进行了整合。

### 6.1.8 执行锁定与传入附件及整合接口CompletionHandler

如果`public final <A> void lock(A attachment, CompletionHandler<FileLock, ? super A> handler)`方法获得不到锁，则一直等待。

示例代码参考example01包AsynchronousFileChannelTest#processA、processB

### 6.1.9 lock (position, size, shared, attachment, CompletionHandler)方法的特点

如果lock(position, size, shared, attachment, CompletionHandler)方法获得不到锁，则一直等待。

示例代码参考example01包AsynchronousFileChannelTest#processA、processB

### 6.1.10 读取数据方式1

`public abstract Future<Integer> read(ByteBuffer dst, long position)`方法的作用是从给定的文件位置开始，从该通道将字节序列读入给定的缓冲区。此方法从给定的文件位置开始，将从该通道的字节序列读取到给定的缓冲区。此方法返回Future对象。如果给定位置大于或等于在尝试读取时文件的大小，则Future的get()方法将返回读取的字节数或-1。
此方法的工作方式与`AsynchronousByteChannel.read(ByteBuffer)`方法相同，只是从给定文件位置开始读取字节。如果给定的文件位置大于文件在读取时的大小，则不读取任何字节。参数dst代表要将字节传输到的缓冲区。参数position代表开始的文件位置，必须是非负数。

示例代码参考example01包AsynchronousFileChannelTest#testRead1

### 6.1.11 读取数据方式2

示例代码参考example01包AsynchronousFileChannelTest#testRead2

### 6.1.12 写入数据方式1

示例代码参考example01包AsynchronousFileChannelTest#testWrite1

### 6.1.13 写入数据方式2

示例代码参考example01包AsynchronousFileChannelTest#testWrite2

## 6.2 AsynchronousServerSocketChannel和AsynchronousSocketChannel类的使用

AsynchronousServerSocketChannel类是**面向流的侦听套接字的异步通道**。1个AsynchronousServerSocketChannel通道是通过调用此类的open()方法创建的。新创建的AsynchronousServerSocketChannel已打开但尚未绑定。它可以绑定到本地地址，并通过调用bind()方法来配置为侦听连接。一旦绑定，accept()方法被用来启动接受连接到通道的Socket。尝试在未绑定通道上调用accept()方法将导致引发NotYetBoundException异常。
**此类型的通道是线程安全的，可由多个并发线程使用**，但在大多数情况下，在任何时候都可以完成一个accept操作。如果线程在上一个接受操作完成之前启动接受操作，则会引发AcceptPendingException异常。
可以使用setOption()方法设置，其他的Socket Option是否支持取决于实现。

AsynchronousSocketChannel类是**面向流的连接套接字的异步通道**。
使用AsynchronousSocketChannel类的open()方法创建的是未连接状态的AsynchronousSocketChannel对象，之后再使用connect()方法将未连接的AsynchronousSocketChannel变成已连接的AsynchronousSocketChannel对象，详述如下：
1）创建AsynchronousSocketChannel是通过调用此类定义的open()方法，新创建的AsynchronousSocketChannel呈已打开但尚未连接的状态。当连接到AsynchronousServerSocketChannel的套接字时，将创建连接的AsynchronousSocketChannel对象。不可能为任意的、预先存在的Socket创建异步套接字通道。
2）通过调用connect()方法将未连接的通道变成已连接，连接后该通道保持连接，直到关闭。是否连接套接字通道可以通过调用其getRemoteAddress()方法来确定。尝试在未连接的通道上调用IO操作将导致引发NotYetConnectedException异常。
**此类型的通道可以安全地由多个并发线程使用。**它们支持并发读写，虽然最多一次读取操作，并且一个写操作可以在任何时候未完成。如果一个线程在上一个读操作完成之前启动了read操作，则会引发ReadPendingException异常。类似的，尝试在前一个写操作完成之前启动一个写运算将会引发一个WritePendingException异常。
可以使用setOption()方法设置，其他的Socket Option是否支持取决于实现。此类定义的read()和write()方法允许在启动读或写操作时指定超时。如果在操作完成之前超时，则操作将以InterruptedByTimeoutException异常完成。超时可能会使通道或基础连接处于不一致状态。如果实现不能保证字节没有从通道中读取，那么它就会将通道置于实现特定的错误状态，随后尝试启动读取操作会导致引发未指定的运行时异常。类似的，如果写操作超时并且实现不能保证字节尚未写入信道，则进一步尝试写入信道会导致引发未指定的运行时异常。如果超时时间已过，则不定义I/O操作的缓冲区或缓冲区序列的状态，应丢弃缓冲区，或者至少要注意确保在通道保持打开状态时不访问缓冲区。所有接受超时参数的方法都将值处理得小于或等于零，这意味着I/O操作不会超时。

### 6.2.1 接受方式1

示例代码参考example02包AsynchronousChannelTest#server1、client1

### 6.2.2 接受方式2

示例代码参考example02包AsynchronousChannelTest#server2、client2

### 6.2.3 重复读与重复写出现异常

因为read()方法是非阻塞的，所以执行第1个read()方法后立即继续执行第2个read()方法，但由于第1个read()方法并没有完成读的操作，因为并没有调用future.get()方法，因此出现ReadPendingException异常。
重复写也是同样的道理，出现WritePendingException异常。

示例代码参考example02包AsynchronousChannelTest#test1

### 6.2.4 读数据

`public abstract <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler)`方法的作用是将此通道中的字节序列读入给定的缓冲区。此方法启动一个异步读取操作，以便将该通道中的字节序列读入给定的缓冲区。handler参数是在读取操作完成或失败时调用的CompletionHandler。传递给completed()方法的结果是读取的字节数，如果无法读取字节，则为-1，因为信道已达到end-of-stream。
如果指定了timeout并且在操作完成之前发生超时的情况，则操作将以异常InterruptedByTimeoutException完成。在发生超时的情况下，实现无法保证字节没有被读取，或者不会从通道读取到给定的缓冲区，那么进一步尝试从通道读取将导致引发不运行时异常，否则，此方法的工作方式与`public final <A> void read(ByteBuffer dst, A attachment, CompletionHand ler<Integer, ? super A> handler)`方法相同。

示例代码参考example02包AsynchronousChannel1Test#server1、client1

### 6.2.5 写数据

`public abstract <A> void write(ByteBuffer src, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler)`方法的作用是从给定缓冲区向此通道写入一个字节序列。此方法启动异步写入操作，以便从给定缓冲区向此通道写入一个字节序列。handler参数是在写操作完成或失败时调用的CompletionHandler。传递给completed()方法的结果是写入的字节数。
如果指定了timeout，并且在操作完成之前发生了超时，则它将以异常 InterruptedByTimeoutException完成。如果发生超时，并且实现无法保证字节尚未写入或不会从给定的缓冲区写入通道，则进一步尝试写入信道将导致引发不运行时异常，否则，此方法的工作方式与`public final <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler)`方法相同。

示例代码参考example02包AsynchronousChannel1Test#server1、client2

## 6.3 同步、异步、阻塞与非阻塞之间的关系

同步、异步、阻塞与非阻塞可以组合成以下4种排列：
1）同步阻塞
2）同步非阻塞
3）异步阻塞
4）异步非阻塞
在使用普通的InputStream、OutputStream类时，就是属于同步阻塞，因为执行当前读写任务一直是当前线程，并且读不到或写不出去就一直是阻塞的状态。阻塞的意思就是方法不返回，直到读到数据或写出数据为止。
NIO技术属于同步非阻塞。当执行“serverSocketChannel.configureBlocking(false)”代码后，也是一直由当前的线程在执行读写操作，但是读不到数据或数据写不出去时读写方法就返回了，继续执行读或写后面的代码。
而异步当然就是指多个线程间的通信。例如，A线程发起一个读操作，这个读操作要B线程进行实现，A线程和B线程就是异步执行了。A线程还要继续做其他的事情，这时B线程开始工作，如果读不到数据，B线程就呈阻塞状态了，如果读到数据，就通知A线程，并且将拿到的数据交给A线程，这种情况是异步阻塞。
最后一种是异步非阻塞，是指A线程发起一个读操作，这个读操作要B线程进行实现，因为A线程还要继续做其他的事情，这时B线程开始工作，如果读不到数据，B线程就继续执行后面的代码，直到读到数据时，B线程就通知A线程，并且将拿到的数据交给A线程。
从大的概念上来讲，同步和异步关注的是消息通信机制，阻塞和非阻塞关注的是程序在等待调用结果时的状态。文件通道永远都是阻塞的，不能设置成非阻塞模式。
首先一个I/O操作其实分成了两个步骤：
1）发起I/O请求；
2）实际的I/O操作。
同步I/O和异步I/O的区别就在于第二个步骤是否阻塞。如果实际的I/O读写阻塞请求进程，那么就是同步I/O。因此，阻塞I/O、非阻塞I/O、I/O复用、信号驱动I/O都是同步I/O。如果不阻塞，而是操作系统帮用户做完IO操作再将结果返回给用户，那么就是异步I/O。
阻塞I/O和非阻塞I/O的区别在于第一步，即发起I/O请求是否会被阻塞。如果阻塞直到完成，那么就是传统的阻塞I/O；如果不阻塞，那么就是非阻塞I/O。

## 6.4 小结

本章介绍了AIO技术的使用，读者应该着重掌握与Socket有关的异步技术，因为该技术是开发高性能的软件项目必备的技能。在Java SE技术中，笔者认为有4个技术是重点，分别是多线程、并发、Socket及NIO/AIO，只有将这4个知识点进行全面掌握，才可以开始学习架构/高并发/高可用等与互联网有关的技术。


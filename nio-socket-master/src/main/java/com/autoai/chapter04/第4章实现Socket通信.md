# 第4章 实现Socket通信

本章在TCP/IP的基础上介绍如何使用Java语言来实现Socket通信，如何使用ServerSocket．类处理服务端（Server），如何使用Socket类处理客户端（Client），如何实现服务端与客户端之间的交互。
基于UDP时，会使用DatagramSocket类处理服务端与客户端之间的Socket通信，传输的数据要存放在DatagramPacket类中。
另外，详细介绍这4个类的API的使用细节和注意事项。

## 4.1 基于TCP的Socket通信

TCP提供基于“流”的“长连接”的数据传递，发送的数据带有顺序性。TCP是一种流协议，以流为单位进行数据传输。
**长连接**可以实现当服务端与客户端连接成功后连续地传输数据，在这个过程中，连接保持开启的状态，数据传输完毕后连接不关闭。长连接是指建立Socket连接后，无论是否使用这个连接，该连接都保持连接的状态。
短连接是当服务端与客户端连接成功后开始传输数据，数据传输完毕后则连接立即关闭，如果还想再次传输数据，则需要再创建新的连接进行数据传输。
在TCP/IP中，连接可以认为是服务端与客户端确认彼此都存在的过程。这个过程需要实现，就要创建连接，如何创建连接（环境）呢？需要服务端与客户端进行3次握手，握手成功之后，说明服务端与客户端之间能实现数据通信。如果建立连接的过程是成功的，就说明连接被成功创建。在创建好的1个连接中，使用TCP可以实现多次的数据通信。在多次数据通信的过程中，服务端与客户端要进行彼此都存在的过程验证，也就是验证连接是否正常，如果连接正常，并且多次通信，则这就是长连接。长连接就是复用当前的连接以达到数据多次通信的目的。由于复用当前的连接进行数据通信，因此不需要重复创建连接，传输效率比较高。而当实现1次数据通信之后，关闭连接，这种情况就可称为短连接。使用短连接进行数据传输时，由于每次传输数据前都要创建连接，这样会产生多个连接对象，增大占用内存的空间，在创建连接时也要进行服务端与客户端之间确认彼此存在，确认的过程比较耗时，因此运行效率较低。**由于UDP是无连接协议，也就是服务端与客户端没有确认彼此都存在的握手过程，因此在UDP里面不存在长连接与短连接的概念。**

* 长连接的优缺点
  * 优点：除了第一次之外，客户端不需要每次传输数据时都先与服务端进行握手，这样就减少了握手确认的时间，直接传输数据，提高程序运行效率。
  * 缺点：在服务端保存多个Socket对象，大量占用服务器资源。
* 短连接的优缺点
  * 优点：在服务端不需要保存多个Socket对象，降低内存占用率。
  * 缺点：每次传输数据前都要重新创建连接，也就是每次都要进行3次握手，增加处理的时间。

### 4.1.1 验证ServerSocket类的accept()方法具有阻塞特性

ServerSocket类的作用是创建Socket（套接字）的服务端，而Socket类的作用是创建Socket的客户端。在代码层面使用的方式就是使用Socket类去连接ServerSocket类，也就是客户端要主动连接服务端。
ServerSocket类中的public Socket accept()方法的作用是侦听并接受此套接字的连接。此方法在连接传入之前一直阻塞。public Socket accept()方法的返回值是Socket类型。
在本实验中，将验证ServerSocket类中的accept()方法具有阻塞特性，也就是当没有客户端连接服务端时，呈阻塞状态。

示例代码参考example01包Server#main

那么什么时候不阻塞呢？有客户端连接到服务端时就不再出现阻塞了，服务端的程序会继续运行。针对该结论，下面继续进行验证。

示例代码参考example01包Client#main

如何使用ServerSocket类创建一个Web服务器。

示例代码参考example01包SocketTest#test1

### 4.1.2 验证Socket中InputStream类的read()方法也具有阻塞特性

除了ServerSocket类中的accept()方法具有阻塞特性外，InputStream类中的read()方法也同样具有阻塞特性。
通过使用Socket类的getInputStream()方法可以获得输入流，从输入流中获取从对方发送过来的数据。
read()方法阻塞的原因是客户端并未发送数据到服务端，服务端一直在尝试读取从客户端传递过来的数据，因为客户端从未发送数据给服务端，所以服务端一直在阻塞。

示例代码参考example01包SocketTest#test2、test3

### 4.1.3 客户端向服务端传递字符串

本实验是学习Socket编程的经典案例，真正实现了服务端与客户端进行通信。

示例代码参考example01包SocketTest#test4、test5

### 4.1.4 服务端向客户端传递字符串

上一节已经实现了从客户端向服务端传递数据，本小节将实现反向操作，也就是从服务端向客户端传递数据。

示例代码参考example01包SocketTest#test6、test7

### 4.1.5 允许多次调用write()方法进行写入操作

write()方法允许多次被调用，每执行一次就代表传递一次数据。

示例代码参考example01包SocketTest#test8、test9

### 4.1.6 实现服务端与客户端多次的往来通信

前面的实验都是服务端与客户端只进行了1次通信，那么如何实现连续多次的长连接通信呢？

示例代码参考example01包SocketTest#test10、test11

### 4.1.7 调用Stream的close()方法造成Socket关闭

当调用java.net.SocketInputStream类的close()方法时，顺便也将Socket（套接字）close()关闭。如果Socket关闭，则服务端与客户端不能进行通信。因此，当执行代码OutputStream outputStream = socket.getOutputStream()取得输出流时，就会出现异常。

### 4.1.8 使用Socket传递PNG图片文件

示例代码参考example01包SocketTest#test12、test13

### 4.1.9 TCP连接的3次“握手”过程

在使用TCP进行服务端与客户端连接时，需要进行3次“握手”。3次“握手”是学习Socket的必备知识，更是学习TCP/IP的必备技能，下面就介绍3次“握手”的过程。

使用wireshark监听回环网络，添加过滤器

```text
(ip.src==127.0.0.1 and tcp.port==9000) or (ip.dst==127.0.0.1 and tcp.port==9000)
```

![image-20200618170138794](/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter04/imgs//image-20200618170138794.png)

1. 在第一次“握手”时，客户端向服务端发送SYN标志位，目的是与服务端建立连接。
   **SYN标志位**的值表示发送数据流序号sequence number的最大值。例如，Seq的值是5，说明在数据流中曾经一共发送了1,2,3,4这4个字节。而在本次“握手”中，**Seq的值是0**，代表发送数据流的大小是0。另外，从**Len=0**也可以看出来是没有数据可供发送的，客户端仅仅发送一个SYN标志位到服务端，代表要进行连接。
2. 第2次“握手”时，**服务端向客户端发送SYN和ACK标志位**，其中ACK标志位表示是对收到的数据包的确认，说明服务端接收到了客户端的连接。**ACK的值是1，表示服务端期待下一次从客户端发送数据流的序列号是1**，而**Seq=0代表服务端曾经并没有给客户端发送数据，而本次也没有发送数据**，因为Len=0也证明了这一点。
3. 第3次“握手”时，**客户端向服务端发送的ACK标志位为1, Seq的值是1**。
   Seq=1代表这正是服务端所期望的Ack=1。虽然Seq=1，但Len=0说明客户端这次还是没有向服务端传递数据。而**客户端向服务端发送ACK标志位为1的信息，说明客户端期待服务端下一次传送的Seq的值是1**。

![image-20200618171142894](/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter04/imgs//image-20200618171142894.png)

### 4.1.10 标志位SYN与ACK值的自增特性

![image-20200619133558494](/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter04/imgs//image-20200619133558494.png)

在服务端与客户端进行数据传输时，标志位SYN和ACK的值具有确认自增机制。
TCP数据包中的序列号（Sequence Number）不是以报文包的数量来进行编号的，而是将传输的所有数据当作一个字节流，序列号就是整个字节流中每个字节的编号。一个TCP数据包中包含多个字节流的数据（即数据段），而且每个TCP数据包中的数据大小不一定相同。在建立TCP连接的3次“握手”过程中，通信双方各自已确定了初始的序号x和y, TCP每次传送的报文段中的序号字段值表示所要传送本报文中的第一个字节在整体字节流中的序号。
TCP的报文到达确认（ACK），是对接收到的数据的最高序列号的确认，并向发送端返回一个下次接收时期望的TCP数据包的序列号（Ack Number）。例如，**主机A发送的当前数据序号是400，数据长度是100，则接收端收到后会返回一个500的确认号给主机A。**
当客户端第一次调用write ("111".getBytes())代码向服务端传输数据时，客户端发送标志位**PSH和ACK**。标志位PSH的作用是发送数据，让接收方立即处理数据。
客户端发送Seq=1、Ack=1和Len=3信息给服务端。Len=3代表发送数据段的大小为3，数据内容是“111”。Seq=1代表以前从未传输数据，此次是从第1位开始发送数据给服务端。Ack=1表示客户端期望服务端返回Seq=1的数据包。
服务器发送给客户端Seq=1、Ack=4和Len=0的信息。Seq=1正是客户端所期望的Ack=1，但由于Len=0，说明服务端并没有给客户端发送任何数据。而服务端期待客户端继续发送第4个字节的数据，说明服务端已经接收到从客户端传递过来的“111”这3个字节的数据。
后面的过程以此类推即可。

### 4.1.11 TCP断开连接的4次“挥手”过程

在使用TCP时，若要断开服务端与客户端的连接，需要进行4次“挥手”。

![image-20200619135459579](/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter04/imgs//image-20200619135459579.png)

![image-20200619135508815](/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter04/imgs//image-20200619135508815.png)

### 4.1.12 “握手”的时机与立即传数据的特性

**服务端与客户端进行“握手”的时机不是在执行accpet()方法时，而是在ServerSocket对象创建出来并且绑定到指定的地址与端口时。**

### 4.1.13 结合多线程Thread实现通信

在Socket技术中，常用的实践方式就是Socket结合Thread多线程技术，客户端每发起一次新的请求，就把这个请求交给新创建的线程来执行这次业务。当然，如果使用线程池技术，则会更加高效。本示例先使用原始的非线程池来进行演示。

示例代码参考example01包SocketTest#test16、test17

使用线程池

示例代码参考example02包

### 4.1.14 服务端与客户端互传对象以及I/O流顺序问题

本实验将实现Server与Client交换Userinfo对象，而不是前面章节String类型的数据。注意服务端和客户端从socket获取流的先后顺序，以免造成堵塞。

示例代码参考example03包

## 4.2 ServerSocket类的使用

ServerSocket类中有很多方法，熟悉这些方法的功能与使用是掌握Socket的基础，下面就开始介绍其常用的API方法。

### 4.2.1 接受accept与超时Timeout

public Socket **accept**()方法的作用就是侦听并接受此套接字的连接。此方法在连接传入之前一直阻塞。
**setSoTimeout** (timeout)方法的作用是设置超时时间，通过指定超时timeout值启用/禁用SO_TIMEOUT，以ms为单位。在将此选项设为非零的超时timeout值时，对此**ServerSocket调用accept()方法将只阻塞timeout的时间长度。如果超过超时值，将引发java.net. SocketTimeoutException**，但ServerSocket仍旧有效，在结合try-catch结构后，还可以继续进行accept()方法的操作。SO_TIMEOUT选项必须在进入阻塞操作前被启用才能生效。注意，超时值必须是大于0的数。超时值为0被解释为无穷大超时值。参数int timeout的作用是在指定的时间内必须有客户端的连接请求，超过这个时间即出现异常，**默认值是0，即永远等待**。
int **getSoTimeout**()方法的作用是获取SO_TIMEOUT的设置。返回0意味着禁用了选项（即无穷大的超时值）。

示例代码参考example04包ServerSocketTest#test1、test2

### 4.2.2 构造方法的backlog参数含义

ServerSocket类的构造方法中的参数backlog的主要作用就是**允许接受客户端连接请求的个数**。客户端有很多连接进入到操作系统中，将这些连接放入操作系统的队列中，当执行accept()方法时，允许客户端连接的个数要取决于backlog参数。
利用指定的backlog创建服务器套接字并将其绑定到指定的本地端口号port。对port端口参数传递值为0，意味着将自动分配空闲的端口号。
传入backlog参数的作用是**设置最大等待队列长度，如果队列已满，则拒绝该连接**。backlog参数必须是大于0的正值，如果传递的值等于或小于0，则使用**默认值50**。

示例代码参考example04包ServerSocketTest#test3、test4

### 4.2.3 参数backlog的默认值

**在不更改参数backlog设置的情况下，其默认值是50**。需要注意的是，backlog限制的连接数量是由操作系统进行处理的，因为backlog最终会传递给用native声明的方法。

### 4.2.4 构造方法ServerSocket (int port, int backlog, InetAddress bindAddr)的使用

作用是使用指定的port和backlog将Socket绑定到本地InetAddress bindAddr来创建服务器。bindAddr参数可以在ServerSocket的多宿主主机（multi-homed host）上使用，ServerSocket仅接受对其多个地址的其中一个的连接请求。如果bindAddr为null，**则默认接受任何/所有本地地址上的连接（0.0.0.0）**。注意，端口号必须0～65535（包括两者）。
多宿主主机代表一台计算机有两块网卡，每个网卡有不同的IP地址，也有可能出现一台计算机有1块网卡，但这块网卡有多个IP地址的情况。
backlog参数必须是大于0的正值。如果传递的值等于或小于0，则使用默认值50。

示例代码参考example04包ServerSocketTest#test5、test6

### 4.2.5 绑定到指定的Socket地址

public void bind (SocketAddress endpoint)方法的主要作用是将ServerSocket绑定到特定的Socket地址（IP地址和端口号），使用这个地址与客户端进行通信。如果地址为null，则系统将挑选一个临时端口和一个有效本地地址来绑定套接字。
该方法的使用场景就是在使用ServerSocket类的无参构造方法后想指定本地端口。
SocketAddress类表示不带任何协议附件的Socket Address。作为一个抽象（abstract）类，应通过特定的、协议相关的实现为其创建子类。它提供不可变对象，供套接字用于绑定、连接或用作返回值。SocketAddress类有1个子类InetSocketAddress。需要注意的是，**InetAddress类代表IP地址，而InetSocketAddress类代表Socket地址**。
InetSocketAddress类有3个构造方法，说明如下。
1）**构造方法public InetSocketAddress (int port)的作用是创建套接字地址**，其中IP地址为通配符地址，端口号为指定值。有效的端口值介于0～65535之间。**端口号传入0代表在bind操作中随机挑选空闲的端口。**
2）**构造方法public InetSocketAddress (String hostname, int port)的作用是根据主机名和端口号创建套接字地址**。有效的端口值介于0～65535之间。端口号传入0代表在bind操作中随机挑选空闲的端口。
3）**构造方法public InetSocketAddress (InetAddress addr, int port)的作用根据IP地址和端口号创建套接字地址。**有效的端口值介于0～65535之间。端口号传入0代表在bind操作中随机挑选空闲的端口。

示例代码参考example04包ServerSocketTest#test7、test8、test9、test10

### 4.2.6 绑定到指定的Socket地址并设置backlog数量

bind (SocketAddress endpoint, int backlog)方法不仅可以绑定到指定IP，而且还可以设置backlog的连接数量。

### 4.2.7 获取本地SocketAdress对象以及本地端口

getLocalSocketAddress()方法用来获取本地的SocketAddress对象，它返回此Socket绑定的端点的地址，如果尚未绑定，则返回null。getLocalPort()方法用来获取Socket绑定到本地的端口。

示例代码参考example04包ServerSocketTest#test7

### 4.2.8 InetSocketAddress类的使用

InetSocketAddress类表示此类实现IP套接字地址（IP地址+端口号）。它还可以是一个（主机名+端口号），在此情况下，将尝试解析主机名，如果解析失败，则该地址将被视为未解析的地址，但是其在某些情形下仍然可以使用，如通过代理连接。它提供不可变对象，供套接字用于绑定、连接或用作返回值。
通配符是一个特殊的本地IP地址。它通常表示“任何”，只能用于bind操作。
SocketAddress与InetAddress本质的区别就是SocketAddress不基于任何协议。

public final String getHostName()方法的作用是获取主机名。注意，如果地址是用字面IP地址创建的，则**此方法可能触发名称服务反向查找，也就是利用DNS服务通过IP找到域名**。
public final String getHostString()方法的作用是返回主机名或地址的字符串形式，**如果它没有主机名，则返回IP地址**。这样做的好处是不尝试反向查找。

public static InetSocketAddress createUnresolved (String host, int port)方法的作用是**根据主机名和端口号创建未解析的套接字地址，但不会尝试将主机名解析为InetAddress**。该方法将地址标记为未解析，有效端口值介于0～65535之间。端口号0代表允许系统在bind操作中随机挑选空闲的端口。
public final boolean isUnresolved()方法的作用：如果无法将主机名解析为InetAddress，则返回true。

示例代码参考example04包InetSocketAddressTest

### 4.2.9 关闭与获取关闭状态

public void close()方法的作用是关闭此套接字。在accept()中，所有当前阻塞的线程都将会抛出SocketException。如果此套接字有一个与之关联的通道，则关闭该通道。
public boolean isClosed()方法的作用是返回ServerSocket的关闭状态。如果已经关闭了套接字，则返回true。

示例代码参考example04包ServerSocketTest#test11

### 4.2.10 判断Socket绑定状态

public boolean isBound()方法的作用是返回ServerSocket的绑定状态。如果将ServerSocket成功地绑定到一个地址，则返回true。

示例代码参考example04包ServerSocketTest#test12

### 4.2.11 获得IP地址信息

getInetAddress()方法用来获取Socket绑定的本地IP地址信息。如果Socket是未绑定的，则该方法返回null。

示例代码参考example04包ServerSocketTest#test13

### 4.2.12 Socket选项ReuseAddress

public void setReuseAddress (boolean on)方法的作用是启用/禁用SO_REUSEADDR套接字选项。关闭TCP连接时，该连接可能在关闭后的一段时间内保持超时状态（通常称为TIME_WAIT状态或2MSL等待状态）。对于使用已知套接字地址或端口的应用程序而言，如果存在处于超时状态的连接（包括地址和端口），则应用程序可能不能将套接字绑定到所需的SocketAddress上。
如果在使用bind (SocketAddress)方法“绑定套接字之前”启用SO_REUSEADDR选项，就可以允许绑定到处于超时状态的套接字。
**当创建ServerSocket时，SO_REUSEADDR的初始设置是不确定的，要依赖于操作系统的实现。在使用bind()方法绑定套接字后，启用或禁用SO_REUSEADDR时的行为是不确定的，也要依赖于操作系统的实现。**
**应用程序可以使用getReuseAddress()来判断SO_REUSEADDR的初始设置。**
public boolean getReuseAddress()方法的作用是测试是否启用SO_REUSEADDR。
在调用Socket类的close()方法时，会关闭当前连接，释放使用的端口，但在操作系统层面，并不会马上释放当前使用的端口。如果端口呈TIME_WAIT状态，则在Linux操作系统中可以重用此状态的端口。setReuseAddress (boolean)方法就是用来实现这样的功能的，也就是端口复用。端口复用的优点是可以大幅提升端口的使用率，用较少的端口数完成更多的任务。
**什么是TIME_WAIT状态？服务端（Server）与客户端（Client）建立TCP连接之后，主动关闭连接的一方就会进入TIME_WAIT状态。**例如，客户端主动关闭连接时，会发送最后一个ACK，然后客户端就会进入TIME_WAIT状态，再“停留若干时间”，然后进入CLOSED状态。在Linux操作系统中，当在“停留若干时间”段时，应用程序是可以复用呈TIME_WAIT状态的端口的，这样可提升端口利用率。
在Linux发行版CentOS中，默认允许端口复用。

示例代码参考example04包ServerSocketTest#test14、test15、test16、test17

### 4.2.13 Socket选项ReceiveBufferSize

**public void setReceiveBufferSize (int size)**方法的作用是**为从此ServerSocket接受的套接字的SO_RCVBUF选项设置新的建议值**。在接受的套接字中，实际被采纳的值必须在accept()方法返回套接字后通过调用Socket.getReceiveBufferSize()方法进行获取。
SO_RCVBUF的值用于设置内部套接字接收缓冲区的大小和设置公布到远程同位体的TCP接收窗口的大小。随后可以通过调用Socket.setReceiveBufferSize (int)方法更改该值。但是，**如果应用程序希望允许大于RFC 1323中定义的64KB的接收窗口，则在将ServerSocket绑定到本地地址之前必须在其中设置建议值**。这意味着，必须用无参数构造方法创建ServerSocket，然后必须调用setReceiveBufferSize()方法，最后通过调用bind()将ServerSocket绑定到地址。如果不是按照前面的顺序设置接收缓冲区的大小，也不会导致错误，缓冲区大小可能被设置为所请求的值，但是此ServerSocket中接受的套接字中的TCP接收窗口将不再大于64KB。
**public int getReceiveBufferSize()**方法的作用是获取此ServerSocket的SO_RCVBUF选项的值，该值是将用于从此ServerSocket接受的套接字的建议缓冲区大小。
**在接受的套接字中，实际设置的值通过调用Socket.getReceiveBufferSize()方法来确定**。
注意，对于客户端，**SO_RCVBUF选项必须在connect方法调用之前设置，对于服务端， SO_RCVBUF选项必须在bind()前设置。**

示例代码参考example04包ReceiveBufferSizeTest

首先运行服务端，再运行客户端，抓包，`右键->Follow->Tcp Stream`

![image-20200623130320993](/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter04/imgs//image-20200623130320993.png)

## 4.3 Socket类的使用

ServerSocket类作用是搭建Socket的服务端环境，而Socket类的主要作用是使Server与Client进行通信。Socket类包含很多实用且能增加软件运行效率的API方法，本节将介绍这些方法的功能及使用。

### 4.3.1 绑定bind与connect以及端口生成的时机

**public void bind (SocketAddress bindpoint)**方法的作用是将套接字绑定到本地地址。如果地址为null，则系统将随机挑选一个空闲的端口和一个有效的本地地址来绑定套接字。
在Socket通信的过程中，服务端和客户端都需要端口来进行通信。而在前面的示例中，都是使用“new ServerSocket (8888)”的代码格式来创建Socket服务端，其中8888就是服务端的端口号。使用代码“new Socket ("localhost", 8888)”来创建客户端的Socket并连接服务端的8888端口，客户端的端口并没有指定，而是采用自动分配端口号的算法。当然，在客户端的Socket中是可以指定使用某个具体的端口的，这个功能就由bind()方法提供。bind()方法就是将客户端绑定到指定的端口上，该方法要优先于connect()方法执行，也就是先绑定本地端口再执行连接方法。
**public void connect (SocketAddress endpoint)**方法的作用就是将此套接字连接到服务端。

### 4.3.2 连接与超时

**public void connect (SocketAddress endpoint, int timeout)**方法的作用是将此套接字连接到服务端，并指定一个超时值。超时值是0意味着无限超时。若时间超过timeout还没有连接到服务端，则出现异常。

示例代码参考example05包SocketTest#test1

### 4.3.3 获得远程端口与本地端口

public int getPort()方法的作用是返回此套接字连接到的远程端口。
public int getLocalPort()方法的作用是返回此套接字绑定到的本地端口。

示例代码参考example05包SocketTest#server、test1

### 4.3.4 获得本地InetAddress地址与本地SocketAddress地址

public InetAddress getLocalAddress()方法的作用是获取套接字绑定的本地InetAddress地址信息。

示例代码参考example05包SocketTest#server、test1

### 4.3.5 获得远程InetAddress与远程SocketAddress()地址

public InetAddress getInetAddress()方法的作用是返回此套接字连接到的远程的InetAddress地址。如果套接字是未连接的，则返回null。
public SocketAddress getRemoteSocketAddress()方法的作用是返回此套接字远程端点的SocketAddress地址，如果未连接，则返回null。

示例代码参考example05包SocketTest#server、test1

### 4.3.6 套接字状态的判断

public boolean isBound()方法的作用是返回套接字的绑定状态。如果将套接字成功地绑定到一个地址，则返回true。
public boolean isConnected()方法的作用是返回套接字的连接状态。如果将套接字成功地连接到服务端，则为true。
public boolean isClosed()方法的作用是返回套接字的关闭状态。如果已经关闭了套接字，则返回true。
public synchronized void close()方法的作用是关闭此套接字。所有当前阻塞于此套接字上的I/O操作中的线程都将抛出SocketException。套接字被关闭后，便不可在以后的网络连接中使用（即无法重新连接或重新绑定），如果想再次使用套接字，则需要创建新的套接字。
关闭此套接字也将会关闭该套接字的InputStream和OutputStream。如果此套接字有一个与之关联的通道，则关闭该通道。

示例代码参考example05包SocketTest#server、test2

### 4.3.7 开启半读与半写状态

**public void shutdownInput()**方法的作用是将套接字的输入流置于“流的末尾EOF”，也就是在套接字上调用shutdownInput()方法后从套接字输入流读取内容，流将返回EOF（文件结束符）。**发送到套接字的输入流端的任何数据都将在确认后被静默丢弃**。调用此方法的一端进入半读状态（read-half），也就是此端不能获得输入流，但对端却能获得输入流。一端能读，另一端不能读，称为半读。
**public void shutdownOutput()**方法的作用是禁用此套接字的输出流。对于TCP套接字，任何以前写入的数据都将被发送，并且后跟TCP的正常连接终止序列。如果在套接字上调用shutdownOutput()方法后写入套接字输出流，则该流将抛出IOException。调用此方法的一端进入半写状态（write-half），也就是此端不能获得输出流。但对端却能获得输出流。一端能写，另一端不能写，称为半写。

示例代码参考example05包SocketTest#test3、test4、test5、test6

### 4.3.8 判断半读半写状态

public boolean isInputShutdown()方法的作用是返回是否关闭套接字连接的半读状态（read-half）。如果已关闭套接字的输入，则返回true。
public boolean isOutputShutdown()方法的作用是返回是否关闭套接字连接的半写状态（write-half）。如果已关闭套接字的输出，则返回true。

示例代码参考example05包SocketTest#test3、test4、test5、test6

### 4.3.9 Socket选项TcpNoDelay

**public void setTcpNoDelay(boolean on)方法的作用是启用/禁用TCP_NODELAY（启用/禁用Nagle算法）**。参数为true，表示启用TCP_NODELAY；参数为false，表示禁用。
public boolean getTcpNoDelay()方法的作用是测试是否启用TCP_NODELAY。返回值为是否启用TCP_NODELAY的boolean值。

#### Nagle算法简介

Socket选项TCP_NODELAY与Nagle算法有关。**该算法可以将许多要发送的数据进行本地缓存（这一过程称为nagling），以减少发送数据包的个数来提高网络软件运行的效率，这就是Nagle算法被发明的初衷。**
**Nagle算法解决了处理小数据包众多而造成的网络拥塞。**网络拥塞的发生是指如果应用程序1次产生1个字节的数据，并且高频率地将它发送给对方网络，那么就会出现严重的网络拥塞。为什么只发送1个字节的数据就会出现这么严重的网络拥塞呢？这是因为在网络中将产生1个41字节的数据包，而不是1个字节的数据包，这41字节的数据包中包括1字节的用户数据以及40字节的TCP/IP协议头，这样的情况对于轻负载的网络来说还是可以接受的，但是在重负载的福特网络就受不了了，网络拥塞就发生了。
Nagle算法的原理是在未确认ACK之前让发送器把数据送到缓存里，后面的数据也继续放入缓存中，直到得到确认ACK或者直到“攒到”了一定大小（size）的数据再发送。尽管Nagle算法解决的问题只是局限于福特网络，然而同样的问题也可能出现在互联网上，因此，这个算法在互联网中也得到了广泛推广。
先来看看不使用Nagle算法时，数据是如何传输的：
![image-20200623154732606](/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter04/imgs//image-20200623154732606.png)

客户端向服务端传输很多小的数据包，造成了网络的拥塞，而使用Nagle算法后不再出现拥塞了。使用Nagle算法的数据传输过程：

![image-20200623154757192](/Users/zhukaishengy/StudyWorkSpace/nio-socket/src/main/java/com/autoai/chapter04/imgs//image-20200623154757192.png)

使用Nagle算法的数据传输过程是在第一个ACK确认之前，将要发送的数据放入缓存中，接收到ACK之后再发送一个大的数据包，以提升网络传输利用率。举个例子，客户端调用Socket的写操作将一个int型数据123456789（称为A块）写入到网络中，由于此时连接是空闲的（也就是说，还没有未被确认的小段），因此这个int类型的数据就会被马上发送到服务端。接着，客户端又调用写操作写入“\r\n”（简称B块），这个时候，因为A块的ACK没有返回，所以可以认为A块是一个未被确认的小段，这时B块在没有收到ACK之前是不会立即被发送到服务端的，一直等到A块的ACK收到（大概40ms之后）, B块才被发送。这里还隐藏了一个问题，就是A块数据的ACK为什么40ms之后才收到？这是因为**TCP/IP中不仅仅有Nagle算法，还有一个TCP“确认延迟（Delay）ACK”机制**，也就是当服务端收到数据之后，它并不会马上向客户端发送ACK，而是会将ACK的发送延迟一段时间（假设为t），**它希望在t时间内服务端会向客户端发送应答数据，这样ACK就能够和应答数据一起发送**，使应答数据和ACK一同发送到对方，节省网络通信的开销。
通过前面的介绍可以知道，Nagle算法是把要发送的数据放在本地缓存中，这就造成客户端与服务端之间交互并不是高互动的，是有一定延迟的，因此，可以使用TCP_NODELAY选项在套接字中开启或关闭这个算法。当然，到底使不使用Nagle算法是要根据实际的项目需求来决定的。
如果采用Nagle算法，那么一个数据包要“攒到”多大才将数据进行发送呢？**要“攒到”MSS大小才发送**！什么是MSS呢？MSS（Maximum Segment Size）即最大报文段长度。在TCP/IP中，无论发送多少数据，总是要在数据前面加上协议头，同时，对方接收到数据，也需要发送回ACK以表示确认。为了尽可能地利用网络带宽，TCP总是希望尽可能一次发送足够大的数据，此时就可以使用MSS来进行设置。MSS选项是TCP/IP定义的一个选项，该选项用于在TCP/IP连接建立时，收发双方协商通信时每一个报文段所能承载的最大数据长度，它的计算方式如下：
MSS = MTU - 20字节的TCP报头- 20字节的IP报头
在以太网环境下，MSS值一般就是1500 - 20 - 20 = 1460字节。TCP/IP希望每次都能够以MSS（最大尺寸）的数据块来发送数据，以增加每次网络传输的数据量。
Nagle算法就是为了尽可能发送大块数据，避免网络中充斥着许多小数据块。Nagle算法的基本含义是在任意的时刻，最多只能有一个未被确认的小段。所谓“小段”指的是小于MSS的数据块；所谓“未被确认”，是指一个数据块发送出去后，没有收到对方发送的ACK确认该数据已收到。
注意
BSD系统的实现是允许在空闲连接上发送大的写操作剩下的最后的小段，也就是说，当超过1个MSS数据发送时，内核先依次发送完n个完整的MSS数据包，然后发送尾部剩余的小数据包，其间不再延时等待。

TCP_NODELAY选项可以控制是否采用Nagle算法。**在默认情况下，发送数据采用的是Nagle算法**，这样虽然提高了网络吞吐量，但是实时性却降低了，在一些交互性很强的应用程序中是不允许的。使用TCP_NODELAY选项可以禁止Nagle算法。
通过前面的一些知识点的介绍，可以分析出以下两点。
1）如果要求高实时性，那么有数据发送时就要马上发送，此时可以将TCP_NODELAY选项设置为true，也就是屏蔽了Nagle算法。典型的应用场景就是开发一个网络格斗游戏，程序设计者希望玩家A每点击一次按键都会立即在玩家B的计算机中得以体现，而不是等到数据包达到最大时才通过网络一次性地发送全部数据，这时就可以屏蔽Nagle算法，传入参数true就达到实时效果了。
2）如果不要求高实时性，要减少发送次数达到减少网络交互，就将TCP_NODELAY设置为false，等数据包累积一定大小后再发送。
Nagle算法适用于大包、高延迟的场合，而对于要求交互速度的B/S或C/S就不合适了。在Socket创建的时候，默认都是使用Nagle算法的，这会导致交互速度严重下降，因此，需要屏蔽Nagle算法。不过，如果取消了Nagle算法，就会导致TCP碎片增多，效率可能会降低，因此，要根据实际的运行场景进行有效的取舍。

#### 启用与屏蔽Nagle算法的测试

示例代码参考example05包NagleTest

### 4.3.10 Socket选项SendBufferSize

Socket中的SO_RCVBUF选项是设置接收缓冲区的大小的，而SO_SNDBUF选项是设置发送缓冲区的大小的。
public synchronized void setSendBufferSize(int size)方法的作用是将此Socket的SO_SNDBUF选项设置为指定的值。平台的网络连接代码将SO_SNDBUF选项用作设置底层网络I/O缓存的大小的提示。由于SO_SNDBUF是一种提示，因此想要验证缓冲区设置大小的应用程序应该调用getSendBufferSize()方法。参数size用来设置发送缓冲区的大小，此值必须大于0。
public int getSendBufferSize()方法的作用是获取此Socket的SO_SNDBUF选项的值，该值是平台在Socket上输出时使用的缓冲区大小。返回值是此Socket的SO_SNDBUF选项的值。

示例代码参考example05包BufferSizeTest

### 4.3.11 Socket选项Linger

Socket中的SO_LINGER选项用来控制Socket关闭close()方法时的行为。在默认情况下，执行Socket的close()方法后，该方法会立即返回，但**底层的Socket实际上并不会立即关闭，它会延迟一段时间**。在延迟的时间里做什么呢？**是将“发送缓冲区”中的剩余数据在延迟的时间内继续发送给对方，然后才会真正地关闭Socket连接**。
public void setSoLinger(boolean on, int linger)方法的作用是启用/禁用具有指定逗留时间（以秒为单位）的SO_LINGER。最大超时值是特定于平台的。该设置仅影响套接字关闭。参数on的含义为是否逗留，参数linger的含义为逗留时间，单位为秒。
public int getSoLinger()方法的作用是返回SO_LINGER的设置。返回-1意味着禁用该选项。该设置仅影响套接字关闭。返回值代表SO_LINGER的设置。

1）on传入false, SO_LINGER功能被屏蔽，也就是**close()方法立即返回，但底层Socket并不关闭**，直到发送完缓冲区中的剩余数据，才会真正地关闭Socket的连接。
2）on传入true, linger等于0，当调用Socket的close()方法时，**将立即中断连接，也就是彻底丢弃在缓冲区中未发送完的数据，并且发送一个RST标记给对方**。
3）on传入true, linger大于65535时，linger值就被赋值为65535。
4）on传入true, linger不大于65535时，linger值就是传入的值。
5）如果执行代码“socket.setSoLinger(true, 5)”，那么执行Socket的close()方法时的行为随着数据量的多少而不同，总结如下。

* 数据量小：如果将“发送缓冲区”中的数据发送到对方的时间需要耗时3s，则close()方法阻塞3s，数据会被完整发送，3s后close()方法立即返回，因为3<5。
* 数据量大：如果将“发送缓冲区”中的数据发送到对方的时间需要耗时8s，则close()方法阻塞5s,5s之后发送RST标记给对方，连接断开，因为8>5。

验证：在on=true、linger=0时，close()方法立即返回且丢弃数据，并且发送RST标记
验证：在on=false时，close()方法立即返回并且数据不丢失，正常进行4次“挥手”
验证：如果只是调用close()方法，则立即返回并且数据不丢失，正常进行4次“挥手”
测试：在on=true、linger=10时，发送数据耗时小于10s的情况
如果将“发送缓冲区”中的数据发送给对方需要耗时3s，则close()方法阻塞3s，数据被完整发送，不会丢失。
测试：在on=true、linger=1时，发送数据耗时大于1s的情况
如果将“发送缓冲区”中的数据发送给对方需要耗时8s，则close()方法阻塞1s后连接立即关闭，并发送RST标记给对方。

示例代码参考example05包LingerTest

### 4.3.12 Socket选项Timeout

**setSoTimeout(int timeout)方法的作用是启用/禁用带有指定超时值的SO_TIMEOUT**，以毫秒为单位。将此选项设为非零的超时值时，**在与此Socket关联的InputStream上调用read()方法将只阻塞此时间长度。如果超过超时值，就将引发java.net.SocketTimeoutException**，尽管Socket仍旧有效。启用timeOut特性必须在进入阻塞操作前被启用才能生效。超时值必须是大于0的数。超时值为0被解释为无穷大超时值。
public int getSoTimeout()方法的作用是返回SO_TIMEOUT的设置。返回0意味着禁用了选项（默认即无穷大的超时值）。

示例代码参考example05包TimeoutTest

### 4.3.13 Socket选项OOBInline

Socket的选项SO_OOBINLINE的作用是**在套接字上接收的所有TCP紧急数据都将通过套接字输入流接收。禁用该选项时（默认），将悄悄丢弃紧急数据。**OOB（Out Of Bound，带外数据）可以理解成是需要紧急发送的数据。
setOOBInline(true)方法的作用是启用/禁用OOBINLINE选项（TCP紧急数据的接收者）**，默认情况下，此选项是禁用的**，即在套接字上接收的TCP紧急数据被静默丢弃。如果用户希望接收到紧急数据，则必须启用此选项。启用时，可以将紧急数据内嵌在普通数据中接收。注意，仅为处理传入紧急数据提供有限支持。特别要指出的是，不提供传入紧急数据的任何通知并且不存在区分普通数据和紧急数据的功能（除非更高级别的协议提供）。参数on传入true表示启用OOBINLINE，传入false表示禁用。public void setOOBInline(boolean on)方法在接收端进行设置来决定是否接收与忽略紧急数据。在发送端，使用public void sendUrgentData (int data)方法发送紧急数据。
Socket类的public void sendUrgentData (int data)方法向对方发送1个单字节的数据，但是这个单字节的数据并不存储在输出缓冲区中，而是立即将数据发送出去，而在对方程序中并不知道发送过来的数据是由OutputStream还是由sendUrgentData(int data)发送过来的。

在调用sendUrgentData()方法时所发送的数据可以被对方所忽略，结合这个特性可以实现测试网络连接状态的心跳机制

示例代码参考example05包OOBInlineTest

### 4.3.14 Socket选项KeepAlive

Socket选项SO_KEEPALIVE的作用是在创建了服务端与客户端时，使客户端连接上服务端。**当设置SO_KEEPALIVE为true时，若对方在某个时间（时间取决于操作系统内核的设置）内没有发送任何数据过来，那么端点都会发送一个ACK探测包到对方，探测双方的TCP/IP连接是否有效（对方可能断电，断网）。如果不设置此选项，那么当客户端宕机时，服务端永远也不知道客户端宕机了，仍然保存这个失效的连接。如果设置了此选项，就会将此连接关闭。**
public boolean getKeepAlive()方法的作用是判断是否启用SO_KEEPALIVE选项。
public void setKeepAlive(boolean on)方法的作用是设置是否启用SO_KEEPALIVE选项。参数on代表是否开启保持活动状态的套接字。	
在2个小时之后，客户端向服务端发送了TCP Keep-Alive探测是否存活的ACK数据包，而服务端也向客户端发送了同样类型的ACK回复数据包，但是该选项**在实际软件开发中并不是常用的技术，判断连接是否正常时，较常用的办法是启动1个线程，在线程中使用轮询嗅探的方式来判断连接是否为正常的状态。**

### 4.3.15 Socket选项TrafficClass

IP规定了以下4种服务类型，用来定性地描述服务的质量。
1）IPTOS_LOWCOST（0x02）：发送成本低。
2）IPTOS_RELIABILITY（0x04）：高可靠性，保证把数据可靠地送到目的地。
3）IPTOS_THROUGHPUT（0x08）：最高吞吐量，一次可以接收或者发送大批量的数据。
4）IPTOS_LOWDELAY（0x10）：最小延迟，传输数据的速度快，把数据快速送达目的地。
这4种服务类型还可以使用“或”运算进行相应的组合。
public void setTrafficClass(int tc)方法的作用是为从此Socket上发送的包在IP头中设置流量类别（traffic class）。
public int getTrafficClass()方法的作用是为从此Socket上发送的包获取IP头中的流量类别或服务类型。
当向IP头中设置了流量类型后，路由器或交换机就会根据这个流量类型来进行不同的处理，同时必须要硬件设备进行参与处理。

## 4.4 基于UDP的Socket通信

> 注意，在使用UDP实现Socket通信时一定要使用两台真机，不要使用虚拟机，不然会出现UDP包无法发送的情况。

UDP（User Datagram Protocol，用户数据报协议）是一种**面向无连接的传输层协议，提供不可靠的信息传送服务。**

* 无连接是指**通信时服务端与客户端不需要建立连接**，直接把数据包从一端发送到另一端，对方获取数据包再进行数据的处理。
* UDP是“不可靠的”，是指该**协议在网络环境不好的情况下，会丢失数据包，因为没有数据包重传的功能，另外它也不提供对数据包进行分组、组装，以及不能对数据包进行排序。使用UDP发送报文后，是无法得知其是否安全，以及是否完整地到达目的地的。**
* 因为**UDP报文没有可靠性保证、不能确保数据的发送和接收的顺序，以及没有流量控制等功能，所以它可靠性较差**。但是，正因为UDP的控制选项较少，**在数据传输过程中延迟小、数据传输效率高，因而适合对可靠性要求不高的应用程序**。
* **通常音频、视频和普通数据在传送时使用UDP较多，因为它们即使偶尔丢失一两个数据包，也不会对接收结果产生太大影响**
* UDP和TCP都属于传输层协议。

### 4.4.1 使用UDP实现Socket通信

在使用UDP实现Socket通信时，服务端与客户端都是使用DatagramSocket类，传输的数据要存放在DatagramPacket类中。
DatagramSocket类表示用来发送和接收数据报包的套接字。数据报套接字是包投递服务的发送或接收点。每个在数据报套接字上发送或接收的包都是单独编址和路由的。从一台机器发送到另一台机器的多个包可能选择不同的路由，也可能按不同的顺序到达。在DatagramSocket上总是启用UDP广播发送。为了接收广播包，应该将DatagramSocket绑定到通配符地址。在某些实现中，将DatagramSocket绑定到一个更加具体的地址时广播包也可以被接收。

DatagramSocket类中的**public synchronized void receive(DatagramPacket p)**方法的作用是从此套接字接收数据报包。当此方法返回时，DatagramPacket的缓冲区填充了接收的数据。数据报包也包含发送方的IP地址和发送方机器上的端口号。此方法在接收到数据报前一直阻塞。数据报包对象的length字段包含所接收信息的长度。如果发送的信息比接收端包关联的byte[]长度长，该信息将被截短。如果发送信息的长度大于65507，则发送端出现异常。
DatagramSocket类中的**public void send(DatagramPacket p)**方法的作用是从此套接字发送数据报包。DatagramPacket包含的信息有：将要发送的数据及其长度、远程主机的IP地址和远程主机的端口号。
DatagramPacket类中的**public synchronized byte[] getData()**方法的作用是返回数据缓冲区。接收到的或将要发送的数据从缓冲区中的偏移量offset处开始，持续length长度。
本测试要实现的是客户端使用UDP将字符串1234567890传递到服务端。

示例代码参考example06包DatagramSocketTest

### 4.4.2 测试发送超大数据量的包导致数据截断的情况

理论上，一个UDP包最大的长度为216- 1（65536 - 1 = 65535），因此，IP包最大的发送长度为65535。但是，在这65535之内包含IP协议头的20个字节，还有UDP协议头的8个字节，即65535 - 20 - 8 = 65507，因此，UDP传输用户数据最大的长度为65507。如果传输的数据大于65507，则在发送端出现异常。

示例代码参考example06包DatagramSocketTest

### 4.4.3 Datagram Packet类中常用API的使用

DatagramPacket类中的**public synchronized void setData(byte[] buf)**方法的作用是为此包设置数据缓冲区。将此DatagramPacket的偏移量设置为0，长度设置为buf的长度。
DatagramPacket类中的**public synchronized void setData(byte[] buf, int offset, int length)**方法的作用是为此包设置数据缓冲区。此方法设置包的数据、长度和偏移量。
DatagramPacket类中的**public synchronized int getOffset()**方法的作用是返回将要发送或接收到的数据的偏移量。
DatagramPacket类中的**public synchronized void setLength(int length)**方法的作用是为此包设置长度。包的长度是指包数据缓冲区中将要发送的字节数，或用来接收数据的包数据缓冲区的字节数。长度必须小于或等于偏移量与包缓冲区长度之和。

### 4.4.4 使用UDP实现单播

“单播”就是将数据报文让1台计算机知道。

示例代码参考example06包DatagramSocketTest

### 4.4.5 使用UDP实现广播

“广播”就是将数据报文让其他计算机都知道。

示例代码参考example06包BroadcastTest

### 4.4.6 使用UDP实现组播

“组播”就是将数据报文让指定的计算机知道。组播也称为多播。
注意，如果在两台物理计算机中进行UDP组播的测试，一定要将多余的网卡禁用，不然会出现使用wireshark工具可以抓到组播包，但receive()方法依然是阻塞的情况，但如果服务端与客户端在同一台中，就不会出现这样的情况。

示例代码参考example06包MulticastSocketTest

## 4.5 小结

本章主要介绍了使用ServerSocket和Socket实现TCP/IP数据通信，使用DatagramSocket和DatagramPacket实现UDP数据通信，并详细介绍了这些类中API的使用，以及全部Socket Option选项的特性，同时，分析了TCP/IP通信时的“握手”与“挥手”，熟练掌握这些知识有助于理解网络编程的特性，对学习ServerSocketChannel通道起到非常重要的铺垫作用。那么在下一章就要学习选择器与ServerSocketChannel通道实现多路复用，深入到NIO高性能处理的核心。
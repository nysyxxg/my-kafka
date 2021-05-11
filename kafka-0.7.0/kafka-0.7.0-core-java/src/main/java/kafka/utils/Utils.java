package kafka.utils;

import kafka.message.CompressionCodec;
import kafka.message.NoCompressionCodec;
import org.apache.log4j.Logger;
import scala.Tuple2;

import javax.management.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.zip.CRC32;


public class Utils {
    private static Logger logger = Logger.getLogger(Utils.class);
    
    
    public static Runnable runnable(UnitFunction func) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                func.call();
            }
        };
        return runnable;
    }
    
    
    public static Runnable loggedRunnable(UnitFunction func) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    func.call();
                    System.out.println("-----");
                } catch (Exception t) {
                    logger.error(t, t);
                    logger.error(stackTrace(t), t);
                }
            }
        };
        return runnable;
    }
    
    public static String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    public static Boolean getBoolean(Properties props, String name, Boolean bl) {
        if (!props.containsKey(name)) {
            return bl;
        } else if ("true".equalsIgnoreCase(props.getProperty(name))) {
            return true;
        } else if ("false".equalsIgnoreCase(props.getProperty(name))) {
            return false;
        } else {
            throw new IllegalArgumentException("Unacceptable value for property '" + name + "', boolean values must be either 'true' or 'false");
        }
    }
    
    public static int getInt(Properties props, String name, int def) {
        return getIntInRange(props, name, def, new Tuple2<Integer, Integer>(Integer.MIN_VALUE, Integer.MAX_VALUE));
    }
    
    public static int getIntInRange(Properties props, String name, int def, Tuple2<Integer, Integer> range) {
        int v = 0;
        if (props.containsKey(name)) {
            v = Integer.valueOf(props.getProperty(name));
        } else {
            v = def;
        }
        if (v < range._1 || v > range._2) {
            throw new IllegalArgumentException(name + " has value " + v + " which is not in the range " + range + ".");
        } else {
            return v;
        }
    }
    
    
    public Thread daemonThread(String name, Runnable runnable) {
        return newThread(name, runnable, true);
    }
    
    public Thread daemonThread(String name, UnitFunction fun) {
        return daemonThread(name, runnable(fun));
    }
    
    
    public static Thread newThread(String name, Runnable runnable, Boolean daemon) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(daemon);
        return thread;
    }
    
    
    public byte[] readBytes(ByteBuffer buffer, int offset, int size) {
        byte bytes[] = new byte[size];
        int i = 0;
        while (i < size) {
            bytes[i] = buffer.get(offset + i);
            i += 1;
        }
        return bytes;
    }
    
    
    public static String readShortString(ByteBuffer buffer, String encoding) {
        int size = buffer.getShort();
        if (size < 0) {
            return null;
        }
        byte bytes[] = new byte[size];
        buffer.get(bytes);
        try {
            return new String(bytes, encoding);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    
    public static void writeShortString(ByteBuffer buffer, String string, String encoding) {
        if (string == null) {
            buffer.putShort((short) -1);
        } else if (string.length() > Short.MAX_VALUE) {
            throw new IllegalArgumentException("String exceeds the maximum size of " + Short.MAX_VALUE + ".");
        } else {
            buffer.putShort((short) string.length());
            try {
                buffer.put(string.getBytes(encoding));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }
    
    
    public static Properties loadProps(String filename) {
        FileInputStream propStream;
        Properties props = new Properties();
        try {
            propStream = new FileInputStream(filename);
            props.load(propStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
        return props;
    }
    
    public static int getInt(Properties props, String name) {
        if (props.containsKey(name))
            return getInt(props, name, -1);
        else
            throw new IllegalArgumentException("Missing required property '" + name + "'");
    }
    
    public static String getString(Properties props, String name, String defaultStr) {
        if (props.containsKey(name))
            return props.getProperty(name);
        else
            return defaultStr;
    }
    
    public static String getString(Properties props, String name) {
        if (props.containsKey(name))
            return props.getProperty(name);
        else
            throw new IllegalArgumentException("Missing required property '" + name + "'");
    }
    
    
    Properties getProps(Properties props, String name) {
        if (props.containsKey(name)) {
            String propString = props.getProperty(name);
            String propValues[] = propString.split(",");
            Properties properties = new Properties();
            for (int i = 0; i < propValues.length; i++) {
                String prop[] = propValues[i].split("=");
                if (prop.length != 2)
                    throw new IllegalArgumentException("Illegal format of specifying properties '" + propValues[i] + "'");
                properties.put(prop[0], prop[1]);
            }
            return properties;
        } else
            throw new IllegalArgumentException("Missing required property '" + name + "'");
    }
    
    
    public static Properties getProps(Properties props, String name, Properties defaultP) {
        if (props.containsKey(name)) {
            String propString = props.getProperty(name);
            String propValues[] = propString.split(",");
            if (propValues.length < 1)
                throw new IllegalArgumentException("Illegal format of specifying properties '" + propString + "'");
            Properties properties = new Properties();
            for (int i = 0; i < propValues.length; i++) {
                String prop[] = propValues[i].split("=");
                if (prop.length != 2)
                    throw new IllegalArgumentException("Illegal format of specifying properties '" + propValues[i] + "'");
                properties.put(prop[0], prop[1]);
            }
            return properties;
        } else
            return defaultP;
    }
    
    public static FileChannel openChannel(File file, Boolean mutable) {
        FileChannel fileChannel = null;
        if (mutable) {
            try {
                fileChannel = new RandomAccessFile(file, "rw").getChannel();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            try {
                fileChannel = new FileInputStream(file).getChannel();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return fileChannel;
    }
    
    public static void func(UnitFunction func) {
        func.call();
    }
    
    void swallow(String logSign, UnitFunction function) {
        try {
            func(function);
        } catch (Exception e) {
            if (logSign.equalsIgnoreCase("info")) {
                logger.info(e.getMessage(), e);
            } else if (logSign.equalsIgnoreCase("error")) {
                logger.error(e.getMessage(), e);
            } else if (logSign.equalsIgnoreCase("warn")) {
                logger.warn(e.getMessage(), e);
            }
        }
    }
    
    void swallow(String logSign, Function function2) {
        try {
            function2.apply(null);
        } catch (Exception e) {
            if (logSign.equalsIgnoreCase("info")) {
                logger.info(e.getMessage(), e);
            } else if (logSign.equalsIgnoreCase("error")) {
                logger.error(e.getMessage(), e);
            } else if (logSign.equalsIgnoreCase("warn")) {
                logger.warn(e.getMessage(), e);
            }
        }
    }
    
    
    Boolean equal(ByteBuffer b1, ByteBuffer b2) {
        // two byte buffers are equal if their position is the same,
        // their remaining bytes are the same, and their contents are the same
        if (b1.position() != b2.position())
            return false;
        if (b1.remaining() != b2.remaining())
            return false;
        for (int i = 0; i < b1.remaining(); i++) {
            if (b1.get(i) != b2.get(i))
                return false;
        }
        return true;
    }
    
    String toString(ByteBuffer buffer, String encoding) {
        byte bytes[] = new byte[buffer.remaining()];
        buffer.get(bytes);
        try {
            return new String(bytes, encoding);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }
    
    void croak(String message) {
        System.err.println(message);
        System.exit(1);
    }
    
    void rm(String file) {
        rm(new File(file));
    }
    
    void rm(File file) {
        if (file == null) {
            return;
        } else if (file.isDirectory()) {
            File files[] = file.listFiles();
            if (files != null) {
                for (File f : files)
                    rm(f);
            }
            file.delete();
        } else {
            file.delete();
        }
    }
    
    public static void registerMBean(Object mbean, String name) {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        synchronized (mbs) {
            ObjectName objName = null;
            try {
                objName = new ObjectName(name);
                if (mbs.isRegistered(objName)) {
                    mbs.unregisterMBean(objName);
                }
            } catch (MalformedObjectNameException e) {
                e.printStackTrace();
            } catch (InstanceNotFoundException e) {
                e.printStackTrace();
            } catch (MBeanRegistrationException e) {
                e.printStackTrace();
            }
            
            try {
                mbs.registerMBean(mbean, objName);
            } catch (InstanceAlreadyExistsException e) {
                e.printStackTrace();
            } catch (MBeanRegistrationException e) {
                e.printStackTrace();
            } catch (NotCompliantMBeanException e) {
                e.printStackTrace();
            }
        }
    }
    
    public static void unregisterMBean(String name) {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        synchronized (mbs) {
            ObjectName objName = null;
            try {
                objName = new ObjectName(name);
            } catch (MalformedObjectNameException e) {
                e.printStackTrace();
            }
            if (mbs.isRegistered(objName)) {
                try {
                    mbs.unregisterMBean(objName);
                } catch (InstanceNotFoundException e) {
                    e.printStackTrace();
                } catch (MBeanRegistrationException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    Long getUnsignedInt(ByteBuffer buffer) {
        return buffer.getInt() & 0xffffffffL;
    }
    
    
    public static Long getUnsignedInt(ByteBuffer buffer, int index) {
        return buffer.getInt(index) & 0xffffffffL;
    }
    
    
    public static void putUnsignedInt(ByteBuffer buffer, Long value) {
        buffer.putInt((int) (value & 0xffffffffL));
    }
    
    
    void putUnsignedInt(ByteBuffer buffer, int index, Long value) {
        Long vl = value & 0xffffffffL;
        buffer.putInt(index, new Long(vl).intValue());
    }
    
    
    public static Long crc32(byte bytes[]) {
        return crc32(bytes, 0, bytes.length);
    }
    
    
    public static Long crc32(byte bytes[], int offset, int size) {
        CRC32 crc = new CRC32();
        crc.update(bytes, offset, size);
        return crc.getValue();
    }
    
    public static int hashcode(Object... as) {
        if (as == null) {
            return 0;
        }
        int h = 1;
        int i = 0;
        while (i < as.length) {
            if (as[i] != null) {
                h = 31 * h + as[i].hashCode();
                i += 1;
            }
        }
        return h;
    }
    
    public Map groupby(Iterable vals, UnitFunction func) {
        Map m = new HashMap();
        Iterator it = vals.iterator();
        while (it.hasNext()) {
            Object v = it.next();
            Object k = func.call(v);
            Object value = m.get(k);
            if (value != null) {
                List<Object> list = (List<Object>) value;
                list.add(v);
                m.put(k, list);
            } else {
                List<Object> list = new ArrayList<>();
                list.add(v);
                m.put(k, list);
            }
        }
        return m;
    }
    
    public static int read(ReadableByteChannel channel, ByteBuffer buffer) {
        int index = 0;
        try {
            index = channel.read(buffer);
            if (index == -1) {
                throw new EOFException("Received -1 when reading from channel, socket has likely been closed.");
            } else {
                return index;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return index;
    }
    
    Object notNull(Object v) {
        if (v == null)
            throw new IllegalArgumentException("Value cannot be null.");
        else
            return v;
    }
    
    Tuple2<String, Integer> getHostPort(String hostport) {
        String splits[] = hostport.split(":");
        return new Tuple2<>(splits[0], Integer.valueOf(splits[1]));
    }
    
    public static Tuple2<String, Integer> getTopicPartition(String topicPartition) {
        int index = topicPartition.lastIndexOf('-');
        return new Tuple2<>(topicPartition.substring(0, index), Integer.valueOf(topicPartition.substring(index + 1)));
    }
    
    
    private static Map getCSVMap(String allCSVals, String exceptionMsg, String successMsg) {
        HashMap map = new HashMap();
        if ("".equals(allCSVals)) {
            return map;
        }
        String csVals[] = allCSVals.split(",");
        for (int i = 0; i < csVals.length; i++) {
            try {
                String tempSplit[] = csVals[i].split(":");
                logger.info(successMsg + tempSplit[0] + " : " + Integer.parseInt(tempSplit[1].trim()));
                map.put(tempSplit[0], Integer.parseInt(tempSplit[1].trim()));
            } catch (Exception ex) {
                logger.error(exceptionMsg + ": " + csVals[i]);
            }
        }
        return map;
    }
    
    
    public static List<String> getCSVList(String csvList) {
        List<String> list = new ArrayList<>();
        if (csvList == null) {
            new ArrayList<>();
        } else {
            String array[] = csvList.split(",");
            for (String value : array) {
                if (!value.equals("")) {
                    list.add(value);
                }
            }
        }
        return list;
    }
    
    public static Map getTopicRentionHours(String retentionHours) {
        String exceptionMsg = "Malformed token for topic.log.retention.hours in server.properties: ";
        String successMsg = "The retention hour for ";
        return getCSVMap(retentionHours, exceptionMsg, successMsg);
    }
    
    public static Map getTopicFlushIntervals(String allIntervals) {
        String exceptionMsg = "Malformed token for topic.flush.Intervals.ms in server.properties: ";
        String successMsg = "The flush interval for ";
        return getCSVMap(allIntervals, exceptionMsg, successMsg);
    }
    
    public static Map getTopicPartitions(String allPartitions) {
        String exceptionMsg = "Malformed token for topic.partition.counts in server.properties: ";
        String successMsg = "The number of partitions for topic  ";
        return getCSVMap(allPartitions, exceptionMsg, successMsg);
    }
    
    public static Map<String, Integer> getConsumerTopicMap(String consumerTopicString) {
        String exceptionMsg = "Malformed token for embeddedconsumer.topics in consumer.properties: ";
        String successMsg = "The number of consumer thread for topic  ";
        return getCSVMap(consumerTopicString, exceptionMsg, successMsg);
    }
    
    public static Object getObject(String className) {
        if (className == null) {
            return null;
        } else {
            Class clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            Class clazzT = clazz;
            Constructor<?>[] constructors = clazzT.getConstructors();
            require(constructors.length == 1);
            try {
                return constructors[0].newInstance();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    
    private static void require(boolean requirement) {
        if (!requirement)
            throw new IllegalArgumentException("requirement failed");
    }
    
    Boolean propertyExists(String prop) {
        if (prop == null)
            return false;
        else if (prop.compareTo("") == 0)
            return false;
        else {
            return true;
        }
    }
    
    public static CompressionCodec getCompressionCodec(Properties props, String codec) {
        String codecValueString = props.getProperty(codec);
        if (codecValueString == null)
            return new NoCompressionCodec();
        else
            return CompressionCodec.getCompressionCodec(Integer.parseInt(codecValueString));
    }
}






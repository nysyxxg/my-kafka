package kafka.utils;

import org.apache.log4j.Logger;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;

public class Mx4jLoader {
    
    private static Logger logger = Logger.getLogger(Mx4jLoader.class);
    
    public static Boolean maybeLoad() {
        if (!Utils.getBoolean(System.getProperties(), "kafka_mx4jenable", false)) {
            return false;
        }
        String address = System.getProperty("mx4jaddress", "0.0.0.0");
        int port = Utils.getInt(System.getProperties(), "mx4jport", 8082);
        try {
            logger.debug("Will try to load MX4j now, if it's in the classpath");
            
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName processorName = new ObjectName("Server:name=XSLTProcessor");
            
            Class httpAdaptorClass = Class.forName("mx4j.tools.adaptor.http.HttpAdaptor");
            Object httpAdaptor = httpAdaptorClass.newInstance();
            httpAdaptorClass.getMethod("setHost", String.class).invoke(httpAdaptor, address);
            httpAdaptorClass.getMethod("setPort", Integer.TYPE).invoke(httpAdaptor, port);
            
            ObjectName httpName = new ObjectName("system:name=http");
            mbs.registerMBean(httpAdaptor, httpName);
            
            Class xsltProcessorClass = Class.forName("mx4j.tools.adaptor.http.XSLTProcessor");
            Object xsltProcessor = xsltProcessorClass.newInstance();
            httpAdaptorClass.getMethod("setProcessor", Class.forName("mx4j.tools.adaptor.http.ProcessorMBean")).invoke(httpAdaptor, xsltProcessor);
            mbs.registerMBean(xsltProcessor, processorName);
            httpAdaptorClass.getMethod("start").invoke(httpAdaptor);
            logger.info("mx4j successfuly loaded");
            return true;
        } catch (MBeanRegistrationException e) {
            e.printStackTrace();
        } catch (InstanceAlreadyExistsException e) {
            e.printStackTrace();
        } catch (NotCompliantMBeanException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            logger.info("Will not load MX4J, mx4j-tools.jar is not in the classpath");
        } catch (MalformedObjectNameException e) {
            e.printStackTrace();
            logger.warn("Could not start register mbean in JMX", e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return false;
    }
}

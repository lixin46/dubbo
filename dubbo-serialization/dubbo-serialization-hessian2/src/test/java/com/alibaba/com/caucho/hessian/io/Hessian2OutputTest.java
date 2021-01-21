package com.alibaba.com.caucho.hessian.io;

import org.apache.dubbo.common.serialize.hessian2.Hessian2SerializerFactory;
import org.junit.jupiter.api.Test;

import java.io.*;

public class Hessian2OutputTest {

    @Test
    public void init() throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        Hessian2Output output = new Hessian2Output(byteStream);
        output.setSerializerFactory(Hessian2SerializerFactory.SERIALIZER_FACTORY);
        output.setCloseStreamOnClose(true);

        User original = new User("hello", 100);

        output.writeObject(original);
        output.flushBuffer();

        Hessian2Input input = new Hessian2Input(new ByteArrayInputStream(byteStream.toByteArray()));
        input.setSerializerFactory(Hessian2SerializerFactory.SERIALIZER_FACTORY);
        input.setCloseStreamOnClose(true);

        User user = (User) input.readObject(User.class);
        System.out.println();
    }

    public static class User implements Serializable {

        public String name;
        public int age;

        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }
}

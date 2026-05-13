package com.ruby.rubyaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.messages.Message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 线程安全的 Kryo 序列化工具，用于序列化 Spring AI 的 Message。
 * Kryo 实例非线程安全，因此使用 ThreadLocal 缓存。
 */
public final class KryoSerializer {

    private static final ThreadLocal<Kryo> KRYO_LOCAL = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        return kryo;
    });

    private KryoSerializer() {}

    /** 序列化单条 Message */
    public static byte[] serialize(Message message) {
        Kryo kryo = KRYO_LOCAL.get();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            kryo.writeClassAndObject(output, message);
            output.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Kryo serialize message failed", e);
        }
    }

    /** 反序列化单条 Message */
    public static Message deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        Kryo kryo = KRYO_LOCAL.get();
        try (Input input = new Input(new ByteArrayInputStream(bytes))) {
            Object obj = kryo.readClassAndObject(input);
            return (Message) obj;
        } catch (Exception e) {
            throw new RuntimeException("Kryo deserialize message failed", e);
        }
    }

    /** 序列化整段对话 List<Message> */
    public static byte[] serializeList(List<Message> messages) {
        Kryo kryo = KRYO_LOCAL.get();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            kryo.writeObject(output, new ArrayList<>(messages));
            output.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Kryo serialize list failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Message> deserializeList(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new ArrayList<>();
        Kryo kryo = KRYO_LOCAL.get();
        try (Input input = new Input(new ByteArrayInputStream(bytes))) {
            return kryo.readObject(input, ArrayList.class);
        } catch (Exception e) {
            throw new RuntimeException("Kryo deserialize list failed", e);
        }
    }
}

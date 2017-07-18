package org.jchien.twitchbrowser.cache;

import com.lambdaworks.redis.codec.ByteArrayCodec;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.codec.StringCodec;

import java.nio.ByteBuffer;

/**
 * @author jchien
 */
public class StringByteArrayCodec implements RedisCodec<String, byte[]> {
    public static final StringByteArrayCodec INSTANCE = new StringByteArrayCodec();

    @Override
    public String decodeKey(ByteBuffer bytes) {
        return StringCodec.UTF8.decodeKey(bytes);
    }

    @Override
    public byte[] decodeValue(ByteBuffer bytes) {
        return ByteArrayCodec.INSTANCE.decodeValue(bytes);
    }

    @Override
    public ByteBuffer encodeKey(String key) {
        return StringCodec.UTF8.encodeKey(key);
    }

    @Override
    public ByteBuffer encodeValue(byte[] value) {
        return ByteArrayCodec.INSTANCE.encodeValue(value);
    }
}

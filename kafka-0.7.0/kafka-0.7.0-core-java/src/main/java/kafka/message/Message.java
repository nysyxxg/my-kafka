package kafka.message;

import kafka.common.UnknownMagicByteException;
import kafka.utils.Utils;

import java.nio.ByteBuffer;

public class Message {
    
    static byte MagicVersion1 = 0;
    static byte MagicVersion2 = 1;
    static byte CurrentMagicValue = 1;
    static int MagicOffset = 0;
    static int MagicLength = 1;
    static int AttributeOffset = MagicOffset + MagicLength;
    static int AttributeLength = 1;
    
    static int CompressionCodeMask = 0x03;
    int NoCompression = 0;
    
    static int crcOffset(byte magic) {
        if (magic == MagicVersion1) {
            return MagicOffset + MagicLength;
        }
        if (magic == MagicVersion2) {
            return AttributeOffset + AttributeLength;
        } else {
            throw new UnknownMagicByteException("Magic byte value of %d is unknown".format(String.valueOf(magic)));
        }
    }
    
    static int CrcLength = 4;
    
    static int payloadOffset(byte magic) {
        return crcOffset(magic) + CrcLength;
    }
    
    
    static int headerSize(byte magic) {
        return payloadOffset(magic);
    }
    
    public static int MinHeaderSize = headerSize((byte) 0);
    
    public ByteBuffer buffer;
    
    public Message(ByteBuffer buffer) {
        this.buffer = buffer;
    }
    
    public Message(Long checksum, byte bytes[], CompressionCodec compressionCodec) {
        this(ByteBuffer.allocate(Message.headerSize(Message.CurrentMagicValue) + bytes.length));
        buffer.put(CurrentMagicValue);
        byte attributes = 0;
        if (compressionCodec.codec > 0) {
            attributes = (byte) (attributes | (Message.CompressionCodeMask & compressionCodec.codec));
        }
        buffer.put(attributes);
        Utils.putUnsignedInt(buffer, checksum);
        buffer.put(bytes);
        buffer.rewind();
    }
    
    public Message(Long checksum, byte bytes[]) {
        this(checksum, bytes, new NoCompressionCodec());
    }
    
    public Message(byte bytes[], CompressionCodec compressionCodec) {
        //Note: we're not crc-ing the attributes header, so we're susceptible to bit-flipping there
        this(Utils.crc32(bytes), bytes, compressionCodec);
    }
    
    public Message(byte bytes[]) {
        this(bytes, new NoCompressionCodec());
    }
    
    
    int size = buffer.limit();
    
    byte magic = buffer.get(MagicOffset);
    
    int payloadSize = size - headerSize(magic);
    
    
    byte attributes = buffer.get(AttributeOffset);
    
    CompressionCodec compressionCodec() {
        if (magic == 0) {
            return new NoCompressionCodec();
        } else if (magic == 1) {
            return CompressionCodec.getCompressionCodec(buffer.get(AttributeOffset) & CompressionCodeMask);
        } else {
            throw new RuntimeException("Invalid magic byte " + magic);
        }
    }
    
    Long checksum = Utils.getUnsignedInt(buffer, crcOffset(magic));
    
    
    public ByteBuffer payload() {
        ByteBuffer payload = buffer.duplicate();
        payload.position(headerSize(magic));
        payload = payload.slice();
        payload.limit(payloadSize);
        payload.rewind();
        return payload;
    }
    
    
    public Boolean isValid() {
        return checksum == Utils.crc32(buffer.array(), buffer.position() + buffer.arrayOffset() + payloadOffset(magic), payloadSize);
    }
    
    int serializedSize = 4 /* int size*/ + buffer.limit();
    
    
    void serializeTo(ByteBuffer serBuffer) {
        serBuffer.putInt(buffer.limit());
        serBuffer.put(buffer.duplicate());
    }
    
    @Override
    public String toString() {
        ByteBuffer payload = payload();
        return "message(magic = %d, attributes = %d, crc = %d, payload = %s)".format(String.valueOf(magic), attributes, checksum, payload);
    }
    
    public boolean equals(Object any) {
        if (any instanceof Message) {
            Message that = (Message) any;
            return size == that.size && attributes == that.attributes && checksum == that.checksum && payload() == that.payload() && magic == that.magic;
        } else {
            return false;
        }
    }
    
    public int hashCode() {
        return buffer.hashCode();
    }
}

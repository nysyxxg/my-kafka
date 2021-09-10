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
    
    static int CrcLength = 4;
    
    public  static int MinHeaderSize;
    
    public ByteBuffer buffer;
    
    public int payloadSize;
    public int size;
    public byte magic;
    public byte attributes;
    public long checksum;
    public int serializedSize;
    
    static {
         MinHeaderSize = headerSize((byte) 0);
    }
    
    public Message(ByteBuffer buffer) {
        this.buffer = buffer;
        this.size = buffer.limit();
        this.magic = buffer.get(MagicOffset);
        this.payloadSize = size - headerSize(magic);
        this.attributes = buffer.get(AttributeOffset);
        
        this.checksum = Utils.getUnsignedInt(buffer, crcOffset(magic));
        this.serializedSize = 4 /* int size*/ + buffer.limit();
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
    
    public static int headerSize(byte magic) {
        return payloadOffset(magic);
    }
    
    public static int payloadOffset(byte magic) {
        return crcOffset(magic) + CrcLength;
    }
    
    public static int crcOffset(byte magic) {
        if (magic == MagicVersion1) {
            return MagicOffset + MagicLength;
        }
        if (magic == MagicVersion2) {
            return AttributeOffset + AttributeLength;
        } else {
            throw new UnknownMagicByteException("Magic byte value of %d is unknown".format(String.valueOf(magic)));
        }
    }
    
    public CompressionCodec compressionCodec() {
        if (magic == 0) {
            return new NoCompressionCodec();
        } else if (magic == 1) {
            return CompressionCodec.getCompressionCodec(buffer.get(AttributeOffset) & CompressionCodeMask);
        } else {
            throw new RuntimeException("Invalid magic byte " + magic);
        }
    }
    
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
    
    public void serializeTo(ByteBuffer serBuffer) {
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
    
    public static int getMinHeaderSize() {
        return MinHeaderSize;
    }
    
    
    public int hashCode() {
        return buffer.hashCode();
    }
}

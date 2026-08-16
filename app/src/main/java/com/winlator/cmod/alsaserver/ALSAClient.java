package com.winlator.cmod.alsaserver;

import com.winlator.cmod.sysvshm.SysVSharedMemory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ALSAClient {
    public enum DataType {
        U8(1), S16LE(2), S16BE(2), FLOATLE(4), FLOATBE(4);
        public final byte byteCount;

        DataType(int byteCount) {
            this.byteCount = (byte)byteCount;
        }
    }
    private DataType dataType = DataType.U8;
    private byte channelCount = 2;
    private int sampleRate = 0;
    private int position;
    private int bufferSize;
    private int frameBytes;
    private ByteBuffer sharedBuffer;
    private volatile boolean playing = false;
    private volatile long streamPtr = 0;

    // Guards streamPtr + playing so release()/prepare()/start()/stop()/pause()/
    // drain()/writeDataToStream() can never race with each other. The native
    // crash ("Pure virtual function called!" inside aaudio::AudioStream::close_l())
    // was caused by two threads calling release() -> close(streamPtr) on the same
    // pointer at the same time (e.g. XConnectorEpoll.killConnection() firing from
    // both the epoll thread and a client's own pollThread). Once one thread's
    // close() call is in flight, the AAudioStream object is being torn down; a
    // second concurrent close() on the same pointer calls a virtual method on a
    // partially-destroyed vtable -> abort.
    private final Object lock = new Object();

    static {
        System.loadLibrary("winlator");
    }

    public void release() {
        synchronized (lock) {
            if (sharedBuffer != null) {
                SysVSharedMemory.unmapSHMSegment(sharedBuffer, sharedBuffer.capacity());
                sharedBuffer = null;
            }

            long ptr = streamPtr;
            // Clear state before touching native so a concurrent caller that
            // is blocked on this same lock sees streamPtr == 0 once it gets in,
            // and never issues a second stop()/close() on the same pointer.
            streamPtr = 0;
            playing = false;

            if (ptr > 0) {
                stop(ptr);
                close(ptr);
            }
        }
    }

    public void prepare() {
        synchronized (lock) {
            position = 0;
            frameBytes = channelCount * dataType.byteCount;
        }

        release();

        synchronized (lock) {
            if (!isValidBufferSize()) return;

            long ptr = create(dataType.ordinal(), channelCount, sampleRate, bufferSize);
            streamPtr = ptr;
            if (ptr > 0) start();
        }
    }

    public void start() {
        synchronized (lock) {
            if (streamPtr > 0 && !playing) {
                start(streamPtr);
                playing = true;
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            if (streamPtr > 0 && playing) {
                stop(streamPtr);
                playing = false;
            }
        }
    }

    public void pause() {
        synchronized (lock) {
            if (streamPtr > 0) {
                pause(streamPtr);
                playing = false;
            }
        }
    }

    public void drain() {
        synchronized (lock) {
            if (streamPtr > 0) flush(streamPtr);
        }
    }

    public void writeDataToStream(ByteBuffer data) {
        if (dataType == DataType.S16LE || dataType == DataType.FLOATLE) {
            data.order(ByteOrder.LITTLE_ENDIAN);
        }
        else if (dataType == DataType.S16BE || dataType == DataType.FLOATBE) {
            data.order(ByteOrder.BIG_ENDIAN);
        }

        synchronized (lock) {
            if (playing && streamPtr > 0) {
                int numFrames = data.limit() / frameBytes;
                int framesWritten = write(streamPtr, data, numFrames);
                if (framesWritten > 0) position += framesWritten;
                data.rewind();
            }
        }
    }

    public int pointer() {
        return position;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = (byte)channelCount;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public ByteBuffer getSharedBuffer() {
        return sharedBuffer;
    }

    public void setSharedBuffer(ByteBuffer sharedBuffer) {
        this.sharedBuffer = sharedBuffer;
    }

    public DataType getDataType() {
        return dataType;
    }

    public byte getChannelCount() {
        return channelCount;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getBufferSizeInBytes() {
        return bufferSize * frameBytes;
    }

    private boolean isValidBufferSize() {
        return (getBufferSizeInBytes() % frameBytes == 0) && bufferSize > 0;
    }

    public int computeLatencyMillis() {
        return (int)(((float)bufferSize / sampleRate) * 1000);
    }

    private native long create(int format, byte channelCount, int sampleRate, int bufferSize);

    private native int write(long streamPtr, ByteBuffer buffer, int numFrames);

    private native void start(long streamPtr);

    private native void stop(long streamPtr);

    private native void pause(long streamPtr);

    private native void flush(long streamPtr);

    private native void close(long streamPtr);
}

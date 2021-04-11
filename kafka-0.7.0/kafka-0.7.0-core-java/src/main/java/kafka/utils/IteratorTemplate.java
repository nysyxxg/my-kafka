package kafka.utils;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;


enum State {
    DONE, READY, NOT_READY, FAILED;
}


public abstract class IteratorTemplate<T> implements Iterator<T> {
    
    private State state = State.NOT_READY;
    private T nextItem = null;
    
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        state = State.NOT_READY;
        if (nextItem != null) {
            return nextItem;
        } else if (nextItem == null) {
            throw new IllegalStateException("Expected item but none found.");
        }
        return null;
    }
    
    public boolean hasNext() {
        if (state == State.FAILED) {
            throw new IllegalStateException("Iterator is in failed state");
        }
        if (state == State.DONE) {
            return false;
        } else if (state == State.READY) {
            return true;
        } else {
            maybeComputeNext();
        }
        return false;
    }
    
    protected abstract T makeNext() throws Throwable;
    
    Boolean maybeComputeNext() {
        state = State.FAILED;
        try {
            nextItem = makeNext();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        if (state == State.DONE) {
            return false;
        } else {
            state = State.READY;
            return true;
        }
    }
    
    protected T allDone() {
        state = State.DONE;
        return null;
    }
    
    public void remove() {
        throw new UnsupportedOperationException("Removal not supported");
    }
    
}


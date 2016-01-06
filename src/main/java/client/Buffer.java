package client;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Buffer {
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition update = lock.newCondition();

	private final long time;
	private final TimeUnit unit;

	private Object o;

	public Buffer(final long time, final TimeUnit unit) {
		this.time = time;
		this.unit = unit;
	}

	public void put(Object o) throws InterruptedException {
		lock.lock();
		try {
			this.o = o;
			update.signal();
		} finally {
			lock.unlock();
		}
	}

	public Object take() throws InterruptedException {
		lock.lock();
		try {
			return update.await(time, unit) ? o : null;
		} finally {
			lock.unlock();
		}
	}
}
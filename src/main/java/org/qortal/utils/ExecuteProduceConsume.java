package org.qortal.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class ExecuteProduceConsume
 *
 * @ThreadSafe
 */
public abstract class ExecuteProduceConsume implements Runnable {

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class StatsSnapshot {
		public int activeThreadCount = 0;
		public int greatestActiveThreadCount = 0;
		public int consumerCount = 0;
		public int tasksProduced = 0;
		public int tasksConsumed = 0;
		public int spawnFailures = 0;

		public StatsSnapshot() {
		}
	}

	private final String className;
	private final Logger logger;

	protected ExecutorService executor;

	// These are atomic to make this class thread-safe

	private AtomicInteger activeThreadCount = new AtomicInteger(0);
	private AtomicInteger greatestActiveThreadCount = new AtomicInteger(0);
	private AtomicInteger consumerCount = new AtomicInteger(0);
	private AtomicInteger tasksProduced = new AtomicInteger(0);
	private AtomicInteger tasksConsumed = new AtomicInteger(0);
	private AtomicInteger spawnFailures = new AtomicInteger(0);

	/** Whether a new thread has already been spawned and is waiting to start. Used to prevent spawning multiple new threads. */
	private AtomicBoolean hasThreadPending = new AtomicBoolean(false);

	public ExecuteProduceConsume(ExecutorService executor) {
		this.className = this.getClass().getSimpleName();
		this.logger = LogManager.getLogger(this.getClass());

		this.executor = executor;

		this.logger.info("Created Thread-Safe ExecuteProduceConsume");
	}

	public ExecuteProduceConsume() {
		this(Executors.newCachedThreadPool());
	}

	public void start() {
		this.executor.execute(this);
	}

	public void shutdown() {
		this.executor.shutdownNow();
	}

	public boolean shutdown(long timeout) throws InterruptedException {
		this.executor.shutdownNow();
		return this.executor.awaitTermination(timeout, TimeUnit.MILLISECONDS);
	}

	public StatsSnapshot getStatsSnapshot() {
		StatsSnapshot snapshot = new StatsSnapshot();

		snapshot.activeThreadCount = this.activeThreadCount.get();
		snapshot.greatestActiveThreadCount = this.greatestActiveThreadCount.get();
		snapshot.consumerCount = this.consumerCount.get();
		snapshot.tasksProduced = this.tasksProduced.get();
		snapshot.tasksConsumed = this.tasksConsumed.get();
		snapshot.spawnFailures = this.spawnFailures.get();

		return snapshot;
	}

	protected void onSpawnFailure() {
		/* Allow override in subclasses */
	}

	/**
	 * Returns a Task to be performed, possibly blocking.
	 * 
	 * @param canBlock
	 * @return task to be performed, or null if no task pending.
	 * @throws InterruptedException
	 *
	 * @ThreadSafe
	 */
	protected abstract Task produceTask(boolean canBlock) throws InterruptedException;

	public interface Task {
		String getName();
		void perform() throws InterruptedException;
	}

	@Override
	public void run() {
		Thread.currentThread().setName(this.className + "-" + Thread.currentThread().getId());

		this.activeThreadCount.incrementAndGet();
		if (this.activeThreadCount.get() > this.greatestActiveThreadCount.get())
			this.greatestActiveThreadCount.set( this.activeThreadCount.get() );

			// Defer clearing hasThreadPending to prevent unnecessary threads waiting to produce...
			boolean wasThreadPending = this.hasThreadPending.get();

		try {
			while (!Thread.currentThread().isInterrupted()) {
				Task task = null;

				if (wasThreadPending) {
					// Clear thread-pending flag now that we about to produce.
					this.hasThreadPending.set( false );
					wasThreadPending = false;
				}

				// If we're the only non-consuming thread - producer can afford to block this round
				boolean canBlock = this.activeThreadCount.get() - this.consumerCount.get() <= 1;

				try {
					task = produceTask(canBlock);
				} catch (InterruptedException e) {
					// We're in shutdown situation so exit
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					this.logger.warn(() -> String.format("[%d] exception while trying to produce task", Thread.currentThread().getId()), e);
				}

				if (task == null) {
					// If we have an excess of non-consuming threads then we can exit
					if (this.activeThreadCount.get() - this.consumerCount.get() > 1) {
						this.activeThreadCount.decrementAndGet();

						return;
					}

					continue;
				}
				// We have a task

				this.tasksProduced.incrementAndGet();
				this.consumerCount.incrementAndGet();

				// If we have no thread pending and no excess of threads then we should spawn a fresh thread
				if (!this.hasThreadPending.get() && this.activeThreadCount.get() == this.consumerCount.get()) {

					this.hasThreadPending.set( true );

					try {
						this.executor.execute(this); // Same object, different thread
					} catch (RejectedExecutionException e) {
						this.spawnFailures.decrementAndGet();
						this.hasThreadPending.set( false );

						this.onSpawnFailure();
					}
				}

				try {
					task.perform(); // This can block for a while
				} catch (InterruptedException e) {
					// We're in shutdown situation so exit
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					this.logger.warn(() -> String.format("[%d] exception while consuming task", Thread.currentThread().getId()), e);
				}

				this.tasksConsumed.incrementAndGet();
				this.consumerCount.decrementAndGet();
			}
		} finally {
			Thread.currentThread().setName(this.className);
		}
	}
}
package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Arrays;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param	transferPriority	<tt>true</tt> if this queue should
	 *					transfer priority from waiting threads
	 *					to the owning thread.
	 * @return	a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum &&
				priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
		System.out.println("Set the priority of " + thread.getName() + " to " + priority);
		thread.yield();
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority+1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority-1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;    

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param	thread	the thread whose scheduling state to return.
	 * @return	the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
			waitQueue = new java.util.PriorityQueue();
		}

		public void log (String message) {
			if (this == KThread.readyQueue) {
				System.out.println("--- cpu output ommitted ---");
			} else {
				System.out.println(message);
			}
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			time++;
			log (thread.getName() + " called waitForAccess; the time on the queue is " + time);
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			log (thread.getName() +  " called acquire()");
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			if (waitQueue.isEmpty()) {
				return null;
			}
			System.out.println ("We should run " + waitQueue.peek() + " next.");
			KThread res = waitQueue.poll().thread;
			print();
			return res;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected ThreadState pickNextThread() {
			return waitQueue.peek();
		}

		public void print() {
		//	Lib.assertTrue(Machine.interrupt().disabled());
			ThreadState[] ts = new ThreadState[waitQueue.size()];
			waitQueue.toArray(ts);
			Arrays.sort (ts);
			for (ThreadState t : ts) {
				log ("\t" + t);
			}
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		public ThreadState owner;
		public java.util.PriorityQueue<ThreadState> waitQueue;
		private int time;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue
	 * it's waiting for, if any.
	 *
	 * @see	nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState implements Comparable<ThreadState> {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param	thread	the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;

			setPriority(priorityDefault);
		}

		public int compareTo (ThreadState other) {
		//	System.out.println("Comparing! priorities: " + priority + " vs. " + other.priority + "; times = " + entryTime + " vs. " + other.entryTime);
			int res = 0;
			if (priority == other.priority) {
				// choose the one with earlier start time
				res = new Integer(entryTime).compareTo (new Integer(other.entryTime));
			} else {
				res = -new Integer(priority).compareTo (new Integer(other.priority));
			}
		//	System.out.println("result = " + res);
			return res;
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return	the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return	the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			if (!q.transferPriority || q.waitQueue.isEmpty()) return priority;
			return Math.max (priority, q.waitQueue.peek().priority);
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			this.priority = priority;
			if (q != null) {
				q.waitQueue.remove (this);
				q.waitQueue.add (this);
			}
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the
		 * resource guarded by <tt>waitQueue</tt>. This method is only called
		 * if the associated thread cannot immediately obtain access.
		 *
		 * @param	waitQueue	the queue that the associated thread is
		 *				now waiting on.
		 *
		 * @see	nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			this.q = waitQueue;
			entryTime = waitQueue.time;
			waitQueue.waitQueue.add (this);
			waitQueue.log("Just added myself to the queue. The queue is now: ");
			waitQueue.print();
			
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see	nachos.threads.ThreadQueue#acquire
		 * @see	nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			// remember which thread called this
			waitQueue.owner = this;
			this.q = waitQueue;
		}	

		public String toString () {
			return thread.getName() + "; priority = " + priority + "; entryTime = " + entryTime;
		}

		/** The thread with which this object is associated. */	   
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		protected int entryTime;
		protected PriorityQueue q;
	}

	static class SchedThing implements Runnable {

		public int n;
		public Lock a;

		public SchedThing (Lock a, int n) {
			this.a = a;
			this.n = n;
		}

		public void run () {
			double q = 5;
			for (int i=0; i < 2; i++) {
				System.out.println("Hi from " + n + "; i = " + i + "; q = " + q);
				a.acquire();
				System.out.println("I have acquired the lock! I am thread " + n);

				for (int j=0; j < 20; j++) {
					Machine.interrupt().disable();
					Machine.interrupt().enable();
				}

				a.release();
				System.out.println("Thread " + n + " released the lock");
				boolean intStatus = Machine.interrupt().disable();
				ThreadedKernel.scheduler.setPriority (KThread.currentThread(), 2);
				Machine.interrupt().restore(intStatus);

			}
		}
	}

	public static void selfTest () {
		Lock a = new Lock();
		KThread k = new KThread(new SchedThing(a, 1));
		KThread m = new KThread(new SchedThing(a, 2));
		KThread n = new KThread(new SchedThing(a, 3));
		k.setName ("thread 1");
		m.setName ("thread 2");
		n.setName ("thread 3");
		boolean intStatus = Machine.interrupt().disable();
		ThreadedKernel.scheduler.setPriority (k, 4);
		ThreadedKernel.scheduler.setPriority (m, 7);
		ThreadedKernel.scheduler.setPriority (n, 5);
		Machine.interrupt().restore(intStatus);
		k.fork();
		n.fork();
		m.fork();

		k.join();
		m.join();
		n.join();

	}

}

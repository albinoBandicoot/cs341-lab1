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
			/*
			if (KThread.readyQueue != null && ((PriorityQueue) KThread.readyQueue).owner != null) {
				System.out.println("the owner of the cpu queue is " + ((PriorityQueue) KThread.readyQueue).owner.thread.getName());
			}
			*/
			if (this == KThread.readyQueue) {
				System.out.println("--- cpu: " + message);
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
			System.out.println ("We are going to decide what to run.");
			((PriorityQueue) KThread.readyQueue).print();
			ThreadState[] tsa = new ThreadState[waitQueue.size()];
			waitQueue.toArray (tsa);
			waitQueue.clear();
			for (ThreadState t : tsa) {
				waitQueue.add (t);
			}
			System.out.println ("We should run " + waitQueue.peek() + " next.");
			KThread res = waitQueue.poll().thread;

			owner.donation_priority = owner.getEffectivePriority ();
			if (owner.q.waitQueue.remove (owner)) {
				owner.q.waitQueue.add (owner);
			}

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
			thread.schedulingState = this;

			setPriority(priorityDefault);
		}

		public int compareTo (ThreadState other) {
		//	System.out.println("Comparing! priorities: " + priority + " vs. " + other.priority + "; times = " + entryTime + " vs. " + other.entryTime);
			int res = 0;
			if (donation_priority == other.donation_priority) {
				// choose the one with earlier start time
				res = new Integer(entryTime).compareTo (new Integer(other.entryTime));
			} else {
				res = -new Integer(donation_priority).compareTo (new Integer(other.donation_priority));
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
			//System.out.println("I am thread " + thread.getName() + "; my priority is " + priority + " and my old donation_priority = " + donation_priority);
			if (!q.transferPriority || q.waitQueue.isEmpty()) return priority;
			//System.out.println("in get effective : the queue's max isthe queue's max is  " + q.waitQueue.peek().donation_priority);
			return Math.max (priority, q.waitQueue.peek().donation_priority);
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
			this.donation_priority = priority;	// will get overwritten below if necessary
			if (q != null) {
				if (q.waitQueue.remove (this)) {
					q.waitQueue.add (this);
					q.owner.donation_priority = q.owner.getEffectivePriority();
				}
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
			q.waitQueue.add (this);
			q.owner.donation_priority = q.owner.getEffectivePriority();
			if (q.owner.q.waitQueue.remove (q.owner)) {
				q.owner.q.waitQueue.add (q.owner);
			}

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
			return thread.getName() + "; priority = " + priority + "; donation priority = " + donation_priority + "; getEffective() = " + getEffectivePriority() + "; entryTime = " + entryTime;
		}

		/** The thread with which this object is associated. */	   
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		protected int donation_priority;
		protected int entryTime;
		protected PriorityQueue q;
	}

	static class SchedThing implements Runnable {

		public int n;
		public Lock a;
		public KThread[] threads;

		public SchedThing (Lock a, KThread[] th, int n) {
			this.a = a;
			this.n = n;
			this.threads = th;
		}

		public void run () {
			double q = 5;
			for (int i=0; i < 2; i++) {
				System.out.println("Hi I'm thread " + n + " i = " + i);
				/*
				if (n == 2) {
					System.out.println("Thread 2 is JOINING THREAD 0!!!");
					threads[0].join();
				}
				*/
				a.acquire();
				System.out.println("I have acquired the lock! I am thread " + n);
				for (int j=0; j < 80; j++) {
					Machine.interrupt().disable();
					if (j==0) {
						ThreadedKernel.scheduler.setPriority (KThread.currentThread(), 1);
					}

					Machine.interrupt().enable();
				}

				a.release();
				System.out.println("Thread " + n + " released the lock");
//				if (n == 1) {
					boolean intStatus = Machine.interrupt().disable();
//					ThreadedKernel.scheduler.setPriority (KThread.currentThread(), 4);
					Machine.interrupt().restore(intStatus);
//				}

			}
		}
	}

	public static void selfTest () {
		Lock a = new Lock();
		int nthreads = 3;
		KThread[] t = new KThread[nthreads];
		boolean intStatus = Machine.interrupt().disable();
		for (int i=0; i < nthreads; i++) {
			t[i] = new KThread(new SchedThing(a, t, i));
			t[i].setName ("thread " + i);
			ThreadedKernel.scheduler.setPriority (t[i], i+3);
		}
		Machine.interrupt().restore(intStatus);
		for (int i=0; i < nthreads; i++) {
			t[i].fork();
		}

		for (int i=1; i < nthreads; i++) {
			t[i].join();
		}

	}

}

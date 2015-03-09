package nachos.threads;

import java.util.*;
import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {

	public Lock lock;
	public Condition A, B, C, D;
	private int message;
	private boolean speakerWaiting, listenerWaiting;
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
		lock = new Lock();
		A = new Condition (lock);
		B = new Condition (lock);
		C = new Condition (lock);
		D = new Condition (lock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
		lock.acquire();
		while (speakerWaiting) {
			B.sleep();
		}
		System.out.println("I have passed the first gate in speak()");
		message = word;
		while (!listenerWaiting) {
			System.out.println("Will sleep on A");
			speakerWaiting = true;
			A.sleep();
		}
		System.out.println("Message has been set to " + message);
		speakerWaiting = false;
		C.wake();
		B.wake();
		lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
		int val = 0;
		lock.acquire();
		while (listenerWaiting) {
			D.sleep();
		}
		System.out.println("I have passed the first gate in listen()");
		while (!speakerWaiting) {
			System.out.println("Will sleep on C");
			listenerWaiting = true;
			C.sleep();
		}
		System.out.println ("I have passed the second gate in listen()");
		val = message;
		listenerWaiting = false;	// placement?? - has troubles.
		A.wake();
		D.wake();
		lock.release();
		System.out.println("I am about to return " + val + " from listen");
		return val;
    }

	static class CommThing implements Runnable {

		public int n;
		public Communicator c;

		public CommThing (Communicator c, int n) {
			this.n = n;
			this.c = c;
		}

		public void run () {
			System.out.println("Thread " + n + " has started");
			if (n % 2 == 0) {
				int x = c.listen();
				System.out.println("I am thread " + n + " and received " + x);
				c.speak (n*5);
				System.out.println("Thread " + n + " has spoken");
			} else {
				c.speak (n*5);
				System.out.println("Thread " + n + " has spoken");
				int x = c.listen();
				System.out.println("I am thread " + n + " and received " + x);
			}
			
		}
	}

	public static void selfTest () {
		System.out.println("Hi.");
		Communicator c = new Communicator();
		KThread k = new KThread(new CommThing(c, 1));
		KThread m = new KThread(new CommThing(c, 2));
		k.fork();
		m.fork();
		k.join();
		m.join();
	}


}

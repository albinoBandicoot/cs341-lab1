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
	private boolean speakerWaiting, listenerWaiting, speakerEntryOK, listenerEntryOK;
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
		lock = new Lock();
		A = new Condition (lock);
		B = new Condition (lock);
		C = new Condition (lock);
		D = new Condition (lock);
		speakerEntryOK = true;
		listenerEntryOK = true;
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
		log ("Called speak (" + word + ")");
		lock.acquire();
		while (!speakerEntryOK) {
			log("Sleeping on B");
			B.sleep();
		}
		speakerEntryOK = false;

		speakerWaiting = true;
		message = word;
		C.wake();
		log("speakerWaiting = true, called C.wake()");
		while (!listenerWaiting) {
			log("Will sleep on A");
			A.sleep();
		}
		log("Setting listenerWaiting to false, waking B, and returning");
		listenerWaiting = false;
		speakerEntryOK = true;
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
		log ("Called listen");
		int val = 0;
		lock.acquire();
		while (!listenerEntryOK) {
			log("Will sleep on D");
			D.sleep();
		}
		listenerEntryOK = false;

		listenerWaiting = true;
		A.wake();
		log("listenerWaiting = true, listenerEntryOK = false, called A.wake()");
		while (!speakerWaiting) {
			log("Will sleep on C");
			C.sleep();
		}
		log("Setting speakerWaiting to false, waking D, and returning " + message);
		speakerWaiting = false;
		listenerEntryOK = true;
		D.wake();

		lock.release();
		return message;
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

	public static void log (String message) {
		System.out.println(KThread.currentThread().getName() + ": " + message);
	}

	public static void selfTest () {
		System.out.println("Hi.");
		Communicator c = new Communicator();
		int n = 4;
		KThread[] thr = new KThread[n];
		for (int i=0; i < n; i++) {
			thr[i] = new KThread (new CommThing (c, i+1));
			thr[i].setName ("Thread " + (i+1));
			thr[i].fork();
		}
		for (int i=0; i < n; i++) {
			thr[i].join();
		}
	}


}

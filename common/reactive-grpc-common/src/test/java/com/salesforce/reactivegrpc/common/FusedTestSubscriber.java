package com.salesforce.reactivegrpc.common;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.rxjava3.core.FlowableSubscriber;
import io.reactivex.rxjava3.internal.fuseable.QueueSubscription;
import io.reactivex.rxjava3.internal.subscriptions.SubscriptionHelper;
import io.reactivex.rxjava3.internal.util.ExceptionHelper;
import io.reactivex.rxjava3.internal.util.VolatileSizeArrayList;
import io.reactivex.rxjava3.subscribers.TestSubscriber;

public class FusedTestSubscriber<T> extends TestSubscriber<T> {

	/**
	 * The latch that indicates an onError or onComplete has been called.
	 */
	private final CountDownLatch done;
	/**
	 * The list of values received.
	 */
	private final List<T> values;
	/**
	 * The list of errors received.
	 */
	private final List<Throwable> errors;
	/**
	 * The number of completions.
	 */
	protected long completions;
	/**
	 * The last thread seen by the observer.
	 */
	protected Thread lastThread;

	protected boolean checkSubscriptionOnce;

	protected int initialFusionMode;

	protected int establishedFusionMode;

	/**
	 * The optional tag associated with this test consumer.
	 *
	 * @since 2.0.7
	 */
	protected CharSequence tag;

	/**
	 * Indicates that one of the awaitX method has timed out.
	 *
	 * @since 2.0.7
	 */
	protected boolean timeout;

	/** The actual subscriber to forward events to. */
	private final Subscriber<? super T> downstream;

	/** Makes sure the incoming Subscriptions get cancelled immediately. */
	private volatile boolean cancelled;

	/** Holds the current subscription if any. */
	private final AtomicReference<Subscription> upstream;

	/** Holds the requested amount until a subscription arrives. */
	private final AtomicLong missedRequested;

	private QueueSubscription<T> qs;

	/**
	 * Creates a TestSubscriber with Long.MAX_VALUE initial request.
	 * @param <T> the value type
	 * @return the new TestSubscriber instance.
	 */
	public static <T> FusedTestSubscriber<T> create() {
		return new FusedTestSubscriber<T>();
	}

	/**
	 * Creates a TestSubscriber with the given initial request.
	 * @param <T> the value type
	 * @param initialRequested the initial requested amount
	 * @return the new TestSubscriber instance.
	 */
	public static <T> FusedTestSubscriber<T> create(long initialRequested) {
		return new FusedTestSubscriber<T>(initialRequested);
	}

	/**
	 * Constructs a forwarding TestSubscriber.
	 * @param <T> the value type received
	 * @param delegate the actual Subscriber to forward events to
	 * @return the new TestObserver instance
	 */
	public static <T> FusedTestSubscriber<T> create(Subscriber<? super T> delegate) {
		return new FusedTestSubscriber<T>(delegate);
	}

	/**
	 * Constructs a non-forwarding TestSubscriber with an initial request value of Long.MAX_VALUE.
	 */
	public FusedTestSubscriber() {
		this(EmptySubscriber.INSTANCE, Long.MAX_VALUE);
	}

	/**
	 * Constructs a non-forwarding TestSubscriber with the specified initial request value.
	 * <p>The TestSubscriber doesn't validate the initialRequest value so one can
	 * test sources with invalid values as well.
	 * @param initialRequest the initial request value
	 */
	public FusedTestSubscriber(long initialRequest) {
		this(EmptySubscriber.INSTANCE, initialRequest);
	}

	/**
	 * Constructs a forwarding TestSubscriber but leaves the requesting to the wrapped subscriber.
	 * @param downstream the actual Subscriber to forward events to
	 */
	public FusedTestSubscriber(Subscriber<? super T> downstream) {
		this(downstream, Long.MAX_VALUE);
	}

	/**
	 * Constructs a forwarding TestSubscriber with the specified initial request value.
	 * <p>The TestSubscriber doesn't validate the initialRequest value so one can
	 * test sources with invalid values as well.
	 * @param actual the actual Subscriber to forward events to
	 * @param initialRequest the initial request value
	 */
	public FusedTestSubscriber(Subscriber<? super T> actual, long initialRequest) {
		super();
		if (initialRequest < 0) {
			throw new IllegalArgumentException("Negative initial request not allowed");
		}
		this.values = new VolatileSizeArrayList<>();
		this.errors = new VolatileSizeArrayList<>();
		this.done = new CountDownLatch(1);
		this.downstream = actual;
		this.upstream = new AtomicReference<>();
		this.missedRequested = new AtomicLong(initialRequest);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onSubscribe(Subscription s) {
		lastThread = Thread.currentThread();

		if (s == null) {
			errors.add(new NullPointerException("onSubscribe received a null Subscription"));
			return;
		}
		if (!upstream.compareAndSet(null, s)) {
			s.cancel();
			if (upstream.get() != SubscriptionHelper.CANCELLED) {
				errors.add(new IllegalStateException("onSubscribe received multiple subscriptions: " + s));
			}
			return;
		}

		if (initialFusionMode != 0) {
			if (s instanceof QueueSubscription) {
				qs = (QueueSubscription<T>)s;

				int m = qs.requestFusion(initialFusionMode);
				establishedFusionMode = m;

				if (m == QueueSubscription.SYNC) {
					checkSubscriptionOnce = true;
					lastThread = Thread.currentThread();
					try {
						T t;
						while ((t = qs.poll()) != null) {
							values.add(t);
						}
						completions++;
					} catch (Throwable ex) {
						// Exceptions.throwIfFatal(e); TODO add fatal exceptions?
						errors.add(ex);
					}
					return;
				}
			}
		}

		downstream.onSubscribe(s);

		long mr = missedRequested.getAndSet(0L);
		if (mr != 0L) {
			s.request(mr);
		}

		onStart();
	}

	/**
	 * Called after the onSubscribe is called and handled.
	 */
	protected void onStart() {

	}

	public boolean awaitTerminalEvent(long duration, TimeUnit unit) {
		try {
			return await(duration, unit);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	@Override
	public void onNext(T t) {
		if (!checkSubscriptionOnce) {
			checkSubscriptionOnce = true;
			if (upstream.get() == null) {
				errors.add(new IllegalStateException("onSubscribe not called in proper order"));
			}
		}
		lastThread = Thread.currentThread();

		if (establishedFusionMode == QueueSubscription.ASYNC) {
			try {
				while ((t = qs.poll()) != null) {
					values.add(t);
				}
			} catch (Throwable ex) {
				// Exceptions.throwIfFatal(e); TODO add fatal exceptions?
				errors.add(ex);
				qs.cancel();
			}
			return;
		}

		values.add(t);

		if (t == null) {
			errors.add(new NullPointerException("onNext received a null value"));
		}

		downstream.onNext(t);
	}

	/**
	 * Sets the initial fusion mode if the upstream supports fusion.
	 * <p>Package-private: avoid leaking the now internal fusion properties into the public API.
	 * Use SubscriberFusion to work with such tests.
	 * @param mode the mode to establish, see the {@link QueueSubscription} constants
	 * @return this
	 */
	final FusedTestSubscriber<T> setInitialFusionMode(int mode) {
		this.initialFusionMode = mode;
		return this;
	}

	/**
	 * Asserts that the given fusion mode has been established
	 * <p>Package-private: avoid leaking the now internal fusion properties into the public API.
	 * Use SubscriberFusion to work with such tests.
	 * @param mode the expected mode
	 * @return this
	 */
	final FusedTestSubscriber<T> assertFusionMode(int mode) {
		int m = establishedFusionMode;
		if (m != mode) {
			if (qs != null) {
				throw new AssertionError("Fusion mode different. Expected: " + fusionModeToString(mode)
						+ ", actual: " + fusionModeToString(m));
			} else {
				throw fail("Upstream is not fuseable");
			}
		}
		return this;
	}

	static String fusionModeToString(int mode) {
		switch (mode) {
			case QueueSubscription.NONE : return "NONE";
			case QueueSubscription.SYNC : return "SYNC";
			case QueueSubscription.ASYNC : return "ASYNC";
			default: return "Unknown(" + mode + ")";
		}
	}

	/**
	 * Assert that the upstream is a fuseable source.
	 * <p>Package-private: avoid leaking the now internal fusion properties into the public API.
	 * Use SubscriberFusion to work with such tests.
	 * @return this
	 */
	final FusedTestSubscriber<T> assertFuseable() {
		if (qs == null) {
			throw new AssertionError("Upstream is not fuseable.");
		}
		return this;
	}

	/**
	 * Assert that the upstream is not a fuseable source.
	 * <p>Package-private: avoid leaking the now internal fusion properties into the public API.
	 * Use SubscriberFusion to work with such tests.
	 * @return this
	 */
	final FusedTestSubscriber<T> assertNotFuseable() {
		if (qs != null) {
			throw new AssertionError("Upstream is fuseable.");
		}
		return this;
	}

	/**
	 * Run a check consumer with this TestSubscriber instance.
	 * @param check the check consumer to run
	 * @return this
	 */
	public final FusedTestSubscriber<T> assertOf(Consumer<? super FusedTestSubscriber<T>> check) {
		try {
			check.accept(this);
		} catch (Throwable ex) {
			throw ExceptionHelper.wrapOrThrow(ex);
		}
		return this;
	}

	/**
	 * A subscriber that ignores all events and does not report errors.
	 */
	enum EmptySubscriber implements FlowableSubscriber<Object> {
		INSTANCE;

		@Override
		public void onSubscribe(Subscription s) {
		}

		@Override
		public void onNext(Object t) {
		}

		@Override
		public void onError(Throwable t) {
		}

		@Override
		public void onComplete() {
		}
	}
}

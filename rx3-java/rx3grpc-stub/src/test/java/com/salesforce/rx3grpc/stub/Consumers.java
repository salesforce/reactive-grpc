package com.salesforce.rx3grpc.stub;

import java.io.Closeable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.LongConsumer;

public final class Consumers {

	private Consumers() {
		// prevent instantiation
	}

	public static LongConsumer addLongTo(final List<Long> list) {
		return new LongConsumer() {

			@Override
			public void accept(long t) throws Exception {
				list.add(t);
			}

		};
	}

	@SuppressWarnings("unchecked")
	public static <T extends Closeable> Consumer<T> close() {
		return (Consumer<T>) CloseHolder.INSTANCE;
	}

	private static final class CloseHolder {
		final static Consumer<Closeable> INSTANCE = new Consumer<Closeable>() {
			@Override
			public void accept(Closeable t) throws Exception {
				t.close();
			}

		};
	}

	public static Consumer<Object> increment(final AtomicInteger value) {
		return new Consumer<Object>() {
			@Override
			public void accept(Object t) throws Exception {
				value.incrementAndGet();
			}
		};
	}

	public static Consumer<Throwable> printStackTrace() {
		// TODO make holder
		return new Consumer<Throwable>() {
			@Override
			public void accept(Throwable e) throws Exception {
				e.printStackTrace();
			}
		};
	}

	@SuppressWarnings("unchecked")
	public static <T> Consumer<T> doNothing() {
		return (Consumer<T>) DoNothingHolder.INSTANCE;
	}

	private static final class DoNothingHolder {
		static final Consumer<Object> INSTANCE = new Consumer<Object>() {

			@Override
			public void accept(Object t) throws Exception {
				// do nothing
			}
		};
	}

	public static <T> Consumer<T> set(final AtomicReference<T> value) {
		return new Consumer<T>() {

			@Override
			public void accept(T t) throws Exception {
				value.set(t);
			}
		};
	}

	public static Consumer<Integer> set(final AtomicInteger value) {
		return new Consumer<Integer>() {
			@Override
			public void accept(Integer t) throws Throwable {
				value.set(t);
			}
		};
	}

	public static Consumer<Object> decrement(final AtomicInteger value) {
		return new Consumer<Object>() {
			@Override
			public void accept(Object t) throws Throwable {
				value.decrementAndGet();
			}
		};
	}

	@SuppressWarnings("unchecked")
	public static <T> Consumer<T> setToTrue(final AtomicBoolean value) {
		return new Consumer<T>() {
			@Override
			public void accept(T t) throws Throwable {
				value.set(true);
			}
		};
	}

	public static <T> Consumer<T> addTo(final List<T> list) {
		return new Consumer<T>() {
			@Override
			public void accept(T t) throws Throwable {
				list.add(t);
			}
		};
	}

	@SuppressWarnings("unchecked")
	public static <T> Consumer<T> println() {
		return (Consumer<T>) PrintlnHolder.INSTANCE;
	}

	private static final class PrintlnHolder {
		static final Consumer<Object> INSTANCE = new Consumer<Object>() {
			@Override
			public void accept(Object t) throws Throwable {
				System.out.println(t);
			}
		};
	}

	public static Consumer<byte[]> assertBytesEquals(final byte[] expected) {
		// TODO make holder
		return new Consumer<byte[]>() {
			@Override
			public void accept(byte[] array) throws Throwable {
				if (!Arrays.equals(expected, array)) {
					// TODO use custom exception
					throw new Exception("arrays not equal: expected=" + Arrays.toString(expected) + ",actual=" + Arrays.toString(array));
				}
			}
		};
	}

	public static LongConsumer printLong(final String prefix) {
		return new LongConsumer() {
			@Override
			public void accept(long t) throws Throwable {
				System.out.println(prefix + t);
			}
		};
	}
}

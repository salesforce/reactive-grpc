package com.salesforce.rx3grpc.stub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.functions.BiFunction;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.functions.Predicate;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Provides builder for the {@link Function} parameter of
 * {@link Flowable#retryWhen(Function)}. For example:
 * 
 * <pre>
 * o.retryWhen(RetryWhen.maxRetries(4).delay(10, TimeUnit.SECONDS).action(log).build());
 * </pre>
 * 
 * <p>
 * or
 * </p>
 * 
 * <pre>
 * o.retryWhen(RetryWhen.exponentialBackoff(100, TimeUnit.MILLISECONDS).maxRetries(10).build());
 * </pre>
 */
public final class RetryWhen {

    private RetryWhen() {
        // prevent instantiation
    }

    private static final long NO_MORE_DELAYS = -1;

    private static Function<Flowable<? extends Throwable>, Flowable<Object>> notificationHandler(
            final Flowable<Long> delays, final Scheduler scheduler, final Consumer<? super ErrorAndDuration> action,
            final List<Class<? extends Throwable>> retryExceptions,
            final List<Class<? extends Throwable>> failExceptions,
            final Predicate<? super Throwable> exceptionPredicate) {

        final Function<ErrorAndDuration, Flowable<ErrorAndDuration>> checkExceptions = createExceptionChecker(
                retryExceptions, failExceptions, exceptionPredicate);

        return createNotificationHandler(delays, scheduler, action, checkExceptions);
    }

    private static Function<Flowable<? extends Throwable>, Flowable<Object>> createNotificationHandler(
            final Flowable<Long> delays, final Scheduler scheduler, final Consumer<? super ErrorAndDuration> action,
            final Function<ErrorAndDuration, Flowable<ErrorAndDuration>> checkExceptions) {
        return new Function<Flowable<? extends Throwable>, Flowable<Object>>() {

            @SuppressWarnings("unchecked")
            @Override
            public Flowable<Object> apply(Flowable<? extends Throwable> errors) {
                // TODO remove this cast when rxjava 2.0.3 released because
                // signature of retryWhen
                // will be fixed
                return (Flowable<Object>) (Flowable<?>) errors
                        // zip with delays, use -1 to signal completion
                        .zipWith(delays.concatWith(Flowable.just(NO_MORE_DELAYS)), TO_ERROR_AND_DURATION)
                        // check retry and non-retry exceptions
                        .flatMap(checkExceptions)
                        // perform user action (for example log that a
                        // delay is happening)
                        .doOnNext(callActionExceptForLast(action))
                        // delay the time in ErrorAndDuration
                        .flatMap(delay(scheduler));
            }
        };
    }

    private static Consumer<ErrorAndDuration> callActionExceptForLast(final Consumer<? super ErrorAndDuration> action) {
        return new Consumer<ErrorAndDuration>() {
            @Override
            public void accept(ErrorAndDuration e) throws Throwable {
                if (e.durationMs() != NO_MORE_DELAYS) {
                    action.accept(e);
                }
            }
        };
    }

    // TODO unit test
    private static Function<ErrorAndDuration, Flowable<ErrorAndDuration>> createExceptionChecker(
            final List<Class<? extends Throwable>> retryExceptions,
            final List<Class<? extends Throwable>> failExceptions,
            final Predicate<? super Throwable> exceptionPredicate) {
        return new Function<ErrorAndDuration, Flowable<ErrorAndDuration>>() {

            @Override
            public Flowable<ErrorAndDuration> apply(ErrorAndDuration e) throws Throwable {
				if (!exceptionPredicate.test(e.throwable())) {
					return Flowable.error(e.throwable());
				}
                for (Class<? extends Throwable> cls : failExceptions) {
					if (cls.isAssignableFrom(e.throwable().getClass())) {
						return Flowable.error(e.throwable());
					}
                }
                if (retryExceptions.size() > 0) {
                    for (Class<? extends Throwable> cls : retryExceptions) {
						if (cls.isAssignableFrom(e.throwable().getClass())) {
							return Flowable.just(e);
						}
                    }
                    return Flowable.error(e.throwable());
                } else {
                    return Flowable.just(e);
                }
            }
        };
    }

    private static BiFunction<Throwable, Long, ErrorAndDuration> TO_ERROR_AND_DURATION = new BiFunction<Throwable, Long, ErrorAndDuration>() {
        @Override
        public ErrorAndDuration apply(Throwable throwable, Long aLong) {
            return new ErrorAndDuration(throwable, aLong);
        }
    };

    private static Function<ErrorAndDuration, Flowable<ErrorAndDuration>> delay(final Scheduler scheduler) {
        return new Function<ErrorAndDuration, Flowable<ErrorAndDuration>>() {
			@Override
			public Flowable<ErrorAndDuration> apply(ErrorAndDuration e) throws Throwable {
				if (e.durationMs() == NO_MORE_DELAYS) {
					return Flowable.error(e.throwable());
				} else {
					return Flowable.timer(e.durationMs(), TimeUnit.MILLISECONDS, scheduler)
							.map(constant(e));
				}
			}
		};
    }

    private static <T> Function<Object, T> constant(final T value) {
        return new Function<Object, T>() {
            @Override
            public T apply(Object t) throws Throwable {
                return value;
            }
        };
    }

    // Builder factory methods

    public static Builder retryWhenInstanceOf(Class<? extends Throwable>... classes) {
        return new Builder().retryWhenInstanceOf(classes);
    }

    public static Builder failWhenInstanceOf(Class<? extends Throwable>... classes) {
        return new Builder().failWhenInstanceOf(classes);
    }

    public static Builder retryIf(Predicate<Throwable> predicate) {
        return new Builder().retryIf(predicate);
    }

    public static Builder delays(Flowable<Long> delays, TimeUnit unit) {
        return new Builder().delays(delays, unit);
    }

    public static Builder delaysInt(Flowable<Integer> delays, TimeUnit unit) {
        return new Builder().delaysInt(delays, unit);
    }

    public static Builder delay(long delay, final TimeUnit unit) {
        return new Builder().delay(delay, unit);
    }

    public static Builder maxRetries(int maxRetries) {
        return new Builder().maxRetries(maxRetries);
    }

    public static Builder scheduler(Scheduler scheduler) {
        return new Builder().scheduler(scheduler);
    }

    public static Builder action(Consumer<? super ErrorAndDuration> action) {
        return new Builder().action(action);
    }

    public static Builder exponentialBackoff(final long firstDelay, final TimeUnit unit, final double factor) {
        return new Builder().exponentialBackoff(firstDelay, unit, factor);
    }

    public static Builder exponentialBackoff(long firstDelay, TimeUnit unit) {
        return new Builder().exponentialBackoff(firstDelay, unit);
    }

    public static final class Builder {

        static final class TruePredicate implements Predicate<Object> {
            @Override
            public boolean test(Object o) {
                return true;
            }
        }
        private final List<Class<? extends Throwable>> retryExceptions = new ArrayList<Class<? extends Throwable>>();
        private final List<Class<? extends Throwable>> failExceptions = new ArrayList<Class<? extends Throwable>>();
        private Predicate<? super Throwable> exceptionPredicate = new TruePredicate();

        private Flowable<Long> delays = Flowable.just(0L).repeat();
        private Optional<Integer> maxRetries = Optional.absent();
        private Optional<Scheduler> scheduler = Optional.of(Schedulers.computation());
        private Consumer<? super ErrorAndDuration> action = Consumers.doNothing();

        private Builder() {
            // must use static factory method to instantiate
        }

        public Builder retryWhenInstanceOf(Class<? extends Throwable>... classes) {
            retryExceptions.addAll(Arrays.asList(classes));
            return this;
        }

        public Builder failWhenInstanceOf(Class<? extends Throwable>... classes) {
            failExceptions.addAll(Arrays.asList(classes));
            return this;
        }

        public Builder retryIf(Predicate<Throwable> predicate) {
            this.exceptionPredicate = predicate;
            return this;
        }

        public Builder delays(Flowable<Long> delays, TimeUnit unit) {
            this.delays = delays.map(toMillis(unit));
            return this;
        }

        private static class ToLongHolder {
            static final Function<Integer, Long> INSTANCE = new Function<Integer, Long>() {
                @Override
                public Long apply(Integer n) {
                    if (n == null) {
                        return null;
                    } else {
                        return n.longValue();
                    }
                }
            };
        }

        public Builder delaysInt(Flowable<Integer> delays, TimeUnit unit) {
            return delays(delays.map(ToLongHolder.INSTANCE), unit);
        }

        public Builder delay(Long delay, final TimeUnit unit) {
            this.delays = Flowable.just(delay).map(toMillis(unit)).repeat();
            return this;
        }

        private static Function<Long, Long> toMillis(final TimeUnit unit) {
            return new Function<Long, Long>() {

                @Override
                public Long apply(Long t) {
                    return unit.toMillis(t);
                }
            };
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = Optional.of(maxRetries);
            return this;
        }

        public Builder scheduler(Scheduler scheduler) {
            this.scheduler = Optional.of(scheduler);
            return this;
        }

        public Builder action(Consumer<? super ErrorAndDuration> action) {
            this.action = action;
            return this;
        }

        public Builder exponentialBackoff(final long firstDelay, final long maxDelay, final TimeUnit unit,
                final double factor) {

            delays = Flowable.range(1, Integer.MAX_VALUE)
                    // make exponential
                    .map(new Function<Integer, Long>() {
                        @Override
                        public Long apply(Integer n) {
                            long delayMs = Math.round(Math.pow(factor, n - 1) * unit.toMillis(firstDelay));
                            if (maxDelay == -1) {
                                return delayMs;
                            } else {
                                long maxDelayMs = unit.toMillis(maxDelay);
                                return Math.min(maxDelayMs, delayMs);
                            }
                        }
                    });
            return this;
        }

        public Builder exponentialBackoff(final long firstDelay, final TimeUnit unit, final double factor) {
            return exponentialBackoff(firstDelay, -1, unit, factor);
        }

        public Builder exponentialBackoff(long firstDelay, TimeUnit unit) {
            return exponentialBackoff(firstDelay, unit, 2);
        }

        public Function<Flowable<? extends Throwable>, Flowable<Object>> build() {
            Preconditions.checkNotNull(delays);
            if (maxRetries.isPresent()) {
                delays = delays.take(maxRetries.get());
            }
            return notificationHandler(delays, scheduler.get(), action, retryExceptions, failExceptions,
                    exceptionPredicate);
        }

    }

    public static final class ErrorAndDuration {

        private final Throwable throwable;
        private final long durationMs;

        public ErrorAndDuration(Throwable throwable, long durationMs) {
            this.throwable = throwable;
            this.durationMs = durationMs;
        }

        public Throwable throwable() {
            return throwable;
        }

        public long durationMs() {
            return durationMs;
        }

    }
}

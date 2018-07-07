package com.salesforce.rxgrpc;

import org.junit.Assert;
import org.junit.rules.ExternalResource;
import io.reactivex.plugins.RxJavaPlugins;

import java.util.function.Predicate;

/**
 * {@code UnhandledRxJavaErrorRule} is a JUnit rule that captures unhandled RxJava exceptions.`
 */
public class UnhandledRxJavaErrorRule extends ExternalResource {
    private Throwable unhandledThrowable;
    private boolean autoverify;

    @Override
    protected void before() throws Throwable {
        RxJavaPlugins.setErrorHandler(throwable -> unhandledThrowable = throwable);
    }

    @Override
    protected void after() {
        RxJavaPlugins.setErrorHandler(null);
        if (autoverify) {
            verifyNoError();
        }
    }

    public UnhandledRxJavaErrorRule autoVerifyNoError() {
        autoverify = true;
        return this;
    }

    public void verifyNoError() {
        if (unhandledThrowable != null) {
            unhandledThrowable.printStackTrace();
            Assert.fail("Unhandled RxJava error\n" + unhandledThrowable.toString());
        }
    }

    public void verify(Predicate<Throwable> test) {
        if (! test.test(unhandledThrowable)) {
            Assert.fail("Unhandled RxJava error was not as expected");
        }
    }
}

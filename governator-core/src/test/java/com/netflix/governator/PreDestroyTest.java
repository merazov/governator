package com.netflix.governator;

import java.io.Closeable;
import java.io.IOException;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class PreDestroyTest {
    private static class Foo {
        @PreDestroy
        public void shutdown() {

        }
    }

    private static class InvalidPreDestroys {
        @PreDestroy
        public String shutdownWithReturnValue() {
            return "invalid return type";
        }

        @PreDestroy
        public static void shutdownStatic() {
            // can't use static method type
            throw new RuntimeException("boom");
        }

        @PreDestroy
        public void shutdownWithParameters(String invalidArg) {
            // can't use method parameters
        }
    }

    private interface PreDestroyInterface {
        @PreDestroy
        public void destroy();
    }

    private static class PreDestroyImpl implements PreDestroyInterface {
        @Override
        public void destroy() {
            // should not be called
        }
    }

    private static class RunnableType implements Runnable {
        @Override
        @PreDestroy
        public void run() {
            // method from interface; will it be called?
        }
    }

    private static class CloseableType implements Closeable {
        @Override
        public void close() throws IOException {
        }

        @PreDestroy
        public void shutdown() {

        }
    }

    private static class PreDestroyParent {
        @PreDestroy
        public void shutdown() {

        }

        @PreDestroy
        public void anotherShutdown() {

        }

        @PreDestroy
        public void yetAnotherShutdown() {

        }

    }

    private static class PreDestroyChild extends PreDestroyParent {
        @PreDestroy
        public void shutdown() {
            System.out.println("shutdown invoked");
        }

        public void yetAnotherShutdown() {

        }
    }

    @Test
    public void testLifecycleShutdownInheritance() {
        final PreDestroyChild preDestroyChild = Mockito.spy(new PreDestroyChild());
        InOrder inOrder = Mockito.inOrder(preDestroyChild);

        try (LifecycleInjector injector = TestSupport.inject(preDestroyChild)) {
            Assert.assertNotNull(injector.getInstance(preDestroyChild.getClass()));
            Mockito.verify(preDestroyChild, Mockito.never()).shutdown();
            Mockito.verify(preDestroyChild, Mockito.never()).anotherShutdown();
            Mockito.verify(preDestroyChild, Mockito.never()).yetAnotherShutdown();
        }

        // once not twice
        inOrder.verify(preDestroyChild, Mockito.times(1)).shutdown(); 
        // once to parent anotherShutdown(), after shutdown()
        inOrder.verify(preDestroyChild, Mockito.times(1)).anotherShutdown();
    }

    @Test
    public void testLifecycleDeclaredInterfaceMethod() {
        final RunnableType runnableInstance = Mockito.mock(RunnableType.class);
        InOrder inOrder = Mockito.inOrder(runnableInstance);

        try (LifecycleInjector injector = TestSupport.inject(runnableInstance)) {
            Assert.assertNotNull(injector.getInstance(RunnableType.class));
            Mockito.verify(runnableInstance, Mockito.never()).run();
        }
        inOrder.verify(runnableInstance, Mockito.times(1)).run();
    }

    @Test
    public void testLifecycleAnnotatedInterfaceMethod() {
        final PreDestroyImpl impl = Mockito.mock(PreDestroyImpl.class);
        InOrder inOrder = Mockito.inOrder(impl);

        try (LifecycleInjector injector = TestSupport.inject(impl)) {
            Assert.assertNotNull(injector.getInstance(RunnableType.class));
            Mockito.verify(impl, Mockito.never()).destroy();
        }
        inOrder.verify(impl, Mockito.never()).destroy();
    }

    @Test
    public void testLifecycleShutdownWithInvalidPreDestroys() {
        final InvalidPreDestroys ipd = Mockito.mock(InvalidPreDestroys.class);

        try (LifecycleInjector injector = TestSupport.inject(ipd)) {
            Assert.assertNotNull(injector.getInstance(InvalidPreDestroys.class));
            Mockito.verify(ipd, Mockito.never()).shutdownWithParameters(Mockito.anyString());
            Mockito.verify(ipd, Mockito.never()).shutdownWithReturnValue();
        }
        Mockito.verify(ipd, Mockito.never()).shutdownWithParameters(Mockito.anyString());
        Mockito.verify(ipd, Mockito.never()).shutdownWithReturnValue();
    }

    @Test
    public void testLifecycleCloseable() {
        final CloseableType closeableType = Mockito.mock(CloseableType.class);
        try {
            Mockito.doThrow(new IOException("boom")).when(closeableType).close();
        } catch (IOException e1) {
            // ignore, mock only
        }

        try (LifecycleInjector injector = TestSupport.inject(closeableType)) {
            Assert.assertNotNull(injector.getInstance(CloseableType.class));
            try {
                Mockito.verify(closeableType, Mockito.never()).close();
                Mockito.verify(closeableType, Mockito.never()).close();
            } catch (IOException e) {
                // close() called before shutdown and failed
                Assert.fail("close() called before shutdown and  failed");
            }
        }

        try {
            Mockito.verify(closeableType, Mockito.times(1)).close();
            Mockito.verify(closeableType, Mockito.never()).shutdown();
        } catch (IOException e) {
            // close() called before shutdown and failed
            Assert.fail("close() called after shutdown and  failed");
        }

    }

    @Test
    public void testLifecycleShutdown() {
        final Foo foo = Mockito.mock(Foo.class);
        try (LifecycleInjector injector = TestSupport.inject(foo)) {
            Assert.assertNotNull(injector.getInstance(Foo.class));
            Mockito.verify(foo, Mockito.never()).shutdown();
        }
        Mockito.verify(foo, Mockito.times(1)).shutdown();
    }

    @Test
    public void testLifecycleShutdownWithAtProvides() {
        final Foo foo = Mockito.mock(Foo.class);

        InjectorBuilder builder = InjectorBuilder.fromModule(new AbstractModule() {
            @Override
            protected void configure() {
            }

            @Provides
            @Singleton
            Foo getFoo() {
                return foo;
            }
        });

        try (LifecycleInjector injector = builder.createInjector()) {
            Assert.assertNotNull(injector.getInstance(Foo.class));
            Mockito.verify(foo, Mockito.never()).shutdown();
        }
        Mockito.verify(foo, Mockito.times(1)).shutdown();
    }

    @Before
    public void printTestHeader() {
        System.out.println("\n=======================================================");
        System.out.println("  Running Test : " + name.getMethodName());
        System.out.println("=======================================================\n");
    }

    @Rule
    public TestName name = new TestName();

}

package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import com.vaadin.testbench.unit.internal.UIFactory;
import jakarta.servlet.ServletException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.locks.ReentrantLock;

import kotlin.jvm.functions.Function0;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.InitParameters;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;
import com.vaadin.flow.spring.VaadinScopesConfig;
import com.vaadin.kubernetes.starter.sessiontracker.PessimisticSerializationRequiredException;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.SpringTransientHandlerVaadinScopesTest.TestConfig.TestView;
import com.vaadin.testbench.unit.UITestSpringLookupInitializer;
import com.vaadin.testbench.unit.internal.MockVaadin;
import com.vaadin.testbench.unit.internal.Routes;
import com.vaadin.testbench.unit.mocks.MockSpringServlet;
import com.vaadin.testbench.unit.mocks.MockedUI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@ContextConfiguration(classes = {
        SpringTransientHandlerVaadinScopesTest.TestConfig.class,
        VaadinScopesConfig.class })
@ExtendWith(SpringExtension.class)
@TestExecutionListeners(listeners = UITestSpringLookupInitializer.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class VaadinScopeSerializationDeserializationTest {

    @Autowired
    ApplicationContext appCtx;

    SpringTransientHandler handler;

    @BeforeEach
    void setUp() {
        System.setProperty("sun.io.serialization.extendedDebugInfo", "true");
        handler = new SpringTransientHandler(appCtx);
        setupVaadin();
    }

    private void setupVaadin() {
        Routes routes = new Routes()
                .autoDiscoverViews(TestView.class.getPackageName());
        UIFactory uiFactory = MockedUI::new;
        MockSpringServlet servlet = new MockSpringServlet(routes, appCtx,
                uiFactory) {
            @Override
            protected DeploymentConfiguration createDeploymentConfiguration()
                    throws ServletException {
                getServletContext().setInitParameter(
                        InitParameters.APPLICATION_PARAMETER_DEVMODE_ENABLE_SERIALIZE_SESSION,
                        "true");
                return super.createDeploymentConfiguration();
            }
        };
        MockVaadin.setup(uiFactory, servlet,
                Set.of(UITestSpringLookupInitializer.class));
    }

    interface SerializableUIFactory extends Function0<UI>, Serializable {
    }

    @Test
    void serialization_vaadinSessionAvailableAndUnlocked_acquireLock_beanInspected()
            throws Exception {
        TestView view = navigateToView();
        VaadinSession vaadinSession = VaadinSession.getCurrent();

        ByteArrayOutputStream result = doSerialize(vaadinSession, 0);
        vaadinSession.getLockInstance().lock();
        MockVaadin.tearDown();

        setupVaadin();
        TestView deserializedView = doDeserialize(result);

        assertScopedBeansInjected(deserializedView, view);
    }

    @Test
    void serialization_vaadinSessionAvailableAndLocked_tryAcquireLockSucceed_beanInspected()
            throws Exception {
        TestView view = navigateToView();
        VaadinSession vaadinSession = VaadinSession.getCurrent();

        ByteArrayOutputStream result = doSerialize(vaadinSession, 300);
        vaadinSession.getLockInstance().lock();
        MockVaadin.tearDown();

        setupVaadin();
        TestView deserializedView = doDeserialize(result);

        assertScopedBeansInjected(deserializedView, view);
    }

    @Test
    void serialization_vaadinSessionAvailableAndLocked_tryAcquireLockFail_requirePessimisticLock()
            throws Exception {
        navigateToView();
        VaadinSession vaadinSession = VaadinSession.getCurrent();

        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(() -> doSerialize(vaadinSession, 1200))
                .withCauseExactlyInstanceOf(
                        PessimisticSerializationRequiredException.class);
    }

    @Test
    void serialization_vaadinSessionNotAvailable_beansNotInspected()
            throws Exception {
        TestView view = navigateToView();
        view.removeFromParent();

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        TransientInjectableObjectOutputStream writer = TransientInjectableObjectOutputStream
                .newInstance(data, handler, clazz -> clazz.getPackageName()
                        .startsWith("com.vaadin.kubernetes"));
        CompletableFuture.runAsync(() -> {
            try {
                writer.writeWithTransients(view);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).join();
        MockVaadin.tearDown();

        TransientInjectableObjectInputStream reader = new TransientInjectableObjectInputStream(
                new ByteArrayInputStream(data.toByteArray()), handler);

        TestView deserializedView = CompletableFuture.supplyAsync(() -> {
            try {
                return reader.<TestView> readWithTransients();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).join();

        assertThat(deserializedView).extracting(v -> v.sessionScoped,
                v -> v.uiScoped, v -> v.routeScoped).containsOnlyNulls();

    }

    private static void assertScopedBeansInjected(TestView deserializedView,
            TestView view) {
        assertThat(deserializedView).extracting(v -> v.sessionScoped,
                v -> v.uiScoped, v -> v.routeScoped).doesNotContainNull();
        assertThat(deserializedView)
                .extracting(v -> v.sessionScoped.value, v -> v.uiScoped.value,
                        v -> v.routeScoped.value)
                .containsExactly(view.sessionScoped.value, view.uiScoped.value,
                        view.routeScoped.value);
    }

    private TestView navigateToView() {
        UI ui = UI.getCurrent();
        TestView view = ui.navigate(TestView.class)
                .orElseThrow(() -> new AssertionError(
                        "Cannot get instance of " + TestView.class));
        String randomValue = UUID.randomUUID().toString();
        view.sessionScoped.value = "SESSION-" + randomValue;
        view.uiScoped.value = "UI-" + randomValue;
        view.routeScoped.value = "ROUTE-" + randomValue;
        return view;
    }

    private ByteArrayOutputStream doSerialize(VaadinSession session,
            int unlockAfterMillis) throws Exception {

        if (unlockAfterMillis == 0) {
            session.getLockInstance().unlock();
        }
        Map<String, Object> target = MapBasedWrappedSession
                .asMap(session.getSession());
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TransientInjectableObjectOutputStream writer = TransientInjectableObjectOutputStream
                .newInstance(os, handler, clazz -> clazz.getPackageName()
                        .startsWith("com.vaadin.kubernetes"));
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                writer.writeWithTransients(target);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        if (unlockAfterMillis > 0) {
            Thread.sleep(unlockAfterMillis);
            session.getLockInstance().unlock();
        }
        future.join();
        return os;
    }

    private TestView doDeserialize(ByteArrayOutputStream data)
            throws IOException {
        VaadinService vaadinService = VaadinService.getCurrent();

        TransientInjectableObjectInputStream reader = new TransientInjectableObjectInputStream(
                new ByteArrayInputStream(data.toByteArray()), handler);
        Map<String, Object> result;
        result = CompletableFuture.supplyAsync(() -> {
            try {
                return reader.<Map<String, Object>> readWithTransients();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).join();

        MapBasedWrappedSession wrappedSession = new MapBasedWrappedSession(
                result);
        ReentrantLock lockInstance = wrappedSession
                .getLockInstance(vaadinService);
        lockInstance.lock();
        VaadinSession session = wrappedSession.getVaadinSession();
        session.refreshTransients(wrappedSession, vaadinService);
        try {
            assertThat(session.getUIs()).hasSize(1);

            TestView deserializedView = getTestView(
                    session.getUIs().iterator().next());
            assertThat(deserializedView).isNotNull();
            return deserializedView;
        } finally {
            lockInstance.unlock();
        }
    }

    private TestView getTestView(UI ui) {
        return ui.getChildren().filter(TestView.class::isInstance)
                .map(TestView.class::cast).findFirst().orElse(null);
    }

    private static class MapBasedWrappedSession implements WrappedSession {

        private final Map<String, Object> map;
        private final VaadinSession session;

        public MapBasedWrappedSession(Map<String, Object> map) {
            this.map = map;
            this.session = map.values().stream()
                    .filter(VaadinSession.class::isInstance)
                    .map(VaadinSession.class::cast).findFirst().orElse(null);
        }

        @Override
        public int getMaxInactiveInterval() {
            return 0;
        }

        @Override
        public Object getAttribute(String name) {
            return map.get(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            map.put(name, value);
        }

        @Override
        public Set<String> getAttributeNames() {
            return Set.copyOf(map.keySet());
        }

        @Override
        public void invalidate() {

        }

        @Override
        public String getId() {
            return "";
        }

        @Override
        public long getCreationTime() {
            return 0;
        }

        @Override
        public long getLastAccessedTime() {
            return 0;
        }

        @Override
        public boolean isNew() {
            return false;
        }

        @Override
        public void removeAttribute(String name) {
            map.remove(name);
        }

        @Override
        public void setMaxInactiveInterval(int interval) {

        }

        ReentrantLock getLockInstance(VaadinService service) {
            return (ReentrantLock) map.get(service.getServiceName() + ".lock");
        }

        VaadinSession getVaadinSession() {
            return session;
        }

        static Map<String, Object> asMap(WrappedSession wrappedSession) {
            Map<String, Object> map = new HashMap<>();
            wrappedSession.getAttributeNames().forEach(attrName -> map
                    .put(attrName, wrappedSession.getAttribute(attrName)));
            return map;
        }
    }
}

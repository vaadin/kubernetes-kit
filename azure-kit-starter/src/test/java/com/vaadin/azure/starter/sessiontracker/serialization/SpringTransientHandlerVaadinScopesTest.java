package com.vaadin.azure.starter.sessiontracker.serialization;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.VaadinScopesConfig;
import com.vaadin.flow.spring.annotation.RouteScope;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import com.vaadin.testbench.unit.UITestSpringLookupInitializer;
import com.vaadin.testbench.unit.internal.MockVaadin;
import com.vaadin.testbench.unit.internal.Routes;
import com.vaadin.testbench.unit.mocks.MockSpringServlet;
import com.vaadin.testbench.unit.mocks.MockedUI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@ContextConfiguration(classes = {
        SpringTransientHandlerVaadinScopesTest.TestConfig.class,
        VaadinScopesConfig.class })
@ExtendWith(SpringExtension.class)
@TestExecutionListeners(listeners = UITestSpringLookupInitializer.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class SpringTransientHandlerVaadinScopesTest {

    @Autowired
    ApplicationContext appCtx;
    SpringTransientHandler handler;

    @BeforeEach
    void setUp() {
        Routes routes = new Routes()
                .autoDiscoverViews(TestConfig.TestView.class.getPackageName());
        MockSpringServlet servlet = new MockSpringServlet(routes, appCtx,
                MockedUI::new);
        MockVaadin.setup(MockedUI::new, servlet,
                Set.of(UITestSpringLookupInitializer.class));

        handler = new SpringTransientHandler(appCtx);
        Map<String, Object> beans = appCtx.getBeansOfType(Object.class);
        System.out.println(beans);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
    }

    @Test
    void inspect_scopedBeans_beansAreDetected() {
        TestConfig.TestView target = UI.getCurrent()
                .navigate(TestConfig.TestView.class)
                .orElseThrow(() -> new AssertionError(
                        "Cannot get instance of " + TestConfig.TestView.class));

        List<TransientDescriptor> transients = handler.inspect(target);

        assertThat(transients).containsExactlyInAnyOrder(
                new TransientDescriptor(TestConfig.TestView.class, "uiScoped",
                        TestConfig.UIScopedComponent.class,
                        TestConfig.UIScopedComponent.class.getName()),
                new TransientDescriptor(TestConfig.TestView.class,
                        "routeScoped", TestConfig.RouteScopedComponent.class,
                        TestConfig.RouteScopedComponent.class.getName()),
                new TransientDescriptor(TestConfig.TestView.class,
                        "sessionScoped",
                        TestConfig.VaadinSessionScopedComponent.class,
                        TestConfig.VaadinSessionScopedComponent.class
                                .getName()));
    }

    @Configuration
    static class TestConfig {
        @UIScope
        @SpringComponent
        static class UIScopedComponent {

        }

        @VaadinSessionScope
        @SpringComponent
        static class VaadinSessionScopedComponent {

        }

        @RouteScope
        @Component
        static class RouteScopedComponent {

        }

        // @Component
        @Route("test")
        @Tag("my-view")
        public static class TestView
                extends com.vaadin.flow.component.Component {
            @Autowired
            transient UIScopedComponent uiScoped;
            @Autowired
            transient VaadinSessionScopedComponent sessionScoped;
            @Autowired
            transient RouteScopedComponent routeScoped;
        }

    }
}

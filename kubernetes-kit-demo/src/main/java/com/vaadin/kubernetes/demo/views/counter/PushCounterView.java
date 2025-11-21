package com.vaadin.kubernetes.demo.views.counter;

import java.io.Serializable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.kubernetes.demo.services.HostInfo;
import com.vaadin.kubernetes.demo.views.MainLayout;

@PageTitle("Push Counter")
@Route(value = "push-counter", layout = MainLayout.class)
public class PushCounterView extends VerticalLayout {

    private static final ScheduledExecutorService executorService = Executors
            .newSingleThreadScheduledExecutor();

    private final H1 counterHeading = new H1();

    private final H2 hostnameHeading = new H2();

    private final H3 ipAddressHeading = new H3();

    private final UnorderedList log = new UnorderedList();

    private final Button button = new Button("Increment");

    private final AtomicInteger counter = new AtomicInteger();

    private transient ScheduledFuture<?> repeatedTaskFeature;

    public PushCounterView() {
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);
        getStyle().set("text-align", "center");

        counterHeading.addClassName("counter");
        hostnameHeading.addClassName("hostname");
        ipAddressHeading.addClassName("ip-address");

        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY,
                ButtonVariant.LUMO_LARGE);
        button.addClickListener(event -> count());

        Button longActionButton = new Button("Long running task", ev -> {
            int currentCounter = counter.get();
            addLogEntry("Long Task " + currentCounter + " Started");
            blocking(5000, () -> addLogEntry(
                    "Long Task " + currentCounter + " Completed"));
        });
        Button backgroundTaskButton = new Button("Background task", ev -> {
            int currentCounter = counter.get();
            addLogEntry("Long Task " + currentCounter + " Started");
            UI ui = ev.getSource().getUI().get();
            executorService.schedule(
                    () -> ui.access(() -> count("Long Task " + currentCounter
                            + " Completed. " + "Counter now is %d")),
                    10, TimeUnit.SECONDS);
        });
        Button repeatedTaskButton = new Button("Repeated task", ev -> {
            if (repeatedTaskFeature != null) {
                repeatedTaskFeature.cancel(true);
                repeatedTaskFeature = null;
                ev.getSource().setText("Repeated task");
            } else {
                ev.getSource().setText("Stop repeated task");
                int currentCounter = counter.get();
                addLogEntry("Repeated Task " + currentCounter + " Started");
                UI ui = ev.getSource().getUI().get();
                repeatedTaskFeature = executorService.scheduleWithFixedDelay(
                        () -> ui.access(
                                () -> count("Repeated Task " + currentCounter
                                        + " Completed. " + "Counter now is %s")),
                        1, 1, TimeUnit.SECONDS);
            }
        });

        count();

        add(counterHeading, hostnameHeading, ipAddressHeading, button,
                new HorizontalLayout(longActionButton, backgroundTaskButton,
                        repeatedTaskButton));
        Scroller logContainer = new Scroller(log);
        addAndExpand(logContainer);

        addDetachListener(ev -> {
            if (repeatedTaskFeature != null) {
                repeatedTaskFeature.cancel(true);
            }
        });
    }

    private void addLogEntry(String text) {
        log.addComponentAsFirst(new ListItem(text));
    }

    private void blocking(int timeoutMs, Runnable action) {
        try {
            Thread.sleep(timeoutMs);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        action.run();
    }

    private void count() {
        count("%2$s (%3$s) %1$d");
    }

    private void count(String message) {
        int newValue = counter.incrementAndGet();
        final var entry = new CountEntry(newValue, HostInfo.getHostname(),
                HostInfo.getIpAddress());

        counterHeading.setText(Integer.toString(entry.getCount()));
        hostnameHeading.setText(entry.getHostname());
        ipAddressHeading.setText(entry.getIpAddress());

        final var item = new ListItem(String.format(message, entry.getCount(),
                entry.getHostname(), entry.getIpAddress()));
        log.addComponentAsFirst(item);
    }

    static class CountEntry implements Serializable {

        private final int count;
        private final String hostname;
        private final String ipAddress;

        public CountEntry(int count, String hostname, String ipAddress) {
            this.count = count;
            this.hostname = hostname;
            this.ipAddress = ipAddress;
        }

        public int getCount() {
            return count;
        }

        public String getHostname() {
            return hostname;
        }

        public String getIpAddress() {
            return ipAddress;
        }
    }
}

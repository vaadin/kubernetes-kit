package com.vaadin.azure.demo.views.counter;

import java.io.Serializable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.vaadin.azure.demo.services.HostInfo;
import com.vaadin.azure.demo.views.MainLayout;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

@PageTitle("Counter")
@Route(value = "counter", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
public class CounterView extends VerticalLayout {

    private static ScheduledExecutorService executorService = Executors
            .newSingleThreadScheduledExecutor();

    private final H1 counterHeading = new H1();

    private final H2 hostnameHeading = new H2();

    private final H3 ipAddressHeading = new H3();

    private final UnorderedList log = new UnorderedList();

    private final Button button = new Button("Increment");

    private final AtomicInteger counter = new AtomicInteger();

    public CounterView() {
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
                    () -> ui.access(() -> addLogEntry(
                            "Long Task " + currentCounter + " Completed. "
                                    + "Counter now is " + counter.incrementAndGet())),
                    10, TimeUnit.SECONDS);
        });

        count();

        add(counterHeading, hostnameHeading, ipAddressHeading, button,
                longActionButton, backgroundTaskButton);
        addAndExpand(log);
    }

    private void addLogEntry(String text) {
        log.addComponentAsFirst(new ListItem(text));
        System.out.println(
                "================================= Maybe Push operation "
                        + text);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        UI ui = attachEvent.getUI();
        // executorService = Executors.newSingleThreadScheduledExecutor();
        /*
         * executorService.scheduleWithFixedDelay(ui.accessLater( () ->
         * addLogEntry("Push Update " + counter.get()), this::stopService), 5,
         * 60, TimeUnit.SECONDS);
         */
    }

    private void stopService() {
        if (executorService != null) {
            executorService.shutdown();
        }
        executorService = null;
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
        final var entry = new CountEntry(counter.incrementAndGet(),
                HostInfo.getHostname(), HostInfo.getIpAddress());

        counterHeading.setText(Integer.toString(entry.getCount()));
        hostnameHeading.setText(entry.getHostname());
        ipAddressHeading.setText(entry.getIpAddress());

        final var item = new ListItem(entry.getHostname() + " ("
                + entry.getIpAddress() + ") " + entry.getCount());
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

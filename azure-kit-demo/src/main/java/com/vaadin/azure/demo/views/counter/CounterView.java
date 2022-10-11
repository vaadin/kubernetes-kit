package com.vaadin.azure.demo.views.counter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.vaadin.azure.demo.services.HostInfo;
import com.vaadin.azure.demo.views.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

@PageTitle("Counter")
@Route(value = "counter", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
public class CounterView extends VerticalLayout {

    private final H1 counterHeading = new H1();

    private final H2 hostnameHeading = new H2();

    private final H3 ipAddressHeading = new H3();

    private final Grid<CountEntry> grid = new Grid<>();

    private final List<CountEntry> log = new ArrayList<>();

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

        grid.addColumn(CountEntry::getCount);
        grid.addColumn(CountEntry::getHostname);
        grid.addColumn(CountEntry::getIpAddress);
        grid.setItems(DataProvider.ofCollection(log));

        count();

        add(counterHeading, hostnameHeading, ipAddressHeading, button, grid);
    }

    private void count() {
        final var entry = new CountEntry(counter.incrementAndGet(),
                HostInfo.getHostname(), HostInfo.getIpAddress());

        counterHeading.setText(Integer.toString(entry.getCount()));
        hostnameHeading.setText(entry.getHostname());
        ipAddressHeading.setText(entry.getIpAddress());

        log.add(0, entry);
        grid.getDataProvider().refreshAll();
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

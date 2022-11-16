package com.vaadin.kubernetes.demo.views.counter;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.vaadin.kubernetes.demo.entity.Company;
import com.vaadin.kubernetes.demo.services.CrmService;
import com.vaadin.kubernetes.demo.services.HostInfo;
import com.vaadin.kubernetes.demo.views.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.VaadinSession;

@PageTitle("Counter")
@Route(value = "counter", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
public class CounterView extends VerticalLayout {

    private final H1 counterHeading = new H1();

    private final H2 hostnameHeading = new H2();

    private final H3 ipAddressHeading = new H3();

    private final UnorderedList log = new UnorderedList();

    private final Button button = new Button("Increment");

    private final AtomicInteger counter = new AtomicInteger();

    private transient CrmService service;

    public CounterView(CrmService service) {
        this.service = service;
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

        count();

        add(counterHeading, hostnameHeading, ipAddressHeading, button,
                new Button("Use Service", ev -> doSomethingWithService()));
        addAndExpand(log);
    }

    public CrmService getService() {
        return service;
    }

    private void doSomethingWithService() {
        add(new Paragraph(getService().findAllCompanies().stream()
                .map(Company::getName).collect(Collectors.joining(", "))));
    }

    private void count() {
        final var entry = new CountEntry(counter.incrementAndGet(),
                HostInfo.getHostname(), HostInfo.getIpAddress());

        counterHeading.setText(Integer.toString(entry.getCount()));
        hostnameHeading.setText(entry.getHostname());
        ipAddressHeading.setText(entry.getIpAddress());

        final var item = new ListItem("host: " + entry.getHostname() + " ("
                + entry.getIpAddress() + ")" + " | version: "
                + System.getenv("APP_VERSION") + " | session: "
                + VaadinSession.getCurrent().getSession().getId()
                + " | counter: " + entry.getCount());
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

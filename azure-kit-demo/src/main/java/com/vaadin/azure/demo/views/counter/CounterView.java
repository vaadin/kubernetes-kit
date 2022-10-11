package com.vaadin.azure.demo.views.counter;

import com.vaadin.azure.demo.services.CounterService;
import com.vaadin.azure.demo.services.HostInfo;
import com.vaadin.azure.demo.views.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

@PageTitle("Counter")
@Route(value = "counter", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
public class CounterView extends VerticalLayout {

    private final CounterService counterService;

    private final H1 counterHeading = new H1();

    private final H2 hostnameHeading = new H2();

    private final H3 ipAddressHeading = new H3();

    private final Button button = new Button("Increment");

    public CounterView(CounterService counterService) {
        this.counterService = counterService;

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);
        getStyle().set("text-align", "center");

        counterHeading.addClassName("counter");
        hostnameHeading.addClassName("hostname");
        ipAddressHeading.addClassName("ip-address");

        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY,
                ButtonVariant.LUMO_LARGE);
        button.addClickListener(event -> updateHeadings());

        updateHeadings();

        add(counterHeading, hostnameHeading, ipAddressHeading, button);
    }

    private void updateHeadings() {
        counterHeading.setText(counterService.incrementAndGet());
        hostnameHeading.setText(HostInfo.getHostname());
        ipAddressHeading.setText(HostInfo.getIpAddress());
    }
}

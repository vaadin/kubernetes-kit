package com.vaadin.kubernetes.demo.views.list;

import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.shared.Registration;
import com.vaadin.kubernetes.demo.entity.Company;
import com.vaadin.kubernetes.demo.entity.Contact;
import com.vaadin.kubernetes.demo.entity.Status;

import java.util.List;
import java.util.Optional;

public class ContactForm extends FormLayout {
    private static final String CONTACT_ID_SESSION = "CONTACT_ID_SESSION";
    private Contact contact;
    TextField firstName = new TextField("First name");
    TextField lastName = new TextField("Last name");
    EmailField email = new EmailField("Email");
    ComboBox<Status> status = new ComboBox<>("Status");
    ComboBox<Company> company = new ComboBox<>("Company");

    Binder<Contact> binder = new BeanValidationBinder<>(Contact.class);
    Button save = new Button("Save");
    Button delete = new Button("Delete");
    Button close = new Button("Cancel");

    public ContactForm(List<Company> companies, List<Status> statuses) {
        addClassName("contact-form");
        binder.bindInstanceFields(this);

        company.setItems(companies);
        company.setItemLabelGenerator(Company::getName);

        status.setItems(statuses);
        status.setItemLabelGenerator(Status::getName);

        add(firstName,
                lastName,
                email,
                company,
                status,
                createButtonsLayout());
    }

    private HorizontalLayout createButtonsLayout() {
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        save.addClickShortcut(Key.ENTER);
        close.addClickShortcut(Key.ESCAPE);

        save.addClickListener(event -> validateAndSave());
        delete.addClickListener(event -> fireEvent(new DeleteEvent(this, contact)));
        close.addClickListener(event -> fireEvent(new CloseEvent(this)));


        binder.addStatusChangeListener(e -> save.setEnabled(binder.isValid()));

        return new HorizontalLayout(save, delete, close);
    }

    public void setContact(Contact contact) {

        this.contact = contact;

        //store contact id in vaadin session
        Optional.ofNullable(UI.getCurrent()).ifPresentOrElse(
                ui -> ui.getSession().setAttribute(CONTACT_ID_SESSION, contact == null ? null : contact.getId()),
                    () -> System.err.println("ui session is not available yet"));

        binder.readBean(contact);
    }

    private void validateAndSave() {
        try {
            binder.writeBean(contact);
            if (contact.getId() != null)
                fireEvent(new SaveEvent(this, contact, false));
            else {
                Optional.ofNullable(UI.getCurrent()).ifPresentOrElse(ui -> {
                    var session = ui.getSession();
                    var id = Integer.valueOf(session.getAttribute(CONTACT_ID_SESSION).toString());
                    contact.setId(id);
                    fireEvent(new SaveEvent(this, contact, true));
                }, () -> System.err.println("ui session is not available yet"));
            }
        } catch (ValidationException e) {
            e.printStackTrace();
        }
    }

    // Events
    public static abstract class ContactFormEvent extends ComponentEvent<ContactForm> {
        private final Contact contact;

        protected ContactFormEvent(ContactForm source, Contact contact) {
            super(source, false);
            this.contact = contact;
        }

        public Contact getContact() {
            return contact;
        }
    }

    public static class SaveEvent extends ContactFormEvent {
        private final boolean detached;

        SaveEvent(ContactForm source, Contact contact, boolean detached) {
            super(source, contact);
            this.detached = detached;
        }

        public boolean isDetached() {
            return detached;
        }
    }

    public static class DeleteEvent extends ContactFormEvent {
        DeleteEvent(ContactForm source, Contact contact) {
            super(source, contact);
        }

    }

    public static class CloseEvent extends ContactFormEvent {
        CloseEvent(ContactForm source) {
            super(source, null);
        }
    }

    public <T extends ComponentEvent<?>> Registration addListener(Class<T> eventType,
            ComponentEventListener<T> listener) {
        return getEventBus().addListener(eventType, listener);
    }
}

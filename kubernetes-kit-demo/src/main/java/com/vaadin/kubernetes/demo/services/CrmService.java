package com.vaadin.kubernetes.demo.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.vaadin.kubernetes.demo.entity.Company;
import com.vaadin.kubernetes.demo.entity.Contact;
import com.vaadin.kubernetes.demo.entity.Status;
import com.vaadin.kubernetes.demo.repository.CompanyRepository;
import com.vaadin.kubernetes.demo.repository.ContactRepository;
import com.vaadin.kubernetes.demo.repository.StatusRepository;

@Service
public class CrmService {

    private static Logger logger = LoggerFactory.getLogger(CrmService.class);

    private final ContactRepository contactRepository;
    private final CompanyRepository companyRepository;
    private final StatusRepository statusRepository;

    public CrmService(ContactRepository contactRepository,
            CompanyRepository companyRepository,
            StatusRepository statusRepository) {
        this.contactRepository = contactRepository;
        this.companyRepository = companyRepository;
        this.statusRepository = statusRepository;
    }

    public List<Contact> findAllContacts(String stringFilter) {
        if (stringFilter == null || stringFilter.isEmpty()) {
            return contactRepository.findAll();
        } else {
            return contactRepository.search(stringFilter);
        }
    }

    public long countContacts() {
        return contactRepository.count();
    }

    public void deleteContact(Contact contact) {
        contactRepository.delete(contact);
    }

    public void saveContact(Contact contact) {
        if (contact == null) {
            System.err.println("Contact is null. Are you sure you have connected your form to the application?");
            return;
        }
        contactRepository.save(contact);
    }

    public List<Company> findAllCompanies() {
        return companyRepository.findAll();
    }

    public List<Status> findAllStatuses(){
        return statusRepository.findAll();
    }
}

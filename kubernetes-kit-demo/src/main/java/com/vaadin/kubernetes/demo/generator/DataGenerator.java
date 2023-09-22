package com.vaadin.kubernetes.demo.generator;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javafaker.Faker;
import com.github.javafaker.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.kubernetes.demo.entity.Company;
import com.vaadin.kubernetes.demo.entity.Contact;
import com.vaadin.kubernetes.demo.entity.Status;
import com.vaadin.kubernetes.demo.repository.CompanyRepository;
import com.vaadin.kubernetes.demo.repository.ContactRepository;
import com.vaadin.kubernetes.demo.repository.StatusRepository;

@SpringComponent
public class DataGenerator {

    @Bean
    CommandLineRunner loadData(ContactRepository contactRepository, CompanyRepository companyRepository,
            StatusRepository statusRepository) {

        return args -> {
            Logger logger = LoggerFactory.getLogger(getClass());
            if (contactRepository.count() != 0L) {
                logger.info("Using existing database");
                return;
            }
            int seed = 123;

            logger.info("Generating demo data");
            Faker faker = new Faker();
            Supplier<Company> companyGenerator = () -> {
                Company company = new Company();
                company.setName(faker.company().name());
                return company;
            };
            List<Company> companies = companyRepository.saveAll(Stream.generate(companyGenerator).limit(5).toList());

            List<Status> statuses = statusRepository
                    .saveAll(Stream.of("Imported lead", "Not contacted", "Contacted", "Customer", "Closed (lost)")
                            .map(Status::new).collect(Collectors.toList()));

            logger.info("... generating 50 Contact entities...");
            Supplier<Contact> contactGenerator = () -> {
                Contact contact = new Contact();
                Name name = faker.name();
                contact.setFirstName(name.firstName());
                contact.setLastName(name.lastName());
                contact.setEmail(faker.internet().emailAddress());
                return contact;
            };

            Random r = new Random(seed);
            List<Contact> contacts = Stream.generate(contactGenerator)
                    .peek(contact -> {
                        int companyIndex = r.nextInt(companies.size());
                        contact.setCompany(companies.get(companyIndex));
                        int contactIndex = r.nextInt(statuses.size());
                        contact.setStatus(statuses.get(contactIndex));
                    }).limit(50).toList();

            contactRepository.saveAll(contacts);

            logger.info("Generated demo data");
        };
    }

}

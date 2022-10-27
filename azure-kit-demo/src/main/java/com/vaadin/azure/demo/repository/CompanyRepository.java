package com.vaadin.azure.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vaadin.azure.demo.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Integer> {

}

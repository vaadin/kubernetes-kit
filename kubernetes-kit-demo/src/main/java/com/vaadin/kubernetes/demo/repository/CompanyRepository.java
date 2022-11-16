package com.vaadin.kubernetes.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vaadin.kubernetes.demo.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Integer> {

}

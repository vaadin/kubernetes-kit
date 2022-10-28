package com.vaadin.azure.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vaadin.azure.demo.entity.Status;

public interface StatusRepository extends JpaRepository<Status, Integer> {

}

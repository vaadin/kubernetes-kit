package com.vaadin.kubernetes.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vaadin.kubernetes.demo.entity.Status;

public interface StatusRepository extends JpaRepository<Status, Integer> {

}

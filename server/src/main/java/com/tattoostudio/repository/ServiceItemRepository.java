package com.tattoostudio.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tattoostudio.model.ServiceItem;

public interface ServiceItemRepository extends MongoRepository<ServiceItem, String> {

    List<ServiceItem> findByActiveTrueOrderBySortOrderAsc();
}

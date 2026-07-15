package com.czl.teamupbackend.repository;

import com.czl.teamupbackend.model.mongo.DocumentContentDoc;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DocumentContentRepository extends MongoRepository<DocumentContentDoc, String> {

    Optional<DocumentContentDoc> findByDocumentId(Long documentId);
}

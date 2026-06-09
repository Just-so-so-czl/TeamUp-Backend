package com.czl.teamupbackend.repository;

import com.czl.teamupbackend.model.mongo.MentorMemoryDoc;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MentorMemoryDocRepository extends MongoRepository<MentorMemoryDoc, String> {

    Optional<MentorMemoryDoc> findBySessionIdAndMemoryType(Long sessionId, String memoryType);
}

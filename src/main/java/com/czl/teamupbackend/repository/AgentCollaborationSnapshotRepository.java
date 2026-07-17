package com.czl.teamupbackend.repository;

import com.czl.teamupbackend.model.mongo.AgentCollaborationSnapshotDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AgentCollaborationSnapshotRepository extends MongoRepository<AgentCollaborationSnapshotDoc, String> {
}

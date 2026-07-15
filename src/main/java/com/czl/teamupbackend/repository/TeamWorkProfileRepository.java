package com.czl.teamupbackend.repository;

import com.czl.teamupbackend.model.mongo.TeamWorkProfileDoc;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TeamWorkProfileRepository extends MongoRepository<TeamWorkProfileDoc, String> {

    Optional<TeamWorkProfileDoc> findByTeamId(Long teamId);
}

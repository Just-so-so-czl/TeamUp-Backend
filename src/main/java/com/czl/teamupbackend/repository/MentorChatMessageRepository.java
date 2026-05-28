package com.czl.teamupbackend.repository;

import com.czl.teamupbackend.model.mongo.MentorChatMessageDoc;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MentorChatMessageRepository extends MongoRepository<MentorChatMessageDoc, String> {

    List<MentorChatMessageDoc> findTop20BySessionIdOrderByCreatedAtDesc(Long sessionId);

    List<MentorChatMessageDoc> findByIdIn(List<String> ids);
}

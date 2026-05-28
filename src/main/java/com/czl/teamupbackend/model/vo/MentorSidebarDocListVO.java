package com.czl.teamupbackend.model.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MentorSidebarDocListVO {

    private List<MentorSidebarDocItemVO> documents;
}


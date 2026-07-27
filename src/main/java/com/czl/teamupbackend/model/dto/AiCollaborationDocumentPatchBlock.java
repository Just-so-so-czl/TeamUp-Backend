package com.czl.teamupbackend.model.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.Data;

@Data
public class AiCollaborationDocumentPatchBlock {

    @JsonPropertyDescription("PARAGRAPH, HEADING, BULLET_LIST, ORDERED_LIST, BLOCKQUOTE, CODE_BLOCK, HORIZONTAL_RULE, or TABLE. IMAGE cannot be created through newBlocks.")
    private String type;

    @JsonPropertyDescription("Block text. Required for paragraph, heading, blockquote, and code block.")
    private String text;

    @JsonPropertyDescription("Heading level from 1 to 6. Used only by HEADING.")
    private Integer level;

    @JsonPropertyDescription("Complete list item texts. Required by BULLET_LIST and ORDERED_LIST.")
    private List<String> items;

    @JsonPropertyDescription("Starting number. Optional and used only by ORDERED_LIST.")
    private Integer start;

    @JsonPropertyDescription("Optional code language. Used only by CODE_BLOCK.")
    private String language;

    @JsonPropertyDescription("Optional table header cells. Used only by TABLE. Omit or use an empty list for a table without a header row.")
    private List<String> headers;

    @JsonPropertyDescription("Complete rectangular table body. Used only by TABLE. Every row must have the same number of cells as headers, or as the first row when headers is empty.")
    private List<List<String>> rows;
}

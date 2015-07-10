package com.nike.mm.entity.plugins

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldIndex
import org.springframework.data.elasticsearch.annotations.FieldType


@Document(indexName = "measurementor", type = "jirahistory")
class JiraHistory {

    @Id
    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String id

    @Field(type = FieldType.Date,
            index = FieldIndex.not_analyzed)
    Date   timestamp

    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String changedBy

    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String changeField

    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String newValue

    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String key

    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String sourceId

    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String dataType

    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String issueType
}

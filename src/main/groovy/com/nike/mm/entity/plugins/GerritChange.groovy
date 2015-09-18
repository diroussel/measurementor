package com.nike.mm.entity.plugins

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldIndex
import org.springframework.data.elasticsearch.annotations.FieldType

@Document(indexName = "measurementor-gerrit-aggregated", type = "gerrit")
class GerritChange {

    @Id
    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String id

    @Field(type = FieldType.Date,
            index = FieldIndex.not_analyzed)
    Date created

    @Field(type = FieldType.Date,
            index = FieldIndex.not_analyzed)
    Date lastUpdated

    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String projectName

    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String branch

    @Field(type = FieldType.String,
            index = FieldIndex.not_analyzed)
    String jiraKey

    @Field(type = FieldType.Integer,
            index = FieldIndex.not_analyzed)
    Integer numberOfPatchSets

    @Field(type = FieldType.Integer,
            index = FieldIndex.not_analyzed)
    Integer totalReviewTimeHours


}

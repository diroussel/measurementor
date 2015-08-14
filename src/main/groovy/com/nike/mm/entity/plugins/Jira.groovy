package com.nike.mm.entity.plugins

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldIndex
import org.springframework.data.elasticsearch.annotations.FieldType

@Document(indexName = "measurementor-jira-aggregated", type = "jira")
class Jira {

	@Id
	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String id

	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String couchId

	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String key

	@Field(type = FieldType.Date,
			index = FieldIndex.not_analyzed)
	Date created

	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String createdBy

	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String issueType

	@Field(type = FieldType.Integer,
			index = FieldIndex.not_analyzed)
	int movedForward

	@Field(type = FieldType.Integer,
			index = FieldIndex.not_analyzed)
	int movedBackward

	@Field(type = FieldType.Integer,
			index = FieldIndex.not_analyzed)
	Integer recidivism

	@Field(type = FieldType.String,
			index = FieldIndex.analyzed)
	String[] fixedVersions

	@Field(type = FieldType.Integer,
			index = FieldIndex.not_analyzed)
	int storyPoints

	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String[] assignees //this is actually a map of stuff

	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String[] tags

	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String dataType

	@Field(type = FieldType.Date,
			index = FieldIndex.not_analyzed)
	Date finished

	@Field(type = FieldType.Long,
			index = FieldIndex.not_analyzed)
	long leadTime

	@Field(type = FieldType.Long,
			index = FieldIndex.not_analyzed)
	long devTime

	@Field(type = FieldType.Integer,
			index = FieldIndex.not_analyzed)
	int commentCount

	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String jiraProject

	@Field(type = FieldType.Integer,
			index = FieldIndex.not_analyzed)
	int estimateHealth

	@Field(type = FieldType.Long,
			index = FieldIndex.not_analyzed)
	long rawEstimateHealth

	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String[] components

	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String product

	@Field(type = FieldType.String,
			index = FieldIndex.not_analyzed)
	String priority
}
